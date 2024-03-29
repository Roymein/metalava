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

package com.android.tools.metalava

import com.android.SdkConstants
import com.android.SdkConstants.DOT_KT
import com.android.SdkConstants.DOT_TXT
import com.android.SdkConstants.DOT_XML
import com.android.ide.common.process.DefaultProcessExecutor
import com.android.ide.common.process.LoggedProcessOutputHandler
import com.android.ide.common.process.ProcessException
import com.android.ide.common.process.ProcessInfoBuilder
import com.android.tools.lint.LintCliClient
import com.android.tools.lint.UastEnvironment
import com.android.tools.lint.checks.ApiLookup
import com.android.tools.lint.checks.infrastructure.ClassName
import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.stripComments
import com.android.tools.lint.client.api.LintClient
import com.android.tools.metalava.model.SUPPORT_TYPE_USE_ANNOTATIONS
import com.android.tools.metalava.model.defaultConfiguration
import com.android.tools.metalava.model.parseDocument
import com.android.tools.metalava.model.text.ApiFile
import com.android.utils.SdkUtils
import com.android.utils.StdLogger
import com.google.common.io.ByteStreams
import com.google.common.io.Closeables
import com.google.common.io.Files
import com.intellij.openapi.util.Disposer
import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.rules.ErrorCollector
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.PrintStream
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URL
import kotlin.text.Charsets.UTF_8

const val CHECK_JDIFF = false

abstract class DriverTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder(File(System.getProperty("user.dir")))

    @get:Rule
    val errorCollector = ErrorCollector()

    @Before
    fun setup() {
        System.setProperty(ENV_VAR_METALAVA_TESTS_RUNNING, SdkConstants.VALUE_TRUE)
        Disposer.setDebugMode(true)
    }

    protected fun createProject(vararg files: TestFile): File {
        val dir = newFolder("project")

        files
            .map { it.createFile(dir) }
            .forEach { assertNotNull(it) }

        return dir
    }

    private fun newFolder(children: String = ""): File {
        var dir = File(temporaryFolder.root.path, children)
        return if (dir.exists()) {
            dir
        } else {
            temporaryFolder.newFolder(children)
        }
    }

    private fun newFile(children: String = ""): File {
        var dir = File(temporaryFolder.root.path, children)
        return if (dir.exists()) {
            dir
        } else {
            temporaryFolder.newFile(children)
        }
    }

    // Makes a note to fail the test, but still allows the test to complete before failing
    protected fun addError(error: String) {
        errorCollector.addError(Throwable(error))
    }

    protected fun getApiFile(): File {
        return File(temporaryFolder.root.path, "public-api.txt")
    }

    protected fun runDriver(vararg args: String, expectedFail: String = ""): String {

        resetTicker()

        // Capture the actual input and output from System.out/err and compare it
        // to the output printed through the official writer; they should be the same,
        // otherwise we have stray println's littered in the code!
        val previousOut = System.out
        val previousErr = System.err
        try {
            val output = TeeWriter(previousOut)
            System.setOut(PrintStream(output))
            val error = TeeWriter(previousErr)
            System.setErr(PrintStream(error))

            val sw = StringWriter()
            val writer = PrintWriter(sw)

            Disposer.setDebugMode(true)

            if (run(arrayOf(*args), writer, writer)) {
                assertTrue("Test expected to fail but didn't. Expected failure: $expectedFail", expectedFail.isEmpty())
            } else {
                val actualFail = cleanupString(sw.toString(), null)
                if (cleanupString(expectedFail, null).replace(".", "").trim() !=
                    actualFail.replace(".", "").trim()
                ) {
                    val reportedCompatError =
                        actualFail.startsWith("Aborting: Found compatibility problems checking the ")
                    if (expectedFail == "Aborting: Found compatibility problems" &&
                        reportedCompatError
                    ) {
                        // Special case for compat checks; we don't want to force each one of them
                        // to pass in the right string (which may vary based on whether writing out
                        // the signature was passed at the same time
                        // ignore
                    } else {
                        if (reportedCompatError) {
                            // if a compatibility error was unexpectedly reported, then mark that as
                            // an error but keep going so we can see the actual compatibility error
                            if (expectedFail.trimIndent() != actualFail) {
                                addError("ComparisonFailure: expected failure $expectedFail, actual $actualFail")
                            }
                        } else {
                            // no compatibility error; check for other errors now, and
                            // if one is found, fail right away
                            assertEquals(expectedFail.trimIndent(), actualFail)
                        }
                    }
                }
            }

            val stdout = output.toString(UTF_8.name())
            if (stdout.isNotEmpty()) {
                addError("Unexpected write to stdout:\n $stdout")
            }
            val stderr = error.toString(UTF_8.name())
            if (stderr.isNotEmpty()) {
                addError("Unexpected write to stderr:\n $stderr")
            }

            val printedOutput = sw.toString()
            if (printedOutput.isNotEmpty() && printedOutput.trim().isEmpty()) {
                fail("Printed newlines with nothing else")
            }

            UastEnvironment.checkApplicationEnvironmentDisposed()
            Disposer.assertIsEmpty(true)

            return printedOutput
        } finally {
            System.setOut(previousOut)
            System.setErr(previousErr)
        }
    }

    // This is here so we can keep a record of what was printed, to make sure we
    // don't have any unexpected printlns in the source that are left behind after
    // debugging and pollute the production output
    class TeeWriter(private val otherStream: PrintStream) : ByteArrayOutputStream() {
        override fun write(b: ByteArray?, off: Int, len: Int) {
            otherStream.write(b, off, len)
            super.write(b, off, len)
        }

        override fun write(b: ByteArray?) {
            otherStream.write(b)
            super.write(b)
        }

        override fun write(b: Int) {
            otherStream.write(b)
            super.write(b)
        }
    }

    protected fun getJdkPath(): String? {
        val javaHome = System.getProperty("java.home")
        if (javaHome != null) {
            var javaHomeFile = File(javaHome)
            if (File(javaHomeFile, "bin${File.separator}javac").exists()) {
                return javaHome
            } else if (javaHomeFile.name == "jre") {
                javaHomeFile = javaHomeFile.parentFile
                if (javaHomeFile != null && File(javaHomeFile, "bin${File.separator}javac").exists()) {
                    return javaHomeFile.path
                }
            }
        }
        return System.getenv("JAVA_HOME")
    }

    private fun <T> buildOptionalArgs(option: T?, converter: (T) -> Array<String>): Array<String> {
        return if (option != null) {
            converter(option)
        } else {
            emptyArray()
        }
    }

    /** File conversion tasks */
    data class ConvertData(
        val fromApi: String,
        val outputFile: String,
        val baseApi: String? = null,
        val strip: Boolean = true,
    )

    protected fun check(
        /** Any jars to add to the class path */
        classpath: Array<TestFile>? = null,
        /** The API signature content (corresponds to --api) */
        @Language("TEXT")
        api: String? = null,
        /** The API signature content (corresponds to --api-xml) */
        @Language("XML")
        apiXml: String? = null,
        /** The DEX API (corresponds to --dex-api) */
        dexApi: String? = null,
        /** The removed API (corresponds to --removed-api) */
        removedApi: String? = null,
        /** The subtract api signature content (corresponds to --subtract-api) */
        @Language("TEXT")
        subtractApi: String? = null,
        /** Expected stubs (corresponds to --stubs) */
        stubFiles: Array<TestFile> = emptyArray(),
        /** Stub source file list generated */
        stubsSourceList: String? = null,
        /** Doc Stub source file list generated */
        docStubsSourceList: String? = null,
        /** Whether the stubs should be written as documentation stubs instead of plain stubs. Decides
         * whether the stubs include @doconly elements, uses rewritten/migration annotations, etc */
        docStubs: Boolean = false,
        /** Signature file format */
        format: FileFormat = FileFormat.latest,
        /** Whether to trim the output (leading/trailing whitespace removal) */
        trim: Boolean = true,
        /** Whether to remove blank lines in the output (the signature file usually contains a lot of these) */
        stripBlankLines: Boolean = true,
        /** All expected issues to be generated when analyzing these sources */
        expectedIssues: String? = "",
        /** Expected [Severity.ERROR] issues to be generated when analyzing these sources */
        errorSeverityExpectedIssues: String? = null,
        checkCompilation: Boolean = false,
        /** Annotations to merge in (in .xml format) */
        @Language("XML")
        mergeXmlAnnotations: String? = null,
        /** Annotations to merge in (in .txt/.signature format) */
        @Language("TEXT")
        mergeSignatureAnnotations: String? = null,
        /** Qualifier annotations to merge in (in Java stub format) */
        @Language("JAVA")
        mergeJavaStubAnnotations: String? = null,
        /** Inclusion annotations to merge in (in Java stub format) */
        @Language("JAVA")
        mergeInclusionAnnotations: String? = null,
        /** Optional API signature files content to load **instead** of Java/Kotlin source files */
        @Language("TEXT")
        signatureSources: Array<String> = emptyArray(),
        /**
         * An optional API signature file content to load **instead** of Java/Kotlin source files.
         * This is added to [signatureSources]. This argument exists for backward compatibility.
         */
        @Language("TEXT")
        signatureSource: String? = null,
        /** An optional API jar file content to load **instead** of Java/Kotlin source files */
        apiJar: File? = null,
        /** An optional API signature to check the last released API's compatibility with */
        @Language("TEXT")
        checkCompatibilityApiReleased: String? = null,
        /** An optional API signature to check the last released removed API's compatibility with */
        @Language("TEXT")
        checkCompatibilityRemovedApiReleased: String? = null,
        /** An optional API signature to use as the base API codebase during compat checks */
        @Language("TEXT")
        checkCompatibilityBaseApi: String? = null,
        @Language("TEXT")
        migrateNullsApi: String? = null,
        /** An optional Proguard keep file to generate */
        @Language("Proguard")
        proguard: String? = null,
        /** Show annotations (--show-annotation arguments) */
        showAnnotations: Array<String> = emptyArray(),
        /** "Show for stub purposes" API annotation ([ARG_SHOW_FOR_STUB_PURPOSES_ANNOTATION]) */
        showForStubPurposesAnnotations: Array<String> = emptyArray(),
        /** Hide annotations (--hide-annotation arguments) */
        hideAnnotations: Array<String> = emptyArray(),
        /** Hide meta-annotations (--hide-meta-annotation arguments) */
        hideMetaAnnotations: Array<String> = emptyArray(),
        /** If using [showAnnotations], whether to include unannotated */
        showUnannotated: Boolean = false,
        /** Additional arguments to supply */
        extraArguments: Array<String> = emptyArray(),
        /** Whether we should emit Kotlin-style null signatures */
        outputKotlinStyleNulls: Boolean = format.useKotlinStyleNulls(),
        /** Whether we should interpret API files being read as having Kotlin-style nullness types */
        inputKotlinStyleNulls: Boolean = false,
        /** Expected output (stdout and stderr combined). If null, don't check. */
        expectedOutput: String? = null,
        /** Expected fail message and state, if any */
        expectedFail: String? = null,
        /** Optional manifest to load and associate with the codebase */
        @Language("XML")
        manifest: String? = null,
        /** Packages to pre-import (these will therefore NOT be included in emitted stubs, signature files etc */
        importedPackages: List<String> = emptyList(),
        /** Packages to skip emitting signatures/stubs for even if public (typically used for unit tests
         * referencing to classpath classes that aren't part of the definitions and shouldn't be part of the
         * test output; e.g. a test may reference java.lang.Enum but we don't want to start reporting all the
         * public APIs in the java.lang package just because it's indirectly referenced via the "enum" superclass
         */
        skipEmitPackages: List<String> = listOf("java.lang", "java.util", "java.io"),
        /** Whether we should include --showAnnotations=android.annotation.SystemApi */
        includeSystemApiAnnotations: Boolean = false,
        /** Whether we should warn about super classes that are stripped because they are hidden */
        includeStrippedSuperclassWarnings: Boolean = false,
        /** Apply level to XML */
        applyApiLevelsXml: String? = null,
        /** Corresponds to SDK constants file broadcast_actions.txt */
        sdk_broadcast_actions: String? = null,
        /** Corresponds to SDK constants file activity_actions.txt */
        sdk_activity_actions: String? = null,
        /** Corresponds to SDK constants file service_actions.txt */
        sdk_service_actions: String? = null,
        /** Corresponds to SDK constants file categories.txt */
        sdk_categories: String? = null,
        /** Corresponds to SDK constants file features.txt */
        sdk_features: String? = null,
        /** Corresponds to SDK constants file widgets.txt */
        sdk_widgets: String? = null,
        /** Extract annotations and check that the given packages contain the given extracted XML files */
        extractAnnotations: Map<String, String>? = null,
        /** Creates the nullability annotations validator, and check that the report has the given lines (does not define files to be validated) */
        validateNullability: Set<String>? = null,
        /** Enable nullability validation for the listed classes */
        validateNullabilityFromList: String? = null,
        /**
         * Whether to include the signature version in signatures
         */
        includeSignatureVersion: Boolean = false,
        /**
         * List of signature files to convert to JDiff XML and the
         * expected XML output.
         */
        convertToJDiff: List<ConvertData> = emptyList(),
        /**
         * Hook for performing additional initialization of the project
         * directory
         */
        projectSetup: ((File) -> Unit)? = null,
        /** Content of the baseline file to use, if any */
        baseline: String? = null,
        /** If non-null, we expect the baseline file to be updated to this. [baseline] must also be set. */
        updateBaseline: String? = null,
        /** Merge instead of replacing the baseline */
        mergeBaseline: String? = null,

        /** [ARG_BASELINE_API_LINT] */
        baselineApiLint: String? = null,
        /** [ARG_UPDATE_BASELINE_API_LINT] */
        updateBaselineApiLint: String? = null,

        /** [ARG_BASELINE_CHECK_COMPATIBILITY_RELEASED] */
        baselineCheckCompatibilityReleased: String? = null,
        /** [ARG_UPDATE_BASELINE_CHECK_COMPATIBILITY_RELEASED] */
        updateBaselineCheckCompatibilityReleased: String? = null,

        /** [ARG_ERROR_MESSAGE_API_LINT] */
        errorMessageApiLint: String? = null,
        /** [ARG_ERROR_MESSAGE_CHECK_COMPATIBILITY_RELEASED] */
        errorMessageCheckCompatibilityReleased: String? = null,

        /**
         * If non null, enable API lint. If non-blank, a codebase where only new APIs not in the codebase
         * are linted.
         */
        @Language("TEXT")
        apiLint: String? = null,
        /** The source files to pass to the analyzer */
        sourceFiles: Array<TestFile> = emptyArray(),
        /** [ARG_REPEAT_ERRORS_MAX] */
        repeatErrorsMax: Int = 0
    ) {
        // Ensure different API clients don't interfere with each other
        try {
            val method = ApiLookup::class.java.getDeclaredMethod("dispose")
            method.isAccessible = true
            method.invoke(null)
        } catch (ignore: Throwable) {
            ignore.printStackTrace()
        }

        // Ensure that lint infrastructure (for UAST) knows it's dealing with a test
        LintCliClient(LintClient.CLIENT_UNIT_TESTS)

        defaultConfiguration.reset()

        val actualExpectedFail = when {
            expectedFail != null -> expectedFail
            (checkCompatibilityApiReleased != null || checkCompatibilityRemovedApiReleased != null) &&
                    expectedIssues != null && expectedIssues.trim().isNotEmpty() -> {
                "Aborting: Found compatibility problems"
            }

            else -> ""
        }

        // Unit test which checks that a signature file is as expected
        val androidJar = getAndroidJar()

        val project = createProject(*sourceFiles)

        val sourcePathDir = File(project, "src")
        if (!sourcePathDir.isDirectory) {
            sourcePathDir.mkdirs()
        }

        var sourcePath = sourcePathDir.path

        // Make it easy to configure a source path with more than one source root: src and src2
        if (sourceFiles.any { it.targetPath.startsWith("src2") }) {
            sourcePath = sourcePath + File.pathSeparator + sourcePath + "2"
        }

        val sourceList =
            if (signatureSources.isNotEmpty() || signatureSource != null) {
                sourcePathDir.mkdirs()

                // if signatureSource is set, add it to signatureSources.
                val sources = signatureSources.toMutableList()
                signatureSource?.let { sources.add(it) }

                var num = 0
                val args = mutableListOf<String>()
                sources.forEach { file ->
                    val signatureFile = File(
                        project,
                        "load-api${if (++num == 1) "" else num.toString()}.txt"
                    )
                    signatureFile.writeText(file.trimIndent())
                    args.add(signatureFile.path)
                }
                if (!includeStrippedSuperclassWarnings) {
                    args.add(ARG_HIDE)
                    args.add("HiddenSuperclass") // Suppress warning #111
                }
                args.toTypedArray()
            } else if (apiJar != null) {
                sourcePathDir.mkdirs()
                assert(sourceFiles.isEmpty()) { "Shouldn't combine sources with API jar file loads" }
                arrayOf(apiJar.path)
            } else {
                sourceFiles.asSequence().map { File(project, it.targetPath).path }.toList().toTypedArray()
            }

        val classpathArgs: Array<String> = if (classpath != null) {
            val classpathString = classpath
                .map { it.createFile(project) }
                .map { it.path }
                .joinToString(separator = File.pathSeparator) { it }

            arrayOf(ARG_CLASS_PATH, classpathString)
        } else {
            emptyArray()
        }

        val allReportedIssues = StringBuilder()
        val errorSeverityReportedIssues = StringBuilder()
        Reporter.rootFolder = project
        Reporter.reportPrinter = { message, severity ->
            val cleanedUpMessage = cleanupString(message, project).trim()
            if (severity == Severity.ERROR) {
                errorSeverityReportedIssues.append(cleanedUpMessage).append('\n')
            }
            allReportedIssues.append(cleanedUpMessage).append('\n')
        }

        val mergeAnnotationsArgs = if (mergeXmlAnnotations != null) {
            val merged = File(project, "merged-annotations.xml")
            merged.writeText(mergeXmlAnnotations.trimIndent())
            arrayOf(ARG_MERGE_QUALIFIER_ANNOTATIONS, merged.path)
        } else {
            emptyArray()
        }

        val signatureAnnotationsArgs = if (mergeSignatureAnnotations != null) {
            val merged = File(project, "merged-annotations.txt")
            merged.writeText(mergeSignatureAnnotations.trimIndent())
            arrayOf(ARG_MERGE_QUALIFIER_ANNOTATIONS, merged.path)
        } else {
            emptyArray()
        }

        val javaStubAnnotationsArgs = if (mergeJavaStubAnnotations != null) {
            // We need to place the qualifier class into its proper package location
            // to make the parsing machinery happy
            val cls = ClassName(mergeJavaStubAnnotations)
            val pkg = cls.packageName
            val relative = pkg?.replace('.', File.separatorChar) ?: "."
            val merged = File(project, "qualifier/$relative/${cls.className}.java")
            merged.parentFile.mkdirs()
            merged.writeText(mergeJavaStubAnnotations.trimIndent())
            arrayOf(ARG_MERGE_QUALIFIER_ANNOTATIONS, merged.path)
        } else {
            emptyArray()
        }

        val inclusionAnnotationsArgs = if (mergeInclusionAnnotations != null) {
            val cls = ClassName(mergeInclusionAnnotations)
            val pkg = cls.packageName
            val relative = pkg?.replace('.', File.separatorChar) ?: "."
            val merged = File(project, "inclusion/$relative/${cls.className}.java")
            merged.parentFile?.mkdirs()
            merged.writeText(mergeInclusionAnnotations.trimIndent())
            arrayOf(ARG_MERGE_INCLUSION_ANNOTATIONS, merged.path)
        } else {
            emptyArray()
        }

        val apiLintArgs = if (apiLint != null) {
            if (apiLint.isBlank()) {
                arrayOf(ARG_API_LINT)
            } else {
                val file = File(project, "prev-api-lint.txt")
                file.writeText(apiLint.trimIndent())
                arrayOf(ARG_API_LINT, file.path)
            }
        } else {
            emptyArray()
        }

        val checkCompatibilityApiReleasedFile = if (checkCompatibilityApiReleased != null) {
            val jar = File(checkCompatibilityApiReleased)
            if (jar.isFile) {
                jar
            } else {
                val file = File(project, "released-api.txt")
                file.writeText(checkCompatibilityApiReleased.trimIndent())
                file
            }
        } else {
            null
        }

        val checkCompatibilityRemovedApiReleasedFile = if (checkCompatibilityRemovedApiReleased != null) {
            val jar = File(checkCompatibilityRemovedApiReleased)
            if (jar.isFile) {
                jar
            } else {
                val file = File(project, "removed-released-api.txt")
                file.writeText(checkCompatibilityRemovedApiReleased.trimIndent())
                file
            }
        } else {
            null
        }

        val checkCompatibilityBaseApiFile = if (checkCompatibilityBaseApi != null) {
            val maybeFile = File(checkCompatibilityBaseApi)
            if (maybeFile.isFile) {
                maybeFile
            } else {
                val file = File(project, "compatibility-base-api.txt")
                file.writeText(checkCompatibilityBaseApi.trimIndent())
                file
            }
        } else {
            null
        }

        val migrateNullsApiFile = if (migrateNullsApi != null) {
            val jar = File(migrateNullsApi)
            if (jar.isFile) {
                jar
            } else {
                val file = File(project, "stable-api.txt")
                file.writeText(migrateNullsApi.trimIndent())
                file
            }
        } else {
            null
        }

        val manifestFileArgs = if (manifest != null) {
            val file = File(project, "manifest.xml")
            file.writeText(manifest.trimIndent())
            arrayOf(ARG_MANIFEST, file.path)
        } else {
            emptyArray()
        }

        val migrateNullsArguments = if (migrateNullsApiFile != null) {
            arrayOf(ARG_MIGRATE_NULLNESS, migrateNullsApiFile.path)
        } else {
            emptyArray()
        }

        val checkCompatibilityApiReleasedArguments = if (checkCompatibilityApiReleasedFile != null) {
            arrayOf(ARG_CHECK_COMPATIBILITY_API_RELEASED, checkCompatibilityApiReleasedFile.path)
        } else {
            emptyArray()
        }

        val checkCompatibilityBaseApiArguments = if (checkCompatibilityBaseApiFile != null) {
            arrayOf(ARG_CHECK_COMPATIBILITY_BASE_API, checkCompatibilityBaseApiFile.path)
        } else {
            emptyArray()
        }

        val checkCompatibilityRemovedReleasedArguments = if (checkCompatibilityRemovedApiReleasedFile != null) {
            arrayOf(ARG_CHECK_COMPATIBILITY_REMOVED_RELEASED, checkCompatibilityRemovedApiReleasedFile.path)
        } else {
            emptyArray()
        }

        val quiet = if (expectedOutput != null && !extraArguments.contains(ARG_VERBOSE)) {
            // If comparing output, avoid noisy output such as the banner etc
            arrayOf(ARG_QUIET)
        } else {
            emptyArray()
        }

        var proguardFile: File? = null
        val proguardKeepArguments = if (proguard != null) {
            proguardFile = File(project, "proguard.cfg")
            arrayOf(ARG_PROGUARD, proguardFile.path)
        } else {
            emptyArray()
        }

        val showAnnotationArguments = if (showAnnotations.isNotEmpty() || includeSystemApiAnnotations) {
            val args = mutableListOf<String>()
            for (annotation in showAnnotations) {
                args.add(ARG_SHOW_ANNOTATION)
                args.add(annotation)
            }
            if (includeSystemApiAnnotations && !args.contains("android.annotation.SystemApi")) {
                args.add(ARG_SHOW_ANNOTATION)
                args.add("android.annotation.SystemApi")
            }
            if (includeSystemApiAnnotations && !args.contains("android.annotation.TestApi")) {
                args.add(ARG_SHOW_ANNOTATION)
                args.add("android.annotation.TestApi")
            }
            args.toTypedArray()
        } else {
            emptyArray()
        }

        val hideAnnotationArguments = if (hideAnnotations.isNotEmpty()) {
            val args = mutableListOf<String>()
            for (annotation in hideAnnotations) {
                args.add(ARG_HIDE_ANNOTATION)
                args.add(annotation)
            }
            args.toTypedArray()
        } else {
            emptyArray()
        }

        val showForStubPurposesAnnotationArguments = if (showForStubPurposesAnnotations.isNotEmpty()) {
            val args = mutableListOf<String>()
            for (annotation in showForStubPurposesAnnotations) {
                args.add(ARG_SHOW_FOR_STUB_PURPOSES_ANNOTATION)
                args.add(annotation)
            }
            args.toTypedArray()
        } else {
            emptyArray()
        }

        val hideMetaAnnotationArguments = if (hideMetaAnnotations.isNotEmpty()) {
            val args = mutableListOf<String>()
            for (annotation in hideMetaAnnotations) {
                args.add(ARG_HIDE_META_ANNOTATION)
                args.add(annotation)
            }
            args.toTypedArray()
        } else {
            emptyArray()
        }

        val showUnannotatedArgs =
            if (showUnannotated) {
                arrayOf(ARG_SHOW_UNANNOTATED)
            } else {
                emptyArray()
            }

        var removedApiFile: File? = null
        val removedArgs = if (removedApi != null) {
            removedApiFile = temporaryFolder.newFile("removed.txt")
            arrayOf(ARG_REMOVED_API, removedApiFile.path)
        } else {
            emptyArray()
        }

        // Always pass apiArgs and generate API text file in runDriver
        var apiFile: File = newFile("public-api.txt")
        val apiArgs = arrayOf(ARG_API, apiFile.path)

        var apiXmlFile: File? = null
        val apiXmlArgs = if (apiXml != null) {
            apiXmlFile = temporaryFolder.newFile("public-api-xml.txt")
            arrayOf(ARG_XML_API, apiXmlFile.path)
        } else {
            emptyArray()
        }

        var dexApiFile: File? = null
        val dexApiArgs = if (dexApi != null) {
            dexApiFile = temporaryFolder.newFile("public-dex.txt")
            arrayOf(ARG_DEX_API, dexApiFile.path)
        } else {
            emptyArray()
        }

        val subtractApiFile: File?
        val subtractApiArgs = if (subtractApi != null) {
            subtractApiFile = temporaryFolder.newFile("subtract-api.txt")
            subtractApiFile.writeText(subtractApi.trimIndent())
            arrayOf(ARG_SUBTRACT_API, subtractApiFile.path)
        } else {
            emptyArray()
        }

        val convertFiles = mutableListOf<Options.ConvertFile>()
        val convertArgs = if (convertToJDiff.isNotEmpty()) {
            val args = mutableListOf<String>()
            var index = 1
            for (convert in convertToJDiff) {
                val signature = convert.fromApi
                val base = convert.baseApi
                val convertSig = temporaryFolder.newFile("convert-signatures$index.txt")
                convertSig.writeText(signature.trimIndent(), UTF_8)
                val extension = FileFormat.JDIFF.preferredExtension()
                val output = temporaryFolder.newFile("convert-output$index$extension")
                val baseFile = if (base != null) {
                    val baseFile = temporaryFolder.newFile("convert-signatures$index-base.txt")
                    baseFile.writeText(base.trimIndent(), UTF_8)
                    baseFile
                } else {
                    null
                }
                convertFiles += Options.ConvertFile(convertSig, output, baseFile, strip = true)
                index++

                if (baseFile != null) {
                    args +=
                        when {
                            convert.strip -> "-new_api"
                            else -> ARG_CONVERT_NEW_TO_JDIFF
                        }
                    args += baseFile.path
                } else {
                    args +=
                        when {
                            convert.strip -> "-convert2xml"
                            else -> ARG_CONVERT_TO_JDIFF
                        }
                }
                args += convertSig.path
                args += output.path
            }
            args.toTypedArray()
        } else {
            emptyArray()
        }

        var stubsDir: File? = null
        val stubsArgs = if (stubFiles.isNotEmpty()) {
            stubsDir = newFolder("stubs")
            if (docStubs) {
                arrayOf(ARG_DOC_STUBS, stubsDir.path)
            } else {
                arrayOf(ARG_STUBS, stubsDir.path)
            }
        } else {
            emptyArray()
        }

        var stubsSourceListFile: File? = null
        val stubsSourceListArgs = if (stubsSourceList != null) {
            stubsSourceListFile = temporaryFolder.newFile("droiddoc-src-list")
            arrayOf(ARG_STUBS_SOURCE_LIST, stubsSourceListFile.path)
        } else {
            emptyArray()
        }

        var docStubsSourceListFile: File? = null
        val docStubsSourceListArgs = if (docStubsSourceList != null) {
            docStubsSourceListFile = temporaryFolder.newFile("droiddoc-doc-src-list")
            arrayOf(ARG_DOC_STUBS_SOURCE_LIST, docStubsSourceListFile.path)
        } else {
            emptyArray()
        }

        val applyApiLevelsXmlFile: File?
        val applyApiLevelsXmlArgs = if (applyApiLevelsXml != null) {
            ApiLookup::class.java.getDeclaredMethod("dispose").apply { isAccessible = true }.invoke(null)
            applyApiLevelsXmlFile = temporaryFolder.newFile("api-versions.xml")
            applyApiLevelsXmlFile?.writeText(applyApiLevelsXml.trimIndent())
            arrayOf(ARG_APPLY_API_LEVELS, applyApiLevelsXmlFile.path)
        } else {
            emptyArray()
        }

        fun buildBaselineArgs(
            argBaseline: String,
            argUpdateBaseline: String,
            argMergeBaseline: String,
            filename: String,
            baselineContent: String?,
            updateContent: String?,
            merge: Boolean
        ): Pair<Array<String>, File?> {
            if (baselineContent != null) {
                val baselineFile = temporaryFolder.newFile(filename)
                baselineFile?.writeText(baselineContent.trimIndent())
                if (!(updateContent != null || merge)) {
                    return Pair(arrayOf(argBaseline, baselineFile.path), baselineFile)
                } else {
                    return Pair(
                        arrayOf(
                            argBaseline,
                            baselineFile.path,
                            if (mergeBaseline != null) argMergeBaseline else argUpdateBaseline,
                            baselineFile.path
                        ),
                        baselineFile
                    )
                }
            } else {
                return Pair(emptyArray(), null)
            }
        }

        val (baselineArgs, baselineFile) = buildBaselineArgs(
            ARG_BASELINE, ARG_UPDATE_BASELINE, ARG_MERGE_BASELINE, "baseline.txt",
            baseline, updateBaseline, mergeBaseline != null
        )
        val (baselineApiLintArgs, baselineApiLintFile) = buildBaselineArgs(
            ARG_BASELINE_API_LINT, ARG_UPDATE_BASELINE_API_LINT, "",
            "baseline-api-lint.txt",
            baselineApiLint, updateBaselineApiLint, false
        )
        val (baselineCheckCompatibilityReleasedArgs, baselineCheckCompatibilityReleasedFile) = buildBaselineArgs(
            ARG_BASELINE_CHECK_COMPATIBILITY_RELEASED, ARG_UPDATE_BASELINE_CHECK_COMPATIBILITY_RELEASED, "",
            "baseline-check-released.txt",
            baselineCheckCompatibilityReleased, updateBaselineCheckCompatibilityReleased, false
        )

        val importedPackageArgs = mutableListOf<String>()
        importedPackages.forEach {
            importedPackageArgs.add("--stub-import-packages")
            importedPackageArgs.add(it)
        }

        val skipEmitPackagesArgs = mutableListOf<String>()
        skipEmitPackages.forEach {
            skipEmitPackagesArgs.add("--skip-emit-packages")
            skipEmitPackagesArgs.add(it)
        }

        val kotlinPathArgs = findKotlinStdlibPathArgs(sourceList)

        val sdkFilesDir: File?
        val sdkFilesArgs: Array<String>
        if (sdk_broadcast_actions != null ||
            sdk_activity_actions != null ||
            sdk_service_actions != null ||
            sdk_categories != null ||
            sdk_features != null ||
            sdk_widgets != null
        ) {
            val dir = File(project, "sdk-files")
            sdkFilesArgs = arrayOf(ARG_SDK_VALUES, dir.path)
            sdkFilesDir = dir
        } else {
            sdkFilesArgs = emptyArray()
            sdkFilesDir = null
        }

        val extractedAnnotationsZip: File?
        val extractAnnotationsArgs = if (extractAnnotations != null) {
            extractedAnnotationsZip = temporaryFolder.newFile("extracted-annotations.zip")
            arrayOf(ARG_EXTRACT_ANNOTATIONS, extractedAnnotationsZip.path)
        } else {
            extractedAnnotationsZip = null
            emptyArray()
        }

        val validateNullabilityTxt: File?
        val validateNullabilityArgs = if (validateNullability != null) {
            validateNullabilityTxt = temporaryFolder.newFile("validate-nullability.txt")
            arrayOf(
                ARG_NULLABILITY_WARNINGS_TXT, validateNullabilityTxt.path,
                ARG_NULLABILITY_ERRORS_NON_FATAL // for testing, report on errors instead of throwing
            )
        } else {
            validateNullabilityTxt = null
            emptyArray()
        }
        val validateNullabilityFromListFile: File?
        val validateNullabilityFromListArgs = if (validateNullabilityFromList != null) {
            validateNullabilityFromListFile = temporaryFolder.newFile("validate-nullability-classes.txt")
            validateNullabilityFromListFile.writeText(validateNullabilityFromList)
            arrayOf(
                ARG_VALIDATE_NULLABILITY_FROM_LIST, validateNullabilityFromListFile.path
            )
        } else {
            emptyArray()
        }

        val errorMessageApiLintArgs = buildOptionalArgs(errorMessageApiLint) {
            arrayOf(ARG_ERROR_MESSAGE_API_LINT, it)
        }
        val errorMessageCheckCompatibilityReleasedArgs = buildOptionalArgs(errorMessageCheckCompatibilityReleased) {
            arrayOf(ARG_ERROR_MESSAGE_CHECK_COMPATIBILITY_RELEASED, it)
        }

        val repeatErrorsMaxArgs = if (repeatErrorsMax > 0) {
            arrayOf(ARG_REPEAT_ERRORS_MAX, repeatErrorsMax.toString())
        } else {
            emptyArray()
        }

        // Run optional additional setup steps on the project directory
        projectSetup?.invoke(project)

        val actualOutput = runDriver(
            ARG_NO_COLOR,
            ARG_NO_BANNER,

            // Tell metalava where to store temp folder: place them under the
            // test root folder such that we clean up the output strings referencing
            // paths to the temp folder
            "--temp-folder",
            newFolder("temp").path,

            // Annotation generation temporarily turned off by default while integrating with
            // SDK builds; tests need these
            ARG_INCLUDE_ANNOTATIONS,

            ARG_SOURCE_PATH,
            sourcePath,
            ARG_CLASS_PATH,
            androidJar.path,
            *classpathArgs,
            *kotlinPathArgs,
            *removedArgs,
            *apiArgs,
            *apiXmlArgs,
            *dexApiArgs,
            *subtractApiArgs,
            *stubsArgs,
            *stubsSourceListArgs,
            *docStubsSourceListArgs,
            "$ARG_OUTPUT_KOTLIN_NULLS=${if (outputKotlinStyleNulls) "yes" else "no"}",
            "$ARG_INPUT_KOTLIN_NULLS=${if (inputKotlinStyleNulls) "yes" else "no"}",
            "$ARG_INCLUDE_SIG_VERSION=${if (includeSignatureVersion) "yes" else "no"}",
            *quiet,
            *mergeAnnotationsArgs,
            *signatureAnnotationsArgs,
            *javaStubAnnotationsArgs,
            *inclusionAnnotationsArgs,
            *migrateNullsArguments,
            *checkCompatibilityApiReleasedArguments,
            *checkCompatibilityBaseApiArguments,
            *checkCompatibilityRemovedReleasedArguments,
            *proguardKeepArguments,
            *manifestFileArgs,
            *convertArgs,
            *applyApiLevelsXmlArgs,
            *baselineArgs,
            *baselineApiLintArgs,
            *baselineCheckCompatibilityReleasedArgs,
            *showAnnotationArguments,
            *hideAnnotationArguments,
            *hideMetaAnnotationArguments,
            *showForStubPurposesAnnotationArguments,
            *showUnannotatedArgs,
            *apiLintArgs,
            *sdkFilesArgs,
            *importedPackageArgs.toTypedArray(),
            *skipEmitPackagesArgs.toTypedArray(),
            *extractAnnotationsArgs,
            *validateNullabilityArgs,
            *validateNullabilityFromListArgs,
            format.outputFlag(),
            *sourceList,
            *extraArguments,
            *errorMessageApiLintArgs,
            *errorMessageCheckCompatibilityReleasedArgs,
            *repeatErrorsMaxArgs,
            expectedFail = actualExpectedFail
        )

        if (expectedIssues != null || allReportedIssues.toString() != "") {
            assertEquals(
                expectedIssues?.trimIndent()?.trim() ?: "",
                cleanupString(allReportedIssues.toString(), project)
            )
        }
        if (errorSeverityExpectedIssues != null) {
            assertEquals(
                errorSeverityExpectedIssues.trimIndent().trim(),
                cleanupString(errorSeverityReportedIssues.toString(), project)
            )
        }

        if (expectedOutput != null) {
            assertEquals(expectedOutput.trimIndent().trim(), actualOutput.trim())
        }

        if (api != null) {
            assertTrue("${apiFile.path} does not exist even though --api was used", apiFile.exists())
            val actualText = readFile(apiFile, stripBlankLines, trim)
            assertEquals(prepareExpectedApi(api, format), actualText)
            // Make sure we can read back the files we write
            ApiFile.parseApi(apiFile, options.outputKotlinStyleNulls)
        }

        if (apiXml != null && apiXmlFile != null) {
            assertTrue(
                "${apiXmlFile.path} does not exist even though $ARG_XML_API was used",
                apiXmlFile.exists()
            )
            val actualText = readFile(apiXmlFile, stripBlankLines, trim)
            assertEquals(stripComments(apiXml, DOT_XML, stripLineComments = false).trimIndent(), actualText)
            // Make sure we can read back the files we write
            parseDocument(apiXmlFile.readText(UTF_8), false)
        }

        fun checkBaseline(
            arg: String,
            baselineContent: String?,
            updateBaselineContent: String?,
            mergeBaselineContent: String?,
            file: File?
        ) {
            if (file == null) {
                return
            }
            assertTrue(
                "${file.path} does not exist even though $arg was used",
                file.exists()
            )
            val actualText = readFile(file, stripBlankLines, trim)

            // Compare against:
            // If "merged baseline" is set, use it.
            // If "update baseline" is set, use it.
            // Otherwise, the original baseline.
            val sourceFile = mergeBaselineContent ?: updateBaselineContent ?: baselineContent ?: ""
            assertEquals(stripComments(sourceFile, DOT_XML, stripLineComments = false).trimIndent(), actualText)
        }
        checkBaseline(ARG_BASELINE, baseline, updateBaseline, mergeBaseline, baselineFile)
        checkBaseline(ARG_BASELINE_API_LINT, baselineApiLint, updateBaselineApiLint, null, baselineApiLintFile)
        checkBaseline(
            ARG_BASELINE_CHECK_COMPATIBILITY_RELEASED, baselineCheckCompatibilityReleased,
            updateBaselineCheckCompatibilityReleased, null, baselineCheckCompatibilityReleasedFile
        )

        if (convertFiles.isNotEmpty()) {
            for (i in convertToJDiff.indices) {
                val expected = convertToJDiff[i].outputFile
                val converted = convertFiles[i].outputFile
                assertTrue(
                    "${converted.path} does not exist even though $ARG_CONVERT_TO_JDIFF was used",
                    converted.exists()
                )
                val actualText = readFile(converted, stripBlankLines, trim)
                if (actualText.contains("<api")) {
                    parseDocument(actualText, false)
                }
                assertEquals(
                    stripComments(expected, DOT_XML, stripLineComments = false).trimIndent(),
                    actualText
                )
                // Make sure we can read back the files we write
            }
        }

        if (dexApi != null && dexApiFile != null) {
            assertTrue(
                "${dexApiFile.path} does not exist even though --dex-api was used",
                dexApiFile.exists()
            )
            val actualText = readFile(dexApiFile, stripBlankLines, trim)
            assertEquals(
                stripComments(dexApi, DOT_TXT, stripLineComments = false).trimIndent(),
                actualText
            )
        }

        if (removedApi != null && removedApiFile != null) {
            assertTrue(
                "${removedApiFile.path} does not exist even though --removed-api was used",
                removedApiFile.exists()
            )
            val actualText = readFile(removedApiFile, stripBlankLines, trim)
            assertEquals(prepareExpectedApi(removedApi, format), actualText)
            // Make sure we can read back the files we write
            ApiFile.parseApi(removedApiFile, options.outputKotlinStyleNulls)
        }

        if (proguard != null && proguardFile != null) {
            val expectedProguard = readFile(proguardFile)
            assertTrue(
                "${proguardFile.path} does not exist even though --proguard was used",
                proguardFile.exists()
            )
            assertEquals(
                stripComments(proguard, DOT_TXT, stripLineComments = false).trimIndent(),
                expectedProguard.trim()
            )
        }

        if (sdk_broadcast_actions != null) {
            val actual = readFile(File(sdkFilesDir, "broadcast_actions.txt"), stripBlankLines, trim)
            assertEquals(sdk_broadcast_actions.trimIndent().trim(), actual.trim())
        }

        if (sdk_activity_actions != null) {
            val actual = readFile(File(sdkFilesDir, "activity_actions.txt"), stripBlankLines, trim)
            assertEquals(sdk_activity_actions.trimIndent().trim(), actual.trim())
        }

        if (sdk_service_actions != null) {
            val actual = readFile(File(sdkFilesDir, "service_actions.txt"), stripBlankLines, trim)
            assertEquals(sdk_service_actions.trimIndent().trim(), actual.trim())
        }

        if (sdk_categories != null) {
            val actual = readFile(File(sdkFilesDir, "categories.txt"), stripBlankLines, trim)
            assertEquals(sdk_categories.trimIndent().trim(), actual.trim())
        }

        if (sdk_features != null) {
            val actual = readFile(File(sdkFilesDir, "features.txt"), stripBlankLines, trim)
            assertEquals(sdk_features.trimIndent().trim(), actual.trim())
        }

        if (sdk_widgets != null) {
            val actual = readFile(File(sdkFilesDir, "widgets.txt"), stripBlankLines, trim)
            assertEquals(sdk_widgets.trimIndent().trim(), actual.trim())
        }

        if (extractAnnotations != null && extractedAnnotationsZip != null) {
            assertTrue(
                "Using --extract-annotations but $extractedAnnotationsZip was not created",
                extractedAnnotationsZip.isFile
            )
            for ((pkg, xml) in extractAnnotations) {
                assertPackageXml(pkg, extractedAnnotationsZip, xml)
            }
        }

        if (validateNullabilityTxt != null) {
            assertTrue(
                "Using $ARG_NULLABILITY_WARNINGS_TXT but $validateNullabilityTxt was not created",
                validateNullabilityTxt.isFile
            )
            val actualReport =
                Files.asCharSource(validateNullabilityTxt, UTF_8).readLines().map(String::trim).toSet()
            assertEquals(validateNullability, actualReport)
        }

        if (stubFiles.isNotEmpty()) {
            for (expected in stubFiles) {
                val actual = File(stubsDir!!, expected.targetRelativePath)
                if (!actual.exists()) {
                    val existing = stubsDir.walkTopDown()
                        .filter { it.isFile }
                        .map { it.path }
                        .joinToString("\n  ")
                    throw FileNotFoundException(
                        "Could not find a generated stub for ${expected.targetRelativePath}. " +
                                "Found these files: \n  $existing"
                    )
                }
                val actualContents = readFile(actual, stripBlankLines, trim)
                val stubSource = if (sourceFiles.isEmpty()) "text" else "source"
                val message =
                    "Generated from-$stubSource stub contents does not match expected contents"
                assertEquals(message, expected.contents, actualContents)
            }
        }

        if (stubsSourceList != null && stubsSourceListFile != null) {
            assertTrue(
                "${stubsSourceListFile.path} does not exist even though --write-stubs-source-list was used",
                stubsSourceListFile.exists()
            )
            val actualText = cleanupString(readFile(stubsSourceListFile, stripBlankLines, trim), project)
                // To make golden files look better put one entry per line instead of a single
                // space separated line
                .replace(' ', '\n')
            assertEquals(
                stripComments(stubsSourceList, DOT_TXT, stripLineComments = false).trimIndent(),
                actualText
            )
        }

        if (docStubsSourceList != null && docStubsSourceListFile != null) {
            assertTrue(
                "${docStubsSourceListFile.path} does not exist even though --write-stubs-source-list was used",
                docStubsSourceListFile.exists()
            )
            val actualText = cleanupString(readFile(docStubsSourceListFile, stripBlankLines, trim), project)
                // To make golden files look better put one entry per line instead of a single
                // space separated line
                .replace(' ', '\n')
            assertEquals(
                stripComments(docStubsSourceList, DOT_TXT, stripLineComments = false).trimIndent(),
                actualText
            )
        }

        if (checkCompilation && stubsDir != null) {
            val generated = gatherSources(listOf(stubsDir)).asSequence().map { it.path }.toList().toTypedArray()

            // Also need to include on the compile path annotation classes referenced in the stubs
            val extraAnnotationsDir = File("stub-annotations/src/main/java")
            if (!extraAnnotationsDir.isDirectory) {
                fail("Couldn't find $extraAnnotationsDir: Is the pwd set to the root of the metalava source code?")
                fail("Couldn't find $extraAnnotationsDir: Is the pwd set to the root of an Android source tree?")
            }
            val extraAnnotations =
                gatherSources(listOf(extraAnnotationsDir)).asSequence().map { it.path }.toList().toTypedArray()

            if (!runCommand(
                    "${getJdkPath()}/bin/javac",
                    arrayOf(
                        "-d", project.path, *generated, *extraAnnotations
                    )
                )
            ) {
                fail("Couldn't compile stub file -- compilation problems")
                return
            }
        }

        if (CHECK_JDIFF && apiXmlFile != null && convertToJDiff.isNotEmpty()) {
            // TODO: Parse the XML file with jdiff too
        }
    }

    /** Checks that the given zip annotations file contains the given XML package contents */
    private fun assertPackageXml(pkg: String, output: File, @Language("XML") expected: String) {
        assertNotNull(output)
        assertTrue(output.exists())
        val url = URL(
            "jar:" + SdkUtils.fileToUrlString(output) + "!/" + pkg.replace('.', '/') +
                    "/annotations.xml"
        )
        val stream = url.openStream()
        try {
            val bytes = ByteStreams.toByteArray(stream)
            assertNotNull(bytes)
            val xml = String(bytes, UTF_8).replace("\r\n", "\n")
            assertEquals(expected.trimIndent().trim(), xml.trimIndent().trim())
        } finally {
            Closeables.closeQuietly(stream)
        }
    }

    /** Hides path prefixes from /tmp folders used by the testing infrastructure */
    private fun cleanupString(string: String, project: File?, dropTestRoot: Boolean = false): String {
        var s = string

        if (project != null) {
            s = s.replace(project.path, "TESTROOT")
            s = s.replace(project.canonicalPath, "TESTROOT")
        }

        s = s.replace(temporaryFolder.root.path, "TESTROOT")

        val tmp = System.getProperty("java.io.tmpdir")
        if (tmp != null) {
            s = s.replace(tmp, "TEST")
        }

        s = s.trim()

        if (dropTestRoot) {
            s = s.replace("TESTROOT/", "")
        }

        return s
    }

    private fun runCommand(executable: String, args: Array<String>): Boolean {
        try {
            val logger = StdLogger(StdLogger.Level.ERROR)
            val processExecutor = DefaultProcessExecutor(logger)
            val processInfo = ProcessInfoBuilder()
                .setExecutable(executable)
                .addArgs(args)
                .createProcess()

            val processOutputHandler = LoggedProcessOutputHandler(logger)
            val result = processExecutor.execute(processInfo, processOutputHandler)

            result.rethrowFailure().assertNormalExitValue()
        } catch (e: ProcessException) {
            fail("Failed to run $executable (${e.message}): not verifying this API on the old doclava engine")
            return false
        }
        return true
    }

    /** Strip comments, trim indent, and add a signature format version header if one is missing */
    private fun prepareExpectedApi(expectedApi: String, format: FileFormat): String {
        val header = format.header()

        return stripComments(expectedApi, DOT_TXT, stripLineComments = false)
            .trimIndent()
            .let {
                if (header != null && !it.startsWith("// Signature format:")) header + it else it
            }
            .trim()
    }

    companion object {
        private const val API_LEVEL = 31

        private fun getAndroidJarFromEnv(apiLevel: Int): File {
            val sdkRoot = System.getenv("ANDROID_SDK_ROOT")
                ?: System.getenv("ANDROID_HOME")
                ?: error("Expected ANDROID_SDK_ROOT to be set")
            val jar = File(sdkRoot, "platforms/android-$apiLevel/android.jar")
            if (!jar.exists()) {
                error("Missing ${jar.absolutePath} file in the SDK")
            }
            return jar
        }

        fun getAndroidJar(apiLevel: Int = API_LEVEL): File {
            val localFile = File("../../prebuilts/sdk/$apiLevel/public/android.jar")
            if (localFile.exists()) {
                return localFile
            } else {
                val androidJar = File("../../prebuilts/sdk/$apiLevel/android.jar")
                if (androidJar.exists()) return androidJar
                return getAndroidJarFromEnv(apiLevel)
            }
        }

        @JvmStatic
        protected fun readFile(file: File, stripBlankLines: Boolean = false, trim: Boolean = false): String {
            var apiLines: List<String> = Files.asCharSource(file, UTF_8).readLines()
            if (stripBlankLines) {
                apiLines = apiLines.asSequence().filter { it.isNotBlank() }.toList()
            }
            var apiText = apiLines.joinToString(separator = "\n") { it }
            if (trim) {
                apiText = apiText.trim()
            }
            return apiText
        }
    }
}

/**
 * A slight modification of com.android.tools.lint.checks.infrastructure.findKotlinStdLibPath
 * that prints program name on error. Returns the paths as metalava args expected by Options.
 */
fun findKotlinStdlibPathArgs(sources: Array<String>): Array<String> {
    val classPath: String = System.getProperty("java.class.path")
    val paths = mutableListOf<String>()
    for (path in classPath.split(':')) {
        val file = File(path)
        val name = file.name
        if (name.startsWith("kotlin-stdlib") ||
            name.startsWith("kotlin-reflect") ||
            name.startsWith("kotlin-script-runtime")
        ) {
            paths.add(file.path)
        }
    }
    if (paths.isEmpty()) {
        error("Did not find kotlin-stdlib-jre8 in $PROGRAM_NAME classpath: $classPath")
    }
    val kotlinPathArgs =
        if (paths.isNotEmpty() &&
            sources.asSequence().any { it.endsWith(DOT_KT) }
        ) {
            arrayOf(ARG_CLASS_PATH, paths.joinToString(separator = File.pathSeparator) { it })
        } else {
            emptyArray()
        }
    return kotlinPathArgs
}

val intRangeAnnotationSource: TestFile = java(
    """
        package android.annotation;
        import java.lang.annotation.*;
        import static java.lang.annotation.ElementType.*;
        import static java.lang.annotation.RetentionPolicy.SOURCE;
        @Retention(SOURCE)
        @Target({METHOD,PARAMETER,FIELD,LOCAL_VARIABLE,ANNOTATION_TYPE})
        public @interface IntRange {
            long from() default Long.MIN_VALUE;
            long to() default Long.MAX_VALUE;
        }
        """
).indented()

val intDefAnnotationSource: TestFile = java(
    """
    package android.annotation;
    import java.lang.annotation.Retention;
    import java.lang.annotation.RetentionPolicy;
    import java.lang.annotation.Target;
    import static java.lang.annotation.ElementType.*;
    import static java.lang.annotation.RetentionPolicy.SOURCE;
    @Retention(SOURCE)
    @Target({ANNOTATION_TYPE})
    public @interface IntDef {
        int[] value() default {};
        boolean flag() default false;
    }
    """
).indented()

val longDefAnnotationSource: TestFile = java(
    """
    package android.annotation;
    import java.lang.annotation.Retention;
    import java.lang.annotation.RetentionPolicy;
    import java.lang.annotation.Target;
    import static java.lang.annotation.ElementType.*;
    import static java.lang.annotation.RetentionPolicy.SOURCE;
    @Retention(SOURCE)
    @Target({ANNOTATION_TYPE})
    public @interface LongDef {
        long[] value() default {};
        boolean flag() default false;
    }
    """
).indented()

@Suppress("ConstantConditionIf")
val nonNullSource: TestFile = java(
    """
    package android.annotation;
    import java.lang.annotation.Retention;
    import java.lang.annotation.Target;

    import static java.lang.annotation.ElementType.FIELD;
    import static java.lang.annotation.ElementType.METHOD;
    import static java.lang.annotation.ElementType.PARAMETER;
    import static java.lang.annotation.RetentionPolicy.SOURCE;
    /**
     * Denotes that a parameter, field or method return value can never be null.
     * @paramDoc This value must never be {@code null}.
     * @returnDoc This value will never be {@code null}.
     * @hide
     */
    @SuppressWarnings({"WeakerAccess", "JavaDoc"})
    @Retention(SOURCE)
    @Target({METHOD, PARAMETER, FIELD${if (SUPPORT_TYPE_USE_ANNOTATIONS) ", TYPE_USE" else ""}})
    public @interface NonNull {
    }
    """
).indented()

val libcoreNonNullSource: TestFile = java(
    """
    package libcore.util;
    import static java.lang.annotation.ElementType.*;
    import static java.lang.annotation.RetentionPolicy.SOURCE;
    import java.lang.annotation.*;
    @Documented
    @Retention(SOURCE)
    @Target({TYPE_USE})
    public @interface NonNull {
    }
    """
).indented()

val libcoreNullFromTypeParamSource: TestFile = java(
    """
    package libcore.util;
    import static java.lang.annotation.ElementType.*;
    import static java.lang.annotation.RetentionPolicy.SOURCE;
    import java.lang.annotation.*;
    @Documented
    @Retention(SOURCE)
    @Target({TYPE_USE})
    public @interface NullFromTypeParam {
    }
    """
).indented()

val libcoreNullableSource: TestFile = java(
    """
    package libcore.util;
    import static java.lang.annotation.ElementType.*;
    import static java.lang.annotation.RetentionPolicy.SOURCE;
    import java.lang.annotation.*;
    @Documented
    @Retention(SOURCE)
    @Target({TYPE_USE})
    public @interface Nullable {
    }
    """
).indented()

val requiresPermissionSource: TestFile = java(
    """
    package android.annotation;
    import java.lang.annotation.*;
    import static java.lang.annotation.ElementType.*;
    import static java.lang.annotation.RetentionPolicy.SOURCE;
    @Retention(SOURCE)
    @Target({ANNOTATION_TYPE,METHOD,CONSTRUCTOR,FIELD,PARAMETER})
    public @interface RequiresPermission {
        String value() default "";
        String[] allOf() default {};
        String[] anyOf() default {};
        boolean conditional() default false;
        @Target({FIELD, METHOD, PARAMETER})
        @interface Read {
            RequiresPermission value() default @RequiresPermission;
        }
        @Target({FIELD, METHOD, PARAMETER})
        @interface Write {
            RequiresPermission value() default @RequiresPermission;
        }
    }
    """
).indented()

val requiresFeatureSource: TestFile = java(
    """
    package android.annotation;
    import java.lang.annotation.*;
    import static java.lang.annotation.ElementType.*;
    import static java.lang.annotation.RetentionPolicy.SOURCE;
    @Retention(SOURCE)
    @Target({TYPE,FIELD,METHOD,CONSTRUCTOR})
    public @interface RequiresFeature {
        String value();
    }
    """
).indented()

val requiresApiSource: TestFile = java(
    """
    package androidx.annotation;
    import java.lang.annotation.*;
    import static java.lang.annotation.ElementType.*;
    import static java.lang.annotation.RetentionPolicy.SOURCE;
    @Retention(SOURCE)
    @Target({TYPE,FIELD,METHOD,CONSTRUCTOR})
    public @interface RequiresApi {
        int value() default 1;
        int api() default 1;
    }
    """
).indented()

val sdkConstantSource: TestFile = java(
    """
    package android.annotation;
    import java.lang.annotation.*;
    @Target({ ElementType.FIELD })
    @Retention(RetentionPolicy.SOURCE)
    public @interface SdkConstant {
        enum SdkConstantType {
            ACTIVITY_INTENT_ACTION, BROADCAST_INTENT_ACTION, SERVICE_ACTION, INTENT_CATEGORY, FEATURE
        }
        SdkConstantType value();
    }
    """
).indented()

val broadcastBehaviorSource: TestFile = java(
    """
    package android.annotation;
    import java.lang.annotation.*;
    /** @hide */
    @Target({ ElementType.FIELD })
    @Retention(RetentionPolicy.SOURCE)
    public @interface BroadcastBehavior {
        boolean explicitOnly() default false;
        boolean registeredOnly() default false;
        boolean includeBackground() default false;
        boolean protectedBroadcast() default false;
    }
    """
).indented()

@Suppress("ConstantConditionIf")
val nullableSource: TestFile = java(
    """
    package android.annotation;
    import java.lang.annotation.*;
    import static java.lang.annotation.ElementType.*;
    import static java.lang.annotation.RetentionPolicy.SOURCE;
    /**
     * Denotes that a parameter, field or method return value can be null.
     * @paramDoc This value may be {@code null}.
     * @returnDoc This value may be {@code null}.
     * @hide
     */
    @SuppressWarnings({"WeakerAccess", "JavaDoc"})
    @Retention(SOURCE)
    @Target({METHOD, PARAMETER, FIELD${if (SUPPORT_TYPE_USE_ANNOTATIONS) ", TYPE_USE" else ""}})
    public @interface Nullable {
    }
    """
).indented()

val androidxNonNullSource: TestFile = java(
    """
    package androidx.annotation;
    import java.lang.annotation.*;
    import static java.lang.annotation.ElementType.*;
    import static java.lang.annotation.RetentionPolicy.SOURCE;
    @SuppressWarnings("WeakerAccess")
    @Retention(SOURCE)
    @Target({METHOD, PARAMETER, FIELD, LOCAL_VARIABLE, TYPE_USE, ANNOTATION_TYPE, PACKAGE})
    public @interface NonNull {
    }
    """
).indented()

val androidxNullableSource: TestFile = java(
    """
    package androidx.annotation;
    import java.lang.annotation.*;
    import static java.lang.annotation.ElementType.*;
    import static java.lang.annotation.RetentionPolicy.SOURCE;
    @SuppressWarnings("WeakerAccess")
    @Retention(SOURCE)
    @Target({METHOD, PARAMETER, FIELD, LOCAL_VARIABLE, TYPE_USE, ANNOTATION_TYPE, PACKAGE})
    public @interface Nullable {
    }
    """
).indented()

val recentlyNonNullSource: TestFile = java(
    """
    package androidx.annotation;
    import java.lang.annotation.*;
    import static java.lang.annotation.ElementType.*;
    import static java.lang.annotation.RetentionPolicy.SOURCE;
    @SuppressWarnings("WeakerAccess")
    @Retention(SOURCE)
    @Target({METHOD, PARAMETER, FIELD, TYPE_USE})
    public @interface RecentlyNonNull {
    }
    """
).indented()

val recentlyNullableSource: TestFile = java(
    """
    package androidx.annotation;
    import java.lang.annotation.*;
    import static java.lang.annotation.ElementType.*;
    import static java.lang.annotation.RetentionPolicy.SOURCE;
    @SuppressWarnings("WeakerAccess")
    @Retention(SOURCE)
    @Target({METHOD, PARAMETER, FIELD, TYPE_USE})
    public @interface RecentlyNullable {
    }
    """
).indented()

val androidxIntRangeSource: TestFile = java(
    """
    package androidx.annotation;
    import java.lang.annotation.*;
    import static java.lang.annotation.ElementType.*;
    import static java.lang.annotation.RetentionPolicy.SOURCE;
    @Retention(CLASS)
    @Target({METHOD, PARAMETER, FIELD, LOCAL_VARIABLE, ANNOTATION_TYPE})
    public @interface IntRange {
        long from() default Long.MIN_VALUE;
        long to() default Long.MAX_VALUE;
    }
    """
).indented()

val supportParameterName: TestFile = java(
    """
    package androidx.annotation;
    import java.lang.annotation.*;
    import static java.lang.annotation.ElementType.*;
    import static java.lang.annotation.RetentionPolicy.SOURCE;
    @SuppressWarnings("WeakerAccess")
    @Retention(SOURCE)
    @Target({METHOD, PARAMETER, FIELD})
    public @interface ParameterName {
        String value();
    }
    """
).indented()

val supportDefaultValue: TestFile = java(
    """
    package androidx.annotation;
    import java.lang.annotation.*;
    import static java.lang.annotation.ElementType.*;
    import static java.lang.annotation.RetentionPolicy.SOURCE;
    @SuppressWarnings("WeakerAccess")
    @Retention(SOURCE)
    @Target({METHOD, PARAMETER, FIELD})
    public @interface DefaultValue {
        String value();
    }
    """
).indented()

val uiThreadSource: TestFile = java(
    """
    package androidx.annotation;
    import java.lang.annotation.*;
    import static java.lang.annotation.ElementType.*;
    import static java.lang.annotation.RetentionPolicy.SOURCE;
    /**
     * Denotes that the annotated method or constructor should only be called on the
     * UI thread. If the annotated element is a class, then all methods in the class
     * should be called on the UI thread.
     * @memberDoc This method must be called on the thread that originally created
     *            this UI element. This is typically the main thread of your app.
     * @classDoc Methods in this class must be called on the thread that originally created
     *            this UI element, unless otherwise noted. This is typically the
     *            main thread of your app. * @hide
     */
    @SuppressWarnings({"WeakerAccess", "JavaDoc"})
    @Retention(SOURCE)
    @Target({METHOD,CONSTRUCTOR,TYPE,PARAMETER})
    public @interface UiThread {
    }
    """
).indented()

val workerThreadSource: TestFile = java(
    """
    package androidx.annotation;
    import java.lang.annotation.*;
    import static java.lang.annotation.ElementType.*;
    import static java.lang.annotation.RetentionPolicy.SOURCE;
    /**
     * @memberDoc This method may take several seconds to complete, so it should
     *            only be called from a worker thread.
     * @classDoc Methods in this class may take several seconds to complete, so it should
     *            only be called from a worker thread unless otherwise noted.
     * @hide
     */
    @SuppressWarnings({"WeakerAccess", "JavaDoc"})
    @Retention(SOURCE)
    @Target({METHOD,CONSTRUCTOR,TYPE,PARAMETER})
    public @interface WorkerThread {
    }
    """
).indented()

val suppressLintSource: TestFile = java(
    """
    package android.annotation;

    import static java.lang.annotation.ElementType.*;
    import java.lang.annotation.*;
    @Target({TYPE, FIELD, METHOD, PARAMETER, CONSTRUCTOR, LOCAL_VARIABLE})
    @Retention(RetentionPolicy.CLASS)
    public @interface SuppressLint {
        String[] value();
    }
    """
).indented()

val systemServiceSource: TestFile = java(
    """
    package android.annotation;
    import static java.lang.annotation.ElementType.TYPE;
    import static java.lang.annotation.RetentionPolicy.SOURCE;
    import java.lang.annotation.*;
    @Retention(SOURCE)
    @Target(TYPE)
    public @interface SystemService {
        String value();
    }
    """
).indented()

val systemApiSource: TestFile = java(
    """
    package android.annotation;
    import static java.lang.annotation.ElementType.*;
    import java.lang.annotation.*;
    @Target({TYPE, FIELD, METHOD, CONSTRUCTOR, ANNOTATION_TYPE, PACKAGE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface SystemApi {
    }
    """
).indented()

val testApiSource: TestFile = java(
    """
    package android.annotation;
    import static java.lang.annotation.ElementType.*;
    import java.lang.annotation.*;
    @Target({TYPE, FIELD, METHOD, CONSTRUCTOR, ANNOTATION_TYPE, PACKAGE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface TestApi {
    }
    """
).indented()

val widgetSource: TestFile = java(
    """
    package android.annotation;
    import java.lang.annotation.*;
    @Target({ ElementType.TYPE })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Widget {
    }
    """
).indented()

val restrictToSource: TestFile = kotlin(
    """
    package androidx.annotation

    import androidx.annotation.RestrictTo.Scope
    import java.lang.annotation.ElementType.*

    @MustBeDocumented
    @kotlin.annotation.Retention(AnnotationRetention.BINARY)
    @Target(
        AnnotationTarget.ANNOTATION_CLASS,
        AnnotationTarget.CLASS,
        AnnotationTarget.FUNCTION,
        AnnotationTarget.PROPERTY_GETTER,
        AnnotationTarget.PROPERTY_SETTER,
        AnnotationTarget.CONSTRUCTOR,
        AnnotationTarget.FIELD,
        AnnotationTarget.FILE
    )
    // Needed due to Kotlin's lack of PACKAGE annotation target
    // https://youtrack.jetbrains.com/issue/KT-45921
    @Suppress("DEPRECATED_JAVA_ANNOTATION")
    @java.lang.annotation.Target(ANNOTATION_TYPE, TYPE, METHOD, CONSTRUCTOR, FIELD, PACKAGE)
    public annotation class RestrictTo(vararg val value: Scope) {
        public enum class Scope {
            LIBRARY,
            LIBRARY_GROUP,
            LIBRARY_GROUP_PREFIX,
            @Deprecated("Use LIBRARY_GROUP_PREFIX instead.")
            GROUP_ID,
            TESTS,
            SUBCLASSES,
        }
    }
    """
).indented()

val visibleForTestingSource: TestFile = java(
    """
    package androidx.annotation;
    import static java.lang.annotation.RetentionPolicy.CLASS;
    import java.lang.annotation.Retention;
    @Retention(CLASS)
    @SuppressWarnings("WeakerAccess")
    public @interface VisibleForTesting {
        int otherwise() default PRIVATE;
        int PRIVATE = 2;
        int PACKAGE_PRIVATE = 3;
        int PROTECTED = 4;
        int NONE = 5;
    }
    """
).indented()

val columnSource: TestFile = java(
    """
    package android.provider;

    import static java.lang.annotation.ElementType.FIELD;
    import static java.lang.annotation.RetentionPolicy.RUNTIME;

    import android.content.ContentProvider;
    import android.content.ContentValues;
    import android.database.Cursor;

    import java.lang.annotation.Documented;
    import java.lang.annotation.Retention;
    import java.lang.annotation.Target;

    @Documented
    @Retention(RUNTIME)
    @Target({FIELD})
    public @interface Column {
        int value();
        boolean readOnly() default false;
    }
    """
).indented()

val publishedApiSource: TestFile = kotlin(
    """
    /**
     * When applied to a class or a member with internal visibility allows to use it from public inline functions and
     * makes it effectively public.
     *
     * Public inline functions cannot use non-public API, since if they are inlined, those non-public API references
     * would violate access restrictions at a call site (https://kotlinlang.org/docs/reference/inline-functions.html#public-inline-restrictions).
     *
     * To overcome this restriction an `internal` declaration can be annotated with the `@PublishedApi` annotation:
     * - this allows to call that declaration from public inline functions;
     * - the declaration becomes effectively public, and this should be considered with respect to binary compatibility maintaining.
     */
    @Target(AnnotationTarget.CLASS, AnnotationTarget.CONSTRUCTOR, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
    @Retention(AnnotationRetention.BINARY)
    @MustBeDocumented
    @SinceKotlin("1.1")
    public annotation class PublishedApi
    """
).indented()

val deprecatedForSdkSource: TestFile = java(
    """
    package android.annotation;
    import static java.lang.annotation.RetentionPolicy.SOURCE;
    import java.lang.annotation.Retention;
    /** @hide */
    @Retention(SOURCE)
    @SuppressWarnings("WeakerAccess")
    public @interface DeprecatedForSdk {
        String value();
        Class<?>[] allowIn() default {};
    }
    """
).indented()
