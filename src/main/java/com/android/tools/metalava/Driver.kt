/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:JvmName("Driver")

package com.android.tools.metalava

import com.android.SdkConstants.DOT_JAR
import com.android.SdkConstants.DOT_JAVA
import com.android.SdkConstants.DOT_KT
import com.android.SdkConstants.DOT_TXT
import com.android.tools.lint.UastEnvironment
import com.android.tools.lint.annotations.Extractor
import com.android.tools.lint.checks.infrastructure.ClassName
import com.android.tools.lint.detector.api.assertionsEnabled
import com.android.tools.metalava.CompatibilityCheck.CheckRequest
import com.android.tools.metalava.apilevels.ApiGenerator
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.PackageDocs
import com.android.tools.metalava.model.psi.PsiBasedCodebase
import com.android.tools.metalava.model.psi.packageHtmlToJavadoc
import com.android.tools.metalava.model.text.TextCodebase
import com.android.tools.metalava.model.visitors.ApiVisitor
import com.android.tools.metalava.stub.StubWriter
import com.google.common.base.Stopwatch
import com.google.common.collect.Lists
import com.google.common.io.Files
import com.intellij.core.CoreApplicationEnvironment
import com.intellij.openapi.diagnostic.DefaultLogger
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.javadoc.CustomJavadocTagProvider
import com.intellij.psi.javadoc.JavadocTagInfo
import org.jetbrains.kotlin.config.CommonConfigurationKeys.MODULE_NAME
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.LanguageVersionSettings
import java.io.File
import java.io.IOException
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.TimeUnit.SECONDS
import java.util.function.Predicate
import kotlin.system.exitProcess
import kotlin.text.Charsets.UTF_8

const val PROGRAM_NAME = "metalava"
const val HELP_PROLOGUE = "$PROGRAM_NAME extracts metadata from source code to generate artifacts such as the " +
        "signature files, the SDK stub files, external annotations etc."
const val PACKAGE_HTML = "package.html"
const val OVERVIEW_HTML = "overview.html"

@Suppress("PropertyName") // Can't mark const because trimIndent() :-(
val BANNER: String = """
                _        _
 _ __ ___   ___| |_ __ _| | __ ___   ____ _
| '_ ` _ \ / _ \ __/ _` | |/ _` \ \ / / _` |
| | | | | |  __/ || (_| | | (_| |\ V / (_| |
|_| |_| |_|\___|\__\__,_|_|\__,_| \_/ \__,_|
""".trimIndent()

fun main(args: Array<String>) {
    run(args, setExitCode = true)
}

internal var hasFileReadViolations = false

/**
 * The metadata driver is a command line interface to extracting various metadata
 * from a source tree (or existing signature files etc.). Run with --help to see
 * more details.
 */
fun run(
        originalArgs: Array<String>,
        stdout: PrintWriter = PrintWriter(OutputStreamWriter(System.out)),
        stderr: PrintWriter = PrintWriter(OutputStreamWriter(System.err)),
        setExitCode: Boolean = false
): Boolean {
    var exitCode = 0

    try {
        val modifiedArgs = preprocessArgv(originalArgs)

        progress("$PROGRAM_NAME started\n")

        // Dump the arguments, and maybe generate a rerun-script.
        maybeDumpArgv(stdout, originalArgs, modifiedArgs)

        // Actual work begins here.
        options = Options(modifiedArgs, stdout, stderr)

        maybeActivateSandbox()

        processFlags()

        if (options.allReporters.any { it.hasErrors() } && !options.passBaselineUpdates) {
            // Repeat the errors at the end to make it easy to find the actual problems.
            if (options.repeatErrorsMax > 0) {
                repeatErrors(stderr, options.allReporters, options.repeatErrorsMax)
            }
            exitCode = -1
        }
        if (hasFileReadViolations) {
            if (options.strictInputFiles.shouldFail) {
                stderr.print("Error: ")
                exitCode = -1
            } else {
                stderr.print("Warning: ")
            }
            stderr.println("$PROGRAM_NAME detected access to files that are not explicitly specified. See ${options.strictInputViolationsFile} for details.")
        }
    } catch (e: DriverException) {
        stdout.flush()
        stderr.flush()

        val prefix = if (e.exitCode != 0) {
            "Aborting: "
        } else {
            ""
        }

        if (e.stderr.isNotBlank()) {
            stderr.println("\n${prefix}${e.stderr}")
        }
        if (e.stdout.isNotBlank()) {
            stdout.println("\n${prefix}${e.stdout}")
        }
        exitCode = e.exitCode
    } finally {
        disposeUastEnvironment()
    }

    // Update and close all baseline files.
    options.allBaselines.forEach { baseline ->
        if (options.verbose) {
            baseline.dumpStats(options.stdout)
        }
        if (baseline.close()) {
            if (!options.quiet) {
                stdout.println("$PROGRAM_NAME wrote updated baseline to ${baseline.updateFile}")
            }
        }
    }

    options.reportEvenIfSuppressedWriter?.close()
    options.strictInputViolationsPrintWriter?.close()

    // Show failure messages, if any.
    options.allReporters.forEach {
        it.writeErrorMessage(stderr)
    }

    stdout.flush()
    stderr.flush()

    if (setExitCode) {
        exit(exitCode)
    }

    return exitCode == 0
}

private fun exit(exitCode: Int = 0) {
    if (options.verbose) {
        progress("$PROGRAM_NAME exiting with exit code $exitCode\n")
    }
    options.stdout.flush()
    options.stderr.flush()
    exitProcess(exitCode)
}

private fun maybeActivateSandbox() {
    // Set up a sandbox to detect access to files that are not explicitly specified.
    if (options.strictInputFiles == Options.StrictInputFileMode.PERMISSIVE) {
        return
    }

    val writer = options.strictInputViolationsPrintWriter!!

    // Writes all violations to [Options.strictInputFiles].
    // If Options.StrictInputFile.Mode is STRICT, then all violations on reads are logged, and the
    // tool exits with a negative error code if there are any file read violations. Directory read
    // violations are logged, but are considered to be a "warning" and doesn't affect the exit code.
    // If STRICT_WARN, all violations on reads are logged similar to STRICT, but the exit code is
    // unaffected.
    // If STRICT_WITH_STACK, similar to STRICT, but also logs the stack trace to
    // Options.strictInputFiles.
    // See [FileReadSandbox] for the details.
    FileReadSandbox.activate(object : FileReadSandbox.Listener {
        var seen = mutableSetOf<String>()
        override fun onViolation(absolutePath: String, isDirectory: Boolean) {
            if (!seen.contains(absolutePath)) {
                val suffix = if (isDirectory) "/" else ""
                writer.println("$absolutePath$suffix")
                if (options.strictInputFiles == Options.StrictInputFileMode.STRICT_WITH_STACK) {
                    Throwable().printStackTrace(writer)
                }
                seen.add(absolutePath)
                if (!isDirectory) {
                    hasFileReadViolations = true
                }
            }
        }
    })
}

private fun repeatErrors(writer: PrintWriter, reporters: List<Reporter>, max: Int) {
    writer.println("Error: $PROGRAM_NAME detected the following problems:")
    val totalErrors = reporters.sumOf { it.errorCount }
    var remainingCap = max
    var totalShown = 0
    reporters.forEach {
        val numShown = it.printErrors(writer, remainingCap)
        remainingCap -= numShown
        totalShown += numShown
    }
    if (totalShown < totalErrors) {
        writer.println("${totalErrors - totalShown} more error(s) omitted. Search the log for 'error:' to find all of them.")
    }
}

private fun processFlags() {
    val stopwatch = Stopwatch.createStarted()

    processNonCodebaseFlags()

    val sources = options.sources
    val codebase =
            if (sources.isNotEmpty() && sources[0].path.endsWith(DOT_TXT)) {
                // Make sure all the source files have .txt extensions.
                sources.firstOrNull { !it.path.endsWith(DOT_TXT) }?.let {
                    throw DriverException("Inconsistent input file types: The first file is of $DOT_TXT, but detected different extension in ${it.path}")
                }
                SignatureFileLoader.loadFiles(sources, options.inputKotlinStyleNulls)
            } else if (options.apiJar != null) {
                loadFromJarFile(options.apiJar!!)
            } else if (sources.size == 1 && sources[0].path.endsWith(DOT_JAR)) {
                loadFromJarFile(sources[0])
            } else if (sources.isNotEmpty() || options.sourcePath.isNotEmpty()) {
                loadFromSources()
            } else {
                return
            }
    options.manifest?.let { codebase.manifest = it }

    if (options.verbose) {
        progress("$PROGRAM_NAME analyzed API in ${stopwatch.elapsed(SECONDS)} seconds\n")
    }

    options.subtractApi?.let {
        progress("Subtracting API: ")
        subtractApi(codebase, it)
    }

    val androidApiLevelXml = options.generateApiLevelXml
    val apiLevelJars = options.apiLevelJars
    if (androidApiLevelXml != null && apiLevelJars != null) {
        assert(options.currentApiLevel != -1)

        progress("Generating API levels XML descriptor file, ${androidApiLevelXml.name}: ")
        ApiGenerator.generate(
                apiLevelJars, options.firstApiLevel, options.currentApiLevel, options.isDeveloperPreviewBuild(),
                androidApiLevelXml, codebase, options.sdkJarRoot, options.sdkInfoFile, options.removeMissingClassesInApiLevels
        )
    }

    if (options.docStubsDir != null || options.enhanceDocumentation) {
        if (!codebase.supportsDocumentation()) {
            error("Codebase does not support documentation, so it cannot be enhanced.")
        }
        progress("Enhancing docs: ")
        val docAnalyzer = DocAnalyzer(codebase)
        docAnalyzer.enhance()
        val applyApiLevelsXml = options.applyApiLevelsXml
        if (applyApiLevelsXml != null) {
            progress("Applying API levels")
            docAnalyzer.applyApiLevels(applyApiLevelsXml)
        }
    }

    // Generate the documentation stubs *before* we migrate nullness information.
    options.docStubsDir?.let {
        createStubFiles(
                it, codebase, docStubs = true,
                writeStubList = options.docStubsSourceList != null
        )
    }

    // Based on the input flags, generates various output files such
    // as signature files and/or stubs files
    options.apiFile?.let { apiFile ->
        val apiType = ApiType.PUBLIC_API
        val apiEmit = apiType.getEmitFilter()
        val apiReference = apiType.getReferenceFilter()

        createReportFile(codebase, apiFile, "API") { printWriter ->
            SignatureWriter(printWriter, apiEmit, apiReference, codebase.preFiltered)
        }
    }

    options.apiXmlFile?.let { apiFile ->
        val apiType = ApiType.PUBLIC_API
        val apiEmit = apiType.getEmitFilter()
        val apiReference = apiType.getReferenceFilter()

        createReportFile(codebase, apiFile, "XML API") { printWriter ->
            JDiffXmlWriter(printWriter, apiEmit, apiReference, codebase.preFiltered)
        }
    }

    options.removedApiFile?.let { apiFile ->
        val unfiltered = codebase.original ?: codebase

        val apiType = ApiType.REMOVED
        val removedEmit = apiType.getEmitFilter()
        val removedReference = apiType.getReferenceFilter()

        createReportFile(unfiltered, apiFile, "removed API", options.deleteEmptyRemovedSignatures) { printWriter ->
            SignatureWriter(printWriter, removedEmit, removedReference, codebase.original != null, options.includeSignatureFormatVersionRemoved)
        }
    }

    options.dexApiFile?.let { apiFile ->
        val apiFilter = FilterPredicate(ApiPredicate())
        val memberIsNotCloned: Predicate<Item> = Predicate { !it.isCloned() }
        val apiReference = ApiPredicate(ignoreShown = true)
        val dexApiEmit = memberIsNotCloned.and(apiFilter)

        createReportFile(
                codebase, apiFile, "DEX API"
        ) { printWriter -> DexApiWriter(printWriter, dexApiEmit, apiReference) }
    }

    options.proguard?.let { proguard ->
        val apiEmit = FilterPredicate(ApiPredicate())
        val apiReference = ApiPredicate(ignoreShown = true)
        createReportFile(
                codebase, proguard, "Proguard file"
        ) { printWriter -> ProguardWriter(printWriter, apiEmit, apiReference) }
    }

    options.sdkValueDir?.let { dir ->
        dir.mkdirs()
        SdkFileWriter(codebase, dir).generate()
    }

    for (check in options.compatibilityChecks) {
        checkCompatibility(codebase, check)
    }

    val previousApiFile = options.migrateNullsFrom
    if (previousApiFile != null) {
        val previous =
                if (previousApiFile.path.endsWith(DOT_JAR)) {
                    loadFromJarFile(previousApiFile)
                } else {
                    SignatureFileLoader.load(
                            file = previousApiFile,
                            kotlinStyleNulls = options.inputKotlinStyleNulls
                    )
                }

        // If configured, checks for newly added nullness information compared
        // to the previous stable API and marks the newly annotated elements
        // as migrated (which will cause the Kotlin compiler to treat problems
        // as warnings instead of errors

        migrateNulls(codebase, previous)

        previous.dispose()
    }

    convertToWarningNullabilityAnnotations(codebase, options.forceConvertToWarningNullabilityAnnotations)

    // Now that we've migrated nullness information we can proceed to write non-doc stubs, if any.

    options.stubsDir?.let {
        createStubFiles(
                it, codebase, docStubs = false,
                writeStubList = options.stubsSourceList != null
        )
    }

    if (options.docStubsDir == null && options.stubsDir == null) {
        val writeStubsFile: (File) -> Unit = { file ->
            val root = File("").absoluteFile
            val rootPath = root.path
            val contents = sources.joinToString(" ") {
                val path = it.path
                if (path.startsWith(rootPath)) {
                    path.substring(rootPath.length)
                } else {
                    path
                }
            }
            file.writeText(contents)
        }
        options.stubsSourceList?.let(writeStubsFile)
        options.docStubsSourceList?.let(writeStubsFile)
    }
    options.externalAnnotations?.let { extractAnnotations(codebase, it) }

    if (options.verbose) {
        val packageCount = codebase.size()
        progress("$PROGRAM_NAME finished handling $packageCount packages in ${stopwatch.elapsed(SECONDS)} seconds\n")
    }
}

fun subtractApi(codebase: Codebase, subtractApiFile: File) {
    val path = subtractApiFile.path
    val oldCodebase =
            when {
                path.endsWith(DOT_TXT) -> SignatureFileLoader.load(subtractApiFile)
                path.endsWith(DOT_JAR) -> loadFromJarFile(subtractApiFile)
                else -> throw DriverException("Unsupported $ARG_SUBTRACT_API format, expected .txt or .jar: ${subtractApiFile.name}")
            }

    CodebaseComparator().compare(
            object : ComparisonVisitor() {
                override fun compare(old: ClassItem, new: ClassItem) {
                    new.emit = false
                }
            },
            oldCodebase, codebase, ApiType.ALL.getReferenceFilter()
    )
}

fun processNonCodebaseFlags() {
    // --copy-annotations?
    val privateAnnotationsSource = options.privateAnnotationsSource
    val privateAnnotationsTarget = options.privateAnnotationsTarget
    if (privateAnnotationsSource != null && privateAnnotationsTarget != null) {
        val rewrite = RewriteAnnotations()
        // Support pointing to both stub-annotations and stub-annotations/src/main/java
        val src = File(privateAnnotationsSource, "src${File.separator}main${File.separator}java")
        val source = if (src.isDirectory) src else privateAnnotationsSource
        source.listFiles()?.forEach { file ->
            rewrite.modifyAnnotationSources(null, file, File(privateAnnotationsTarget, file.name))
        }
    }

    // Convert android.jar files?
    options.androidJarSignatureFiles?.let { root ->
        // Generate API signature files for all the historical JAR files
        ConvertJarsToSignatureFiles().convertJars(root)
    }

    for (convert in options.convertToXmlFiles) {
        val signatureApi = SignatureFileLoader.load(
                file = convert.fromApiFile,
                kotlinStyleNulls = options.inputKotlinStyleNulls
        )

        val apiType = ApiType.ALL
        val apiEmit = apiType.getEmitFilter()
        val strip = convert.strip
        val apiReference = if (strip) apiType.getEmitFilter() else apiType.getReferenceFilter()
        val baseFile = convert.baseApiFile

        val outputApi =
                if (baseFile != null) {
                    // Convert base on a diff
                    val baseApi = SignatureFileLoader.load(
                            file = baseFile,
                            kotlinStyleNulls = options.inputKotlinStyleNulls
                    )
                    TextCodebase.computeDelta(baseFile, baseApi, signatureApi)
                } else {
                    signatureApi
                }

        // See JDiff's XMLToAPI#nameAPI
        val apiName = convert.outputFile.nameWithoutExtension.replace(' ', '_')
        createReportFile(outputApi, convert.outputFile, "JDiff File") { printWriter ->
            JDiffXmlWriter(printWriter, apiEmit, apiReference, signatureApi.preFiltered && !strip, apiName)
        }
    }
}

/**
 * Checks compatibility of the given codebase with the codebase described in the
 * signature file.
 */
fun checkCompatibility(
        newCodebase: Codebase,
        check: CheckRequest
) {
    progress("Checking API compatibility ($check): ")
    val signatureFile = check.file

    val oldCodebase =
            if (signatureFile.path.endsWith(DOT_JAR)) {
                loadFromJarFile(signatureFile)
            } else {
                SignatureFileLoader.load(
                        file = signatureFile,
                        kotlinStyleNulls = options.inputKotlinStyleNulls
                )
            }

    if (oldCodebase is TextCodebase && oldCodebase.format > FileFormat.V1 && options.outputFormat == FileFormat.V1) {
        throw DriverException("Cannot perform compatibility check of signature file $signatureFile in format ${oldCodebase.format} without analyzing current codebase with $ARG_FORMAT=${oldCodebase.format}")
    }

    var baseApi: Codebase? = null

    val apiType = check.apiType

    if (options.showUnannotated && apiType == ApiType.PUBLIC_API) {
        // Fast path: if we've already generated a signature file, and it's identical, we're good!
        val apiFile = options.apiFile
        if (apiFile != null && apiFile.readText(UTF_8) == signatureFile.readText(UTF_8)) {
            return
        }
        val baseApiFile = options.baseApiForCompatCheck
        if (baseApiFile != null) {
            baseApi = SignatureFileLoader.load(
                    file = baseApiFile,
                    kotlinStyleNulls = options.inputKotlinStyleNulls
            )
        }
    } else if (options.baseApiForCompatCheck != null) {
        // This option does not make sense with showAnnotation, as the "base" in that case
        // is the non-annotated APIs.
        throw DriverException(
                ARG_CHECK_COMPATIBILITY_BASE_API +
                        " is not compatible with --showAnnotation."
        )
    }

    // If configured, compares the new API with the previous API and reports
    // any incompatibilities.
    CompatibilityCheck.checkCompatibility(newCodebase, oldCodebase, apiType, baseApi)
}

fun createTempFile(namePrefix: String, nameSuffix: String): File {
    val tempFolder = options.tempFolder
    return if (tempFolder != null) {
        val preferred = File(tempFolder, namePrefix + nameSuffix)
        if (!preferred.exists()) {
            return preferred
        }
        File.createTempFile(namePrefix, nameSuffix, tempFolder)
    } else {
        File.createTempFile(namePrefix, nameSuffix)
    }
}

private fun migrateNulls(codebase: Codebase, previous: Codebase) {
    previous.compareWith(NullnessMigration(), codebase)
}

private fun convertToWarningNullabilityAnnotations(codebase: Codebase, filter: PackageFilter?) {
    if (filter != null) {
        // Our caller has asked for these APIs to not trigger nullness errors (only warnings) if
        // their callers make incorrect nullness assumptions (for example, calling a function on a
        // reference of nullable type). The way to communicate this to kotlinc is to mark these
        // APIs as RecentlyNullable/RecentlyNonNull
        codebase.accept(MarkPackagesAsRecent(filter))
    }
}

private fun loadFromSources(): Codebase {
    progress("Processing sources: ")

    val sources = options.sources.ifEmpty {
        if (options.verbose) {
            options.stdout.println("No source files specified: recursively including all sources found in the source path (${options.sourcePath.joinToString()}})")
        }
        gatherSources(options.sourcePath)
    }

    progress("Reading Codebase: ")
    val codebase = parseSources(sources, "Codebase loaded from source folders")

    progress("Analyzing API: ")

    val analyzer = ApiAnalyzer(codebase)
    analyzer.mergeExternalInclusionAnnotations()
    analyzer.computeApi()

    val filterEmit = ApiPredicate(ignoreShown = true, ignoreRemoved = false)
    val apiEmit = ApiPredicate(ignoreShown = true)
    val apiReference = ApiPredicate(ignoreShown = true)

    // Copy methods from soon-to-be-hidden parents into descendant classes, when necessary. Do
    // this before merging annotations or performing checks on the API to ensure that these methods
    // can have annotations added and are checked properly.
    progress("Insert missing stubs methods: ")
    analyzer.generateInheritedStubs(apiEmit, apiReference)

    analyzer.mergeExternalQualifierAnnotations()
    options.nullabilityAnnotationsValidator?.validateAllFrom(codebase, options.validateNullabilityFromList)
    options.nullabilityAnnotationsValidator?.report()
    analyzer.handleStripping()

    // General API checks for Android APIs
    AndroidApiChecks().check(codebase)

    if (options.checkApi) {
        progress("API Lint: ")
        val localTimer = Stopwatch.createStarted()
        // See if we should provide a previous codebase to provide a delta from?
        val previousApiFile = options.checkApiBaselineApiFile
        val previous =
                when {
                    previousApiFile == null -> null
                    previousApiFile.path.endsWith(DOT_JAR) -> loadFromJarFile(previousApiFile)
                    else -> SignatureFileLoader.load(
                            file = previousApiFile,
                            kotlinStyleNulls = options.inputKotlinStyleNulls
                    )
                }
        val apiLintReporter = options.reporterApiLint
        ApiLint.check(codebase, previous, apiLintReporter)
        progress("$PROGRAM_NAME ran api-lint in ${localTimer.elapsed(SECONDS)} seconds with ${apiLintReporter.getBaselineDescription()}")
    }

    // Compute default constructors (and add missing package private constructors
    // to make stubs compilable if necessary). Do this after all the checks as
    // these are not part of the API.
    if (options.stubsDir != null || options.docStubsDir != null) {
        progress("Insert missing constructors: ")
        analyzer.addConstructors(filterEmit)
    }

    progress("Performing misc API checks: ")
    analyzer.performChecks()

    return codebase
}

/**
 * Returns a codebase initialized from the given Java or Kotlin source files, with the given
 * description. The codebase will use a project environment initialized according to the current
 * [options].
 */
internal fun parseSources(
        sources: List<File>,
        description: String,
        sourcePath: List<File> = options.sourcePath,
        classpath: List<File> = options.classpath,
        javaLanguageLevel: LanguageLevel = options.javaLanguageLevel,
        kotlinLanguageLevel: LanguageVersionSettings = options.kotlinLanguageLevel,
        manifest: File? = options.manifest
): PsiBasedCodebase {
    val sourceRoots = mutableListOf<File>()
    sourcePath.filterTo(sourceRoots) { it.path.isNotBlank() }
    // Add in source roots implied by the source files
    if (options.allowImplicitRoot) {
        extractRoots(sources, sourceRoots)
    }

    val config = UastEnvironment.Configuration.create()
    config.javaLanguageLevel = javaLanguageLevel
    config.kotlinLanguageLevel = kotlinLanguageLevel
    config.addSourceRoots(sourceRoots.map { it.absoluteFile })
    config.addClasspathRoots(classpath.map { it.absoluteFile })
    options.jdkHome?.let {
        if (options.isJdkModular(it)) {
            config.kotlinCompilerConfig.put(JVMConfigurationKeys.JDK_HOME, it)
            config.kotlinCompilerConfig.put(JVMConfigurationKeys.NO_JDK, false)
        }
    }

    val environment = createProjectEnvironment(config)

    val kotlinFiles = sources.filter { it.path.endsWith(DOT_KT) }
    environment.analyzeFiles(kotlinFiles)

    val rootDir = sourceRoots.firstOrNull() ?: sourcePath.firstOrNull() ?: File("").canonicalFile

    val units = Extractor.createUnitsForFiles(environment.ideaProject, sources)
    val packageDocs = gatherPackageJavadoc(sources, sourceRoots)

    val codebase = PsiBasedCodebase(rootDir, description)
    codebase.initialize(environment, units, packageDocs)
    codebase.manifest = manifest
    return codebase
}

fun loadFromJarFile(apiJar: File, manifest: File? = null, preFiltered: Boolean = false): Codebase {
    progress("Processing jar file: ")

    val config = UastEnvironment.Configuration.create()
    config.addClasspathRoots(listOf(apiJar))

    val environment = createProjectEnvironment(config)
    environment.analyzeFiles(emptyList()) // Initializes PSI machinery.

    val codebase = PsiBasedCodebase(apiJar, "Codebase loaded from $apiJar")
    codebase.initialize(environment, apiJar, preFiltered)
    if (manifest != null) {
        codebase.manifest = options.manifest
    }
    val apiEmit = ApiPredicate(ignoreShown = true)
    val apiReference = ApiPredicate(ignoreShown = true)
    val analyzer = ApiAnalyzer(codebase)
    analyzer.mergeExternalInclusionAnnotations()
    analyzer.computeApi()
    analyzer.mergeExternalQualifierAnnotations()
    options.nullabilityAnnotationsValidator?.validateAllFrom(codebase, options.validateNullabilityFromList)
    options.nullabilityAnnotationsValidator?.report()
    analyzer.generateInheritedStubs(apiEmit, apiReference)
    return codebase
}

internal const val METALAVA_SYNTHETIC_SUFFIX = "metalava_module"

private fun createProjectEnvironment(config: UastEnvironment.Configuration): UastEnvironment {
    ensurePsiFileCapacity()

    // Note: the Kotlin module name affects the naming of certain synthetic methods.
    config.kotlinCompilerConfig.put(MODULE_NAME, METALAVA_SYNTHETIC_SUFFIX)

    val environment = UastEnvironment.create(config)
    uastEnvironments.add(environment)

    if (!assertionsEnabled() &&
            System.getenv(ENV_VAR_METALAVA_DUMP_ARGV) == null &&
            !isUnderTest()
    ) {
        DefaultLogger.disableStderrDumping(environment.ideaProject)
    }

    // Missing service needed in metalava but not in lint: javadoc handling
    environment.ideaProject.registerService(
            com.intellij.psi.javadoc.JavadocManager::class.java,
            com.intellij.psi.impl.source.javadoc.JavadocManagerImpl::class.java
    )
    CoreApplicationEnvironment.registerExtensionPoint(
            environment.ideaProject.extensionArea, JavadocTagInfo.EP_NAME, JavadocTagInfo::class.java
    )
    CoreApplicationEnvironment.registerApplicationExtensionPoint(
            CustomJavadocTagProvider.EP_NAME, CustomJavadocTagProvider::class.java
    )

    return environment
}

private val uastEnvironments = mutableListOf<UastEnvironment>()

private fun disposeUastEnvironment() {
    // Codebase.dispose() is not consistently called, so we dispose the environments here too.
    for (env in uastEnvironments) {
        if (!env.ideaProject.isDisposed) {
            env.dispose()
        }
    }
    uastEnvironments.clear()
    UastEnvironment.disposeApplicationEnvironment()
}

private fun ensurePsiFileCapacity() {
    val fileSize = System.getProperty("idea.max.intellisense.filesize")
    if (fileSize == null) {
        // Ensure we can handle large compilation units like android.R
        System.setProperty("idea.max.intellisense.filesize", "100000")
    }
}

private fun extractAnnotations(codebase: Codebase, file: File) {
    val localTimer = Stopwatch.createStarted()

    options.externalAnnotations?.let { outputFile ->
        @Suppress("UNCHECKED_CAST")
        ExtractAnnotations(
                codebase,
                outputFile
        ).extractAnnotations()
        if (options.verbose) {
            progress("$PROGRAM_NAME extracted annotations into $file in ${localTimer.elapsed(SECONDS)} seconds\n")
        }
    }
}

private fun createStubFiles(stubDir: File, codebase: Codebase, docStubs: Boolean, writeStubList: Boolean) {
    if (codebase is TextCodebase) {
        if (options.verbose) {
            options.stdout.println(
                    "Generating stubs from text based codebase is an experimental feature. " +
                            "It is not guaranteed that stubs generated from text based codebase are " +
                            "class level equivalent to the stubs generated from source files. "
            )
        }
    }

    // Temporary bug workaround for org.chromium.arc
    if (options.sourcePath.firstOrNull()?.path?.endsWith("org.chromium.arc") == true) {
        codebase.findClass("org.chromium.mojo.bindings.Callbacks")?.hidden = true
    }

    if (docStubs) {
        progress("Generating documentation stub files: ")
    } else {
        progress("Generating stub files: ")
    }

    val localTimer = Stopwatch.createStarted()

    val stubWriter =
            StubWriter(
                    codebase = codebase,
                    stubsDir = stubDir,
                    generateAnnotations = options.generateAnnotations,
                    preFiltered = codebase.preFiltered,
                    docStubs = docStubs
            )
    codebase.accept(stubWriter)

    if (docStubs) {
        // Overview docs? These are generally in the empty package.
        codebase.findPackage("")?.let { empty ->
            val overview = codebase.getPackageDocs()?.getOverviewDocumentation(empty)
            if (overview != null && overview.isNotBlank()) {
                stubWriter.writeDocOverview(empty, overview)
            }
        }
    }

    if (writeStubList) {
        // Optionally also write out a list of source files that were generated; used
        // for example to point javadoc to the stubs output to generate documentation
        val file = if (docStubs) {
            options.docStubsSourceList ?: options.stubsSourceList
        } else {
            options.stubsSourceList
        }
        file?.let {
            val root = File("").absoluteFile
            stubWriter.writeSourceList(it, root)
        }
    }

    progress(
            "$PROGRAM_NAME wrote ${if (docStubs) "documentation" else ""} stubs directory $stubDir in ${
                localTimer.elapsed(SECONDS)
            } seconds\n"
    )
}

fun createReportFile(
        codebase: Codebase,
        apiFile: File,
        description: String?,
        deleteEmptyFiles: Boolean = false,
        createVisitor: (PrintWriter) -> ApiVisitor
) {
    if (description != null) {
        progress("Writing $description file: ")
    }
    val localTimer = Stopwatch.createStarted()
    try {
        val stringWriter = StringWriter()
        val writer = PrintWriter(stringWriter)
        writer.use { printWriter ->
            val apiWriter = createVisitor(printWriter)
            codebase.accept(apiWriter)
        }
        val text = stringWriter.toString()
        if (text.isNotEmpty() || !deleteEmptyFiles) {
            apiFile.writeText(text)
        }
    } catch (e: IOException) {
        reporter.report(Issues.IO_ERROR, apiFile, "Cannot open file for write.")
    }
    if (description != null && options.verbose) {
        progress("$PROGRAM_NAME wrote $description file $apiFile in ${localTimer.elapsed(SECONDS)} seconds\n")
    }
}

private fun skippableDirectory(file: File): Boolean = file.path.endsWith(".git") && file.name == ".git"

private fun addSourceFiles(list: MutableList<File>, file: File) {
    if (file.isDirectory) {
        if (skippableDirectory(file)) {
            return
        }
        if (java.nio.file.Files.isSymbolicLink(file.toPath())) {
            reporter.report(
                    Issues.IGNORING_SYMLINK, file,
                    "Ignoring symlink during source file discovery directory traversal"
            )
            return
        }
        val files = file.listFiles()
        if (files != null) {
            for (child in files) {
                addSourceFiles(list, child)
            }
        }
    } else if (file.isFile) {
        when {
            file.name.endsWith(DOT_JAVA) ||
                    file.name.endsWith(DOT_KT) ||
                    file.name.equals(PACKAGE_HTML) ||
                    file.name.equals(OVERVIEW_HTML) -> list.add(file)
        }
    }
}

fun gatherSources(sourcePath: List<File>): List<File> {
    val sources = Lists.newArrayList<File>()
    for (file in sourcePath) {
        if (file.path.isBlank()) {
            // --source-path "" means don't search source path; use "." for pwd
            continue
        }
        addSourceFiles(sources, file.absoluteFile)
    }
    return sources.sortedWith(compareBy { it.name })
}

private fun gatherPackageJavadoc(sources: List<File>, sourceRoots: List<File>): PackageDocs {
    val packageComments = HashMap<String, String>(100)
    val overviewHtml = HashMap<String, String>(10)
    val hiddenPackages = HashSet<String>(100)
    val sortedSourceRoots = sourceRoots.sortedBy { -it.name.length }
    for (file in sources) {
        var javadoc = false
        val map = when (file.name) {
            PACKAGE_HTML -> {
                javadoc = true; packageComments
            }

            OVERVIEW_HTML -> {
                overviewHtml
            }

            else -> continue
        }
        var contents = Files.asCharSource(file, UTF_8).read()
        if (javadoc) {
            contents = packageHtmlToJavadoc(contents)
        }

        // Figure out the package: if there is a java file in the same directory, get the package
        // name from the java file. Otherwise, guess from the directory path + source roots.
        // NOTE: This causes metalava to read files other than the ones explicitly passed to it.
        var pkg = file.parentFile?.listFiles()
                ?.filter { it.name.endsWith(DOT_JAVA) }
                ?.asSequence()?.mapNotNull { findPackage(it) }
                ?.firstOrNull()
        if (pkg == null) {
            // Strip the longest prefix source root.
            val prefix = sortedSourceRoots.firstOrNull { file.startsWith(it) }?.path ?: ""
            pkg = file.parentFile.path.substring(prefix.length).trim('/').replace("/", ".")
        }
        map[pkg] = contents
        if (contents.contains("@hide")) {
            hiddenPackages.add(pkg)
        }
    }

    return PackageDocs(packageComments, overviewHtml, hiddenPackages)
}

fun extractRoots(sources: List<File>, sourceRoots: MutableList<File> = mutableListOf()): List<File> {
    // Cache for each directory since computing root for a source file is
    // expensive
    val dirToRootCache = mutableMapOf<String, File>()
    for (file in sources) {
        val parent = file.parentFile ?: continue
        val found = dirToRootCache[parent.path]
        if (found != null) {
            continue
        }

        val root = findRoot(file) ?: continue
        dirToRootCache[parent.path] = root

        if (!sourceRoots.contains(root)) {
            sourceRoots.add(root)
        }
    }

    return sourceRoots
}

/**
 * If given a full path to a Java or Kotlin source file, produces the path to
 * the source root if possible.
 */
private fun findRoot(file: File): File? {
    val path = file.path
    if (path.endsWith(DOT_JAVA) || path.endsWith(DOT_KT)) {
        val pkg = findPackage(file) ?: return null
        val parent = file.parentFile ?: return null
        val endIndex = parent.path.length - pkg.length
        val before = path[endIndex - 1]
        if (before == '/' || before == '\\') {
            return File(path.substring(0, endIndex))
        } else {
            reporter.report(
                    Issues.IO_ERROR, file,
                    "$PROGRAM_NAME was unable to determine the package name. " +
                            "This usually means that a source file was where the directory does not seem to match the package " +
                            "declaration; we expected the path $path to end with /${pkg.replace('.', '/') + '/' + file.name}"
            )
        }
    }

    return null
}

/** Finds the package of the given Java/Kotlin source file, if possible */
fun findPackage(file: File): String? {
    val source = Files.asCharSource(file, UTF_8).read()
    return findPackage(source)
}

/** Finds the package of the given Java/Kotlin source code, if possible */
fun findPackage(source: String): String? {
    return ClassName(source).packageName
}

/** Whether metalava is running unit tests */
fun isUnderTest() = java.lang.Boolean.getBoolean(ENV_VAR_METALAVA_TESTS_RUNNING)

/** Whether metalava is being invoked as part of an Android platform build */
fun isBuildingAndroid() = System.getenv("ANDROID_BUILD_TOP") != null && !isUnderTest()
