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

package com.android.tools.metalava.model

import com.android.SdkConstants
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.INT_DEF_ANNOTATION
import com.android.SdkConstants.LONG_DEF_ANNOTATION
import com.android.SdkConstants.STRING_DEF_ANNOTATION
import com.android.tools.lint.annotations.Extractor.ANDROID_INT_DEF
import com.android.tools.lint.annotations.Extractor.ANDROID_LONG_DEF
import com.android.tools.lint.annotations.Extractor.ANDROID_STRING_DEF
import com.android.tools.metalava.ANDROIDX_ANNOTATION_PREFIX
import com.android.tools.metalava.ANDROIDX_NONNULL
import com.android.tools.metalava.ANDROIDX_NULLABLE
import com.android.tools.metalava.ANDROID_NONNULL
import com.android.tools.metalava.ANDROID_NULLABLE
import com.android.tools.metalava.ApiPredicate
import com.android.tools.metalava.JAVA_LANG_PREFIX
import com.android.tools.metalava.Options
import com.android.tools.metalava.RECENTLY_NONNULL
import com.android.tools.metalava.RECENTLY_NULLABLE
import com.android.tools.metalava.model.psi.PsiBasedCodebase
import com.android.tools.metalava.options
import com.intellij.psi.PsiCallExpression
import com.intellij.psi.PsiField
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiReference
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.uast.UElement

fun isNullableAnnotation(qualifiedName: String): Boolean {
    return qualifiedName.endsWith("Nullable")
}

fun isNonNullAnnotation(qualifiedName: String): Boolean {
    return qualifiedName.endsWith("NonNull") ||
        qualifiedName.endsWith("NotNull") ||
        qualifiedName.endsWith("Nonnull")
}

fun isJvmSyntheticAnnotation(qualifiedName: String): Boolean {
    return qualifiedName == "kotlin.jvm.JvmSynthetic"
}

interface AnnotationItem {
    val codebase: Codebase

    /** Fully qualified name of the annotation */
    val qualifiedName: String?

    /** Fully qualified name of the annotation (prior to name mapping) */
    val originalName: String?

    /** Generates source code for this annotation (using fully qualified names) */
    fun toSource(
        target: AnnotationTarget = AnnotationTarget.SIGNATURE_FILE,
        showDefaultAttrs: Boolean = true
    ): String

    /** The applicable targets for this annotation */
    val targets: Set<AnnotationTarget>

    /** Attributes of the annotation (may be empty) */
    val attributes: List<AnnotationAttribute>

    /** True if this annotation represents @Nullable or @NonNull (or some synonymous annotation) */
    fun isNullnessAnnotation(): Boolean {
        return isNullable() || isNonNull()
    }

    /** True if this annotation represents @Nullable (or some synonymous annotation) */
    fun isNullable(): Boolean {
        return isNullableAnnotation(qualifiedName ?: return false)
    }

    /** True if this annotation represents @NonNull (or some synonymous annotation) */
    fun isNonNull(): Boolean {
        return isNonNullAnnotation(qualifiedName ?: return false)
    }

    /** True if this annotation represents @JvmSynthetic */
    fun isJvmSynthetic(): Boolean {
        return isJvmSyntheticAnnotation(qualifiedName ?: return false)
    }

    /** True if this annotation represents @IntDef, @LongDef or @StringDef */
    fun isTypeDefAnnotation(): Boolean {
        val name = qualifiedName ?: return false
        if (!(name.endsWith("Def"))) {
            return false
        }
        return (
            INT_DEF_ANNOTATION.isEquals(name) ||
                STRING_DEF_ANNOTATION.isEquals(name) ||
                LONG_DEF_ANNOTATION.isEquals(name) ||
                ANDROID_INT_DEF == name ||
                ANDROID_STRING_DEF == name ||
                ANDROID_LONG_DEF == name
            )
    }

    /**
     * True if this annotation represents a @ParameterName annotation (or some synonymous annotation).
     * The parameter name should be the default attribute or "value".
     */
    fun isParameterName(): Boolean {
        return qualifiedName?.endsWith(".ParameterName") ?: return false
    }

    /**
     * True if this annotation represents a @DefaultValue annotation (or some synonymous annotation).
     * The default value should be the default attribute or "value".
     */
    fun isDefaultValue(): Boolean {
        return qualifiedName?.endsWith(".DefaultValue") ?: return false
    }

    /** Returns the given named attribute if specified */
    fun findAttribute(name: String?): AnnotationAttribute? {
        val actualName = name ?: ATTR_VALUE
        return attributes.firstOrNull { it.name == actualName }
    }

    /** Find the class declaration for the given annotation */
    fun resolve(): ClassItem? {
        return codebase.findClass(qualifiedName ?: return null)
    }

    /** If this annotation has a typedef annotation associated with it, return it */
    fun findTypedefAnnotation(): AnnotationItem? {
        val className = originalName ?: return null
        return codebase.findClass(className)?.modifiers?.annotations()?.firstOrNull { it.isTypeDefAnnotation() }
    }

    /** Returns the retention of this annotation */
    val retention: AnnotationRetention
        get() {
            val name = qualifiedName
            if (name != null) {
                val cls = codebase.findClass(name) ?: (codebase as? PsiBasedCodebase)?.findOrCreateClass(name)
                if (cls != null) {
                    if (cls.isAnnotationType()) {
                        return cls.getRetention()
                    }
                }
            }

            return AnnotationRetention.CLASS
        }

    companion object {
        /** The simple name of an annotation, which is the annotation name (not qualified name) prefixed by @ */
        fun simpleName(item: AnnotationItem): String {
            return item.qualifiedName?.let { "@${it.substringAfterLast('.')}" }.orEmpty()
        }

        /**
         * Maps an annotation name to the name to be used in signatures/stubs/external annotation files.
         * Annotations that should not be exported are mapped to null.
         */
        fun mapName(
            qualifiedName: String?,
            target: AnnotationTarget = AnnotationTarget.SIGNATURE_FILE
        ): String? {
            qualifiedName ?: return null
            if (options.passThroughAnnotations.contains(qualifiedName) ||
                options.showAnnotations.matches(qualifiedName) ||
                options.hideAnnotations.matches(qualifiedName)
            ) {
                return qualifiedName
            }
            if (options.excludeAnnotations.contains(qualifiedName)) {
                return null
            }

            when (qualifiedName) {
                // Resource annotations
                "android.annotation.AnimRes" -> return "androidx.annotation.AnimRes"
                "android.annotation.AnimatorRes" -> return "androidx.annotation.AnimatorRes"
                "android.annotation.AnyRes" -> return "androidx.annotation.AnyRes"
                "android.annotation.ArrayRes" -> return "androidx.annotation.ArrayRes"
                "android.annotation.AttrRes" -> return "androidx.annotation.AttrRes"
                "android.annotation.BoolRes" -> return "androidx.annotation.BoolRes"
                "android.annotation.ColorRes" -> return "androidx.annotation.ColorRes"
                "android.annotation.DimenRes" -> return "androidx.annotation.DimenRes"
                "android.annotation.DrawableRes" -> return "androidx.annotation.DrawableRes"
                "android.annotation.FontRes" -> return "androidx.annotation.FontRes"
                "android.annotation.FractionRes" -> return "androidx.annotation.FractionRes"
                "android.annotation.IdRes" -> return "androidx.annotation.IdRes"
                "android.annotation.IntegerRes" -> return "androidx.annotation.IntegerRes"
                "android.annotation.InterpolatorRes" -> return "androidx.annotation.InterpolatorRes"
                "android.annotation.LayoutRes" -> return "androidx.annotation.LayoutRes"
                "android.annotation.MenuRes" -> return "androidx.annotation.MenuRes"
                "android.annotation.PluralsRes" -> return "androidx.annotation.PluralsRes"
                "android.annotation.RawRes" -> return "androidx.annotation.RawRes"
                "android.annotation.StringRes" -> return "androidx.annotation.StringRes"
                "android.annotation.StyleRes" -> return "androidx.annotation.StyleRes"
                "android.annotation.StyleableRes" -> return "androidx.annotation.StyleableRes"
                "android.annotation.TransitionRes" -> return "androidx.annotation.TransitionRes"
                "android.annotation.XmlRes" -> return "androidx.annotation.XmlRes"

                // Threading
                "android.annotation.AnyThread" -> return "androidx.annotation.AnyThread"
                "android.annotation.BinderThread" -> return "androidx.annotation.BinderThread"
                "android.annotation.MainThread" -> return "androidx.annotation.MainThread"
                "android.annotation.UiThread" -> return "androidx.annotation.UiThread"
                "android.annotation.WorkerThread" -> return "androidx.annotation.WorkerThread"

                // Colors
                "android.annotation.ColorInt" -> return "androidx.annotation.ColorInt"
                "android.annotation.ColorLong" -> return "androidx.annotation.ColorLong"
                "android.annotation.HalfFloat" -> return "androidx.annotation.HalfFloat"

                // Ranges and sizes
                "android.annotation.FloatRange" -> return "androidx.annotation.FloatRange"
                "android.annotation.IntRange" -> return "androidx.annotation.IntRange"
                "android.annotation.Size" -> return "androidx.annotation.Size"
                "android.annotation.Px" -> return "androidx.annotation.Px"
                "android.annotation.Dimension" -> return "androidx.annotation.Dimension"

                // Null
                // We only change recently/newly nullable annotation in stubs
                RECENTLY_NULLABLE -> return if (target == AnnotationTarget.SDK_STUBS_FILE) qualifiedName else ANDROIDX_NULLABLE
                RECENTLY_NONNULL -> return if (target == AnnotationTarget.SDK_STUBS_FILE) qualifiedName else ANDROIDX_NONNULL

                ANDROIDX_NULLABLE,
                ANDROID_NULLABLE,
                "libcore.util.Nullable",
                "org.jetbrains.annotations.Nullable" -> return nullableAnnotationName(target)

                ANDROIDX_NONNULL,
                ANDROID_NONNULL,
                "libcore.util.NonNull",
                "org.jetbrains.annotations.NotNull" -> return nonNullAnnotationName(target)

                // Typedefs
                "android.annotation.IntDef" -> return "androidx.annotation.IntDef"
                "android.annotation.StringDef" -> return "androidx.annotation.StringDef"
                "android.annotation.LongDef" -> return "androidx.annotation.LongDef"

                // Context Types
                "android.annotation.UiContext" -> return "androidx.annotation.UiContext"
                "android.annotation.DisplayContext" -> return "androidx.annotation.DisplayContext"
                "android.annotation.NonUiContext" -> return "androidx.annotation.NonUiContext"

                // Misc
                "android.annotation.DeprecatedForSdk" -> return "java.lang.Deprecated"
                "android.annotation.CallSuper" -> return "androidx.annotation.CallSuper"
                "android.annotation.CheckResult" -> return "androidx.annotation.CheckResult"
                "android.annotation.Discouraged" -> return "androidx.annotation.Discouraged"
                "android.annotation.RequiresPermission" -> return "androidx.annotation.RequiresPermission"
                "android.annotation.RequiresPermission.Read" -> return "androidx.annotation.RequiresPermission.Read"
                "android.annotation.RequiresPermission.Write" -> return "androidx.annotation.RequiresPermission.Write"

                // These aren't support annotations, but could/should be:
                "android.annotation.CurrentTimeMillisLong",
                "android.annotation.DurationMillisLong",
                "android.annotation.ElapsedRealtimeLong",
                "android.annotation.UserIdInt",
                "android.annotation.BytesLong",

                // These aren't support annotations
                "android.annotation.AppIdInt",
                "android.annotation.SuppressAutoDoc",
                "android.annotation.SystemApi",
                "android.annotation.TestApi",
                "android.annotation.CallbackExecutor",
                "android.annotation.Condemned",
                "android.annotation.Hide",

                "android.annotation.Widget" -> return qualifiedName

                // Included for analysis, but should not be exported:
                "android.annotation.BroadcastBehavior",
                "android.annotation.SdkConstant",
                "android.annotation.RequiresFeature",
                "android.annotation.SystemService" -> return qualifiedName

                // Should not be mapped to a different package name:
                "android.annotation.TargetApi",
                "android.annotation.SuppressLint" -> return qualifiedName

                else -> {
                    // Some new annotations added to the platform: assume they are support annotations?
                    return when {
                        // Special Kotlin annotations recognized by the compiler: map to supported package name
                        qualifiedName.endsWith(".ParameterName") || qualifiedName.endsWith(".DefaultValue") ->
                            "kotlin.annotations.jvm.internal${qualifiedName.substring(qualifiedName.lastIndexOf('.'))}"

                        // Other third party nullness annotations?
                        isNullableAnnotation(qualifiedName) -> nullableAnnotationName(target)
                        isNonNullAnnotation(qualifiedName) -> nonNullAnnotationName(target)

                        // AndroidX annotations are all included, as is the built-in stuff like @Retention
                        qualifiedName.startsWith(ANDROIDX_ANNOTATION_PREFIX) -> return qualifiedName
                        qualifiedName.startsWith(JAVA_LANG_PREFIX) -> return qualifiedName

                        // Unknown Android platform annotations
                        qualifiedName.startsWith("android.annotation.") -> {
                            return null
                        }

                        else -> qualifiedName
                    }
                }
            }
        }

        private fun nullableAnnotationName(target: AnnotationTarget) =
            if (target == AnnotationTarget.SDK_STUBS_FILE) ANDROID_NULLABLE else ANDROIDX_NULLABLE

        private fun nonNullAnnotationName(target: AnnotationTarget) =
            if (target == AnnotationTarget.SDK_STUBS_FILE) ANDROID_NONNULL else ANDROIDX_NONNULL

        private val TYPEDEF_ANNOTATION_TARGETS =
            if (options.typedefMode == Options.TypedefMode.INLINE ||
                options.typedefMode == Options.TypedefMode.NONE
            ) // just here for compatibility purposes
                ANNOTATION_EXTERNAL
            else
                ANNOTATION_EXTERNAL_ONLY

        /** The applicable targets for this annotation */
        fun computeTargets(
            annotation: AnnotationItem,
            classFinder: (String) -> ClassItem?
        ): Set<AnnotationTarget> {
            val qualifiedName = annotation.qualifiedName ?: return NO_ANNOTATION_TARGETS
            if (options.passThroughAnnotations.contains(qualifiedName)) {
                return ANNOTATION_IN_ALL_STUBS
            }
            when (qualifiedName) {

                // The typedef annotations are special: they should not be in the signature
                // files, but we want to include them in the external annotations file such that tools
                // can enforce them.
                "android.annotation.IntDef",
                "androidx.annotation.IntDef",
                "android.annotation.StringDef",
                "androidx.annotation.StringDef",
                "android.annotation.LongDef",
                "androidx.annotation.LongDef" -> return TYPEDEF_ANNOTATION_TARGETS

                // Not directly API relevant
                "android.view.ViewDebug.ExportedProperty",
                "android.view.ViewDebug.CapturedViewProperty" -> return ANNOTATION_STUBS_ONLY

                // Retained in the sdk/jar stub source code so that SdkConstant files can be extracted
                // from those. This is useful for modularizing the main SDK stubs without having to
                // add a separate module SDK artifact for sdk constants.
                "android.annotation.SdkConstant" -> return ANNOTATION_SDK_STUBS_ONLY

                // Skip known annotations that we (a) never want in external annotations and (b) we are
                // specially overwriting anyway in the stubs (and which are (c) not API significant)
                "com.android.modules.annotation.MinSdk",
                "java.lang.annotation.Native",
                "java.lang.SuppressWarnings",
                "java.lang.Override",
                "kotlin.Suppress",
                "androidx.annotation.experimental.UseExperimental",
                "androidx.annotation.OptIn",
                "kotlin.UseExperimental",
                "kotlin.OptIn" -> return NO_ANNOTATION_TARGETS

                // These optimization-related annotations shouldn't be exported.
                "dalvik.annotation.optimization.CriticalNative",
                "dalvik.annotation.optimization.FastNative",
                "dalvik.annotation.optimization.NeverCompile",
                "dalvik.annotation.optimization.NeverInline",
                "dalvik.annotation.optimization.ReachabilitySensitive" ->
                    return NO_ANNOTATION_TARGETS

                // TODO(aurimas): consider using annotation directly instead of modifiers
                "kotlin.Deprecated" -> return NO_ANNOTATION_TARGETS // tracked separately as a pseudo-modifier
                "android.annotation.DeprecatedForSdk",
                "java.lang.Deprecated", // tracked separately as a pseudo-modifier

                // Below this when-statement we perform the correct lookup: check API predicate, and check
                // that retention is class or runtime, but we've hardcoded the answers here
                // for some common annotations.

                "android.widget.RemoteViews.RemoteView",

                "kotlin.annotation.Target",
                "kotlin.annotation.Retention",
                "kotlin.annotation.Repeatable",
                "kotlin.annotation.MustBeDocumented",
                "kotlin.DslMarker",
                "kotlin.PublishedApi",
                "kotlin.ExtensionFunctionType",

                "java.lang.FunctionalInterface",
                "java.lang.SafeVarargs",
                "java.lang.annotation.Documented",
                "java.lang.annotation.Inherited",
                "java.lang.annotation.Repeatable",
                "java.lang.annotation.Retention",
                "java.lang.annotation.Target" -> return ANNOTATION_IN_ALL_STUBS

                // Metalava already tracks all the methods that get generated due to these annotations.
                "kotlin.jvm.JvmOverloads",
                "kotlin.jvm.JvmField",
                "kotlin.jvm.JvmStatic",
                "kotlin.jvm.JvmName" -> return NO_ANNOTATION_TARGETS
            }

            // @android.annotation.Nullable and NonNullable specially recognized annotations by the Kotlin
            // compiler 1.3 and above: they always go in the stubs.
            if (qualifiedName == ANDROID_NULLABLE ||
                qualifiedName == ANDROID_NONNULL ||
                qualifiedName == ANDROIDX_NULLABLE ||
                qualifiedName == ANDROIDX_NONNULL
            ) {
                return ANNOTATION_IN_ALL_STUBS
            }

            if (qualifiedName.startsWith("android.annotation.")) {
                // internal annotations not mapped to androidx: things like @SystemApi. Skip from
                // stubs, external annotations, signature files, etc.
                return NO_ANNOTATION_TARGETS
            }

            // @RecentlyNullable and @RecentlyNonNull are specially recognized annotations by the Kotlin
            // compiler: they always go in the stubs.
            if (qualifiedName == RECENTLY_NULLABLE ||
                qualifiedName == RECENTLY_NONNULL
            ) {
                return ANNOTATION_IN_ALL_STUBS
            }

            // Determine the retention of the annotation: source retention annotations go
            // in the external annotations file, class and runtime annotations go in
            // the stubs files (except for the androidx annotations which are not included
            // in the SDK and therefore cannot be referenced from it due to apt's unfortunate
            // habit of loading all annotation classes it encounters.)

            if (qualifiedName.startsWith("androidx.annotation.")) {
                if (qualifiedName == ANDROIDX_NULLABLE || qualifiedName == ANDROIDX_NONNULL) {
                    // Right now, nullness annotations (other than @RecentlyNullable and @RecentlyNonNull)
                    // have to go in external annotations since they aren't in the class path for
                    // annotation processors. However, we do want them showing up in the documentation using
                    // their real annotation names.
                    return ANNOTATION_IN_DOC_STUBS_AND_EXTERNAL
                }

                return ANNOTATION_EXTERNAL
            }

            // See if the annotation is pointing to an annotation class that is part of the API; if not, skip it.
            val cls = classFinder(qualifiedName) ?: return NO_ANNOTATION_TARGETS
            if (!ApiPredicate().test(cls)) {
                if (options.typedefMode != Options.TypedefMode.NONE) {
                    if (cls.modifiers.annotations().any { it.isTypeDefAnnotation() }) {
                        return ANNOTATION_SIGNATURE_ONLY
                    }
                }

                return NO_ANNOTATION_TARGETS
            }

            if (cls.isAnnotationType()) {
                val retention = cls.getRetention()
                if (retention == AnnotationRetention.RUNTIME || retention == AnnotationRetention.CLASS) {
                    return ANNOTATION_IN_ALL_STUBS
                }
            }

            return ANNOTATION_EXTERNAL
        }

        /**
         * Given a "full" annotation name, shortens it by removing redundant package names.
         * This is intended to be used to reduce clutter in signature files.
         *
         * For example, this method will convert `@androidx.annotation.Nullable` to just
         * `@Nullable`, and `@androidx.annotation.IntRange(from=20)` to `IntRange(from=20)`.
         */
        fun shortenAnnotation(source: String): String {
            return when {
                source == "@java.lang.Deprecated" -> "@Deprecated"
                source.startsWith("android.annotation.", 1) -> {
                    "@" + source.substring("@android.annotation.".length)
                }
                source.startsWith(ANDROIDX_ANNOTATION_PREFIX, 1) -> {
                    "@" + source.substring("@androidx.annotation.".length)
                }
                else -> source
            }
        }

        /**
         * Reverses the [shortenAnnotation] method. Intended for use when reading in signature files
         * that contain shortened type references.
         */
        fun unshortenAnnotation(source: String): String {
            return when {
                source == "@Deprecated" -> "@java.lang.Deprecated"
                // The first 3 annotations are in the android.annotation. package, not androidx.annotation
                // Nullability annotations are written as @NonNull and @Nullable in API text files,
                // and these should be linked no android.annotation package when generating stubs.
                source.startsWith("@SystemService") ||
                    source.startsWith("@TargetApi") ||
                    source.startsWith("@SuppressLint") ||
                    source.startsWith("@Nullable") ||
                    source.startsWith("@NonNull") ->
                    "@android.annotation." + source.substring(1)
                // If the first character of the name (after "@") is lower-case, then
                // assume it's a package name, so no need to shorten it.
                source.startsWith("@") && source[1].isLowerCase() -> source
                else -> {
                    "@androidx.annotation." + source.substring(1)
                }
            }
        }

        /**
         * If the given element has an *implicit* nullness, return it. This returns
         * true for implicitly nullable elements, such as the parameter to the equals
         * method, false for implicitly non null elements (such as annotation type
         * members), and null if there is no implicit nullness.
         */
        fun getImplicitNullness(item: Item): Boolean? {
            var nullable: Boolean? = null

            // Is this a Kotlin object declaration (such as a companion object) ?
            // If so, it is always non null.
            val sourcePsi = item.psi()
            if (sourcePsi is UElement && sourcePsi.sourcePsi is KtObjectDeclaration) {
                nullable = false
            }

            // Constant field not initialized to null?
            if (item is FieldItem &&
                (item.isEnumConstant() || item.modifiers.isFinal() && item.initialValue(false) != null)
            ) {
                // Assigned to constant: not nullable
                nullable = false
            } else if (item is FieldItem && item.modifiers.isFinal()) {
                // If we're looking at a final field, look at the right hand side
                // of the field to the field initialization. If that right hand side
                // for example represents a method call, and the method we're calling
                // is annotated with @NonNull, then the field (since it is final) will
                // always be @NonNull as well.
                val initializer = (item.psi() as? PsiField)?.initializer
                if (initializer != null && initializer is PsiReference) {
                    val resolved = initializer.resolve()
                    if (resolved is PsiModifierListOwner &&
                        resolved.annotations.any {
                            isNonNullAnnotation(it.qualifiedName ?: "")
                        }
                    ) {
                        nullable = false
                    }
                } else if (initializer != null && initializer is PsiCallExpression) {
                    val resolved = initializer.resolveMethod()
                    if (resolved != null &&
                        resolved.annotations.any {
                            isNonNullAnnotation(it.qualifiedName ?: "")
                        }
                    ) {
                        nullable = false
                    }
                }
            } else if (item.synthetic && (
                item is MethodItem && item.isEnumSyntheticMethod() ||
                    item is ParameterItem && item.containingMethod().isEnumSyntheticMethod()
                )
            ) {
                // Workaround the fact that the Kotlin synthetic enum methods
                // do not have nullness information
                nullable = false
            }

            // Annotation type members cannot be null
            if (item is MemberItem && item.containingClass().isAnnotationType()) {
                nullable = false
            }

            // Equals and toString have known nullness
            if (item is MethodItem && item.name() == "toString" && item.parameters().isEmpty()) {
                nullable = false
            } else if (item is ParameterItem && item.containingMethod().name() == "equals" &&
                item.containingMethod().parameters().size == 1
            ) {
                nullable = true
            }

            return nullable
        }
    }
}

/** Default implementation of an annotation item */
abstract class DefaultAnnotationItem(override val codebase: Codebase) : AnnotationItem {
    override val targets: Set<AnnotationTarget> by lazy {
        AnnotationItem.computeTargets(this, codebase::findClass)
    }
}

/** An attribute of an annotation, such as "value" */
interface AnnotationAttribute {
    /** The name of the annotation */
    val name: String
    /** The annotation value */
    val value: AnnotationAttributeValue

    /**
     * Return all leaf values; this flattens the complication of handling
     * {@code @SuppressLint("warning")} and {@code @SuppressLint({"warning1","warning2"})
     */
    fun leafValues(): List<AnnotationAttributeValue> {
        val result = mutableListOf<AnnotationAttributeValue>()
        AnnotationAttributeValue.addValues(value, result)
        return result
    }
}

/** An annotation value */
interface AnnotationAttributeValue {
    /** Generates source code for this annotation value */
    fun toSource(): String

    /** The value of the annotation */
    fun value(): Any?

    /** If the annotation declaration references a field (or class etc), return the resolved class */
    fun resolve(): Item?

    companion object {
        fun addValues(value: AnnotationAttributeValue, into: MutableList<AnnotationAttributeValue>) {
            if (value is AnnotationArrayAttributeValue) {
                for (v in value.values) {
                    addValues(v, into)
                }
            } else if (value is AnnotationSingleAttributeValue) {
                into.add(value)
            }
        }
    }
}

/** An annotation value (for a single item, not an array) */
interface AnnotationSingleAttributeValue : AnnotationAttributeValue {
    /** The annotation value, expressed as source code */
    val valueSource: String
    /** The annotation value */
    val value: Any?

    override fun value() = value
}

/** An annotation value for an array of items */
interface AnnotationArrayAttributeValue : AnnotationAttributeValue {
    /** The annotation values */
    val values: List<AnnotationAttributeValue>

    override fun resolve(): Item? {
        error("resolve() should not be called on an array value")
    }

    override fun value() = values.mapNotNull { it.value() }.toTypedArray()
}

class DefaultAnnotationAttribute(
    override val name: String,
    override val value: DefaultAnnotationValue
) : AnnotationAttribute {
    companion object {
        fun create(name: String, value: String): DefaultAnnotationAttribute {
            return DefaultAnnotationAttribute(name, DefaultAnnotationValue.create(value))
        }

        fun createList(source: String): List<AnnotationAttribute> {
            val list = mutableListOf<AnnotationAttribute>() // TODO: default size = 2
            var begin = 0
            var index = 0
            val length = source.length
            while (index < length) {
                val c = source[index]
                if (c == '{') {
                    index = findEnd(source, index + 1, length, '}')
                } else if (c == '"') {
                    index = findEnd(source, index + 1, length, '"')
                } else if (c == ',') {
                    addAttribute(list, source, begin, index)
                    index++
                    begin = index
                    continue
                } else if (c == ' ' && index == begin) {
                    begin++
                }

                index++
            }

            if (begin < length) {
                addAttribute(list, source, begin, length)
            }

            return list
        }

        private fun findEnd(source: String, from: Int, to: Int, sentinel: Char): Int {
            var i = from
            while (i < to) {
                val c = source[i]
                if (c == '\\') {
                    i++
                } else if (c == sentinel) {
                    return i
                }
                i++
            }
            return to
        }

        private fun addAttribute(list: MutableList<AnnotationAttribute>, source: String, from: Int, to: Int) {
            var split = source.indexOf('=', from)
            if (split >= to) {
                split = -1
            }
            val name: String
            val value: String
            val valueBegin: Int
            val valueEnd: Int
            if (split == -1) {
                valueBegin = split + 1
                valueEnd = to
                name = "value"
            } else {
                name = source.substring(from, split).trim()
                valueBegin = split + 1
                valueEnd = to
            }
            value = source.substring(valueBegin, valueEnd).trim()
            list.add(DefaultAnnotationAttribute.create(name, value))
        }
    }

    override fun toString(): String {
        return "DefaultAnnotationAttribute(name='$name', value=$value)"
    }
}

abstract class DefaultAnnotationValue : AnnotationAttributeValue {
    companion object {
        fun create(value: String): DefaultAnnotationValue {
            return if (value.startsWith("{")) { // Array
                DefaultAnnotationArrayAttributeValue(value)
            } else {
                DefaultAnnotationSingleAttributeValue(value)
            }
        }
    }

    override fun toString(): String = toSource()
}

class DefaultAnnotationSingleAttributeValue(override val valueSource: String) :
    DefaultAnnotationValue(),
    AnnotationSingleAttributeValue {
    @Suppress("IMPLICIT_CAST_TO_ANY")
    override val value = when {
        valueSource == SdkConstants.VALUE_TRUE -> true
        valueSource == SdkConstants.VALUE_FALSE -> false
        valueSource.startsWith("\"") -> valueSource.removeSurrounding("\"")
        valueSource.startsWith('\'') -> valueSource.removeSurrounding("'")[0]
        else -> try {
            if (valueSource.contains(".")) {
                valueSource.toDouble()
            } else {
                valueSource.toLong()
            }
        } catch (e: NumberFormatException) {
            valueSource
        }
    }

    override fun resolve(): Item? = null

    override fun toSource() = valueSource
}

class DefaultAnnotationArrayAttributeValue(val value: String) :
    DefaultAnnotationValue(),
    AnnotationArrayAttributeValue {
    init {
        assert(value.startsWith("{") && value.endsWith("}")) { value }
    }

    override val values = value.substring(1, value.length - 1).split(",").map {
        DefaultAnnotationValue.create(it.trim())
    }.toList()

    override fun toSource() = value
}
