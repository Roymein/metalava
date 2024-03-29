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

import com.android.tools.lint.detector.api.ClassContext
import com.android.tools.metalava.JAVA_LANG_OBJECT
import com.android.tools.metalava.JAVA_LANG_PREFIX
import com.android.tools.metalava.JAVA_LANG_STRING
import java.util.function.Predicate

/**
 * Whether metalava supports type use annotations.
 * Note that you can't just turn this flag back on; you have to
 * also add TYPE_USE back to the handful of nullness
 * annotations in stub-annotations/src/main/java/.
 */
const val SUPPORT_TYPE_USE_ANNOTATIONS = false

/** Represents a {@link https://docs.oracle.com/javase/8/docs/api/java/lang/reflect/Type.html Type} */
interface TypeItem {
    /**
     * Generates a string for this type.
     *
     * For a type like this: @Nullable java.util.List<@NonNull java.lang.String>,
     * [outerAnnotations] controls whether the top level annotation like @Nullable
     * is included, [innerAnnotations] controls whether annotations like @NonNull
     * are included, and [erased] controls whether we return the string for
     * the raw type, e.g. just "java.util.List". The [kotlinStyleNulls] parameter
     * controls whether it should return "@Nullable List<String>" as "List<String!>?".
     * Finally, [filter] specifies a filter to apply to the type annotations, if
     * any.
     *
     * (The combination [outerAnnotations] = true and [innerAnnotations] = false
     * is not allowed.)
     */
    fun toTypeString(
        outerAnnotations: Boolean = false,
        innerAnnotations: Boolean = outerAnnotations,
        erased: Boolean = false,
        kotlinStyleNulls: Boolean = false,
        context: Item? = null,
        filter: Predicate<Item>? = null
    ): String

    /** Alias for [toTypeString] with erased=true */
    fun toErasedTypeString(context: Item? = null): String

    /**
     * Returns the internal name of the type, as seen in bytecode. The optional [context]
     * provides the method or class where this type appears, and can be used for example
     * to resolve the bounds for a type variable used in a method that was specified on the class.
     */
    fun internalName(context: Item? = null): String {
        // Default implementation; PSI subclass is more accurate
        return toSlashFormat(toErasedTypeString(context))
    }

    /** Array dimensions of this type; for example, for String it's 0 and for String[][] it's 2. */
    fun arrayDimensions(): Int

    fun asClass(): ClassItem?

    fun toSimpleType(): String {
        return stripJavaLangPrefix(toTypeString())
    }

    /**
     * Helper methods to compare types, especially types from signature files with types
     * from parsing, which may have slightly different formats, e.g. varargs ("...") versus
     * arrays ("[]"), java.lang. prefixes removed in wildcard signatures, etc.
     */
    fun toCanonicalType(context: Item? = null): String {
        var s = toTypeString(context = context)
        while (s.contains(JAVA_LANG_PREFIX)) {
            s = s.replace(JAVA_LANG_PREFIX, "")
        }
        if (s.contains("...")) {
            s = s.replace("...", "[]")
        }

        return s
    }

    /**
     * Returns the element type if the type is an array or contains a vararg.
     * If the element is not an array or does not contain a vararg,
     * returns the original type string.
     */
    fun toElementType(): String {
        return toErasedTypeString().replace("...", "").replace("[]", "")
    }

    val primitive: Boolean

    fun typeArgumentClasses(): List<ClassItem>

    fun convertType(from: ClassItem, to: ClassItem): TypeItem {
        val map = from.mapTypeVariables(to)
        if (map.isNotEmpty()) {
            return convertType(map)
        }

        return this
    }

    fun convertType(replacementMap: Map<String, String>?, owner: Item? = null): TypeItem

    fun convertTypeString(replacementMap: Map<String, String>?): String {
        val typeString =
            toTypeString(outerAnnotations = true, innerAnnotations = true, kotlinStyleNulls = false)
        return convertTypeString(typeString, replacementMap)
    }

    fun isJavaLangObject(): Boolean {
        return toTypeString() == JAVA_LANG_OBJECT
    }

    fun isString(): Boolean {
        return toTypeString() == JAVA_LANG_STRING
    }

    fun defaultValue(): Any? {
        return when (toTypeString()) {
            "boolean" -> false
            "char", "int", "float", "double" -> 0
            "byte" -> 0.toByte()
            "short" -> 0.toShort()
            "long" -> 0L
            else -> null
        }
    }

    fun defaultValueString(): String = defaultValue()?.toString() ?: "null"

    fun hasTypeArguments(): Boolean = toTypeString().contains("<")

    /**
     * If the item has type arguments, return a list of type arguments.
     * If simplified is true, returns the simplified forms of the type arguments.
     * e.g. when type arguments are <K, V extends some.arbitrary.Class>, [K, V] will be returned.
     * If the item does not have any type arguments, return an empty list.
     */
    fun typeArguments(simplified: Boolean = false): List<String> {
        if (!hasTypeArguments()) {
            return emptyList()
        }
        val typeString = toTypeString()
        val bracketRemovedTypeString = toTypeString().indexOf('<')
            .let { typeString.substring(it + 1, typeString.length - 1) }
        val typeArguments = mutableListOf<String>()
        var builder = StringBuilder()
        var balance = 0
        var idx = 0
        while (idx < bracketRemovedTypeString.length) {
            when (val s = bracketRemovedTypeString[idx]) {
                ',' -> {
                    if (balance == 0) {
                        typeArguments.add(builder.toString())
                        builder = StringBuilder()
                    } else {
                        builder.append(s)
                    }
                }
                '<' -> {
                    balance += 1
                    builder.append(s)
                }
                '>' -> {
                    balance -= 1
                    builder.append(s)
                }
                else -> builder.append(s)
            }
            idx += 1
        }
        typeArguments.add(builder.toString())

        if (simplified) {
            return typeArguments.map { it.substringBefore(" extends ").trim() }
        }
        return typeArguments.map { it.trim() }
    }

    /**
     * If this type is a type parameter, then return the corresponding [TypeParameterItem].
     * The optional [context] provides the method or class where this type parameter
     * appears, and can be used for example to resolve the bounds for a type variable
     * used in a method that was specified on the class.
     */
    fun asTypeParameter(context: MemberItem? = null): TypeParameterItem?

    /**
     * Whether this type is a type parameter.
     */
    fun isTypeParameter(context: MemberItem? = null): Boolean = asTypeParameter(context) != null

    /**
     * Mark nullness annotations in the type as recent.
     * TODO: This isn't very clean; we should model individual annotations.
     */
    fun markRecent()

    /** Returns true if this type represents an array of one or more dimensions */
    fun isArray(): Boolean = arrayDimensions() > 0

    /**
     * Ensure that we don't include any annotations in the type strings for this type.
     */
    fun scrubAnnotations()

    companion object {
        /** Shortens types, if configured */
        fun shortenTypes(type: String): String {
            var cleaned = type
            if (cleaned.contains("@androidx.annotation.")) {
                cleaned = cleaned.replace("@androidx.annotation.", "@")
            }
            return stripJavaLangPrefix(cleaned)
        }

        /**
         * Removes java.lang. prefixes from types, unless it's in a subpackage such
         * as java.lang.reflect. For simplicity we may also leave inner classes
         * in the java.lang package untouched.
         *
         * NOTE: We only remove this from the front of the type; e.g. we'll replace
         * java.lang.Class<java.lang.String> with Class<java.lang.String>.
         * This is because the signature parsing of types is not 100% accurate
         * and we don't want to run into trouble with more complicated generic
         * type signatures where we end up not mapping the simplified types back
         * to the real fully qualified type names.
         */
        fun stripJavaLangPrefix(type: String): String {
            if (type.startsWith(JAVA_LANG_PREFIX)) {
                // Replacing java.lang is harder, since we don't want to operate in sub packages,
                // e.g. java.lang.String -> String, but java.lang.reflect.Method -> unchanged
                val start = JAVA_LANG_PREFIX.length
                val end = type.length
                for (index in start until end) {
                    if (type[index] == '<') {
                        return type.substring(start)
                    } else if (type[index] == '.') {
                        return type
                    }
                }

                return type.substring(start)
            }

            return type
        }

        fun formatType(type: String?): String {
            return if (type == null) {
                ""
            } else cleanupGenerics(type)
        }

        fun cleanupGenerics(signature: String): String {
            // <T extends java.lang.Object> is the same as <T>
            //  but NOT for <T extends Object & java.lang.Comparable> -- you can't
            //  shorten this to <T & java.lang.Comparable
            // return type.replace(" extends java.lang.Object", "")
            return signature.replace(" extends java.lang.Object>", ">")
        }

        val comparator: Comparator<TypeItem> = Comparator { type1, type2 ->
            val cls1 = type1.asClass()
            val cls2 = type2.asClass()
            if (cls1 != null && cls2 != null) {
                ClassItem.fullNameComparator.compare(cls1, cls2)
            } else {
                type1.toTypeString().compareTo(type2.toTypeString())
            }
        }

        fun convertTypeString(typeString: String, replacementMap: Map<String, String>?): String {
            var string = typeString
            if (replacementMap != null && replacementMap.isNotEmpty()) {
                // This is a moved method (typically an implementation of an interface
                // method provided in a hidden superclass), with generics signatures.
                // We need to rewrite the generics variables in case they differ
                // between the classes.
                if (replacementMap.isNotEmpty()) {
                    replacementMap.forEach { (from, to) ->
                        // We can't just replace one string at a time:
                        // what if I have a map of {"A"->"B", "B"->"C"} and I tried to convert A,B,C?
                        // If I do the replacements one letter at a time I end up with C,C,C; if I do the substitutions
                        // simultaneously I get B,C,C. Therefore, we insert "___" as a magical prefix to prevent
                        // scenarios like this, and then we'll drop them afterwards.
                        string =
                            string.replace(Regex(pattern = """\b$from\b"""), replacement = "___$to")
                    }
                }
                string = string.replace("___", "")
                return string
            } else {
                return string
            }
        }

        /**
         * Convert a type string containing to its lambda representation or return the original.
         *
         * E.g.: `"kotlin.jvm.functions.Function1<Integer, String>"` to `"(Integer) -> String"`.
         */
        fun toLambdaFormat(typeName: String): String {
            // Bail if this isn't a Kotlin function type
            if (!typeName.startsWith(KOTLIN_FUNCTION_PREFIX)) {
                return typeName
            }

            // Find the first character after the first opening angle bracket. This will either be
            // the first character of the paramTypes of the lambda if it has parameters.
            val paramTypesStart =
                typeName.indexOf('<', startIndex = KOTLIN_FUNCTION_PREFIX.length) + 1

            // The last type param is always the return type. We find and set these boundaries with
            // the push down loop below.
            var paramTypesEnd = -1
            var returnTypeStart = -1

            // Get the exclusive end of the return type parameter by finding the last closing
            // angle bracket.
            val returnTypeEnd = typeName.lastIndexOf('>')

            // Bail if an an unexpected format broke the indexOf's above.
            if (paramTypesStart <= 0 || paramTypesStart >= returnTypeEnd) {
                return typeName
            }

            // This loop looks for the last comma that is not inside the type parameters of a type
            // parameter. It's a simple push down state machine that stores its depth as a counter
            // instead of a stack. It runs backwards from the last character of the type parameters
            // just before the last closing angle bracket to the beginning just before the first
            // opening angle bracket.
            var depth = 0
            for (i in returnTypeEnd - 1 downTo paramTypesStart) {
                val c = typeName[i]

                // Increase or decrease stack depth on angle brackets
                when (c) {
                    '>' -> depth++
                    '<' -> depth--
                }

                when {
                    depth == 0 -> when { // At the top level
                        c == ',' -> {
                            // When top level comma is found, mark it as the exclusive end of the
                            // parameter types and end the loop
                            paramTypesEnd = i
                            break
                        }
                        !c.isWhitespace() -> {
                            // Keep moving the start of the return type back until whitespace
                            returnTypeStart = i
                        }
                    }
                    depth < 0 -> return typeName // Bail, unbalanced nesting
                }
            }

            // Bail if some sort of unbalanced nesting occurred or the indices around the comma
            // appear grossly incorrect.
            if (depth > 0 || returnTypeStart < 0 || returnTypeStart <= paramTypesEnd) {
                return typeName
            }

            return buildString(typeName.length) {
                append("(")

                // Slice param types, if any, and append them between the parenthesis
                if (paramTypesEnd > 0) {
                    append(typeName, paramTypesStart, paramTypesEnd)
                }

                append(") -> ")

                // Slice out the return type param and append it after the arrow
                append(typeName, returnTypeStart, returnTypeEnd)
            }
        }

        /** Prefix of Kotlin JVM function types, used for lambdas. */
        private const val KOTLIN_FUNCTION_PREFIX = "kotlin.jvm.functions.Function"

        // Copied from doclava1
        fun toSlashFormat(typeName: String): String {
            var name = typeName
            var dimension = ""
            while (name.endsWith("[]")) {
                dimension += "["
                name = name.substring(0, name.length - 2)
            }

            val base: String
            base = when (name) {
                "void" -> "V"
                "byte" -> "B"
                "boolean" -> "Z"
                "char" -> "C"
                "short" -> "S"
                "int" -> "I"
                "long" -> "J"
                "float" -> "F"
                "double" -> "D"
                else -> "L" + ClassContext.getInternalName(name) + ";"
            }

            return dimension + base
        }

        /** Compares two strings, ignoring space diffs (spaces, not whitespace in general) */
        fun equalsWithoutSpace(s1: String, s2: String): Boolean {
            if (s1 == s2) {
                return true
            }
            val sp1 = s1.indexOf(' ') // first space
            val sp2 = s2.indexOf(' ')
            if (sp1 == -1 && sp2 == -1) {
                // no spaces in strings and aren't equal
                return false
            }

            val l1 = s1.length
            val l2 = s2.length
            var i1 = 0
            var i2 = 0

            while (i1 < l1 && i2 < l2) {
                var c1 = s1[i1++]
                var c2 = s2[i2++]

                while (c1 == ' ' && i1 < l1) {
                    c1 = s1[i1++]
                }
                while (c2 == ' ' && i2 < l2) {
                    c2 = s2[i2++]
                }
                if (c1 != c2) {
                    return false
                }
            }
            // Skip trailing spaces
            while (i1 < l1 && s1[i1] == ' ') {
                i1++
            }
            while (i2 < l2 && s2[i2] == ' ') {
                i2++
            }
            return i1 == l1 && i2 == l2
        }
    }
}
