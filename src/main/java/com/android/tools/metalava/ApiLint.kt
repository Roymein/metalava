/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.resources.ResourceType
import com.android.resources.ResourceType.AAPT
import com.android.resources.ResourceType.ANIM
import com.android.resources.ResourceType.ANIMATOR
import com.android.resources.ResourceType.ARRAY
import com.android.resources.ResourceType.ATTR
import com.android.resources.ResourceType.BOOL
import com.android.resources.ResourceType.COLOR
import com.android.resources.ResourceType.DIMEN
import com.android.resources.ResourceType.DRAWABLE
import com.android.resources.ResourceType.FONT
import com.android.resources.ResourceType.FRACTION
import com.android.resources.ResourceType.ID
import com.android.resources.ResourceType.INTEGER
import com.android.resources.ResourceType.INTERPOLATOR
import com.android.resources.ResourceType.LAYOUT
import com.android.resources.ResourceType.MENU
import com.android.resources.ResourceType.MIPMAP
import com.android.resources.ResourceType.NAVIGATION
import com.android.resources.ResourceType.PLURALS
import com.android.resources.ResourceType.PUBLIC
import com.android.resources.ResourceType.RAW
import com.android.resources.ResourceType.SAMPLE_DATA
import com.android.resources.ResourceType.STRING
import com.android.resources.ResourceType.STYLE
import com.android.resources.ResourceType.STYLEABLE
import com.android.resources.ResourceType.STYLE_ITEM
import com.android.resources.ResourceType.TRANSITION
import com.android.resources.ResourceType.XML
import com.android.sdklib.SdkVersionInfo
import com.android.tools.metalava.Issues.ABSTRACT_INNER
import com.android.tools.metalava.Issues.ACRONYM_NAME
import com.android.tools.metalava.Issues.ACTION_VALUE
import com.android.tools.metalava.Issues.ALL_UPPER
import com.android.tools.metalava.Issues.ANDROID_URI
import com.android.tools.metalava.Issues.ARRAY_RETURN
import com.android.tools.metalava.Issues.ASYNC_SUFFIX_FUTURE
import com.android.tools.metalava.Issues.AUTO_BOXING
import com.android.tools.metalava.Issues.BAD_FUTURE
import com.android.tools.metalava.Issues.BANNED_THROW
import com.android.tools.metalava.Issues.BUILDER_SET_STYLE
import com.android.tools.metalava.Issues.CALLBACK_INTERFACE
import com.android.tools.metalava.Issues.CALLBACK_METHOD_NAME
import com.android.tools.metalava.Issues.CALLBACK_NAME
import com.android.tools.metalava.Issues.COMMON_ARGS_FIRST
import com.android.tools.metalava.Issues.COMPILE_TIME_CONSTANT
import com.android.tools.metalava.Issues.CONCRETE_COLLECTION
import com.android.tools.metalava.Issues.CONFIG_FIELD_NAME
import com.android.tools.metalava.Issues.CONSISTENT_ARGUMENT_ORDER
import com.android.tools.metalava.Issues.CONTEXT_FIRST
import com.android.tools.metalava.Issues.CONTEXT_NAME_SUFFIX
import com.android.tools.metalava.Issues.ENDS_WITH_IMPL
import com.android.tools.metalava.Issues.ENUM
import com.android.tools.metalava.Issues.EQUALS_AND_HASH_CODE
import com.android.tools.metalava.Issues.EXCEPTION_NAME
import com.android.tools.metalava.Issues.EXECUTOR_REGISTRATION
import com.android.tools.metalava.Issues.EXTENDS_ERROR
import com.android.tools.metalava.Issues.FORBIDDEN_SUPER_CLASS
import com.android.tools.metalava.Issues.FRACTION_FLOAT
import com.android.tools.metalava.Issues.GENERIC_CALLBACKS
import com.android.tools.metalava.Issues.GENERIC_EXCEPTION
import com.android.tools.metalava.Issues.GETTER_ON_BUILDER
import com.android.tools.metalava.Issues.GETTER_SETTER_NAMES
import com.android.tools.metalava.Issues.HEAVY_BIT_SET
import com.android.tools.metalava.Issues.INTENT_BUILDER_NAME
import com.android.tools.metalava.Issues.INTENT_NAME
import com.android.tools.metalava.Issues.INTERFACE_CONSTANT
import com.android.tools.metalava.Issues.INTERNAL_CLASSES
import com.android.tools.metalava.Issues.INTERNAL_FIELD
import com.android.tools.metalava.Issues.INVALID_NULLABILITY_OVERRIDE
import com.android.tools.metalava.Issues.Issue
import com.android.tools.metalava.Issues.KOTLIN_OPERATOR
import com.android.tools.metalava.Issues.LISTENER_INTERFACE
import com.android.tools.metalava.Issues.LISTENER_LAST
import com.android.tools.metalava.Issues.MANAGER_CONSTRUCTOR
import com.android.tools.metalava.Issues.MANAGER_LOOKUP
import com.android.tools.metalava.Issues.MENTIONS_GOOGLE
import com.android.tools.metalava.Issues.METHOD_NAME_TENSE
import com.android.tools.metalava.Issues.METHOD_NAME_UNITS
import com.android.tools.metalava.Issues.MIN_MAX_CONSTANT
import com.android.tools.metalava.Issues.MISSING_BUILD_METHOD
import com.android.tools.metalava.Issues.MISSING_GETTER_MATCHING_BUILDER
import com.android.tools.metalava.Issues.MISSING_NULLABILITY
import com.android.tools.metalava.Issues.MUTABLE_BARE_FIELD
import com.android.tools.metalava.Issues.NOT_CLOSEABLE
import com.android.tools.metalava.Issues.NO_BYTE_OR_SHORT
import com.android.tools.metalava.Issues.NO_CLONE
import com.android.tools.metalava.Issues.NO_SETTINGS_PROVIDER
import com.android.tools.metalava.Issues.NULLABLE_COLLECTION
import com.android.tools.metalava.Issues.ON_NAME_EXPECTED
import com.android.tools.metalava.Issues.OPTIONAL_BUILDER_CONSTRUCTOR_ARGUMENT
import com.android.tools.metalava.Issues.OVERLAPPING_CONSTANTS
import com.android.tools.metalava.Issues.PACKAGE_LAYERING
import com.android.tools.metalava.Issues.PAIRED_REGISTRATION
import com.android.tools.metalava.Issues.PARCELABLE_LIST
import com.android.tools.metalava.Issues.PARCEL_CONSTRUCTOR
import com.android.tools.metalava.Issues.PARCEL_CREATOR
import com.android.tools.metalava.Issues.PARCEL_NOT_FINAL
import com.android.tools.metalava.Issues.PERCENTAGE_INT
import com.android.tools.metalava.Issues.PROTECTED_MEMBER
import com.android.tools.metalava.Issues.PUBLIC_TYPEDEF
import com.android.tools.metalava.Issues.RAW_AIDL
import com.android.tools.metalava.Issues.REGISTRATION_NAME
import com.android.tools.metalava.Issues.RESOURCE_FIELD_NAME
import com.android.tools.metalava.Issues.RESOURCE_STYLE_FIELD_NAME
import com.android.tools.metalava.Issues.RESOURCE_VALUE_FIELD_NAME
import com.android.tools.metalava.Issues.RETHROW_REMOTE_EXCEPTION
import com.android.tools.metalava.Issues.SERVICE_NAME
import com.android.tools.metalava.Issues.SETTER_RETURNS_THIS
import com.android.tools.metalava.Issues.SINGLETON_CONSTRUCTOR
import com.android.tools.metalava.Issues.SINGLE_METHOD_INTERFACE
import com.android.tools.metalava.Issues.SINGULAR_CALLBACK
import com.android.tools.metalava.Issues.START_WITH_LOWER
import com.android.tools.metalava.Issues.START_WITH_UPPER
import com.android.tools.metalava.Issues.STATIC_FINAL_BUILDER
import com.android.tools.metalava.Issues.STATIC_UTILS
import com.android.tools.metalava.Issues.STREAM_FILES
import com.android.tools.metalava.Issues.TOP_LEVEL_BUILDER
import com.android.tools.metalava.Issues.UNIQUE_KOTLIN_OPERATOR
import com.android.tools.metalava.Issues.USER_HANDLE
import com.android.tools.metalava.Issues.USER_HANDLE_NAME
import com.android.tools.metalava.Issues.USE_ICU
import com.android.tools.metalava.Issues.USE_PARCEL_FILE_DESCRIPTOR
import com.android.tools.metalava.Issues.VISIBLY_SYNCHRONIZED
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.AnnotationItem.Companion.getImplicitNullness
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.MemberItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.SetMinSdkVersion
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.psi.PsiMethodItem
import com.android.tools.metalava.model.psi.PsiTypeItem
import com.android.tools.metalava.model.visitors.ApiVisitor
import com.intellij.psi.JavaRecursiveElementVisitor
import com.intellij.psi.PsiClassObjectAccessExpression
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiSynchronizedStatement
import com.intellij.psi.PsiThisExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClassLiteralExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor
import java.util.Locale
import java.util.function.Predicate

/**
 * The [ApiLint] analyzer checks the API against a known set of preferred API practices
 * by the Android API council.
 */
class ApiLint(private val codebase: Codebase, private val oldCodebase: Codebase?, private val reporter: Reporter) : ApiVisitor(
        // Sort by source order such that warnings follow source line number order
        methodComparator = MethodItem.sourceOrderComparator,
        fieldComparator = FieldItem.comparator,
        ignoreShown = options.showUnannotated,
        // No need to check "for stubs only APIs" (== "implicit" APIs)
        includeApisForStubPurposes = false
) {
    private fun report(id: Issue, item: Item, message: String, element: PsiElement? = null) {
        // Don't flag api warnings on deprecated APIs; these are obviously already known to
        // be problematic.
        if (item.deprecated) {
            return
        }

        if (item is ParameterItem && item.containingMethod().deprecated) {
            return
        }

        // With show annotations we might be flagging API that is filtered out: hide these here
        val testItem = if (item is ParameterItem) item.containingMethod() else item
        if (!filterEmit.test(testItem)) {
            return
        }

        reporter.report(id, item, message, element)
    }

    private fun check() {
        if (oldCodebase != null) {
            // Only check the new APIs
            CodebaseComparator().compare(
                    object : ComparisonVisitor() {
                        override fun added(new: Item) {
                            new.accept(this@ApiLint)
                        }
                    },
                    oldCodebase, codebase, filterReference
            )
        } else {
            // No previous codebase to compare with: visit the whole thing
            codebase.accept(this)
        }
    }

    override fun skip(item: Item): Boolean {
        return super.skip(item) ||
                item is ClassItem && !isInteresting(item) ||
                item is MethodItem && !isInteresting(item.containingClass()) ||
                item is FieldItem && !isInteresting(item.containingClass())
    }

    private val kotlinInterop = KotlinInteropChecks(reporter)

    override fun visitClass(cls: ClassItem) {
        val methods = cls.filteredMethods(filterReference).asSequence()
        val fields = cls.filteredFields(filterReference, showUnannotated).asSequence()
        val constructors = cls.filteredConstructors(filterReference)
        val superClass = cls.filteredSuperclass(filterReference)
        val interfaces = cls.filteredInterfaceTypes(filterReference).asSequence()
        val allMethods = methods.asSequence() + constructors.asSequence()
        checkClass(cls, methods, constructors, allMethods, fields, superClass, interfaces)
    }

    override fun visitMethod(method: MethodItem) {
        checkMethod(method, filterReference)
        val returnType = method.returnType()
        checkType(returnType, method)
        checkNullableCollections(returnType, method)
        checkMethodSuffixListenableFutureReturn(returnType, method)
        for (parameter in method.parameters()) {
            checkType(parameter.type(), parameter)
        }
        kotlinInterop.checkMethod(method)
    }

    override fun visitField(field: FieldItem) {
        checkField(field)
        checkType(field.type(), field)
        kotlinInterop.checkField(field)
    }

    private fun checkType(type: TypeItem, item: Item) {
        val typeString = type.toTypeString()
        checkPfd(typeString, item)
        checkNumbers(typeString, item)
        checkCollections(type, item)
        checkCollectionsOverArrays(type, typeString, item)
        checkBoxed(type, item)
        checkIcu(type, typeString, item)
        checkBitSet(type, typeString, item)
        checkHasNullability(item)
        checkUri(typeString, item)
        checkFutures(typeString, item)
    }

    private fun checkClass(
            cls: ClassItem,
            methods: Sequence<MethodItem>,
            constructors: Sequence<ConstructorItem>,
            methodsAndConstructors: Sequence<MethodItem>,
            fields: Sequence<FieldItem>,
            superClass: ClassItem?,
            interfaces: Sequence<TypeItem>
    ) {
        checkEquals(methods)
        checkEnums(cls)
        checkClassNames(cls)
        checkCallbacks(cls)
        checkListeners(cls, methods)
        checkParcelable(cls, methods, constructors, fields)
        checkRegistrationMethods(cls, methods)
        checkHelperClasses(cls, methods, fields)
        checkBuilder(cls, methods, constructors, superClass)
        checkAidl(cls, superClass, interfaces)
        checkInternal(cls)
        checkLayering(cls, methodsAndConstructors, fields)
        checkBooleans(methods)
        checkFlags(fields)
        checkGoogle(cls, methods, fields)
        checkManager(cls, methods, constructors)
        checkStaticUtils(cls, methods, constructors, fields)
        checkCallbackHandlers(cls, methodsAndConstructors, superClass)
        checkGenericCallbacks(cls, methods, constructors, fields)
        checkResourceNames(cls, fields)
        checkFiles(methodsAndConstructors)
        checkManagerList(cls, methods)
        checkAbstractInner(cls)
        checkError(cls, superClass)
        checkCloseable(cls, methods)
        checkNotKotlinOperator(methods)
        checkUserHandle(cls, methods)
        checkParams(cls)
        checkSingleton(cls, methods, constructors)
        checkExtends(cls)
        checkTypedef(cls)

        // TODO: Not yet working
        // checkOverloadArgs(cls, methods)
    }

    private fun checkField(
            field: FieldItem
    ) {
        val modifiers = field.modifiers
        if (modifiers.isStatic() && modifiers.isFinal()) {
            checkConstantNames(field)
            checkActions(field)
            checkIntentExtras(field)
        }
        checkProtected(field)
        checkServices(field)
        checkFieldName(field)
        checkSettingKeys(field)
        checkNullableCollections(field.type(), field)
    }

    private fun checkMethod(
            method: MethodItem,
            filterReference: Predicate<Item>
    ) {
        if (!method.isConstructor()) {
            checkMethodNames(method)
            checkProtected(method)
            checkSynchronized(method)
            checkIntentBuilder(method)
            checkUnits(method)
            checkTense(method)
            checkClone(method)
            checkCallbackOrListenerMethod(method)
        }
        checkExceptions(method, filterReference)
        checkContextFirst(method)
        checkListenerLast(method)
    }

    private fun checkEnums(cls: ClassItem) {
        if (cls.isEnum()) {
            report(ENUM, cls, "Enums are discouraged in Android APIs")
        }
    }

    private fun checkMethodNames(method: MethodItem) {
        // Existing violations
        val containing = method.containingClass().qualifiedName()
        if (containing.startsWith("android.opengl") ||
                containing.startsWith("android.renderscript") ||
                containing.startsWith("android.database.sqlite.") ||
                containing == "android.system.OsConstants"
        ) {
            return
        }

        val name = if (method.isKotlin() && method.name().contains("-")) {
            // Kotlin renames certain methods in binary, e.g. fun foo(bar: Bar) where Bar is an
            // inline class becomes foo-HASHCODE. We only want to consider the original name for
            // this API lint check
            method.name().substringBefore("-")
        } else {
            method.name()
        }
        val first = name[0]

        when {
            first !in 'a'..'z' -> report(START_WITH_LOWER, method, "Method name must start with lowercase char: $name")
            hasAcronyms(name) -> {
                report(
                        ACRONYM_NAME, method,
                        "Acronyms should not be capitalized in method names: was `$name`, should this be `${
                            decapitalizeAcronyms(
                                    name
                            )
                        }`?"
                )
            }
        }
    }

    private fun checkClassNames(cls: ClassItem) {
        // Existing violations
        val qualifiedName = cls.qualifiedName()
        if (qualifiedName.startsWith("android.opengl") ||
                qualifiedName.startsWith("android.renderscript") ||
                qualifiedName.startsWith("android.database.sqlite.") ||
                qualifiedName.startsWith("android.R.")
        ) {
            return
        }

        val name = cls.simpleName()
        val first = name[0]
        when {
            first !in 'A'..'Z' -> {
                report(
                        START_WITH_UPPER, cls,
                        "Class must start with uppercase char: $name"
                )
            }

            hasAcronyms(name) -> {
                report(
                        ACRONYM_NAME, cls,
                        "Acronyms should not be capitalized in class names: was `$name`, should this be `${
                            decapitalizeAcronyms(
                                    name
                            )
                        }`?"
                )
            }

            name.endsWith("Impl") -> {
                report(
                        ENDS_WITH_IMPL, cls,
                        "Don't expose your implementation details: `$name` ends with `Impl`"
                )
            }
        }
    }

    private fun checkConstantNames(field: FieldItem) {
        // Skip this check on Kotlin
        if (field.isKotlin()) {
            return
        }

        // Existing violations
        val qualified = field.containingClass().qualifiedName()
        if (qualified.startsWith("android.os.Build") ||
                qualified == "android.system.OsConstants" ||
                qualified == "android.media.MediaCodecInfo" ||
                qualified.startsWith("android.opengl.") ||
                qualified.startsWith("android.R.")
        ) {
            return
        }

        val name = field.name()
        if (!constantNamePattern.matches(name)) {
            val suggested = SdkVersionInfo.camelCaseToUnderlines(name).uppercase(Locale.US)
            report(
                    ALL_UPPER, field,
                    "Constant field names must be named with only upper case characters: `$qualified#$name`, should be `$suggested`?"
            )
        } else if ((name.startsWith("MIN_") || name.startsWith("MAX_")) && !field.type().isString()) {
            report(
                    MIN_MAX_CONSTANT, field,
                    "If min/max could change in future, make them dynamic methods: $qualified#$name"
            )
        } else if ((field.type().primitive || field.type().isString()) && field.initialValue(true) == null) {
            report(
                    COMPILE_TIME_CONSTANT, field,
                    "All constants must be defined at compile time: $qualified#$name"
            )
        }
    }

    private fun checkCallbacks(cls: ClassItem) {
        // Existing violations
        val qualified = cls.qualifiedName()
        if (qualified == "android.speech.tts.SynthesisCallback") {
            return
        }

        val name = cls.simpleName()
        when {
            name.endsWith("Callbacks") -> {
                report(
                        SINGULAR_CALLBACK, cls,
                        "Callback class names should be singular: $name"
                )
            }

            name.endsWith("Observer") -> {
                val prefix = name.removeSuffix("Observer")
                report(
                        CALLBACK_NAME, cls,
                        "Class should be named ${prefix}Callback"
                )
            }

            name.endsWith("Callback") -> {
                if (cls.isInterface()) {
                    report(
                            CALLBACK_INTERFACE, cls,
                            "Callbacks must be abstract class instead of interface to enable extension in future API levels: $name"
                    )
                }
            }
        }
    }

    private fun checkCallbackOrListenerMethod(method: MethodItem) {
        if (method.isConstructor() || method.modifiers.isStatic() || method.modifiers.isFinal()) {
            return
        }
        val cls = method.containingClass()

        // These are not listeners or callbacks despite their name.
        when {
            cls.modifiers.isFinal() -> return
            cls.qualifiedName() == "android.telephony.ims.ImsCallSessionListener" -> return
        }

        val containingClassSimpleName = cls.simpleName()
        val kind = when {
            containingClassSimpleName.endsWith("Callback") -> "Callback"
            containingClassSimpleName.endsWith("Listener") -> "Listener"
            else -> return
        }
        val methodName = method.name()

        if (!onCallbackNamePattern.matches(methodName)) {
            report(
                    CALLBACK_METHOD_NAME, method,
                    "$kind method names must follow the on<Something> style: $methodName"
            )
        }

        for (parameter in method.parameters()) {
            // We require nonnull collections as parameters to callback methods
            checkNullableCollections(parameter.type(), parameter)
        }
    }

    private fun checkListeners(cls: ClassItem, methods: Sequence<MethodItem>) {
        val name = cls.simpleName()
        if (name.endsWith("Listener")) {
            if (cls.isClass()) {
                report(
                        LISTENER_INTERFACE, cls,
                        "Listeners should be an interface, or otherwise renamed Callback: $name"
                )
            } else {
                if (methods.count() == 1) {
                    val method = methods.first()
                    val methodName = method.name()
                    if (methodName.startsWith("On") &&
                            !("${methodName}Listener").equals(cls.simpleName(), ignoreCase = true)
                    ) {
                        report(
                                SINGLE_METHOD_INTERFACE, cls,
                                "Single listener method name must match class name"
                        )
                    }
                }
            }
        }
    }

    private fun checkGenericCallbacks(
            cls: ClassItem,
            methods: Sequence<MethodItem>,
            constructors: Sequence<ConstructorItem>,
            fields: Sequence<FieldItem>
    ) {
        val simpleName = cls.simpleName()
        if (!simpleName.endsWith("Callback") && !simpleName.endsWith("Listener")) return

        // The following checks for an interface or abstract class of the same shape as
        // OutcomeReceiver, i.e. two methods, both with the "on" prefix for callbacks, one of
        // them taking a Throwable or subclass.
        if (constructors.any { !it.isImplicitConstructor() }) return
        if (fields.any()) return
        if (methods.count() != 2) return

        fun isSingleParamCallbackMethod(method: MethodItem) =
                method.parameters().size == 1 &&
                        method.name().startsWith("on") &&
                        !method.parameters().first().type().primitive &&
                        method.returnType().toTypeString() == Void.TYPE.name

        if (!methods.all(::isSingleParamCallbackMethod)) return

        fun TypeItem.extendsThrowable() = asClass()?.extends(JAVA_LANG_THROWABLE) ?: false
        fun isErrorMethod(method: MethodItem) =
                method.name().run { startsWith("onError") || startsWith("onFail") } &&
                        method.parameters().first().type().extendsThrowable()

        if (methods.count(::isErrorMethod) == 1) {
            report(
                    GENERIC_CALLBACKS,
                    cls,
                    "${cls.fullName()} can be replaced with OutcomeReceiver<R,E> (platform)" +
                            " or suspend fun / ListenableFuture (AndroidX)."
            )
        }
    }

    private fun checkActions(field: FieldItem) {
        val name = field.name()
        if (name.startsWith("EXTRA_") || name == "SERVICE_INTERFACE" || name == "PROVIDER_INTERFACE") {
            return
        }
        if (!field.type().isString()) {
            return
        }
        val value = field.initialValue(true) as? String ?: return
        if (!(name.contains("_ACTION") || name.contains("ACTION_") || value.contains(".action."))) {
            return
        }
        val className = field.containingClass().qualifiedName()
        when (className) {
            "android.Manifest.permission" -> return
        }
        if (!name.startsWith("ACTION_")) {
            report(
                    INTENT_NAME, field,
                    "Intent action constant name must be ACTION_FOO: $name"
            )
            return
        }
        val prefix = when (className) {
            "android.content.Intent" -> "android.intent.action"
            "android.provider.Settings" -> "android.settings"
            else -> field.containingClass().containingPackage().qualifiedName() + ".action"
        }
        val expected = prefix + "." + name.substring(7)
        if (value != expected) {
            report(
                    ACTION_VALUE, field,
                    "Inconsistent action value; expected `$expected`, was `$value`"
            )
        }
    }

    private fun checkIntentExtras(field: FieldItem) {
        val className = field.containingClass().qualifiedName()
        if (className == "android.app.Notification" || className == "android.appwidget.AppWidgetManager") {
            return
        }

        val name = field.name()
        if (name.startsWith("ACTION_") || !field.type().isString()) {
            return
        }
        val value = field.initialValue(true) as? String ?: return
        if (!(name.contains("_EXTRA") || name.contains("EXTRA_") || value.contains(".extra"))) {
            return
        }
        if (!name.startsWith("EXTRA_")) {
            report(
                    INTENT_NAME, field,
                    "Intent extra constant name must be EXTRA_FOO: $name"
            )
            return
        }

        val packageName = field.containingClass().containingPackage().qualifiedName()
        val prefix = when {
            className == "android.content.Intent" -> "android.intent.extra"
            else -> "$packageName.extra"
        }
        val expected = prefix + "." + name.substring(6)
        if (value != expected) {
            report(
                    ACTION_VALUE, field,
                    "Inconsistent extra value; expected `$expected`, was `$value`"
            )
        }
    }

    private fun checkEquals(methods: Sequence<MethodItem>) {
        var equalsMethod: MethodItem? = null
        var hashCodeMethod: MethodItem? = null

        for (method in methods) {
            if (isEqualsMethod(method)) {
                equalsMethod = method
            } else if (isHashCodeMethod(method)) {
                hashCodeMethod = method
            }
        }
        if ((equalsMethod == null) != (hashCodeMethod == null)) {
            val method = equalsMethod ?: hashCodeMethod!!
            report(
                    EQUALS_AND_HASH_CODE, method,
                    "Must override both equals and hashCode; missing one in ${method.containingClass().qualifiedName()}"
            )
        }
    }

    private fun isEqualsMethod(method: MethodItem): Boolean {
        return method.name() == "equals" && method.parameters().size == 1 &&
                method.parameters()[0].type().isJavaLangObject() &&
                !method.modifiers.isStatic()
    }

    private fun isHashCodeMethod(method: MethodItem): Boolean {
        return method.name() == "hashCode" && method.parameters().isEmpty() &&
                !method.modifiers.isStatic()
    }

    private fun checkParcelable(
            cls: ClassItem,
            methods: Sequence<MethodItem>,
            constructors: Sequence<MethodItem>,
            fields: Sequence<FieldItem>
    ) {
        if (!cls.implements("android.os.Parcelable")) {
            return
        }

        if (fields.none { it.name() == "CREATOR" }) {
            report(
                    PARCEL_CREATOR, cls,
                    "Parcelable requires a `CREATOR` field; missing in ${cls.qualifiedName()}"
            )
        }
        if (methods.none { it.name() == "writeToParcel" }) {
            report(
                    PARCEL_CREATOR, cls,
                    "Parcelable requires `void writeToParcel(Parcel, int)`; missing in ${cls.qualifiedName()}"
            )
        }
        if (methods.none { it.name() == "describeContents" }) {
            report(
                    PARCEL_CREATOR, cls,
                    "Parcelable requires `public int describeContents()`; missing in ${cls.qualifiedName()}"
            )
        }

        if (!cls.modifiers.isFinal()) {
            report(
                    PARCEL_NOT_FINAL, cls,
                    "Parcelable classes must be final: ${cls.qualifiedName()} is not final"
            )
        }

        val parcelConstructor = constructors.firstOrNull {
            val parameters = it.parameters()
            parameters.size == 1 && parameters[0].type().toTypeString() == "android.os.Parcel"
        }

        if (parcelConstructor != null) {
            report(
                    PARCEL_CONSTRUCTOR, parcelConstructor,
                    "Parcelable inflation is exposed through CREATOR, not raw constructors, in ${cls.qualifiedName()}"
            )
        }
    }

    private fun checkProtected(member: MemberItem) {
        val modifiers = member.modifiers
        if (modifiers.isProtected()) {
            if (member.name() == "finalize" && member is MethodItem && member.parameters().isEmpty()) {
                return
            }

            report(
                    PROTECTED_MEMBER, member,
                    "Protected ${if (member is MethodItem) "methods" else "fields"} not allowed; must be public: ${member.describe()}}"
            )
        }
    }

    private fun checkFieldName(field: FieldItem) {
        val className = field.containingClass().qualifiedName()
        val modifiers = field.modifiers
        if (!modifiers.isFinal()) {
            if (className !in classesWithBareFields &&
                    !className.endsWith("LayoutParams") &&
                    !className.startsWith("android.util.Mutable")
            ) {
                report(
                        MUTABLE_BARE_FIELD, field,
                        "Bare field ${field.name()} must be marked final, or moved behind accessors if mutable"
                )
            }
        }
        if (!modifiers.isStatic()) {
            if (!fieldNamePattern.matches(field.name())) {
                report(
                        START_WITH_LOWER, field,
                        "Non-static field ${field.name()} must be named using fooBar style"
                )
            }
        }
        if (internalNamePattern.matches(field.name())) {
            report(
                    INTERNAL_FIELD, field,
                    "Internal field ${field.name()} must not be exposed"
            )
        }
        if (constantNamePattern.matches(field.name()) && field.isJava()) {
            if (!modifiers.isStatic() || !modifiers.isFinal()) {
                report(
                        ALL_UPPER, field,
                        "Constant ${field.name()} must be marked static final"
                )
            }
        }
    }

    private fun checkSettingKeys(field: FieldItem) {
        val className = field.containingClass().qualifiedName()
        val modifiers = field.modifiers
        val type = field.type()

        if (modifiers.isFinal() && modifiers.isStatic() && type.isString() && className in settingsKeyClasses) {
            report(
                    NO_SETTINGS_PROVIDER, field,
                    "New setting keys are not allowed (Field: ${field.name()}); use getters/setters in relevant manager class"
            )
        }
    }

    private fun checkRegistrationMethods(cls: ClassItem, methods: Sequence<MethodItem>) {
        /** Make sure that there is a corresponding method */
        fun ensureMatched(cls: ClassItem, methods: Sequence<MethodItem>, method: MethodItem, name: String) {
            if (method.superMethods().isNotEmpty()) return // Do not report for override methods
            for (candidate in methods) {
                if (candidate.name() == name) {
                    return
                }
            }

            report(
                    PAIRED_REGISTRATION, method,
                    "Found ${method.name()} but not $name in ${cls.qualifiedName()}"
            )
        }

        for (method in methods) {
            val name = method.name()
            // the python version looks for any substring, but that includes a lot of other stuff, like plurals
            if (name.endsWith("Callback")) {
                if (name.startsWith("register")) {
                    val unregister = "unregister" + name.substring(8) // "register".length
                    ensureMatched(cls, methods, method, unregister)
                } else if (name.startsWith("unregister")) {
                    val unregister = "register" + name.substring(10) // "unregister".length
                    ensureMatched(cls, methods, method, unregister)
                }
                if (name.startsWith("add") || name.startsWith("remove")) {
                    report(
                            REGISTRATION_NAME, method,
                            "Callback methods should be named register/unregister; was $name"
                    )
                }
            } else if (name.endsWith("Listener")) {
                if (name.startsWith("add")) {
                    val unregister = "remove" + name.substring(3) // "add".length
                    ensureMatched(cls, methods, method, unregister)
                } else if (name.startsWith("remove") && !name.startsWith("removeAll")) {
                    val unregister = "add" + name.substring(6) // "remove".length
                    ensureMatched(cls, methods, method, unregister)
                }
                if (name.startsWith("register") || name.startsWith("unregister")) {
                    report(
                            REGISTRATION_NAME, method,
                            "Listener methods should be named add/remove; was $name"
                    )
                }
            }
        }
    }

    private fun checkSynchronized(method: MethodItem) {
        fun reportError(method: MethodItem, psi: PsiElement? = null) {
            val message = StringBuilder("Internal locks must not be exposed")
            if (psi != null) {
                message.append(" (synchronizing on this or class is still externally observable)")
            }
            message.append(": ")
            message.append(method.describe())
            report(VISIBLY_SYNCHRONIZED, method, message.toString(), psi)
        }

        if (method.modifiers.isSynchronized()) {
            reportError(method)
        } else if (method is PsiMethodItem) {
            val psiMethod = method.psiMethod
            if (psiMethod is UMethod) {
                psiMethod.accept(object : AbstractUastVisitor() {
                    override fun afterVisitCallExpression(node: UCallExpression) {
                        super.afterVisitCallExpression(node)

                        if (node.methodName == "synchronized" && node.receiver == null) {
                            val arg = node.valueArguments.firstOrNull()
                            if (arg is UThisExpression ||
                                    arg is UClassLiteralExpression ||
                                    arg is UQualifiedReferenceExpression && arg.receiver is UClassLiteralExpression
                            ) {
                                reportError(method, arg.sourcePsi ?: node.sourcePsi ?: node.javaPsi)
                            }
                        }
                    }
                })
            } else {
                psiMethod.body?.accept(object : JavaRecursiveElementVisitor() {
                    override fun visitSynchronizedStatement(statement: PsiSynchronizedStatement) {
                        super.visitSynchronizedStatement(statement)

                        val lock = statement.lockExpression
                        if (lock == null || lock is PsiThisExpression ||
                                // locking on any class is visible
                                lock is PsiClassObjectAccessExpression
                        ) {
                            reportError(method, lock ?: statement)
                        }
                    }
                })
            }
        }
    }

    private fun checkIntentBuilder(method: MethodItem) {
        if (method.returnType().toTypeString() == "android.content.Intent") {
            val name = method.name()
            if (name.startsWith("create") && name.endsWith("Intent")) {
                return
            }
            if (method.containingClass().simpleName() == "Intent") {
                return
            }

            report(
                    INTENT_BUILDER_NAME, method,
                    "Methods creating an Intent should be named `create<Foo>Intent()`, was `$name`"
            )
        }
    }

    private fun checkHelperClasses(cls: ClassItem, methods: Sequence<MethodItem>, fields: Sequence<FieldItem>) {
        fun ensureFieldValue(fields: Sequence<FieldItem>, fieldName: String, fieldValue: String) {
            fields.firstOrNull { it.name() == fieldName }?.let { field ->
                if (field.initialValue(true) != fieldValue) {
                    report(
                            INTERFACE_CONSTANT, field,
                            "Inconsistent interface constant; expected '$fieldValue'`"
                    )
                }
            }
        }

        fun ensureContextNameSuffix(cls: ClassItem, suffix: String) {
            if (!cls.simpleName().endsWith(suffix)) {
                report(
                        CONTEXT_NAME_SUFFIX, cls,
                        "Inconsistent class name; should be `<Foo>$suffix`, was `${cls.simpleName()}`"
                )
            }
        }

        var testMethods = false

        when {
            cls.extends("android.app.Service") -> {
                testMethods = true
                ensureContextNameSuffix(cls, "Service")
                ensureFieldValue(fields, "SERVICE_INTERFACE", cls.qualifiedName())
            }

            cls.extends("android.content.ContentProvider") -> {
                testMethods = true
                ensureContextNameSuffix(cls, "Provider")
                ensureFieldValue(fields, "PROVIDER_INTERFACE", cls.qualifiedName())
            }

            cls.extends("android.content.BroadcastReceiver") -> {
                testMethods = true
                ensureContextNameSuffix(cls, "Receiver")
            }

            cls.extends("android.app.Activity") -> {
                testMethods = true
                ensureContextNameSuffix(cls, "Activity")
            }
        }

        if (testMethods) {
            for (method in methods) {
                val modifiers = method.modifiers
                if (modifiers.isFinal() || modifiers.isStatic()) {
                    continue
                }
                val name = method.name()
                if (!onCallbackNamePattern.matches(name)) {
                    val message =
                            if (modifiers.isAbstract()) {
                                "Methods implemented by developers should follow the on<Something> style, was `$name`"
                            } else {
                                "If implemented by developer, should follow the on<Something> style; otherwise consider marking final"
                            }
                    report(ON_NAME_EXPECTED, method, message)
                }
            }
        }
    }

    private fun checkBuilder(
            cls: ClassItem,
            methods: Sequence<MethodItem>,
            constructors: Sequence<ConstructorItem>,
            superClass: ClassItem?
    ) {
        if (!cls.simpleName().endsWith("Builder")) {
            return
        }
        if (superClass != null && !superClass.isJavaLangObject()) {
            return
        }
        if (cls.isTopLevelClass()) {
            report(
                    TOP_LEVEL_BUILDER, cls,
                    "Builder should be defined as inner class: ${cls.qualifiedName()}"
            )
        }
        if (!cls.modifiers.isFinal()) {
            report(
                    STATIC_FINAL_BUILDER, cls,
                    "Builder must be final: ${cls.qualifiedName()}"
            )
        }
        if (!cls.modifiers.isStatic() && !cls.isTopLevelClass()) {
            report(
                    STATIC_FINAL_BUILDER, cls,
                    "Builder must be static: ${cls.qualifiedName()}"
            )
        }
        for (constructor in constructors) {
            for (arg in constructor.parameters()) {
                if (arg.modifiers.isNullable()) {
                    report(
                            OPTIONAL_BUILDER_CONSTRUCTOR_ARGUMENT, arg,
                            "Builder constructor arguments must be mandatory (i.e. not @Nullable): ${arg.describe()}"
                    )
                }
            }
        }
        // Maps each setter to a list of potential getters that would satisfy it.
        val expectedGetters = mutableListOf<Pair<Item, Set<String>>>()
        var builtType: TypeItem? = null
        val clsType = cls.toType()

        for (method in methods) {
            val name = method.name()
            if (name == "build") {
                builtType = method.type()
                continue
            } else if (name.startsWith("get") || name.startsWith("is")) {
                report(
                        GETTER_ON_BUILDER, method,
                        "Getter should be on the built object, not the builder: ${method.describe()}"
                )
            } else if (name.startsWith("set") || name.startsWith("add") || name.startsWith("clear")) {
                val returnType = method.returnType()
                val returnsClassType = if (
                        returnType is PsiTypeItem && clsType is PsiTypeItem
                ) {
                    clsType.isAssignableFromWithoutUnboxing(returnType)
                } else {
                    // fallback to a limited text based check
                    val returnTypeBounds = returnType
                            .asTypeParameter(context = method)
                            ?.typeBounds()?.map {
                                it.toTypeString()
                            } ?: emptyList()
                    returnTypeBounds.contains(clsType.toTypeString()) || returnType == clsType
                }
                if (!returnsClassType) {
                    report(
                            SETTER_RETURNS_THIS, method,
                            "Methods must return the builder object (return type " +
                                    "$clsType instead of $returnType): ${method.describe()}"
                    )
                }

                if (method.modifiers.isNullable()) {
                    report(
                            SETTER_RETURNS_THIS, method,
                            "Builder setter must be @NonNull: ${method.describe()}"
                    )
                }
                val isBool = when (method.parameters().firstOrNull()?.type()?.toTypeString()) {
                    "boolean", "java.lang.Boolean" -> true
                    else -> false
                }
                val allowedGetters: Set<String>? = if (isBool && name.startsWith("set")) {
                    val pattern = goodBooleanGetterSetterPrefixes.match(
                            name, GetterSetterPattern::setter
                    )!!
                    setOf("${pattern.getter}${name.removePrefix(pattern.setter)}")
                } else {
                    when {
                        name.startsWith("set") -> listOf(name.removePrefix("set"))
                        name.startsWith("add") -> {
                            val nameWithoutPrefix = name.removePrefix("add")
                            when {
                                name.endsWith("s") -> {
                                    // If the name ends with s, it may already be a plural. If the
                                    // add method accepts a single value, it is called addFoo() and
                                    // getFoos() is right. If an add method accepts a collection, it
                                    // is called addFoos() and getFoos() is right. So we allow both.
                                    listOf(nameWithoutPrefix, "${nameWithoutPrefix}es")
                                }

                                name.endsWith("sh") || name.endsWith("ch") || name.endsWith("x") ||
                                        name.endsWith("z") -> listOf("${nameWithoutPrefix}es")

                                name.endsWith("y") &&
                                        name[name.length - 2] !in listOf('a', 'e', 'i', 'o', 'u')
                                -> {
                                    listOf("${nameWithoutPrefix.removeSuffix("y")}ies")
                                }

                                else -> listOf("${nameWithoutPrefix}s")
                            }
                        }

                        else -> null
                    }?.map { "get$it" }?.toSet()
                }
                allowedGetters?.let { expectedGetters.add(method to it) }
            } else {
                report(
                        BUILDER_SET_STYLE, method,
                        "Builder methods names should use setFoo() / addFoo() / clearFoo() style: ${method.describe()}"
                )
            }
        }
        if (builtType == null) {
            report(
                    MISSING_BUILD_METHOD, cls,
                    "${cls.qualifiedName()} does not declare a `build()` method, but builder classes are expected to"
            )
        }
        builtType?.asClass()?.let { builtClass ->
            val builtMethods = builtClass.filteredMethods(filterReference, includeSuperClassMethods = true).map { it.name() }.toSet()
            for ((setter, expectedGetterNames) in expectedGetters) {
                if (builtMethods.intersect(expectedGetterNames).isEmpty()) {
                    val expectedGetterCalls = expectedGetterNames.map { "$it()" }
                    val errorString = if (expectedGetterCalls.size == 1) {
                        "${builtClass.qualifiedName()} does not declare a " +
                                "`${expectedGetterCalls.first()}` method matching " +
                                setter.describe()
                    } else {
                        "${builtClass.qualifiedName()} does not declare a getter method " +
                                "matching ${setter.describe()} (expected one of: " +
                                "$expectedGetterCalls)"
                    }
                    report(MISSING_GETTER_MATCHING_BUILDER, setter, errorString)
                }
            }
        }
    }

    private fun checkAidl(cls: ClassItem, superClass: ClassItem?, interfaces: Sequence<TypeItem>) {
        // Instead of ClassItem.implements() and .extends() which performs hierarchy
        // searches, here we only want to flag directly extending or implementing:
        val extendsBinder = superClass?.qualifiedName() == "android.os.Binder"
        val implementsIInterface = interfaces.any { it.toTypeString() == "android.os.IInterface" }
        if (extendsBinder || implementsIInterface) {
            val problem = if (extendsBinder) {
                "extends Binder"
            } else {
                "implements IInterface"
            }
            report(
                    RAW_AIDL, cls,
                    "Raw AIDL interfaces must not be exposed: ${cls.simpleName()} $problem"
            )
        }
    }

    private fun checkInternal(cls: ClassItem) {
        if (cls.qualifiedName().startsWith("com.android.")) {
            report(
                    INTERNAL_CLASSES, cls,
                    "Internal classes must not be exposed"
            )
        }
    }

    private fun checkLayering(
            cls: ClassItem,
            methodsAndConstructors: Sequence<MethodItem>,
            fields: Sequence<FieldItem>
    ) {
        fun packageRank(pkg: PackageItem): Int {
            return when (pkg.qualifiedName()) {
                "android.service",
                "android.accessibilityservice",
                "android.inputmethodservice",
                "android.printservice",
                "android.appwidget",
                "android.webkit",
                "android.preference",
                "android.gesture",
                "android.print" -> 10

                "android.app" -> 20
                "android.widget" -> 30
                "android.view" -> 40
                "android.animation" -> 50
                "android.provider" -> 60

                "android.content",
                "android.graphics.drawable" -> 70

                "android.database" -> 80
                "android.text" -> 90
                "android.graphics" -> 100
                "android.os" -> 110
                "android.util" -> 120
                else -> -1
            }
        }

        fun getTypePackage(type: TypeItem?): PackageItem? {
            return if (type == null || type.primitive) {
                null
            } else {
                type.asClass()?.containingPackage()
            }
        }

        fun getTypeRank(type: TypeItem?): Int {
            type ?: return -1
            val pkg = getTypePackage(type) ?: return -1
            return packageRank(pkg)
        }

        val classPackage = cls.containingPackage()
        val classRank = packageRank(classPackage)
        if (classRank == -1) {
            return
        }
        for (field in fields) {
            val fieldTypeRank = getTypeRank(field.type())
            if (fieldTypeRank != -1 && fieldTypeRank < classRank) {
                report(
                        PACKAGE_LAYERING, cls,
                        "Field type `${field.type().toTypeString()}` violates package layering: nothing in `$classPackage` should depend on `${
                            getTypePackage(
                                    field.type()
                            )
                        }`"
                )
            }
        }

        for (method in methodsAndConstructors) {
            val returnType = method.returnType()
            val returnTypeRank = getTypeRank(returnType)
            if (returnTypeRank != -1 && returnTypeRank < classRank) {
                report(
                        PACKAGE_LAYERING, cls,
                        "Method return type `${returnType.toTypeString()}` violates package layering: nothing in `$classPackage` should depend on `${
                            getTypePackage(
                                    returnType
                            )
                        }`"
                )
            }

            for (parameter in method.parameters()) {
                val parameterTypeRank = getTypeRank(parameter.type())
                if (parameterTypeRank != -1 && parameterTypeRank < classRank) {
                    report(
                            PACKAGE_LAYERING, cls,
                            "Method parameter type `${parameter.type().toTypeString()}` violates package layering: nothing in `$classPackage` should depend on `${
                                getTypePackage(
                                        parameter.type()
                                )
                            }`"
                    )
                }
            }
        }
    }

    private fun checkBooleans(methods: Sequence<MethodItem>) {
        /*
            Correct:

            void setVisible(boolean visible);
            boolean isVisible();

            void setHasTransientState(boolean hasTransientState);
            boolean hasTransientState();

            void setCanRecord(boolean canRecord);
            boolean canRecord();

            void setShouldFitWidth(boolean shouldFitWidth);
            boolean shouldFitWidth();

            void setWiFiRoamingSettingEnabled(boolean enabled)
            boolean isWiFiRoamingSettingEnabled()
        */

        fun errorIfExists(methods: Sequence<MethodItem>, trigger: String, expected: String, actual: String) {
            for (method in methods) {
                if (method.name() == actual) {
                    report(
                            GETTER_SETTER_NAMES, method,
                            "Symmetric method for `$trigger` must be named `$expected`; was `$actual`"
                    )
                }
            }
        }

        fun isGetter(method: MethodItem): Boolean {
            val returnType = method.returnType()
            return method.parameters().isEmpty() && returnType.primitive && returnType.toTypeString() == "boolean"
        }

        fun isSetter(method: MethodItem): Boolean {
            return method.parameters().size == 1 && method.parameters()[0].type().toTypeString() == "boolean"
        }

        for (method in methods) {
            val name = method.name()
            if (isGetter(method)) {
                val pattern = goodBooleanGetterSetterPrefixes.match(name, GetterSetterPattern::getter) ?: continue
                val target = name.substring(pattern.getter.length)
                val expectedSetter = "${pattern.setter}$target"

                badBooleanSetterPrefixes.forEach {
                    val actualSetter = "${it}$target"
                    if (actualSetter != expectedSetter) {
                        errorIfExists(methods, name, expectedSetter, actualSetter)
                    }
                }
            } else if (isSetter(method)) {
                val pattern = goodBooleanGetterSetterPrefixes.match(name, GetterSetterPattern::setter) ?: continue
                val target = name.substring(pattern.setter.length)
                val expectedGetter = "${pattern.getter}$target"

                badBooleanGetterPrefixes.forEach {
                    val actualGetter = "${it}$target"
                    if (actualGetter != expectedGetter) {
                        errorIfExists(methods, name, expectedGetter, actualGetter)
                    }
                }
            }
        }
    }

    private fun checkCollections(
            type: TypeItem,
            item: Item
    ) {
        if (type.primitive) {
            return
        }

        when (type.asClass()?.qualifiedName()) {
            "java.util.Vector",
            "java.util.LinkedList",
            "java.util.ArrayList",
            "java.util.Stack",
            "java.util.HashMap",
            "java.util.HashSet",
            "android.util.ArraySet",
            "android.util.ArrayMap" -> {
                if (item.containingClass()?.qualifiedName() == "android.os.Bundle") {
                    return
                }
                val where = when (item) {
                    is MethodItem -> "Return type"
                    is FieldItem -> "Field type"
                    else -> "Parameter type"
                }
                val erased = type.toErasedTypeString()
                report(
                        CONCRETE_COLLECTION, item,
                        "$where is concrete collection (`$erased`); must be higher-level interface"
                )
            }
        }
    }

    fun Item.containingClass(): ClassItem? {
        return when (this) {
            is MemberItem -> this.containingClass()
            is ParameterItem -> this.containingMethod().containingClass()
            is ClassItem -> this
            else -> null
        }
    }

    private fun checkNullableCollections(type: TypeItem, item: Item) {
        if (type.primitive) return
        if (!item.modifiers.isNullable()) return
        val typeAsClass = type.asClass() ?: return

        val superItem: Item? = when (item) {
            is MethodItem -> item.findPredicateSuperMethod(filterReference)
            is ParameterItem -> item.containingMethod().findPredicateSuperMethod(filterReference)
                    ?.parameters()?.find { it.parameterIndex == item.parameterIndex }

            else -> null
        }

        if (superItem?.modifiers?.isNullable() == true) {
            return
        }

        if (type.isArray() ||
                typeAsClass.extendsOrImplements("java.util.Collection") ||
                typeAsClass.extendsOrImplements("kotlin.collections.Collection") ||
                typeAsClass.extendsOrImplements("java.util.Map") ||
                typeAsClass.extendsOrImplements("kotlin.collections.Map") ||
                typeAsClass.qualifiedName() == "android.os.Bundle" ||
                typeAsClass.qualifiedName() == "android.os.PersistableBundle"
        ) {
            val where = when (item) {
                is MethodItem -> "Return type of ${item.describe()}"
                else -> "Type of ${item.describe()}"
            }

            val erased = type.toErasedTypeString(item)
            report(
                    NULLABLE_COLLECTION, item,
                    "$where is a nullable collection (`$erased`); must be non-null"
            )
        }
    }

    private fun checkFlags(fields: Sequence<FieldItem>) {
        var known: MutableMap<String, Int>? = null
        var valueToFlag: MutableMap<Int?, String>? = null
        for (field in fields) {
            val name = field.name()
            val index = name.indexOf("FLAG_")
            if (index != -1) {
                val value = field.initialValue() as? Int ?: continue
                val scope = name.substring(0, index)
                val prev = known?.get(scope) ?: 0
                if (known != null && (prev and value) != 0) {
                    val prevName = valueToFlag?.get(prev)
                    report(
                            OVERLAPPING_CONSTANTS, field,
                            "Found overlapping flag constant values: `$name` with value $value (0x${
                                Integer.toHexString(
                                        value
                                )
                            }) and overlapping flag value $prev (0x${Integer.toHexString(prev)}) from `$prevName`"
                    )
                }
                if (known == null) {
                    known = mutableMapOf()
                }
                known[scope] = value
                if (valueToFlag == null) {
                    valueToFlag = mutableMapOf()
                }
                valueToFlag[value] = name
            }
        }
    }

    private fun checkExceptions(method: MethodItem, filterReference: Predicate<Item>) {
        for (exception in method.filteredThrowsTypes(filterReference)) {
            if (isUncheckedException(exception)) {
                report(
                        BANNED_THROW, method,
                        "Methods must not throw unchecked exceptions"
                )
            } else {
                when (val qualifiedName = exception.qualifiedName()) {
                    "java.lang.Exception",
                    "java.lang.Throwable",
                    "java.lang.Error" -> {
                        report(
                                GENERIC_EXCEPTION, method,
                                "Methods must not throw generic exceptions (`$qualifiedName`)"
                        )
                    }

                    "android.os.RemoteException" -> {
                        when (method.containingClass().qualifiedName()) {
                            "android.content.ContentProviderClient",
                            "android.os.Binder",
                            "android.os.IBinder" -> {
                                // exceptions
                            }

                            else -> {
                                report(
                                        RETHROW_REMOTE_EXCEPTION, method,
                                        "Methods calling system APIs should rethrow `RemoteException` as `RuntimeException` (but do not list it in the throws clause)"
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Unchecked exceptions are subclasses of RuntimeException or Error. These are not
     * checked by the compiler, and it is against API guidelines to put them in the 'throws'.
     * See https://docs.oracle.com/javase/tutorial/essential/exceptions/runtime.html
     */
    private fun isUncheckedException(exception: ClassItem): Boolean {
        val superNames = exception.allSuperClasses().map {
            it.qualifiedName()
        }
        return superNames.any {
            it == "java.lang.RuntimeException" || it == "java.lang.Error"
        }
    }

    private fun checkGoogle(cls: ClassItem, methods: Sequence<MethodItem>, fields: Sequence<FieldItem>) {
        fun checkName(name: String, item: Item) {
            if (name.contains("Google", ignoreCase = true)) {
                report(
                        MENTIONS_GOOGLE, item,
                        "Must never reference Google (`$name`)"
                )
            }
        }

        checkName(cls.simpleName(), cls)
        for (method in methods) {
            checkName(method.name(), method)
        }
        for (field in fields) {
            checkName(field.name(), field)
        }
    }

    private fun checkBitSet(type: TypeItem, typeString: String, item: Item) {
        if (typeString.startsWith("java.util.BitSet") &&
                type.asClass()?.qualifiedName() == "java.util.BitSet"
        ) {
            report(
                    HEAVY_BIT_SET, item,
                    "Type must not be heavy BitSet (${item.describe()})"
            )
        }
    }

    private fun checkManager(cls: ClassItem, methods: Sequence<MethodItem>, constructors: Sequence<ConstructorItem>) {
        if (!cls.simpleName().endsWith("Manager")) {
            return
        }
        for (method in constructors) {
            method.modifiers.isPublic()
            method.modifiers.isPrivate()
            report(
                    MANAGER_CONSTRUCTOR, method,
                    "Managers must always be obtained from Context; no direct constructors"
            )
        }
        for (method in methods) {
            if (method.returnType().asClass() == cls) {
                report(
                        MANAGER_LOOKUP, method,
                        "Managers must always be obtained from Context (`${method.name()}`)"
                )
            }
        }
    }

    private fun checkHasNullability(item: Item) {
        if (!item.requiresNullnessInfo()) return
        if (!item.hasNullnessInfo() && getImplicitNullness(item) == null) {
            val type = item.type()
            val inherited = when (item) {
                is ParameterItem -> item.containingMethod().inheritedMethod
                is FieldItem -> item.inheritedField
                is MethodItem -> item.inheritedMethod
                else -> false
            }
            if (inherited) {
                return // Do not enforce nullability on inherited items (non-overridden)
            }
            if (type != null && type.isTypeParameter()) {
                // Generic types should have declarations of nullability set at the site of where
                // the type is set, so that for Foo<T>, T does not need to specify nullability, but
                // for Foo<Bar>, Bar does.
                return // Do not enforce nullability for generics
            }
            if (item is MethodItem && item.isKotlinProperty()) {
                return // kotlinc doesn't add nullability https://youtrack.jetbrains.com/issue/KT-45771
            }
            val where = when (item) {
                is ParameterItem -> "parameter `${item.name()}` in method `${item.parent()?.name()}`"
                is FieldItem -> {
                    if (item.isKotlin()) {
                        if (item.name() == "INSTANCE") {
                            // Kotlin compiler is not marking it with a nullability annotation
                            // https://youtrack.jetbrains.com/issue/KT-33226
                            return
                        }
                        if (item.modifiers.isCompanion()) {
                            // Kotlin compiler is not marking it with a nullability annotation
                            // https://youtrack.jetbrains.com/issue/KT-33314
                            return
                        }
                    }
                    "field `${item.name()}` in class `${item.parent()}`"
                }

                is ConstructorItem -> "constructor `${item.name()}` return"
                is MethodItem -> {
                    // For methods requiresNullnessInfo and hasNullnessInfo considers both parameters and return,
                    // only warn about non-annotated returns here as parameters will get visited individually.
                    if (item.isConstructor() || item.returnType().primitive) return
                    if (item.modifiers.hasNullnessInfo()) return
                    "method `${item.name()}` return"
                }

                else -> throw IllegalStateException("Unexpected item type: $item")
            }
            report(MISSING_NULLABILITY, item, "Missing nullability on $where")
        } else {
            when (item) {
                is ParameterItem -> {
                    // We don't enforce this check on constructor params
                    if (item.containingMethod().isConstructor()) return
                    if (item.modifiers.isNonNull()) {
                        if (anySuperParameterLacksNullnessInfo(item)) {
                            report(INVALID_NULLABILITY_OVERRIDE, item, "Invalid nullability on parameter `${item.name()}` in method `${item.parent()?.name()}`. Parameters of overrides cannot be NonNull if the super parameter is unannotated.")
                        } else if (anySuperParameterIsNullable(item)) {
                            report(INVALID_NULLABILITY_OVERRIDE, item, "Invalid nullability on parameter `${item.name()}` in method `${item.parent()?.name()}`. Parameters of overrides cannot be NonNull if super parameter is Nullable.")
                        }
                    }
                }

                is MethodItem -> {
                    // We don't enforce this check on constructors
                    if (item.isConstructor()) return
                    if (item.modifiers.isNullable()) {
                        if (anySuperMethodLacksNullnessInfo(item)) {
                            report(INVALID_NULLABILITY_OVERRIDE, item, "Invalid nullability on method `${item.name()}` return. Overrides of unannotated super method cannot be Nullable.")
                        } else if (anySuperMethodIsNonNull(item)) {
                            report(INVALID_NULLABILITY_OVERRIDE, item, "Invalid nullability on method `${item.name()}` return. Overrides of NonNull methods cannot be Nullable.")
                        }
                    }
                }
            }
        }
    }

    private fun anySuperMethodIsNonNull(method: MethodItem): Boolean {
        return method.superMethods().any { superMethod ->
            // Disable check for generics
            superMethod.modifiers.isNonNull() && !superMethod.returnType().isTypeParameter()
        }
    }

    private fun anySuperParameterIsNullable(parameter: ParameterItem): Boolean {
        val supers = parameter.containingMethod().superMethods()
        return supers.all { superMethod ->
            // Disable check for generics
            superMethod.parameters().none {
                it.type().isTypeParameter()
            }
        } && supers.any { superMethod ->
            superMethod.parameters().firstOrNull { param ->
                parameter.parameterIndex == param.parameterIndex
            }?.modifiers?.isNullable() ?: false
        }
    }

    private fun anySuperMethodLacksNullnessInfo(method: MethodItem): Boolean {
        return method.superMethods().any { superMethod ->
            // Disable check for generics
            !superMethod.hasNullnessInfo() && !superMethod.returnType().isTypeParameter()
        }
    }

    private fun anySuperParameterLacksNullnessInfo(parameter: ParameterItem): Boolean {
        val supers = parameter.containingMethod().superMethods()
        return supers.all { superMethod ->
            // Disable check for generics
            superMethod.parameters().none {
                it.type().isTypeParameter()
            }
        } && supers.any { superMethod ->
            !(
                    superMethod.parameters().firstOrNull { param ->
                        parameter.parameterIndex == param.parameterIndex
                    }?.hasNullnessInfo() ?: true
                    )
        }
    }

    private fun checkBoxed(type: TypeItem, item: Item) {
        fun isBoxType(qualifiedName: String): Boolean {
            return when (qualifiedName) {
                "java.lang.Number",
                "java.lang.Byte",
                "java.lang.Double",
                "java.lang.Float",
                "java.lang.Integer",
                "java.lang.Long",
                "java.lang.Short",
                "java.lang.Boolean" ->
                    true

                else ->
                    false
            }
        }

        val qualifiedName = type.asClass()?.qualifiedName() ?: return
        if (isBoxType(qualifiedName)) {
            report(
                    AUTO_BOXING, item,
                    "Must avoid boxed primitives (`$qualifiedName`)"
            )
        }
    }

    private fun checkStaticUtils(
            cls: ClassItem,
            methods: Sequence<MethodItem>,
            constructors: Sequence<ConstructorItem>,
            fields: Sequence<FieldItem>
    ) {
        if (!cls.isClass()) {
            return
        }

        val hasDefaultConstructor = cls.hasImplicitDefaultConstructor() || run {
            if (constructors.count() == 1) {
                val constructor = constructors.first()
                constructor.parameters().isEmpty() && constructor.modifiers.isPublic()
            } else {
                false
            }
        }

        if (hasDefaultConstructor) {
            val qualifiedName = cls.qualifiedName()
            if (qualifiedName.startsWith("android.opengl.") ||
                    qualifiedName.startsWith("android.R.") ||
                    qualifiedName == "android.R"
            ) {
                return
            }

            if (methods.none() && fields.none()) {
                return
            }

            if (methods.none { !it.modifiers.isStatic() } &&
                    fields.none { !it.modifiers.isStatic() }
            ) {
                report(
                        STATIC_UTILS, cls,
                        "Fully-static utility classes must not have constructor"
                )
            }
        }
    }

    private fun checkOverloadArgs(cls: ClassItem, methods: Sequence<MethodItem>) {
        if (cls.qualifiedName().startsWith("android.opengl")) {
            return
        }

        val overloads = mutableMapOf<String, MutableList<MethodItem>>()
        for (method in methods) {
            if (!method.deprecated) {
                val name = method.name()
                val list = overloads[name] ?: run {
                    val new = mutableListOf<MethodItem>()
                    overloads[name] = new
                    new
                }
                list.add(method)
            }
        }

        // Look for arguments common across all overloads
        fun cluster(args: List<ParameterItem>): MutableSet<String> {
            val count = mutableMapOf<String, Int>()
            val res = mutableSetOf<String>()
            for (parameter in args) {
                val a = parameter.type().toTypeString()
                val currCount = count[a] ?: 1
                res.add("$a#$currCount")
                count[a] = currCount + 1
            }
            return res
        }

        for ((_, methodList) in overloads.entries) {
            if (methodList.size <= 1) {
                continue
            }

            val commonArgs = cluster(methodList[0].parameters())
            for (m in methodList) {
                val clustered = cluster(m.parameters())
                commonArgs.removeAll(clustered)
            }
            if (commonArgs.isEmpty()) {
                continue
            }

            // Require that all common arguments are present at the start of the signature
            var lockedSig: List<ParameterItem>? = null
            val commonArgCount = commonArgs.size
            for (m in methodList) {
                val sig = m.parameters().subList(0, commonArgCount)
                val cluster = cluster(sig)
                if (!cluster.containsAll(commonArgs)) {
                    report(
                            COMMON_ARGS_FIRST, m,
                            "Expected common arguments ${commonArgs.joinToString()}} at beginning of overloaded method ${m.describe()}"
                    )
                } else if (lockedSig == null) {
                    lockedSig = sig
                } else if (lockedSig != sig) {
                    report(
                            CONSISTENT_ARGUMENT_ORDER, m,
                            "Expected consistent argument ordering between overloads: ${lockedSig.joinToString()}}"
                    )
                }
            }
        }
    }

    private fun checkCallbackHandlers(
            cls: ClassItem,
            methodsAndConstructors: Sequence<MethodItem>,
            superClass: ClassItem?
    ) {
        fun packageContainsSegment(packageName: String?, segment: String): Boolean {
            packageName ?: return false
            return (
                    packageName.contains(segment) &&
                            (packageName.contains(".$segment.") || packageName.endsWith(".$segment"))
                    )
        }

        fun skipPackage(packageName: String?): Boolean {
            packageName ?: return false
            for (segment in uiPackageParts) {
                if (packageContainsSegment(packageName, segment)) {
                    return true
                }
            }

            return false
        }

        // Ignore UI packages which assume main thread
        val classPackage = cls.containingPackage().qualifiedName()
        val extendsPackage = superClass?.containingPackage()?.qualifiedName()

        if (skipPackage(classPackage) || skipPackage(extendsPackage)) {
            return
        }

        // Ignore UI classes which assume main thread
        if (packageContainsSegment(classPackage, "app") ||
                packageContainsSegment(extendsPackage, "app")
        ) {
            val fullName = cls.fullName()
            if (fullName.contains("ActionBar") ||
                    fullName.contains("Dialog") ||
                    fullName.contains("Application") ||
                    fullName.contains("Activity") ||
                    fullName.contains("Fragment") ||
                    fullName.contains("Loader")
            ) {
                return
            }
        }
        if (packageContainsSegment(classPackage, "content") ||
                packageContainsSegment(extendsPackage, "content")
        ) {
            val fullName = cls.fullName()
            if (fullName.contains("Loader")) {
                return
            }
        }

        val found = mutableMapOf<String, MethodItem>()
        val byName = mutableMapOf<String, MutableList<MethodItem>>()
        for (method in methodsAndConstructors) {
            val name = method.name()
            if (name.startsWith("unregister")) {
                continue
            }
            if (name.startsWith("remove")) {
                continue
            }
            if (name.startsWith("on") && onCallbackNamePattern.matches(name)) {
                continue
            }

            val list = byName[name] ?: run {
                val new = mutableListOf<MethodItem>()
                byName[name] = new
                new
            }
            list.add(method)

            for (parameter in method.parameters()) {
                val type = parameter.type().toTypeString()
                if (type.endsWith("Listener") ||
                        type.endsWith("Callback") ||
                        type.endsWith("Callbacks")
                ) {
                    found[name] = method
                }
            }
        }

        for (f in found.values) {
            var takesExec = false

            // TODO: apilint computed takes_handler but did not use it; should we add more checks or conditions?
            // var takesHandler = false

            val name = f.name()
            for (method in byName[name]!!) {
                // if (method.parameters().any { it.type().toTypeString() == "android.os.Handler" }) {
                //    takesHandler = true
                // }
                if (method.parameters().any { it.type().toTypeString() == "java.util.concurrent.Executor" }) {
                    takesExec = true
                }
            }
            if (!takesExec) {
                report(
                        EXECUTOR_REGISTRATION, f,
                        "Registration methods should have overload that accepts delivery Executor: `$name`"
                )
            }
        }
    }

    private fun checkContextFirst(method: MethodItem) {
        val parameters = method.parameters()
        if (parameters.size > 1 && parameters[0].type().toTypeString() != "android.content.Context") {
            for (i in 1 until parameters.size) {
                val p = parameters[i]
                if (p.type().toTypeString() == "android.content.Context") {
                    report(
                            CONTEXT_FIRST, p,
                            "Context is distinct, so it must be the first argument (method `${method.name()}`)"
                    )
                }
            }
        }
        if (parameters.size > 1 && parameters[0].type().toTypeString() != "android.content.ContentResolver") {
            for (i in 1 until parameters.size) {
                val p = parameters[i]
                if (p.type().toTypeString() == "android.content.ContentResolver") {
                    report(
                            CONTEXT_FIRST, p,
                            "ContentResolver is distinct, so it must be the first argument (method `${method.name()}`)"
                    )
                }
            }
        }
    }

    private fun checkListenerLast(method: MethodItem) {
        val name = method.name()
        if (name.contains("Listener") || name.contains("Callback")) {
            return
        }

        val parameters = method.parameters()
        if (parameters.size > 1) {
            var found = false
            for (parameter in parameters) {
                val type = parameter.type().toTypeString()
                if (type.endsWith("Callback") || type.endsWith("Callbacks") || type.endsWith("Listener")) {
                    found = true
                } else if (found) {
                    report(
                            LISTENER_LAST, parameter,
                            "Listeners should always be at end of argument list (method `${method.name()}`)"
                    )
                }
            }
        }
    }

    private fun checkResourceNames(cls: ClassItem, fields: Sequence<FieldItem>) {
        if (!cls.qualifiedName().startsWith("android.R.")) {
            return
        }

        val resourceType = ResourceType.fromClassName(cls.simpleName()) ?: return
        when (resourceType) {
            ANIM,
            ANIMATOR,
            COLOR,
            DIMEN,
            DRAWABLE,
            FONT,
            INTERPOLATOR,
            LAYOUT,
            MENU,
            MIPMAP,
            NAVIGATION,
            PLURALS,
            RAW,
            STRING,
            TRANSITION,
            XML -> {
                // Resources defined by files are foo_bar_baz
                // Note: it's surprising that dimen, plurals and string are in this list since
                // they are value resources, not file resources, but keeping api lint compatibility
                // for now.

                for (field in fields) {
                    val name = field.name()
                    if (name.startsWith("config_")) {
                        if (!configFieldPattern.matches(name)) {
                            report(
                                    CONFIG_FIELD_NAME, field,
                                    "Expected config name to be in the `config_fooBarBaz` style, was `$name`"
                            )
                        }
                        continue
                    }
                    if (!resourceFileFieldPattern.matches(name)) {
                        report(
                                RESOURCE_FIELD_NAME, field,
                                "Expected resource name in `${cls.qualifiedName()}` to be in the `foo_bar_baz` style, was `$name`"
                        )
                    }
                }
            }

            ARRAY,
            ATTR,
            BOOL,
            FRACTION,
            ID,
            INTEGER -> {
                // Resources defined inside files are fooBarBaz
                for (field in fields) {
                    val name = field.name()
                    if (name.startsWith("config_") && configFieldPattern.matches(name)) {
                        continue
                    }
                    if (name.startsWith("layout_") && layoutFieldPattern.matches(name)) {
                        continue
                    }
                    if (name.startsWith("state_") && stateFieldPattern.matches(name)) {
                        continue
                    }
                    if (resourceValueFieldPattern.matches(name)) {
                        continue
                    }
                    report(
                            RESOURCE_VALUE_FIELD_NAME, field,
                            "Expected resource name in `${cls.qualifiedName()}` to be in the `fooBarBaz` style, was `$name`"
                    )
                }
            }

            STYLE -> {
                for (field in fields) {
                    val name = field.name()
                    if (!styleFieldPattern.matches(name)) {
                        report(
                                RESOURCE_STYLE_FIELD_NAME, field,
                                "Expected resource name in `${cls.qualifiedName()}` to be in the `FooBar_Baz` style, was `$name`"
                        )
                    }
                }
            }

            STYLEABLE, // appears as R class but name check is implicitly done as part of style class check
                // DECLARE_STYLEABLE,
            STYLE_ITEM,
            PUBLIC,
            SAMPLE_DATA,
            AAPT -> {
                // no-op; these are resource "types" in XML but not present as R classes
                // Listed here explicitly to force compiler error as new resource types
                // are added.
            }
        }
    }

    private fun checkFiles(methodsAndConstructors: Sequence<MethodItem>) {
        var hasFile: MutableSet<MethodItem>? = null
        var hasStream: MutableSet<String>? = null
        for (method in methodsAndConstructors) {
            for (parameter in method.parameters()) {
                when (parameter.type().toTypeString()) {
                    "java.io.File" -> {
                        val set = hasFile ?: run {
                            val new = mutableSetOf<MethodItem>()
                            hasFile = new
                            new
                        }
                        set.add(method)
                    }

                    "java.io.FileDescriptor",
                    "android.os.ParcelFileDescriptor",
                    "java.io.InputStream",
                    "java.io.OutputStream" -> {
                        val set = hasStream ?: run {
                            val new = mutableSetOf<String>()
                            hasStream = new
                            new
                        }
                        set.add(method.name())
                    }
                }
            }
        }
        val files = hasFile
        if (files != null) {
            val streams = hasStream
            for (method in files) {
                if (streams == null || !streams.contains(method.name())) {
                    report(
                            STREAM_FILES, method,
                            "Methods accepting `File` should also accept `FileDescriptor` or streams: ${method.describe()}"
                    )
                }
            }
        }
    }

    private fun checkManagerList(cls: ClassItem, methods: Sequence<MethodItem>) {
        if (!cls.simpleName().endsWith("Manager")) {
            return
        }
        for (method in methods) {
            val returnType = method.returnType()
            if (returnType.primitive) {
                return
            }
            val type = returnType.toTypeString()
            if (type.startsWith("android.") && returnType.isArray()) {
                report(
                        PARCELABLE_LIST, method,
                        "Methods should return `List<? extends Parcelable>` instead of `Parcelable[]` to support `ParceledListSlice` under the hood: ${method.describe()}"
                )
            }
        }
    }

    private fun checkAbstractInner(cls: ClassItem) {
        if (!cls.isTopLevelClass() && cls.isClass() && cls.modifiers.isAbstract() && !cls.modifiers.isStatic()) {
            report(
                    ABSTRACT_INNER, cls,
                    "Abstract inner classes should be static to improve testability: ${cls.describe()}"
            )
        }
    }

    private fun checkError(cls: ClassItem, superClass: ClassItem?) {
        superClass ?: return
        if (superClass.simpleName().endsWith("Error")) {
            report(
                    EXTENDS_ERROR, cls,
                    "Trouble must be reported through an `Exception`, not an `Error` (`${cls.simpleName()}` extends `${superClass.simpleName()}`)"
            )
        }
        if (superClass.simpleName().endsWith("Exception") && !cls.simpleName().endsWith("Exception")) {
            report(
                    EXCEPTION_NAME, cls,
                    "Exceptions must be named `FooException`, was `${cls.simpleName()}`"
            )
        }
    }

    private fun checkUnits(method: MethodItem) {
        val returnType = method.returnType()
        var type = returnType.toTypeString()
        val name = method.name()
        if (type == "int" || type == "long" || type == "short") {
            if (badUnits.any { name.endsWith(it.key) }) {
                val typeIsTypeDef = method.modifiers.annotations().any { annotation ->
                    val annotationClass = annotation.resolve() ?: return@any false
                    annotationClass.modifiers.annotations().any { it.isTypeDefAnnotation() }
                }
                if (!typeIsTypeDef) {
                    val badUnit = badUnits.keys.find { name.endsWith(it) }
                    val value = badUnits[badUnit]
                    report(
                            METHOD_NAME_UNITS, method,
                            "Expected method name units to be `$value`, was `$badUnit` in `$name`"
                    )
                }
            }
        } else if (type == "void") {
            if (method.parameters().size != 1) {
                return
            }
            type = method.parameters()[0].type().toTypeString()
        }
        if (name.endsWith("Fraction") && (type == "int" || type == "long" || type == "short")) {
            report(
                    FRACTION_FLOAT, method,
                    "Fractions must use floats, was `$type` in `$name`"
            )
        } else if (name.endsWith("Percentage") && (type == "float" || type == "double")) {
            report(
                    PERCENTAGE_INT, method,
                    "Percentage must use ints, was `$type` in `$name`"
            )
        }
    }

    private fun checkCloseable(cls: ClassItem, methods: Sequence<MethodItem>) {
        // AutoCloseable has been added in API 19, so libraries with minSdkVersion <19 cannot use it. If the version
        // is not set, then keep the check enabled.
        val minSdkVersion = codebase.getMinSdkVersion()
        if (minSdkVersion is SetMinSdkVersion && minSdkVersion.value < 19) {
            return
        }

        val foundMethods = methods.filter { method ->
            when (method.name()) {
                "close", "release", "destroy", "finish", "finalize", "disconnect", "shutdown", "stop", "free", "quit" -> true
                else -> false
            }
        }
        if (foundMethods.iterator().hasNext() && !cls.implements("java.lang.AutoCloseable")) { // includes java.io.Closeable
            val foundMethodsDescriptions = foundMethods.joinToString { method -> "${method.name()}()" }
            report(
                    NOT_CLOSEABLE, cls,
                    "Classes that release resources ($foundMethodsDescriptions) should implement AutoCloseable and CloseGuard: ${cls.describe()}"
            )
        }
    }

    private fun checkNotKotlinOperator(methods: Sequence<MethodItem>) {
        fun flagKotlinOperator(method: MethodItem, message: String) {
            if (method.isKotlin()) {
                report(
                        KOTLIN_OPERATOR, method,
                        "Note that adding the `operator` keyword would allow calling this method using operator syntax"
                )
            } else {
                report(
                        KOTLIN_OPERATOR, method,
                        "$message (this is usually desirable; just make sure it makes sense for this type of object)"
                )
            }
        }

        for (method in methods) {
            if (method.modifiers.isStatic() || method.modifiers.isOperator() || method.superMethods().isNotEmpty()) {
                continue
            }
            when (val name = method.name()) {
                // https://kotlinlang.org/docs/reference/operator-overloading.html#unary-prefix-operators
                "unaryPlus", "unaryMinus", "not" -> {
                    if (method.parameters().isEmpty()) {
                        flagKotlinOperator(
                                method, "Method can be invoked as a unary operator from Kotlin: `$name`"
                        )
                    }
                }
                // https://kotlinlang.org/docs/reference/operator-overloading.html#increments-and-decrements
                "inc", "dec" -> {
                    if (method.parameters().isEmpty() && method.returnType().toTypeString() != "void") {
                        flagKotlinOperator(
                                method, "Method can be invoked as a pre/postfix inc/decrement operator from Kotlin: `$name`"
                        )
                    }
                }
                // https://kotlinlang.org/docs/reference/operator-overloading.html#arithmetic
                "plus", "minus", "times", "div", "rem", "mod", "rangeTo" -> {
                    if (method.parameters().size == 1) {
                        flagKotlinOperator(
                                method, "Method can be invoked as a binary operator from Kotlin: `$name`"
                        )
                    }
                    val assignName = name + "Assign"

                    if (methods.any {
                                it.name() == assignName &&
                                        it.parameters().size == 1 &&
                                        it.returnType().toTypeString() == "void"
                            }
                    ) {
                        report(
                                UNIQUE_KOTLIN_OPERATOR, method,
                                "Only one of `$name` and `${name}Assign` methods should be present for Kotlin"
                        )
                    }
                }
                // https://kotlinlang.org/docs/reference/operator-overloading.html#in
                "contains" -> {
                    if (method.parameters().size == 1 && method.returnType().toTypeString() == "boolean") {
                        flagKotlinOperator(
                                method, "Method can be invoked as a \"in\" operator from Kotlin: `$name`"
                        )
                    }
                }
                // https://kotlinlang.org/docs/reference/operator-overloading.html#indexed
                "get" -> {
                    if (method.parameters().isNotEmpty()) {
                        flagKotlinOperator(
                                method, "Method can be invoked with an indexing operator from Kotlin: `$name`"
                        )
                    }
                }
                // https://kotlinlang.org/docs/reference/operator-overloading.html#indexed
                "set" -> {
                    if (method.parameters().size > 1) {
                        flagKotlinOperator(
                                method, "Method can be invoked with an indexing operator from Kotlin: `$name`"
                        )
                    }
                }
                // https://kotlinlang.org/docs/reference/operator-overloading.html#invoke
                "invoke" -> {
                    if (method.parameters().size > 1) {
                        flagKotlinOperator(
                                method, "Method can be invoked with function call syntax from Kotlin: `$name`"
                        )
                    }
                }
                // https://kotlinlang.org/docs/reference/operator-overloading.html#assignments
                "plusAssign", "minusAssign", "timesAssign", "divAssign", "remAssign", "modAssign" -> {
                    if (method.parameters().size == 1 && method.returnType().toTypeString() == "void") {
                        flagKotlinOperator(
                                method, "Method can be invoked as a compound assignment operator from Kotlin: `$name`"
                        )
                    }
                }
            }
        }
    }

    private fun checkCollectionsOverArrays(type: TypeItem, typeString: String, item: Item) {
        if (!type.isArray() || (item is ParameterItem && item.isVarArgs())) {
            return
        }

        when (typeString) {
            "java.lang.String[]",
            "byte[]",
            "short[]",
            "int[]",
            "long[]",
            "float[]",
            "double[]",
            "boolean[]",
            "char[]" -> {
                return
            }

            else -> {
                val action = when (item) {
                    is MethodItem -> {
                        if (item.name() == "values" && item.containingClass().isEnum()) {
                            return
                        }
                        if (item.containingClass().extends("java.lang.annotation.Annotation")) {
                            // Annotation are allowed to use arrays
                            return
                        }
                        "Method should return"
                    }

                    is FieldItem -> "Field should be"
                    else -> "Method parameter should be"
                }
                val component = type.asClass()?.simpleName() ?: ""
                report(
                        ARRAY_RETURN, item,
                        "$action Collection<$component> (or subclass) instead of raw array; was `$typeString`"
                )
            }
        }
    }

    private fun checkUserHandle(cls: ClassItem, methods: Sequence<MethodItem>) {
        val qualifiedName = cls.qualifiedName()
        if (qualifiedName == "android.content.pm.LauncherApps" ||
                qualifiedName == "android.os.UserHandle" ||
                qualifiedName == "android.os.UserManager"
        ) {
            return
        }

        for (method in methods) {
            val parameters = method.parameters()
            if (parameters.isEmpty()) {
                continue
            }
            val name = method.name()
            if (name.startsWith("on") && onCallbackNamePattern.matches(name)) {
                continue
            }
            val hasArg = parameters.any { it.type().toTypeString() == "android.os.UserHandle" }
            if (!hasArg) {
                continue
            }
            if (qualifiedName.endsWith("Manager")) {
                report(
                        USER_HANDLE, method,
                        "When a method overload is needed to target a specific " +
                                "UserHandle, callers should be directed to use " +
                                "Context.createPackageContextAsUser() and re-obtain the relevant " +
                                "Manager, and no new API should be added"
                )
            } else if (!(name.endsWith("AsUser") || name.endsWith("ForUser"))) {
                report(
                        USER_HANDLE_NAME, method,
                        "Method taking UserHandle should be named `doFooAsUser` or `queryFooForUser`, was `$name`"
                )
            }
        }
    }

    private fun checkParams(cls: ClassItem) {
        val qualifiedName = cls.qualifiedName()
        for (suffix in badParameterClassNames) {
            if (qualifiedName.endsWith(suffix) && !(
                            (
                                    qualifiedName.endsWith("Params") ||
                                            qualifiedName == "android.app.ActivityOptions" ||
                                            qualifiedName == "android.app.BroadcastOptions" ||
                                            qualifiedName == "android.os.Bundle" ||
                                            qualifiedName == "android.os.BaseBundle" ||
                                            qualifiedName == "android.os.PersistableBundle"
                                    )
                            )
            ) {
                report(
                        USER_HANDLE_NAME, cls,
                        "Classes holding a set of parameters should be called `FooParams`, was `${cls.simpleName()}`"
                )
            }
        }
    }

    private fun checkServices(field: FieldItem) {
        val type = field.type()
        if (!type.isString() || !field.modifiers.isFinal() || !field.modifiers.isStatic() ||
                field.containingClass().qualifiedName() != "android.content.Context"
        ) {
            return
        }
        val name = field.name()
        val endsWithService = name.endsWith("_SERVICE")
        val value = field.initialValue(requireConstant = true) as? String

        if (value == null) {
            val mustEndInService =
                    if (!endsWithService) " and its name must end with `_SERVICE`" else ""

            report(
                    SERVICE_NAME, field,
                    "Non-constant service constant `$name`. Must be static," +
                            " final and initialized with a String literal$mustEndInService."
            )
            return
        }

        if (name.endsWith("_MANAGER_SERVICE")) {
            report(
                    SERVICE_NAME, field,
                    "Inconsistent service constant name; expected " +
                            "`${name.removeSuffix("_MANAGER_SERVICE")}_SERVICE`, was `$name`"
            )
        } else if (endsWithService) {
            val service = name.substring(0, name.length - "_SERVICE".length).lowercase(Locale.US)
            if (service != value) {
                report(
                        SERVICE_NAME, field,
                        "Inconsistent service value; expected `$service`, was `$value` (Note: Do not" +
                                " change the name of already released services, which will break tools" +
                                " using `adb shell dumpsys`." +
                                " Instead add `@SuppressLint(\"${SERVICE_NAME.name}\"))`"
                )
            }
        } else {
            val valueUpper = value.uppercase(Locale.US)
            report(
                    SERVICE_NAME, field,
                    "Inconsistent service constant name;" +
                            " expected `${valueUpper}_SERVICE`, was `$name`"
            )
        }
    }

    private fun checkTense(method: MethodItem) {
        val name = method.name()
        if (name.endsWith("Enable")) {
            if (method.containingClass().qualifiedName().startsWith("android.opengl")) {
                return
            }
            report(
                    METHOD_NAME_TENSE, method,
                    "Unexpected tense; probably meant `enabled`, was `$name`"
            )
        }
    }

    private fun checkIcu(type: TypeItem, typeString: String, item: Item) {
        if (type.primitive) {
            return
        }
        // ICU types have been added in API 24, so libraries with minSdkVersion <24 cannot use them.
        // If the version is not set, then keep the check enabled.
        val minSdkVersion = codebase.getMinSdkVersion()
        if (minSdkVersion is SetMinSdkVersion && minSdkVersion.value < 24) {
            return
        }
        val better = when (typeString) {
            "java.util.TimeZone" -> "android.icu.util.TimeZone"
            "java.util.Calendar" -> "android.icu.util.Calendar"
            "java.util.Locale" -> "android.icu.util.ULocale"
            "java.util.ResourceBundle" -> "android.icu.util.UResourceBundle"
            "java.util.SimpleTimeZone" -> "android.icu.util.SimpleTimeZone"
            "java.util.StringTokenizer" -> "android.icu.util.StringTokenizer"
            "java.util.GregorianCalendar" -> "android.icu.util.GregorianCalendar"
            "java.lang.Character" -> "android.icu.lang.UCharacter"
            "java.text.BreakIterator" -> "android.icu.text.BreakIterator"
            "java.text.Collator" -> "android.icu.text.Collator"
            "java.text.DecimalFormatSymbols" -> "android.icu.text.DecimalFormatSymbols"
            "java.text.NumberFormat" -> "android.icu.text.NumberFormat"
            "java.text.DateFormatSymbols" -> "android.icu.text.DateFormatSymbols"
            "java.text.DateFormat" -> "android.icu.text.DateFormat"
            "java.text.SimpleDateFormat" -> "android.icu.text.SimpleDateFormat"
            "java.text.MessageFormat" -> "android.icu.text.MessageFormat"
            "java.text.DecimalFormat" -> "android.icu.text.DecimalFormat"
            else -> return
        }
        report(
                USE_ICU, item,
                "Type `$typeString` should be replaced with richer ICU type `$better`"
        )
    }

    private fun checkClone(method: MethodItem) {
        if (method.name() == "clone" && method.parameters().isEmpty()) {
            report(
                    NO_CLONE, method,
                    "Provide an explicit copy constructor instead of implementing `clone()`"
            )
        }
    }

    private fun checkPfd(type: String, item: Item) {
        if (item.containingClass()?.qualifiedName() in lowLevelFileClassNames ||
                isServiceDumpMethod(item)
        ) {
            return
        }

        if (type == "java.io.FileDescriptor") {
            report(
                    USE_PARCEL_FILE_DESCRIPTOR, item,
                    "Must use ParcelFileDescriptor instead of FileDescriptor in ${item.describe()}"
            )
        } else if (type == "int" && item is MethodItem) {
            val name = item.name()
            if (name.contains("Fd") || name.contains("FD") || name.contains("FileDescriptor", ignoreCase = true)) {
                report(
                        USE_PARCEL_FILE_DESCRIPTOR, item,
                        "Must use ParcelFileDescriptor instead of FileDescriptor in ${item.describe()}"
                )
            }
        }
    }

    private fun checkNumbers(type: String, item: Item) {
        if (type == "short" || type == "byte") {
            report(
                    NO_BYTE_OR_SHORT, item,
                    "Should avoid odd sized primitives; use `int` instead of `$type` in ${item.describe()}"
            )
        }
    }

    private fun checkSingleton(
            cls: ClassItem,
            methods: Sequence<MethodItem>,
            constructors: Sequence<ConstructorItem>
    ) {
        if (constructors.none()) {
            return
        }
        if (methods.any { it.name().startsWith("get") && it.name().endsWith("Instance") && it.modifiers.isStatic() }) {
            for (constructor in constructors) {
                report(
                        SINGLETON_CONSTRUCTOR, constructor,
                        "Singleton classes should use `getInstance()` methods: `${cls.simpleName()}`"
                )
            }
        }
    }

    private fun checkExtends(cls: ClassItem) {
        // Call cls.superClass().extends() instead of cls.extends() since extends returns true for self
        val superCls = cls.superClass() ?: return
        if (superCls.extends("android.os.AsyncTask")) {
            report(
                    FORBIDDEN_SUPER_CLASS, cls,
                    "${cls.simpleName()} should not extend `AsyncTask`. AsyncTask is an implementation detail. Expose a listener or, in androidx, a `ListenableFuture` API instead"
            )
        }
        if (superCls.extends("android.app.Activity")) {
            report(
                    FORBIDDEN_SUPER_CLASS, cls,
                    "${cls.simpleName()} should not extend `Activity`. Activity subclasses are impossible to compose. Expose a composable API instead."
            )
        }
        badFutureTypes.firstOrNull { cls.extendsOrImplements(it) }?.let {
            val extendOrImplement = if (cls.extends(it)) "extend" else "implement"
            report(
                    BAD_FUTURE, cls,
                    "${cls.simpleName()} should not $extendOrImplement `$it`." +
                            " In AndroidX, use (but do not extend) ListenableFuture. In platform, use a combination of OutcomeReceiver<R,E>, Executor, and CancellationSignal`."
            )
        }
    }

    private fun checkTypedef(cls: ClassItem) {
        if (cls.isAnnotationType()) {
            cls.modifiers.annotations().firstOrNull { it.isTypeDefAnnotation() }?.let {
                report(PUBLIC_TYPEDEF, cls, "Don't expose ${AnnotationItem.simpleName(it)}: ${cls.simpleName()} must be hidden.")
            }
        }
    }

    private fun checkUri(typeString: String, item: Item) {
        badUriTypes.firstOrNull { typeString.contains(it) }?.let {
            report(
                    ANDROID_URI, item, "Use android.net.Uri instead of $it (${item.describe()})"
            )
        }
    }

    private fun checkFutures(typeString: String, item: Item) {
        badFutureTypes.firstOrNull { typeString.contains(it) }?.let {
            report(
                    BAD_FUTURE, item,
                    "Use ListenableFuture (library), " +
                            "or a combination of OutcomeReceiver<R,E>, Executor, and CancellationSignal (platform) instead of $it (${item.describe()})"
            )
        }
    }

    private fun checkMethodSuffixListenableFutureReturn(type: TypeItem, method: MethodItem) {
        if (type.toTypeString().contains(listenableFuture) &&
                !method.isConstructor() &&
                !method.name().endsWith("Async")
        ) {
            report(
                    ASYNC_SUFFIX_FUTURE,
                    method,
                    "Methods returning $listenableFuture should have a suffix *Async to " +
                            "reserve unmodified name for a suspend function"
            )
        }
    }

    private fun isInteresting(cls: ClassItem): Boolean {
        val name = cls.qualifiedName()
        for (prefix in options.checkApiIgnorePrefix) {
            if (name.startsWith(prefix)) {
                return false
            }
        }
        return true
    }

    companion object {

        private data class GetterSetterPattern(val getter: String, val setter: String)

        private val goodBooleanGetterSetterPrefixes = listOf(
                GetterSetterPattern("has", "setHas"),
                GetterSetterPattern("can", "setCan"),
                GetterSetterPattern("should", "setShould"),
                GetterSetterPattern("is", "set")
        )

        private fun List<GetterSetterPattern>.match(
                name: String,
                prop: (GetterSetterPattern) -> String
        ) = firstOrNull {
            name.startsWith(prop(it)) && name.getOrNull(prop(it).length)?.let { charAfterPrefix ->
                charAfterPrefix.isUpperCase() || charAfterPrefix.isDigit()
            } ?: false
        }

        private val badBooleanGetterPrefixes = listOf("isHas", "isCan", "isShould", "get", "is")
        private val badBooleanSetterPrefixes = listOf("setIs", "set")

        private val badParameterClassNames = listOf(
                "Param", "Parameter", "Parameters", "Args", "Arg", "Argument", "Arguments", "Options", "Bundle"
        )

        private val badUriTypes = listOf("java.net.URL", "java.net.URI", "android.net.URL")

        private val badFutureTypes = listOf(
                "java.util.concurrent.CompletableFuture",
                "java.util.concurrent.Future"
        )

        private val listenableFuture = "com.google.common.util.concurrent.ListenableFuture"

        /**
         * Classes for manipulating file descriptors directly, where using ParcelFileDescriptor
         * isn't required
         */
        private val lowLevelFileClassNames = listOf(
                "android.os.FileUtils",
                "android.system.Os",
                "android.net.util.SocketUtils",
                "android.os.NativeHandle",
                "android.os.ParcelFileDescriptor"
        )

        /**
         * Classes which already use bare fields extensively, and bare fields are thus allowed for
         * consistency with existing API surface.
         */
        private val classesWithBareFields = listOf(
                "android.app.ActivityManager.RecentTaskInfo",
                "android.app.Notification",
                "android.content.pm.ActivityInfo",
                "android.content.pm.ApplicationInfo",
                "android.content.pm.ComponentInfo",
                "android.content.pm.ResolveInfo",
                "android.content.pm.FeatureGroupInfo",
                "android.content.pm.InstrumentationInfo",
                "android.content.pm.PackageInfo",
                "android.content.pm.PackageItemInfo",
                "android.content.res.Configuration",
                "android.graphics.BitmapFactory.Options",
                "android.os.Message",
                "android.system.StructPollfd"
        )

        /**
         * Classes containing setting provider keys.
         */
        private val settingsKeyClasses = listOf(
                "android.provider.Settings.Global",
                "android.provider.Settings.Secure",
                "android.provider.Settings.System"
        )

        private val badUnits = mapOf(
                "Ns" to "Nanos",
                "Ms" to "Millis or Micros",
                "Sec" to "Seconds",
                "Secs" to "Seconds",
                "Hr" to "Hours",
                "Hrs" to "Hours",
                "Mo" to "Months",
                "Mos" to "Months",
                "Yr" to "Years",
                "Yrs" to "Years",
                "Byte" to "Bytes",
                "Space" to "Bytes"
        )
        private val uiPackageParts = listOf(
                "animation",
                "view",
                "graphics",
                "transition",
                "widget",
                "webkit"
        )

        private val constantNamePattern = Regex("[A-Z0-9_]+")
        private val internalNamePattern = Regex("[ms][A-Z0-9].*")
        private val fieldNamePattern = Regex("[a-z].*")
        private val onCallbackNamePattern = Regex("on[A-Z][a-z0-9][a-zA-Z0-9]*")
        private val configFieldPattern = Regex("config_[a-z][a-zA-Z0-9]*")
        private val layoutFieldPattern = Regex("layout_[a-z][a-zA-Z0-9]*")
        private val stateFieldPattern = Regex("state_[a-z_]+")
        private val resourceFileFieldPattern = Regex("[a-z0-9_]+")
        private val resourceValueFieldPattern = Regex("[a-z][a-zA-Z0-9]*")
        private val styleFieldPattern = Regex("[A-Z][A-Za-z0-9]+(_[A-Z][A-Za-z0-9]+?)*")

        private val acronymPattern2 = Regex("([A-Z]){2,}")
        private val acronymPattern3 = Regex("([A-Z]){3,}")

        private val serviceDumpMethodParameterTypes =
                listOf("java.io.FileDescriptor", "java.io.PrintWriter", "java.lang.String[]")

        private fun isServiceDumpMethod(item: Item) = when (item) {
            is MethodItem -> isServiceDumpMethod(item)
            is ParameterItem -> isServiceDumpMethod(item.containingMethod())
            else -> false
        }

        private fun isServiceDumpMethod(item: MethodItem) = item.name() == "dump" &&
                item.containingClass().extends("android.app.Service") &&
                item.parameters().map { it.type().toTypeString() } == serviceDumpMethodParameterTypes

        private fun hasAcronyms(name: String): Boolean {
            // Require 3 capitals, or 2 if it's at the end of a word.
            val result = acronymPattern2.find(name) ?: return false
            return result.range.first == name.length - 2 || acronymPattern3.find(name) != null
        }

        private fun getFirstAcronym(name: String): String? {
            // Require 3 capitals, or 2 if it's at the end of a word.
            val result = acronymPattern2.find(name) ?: return null
            if (result.range.first == name.length - 2) {
                return name.substring(name.length - 2)
            }
            val result2 = acronymPattern3.find(name)
            return if (result2 != null) {
                name.substring(result2.range.first, result2.range.last + 1)
            } else {
                null
            }
        }

        /** for something like "HTMLWriter", returns "HtmlWriter" */
        private fun decapitalizeAcronyms(name: String): String {
            var s = name

            if (s.none { it.isLowerCase() }) {
                // The entire thing is capitalized. If so, just perform
                // normal capitalization, but try dropping _'s.
                return SdkVersionInfo.underlinesToCamelCase(s.lowercase(Locale.US))
                        .replaceFirstChar {
                            if (it.isLowerCase()) {
                                it.titlecase(Locale.getDefault())
                            } else {
                                it.toString()
                            }
                        }
            }

            while (true) {
                val acronym = getFirstAcronym(s) ?: return s
                val index = s.indexOf(acronym)
                if (index == -1) {
                    return s
                }
                // The last character, if not the end of the string, is probably the beginning of the
                // next word so capitalize it
                s = if (index == s.length - acronym.length) {
                    // acronym at the end of the word word
                    val decapitalized = acronym[0] + acronym.substring(1).lowercase(Locale.US)
                    s.replace(acronym, decapitalized)
                } else {
                    val replacement = acronym[0] + acronym.substring(
                            1,
                            acronym.length - 1
                    ).lowercase(Locale.US) + acronym[acronym.length - 1]
                    s.replace(acronym, replacement)
                }
            }
        }

        fun check(codebase: Codebase, oldCodebase: Codebase?, reporter: Reporter) {
            ApiLint(codebase, oldCodebase, reporter).check()
        }
    }
}

internal const val DefaultLintErrorMessage = """
************************************************************
Your API changes are triggering API Lint warnings or errors.
To make these errors go away, fix the code according to the
error and/or warning messages above.

If it's not possible to do so, there are two workarounds:

1. Suppress the issues with @Suppress("<id>") / @SuppressWarnings("<id>")
2. Update the baseline passed into metalava
************************************************************
"""
