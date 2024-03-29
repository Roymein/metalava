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
import com.android.SdkConstants.FN_FRAMEWORK_LIBRARY
import com.android.tools.lint.detector.api.isJdkFolder
import com.android.tools.metalava.CompatibilityCheck.CheckRequest
import com.android.tools.metalava.model.defaultConfiguration
import com.android.utils.SdkUtils.wrap
import com.google.common.base.CharMatcher
import com.google.common.base.Splitter
import com.google.common.io.Files
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.jps.model.java.impl.JavaSdkUtil
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import java.io.File
import java.io.IOException
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Locale
import kotlin.text.Charsets.UTF_8

/** Global options for the metadata extraction tool */
var options = Options(emptyArray())

private const val MAX_LINE_WIDTH = 120
private const val INDENT_WIDTH = 45

const val ARG_FORMAT = "--format"
const val ARG_HELP = "--help"
const val ARG_VERSION = "--version"
const val ARG_QUIET = "--quiet"
const val ARG_VERBOSE = "--verbose"
const val ARG_CLASS_PATH = "--classpath"
const val ARG_SOURCE_PATH = "--source-path"
const val ARG_SOURCE_FILES = "--source-files"
const val ARG_API = "--api"
const val ARG_XML_API = "--api-xml"
const val ARG_CONVERT_TO_JDIFF = "--convert-to-jdiff"
const val ARG_CONVERT_NEW_TO_JDIFF = "--convert-new-to-jdiff"
const val ARG_DEX_API = "--dex-api"
const val ARG_SDK_VALUES = "--sdk-values"
const val ARG_REMOVED_API = "--removed-api"
const val ARG_MERGE_QUALIFIER_ANNOTATIONS = "--merge-qualifier-annotations"
const val ARG_MERGE_INCLUSION_ANNOTATIONS = "--merge-inclusion-annotations"
const val ARG_VALIDATE_NULLABILITY_FROM_MERGED_STUBS = "--validate-nullability-from-merged-stubs"
const val ARG_VALIDATE_NULLABILITY_FROM_LIST = "--validate-nullability-from-list"
const val ARG_NULLABILITY_WARNINGS_TXT = "--nullability-warnings-txt"
const val ARG_NULLABILITY_ERRORS_NON_FATAL = "--nullability-errors-non-fatal"
const val ARG_INPUT_API_JAR = "--input-api-jar"
const val ARG_STUBS = "--stubs"
const val ARG_DOC_STUBS = "--doc-stubs"
const val ARG_KOTLIN_STUBS = "--kotlin-stubs"
const val ARG_STUBS_SOURCE_LIST = "--write-stubs-source-list"
const val ARG_DOC_STUBS_SOURCE_LIST = "--write-doc-stubs-source-list"

/**
 * Used by Firebase, see b/116185431#comment15, not used by Android Platform or AndroidX
 */
const val ARG_PROGUARD = "--proguard"
const val ARG_EXTRACT_ANNOTATIONS = "--extract-annotations"
const val ARG_EXCLUDE_ALL_ANNOTATIONS = "--exclude-all-annotations"
const val ARG_EXCLUDE_DOCUMENTATION_FROM_STUBS = "--exclude-documentation-from-stubs"
const val ARG_ENHANCE_DOCUMENTATION = "--enhance-documentation"
const val ARG_HIDE_PACKAGE = "--hide-package"
const val ARG_MANIFEST = "--manifest"
const val ARG_MIGRATE_NULLNESS = "--migrate-nullness"
const val ARG_CHECK_COMPATIBILITY_API_RELEASED = "--check-compatibility:api:released"
const val ARG_CHECK_COMPATIBILITY_REMOVED_RELEASED = "--check-compatibility:removed:released"
const val ARG_CHECK_COMPATIBILITY_BASE_API = "--check-compatibility:base"
const val ARG_NO_NATIVE_DIFF = "--no-native-diff"
const val ARG_INPUT_KOTLIN_NULLS = "--input-kotlin-nulls"
const val ARG_OUTPUT_KOTLIN_NULLS = "--output-kotlin-nulls"
const val ARG_OUTPUT_DEFAULT_VALUES = "--output-default-values"
const val ARG_WARNINGS_AS_ERRORS = "--warnings-as-errors"
const val ARG_LINTS_AS_ERRORS = "--lints-as-errors"
const val ARG_SHOW_ANNOTATION = "--show-annotation"
const val ARG_SHOW_SINGLE_ANNOTATION = "--show-single-annotation"
const val ARG_HIDE_ANNOTATION = "--hide-annotation"
const val ARG_HIDE_META_ANNOTATION = "--hide-meta-annotation"
const val ARG_SHOW_FOR_STUB_PURPOSES_ANNOTATION = "--show-for-stub-purposes-annotation"
const val ARG_SHOW_UNANNOTATED = "--show-unannotated"
const val ARG_COLOR = "--color"
const val ARG_NO_COLOR = "--no-color"
const val ARG_NO_BANNER = "--no-banner"
const val ARG_ERROR = "--error"
const val ARG_WARNING = "--warning"
const val ARG_LINT = "--lint"
const val ARG_HIDE = "--hide"
const val ARG_APPLY_API_LEVELS = "--apply-api-levels"
const val ARG_GENERATE_API_LEVELS = "--generate-api-levels"
const val ARG_REMOVE_MISSING_CLASS_REFERENCES_IN_API_LEVELS = "--remove-missing-class-references-in-api-levels"
const val ARG_ANDROID_JAR_PATTERN = "--android-jar-pattern"
const val ARG_CURRENT_VERSION = "--current-version"
const val ARG_FIRST_VERSION = "--first-version"
const val ARG_CURRENT_CODENAME = "--current-codename"
const val ARG_CURRENT_JAR = "--current-jar"
const val ARG_API_LINT = "--api-lint"
const val ARG_API_LINT_IGNORE_PREFIX = "--api-lint-ignore-prefix"
const val ARG_PUBLIC = "--public"
const val ARG_PROTECTED = "--protected"
const val ARG_PACKAGE = "--package"
const val ARG_PRIVATE = "--private"
const val ARG_HIDDEN = "--hidden"
const val ARG_JAVA_SOURCE = "--java-source"
const val ARG_KOTLIN_SOURCE = "--kotlin-source"
const val ARG_SDK_HOME = "--sdk-home"
const val ARG_JDK_HOME = "--jdk-home"
const val ARG_COMPILE_SDK_VERSION = "--compile-sdk-version"
const val ARG_INCLUDE_ANNOTATIONS = "--include-annotations"
const val ARG_COPY_ANNOTATIONS = "--copy-annotations"
const val ARG_INCLUDE_SOURCE_RETENTION = "--include-source-retention"
const val ARG_PASS_THROUGH_ANNOTATION = "--pass-through-annotation"
const val ARG_EXCLUDE_ANNOTATION = "--exclude-annotation"
const val ARG_INCLUDE_SIG_VERSION = "--include-signature-version"
const val ARG_UPDATE_API = "--only-update-api"
const val ARG_CHECK_API = "--only-check-api"
const val ARG_PASS_BASELINE_UPDATES = "--pass-baseline-updates"
const val ARG_BASELINE = "--baseline"
const val ARG_BASELINE_API_LINT = "--baseline:api-lint"
const val ARG_BASELINE_CHECK_COMPATIBILITY_RELEASED = "--baseline:compatibility:released"
const val ARG_REPORT_EVEN_IF_SUPPRESSED = "--report-even-if-suppressed"
const val ARG_UPDATE_BASELINE = "--update-baseline"
const val ARG_UPDATE_BASELINE_API_LINT = "--update-baseline:api-lint"
const val ARG_UPDATE_BASELINE_CHECK_COMPATIBILITY_RELEASED = "--update-baseline:compatibility:released"
const val ARG_MERGE_BASELINE = "--merge-baseline"
const val ARG_STUB_PACKAGES = "--stub-packages"
const val ARG_STUB_IMPORT_PACKAGES = "--stub-import-packages"
const val ARG_DELETE_EMPTY_BASELINES = "--delete-empty-baselines"
const val ARG_DELETE_EMPTY_REMOVED_SIGNATURES = "--delete-empty-removed-signatures"
const val ARG_SUBTRACT_API = "--subtract-api"
const val ARG_TYPEDEFS_IN_SIGNATURES = "--typedefs-in-signatures"
const val ARG_FORCE_CONVERT_TO_WARNING_NULLABILITY_ANNOTATIONS = "--force-convert-to-warning-nullability-annotations"
const val ARG_IGNORE_CLASSES_ON_CLASSPATH = "--ignore-classes-on-classpath"
const val ARG_ERROR_MESSAGE_API_LINT = "--error-message:api-lint"
const val ARG_ERROR_MESSAGE_CHECK_COMPATIBILITY_RELEASED = "--error-message:compatibility:released"
const val ARG_NO_IMPLICIT_ROOT = "--no-implicit-root"
const val ARG_STRICT_INPUT_FILES = "--strict-input-files"
const val ARG_STRICT_INPUT_FILES_STACK = "--strict-input-files:stack"
const val ARG_STRICT_INPUT_FILES_WARN = "--strict-input-files:warn"
const val ARG_STRICT_INPUT_FILES_EXEMPT = "--strict-input-files-exempt"
const val ARG_REPEAT_ERRORS_MAX = "--repeat-errors-max"
const val ARG_SDK_JAR_ROOT = "--sdk-extensions-root"
const val ARG_SDK_INFO_FILE = "--sdk-extensions-info"

class Options(
        private val args: Array<String>,
        /** Writer to direct output to */
        var stdout: PrintWriter = PrintWriter(OutputStreamWriter(System.out)),
        /** Writer to direct error messages to */
        var stderr: PrintWriter = PrintWriter(OutputStreamWriter(System.err))
) {

    /** Internal list backing [sources] */
    private val mutableSources: MutableList<File> = mutableListOf()

    /** Internal list backing [sourcePath] */
    private val mutableSourcePath: MutableList<File> = mutableListOf()

    /** Internal list backing [classpath] */
    private val mutableClassPath: MutableList<File> = mutableListOf()

    /** Internal list backing [showAnnotations] */
    private val mutableShowAnnotations = MutableAnnotationFilter()

    /** Internal list backing [showSingleAnnotations] */
    private val mutableShowSingleAnnotations = MutableAnnotationFilter()

    /** Internal list backing [hideAnnotations] */
    private val mutableHideAnnotations = MutableAnnotationFilter()

    /** Internal list backing [hideMetaAnnotations] */
    private val mutableHideMetaAnnotations: MutableList<String> = mutableListOf()

    /** Internal list backing [showForStubPurposesAnnotations] */
    private val mutableShowForStubPurposesAnnotation = MutableAnnotationFilter()

    /** Internal list backing [stubImportPackages] */
    private val mutableStubImportPackages: MutableSet<String> = mutableSetOf()

    /** Internal list backing [mergeQualifierAnnotations] */
    private val mutableMergeQualifierAnnotations: MutableList<File> = mutableListOf()

    /** Internal list backing [mergeInclusionAnnotations] */
    private val mutableMergeInclusionAnnotations: MutableList<File> = mutableListOf()

    /** Internal list backing [annotationCoverageOf] */
    private val mutableAnnotationCoverageOf: MutableList<File> = mutableListOf()

    /** Internal list backing [hidePackages] */
    private val mutableHidePackages: MutableList<String> = mutableListOf()

    /** Internal list backing [skipEmitPackages] */
    private val mutableSkipEmitPackages: MutableList<String> = mutableListOf()

    /** Internal list backing [convertToXmlFiles] */
    private val mutableConvertToXmlFiles: MutableList<ConvertFile> = mutableListOf()

    /** Internal list backing [passThroughAnnotations] */
    private val mutablePassThroughAnnotations: MutableSet<String> = mutableSetOf()

    /** Internal list backing [excludeAnnotations] */
    private val mutableExcludeAnnotations: MutableSet<String> = mutableSetOf()

    /** Ignored flags we've already warned about - store here such that we don't keep reporting them */
    private val alreadyWarned: MutableSet<String> = mutableSetOf()

    /** API to subtract from signature and stub generation. Corresponds to [ARG_SUBTRACT_API]. */
    var subtractApi: File? = null

    /**
     * Validator for nullability annotations, if validation is enabled.
     */
    var nullabilityAnnotationsValidator: NullabilityAnnotationsValidator? = null

    /**
     * Whether nullability validation errors should be considered fatal.
     */
    var nullabilityErrorsFatal = true

    /**
     * A file to write non-fatal nullability validation issues to. If null, all issues are treated
     * as fatal or else logged as warnings, depending on the value of [nullabilityErrorsFatal].
     */
    var nullabilityWarningsTxt: File? = null

    /**
     * Whether to validate nullability for all the classes where we are merging annotations from
     * external java stub files. If true, [nullabilityAnnotationsValidator] must be set.
     */
    var validateNullabilityFromMergedStubs = false

    /**
     * A file containing a list of classes whose nullability annotations should be validated. If
     * set, [nullabilityAnnotationsValidator] must also be set.
     */
    var validateNullabilityFromList: File? = null

    /**
     * Whether to include element documentation (javadoc and KDoc) is in the generated stubs.
     * (Copyright notices are not affected by this, they are always included. Documentation stubs
     * (--doc-stubs) are not affected.)
     */
    var includeDocumentationInStubs = true

    /**
     * Enhance documentation in various ways, for example auto-generating documentation based on
     * source annotations present in the code. This is implied by --doc-stubs.
     */
    var enhanceDocumentation = false

    /**
     * Whether metalava is invoked as part of updating the API files. When this is true, metalava
     * should *cancel* various other flags that are also being passed in, such as --check-compatibility:*.
     * This is there to ease integration in the build system: for a given target, the build system will
     * pass all the applicable flags (--stubs, --api, --check-compatibility:*, --generate-documentation, etc),
     * and this integration is re-used for the update-api facility where we *only* want to generate the
     * signature files. This avoids having duplicate metalava invocation logic where potentially newly
     * added flags are missing in one of the invocations etc.
     */
    var onlyUpdateApi = false

    /**
     * Whether metalava is invoked as part of running the checkapi target. When this is true, metalava
     * should *cancel* various other flags that are also being passed in, such as updating signature
     * files.
     *
     * This is there to ease integration in the build system: for a given target, the build system will
     * pass all the applicable flags (--stubs, --api, --check-compatibility:*, --generate-documentation, etc),
     * and this integration is re-used for the checkapi facility where we *only* want to run compatibility
     * checks. This avoids having duplicate metalava invocation logic where potentially newly
     * added flags are missing in one of the invocations etc.
     */
    var onlyCheckApi = false

    /** Whether nullness annotations should be displayed as ?/!/empty instead of with @NonNull/@Nullable. */
    var outputKotlinStyleNulls = false // requires v3

    /** Whether default values should be included in signature files */
    var outputDefaultValues = true

    /** The output format version being used */
    var outputFormat: FileFormat = FileFormat.recommended

    /**
     * Whether reading signature files should assume the input is formatted as Kotlin-style nulls
     * (e.g. ? means nullable, ! means unknown, empty means not null).
     *
     * Even when it's false, if the format supports Kotlin-style nulls, we'll still allow them.
     */
    var inputKotlinStyleNulls: Boolean = false

    /** If true, treat all warnings as errors */
    var warningsAreErrors: Boolean = false

    /** If true, treat all API lint warnings as errors */
    var lintsAreErrors: Boolean = false

    /** The list of source roots */
    val sourcePath: List<File> = mutableSourcePath

    /** The list of dependency jars */
    val classpath: List<File> = mutableClassPath

    /** All source files to parse */
    var sources: List<File> = mutableSources

    /**
     * Whether to include APIs with annotations (intended for documentation purposes).
     * This includes [ARG_SHOW_ANNOTATION], [ARG_SHOW_SINGLE_ANNOTATION] and
     * [ARG_SHOW_FOR_STUB_PURPOSES_ANNOTATION].
     */
    var showAnnotations: AnnotationFilter = mutableShowAnnotations

    /**
     * Like [showAnnotations], but does not work recursively. Note that
     * these annotations are *also* show annotations and will be added to the above list;
     * this is a subset.
     */
    val showSingleAnnotations: AnnotationFilter = mutableShowSingleAnnotations

    /**
     * Whether to include unannotated elements if {@link #showAnnotations} is set.
     * Note: This only applies to signature files, not stub files.
     */
    var showUnannotated = false

    /** Whether to validate the API for best practices */
    var checkApi = false

    val checkApiIgnorePrefix: MutableList<String> = mutableListOf()

    /** If non null, an API file to use to hide for controlling what parts of the API are new */
    var checkApiBaselineApiFile: File? = null

    /** Packages to include (if null, include all) */
    var stubPackages: PackageFilter? = null

    /** Packages to import (if empty, include all) */
    var stubImportPackages: Set<String> = mutableStubImportPackages

    /** Packages to exclude/hide */
    var hidePackages: List<String> = mutableHidePackages

    /** Packages that we should skip generating even if not hidden; typically only used by tests */
    var skipEmitPackages: List<String> = mutableSkipEmitPackages

    var showAnnotationOverridesVisibility: Boolean = false

    /** Annotations to hide */
    var hideAnnotations: AnnotationFilter = mutableHideAnnotations

    /** Meta-annotations to hide */
    var hideMetaAnnotations = mutableHideMetaAnnotations

    /**
     * Annotations that defines APIs that are implicitly included in the API surface. These APIs
     * will be included in certain kinds of output such as stubs, but others (e.g. API lint and the
     * API signature file) ignore them.
     */
    var showForStubPurposesAnnotations: AnnotationFilter = mutableShowForStubPurposesAnnotation

    /** Whether the generated API can contain classes that are not present in the source but are present on the
     * classpath. Defaults to true for backwards compatibility but is set to false if any API signatures are imported
     * as they must provide a complete set of all classes required but not provided by the generated API.
     *
     * Once all APIs are either self contained or imported all the required references this will be removed and no
     * classes will be allowed from the classpath JARs. */
    var allowClassesFromClasspath = true

    /** Whether to report warnings and other diagnostics along the way */
    var quiet = false

    /** Whether to report extra diagnostics along the way (note that verbose isn't the same as not quiet) */
    var verbose = false

    /** If set, a directory to write stub files to. Corresponds to the --stubs/-stubs flag. */
    var stubsDir: File? = null

    /** If set, a directory to write documentation stub files to. Corresponds to the --stubs/-stubs flag. */
    var docStubsDir: File? = null

    /** If set, a source file to write the stub index (list of source files) to. Can be passed to
     * other tools like javac/javadoc using the special @-syntax. */
    var stubsSourceList: File? = null

    /** If set, a source file to write the doc stub index (list of source files) to. Can be passed to
     * other tools like javac/javadoc using the special @-syntax. */
    var docStubsSourceList: File? = null

    /** Whether code compiled from Kotlin should be emitted as .kt stubs instead of .java stubs */
    var kotlinStubs = false

    /** Proguard Keep list file to write */
    var proguard: File? = null

    /** If set, a file to write an API file to. Corresponds to the --api/-api flag. */
    var apiFile: File? = null

    /** Like [apiFile], but with JDiff xml format. */
    var apiXmlFile: File? = null

    /** If set, a file to write the DEX signatures to. Corresponds to [ARG_DEX_API]. */
    var dexApiFile: File? = null

    /** Path to directory to write SDK values to */
    var sdkValueDir: File? = null

    /** If set, a file to write extracted annotations to. Corresponds to the --extract-annotations flag. */
    var externalAnnotations: File? = null

    /** For [ARG_COPY_ANNOTATIONS], the source directory to read stub annotations from */
    var privateAnnotationsSource: File? = null

    /** For [ARG_COPY_ANNOTATIONS], the target directory to write converted stub annotations from */
    var privateAnnotationsTarget: File? = null

    /** A manifest file to read to for example look up available permissions */
    var manifest: File? = null

    /** If set, a file to write a dex API file to. Corresponds to the --removed-dex-api/-removedDexApi flag. */
    var removedApiFile: File? = null

    /** Whether output should be colorized */
    var color = System.getenv("TERM")?.startsWith("xterm") ?: System.getenv("COLORTERM") != null ?: false

    /** Whether to generate annotations into the stubs */
    var generateAnnotations = false

    /** The set of annotation classes that should be passed through unchanged */
    var passThroughAnnotations = mutablePassThroughAnnotations

    /** The set of annotation classes that should be removed from all outputs */
    var excludeAnnotations = mutableExcludeAnnotations

    /**
     * A signature file to migrate nullness data from
     */
    var migrateNullsFrom: File? = null

    /** Private backing list for [compatibilityChecks]] */
    private val mutableCompatibilityChecks: MutableList<CheckRequest> = mutableListOf()

    /** The list of compatibility checks to run */
    val compatibilityChecks: List<CheckRequest> = mutableCompatibilityChecks

    /** The API to use a base for the otherwise checked API during compat checks. */
    var baseApiForCompatCheck: File? = null

    /** If false, attempt to use the native diff utility on the system */
    var noNativeDiff = false

    /** Existing external annotation files to merge in */
    var mergeQualifierAnnotations: List<File> = mutableMergeQualifierAnnotations
    var mergeInclusionAnnotations: List<File> = mutableMergeInclusionAnnotations

    /**
     * We modify the annotations on these APIs to ask kotlinc to treat it as only a warning
     * if a caller of one of these APIs makes an incorrect assumption about its nullability.
     */
    var forceConvertToWarningNullabilityAnnotations: PackageFilter? = null

    /** An optional <b>jar</b> file to load classes from instead of from source.
     * This is similar to the [classpath] attribute except we're explicitly saying
     * that this is the complete set of classes and that we <b>should</b> generate
     * signatures/stubs from them or use them to diff APIs with (whereas [classpath]
     * is only used to resolve types.) */
    var apiJar: File? = null

    /**
     * mapping from API level to android.jar files, if computing API levels
     */
    var apiLevelJars: Array<File>? = null

    /** The api level of the codebase, or -1 if not known/specified */
    var currentApiLevel = -1

    /**
     * The first api level of the codebase; typically 1 but can be
     * higher for example for the System API.
     */
    var firstApiLevel = 1

    /**
     * The codename of the codebase: non-null string if this is a developer preview build, null if
     * this is a release build.
     */
    var currentCodeName: String? = null

    /** API level XML file to generate */
    var generateApiLevelXml: File? = null

    /** Whether references to missing classes should be removed from the api levels file. */
    var removeMissingClassesInApiLevels: Boolean = false

    /** Reads API XML file to apply into documentation */
    var applyApiLevelsXml: File? = null

    /** Directory of prebuilt extension SDK jars that contribute to the API */
    var sdkJarRoot: File? = null

    /**
     * Rules to filter out some of the extension SDK APIs from the API, and assign extensions to
     * the APIs that are kept
     */
    var sdkInfoFile: File? = null

    /** Level to include for javadoc */
    var docLevel = DocLevel.PROTECTED

    /** Whether to include the signature file format version header in most signature files */
    var includeSignatureFormatVersion: Boolean = true

    /** Whether to include the signature file format version header in removed signature files */
    val includeSignatureFormatVersionNonRemoved: EmitFileHeader
        get() =
            if (includeSignatureFormatVersion) {
                EmitFileHeader.ALWAYS
            } else {
                EmitFileHeader.NEVER
            }

    /** Whether to include the signature file format version header in removed signature files */
    val includeSignatureFormatVersionRemoved: EmitFileHeader
        get() =
            if (includeSignatureFormatVersion) {
                if (deleteEmptyRemovedSignatures) {
                    EmitFileHeader.IF_NONEMPTY_FILE
                } else {
                    EmitFileHeader.ALWAYS
                }
            } else {
                EmitFileHeader.NEVER
            }

    /** A baseline to check against */
    var baseline: Baseline? = null

    /** A baseline to check against, specifically used for "API lint" (i.e. [ARG_API_LINT]) */
    var baselineApiLint: Baseline? = null

    /**
     * A baseline to check against, specifically used for "check-compatibility:*:released"
     * (i.e. [ARG_CHECK_COMPATIBILITY_API_RELEASED] and [ARG_CHECK_COMPATIBILITY_REMOVED_RELEASED])
     */
    var baselineCompatibilityReleased: Baseline? = null

    var allBaselines: List<Baseline>

    /** If set, metalava will show this error message when "API lint" (i.e. [ARG_API_LINT]) fails. */
    var errorMessageApiLint: String = DefaultLintErrorMessage

    /**
     * If set, metalava will show this error message when "check-compatibility:*:released" fails.
     * (i.e. [ARG_CHECK_COMPATIBILITY_API_RELEASED] and [ARG_CHECK_COMPATIBILITY_REMOVED_RELEASED])
     */
    var errorMessageCompatibilityReleased: String? = null

    /** [Reporter] for "api-lint" */
    var reporterApiLint: Reporter

    /**
     * [Reporter] for "check-compatibility:*:released".
     * (i.e. [ARG_CHECK_COMPATIBILITY_API_RELEASED] and [ARG_CHECK_COMPATIBILITY_REMOVED_RELEASED])
     */
    var reporterCompatibilityReleased: Reporter

    var allReporters: List<Reporter>

    /** If updating baselines, don't fail the build */
    var passBaselineUpdates = false

    /** If updating baselines and the baseline is empty, delete the file */
    var deleteEmptyBaselines = false

    /** If generating a removed signature file and it is empty, delete it */
    var deleteEmptyRemovedSignatures = false

    /** Whether the baseline should only contain errors */
    var baselineErrorsOnly = false

    /** Writes a list of all errors, even if they were suppressed in baseline or via annotation. */
    var reportEvenIfSuppressed: File? = null
    var reportEvenIfSuppressedWriter: PrintWriter? = null

    /**
     * Whether to omit locations for warnings and errors. This is not a flag exposed to users
     * or listed in help; this is intended for the unit test suite, used for example for the
     * test which checks compatibility between signature and API files where the paths vary.
     */
    var omitLocations = false

    /** Directory to write signature files to, if any. */
    var androidJarSignatureFiles: File? = null

    /**
     * The language level to use for Java files, set with [ARG_JAVA_SOURCE]
     */
    var javaLanguageLevel: LanguageLevel = LanguageLevel.JDK_1_8

    /**
     * The language level to use for Java files, set with [ARG_KOTLIN_SOURCE]
     */
    var kotlinLanguageLevel: LanguageVersionSettings = LanguageVersionSettingsImpl.DEFAULT

    /**
     * The JDK to use as a platform, if set with [ARG_JDK_HOME]. This is only set
     * when metalava is used for non-Android projects.
     */
    var jdkHome: File? = null

    /**
     * The JDK to use as a platform, if set with [ARG_SDK_HOME]. If this is set
     * along with [ARG_COMPILE_SDK_VERSION], metalava will automatically add
     * the platform's android.jar file to the classpath if it does not already
     * find the android.jar file in the classpath.
     */
    var sdkHome: File? = null

    /**
     * The compileSdkVersion, set by [ARG_COMPILE_SDK_VERSION]. For example,
     * for R it would be "29". For R preview, if would be "R".
     */
    var compileSdkVersion: String? = null

    /** List of signature files to export as JDiff files */
    val convertToXmlFiles: List<ConvertFile> = mutableConvertToXmlFiles

    enum class TypedefMode {
        NONE,
        REFERENCE,
        INLINE
    }

    /** How to handle typedef annotations in signature files; corresponds to $ARG_TYPEDEFS_IN_SIGNATURES */
    var typedefMode = TypedefMode.NONE

    /** Allow implicit root detection (which is the default behavior). See [ARG_NO_IMPLICIT_ROOT] */
    var allowImplicitRoot = true

    enum class StrictInputFileMode {
        PERMISSIVE,
        STRICT {
            override val shouldFail = true
        },
        STRICT_WARN,
        STRICT_WITH_STACK {
            override val shouldFail = true
        };

        open val shouldFail = false

        companion object {
            fun fromArgument(arg: String): StrictInputFileMode {
                return when (arg) {
                    ARG_STRICT_INPUT_FILES -> STRICT
                    ARG_STRICT_INPUT_FILES_WARN -> STRICT_WARN
                    ARG_STRICT_INPUT_FILES_STACK -> STRICT_WITH_STACK
                    else -> PERMISSIVE
                }
            }
        }
    }

    /**
     * Whether we should allow metalava to read files that are not explicitly specified in the
     * command line. See [ARG_STRICT_INPUT_FILES], [ARG_STRICT_INPUT_FILES_WARN] and
     * [ARG_STRICT_INPUT_FILES_STACK].
     */
    var strictInputFiles = StrictInputFileMode.PERMISSIVE

    var strictInputViolationsFile: File? = null
    var strictInputViolationsPrintWriter: PrintWriter? = null

    /** File conversion tasks */
    data class ConvertFile(
            val fromApiFile: File,
            val outputFile: File,
            val baseApiFile: File? = null,
            val strip: Boolean = false
    )

    /** Temporary folder to use instead of the JDK default, if any */
    var tempFolder: File? = null

    /** When non-0, metalava repeats all the errors at the end of the run, at most this many. */
    var repeatErrorsMax = 0

    init {
        // Pre-check whether --color/--no-color is present and use that to decide how
        // to emit the banner even before we emit errors
        if (args.contains(ARG_NO_COLOR)) {
            color = false
        } else if (args.contains(ARG_COLOR) || args.contains("-android")) {
            color = true
        }
        // empty args: only when building initial default Options (options field
        // at the top of this file; replaced once the driver runs and passes in
        // a real argv. Don't print a banner when initializing the default options.)
        if (args.isNotEmpty() && !args.contains(ARG_QUIET) && !args.contains(ARG_NO_BANNER) &&
                !args.contains(ARG_VERSION)
        ) {
            if (color) {
                stdout.print(colorized(BANNER.trimIndent(), TerminalColor.BLUE))
            } else {
                stdout.println(BANNER.trimIndent())
            }
            stdout.println()
            stdout.flush()
        }

        var androidJarPatterns: MutableList<String>? = null
        var currentJar: File? = null
        var skipGenerateAnnotations = false
        reporter = Reporter(null, null)

        val baselineBuilder = Baseline.Builder().apply { description = "base" }
        val baselineApiLintBuilder = Baseline.Builder().apply { description = "api-lint" }
        val baselineCompatibilityReleasedBuilder = Baseline.Builder().apply { description = "compatibility:released" }

        fun getBaselineBuilderForArg(flag: String): Baseline.Builder = when (flag) {
            ARG_BASELINE, ARG_UPDATE_BASELINE, ARG_MERGE_BASELINE -> baselineBuilder
            ARG_BASELINE_API_LINT, ARG_UPDATE_BASELINE_API_LINT -> baselineApiLintBuilder
            ARG_BASELINE_CHECK_COMPATIBILITY_RELEASED, ARG_UPDATE_BASELINE_CHECK_COMPATIBILITY_RELEASED
            -> baselineCompatibilityReleasedBuilder

            else -> error("Internal error: Invalid flag: $flag")
        }

        var index = 0
        while (index < args.size) {

            when (val arg = args[index]) {
                ARG_HELP, "-h", "-?" -> {
                    helpAndQuit(color)
                }

                ARG_QUIET -> {
                    quiet = true; verbose = false
                }

                ARG_VERBOSE -> {
                    verbose = true; quiet = false
                }

                ARG_VERSION -> {
                    throw DriverException(stdout = "$PROGRAM_NAME version: ${Version.VERSION}")
                }

                // For now we don't distinguish between bootclasspath and classpath
                ARG_CLASS_PATH, "-classpath", "-bootclasspath" -> {
                    val path = getValue(args, ++index)
                    mutableClassPath.addAll(stringToExistingDirsOrJars(path))
                }

                ARG_SOURCE_PATH, "--sources", "--sourcepath", "-sourcepath" -> {
                    val path = getValue(args, ++index)
                    if (path.isBlank()) {
                        // Don't compute absolute path; we want to skip this file later on.
                        // For current directory one should use ".", not "".
                        mutableSourcePath.add(File(""))
                    } else {
                        if (path.endsWith(SdkConstants.DOT_JAVA)) {
                            throw DriverException(
                                    "$arg should point to a source root directory, not a source file ($path)"
                            )
                        }
                        mutableSourcePath.addAll(stringToExistingDirsOrJars(path, false))
                    }
                }

                ARG_SOURCE_FILES -> {
                    val listString = getValue(args, ++index)
                    listString.split(",").forEach { path ->
                        mutableSources.addAll(stringToExistingFiles(path))
                    }
                }

                ARG_SUBTRACT_API -> {
                    if (subtractApi != null) {
                        throw DriverException(stderr = "Only one $ARG_SUBTRACT_API can be supplied")
                    }
                    subtractApi = stringToExistingFile(getValue(args, ++index))
                }

                // TODO: Remove the legacy --merge-annotations flag once it's no longer used to update P docs
                ARG_MERGE_QUALIFIER_ANNOTATIONS, "--merge-zips", "--merge-annotations" -> mutableMergeQualifierAnnotations.addAll(
                        stringToExistingDirsOrFiles(
                                getValue(args, ++index)
                        )
                )

                ARG_MERGE_INCLUSION_ANNOTATIONS -> mutableMergeInclusionAnnotations.addAll(
                        stringToExistingDirsOrFiles(
                                getValue(args, ++index)
                        )
                )

                ARG_FORCE_CONVERT_TO_WARNING_NULLABILITY_ANNOTATIONS -> {
                    val nextArg = getValue(args, ++index)
                    forceConvertToWarningNullabilityAnnotations = PackageFilter.parse(nextArg)
                }

                ARG_VALIDATE_NULLABILITY_FROM_MERGED_STUBS -> {
                    validateNullabilityFromMergedStubs = true
                    nullabilityAnnotationsValidator =
                            nullabilityAnnotationsValidator ?: NullabilityAnnotationsValidator()
                }

                ARG_VALIDATE_NULLABILITY_FROM_LIST -> {
                    validateNullabilityFromList = stringToExistingFile(getValue(args, ++index))
                    nullabilityAnnotationsValidator =
                            nullabilityAnnotationsValidator ?: NullabilityAnnotationsValidator()
                }

                ARG_NULLABILITY_WARNINGS_TXT ->
                    nullabilityWarningsTxt = stringToNewFile(getValue(args, ++index))

                ARG_NULLABILITY_ERRORS_NON_FATAL ->
                    nullabilityErrorsFatal = false

                "-sdkvalues", ARG_SDK_VALUES -> sdkValueDir = stringToNewDir(getValue(args, ++index))
                ARG_API, "-api" -> apiFile = stringToNewFile(getValue(args, ++index))
                ARG_XML_API -> apiXmlFile = stringToNewFile(getValue(args, ++index))
                ARG_DEX_API, "-dexApi" -> dexApiFile = stringToNewFile(getValue(args, ++index))

                ARG_REMOVED_API, "-removedApi" -> removedApiFile = stringToNewFile(getValue(args, ++index))

                ARG_MANIFEST, "-manifest" -> manifest = stringToExistingFile(getValue(args, ++index))

                ARG_SHOW_ANNOTATION, "-showAnnotation" -> mutableShowAnnotations.add(getValue(args, ++index))

                ARG_SHOW_SINGLE_ANNOTATION -> {
                    val annotation = getValue(args, ++index)
                    mutableShowSingleAnnotations.add(annotation)
                    // These should also be counted as show annotations
                    mutableShowAnnotations.add(annotation)
                }

                ARG_SHOW_FOR_STUB_PURPOSES_ANNOTATION, "--show-for-stub-purposes-annotations", "-show-for-stub-purposes-annotation" -> {
                    val annotation = getValue(args, ++index)
                    mutableShowForStubPurposesAnnotation.add(annotation)
                    // These should also be counted as show annotations
                    mutableShowAnnotations.add(annotation)
                }

                ARG_SHOW_UNANNOTATED, "-showUnannotated" -> showUnannotated = true

                "--showAnnotationOverridesVisibility" -> {
                    unimplemented(arg)
                    showAnnotationOverridesVisibility = true
                }

                ARG_HIDE_ANNOTATION, "--hideAnnotations", "-hideAnnotation" ->
                    mutableHideAnnotations.add(getValue(args, ++index))

                ARG_HIDE_META_ANNOTATION, "--hideMetaAnnotations", "-hideMetaAnnotation" ->
                    mutableHideMetaAnnotations.add(getValue(args, ++index))

                ARG_STUBS, "-stubs" -> stubsDir = stringToNewDir(getValue(args, ++index))
                ARG_DOC_STUBS -> docStubsDir = stringToNewDir(getValue(args, ++index))
                ARG_KOTLIN_STUBS -> kotlinStubs = true
                ARG_STUBS_SOURCE_LIST -> stubsSourceList = stringToNewFile(getValue(args, ++index))
                ARG_DOC_STUBS_SOURCE_LIST -> docStubsSourceList = stringToNewFile(getValue(args, ++index))

                ARG_EXCLUDE_ALL_ANNOTATIONS -> generateAnnotations = false

                ARG_EXCLUDE_DOCUMENTATION_FROM_STUBS -> includeDocumentationInStubs = false
                ARG_ENHANCE_DOCUMENTATION -> enhanceDocumentation = true

                // Note that this only affects stub generation, not signature files.
                // For signature files, clear the compatibility mode
                // (--annotations-in-signatures)
                ARG_INCLUDE_ANNOTATIONS -> generateAnnotations = true

                ARG_PASS_THROUGH_ANNOTATION -> {
                    val annotations = getValue(args, ++index)
                    annotations.split(",").forEach { path ->
                        mutablePassThroughAnnotations.add(path)
                    }
                }

                ARG_EXCLUDE_ANNOTATION -> {
                    val annotations = getValue(args, ++index)
                    annotations.split(",").forEach { path ->
                        mutableExcludeAnnotations.add(path)
                    }
                }

                // Flag used by test suite to avoid including locations in
                // the output when diffing against golden files
                "--omit-locations" -> omitLocations = true

                ARG_PROGUARD, "-proguard" -> proguard = stringToNewFile(getValue(args, ++index))

                ARG_HIDE_PACKAGE, "-hidePackage" -> mutableHidePackages.add(getValue(args, ++index))

                ARG_STUB_PACKAGES, "-stubpackages" -> {
                    val packages = getValue(args, ++index)
                    val filter = stubPackages ?: run {
                        val newFilter = PackageFilter()
                        stubPackages = newFilter
                        newFilter
                    }
                    filter.addPackages(packages)
                }

                ARG_STUB_IMPORT_PACKAGES, "-stubimportpackages" -> {
                    val packages = getValue(args, ++index)
                    for (pkg in packages.split(File.pathSeparatorChar)) {
                        mutableStubImportPackages.add(pkg)
                        mutableHidePackages.add(pkg)
                    }
                }

                "--skip-emit-packages" -> {
                    val packages = getValue(args, ++index)
                    mutableSkipEmitPackages += packages.split(File.pathSeparatorChar)
                }

                ARG_TYPEDEFS_IN_SIGNATURES -> {
                    val type = getValue(args, ++index)
                    typedefMode = when (type) {
                        "ref" -> TypedefMode.REFERENCE
                        "inline" -> TypedefMode.INLINE
                        "none" -> TypedefMode.NONE
                        else -> throw DriverException(
                                stderr = "$ARG_TYPEDEFS_IN_SIGNATURES must be one of ref, inline, none; was $type"
                        )
                    }
                }

                ARG_IGNORE_CLASSES_ON_CLASSPATH -> {
                    allowClassesFromClasspath = false
                }

                ARG_BASELINE, ARG_BASELINE_API_LINT, ARG_BASELINE_CHECK_COMPATIBILITY_RELEASED -> {
                    val nextArg = getValue(args, ++index)
                    val builder = getBaselineBuilderForArg(arg)
                    builder.file = stringToExistingFile(nextArg)
                }

                ARG_REPORT_EVEN_IF_SUPPRESSED -> {
                    val relative = getValue(args, ++index)
                    if (reportEvenIfSuppressed != null) {
                        throw DriverException("Only one $ARG_REPORT_EVEN_IF_SUPPRESSED is allowed; found both $reportEvenIfSuppressed and $relative")
                    }
                    reportEvenIfSuppressed = stringToNewOrExistingFile(relative)
                    reportEvenIfSuppressedWriter = reportEvenIfSuppressed?.printWriter()
                }

                ARG_MERGE_BASELINE, ARG_UPDATE_BASELINE, ARG_UPDATE_BASELINE_API_LINT, ARG_UPDATE_BASELINE_CHECK_COMPATIBILITY_RELEASED -> {
                    val builder = getBaselineBuilderForArg(arg)
                    builder.merge = (arg == ARG_MERGE_BASELINE)
                    if (index < args.size - 1) {
                        val nextArg = args[index + 1]
                        if (!nextArg.startsWith("-")) {
                            index++
                            builder.updateFile = stringToNewOrExistingFile(nextArg)
                        }
                    }
                }

                ARG_ERROR_MESSAGE_API_LINT -> errorMessageApiLint = getValue(args, ++index)
                ARG_ERROR_MESSAGE_CHECK_COMPATIBILITY_RELEASED -> errorMessageCompatibilityReleased = getValue(args, ++index)

                ARG_PASS_BASELINE_UPDATES -> passBaselineUpdates = true
                ARG_DELETE_EMPTY_BASELINES -> deleteEmptyBaselines = true
                ARG_DELETE_EMPTY_REMOVED_SIGNATURES -> deleteEmptyRemovedSignatures = true

                ARG_PUBLIC, "-public" -> docLevel = DocLevel.PUBLIC
                ARG_PROTECTED, "-protected" -> docLevel = DocLevel.PROTECTED
                ARG_PACKAGE, "-package" -> docLevel = DocLevel.PACKAGE
                ARG_PRIVATE, "-private" -> docLevel = DocLevel.PRIVATE
                ARG_HIDDEN, "-hidden" -> docLevel = DocLevel.HIDDEN

                ARG_INPUT_API_JAR -> apiJar = stringToExistingFile(getValue(args, ++index))

                ARG_EXTRACT_ANNOTATIONS -> externalAnnotations = stringToNewFile(getValue(args, ++index))
                ARG_COPY_ANNOTATIONS -> {
                    privateAnnotationsSource = stringToExistingDir(getValue(args, ++index))
                    privateAnnotationsTarget = stringToNewDir(getValue(args, ++index))
                }

                "--previous-api" -> {
                    migrateNullsFrom = stringToExistingFile(getValue(args, ++index))
                    reporter.report(
                            Issues.DEPRECATED_OPTION, null as File?,
                            "--previous-api is deprecated; instead " +
                                    "use $ARG_MIGRATE_NULLNESS $migrateNullsFrom"
                    )
                }

                ARG_MIGRATE_NULLNESS -> {
                    // See if the next argument specifies the nullness API codebase
                    if (index < args.size - 1) {
                        val nextArg = args[index + 1]
                        if (!nextArg.startsWith("-")) {
                            val file = stringToExistingFile(nextArg)
                            if (file.isFile) {
                                index++
                                migrateNullsFrom = file
                            }
                        }
                    }
                }

                ARG_CHECK_COMPATIBILITY_API_RELEASED -> {
                    val file = stringToExistingFile(getValue(args, ++index))
                    mutableCompatibilityChecks.add(CheckRequest(file, ApiType.PUBLIC_API))
                }

                ARG_CHECK_COMPATIBILITY_REMOVED_RELEASED -> {
                    val file = stringToExistingFile(getValue(args, ++index))
                    mutableCompatibilityChecks.add(CheckRequest(file, ApiType.REMOVED))
                }

                ARG_CHECK_COMPATIBILITY_BASE_API -> {
                    val file = stringToExistingFile(getValue(args, ++index))
                    baseApiForCompatCheck = file
                }

                ARG_NO_NATIVE_DIFF -> noNativeDiff = true

                ARG_ERROR, "-error" -> setIssueSeverity(
                        getValue(args, ++index),
                        Severity.ERROR,
                        arg
                )

                ARG_WARNING, "-warning" -> setIssueSeverity(
                        getValue(args, ++index),
                        Severity.WARNING,
                        arg
                )

                ARG_LINT, "-lint" -> setIssueSeverity(getValue(args, ++index), Severity.LINT, arg)
                ARG_HIDE, "-hide" -> setIssueSeverity(getValue(args, ++index), Severity.HIDDEN, arg)

                ARG_WARNINGS_AS_ERRORS -> warningsAreErrors = true
                ARG_LINTS_AS_ERRORS -> lintsAreErrors = true
                "-werror" -> {
                    // Temporarily disabled; this is used in various builds but is pretty much
                    // never what we want.
                    // warningsAreErrors = true
                }

                "-lerror" -> {
                    // Temporarily disabled; this is used in various builds but is pretty much
                    // never what we want.
                    // lintsAreErrors = true
                }

                ARG_API_LINT -> {
                    checkApi = true
                    if (index < args.size - 1) {
                        val nextArg = args[index + 1]
                        if (!nextArg.startsWith("-")) {
                            val file = stringToExistingFile(nextArg)
                            if (file.isFile) {
                                index++
                                checkApiBaselineApiFile = file
                            }
                        }
                    }
                }

                ARG_API_LINT_IGNORE_PREFIX -> {
                    checkApiIgnorePrefix.add(getValue(args, ++index))
                }

                ARG_COLOR -> color = true
                ARG_NO_COLOR -> color = false
                ARG_NO_BANNER -> {
                    // Already processed above but don't flag it here as invalid
                }

                // Extracting API levels
                ARG_ANDROID_JAR_PATTERN -> {
                    val list = androidJarPatterns ?: run {
                        val list = arrayListOf<String>()
                        androidJarPatterns = list
                        list
                    }
                    list.add(getValue(args, ++index))
                }

                ARG_CURRENT_VERSION -> {
                    currentApiLevel = Integer.parseInt(getValue(args, ++index))
                    if (currentApiLevel <= 26) {
                        throw DriverException("Suspicious currentApi=$currentApiLevel, expected at least 27")
                    }
                }

                ARG_FIRST_VERSION -> {
                    firstApiLevel = Integer.parseInt(getValue(args, ++index))
                }

                ARG_CURRENT_CODENAME -> {
                    val codeName = getValue(args, ++index)
                    if (codeName != "REL") {
                        currentCodeName = codeName
                    }
                }

                ARG_CURRENT_JAR -> {
                    currentJar = stringToExistingFile(getValue(args, ++index))
                }

                ARG_GENERATE_API_LEVELS -> {
                    generateApiLevelXml = stringToNewFile(getValue(args, ++index))
                }

                ARG_APPLY_API_LEVELS -> {
                    applyApiLevelsXml = if (args.contains(ARG_GENERATE_API_LEVELS)) {
                        // If generating the API file at the same time, it doesn't have
                        // to already exist
                        stringToNewFile(getValue(args, ++index))
                    } else {
                        stringToExistingFile(getValue(args, ++index))
                    }
                }

                ARG_REMOVE_MISSING_CLASS_REFERENCES_IN_API_LEVELS -> removeMissingClassesInApiLevels = true

                ARG_UPDATE_API, "--update-api" -> onlyUpdateApi = true
                ARG_CHECK_API -> onlyCheckApi = true

                ARG_CONVERT_TO_JDIFF,
                    // doclava compatibility:
                "-convert2xml",
                "-convert2xmlnostrip" -> {
                    val strip = arg == "-convert2xml"
                    val signatureFile = stringToExistingFile(getValue(args, ++index))
                    val outputFile = stringToNewFile(getValue(args, ++index))
                    mutableConvertToXmlFiles.add(ConvertFile(signatureFile, outputFile, null, strip))
                }

                ARG_CONVERT_NEW_TO_JDIFF,
                    // doclava compatibility:
                "-new_api",
                "-new_api_no_strip" -> {
                    val strip = arg == "-new_api"
                    val baseFile = stringToExistingFile(getValue(args, ++index))
                    val signatureFile = stringToExistingFile(getValue(args, ++index))
                    val jDiffFile = stringToNewFile(getValue(args, ++index))
                    mutableConvertToXmlFiles.add(ConvertFile(signatureFile, jDiffFile, baseFile, strip))
                }

                "--write-android-jar-signatures" -> {
                    val root = stringToExistingDir(getValue(args, ++index))
                    if (!File(root, "prebuilts/sdk").isDirectory) {
                        throw DriverException("$androidJarSignatureFiles does not point to an Android source tree")
                    }
                    androidJarSignatureFiles = root
                }

                "-encoding" -> {
                    val value = getValue(args, ++index)
                    if (value.uppercase(Locale.getDefault()) != "UTF-8") {
                        throw DriverException("$value: Only UTF-8 encoding is supported")
                    }
                }

                ARG_JAVA_SOURCE, "-source" -> {
                    val value = getValue(args, ++index)
                    val level = LanguageLevel.parse(value)
                    when {
                        level == null -> throw DriverException("$value is not a valid or supported Java language level")
                        level.isLessThan(LanguageLevel.JDK_1_7) -> throw DriverException("$arg must be at least 1.7")
                        else -> javaLanguageLevel = level
                    }
                }

                ARG_KOTLIN_SOURCE -> {
                    val value = getValue(args, ++index)
                    val languageLevel =
                            LanguageVersion.fromVersionString(value)
                                    ?: throw DriverException("$value is not a valid or supported Kotlin language level")
                    val apiVersion = ApiVersion.createByLanguageVersion(languageLevel)
                    val settings = LanguageVersionSettingsImpl(languageLevel, apiVersion)
                    kotlinLanguageLevel = settings
                }

                ARG_JDK_HOME -> {
                    jdkHome = stringToExistingDir(getValue(args, ++index))
                }

                ARG_SDK_HOME -> {
                    sdkHome = stringToExistingDir(getValue(args, ++index))
                }

                ARG_COMPILE_SDK_VERSION -> {
                    compileSdkVersion = getValue(args, ++index)
                }

                ARG_NO_IMPLICIT_ROOT -> {
                    allowImplicitRoot = false
                }

                ARG_STRICT_INPUT_FILES, ARG_STRICT_INPUT_FILES_WARN, ARG_STRICT_INPUT_FILES_STACK -> {
                    if (strictInputViolationsFile != null) {
                        throw DriverException("$ARG_STRICT_INPUT_FILES, $ARG_STRICT_INPUT_FILES_WARN and $ARG_STRICT_INPUT_FILES_STACK may be specified only once")
                    }
                    strictInputFiles = StrictInputFileMode.fromArgument(arg)

                    val file = stringToNewOrExistingFile(getValue(args, ++index))
                    strictInputViolationsFile = file
                    strictInputViolationsPrintWriter = file.printWriter()
                }

                ARG_STRICT_INPUT_FILES_EXEMPT -> {
                    val listString = getValue(args, ++index)
                    listString.split(File.pathSeparatorChar).forEach { path ->
                        // Throw away the result; just let the function add the files to the
                        // allowed list.
                        stringToExistingFilesOrDirs(path)
                    }
                }

                ARG_REPEAT_ERRORS_MAX -> {
                    repeatErrorsMax = Integer.parseInt(getValue(args, ++index))
                }

                ARG_SDK_JAR_ROOT -> {
                    sdkJarRoot = stringToExistingDir(getValue(args, ++index))
                }

                ARG_SDK_INFO_FILE -> {
                    sdkInfoFile = stringToExistingFile(getValue(args, ++index))
                }

                "--temp-folder" -> {
                    tempFolder = stringToNewOrExistingDir(getValue(args, ++index))
                }

                // Option only meant for tests (not documented); doesn't work in all cases (to do that we'd
                // need JNA to call libc)
                "--pwd" -> {
                    val pwd = stringToExistingDir(getValue(args, ++index)).absoluteFile
                    System.setProperty("user.dir", pwd.path)
                }

                "--noop", "--no-op" -> {
                }

                // Doclava1 flag: Already the behavior in metalava
                "-keepstubcomments" -> {
                }

                // Unimplemented doclava1 flags (no arguments)
                "-quiet",
                "-yamlV2" -> {
                    unimplemented(arg)
                }

                "-android" -> { // partially implemented: Pick up the color hint, but there may be other implications
                    color = true
                    unimplemented(arg)
                }

                "-stubsourceonly" -> {
                    /* noop */
                }

                // Unimplemented doclava1 flags (1 argument)
                "-d" -> {
                    unimplemented(arg)
                    index++
                }

                // Unimplemented doclava1 flags (2 arguments)
                "-since" -> {
                    unimplemented(arg)
                    index += 2
                }

                // doclava1 doc-related flags: only supported here to make this command a drop-in
                // replacement
                "-referenceonly",
                "-devsite",
                "-ignoreJdLinks",
                "-nodefaultassets",
                "-parsecomments",
                "-offlinemode",
                "-gcmref",
                "-metadataDebug",
                "-includePreview",
                "-staticonly",
                "-navtreeonly",
                "-atLinksNavtree" -> {
                    javadoc(arg)
                }

                // doclava1 flags with 1 argument
                "-doclet",
                "-docletpath",
                "-templatedir",
                "-htmldir",
                "-knowntags",
                "-resourcesdir",
                "-resourcesoutdir",
                "-yaml",
                "-apidocsdir",
                "-toroot",
                "-samplegroup",
                "-samplesdir",
                "-dac_libraryroot",
                "-dac_dataname",
                "-title",
                "-proofread",
                "-todo",
                "-overview" -> {
                    javadoc(arg)
                    index++
                }

                // doclava1 flags with two arguments
                "-federate",
                "-federationapi",
                "-htmldir2" -> {
                    javadoc(arg)
                    index += 2
                }

                // doclava1 flags with three arguments
                "-samplecode" -> {
                    javadoc(arg)
                    index += 3
                }

                // doclava1 flag with variable number of arguments; skip everything until next arg
                "-hdf" -> {
                    javadoc(arg)
                    index++
                    while (index < args.size) {
                        if (args[index].startsWith("-")) {
                            break
                        }
                        index++
                    }
                    index--
                }

                else -> {
                    if (arg.startsWith("-J-") || arg.startsWith("-XD")) {
                        // -J: mechanism to pass extra flags to javadoc, e.g.
                        //    -J-XX:-OmitStackTraceInFastThrow
                        // -XD: mechanism to set properties, e.g.
                        //    -XDignore.symbol.file
                        javadoc(arg)
                    } else if (arg.startsWith(ARG_OUTPUT_KOTLIN_NULLS)) {
                        outputKotlinStyleNulls = if (arg == ARG_OUTPUT_KOTLIN_NULLS) {
                            true
                        } else {
                            yesNo(arg.substring(ARG_OUTPUT_KOTLIN_NULLS.length + 1))
                        }
                    } else if (arg.startsWith(ARG_INPUT_KOTLIN_NULLS)) {
                        inputKotlinStyleNulls = if (arg == ARG_INPUT_KOTLIN_NULLS) {
                            true
                        } else {
                            yesNo(arg.substring(ARG_INPUT_KOTLIN_NULLS.length + 1))
                        }
                    } else if (arg.startsWith(ARG_OUTPUT_DEFAULT_VALUES)) {
                        outputDefaultValues = if (arg == ARG_OUTPUT_DEFAULT_VALUES) {
                            true
                        } else {
                            yesNo(arg.substring(ARG_OUTPUT_DEFAULT_VALUES.length + 1))
                        }
                    } else if (arg.startsWith(ARG_INCLUDE_SIG_VERSION)) {
                        includeSignatureFormatVersion = if (arg == ARG_INCLUDE_SIG_VERSION)
                            true
                        else yesNo(arg.substring(ARG_INCLUDE_SIG_VERSION.length + 1))
                    } else if (arg.startsWith(ARG_FORMAT)) {
                        outputFormat = when (arg) {
                            "$ARG_FORMAT=v1" -> FileFormat.V1
                            "$ARG_FORMAT=v2" -> FileFormat.V2
                            "$ARG_FORMAT=v3" -> FileFormat.V3
                            "$ARG_FORMAT=v4" -> FileFormat.V4
                            "$ARG_FORMAT=recommended" -> FileFormat.recommended
                            "$ARG_FORMAT=latest" -> FileFormat.latest
                            else -> throw DriverException(stderr = "Unexpected signature format; expected v1, v2, v3 or v4")
                        }
                        outputFormat.configureOptions(this)
                    } else if (arg.startsWith("-")) {
                        // Some other argument: display usage info and exit
                        val usage = getUsage(includeHeader = false, colorize = color)
                        throw DriverException(stderr = "Invalid argument $arg\n\n$usage")
                    } else {
                        // All args that don't start with "-" are taken to be filenames
                        mutableSources.addAll(stringToExistingFiles(arg))

                        // Temporary workaround for
                        // aosp/I73ff403bfc3d9dfec71789a3e90f9f4ea95eabe3
                        if (arg.endsWith("hwbinder-stubs-docs-stubs.srcjar.rsp")) {
                            skipGenerateAnnotations = true
                        }
                    }
                }
            }

            ++index
        }

        if (generateApiLevelXml != null) {
            if (currentApiLevel == -1) {
                throw DriverException(stderr = "$ARG_GENERATE_API_LEVELS requires $ARG_CURRENT_VERSION")
            }

            // <String> is redundant here but while IDE (with newer type inference engine
            // understands that) the current 1.3.x compiler does not
            @Suppress("RemoveExplicitTypeArguments")
            val patterns = androidJarPatterns ?: run {
                mutableListOf<String>()
            }
            // Fallbacks
            patterns.add("prebuilts/tools/common/api-versions/android-%/android.jar")
            patterns.add("prebuilts/sdk/%/public/android.jar")
            apiLevelJars = findAndroidJars(
                    patterns,
                    firstApiLevel,
                    currentApiLevel + if (isDeveloperPreviewBuild()) 1 else 0,
                    currentJar
            )
        }

        if ((sdkJarRoot == null) != (sdkInfoFile == null)) {
            throw DriverException(stderr = "$ARG_SDK_JAR_ROOT and $ARG_SDK_INFO_FILE must both be supplied")
        }

        // outputKotlinStyleNulls implies at least format=v3
        if (outputKotlinStyleNulls) {
            if (outputFormat < FileFormat.V3) {
                outputFormat = FileFormat.V3
            }
            outputFormat.configureOptions(this)
        }

        // If the caller has not explicitly requested that unannotated classes and
        // members should be shown in the output then only show them if no annotations were provided.
        if (!showUnannotated && showAnnotations.isEmpty()) {
            showUnannotated = true
        }

        if (skipGenerateAnnotations) {
            generateAnnotations = false
        }

        if (onlyUpdateApi) {
            if (onlyCheckApi) {
                throw DriverException(stderr = "Cannot supply both $ARG_UPDATE_API and $ARG_CHECK_API at the same time")
            }
            // We're running in update API mode: cancel other "action" flags; only signature file generation
            // flags count
            apiLevelJars = null
            generateApiLevelXml = null
            sdkJarRoot = null
            sdkInfoFile = null
            applyApiLevelsXml = null
            androidJarSignatureFiles = null
            stubsDir = null
            docStubsDir = null
            stubsSourceList = null
            docStubsSourceList = null
            sdkValueDir = null
            externalAnnotations = null
            proguard = null
            mutableCompatibilityChecks.clear()
            mutableAnnotationCoverageOf.clear()
            mutableConvertToXmlFiles.clear()
            nullabilityAnnotationsValidator = null
            nullabilityWarningsTxt = null
            validateNullabilityFromMergedStubs = false
            validateNullabilityFromMergedStubs = false
            validateNullabilityFromList = null
        } else if (onlyCheckApi) {
            apiLevelJars = null
            generateApiLevelXml = null
            sdkJarRoot = null
            sdkInfoFile = null
            applyApiLevelsXml = null
            androidJarSignatureFiles = null
            stubsDir = null
            docStubsDir = null
            stubsSourceList = null
            docStubsSourceList = null
            sdkValueDir = null
            externalAnnotations = null
            proguard = null
            mutableAnnotationCoverageOf.clear()
            mutableConvertToXmlFiles.clear()
            nullabilityAnnotationsValidator = null
            nullabilityWarningsTxt = null
            validateNullabilityFromMergedStubs = false
            validateNullabilityFromMergedStubs = false
            validateNullabilityFromList = null
            apiFile = null
            apiXmlFile = null
            dexApiFile = null
            removedApiFile = null
        }

        // Fix up [Baseline] files and [Reporter]s.

        val baselineHeaderComment = if (isBuildingAndroid())
            "// See tools/metalava/API-LINT.md for how to update this file.\n\n"
        else
            ""
        baselineBuilder.headerComment = baselineHeaderComment
        baselineApiLintBuilder.headerComment = baselineHeaderComment
        baselineCompatibilityReleasedBuilder.headerComment = baselineHeaderComment

        if (baselineBuilder.file == null) {
            // If default baseline is a file, use it.
            val defaultBaselineFile = getDefaultBaselineFile()
            if (defaultBaselineFile != null && defaultBaselineFile.isFile) {
                baselineBuilder.file = defaultBaselineFile
            }
        }

        baseline = baselineBuilder.build()
        baselineApiLint = baselineApiLintBuilder.build()
        baselineCompatibilityReleased = baselineCompatibilityReleasedBuilder.build()

        reporterApiLint = Reporter(
                baselineApiLint ?: baseline,
                errorMessageApiLint
        )
        reporterCompatibilityReleased = Reporter(
                baselineCompatibilityReleased ?: baseline,
                errorMessageCompatibilityReleased
        )

        // Build "all baselines" and "all reporters"

        // Baselines are nullable, so selectively add to the list.
        allBaselines = listOfNotNull(baseline, baselineApiLint, baselineCompatibilityReleased)

        // Reporters are non-null.
        allReporters = listOf(
                reporter,
                reporterApiLint,
                reporterCompatibilityReleased,
        )

        updateClassPath()
        checkFlagConsistency()
    }

    public fun isDeveloperPreviewBuild(): Boolean = currentCodeName != null

    /** Update the classpath to insert android.jar or JDK classpath elements if necessary */
    private fun updateClassPath() {
        val sdkHome = sdkHome
        val jdkHome = jdkHome

        if (sdkHome != null &&
                compileSdkVersion != null &&
                classpath.none { it.name == FN_FRAMEWORK_LIBRARY }
        ) {
            val jar = File(sdkHome, "platforms/android-$compileSdkVersion")
            if (jar.isFile) {
                mutableClassPath.add(jar)
            } else {
                throw DriverException(
                        stderr = "Could not find android.jar for API level " +
                                "$compileSdkVersion in SDK $sdkHome: $jar does not exist"
                )
            }
            if (jdkHome != null) {
                throw DriverException(stderr = "Do not specify both $ARG_SDK_HOME and $ARG_JDK_HOME")
            }
        } else if (jdkHome != null) {
            val isJre = !isJdkFolder(jdkHome)

            @Suppress("DEPRECATION", "UnstableApiUsage")
            val roots = JavaSdkUtil.getJdkClassesRoots(jdkHome, isJre)
            mutableClassPath.addAll(roots)
        }
    }

    fun isJdkModular(homePath: File): Boolean {
        return File(homePath, "jmods").isDirectory
    }

    /**
     * Produce a default file name for the baseline. It's normally "baseline.txt", but can
     * be prefixed by show annotations; e.g. @TestApi -> test-baseline.txt, @SystemApi -> system-baseline.txt,
     * etc.
     *
     * Note because the default baseline file is not explicitly set in the command line,
     * this file would trigger a --strict-input-files violation. To avoid that, always explicitly
     * pass a baseline file.
     */
    private fun getDefaultBaselineFile(): File? {
        if (sourcePath.isNotEmpty() && sourcePath[0].path.isNotBlank()) {
            fun annotationToPrefix(qualifiedName: String): String {
                val name = qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1)
                return name.lowercase(Locale.US).removeSuffix("api") + "-"
            }

            val sb = StringBuilder()
            showAnnotations.getIncludedAnnotationNames().forEach { sb.append(annotationToPrefix(it)) }
            sb.append(DEFAULT_BASELINE_NAME)
            var base = sourcePath[0]
            // Convention: in AOSP, signature files are often in sourcepath/api: let's place baseline
            // files there too
            val api = File(base, "api")
            if (api.isDirectory) {
                base = api
            }
            return File(base, sb.toString())
        } else {
            return null
        }
    }

    /**
     * Find an android stub jar that matches the given criteria.
     *
     * Note because the default baseline file is not explicitly set in the command line,
     * this file would trigger a --strict-input-files violation. To avoid that, use
     * --strict-input-files-exempt to exempt the jar directory.
     */
    private fun findAndroidJars(
            androidJarPatterns: List<String>,
            minApi: Int,
            currentApiLevel: Int,
            currentJar: File?
    ): Array<File> {
        val apiLevelFiles = mutableListOf<File>()
        // api level 0: placeholder, should not be processed.
        // (This is here because we want the array index to match
        // the API level)
        val element = File("not an api: the starting API index is $minApi")
        for (i in 0 until minApi) {
            apiLevelFiles.add(element)
        }

        // Get all the android.jar. They are in platforms-#
        var apiLevel = minApi - 1
        while (true) {
            apiLevel++
            try {
                var jar: File? = null
                if (apiLevel == currentApiLevel) {
                    jar = currentJar
                }
                if (jar == null) {
                    jar = getAndroidJarFile(apiLevel, androidJarPatterns)
                }
                if (jar == null || !jar.isFile) {
                    if (verbose) {
                        stdout.println("Last API level found: ${apiLevel - 1}")
                    }

                    if (apiLevel < 28) {
                        // Clearly something is wrong with the patterns; this should result in a build error
                        val argList = mutableListOf<String>()
                        args.forEachIndexed { index, arg ->
                            if (arg == ARG_ANDROID_JAR_PATTERN) {
                                argList.add(args[index + 1])
                            }
                        }
                        throw DriverException(
                                stderr = "Could not find android.jar for API level $apiLevel; the " +
                                        "$ARG_ANDROID_JAR_PATTERN set might be invalid: ${argList.joinToString()}"
                        )
                    }

                    break
                }
                if (verbose) {
                    stdout.println("Found API $apiLevel at ${jar.path}")
                }
                apiLevelFiles.add(jar)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        return apiLevelFiles.toTypedArray()
    }

    private fun getAndroidJarFile(apiLevel: Int, patterns: List<String>): File? {
        // Note this method doesn't register the result to [FileReadSandbox]
        return patterns
                .map { fileForPathInner(it.replace("%", apiLevel.toString())) }
                .firstOrNull { it.isFile }
    }

    private fun yesNo(answer: String): Boolean {
        return when (answer) {
            "yes", "true", "enabled", "on" -> true
            "no", "false", "disabled", "off" -> false
            else -> throw DriverException(stderr = "Unexpected $answer; expected yes or no")
        }
    }

    /** Makes sure that the flag combinations make sense */
    private fun checkFlagConsistency() {
        if (apiJar != null && sources.isNotEmpty()) {
            throw DriverException(stderr = "Specify either $ARG_SOURCE_FILES or $ARG_INPUT_API_JAR, not both")
        }
    }

    private fun javadoc(arg: String) {
        if (!alreadyWarned.add(arg)) {
            return
        }
        if (!options.quiet) {
            reporter.report(
                    Severity.WARNING, null as String?, "Ignoring javadoc-related doclava1 flag $arg",
                    color = color
            )
        }
    }

    private fun unimplemented(arg: String) {
        if (!alreadyWarned.add(arg)) {
            return
        }
        if (!options.quiet) {
            val message = "Ignoring unimplemented doclava1 flag $arg" +
                    when (arg) {
                        "-encoding" -> " (UTF-8 assumed)"
                        "-source" -> "  (1.8 assumed)"
                        else -> ""
                    }
            reporter.report(Severity.WARNING, null as String?, message, color = color)
        }
    }

    private fun helpAndQuit(colorize: Boolean = color) {
        throw DriverException(stdout = getUsage(colorize = colorize))
    }

    private fun getValue(args: Array<String>, index: Int): String {
        if (index >= args.size) {
            throw DriverException("Missing argument for ${args[index - 1]}")
        }
        return args[index]
    }

    private fun stringToExistingDir(value: String): File {
        val file = fileForPathInner(value)
        if (!file.isDirectory) {
            throw DriverException("$file is not a directory")
        }
        return FileReadSandbox.allowAccess(file)
    }

    @Suppress("unused")
    private fun stringToExistingDirs(value: String): List<File> {
        val files = mutableListOf<File>()
        for (path in value.split(File.pathSeparatorChar)) {
            val file = fileForPathInner(path)
            if (!file.isDirectory) {
                throw DriverException("$file is not a directory")
            }
            files.add(file)
        }
        return FileReadSandbox.allowAccess(files)
    }

    private fun stringToExistingDirsOrJars(value: String, exempt: Boolean = true): List<File> {
        val files = mutableListOf<File>()
        for (path in value.split(File.pathSeparatorChar)) {
            val file = fileForPathInner(path)
            if (!file.isDirectory && !(file.path.endsWith(SdkConstants.DOT_JAR) && file.isFile)) {
                throw DriverException("$file is not a jar or directory")
            }
            files.add(file)
        }
        if (exempt) {
            return FileReadSandbox.allowAccess(files)
        }
        return files
    }

    private fun stringToExistingDirsOrFiles(value: String): List<File> {
        val files = mutableListOf<File>()
        for (path in value.split(File.pathSeparatorChar)) {
            val file = fileForPathInner(path)
            if (!file.exists()) {
                throw DriverException("$file does not exist")
            }
            files.add(file)
        }
        return FileReadSandbox.allowAccess(files)
    }

    private fun stringToExistingFile(value: String): File {
        val file = fileForPathInner(value)
        if (!file.isFile) {
            throw DriverException("$file is not a file")
        }
        return FileReadSandbox.allowAccess(file)
    }

    @Suppress("unused")
    private fun stringToExistingFileOrDir(value: String): File {
        val file = fileForPathInner(value)
        if (!file.exists()) {
            throw DriverException("$file is not a file or directory")
        }
        return FileReadSandbox.allowAccess(file)
    }

    private fun stringToExistingFiles(value: String): List<File> {
        return stringToExistingFilesOrDirsInternal(value, false)
    }

    private fun stringToExistingFilesOrDirs(value: String): List<File> {
        return stringToExistingFilesOrDirsInternal(value, true)
    }

    private fun stringToExistingFilesOrDirsInternal(value: String, allowDirs: Boolean): List<File> {
        val files = mutableListOf<File>()
        value.split(File.pathSeparatorChar)
                .map { fileForPathInner(it) }
                .forEach { file ->
                    if (file.path.startsWith("@")) {
                        // File list; files to be read are stored inside. SHOULD have been one per line
                        // but sadly often uses spaces for separation too (so we split by whitespace,
                        // which means you can't point to files in paths with spaces)
                        val listFile = File(file.path.substring(1))
                        if (!allowDirs && !listFile.isFile) {
                            throw DriverException("$listFile is not a file")
                        }
                        val contents = Files.asCharSource(listFile, UTF_8).read()
                        val pathList = Splitter.on(CharMatcher.whitespace()).trimResults().omitEmptyStrings().split(
                                contents
                        )
                        pathList.asSequence().map { File(it) }.forEach {
                            if (!allowDirs && !it.isFile) {
                                throw DriverException("$it is not a file")
                            }
                            files.add(it)
                        }
                    } else {
                        if (!allowDirs && !file.isFile) {
                            throw DriverException("$file is not a file")
                        }
                        files.add(file)
                    }
                }
        return FileReadSandbox.allowAccess(files)
    }

    private fun stringToNewFile(value: String): File {
        val output = fileForPathInner(value)

        if (output.exists()) {
            if (output.isDirectory) {
                throw DriverException("$output is a directory")
            }
            val deleted = output.delete()
            if (!deleted) {
                throw DriverException("Could not delete previous version of $output")
            }
        } else if (output.parentFile != null && !output.parentFile.exists()) {
            val ok = output.parentFile.mkdirs()
            if (!ok) {
                throw DriverException("Could not create ${output.parentFile}")
            }
        }

        return FileReadSandbox.allowAccess(output)
    }

    private fun stringToNewOrExistingDir(value: String): File {
        val dir = fileForPathInner(value)
        if (!dir.isDirectory) {
            val ok = dir.mkdirs()
            if (!ok) {
                throw DriverException("Could not create $dir")
            }
        }
        return FileReadSandbox.allowAccess(dir)
    }

    private fun stringToNewOrExistingFile(value: String): File {
        val file = fileForPathInner(value)
        if (!file.exists()) {
            val parentFile = file.parentFile
            if (parentFile != null && !parentFile.isDirectory) {
                val ok = parentFile.mkdirs()
                if (!ok) {
                    throw DriverException("Could not create $parentFile")
                }
            }
        }
        return FileReadSandbox.allowAccess(file)
    }

    private fun stringToNewDir(value: String): File {
        val output = fileForPathInner(value)
        val ok =
                if (output.exists()) {
                    if (output.isDirectory) {
                        output.deleteRecursively()
                    }
                    if (output.exists()) {
                        true
                    } else {
                        output.mkdir()
                    }
                } else {
                    output.mkdirs()
                }
        if (!ok) {
            throw DriverException("Could not create $output")
        }

        return FileReadSandbox.allowAccess(output)
    }

    /**
     * Converts a path to a [File] that represents the absolute path, with the following special
     * behavior:
     * - "~" will be expanded into the home directory path.
     * - If the given path starts with "@", it'll be converted into "@" + [file's absolute path]
     *
     * Note, unlike the other "stringToXxx" methods, this method won't register the given path
     * to [FileReadSandbox].
     */
    private fun fileForPathInner(path: String): File {
        // java.io.File doesn't automatically handle ~/ -> home directory expansion.
        // This isn't necessary when metalava is run via the command line driver
        // (since shells will perform this expansion) but when metalava is run
        // directly, not from a shell.
        if (path.startsWith("~/")) {
            val home = System.getProperty("user.home") ?: return File(path)
            return File(home + path.substring(1))
        } else if (path.startsWith("@")) {
            return File("@" + File(path.substring(1)).absolutePath)
        }

        return File(path).absoluteFile
    }

    private fun getUsage(includeHeader: Boolean = true, colorize: Boolean = color): String {
        val usage = StringWriter()
        val printWriter = PrintWriter(usage)
        usage(printWriter, includeHeader, colorize)
        return usage.toString()
    }

    private fun usage(out: PrintWriter, includeHeader: Boolean = true, colorize: Boolean = color) {
        if (includeHeader) {
            out.println(wrap(HELP_PROLOGUE, MAX_LINE_WIDTH, ""))
        }

        if (colorize) {
            out.println("Usage: ${colorized(PROGRAM_NAME, TerminalColor.BLUE)} <flags>")
        } else {
            out.println("Usage: $PROGRAM_NAME <flags>")
        }

        val args = arrayOf(
                "", "\nGeneral:",
                ARG_HELP, "This message.",
                ARG_VERSION, "Show the version of $PROGRAM_NAME.",
                ARG_QUIET, "Only include vital output",
                ARG_VERBOSE, "Include extra diagnostic output",
                ARG_COLOR, "Attempt to colorize the output (defaults to true if \$TERM is xterm)",
                ARG_NO_COLOR, "Do not attempt to colorize the output",
                ARG_UPDATE_API,
                "Cancel any other \"action\" flags other than generating signature files. This is here " +
                        "to make it easier customize build system tasks, particularly for the \"make update-api\" task.",
                ARG_CHECK_API,
                "Cancel any other \"action\" flags other than checking signature files. This is here " +
                        "to make it easier customize build system tasks, particularly for the \"make checkapi\" task.",
                "$ARG_REPEAT_ERRORS_MAX <N>", "When specified, repeat at most N errors before finishing.",

                "", "\nAPI sources:",
                "$ARG_SOURCE_FILES <files>",
                "A comma separated list of source files to be parsed. Can also be " +
                        "@ followed by a path to a text file containing paths to the full set of files to parse.",

                "$ARG_SOURCE_PATH <paths>",
                "One or more directories (separated by `${File.pathSeparator}`) " +
                        "containing source files (within a package hierarchy). If $ARG_STRICT_INPUT_FILES, " +
                        "$ARG_STRICT_INPUT_FILES_WARN, or $ARG_STRICT_INPUT_FILES_STACK are used, files accessed under " +
                        "$ARG_SOURCE_PATH that are not explicitly specified in $ARG_SOURCE_FILES are reported as " +
                        "violations.",

                "$ARG_CLASS_PATH <paths>",
                "One or more directories or jars (separated by " +
                        "`${File.pathSeparator}`) containing classes that should be on the classpath when parsing the " +
                        "source files",

                "$ARG_MERGE_QUALIFIER_ANNOTATIONS <file>",
                "An external annotations file to merge and overlay " +
                        "the sources, or a directory of such files. Should be used for annotations intended for " +
                        "inclusion in the API to be written out, e.g. nullability. Formats supported are: IntelliJ's " +
                        "external annotations database format, .jar or .zip files containing those, Android signature " +
                        "files, and Java stub files.",

                "$ARG_MERGE_INCLUSION_ANNOTATIONS <file>",
                "An external annotations file to merge and overlay " +
                        "the sources, or a directory of such files. Should be used for annotations which determine " +
                        "inclusion in the API to be written out, i.e. show and hide. The only format supported is " +
                        "Java stub files.",

                ARG_VALIDATE_NULLABILITY_FROM_MERGED_STUBS,
                "Triggers validation of nullability annotations " +
                        "for any class where $ARG_MERGE_QUALIFIER_ANNOTATIONS includes a Java stub file.",

                ARG_VALIDATE_NULLABILITY_FROM_LIST,
                "Triggers validation of nullability annotations " +
                        "for any class listed in the named file (one top-level class per line, # prefix for comment line).",

                "$ARG_NULLABILITY_WARNINGS_TXT <file>",
                "Specifies where to write warnings encountered during " +
                        "validation of nullability annotations. (Does not trigger validation by itself.)",

                ARG_NULLABILITY_ERRORS_NON_FATAL,
                "Specifies that errors encountered during validation of " +
                        "nullability annotations should not be treated as errors. They will be written out to the " +
                        "file specified in $ARG_NULLABILITY_WARNINGS_TXT instead.",

                "$ARG_INPUT_API_JAR <file>", "A .jar file to read APIs from directly",

                "$ARG_MANIFEST <file>", "A manifest file, used to for check permissions to cross check APIs",

                "$ARG_HIDE_PACKAGE <package>",
                "Remove the given packages from the API even if they have not been " +
                        "marked with @hide",

                "$ARG_SHOW_ANNOTATION <annotation class>",
                "Unhide any hidden elements that are also annotated " +
                        "with the given annotation",
                "$ARG_SHOW_SINGLE_ANNOTATION <annotation>",
                "Like $ARG_SHOW_ANNOTATION, but does not apply " +
                        "to members; these must also be explicitly annotated",
                "$ARG_SHOW_FOR_STUB_PURPOSES_ANNOTATION <annotation class>",
                "Like $ARG_SHOW_ANNOTATION, but elements annotated " +
                        "with it are assumed to be \"implicitly\" included in the API surface, and they'll be included " +
                        "in certain kinds of output such as stubs, but not in others, such as the signature file and API lint",
                "$ARG_HIDE_ANNOTATION <annotation class>",
                "Treat any elements annotated with the given annotation " +
                        "as hidden",
                "$ARG_HIDE_META_ANNOTATION <meta-annotation class>",
                "Treat as hidden any elements annotated with an " +
                        "annotation which is itself annotated with the given meta-annotation",
                ARG_SHOW_UNANNOTATED, "Include un-annotated public APIs in the signature file as well",
                "$ARG_JAVA_SOURCE <level>", "Sets the source level for Java source files; default is 1.8.",
                "$ARG_KOTLIN_SOURCE <level>", "Sets the source level for Kotlin source files; default is ${LanguageVersionSettingsImpl.DEFAULT.languageVersion}.",
                "$ARG_SDK_HOME <dir>", "If set, locate the `android.jar` file from the given Android SDK",
                "$ARG_COMPILE_SDK_VERSION <api>", "Use the given API level",
                "$ARG_JDK_HOME <dir>", "If set, add the Java APIs from the given JDK to the classpath",
                "$ARG_STUB_PACKAGES <package-list>",
                "List of packages (separated by ${File.pathSeparator}) which will " +
                        "be used to filter out irrelevant code. If specified, only code in these packages will be " +
                        "included in signature files, stubs, etc. (This is not limited to just the stubs; the name " +
                        "is historical.) You can also use \".*\" at the end to match subpackages, so `foo.*` will " +
                        "match both `foo` and `foo.bar`.",
                "$ARG_SUBTRACT_API <api file>",
                "Subtracts the API in the given signature or jar file from the " +
                        "current API being emitted via $ARG_API, $ARG_STUBS, $ARG_DOC_STUBS, etc. " +
                        "Note that the subtraction only applies to classes; it does not subtract members.",
                "$ARG_TYPEDEFS_IN_SIGNATURES <ref|inline>",
                "Whether to include typedef annotations in signature " +
                        "files. `$ARG_TYPEDEFS_IN_SIGNATURES ref` will include just a reference to the typedef class, " +
                        "which is not itself part of the API and is not included as a class, and " +
                        "`$ARG_TYPEDEFS_IN_SIGNATURES inline` will include the constants themselves into each usage " +
                        "site. You can also supply `$ARG_TYPEDEFS_IN_SIGNATURES none` to explicitly turn it off, if the " +
                        "default ever changes.",
                ARG_IGNORE_CLASSES_ON_CLASSPATH,
                "Prevents references to classes on the classpath from being added to " +
                        "the generated stub files.",

                "", "\nDocumentation:",
                ARG_PUBLIC, "Only include elements that are public",
                ARG_PROTECTED, "Only include elements that are public or protected",
                ARG_PACKAGE, "Only include elements that are public, protected or package protected",
                ARG_PRIVATE, "Include all elements except those that are marked hidden",
                ARG_HIDDEN, "Include all elements, including hidden",

                "", "\nExtracting Signature Files:",
                // TODO: Document --show-annotation!
                "$ARG_API <file>", "Generate a signature descriptor file",
                "$ARG_DEX_API <file>", "Generate a DEX signature descriptor file listing the APIs",
                "$ARG_REMOVED_API <file>", "Generate a signature descriptor file for APIs that have been removed",
                "$ARG_FORMAT=<v1,v2,v3,...>", "Sets the output signature file format to be the given version.",
                "$ARG_OUTPUT_KOTLIN_NULLS[=yes|no]",
                "Controls whether nullness annotations should be formatted as " +
                        "in Kotlin (with \"?\" for nullable types, \"\" for non nullable types, and \"!\" for unknown. " +
                        "The default is yes.",
                "$ARG_OUTPUT_DEFAULT_VALUES[=yes|no]",
                "Controls whether default values should be included in " +
                        "signature files. The default is yes.",
                "$ARG_INCLUDE_SIG_VERSION[=yes|no]",
                "Whether the signature files should include a comment listing " +
                        "the format version of the signature file.",

                "$ARG_PROGUARD <file>", "Write a ProGuard keep file for the API",
                "$ARG_SDK_VALUES <dir>", "Write SDK values files to the given directory",

                "", "\nGenerating Stubs:",
                "$ARG_STUBS <dir>", "Generate stub source files for the API",
                "$ARG_DOC_STUBS <dir>",
                "Generate documentation stub source files for the API. Documentation stub " +
                        "files are similar to regular stub files, but there are some differences. For example, in " +
                        "the stub files, we'll use special annotations like @RecentlyNonNull instead of @NonNull to " +
                        "indicate that an element is recently marked as non null, whereas in the documentation stubs we'll " +
                        "just list this as @NonNull. Another difference is that @doconly elements are included in " +
                        "documentation stubs, but not regular stubs, etc.",
                ARG_KOTLIN_STUBS,
                "[CURRENTLY EXPERIMENTAL] If specified, stubs generated from Kotlin source code will " +
                        "be written in Kotlin rather than the Java programming language.",
                ARG_INCLUDE_ANNOTATIONS, "Include annotations such as @Nullable in the stub files.",
                ARG_EXCLUDE_ALL_ANNOTATIONS, "Exclude annotations such as @Nullable from the stub files; the default.",
                "$ARG_PASS_THROUGH_ANNOTATION <annotation classes>",
                "A comma separated list of fully qualified names of " +
                        "annotation classes that must be passed through unchanged.",
                "$ARG_EXCLUDE_ANNOTATION <annotation classes>",
                "A comma separated list of fully qualified names of " +
                        "annotation classes that must be stripped from metalava's outputs.",
                ARG_ENHANCE_DOCUMENTATION,
                "Enhance documentation in various ways, for example auto-generating documentation based on source " +
                        "annotations present in the code. This is implied by --doc-stubs.",
                ARG_EXCLUDE_DOCUMENTATION_FROM_STUBS,
                "Exclude element documentation (javadoc and kdoc) " +
                        "from the generated stubs. (Copyright notices are not affected by this, they are always included. " +
                        "Documentation stubs (--doc-stubs) are not affected.)",
                "$ARG_STUBS_SOURCE_LIST <file>",
                "Write the list of generated stub files into the given source " +
                        "list file. If generating documentation stubs and you haven't also specified " +
                        "$ARG_DOC_STUBS_SOURCE_LIST, this list will refer to the documentation stubs; " +
                        "otherwise it's the non-documentation stubs.",
                "$ARG_DOC_STUBS_SOURCE_LIST <file>",
                "Write the list of generated doc stub files into the given source " +
                        "list file",

                "", "\nDiffs and Checks:",
                "$ARG_INPUT_KOTLIN_NULLS[=yes|no]",
                "Whether the signature file being read should be " +
                        "interpreted as having encoded its types using Kotlin style types: a suffix of \"?\" for nullable " +
                        "types, no suffix for non nullable types, and \"!\" for unknown. The default is no.",
                "--check-compatibility:type:released <file>",
                "Check compatibility. Type is one of 'api' " +
                        "and 'removed', which checks either the public api or the removed api.",
                "$ARG_CHECK_COMPATIBILITY_BASE_API <file>",
                "When performing a compat check, use the provided signature " +
                        "file as a base api, which is treated as part of the API being checked. This allows us to compute the " +
                        "full API surface from a partial API surface (e.g. the current @SystemApi txt file), which allows us to " +
                        "recognize when an API is moved from the partial API to the base API and avoid incorrectly flagging this " +
                        "as an API removal.",
                "$ARG_API_LINT [api file]",
                "Check API for Android API best practices. If a signature file is " +
                        "provided, only the APIs that are new since the API will be checked.",
                "$ARG_API_LINT_IGNORE_PREFIX [prefix]",
                "A list of package prefixes to ignore API issues in " +
                        "when running with $ARG_API_LINT.",
                "$ARG_MIGRATE_NULLNESS <api file>",
                "Compare nullness information with the previous stable API " +
                        "and mark newly annotated APIs as under migration.",
                ARG_WARNINGS_AS_ERRORS, "Promote all warnings to errors",
                ARG_LINTS_AS_ERRORS, "Promote all API lint warnings to errors",
                "$ARG_ERROR <id>", "Report issues of the given id as errors",
                "$ARG_WARNING <id>", "Report issues of the given id as warnings",
                "$ARG_LINT <id>", "Report issues of the given id as having lint-severity",
                "$ARG_HIDE <id>", "Hide/skip issues of the given id",
                "$ARG_REPORT_EVEN_IF_SUPPRESSED <file>", "Write all issues into the given file, even if suppressed (via annotation or baseline) but not if hidden (by '$ARG_HIDE')",
                "$ARG_BASELINE <file>",
                "Filter out any errors already reported in the given baseline file, or " +
                        "create if it does not already exist",
                "$ARG_UPDATE_BASELINE [file]",
                "Rewrite the existing baseline file with the current set of warnings. " +
                        "If some warnings have been fixed, this will delete them from the baseline files. If a file " +
                        "is provided, the updated baseline is written to the given file; otherwise the original source " +
                        "baseline file is updated.",
                "$ARG_BASELINE_API_LINT <file> $ARG_UPDATE_BASELINE_API_LINT [file]",
                "Same as $ARG_BASELINE and " +
                        "$ARG_UPDATE_BASELINE respectively, but used specifically for API lint issues performed by " +
                        "$ARG_API_LINT.",
                "$ARG_BASELINE_CHECK_COMPATIBILITY_RELEASED <file> $ARG_UPDATE_BASELINE_CHECK_COMPATIBILITY_RELEASED [file]",
                "Same as $ARG_BASELINE and " +
                        "$ARG_UPDATE_BASELINE respectively, but used specifically for API compatibility issues performed by " +
                        "$ARG_CHECK_COMPATIBILITY_API_RELEASED and $ARG_CHECK_COMPATIBILITY_REMOVED_RELEASED.",
                "$ARG_MERGE_BASELINE [file]",
                "Like $ARG_UPDATE_BASELINE, but instead of always replacing entries " +
                        "in the baseline, it will merge the existing baseline with the new baseline. This is useful " +
                        "if $PROGRAM_NAME runs multiple times on the same source tree with different flags at different " +
                        "times, such as occasionally with $ARG_API_LINT.",
                ARG_PASS_BASELINE_UPDATES,
                "Normally, encountering error will fail the build, even when updating " +
                        "baselines. This flag allows you to tell $PROGRAM_NAME to continue without errors, such that " +
                        "all the baselines in the source tree can be updated in one go.",
                ARG_DELETE_EMPTY_BASELINES,
                "Whether to delete baseline files if they are updated and there is nothing " +
                        "to include.",
                "$ARG_ERROR_MESSAGE_API_LINT <message>", "If set, $PROGRAM_NAME shows it when errors are detected in $ARG_API_LINT.",
                "$ARG_ERROR_MESSAGE_CHECK_COMPATIBILITY_RELEASED <message>",
                "If set, $PROGRAM_NAME shows it " +
                        "when errors are detected in $ARG_CHECK_COMPATIBILITY_API_RELEASED and $ARG_CHECK_COMPATIBILITY_REMOVED_RELEASED.",

                "", "\nJDiff:",
                "$ARG_XML_API <file>", "Like $ARG_API, but emits the API in the JDiff XML format instead",
                "$ARG_CONVERT_TO_JDIFF <sig> <xml>",
                "Reads in the given signature file, and writes it out " +
                        "in the JDiff XML format. Can be specified multiple times.",
                "$ARG_CONVERT_NEW_TO_JDIFF <old> <new> <xml>",
                "Reads in the given old and new api files, " +
                        "computes the difference, and writes out only the new parts of the API in the JDiff XML format.",

                "", "\nExtracting Annotations:",
                "$ARG_EXTRACT_ANNOTATIONS <zipfile>",
                "Extracts source annotations from the source files and writes " +
                        "them into the given zip file",
                "$ARG_FORCE_CONVERT_TO_WARNING_NULLABILITY_ANNOTATIONS <package1:-package2:...>",
                "On every API declared " +
                        "in a class referenced by the given filter, makes nullability issues appear to callers as warnings " +
                        "rather than errors by replacing @Nullable/@NonNull in these APIs with " +
                        "@RecentlyNullable/@RecentlyNonNull",
                "$ARG_COPY_ANNOTATIONS <source> <dest>",
                "For a source folder full of annotation " +
                        "sources, generates corresponding package private versions of the same annotations.",
                ARG_INCLUDE_SOURCE_RETENTION,
                "If true, include source-retention annotations in the stub files. Does " +
                        "not apply to signature files. Source retention annotations are extracted into the external " +
                        "annotations files instead.",
                "", "\nInjecting API Levels:",
                "$ARG_APPLY_API_LEVELS <api-versions.xml>",
                "Reads an XML file containing API level descriptions " +
                        "and merges the information into the documentation",

                "", "\nExtracting API Levels:",
                "$ARG_GENERATE_API_LEVELS <xmlfile>",
                "Reads android.jar SDK files and generates an XML file recording " +
                        "the API level for each class, method and field",
                "$ARG_REMOVE_MISSING_CLASS_REFERENCES_IN_API_LEVELS",
                "Removes references to missing classes when generating the API levels XML file. " +
                        "This can happen when generating the XML file for the non-updatable portions of " +
                        "the module-lib sdk, as those non-updatable portions can reference classes that are " +
                        "part of an updatable apex.",
                "$ARG_ANDROID_JAR_PATTERN <pattern>",
                "Patterns to use to locate Android JAR files. The default " +
                        "is \$ANDROID_HOME/platforms/android-%/android.jar.",
                ARG_FIRST_VERSION, "Sets the first API level to generate an API database from; usually 1",
                ARG_CURRENT_VERSION, "Sets the current API level of the current source code",
                ARG_CURRENT_CODENAME, "Sets the code name for the current source code",
                ARG_CURRENT_JAR, "Points to the current API jar, if any",
                ARG_SDK_JAR_ROOT,
                "Points to root of prebuilt extension SDK jars, if any. This directory is expected to " +
                        "contain snapshots of historical extension SDK versions in the form of stub jars. " +
                        "The paths should be on the format \"<int>/public/<module-name>.jar\", where <int> " +
                        "corresponds to the extension SDK version, and <module-name> to the name of the mainline module.",
                ARG_SDK_INFO_FILE,
                "Points to map of extension SDK APIs to include, if any. The file is a plain text file " +
                        "and describes, per extension SDK, what APIs from that extension to include in the " +
                        "file created via $ARG_GENERATE_API_LEVELS. The format of each line is one of the following: " +
                        "\"<module-name> <pattern> <ext-name> [<ext-name> [...]]\", where <module-name> is the " +
                        "name of the mainline module this line refers to, <pattern> is a common Java name prefix " +
                        "of the APIs this line refers to, and <ext-name> is a list of extension SDK names " +
                        "in which these SDKs first appeared, or \"<ext-name> <ext-id> <type>\", where " +
                        "<ext-name> is the name of an SDK, " +
                        "<ext-id> its numerical ID and <type> is one of " +
                        "\"platform\" (the Android platform SDK), " +
                        "\"platform-ext\" (an extension to the Android platform SDK), " +
                        "\"standalone\" (a separate SDK). " +
                        "Fields are separated by whitespace. " +
                        "A mainline module may be listed multiple times. " +
                        "The special pattern \"*\" refers to all APIs in the given mainline module. " +
                        "Lines beginning with # are comments.",

                "", "\nSandboxing:",
                ARG_NO_IMPLICIT_ROOT,
                "Disable implicit root directory detection. " +
                        "Otherwise, $PROGRAM_NAME adds in source roots implied by the source files",
                "$ARG_STRICT_INPUT_FILES <file>",
                "Do not read files that are not explicitly specified in the command line. " +
                        "All violations are written to the given file. Reads on directories are always allowed, but " +
                        "$PROGRAM_NAME still tracks reads on directories that are not specified in the command line, " +
                        "and write them to the file.",
                "$ARG_STRICT_INPUT_FILES_WARN <file>",
                "Warn when files not explicitly specified on the command line are " +
                        "read. All violations are written to the given file. Reads on directories not specified in the command " +
                        "line are allowed but also logged.",
                "$ARG_STRICT_INPUT_FILES_STACK <file>", "Same as $ARG_STRICT_INPUT_FILES but also print stacktraces.",
                "$ARG_STRICT_INPUT_FILES_EXEMPT <files or dirs>",
                "Used with $ARG_STRICT_INPUT_FILES. Explicitly allow " +
                        "access to files and/or directories (separated by `${File.pathSeparator}). Can also be " +
                        "@ followed by a path to a text file containing paths to the full set of files and/or directories.",

                "", "\nEnvironment Variables:",
                ENV_VAR_METALAVA_DUMP_ARGV,
                "Set to true to have metalava emit all the arguments it was invoked with. " +
                        "Helpful when debugging or reproducing under a debugger what the build system is doing.",
                ENV_VAR_METALAVA_PREPEND_ARGS,
                "One or more arguments (concatenated by space) to insert into the " +
                        "command line, before the documentation flags.",
                ENV_VAR_METALAVA_APPEND_ARGS,
                "One or more arguments (concatenated by space) to append to the " +
                        "end of the command line, after the generate documentation flags."
        )

        val sb = StringBuilder(INDENT_WIDTH)
        for (indent in 0 until INDENT_WIDTH) {
            sb.append(' ')
        }
        val indent = sb.toString()
        val formatString = "%1$-" + INDENT_WIDTH + "s%2\$s"

        var i = 0
        while (i < args.size) {
            val arg = args[i]
            val description = "\n" + args[i + 1]
            if (arg.isEmpty()) {
                if (colorize) {
                    out.println(colorized(description, TerminalColor.YELLOW))
                } else {
                    out.println(description)
                }
            } else {
                val output =
                        if (colorize) {
                            val colorArg = bold(arg)
                            val invisibleChars = colorArg.length - arg.length
                            // +invisibleChars: the extra chars in the above are counted but don't contribute to width
                            // so allow more space
                            val colorFormatString = "%1$-" + (INDENT_WIDTH + invisibleChars) + "s%2\$s"

                            wrap(
                                    String.format(colorFormatString, colorArg, description),
                                    MAX_LINE_WIDTH + invisibleChars, MAX_LINE_WIDTH, indent
                            )
                        } else {
                            wrap(
                                    String.format(formatString, arg, description),
                                    MAX_LINE_WIDTH, indent
                            )
                        }

                // Remove trailing whitespace
                val lines = output.lines()
                lines.forEachIndexed { index, line ->
                    out.print(line.trimEnd())
                    if (index < lines.size - 1) {
                        out.println()
                    }
                }
            }
            i += 2
        }
    }

    companion object {
        private fun setIssueSeverity(
                id: String,
                severity: Severity,
                arg: String
        ) {
            if (id.contains(",")) { // Handle being passed in multiple comma separated id's
                id.split(",").forEach {
                    setIssueSeverity(it.trim(), severity, arg)
                }
                return
            }
            val issue = Issues.findIssueById(id)
                    ?: Issues.findIssueByIdIgnoringCase(id)?.also {
                        reporter.report(
                                Issues.DEPRECATED_OPTION, null as File?,
                                "Case-insensitive issue matching is deprecated, use " +
                                        "$arg ${it.name} instead of $arg $id"
                        )
                    } ?: throw DriverException("Unknown issue id: $arg $id")

            defaultConfiguration.setSeverity(issue, severity)
        }
    }
}
