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

const val JAVA_LANG_PREFIX = "java.lang."
const val JAVA_LANG_OBJECT = "java.lang.Object"
const val JAVA_LANG_STRING = "java.lang.String"
const val JAVA_LANG_ENUM = "java.lang.Enum"
const val JAVA_LANG_THROWABLE = "java.lang.Throwable"
const val JAVA_LANG_ANNOTATION = "java.lang.annotation.Annotation"
const val JAVA_LANG_DEPRECATED = "java.lang.Deprecated"
const val ORG_JETBRAINS_ANNOTATIONS_PREFIX = "org.jetbrains.annotations."
const val ORG_INTELLIJ_LANG_ANNOTATIONS_PREFIX = "org.intellij.lang.annotations."
const val ANDROID_ANNOTATION_PREFIX = "android.annotation."
const val ANDROIDX_ANNOTATION_PREFIX = "androidx.annotation."
const val ANDROIDX_NONNULL = "androidx.annotation.NonNull"
const val ANDROIDX_NULLABLE = "androidx.annotation.Nullable"
const val ANDROID_SYSTEM_API = "android.annotation.SystemApi"
const val ANDROID_REQUIRES_PERMISSION = "android.annotation.RequiresPermission"
const val ANDROID_DEPRECATED_FOR_SDK = "android.annotation.DeprecatedForSdk"
const val RECENTLY_NULLABLE = "androidx.annotation.RecentlyNullable"
const val RECENTLY_NONNULL = "androidx.annotation.RecentlyNonNull"
const val ANDROID_NULLABLE = "android.annotation.Nullable"
const val ANDROID_NONNULL = "android.annotation.NonNull"
const val ANDROID_SDK_CONSTANT = "android.annotation.SdkConstant"
const val ANDROIDX_VISIBLE_FOR_TESTING = "androidx.annotation.VisibleForTesting"
const val ATTR_OTHERWISE = "otherwise"
const val ATTR_ALLOW_IN = "allowIn"
const val CARRIER_PRIVILEGES_MARKER = "carrier privileges"

const val ANDROID_SUPPRESS_LINT = "android.annotation.SuppressLint"
const val JAVA_LANG_SUPPRESS_WARNINGS = "java.lang.SuppressWarnings"
const val KOTLIN_SUPPRESS = "kotlin.Suppress"

const val ENV_VAR_METALAVA_TESTS_RUNNING = "METALAVA_TESTS_RUNNING"
const val ENV_VAR_METALAVA_DUMP_ARGV = "METALAVA_DUMP_ARGV"
const val ENV_VAR_METALAVA_PREPEND_ARGS = "METALAVA_PREPEND_ARGS"
const val ENV_VAR_METALAVA_APPEND_ARGS = "METALAVA_APPEND_ARGS"

const val JAVA_RETENTION = "java.lang.annotation.Retention"
const val KT_RETENTION = "kotlin.annotation.Retention"

fun isRetention(qualifiedName: String?): Boolean =
        JAVA_RETENTION == qualifiedName || KT_RETENTION == qualifiedName
