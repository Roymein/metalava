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

@file:Suppress("ALL")

package com.android.tools.metalava.stub

import com.android.tools.lint.checks.infrastructure.LintDetectorTest.source
import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.ANDROIDX_NONNULL
import com.android.tools.metalava.ARG_CHECK_API
import com.android.tools.metalava.ARG_EXCLUDE_ALL_ANNOTATIONS
import com.android.tools.metalava.ARG_EXCLUDE_ANNOTATION
import com.android.tools.metalava.ARG_EXCLUDE_DOCUMENTATION_FROM_STUBS
import com.android.tools.metalava.ARG_HIDE_PACKAGE
import com.android.tools.metalava.ARG_KOTLIN_STUBS
import com.android.tools.metalava.ARG_PASS_THROUGH_ANNOTATION
import com.android.tools.metalava.ARG_UPDATE_API
import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.FileFormat
import com.android.tools.metalava.androidxNullableSource
import com.android.tools.metalava.deprecatedForSdkSource
import com.android.tools.metalava.extractRoots
import com.android.tools.metalava.gatherSources
import com.android.tools.metalava.java
import com.android.tools.metalava.kotlin
import com.android.tools.metalava.libcoreNonNullSource
import com.android.tools.metalava.model.SUPPORT_TYPE_USE_ANNOTATIONS
import com.android.tools.metalava.requiresApiSource
import com.android.tools.metalava.restrictToSource
import com.android.tools.metalava.supportParameterName
import com.android.tools.metalava.systemApiSource
import com.android.tools.metalava.testApiSource
import org.intellij.lang.annotations.Language
import org.junit.Test
import java.io.File
import java.io.FileNotFoundException
import kotlin.test.assertEquals

@SuppressWarnings("ALL")
class StubsTest : DriverTest() {
    // TODO: test fields that need initialization
    // TODO: test @DocOnly handling

    private fun checkStubs(
        // source is a wrapper for stubFiles. When passing multiple stub Java files to test,
        // use stubFiles.
        @Language("JAVA") source: String = "",
        stubFiles: Array<TestFile> = emptyArray(),
        warnings: String? = "",
        api: String? = null,
        extraArguments: Array<String> = emptyArray(),
        docStubs: Boolean = false,
        showAnnotations: Array<String> = emptyArray(),
        skipEmitPackages: List<String> = listOf("java.lang", "java.util", "java.io"),
        format: FileFormat = FileFormat.latest,
        sourceFiles: Array<TestFile> = emptyArray(),
        signatureSources: Array<String> = emptyArray(),
        checkTextStubEquivalence: Boolean = false
    ) {
        val stubFilesArr = if (source.isNotEmpty()) arrayOf(java(source)) else stubFiles
        check(
            sourceFiles = sourceFiles,
            signatureSources = signatureSources,
            showAnnotations = showAnnotations,
            stubFiles = stubFilesArr,
            expectedIssues = warnings,
            checkCompilation = true,
            api = api,
            extraArguments = extraArguments,
            docStubs = docStubs,
            skipEmitPackages = skipEmitPackages,
            format = format
        )
        if (checkTextStubEquivalence) {
            if (stubFilesArr.isEmpty()) {
                addError("Stub files may not be empty when checkTextStubEquivalence is set to true.")
                return
            }
            if (docStubs) {
                addError("From-text stub generation is not supported for documentation stub.")
                return
            }
            if (stubFilesArr.any { it !is TestFile.JavaTestFile }) {
                addError("From-text stub generation is only supported for Java stubs.")
                return
            }
            check(
                signatureSources = arrayOf(readFile(getApiFile())),
                showAnnotations = showAnnotations,
                stubFiles = stubFilesArr,
                expectedIssues = warnings,
                checkCompilation = true,
                extraArguments = arrayOf(*extraArguments, ARG_EXCLUDE_ANNOTATION, ANDROIDX_NONNULL),
                skipEmitPackages = skipEmitPackages,
                format = format
            )
        }
    }

    @Test
    fun `Generate stubs for basic class`() {
        checkStubs(
            sourceFiles = arrayOf(
                java(
                    """
                    /*
                     * This is the copyright header.
                     */

                    package test.pkg;
                    /** This is the documentation for the class */
                    @SuppressWarnings("ALL")
                    public class Foo {
                        private int hidden;

                        /** My field doc */
                        protected static final String field = "a\nb\n\"test\"";

                        /**
                         * Method documentation.
                         * Maybe it spans
                         * multiple lines.
                         */
                        protected static void onCreate(String parameter1) {
                            // This is not in the stub
                            System.out.println(parameter1);
                        }

                        static {
                           System.out.println("Not included in stub");
                        }
                    }
                    """
                )
            ),
            source = """
                /*
                 * This is the copyright header.
                 */
                package test.pkg;
                /** This is the documentation for the class */
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public class Foo {
                public Foo() { throw new RuntimeException("Stub!"); }
                /**
                 * Method documentation.
                 * Maybe it spans
                 * multiple lines.
                 */
                protected static void onCreate(java.lang.String parameter1) { throw new RuntimeException("Stub!"); }
                /** My field doc */
                protected static final java.lang.String field = "a\nb\n\"test\"";
                }
                """
        )
    }

    @Test
    fun `Generate stubs for generics`() {
        // Basic interface with generics; makes sure <T extends Object> is written as just <T>
        // Also include some more complex generics expressions to make sure they're serialized
        // correctly (in particular, using fully qualified names instead of what appears in
        // the source code.)
        check(
            checkCompilation = true,
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;
                    @SuppressWarnings("ALL")
                    public interface MyInterface2<T extends Number>
                            extends MyBaseInterface {
                        class TtsSpan<C extends MyInterface<?>> { }
                        abstract class Range<T extends Comparable<? super T>> { }
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    @SuppressWarnings("ALL")
                    public interface MyInterface<T extends Object>
                            extends MyBaseInterface {
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    public interface MyBaseInterface {
                    }
                    """
                )
            ),
            expectedIssues = "",
            stubFiles = arrayOf(
                java(
                    """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public interface MyInterface2<T extends java.lang.Number> extends test.pkg.MyBaseInterface {
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public abstract static class Range<T extends java.lang.Comparable<? super T>> {
                    public Range() { throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public static class TtsSpan<C extends test.pkg.MyInterface<?>> {
                    public TtsSpan() { throw new RuntimeException("Stub!"); }
                    }
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public interface MyInterface<T> extends test.pkg.MyBaseInterface {
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public interface MyBaseInterface {
                    }
                    """
                )
            )
        )
    }

    @Test
    fun `Generate stubs for class that should not get default constructor (has other constructors)`() {
        // Class without explicit constructors (shouldn't insert default constructor)
        checkStubs(
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;
                    public class Foo {
                        public Foo(int i) {

                        }
                        public Foo(int i, int j) {
                        }
                    }
                    """
                )
            ),
            source = """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public class Foo {
                public Foo(int i) { throw new RuntimeException("Stub!"); }
                public Foo(int i, int j) { throw new RuntimeException("Stub!"); }
                }
                """
        )
    }

    @Test
    fun `Generate stubs for class that already has a private constructor`() {
        // Class without private constructor; no default constructor should be inserted
        checkStubs(
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;
                    public class Foo {
                        private Foo() {
                        }
                    }
                    """
                )
            ),
            source = """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public class Foo {
                Foo() { throw new RuntimeException("Stub!"); }
                }
                """
        )
    }

    @Test
    fun `Generate stubs for interface class`() {
        // Interface: makes sure the right modifiers etc are shown (and that "package private" methods
        // in the interface are taken to be public etc)
        checkStubs(
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;
                    public interface Foo {
                        void foo();
                    }
                    """
                )
            ),
            source = """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public interface Foo {
                public void foo();
                }
                """,
            checkTextStubEquivalence = true
        )
    }

    @Test
    fun `Generate stubs for enum`() {
        // Interface: makes sure the right modifiers etc are shown (and that "package private" methods
        // in the interface are taken to be public etc)
        checkStubs(
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;
                    @SuppressWarnings("ALL")
                    public enum Foo {
                        A, /** @deprecated */ @Deprecated B;
                    }
                    """
                )
            ),
            source = """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public enum Foo {
                A,
                /** @deprecated */
                @Deprecated
                B;
                }
                """
        )
    }

    @Test
    fun `Generate stubs for annotation type`() {
        // Interface: makes sure the right modifiers etc are shown (and that "package private" methods
        // in the interface are taken to be public etc)
        checkStubs(
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;
                    import static java.lang.annotation.ElementType.*;
                    import java.lang.annotation.*;
                    @Target({TYPE, FIELD, METHOD, PARAMETER, CONSTRUCTOR, LOCAL_VARIABLE})
                    @Retention(RetentionPolicy.CLASS)
                    public @interface Foo {
                        String value();
                    }
                    """
                )
            ),
            source = """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS)
                @java.lang.annotation.Target({java.lang.annotation.ElementType.TYPE, java.lang.annotation.ElementType.FIELD, java.lang.annotation.ElementType.METHOD, java.lang.annotation.ElementType.PARAMETER, java.lang.annotation.ElementType.CONSTRUCTOR, java.lang.annotation.ElementType.LOCAL_VARIABLE})
                public @interface Foo {
                public java.lang.String value();
                }
                """
        )
    }

    @Test
    fun `Generate stubs for class with superclass`() {
        // Make sure superclass statement is correct; unlike signature files, inherited method from parent
        // that has same signature should be included in the child
        checkStubs(
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;
                    public class Foo extends Super {
                        @Override public void base() { }
                        public void child() { }
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    public class Super {
                        public void base() { }
                    }
                    """
                )
            ),
            source =
            """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public class Foo extends test.pkg.Super {
                public Foo() { throw new RuntimeException("Stub!"); }
                public void base() { throw new RuntimeException("Stub!"); }
                public void child() { throw new RuntimeException("Stub!"); }
                }
                """
        )
    }

    @Test
    fun `Generate stubs for fields with initial values`() {
        checkStubs(
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;
                    @SuppressWarnings("ALL")
                    public class Foo {
                        private int hidden = 1;
                        int hidden2 = 2;
                        /** @hide */
                        int hidden3 = 3;

                        protected int field00; // No value
                        public static final boolean field01 = true;
                        public static final int field02 = 42;
                        public static final long field03 = 42L;
                        public static final short field04 = 5;
                        public static final byte field05 = 5;
                        public static final char field06 = 'c';
                        public static final float field07 = 98.5f;
                        public static final double field08 = 98.5;
                        public static final String field09 = "String with \"escapes\" and \u00a9...";
                        public static final double field10 = Double.NaN;
                        public static final double field11 = Double.POSITIVE_INFINITY;

                        public static final boolean field12;
                        public static final byte field13;
                        public static final char field14;
                        public static final short field15;
                        public static final int field16;
                        public static final long field17;
                        public static final float field18;
                        public static final double field19;

                        public static final String GOOD_IRI_CHAR = "a-zA-Z0-9\u00a0-\ud7ff\uf900-\ufdcf\ufdf0-\uffef";
                        public static final char HEX_INPUT = 61184;
                    }
                    """
                )
            ),
            source = """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public class Foo {
                public Foo() { throw new RuntimeException("Stub!"); }
                public static final java.lang.String GOOD_IRI_CHAR = "a-zA-Z0-9\u00a0-\ud7ff\uf900-\ufdcf\ufdf0-\uffef";
                public static final char HEX_INPUT = 61184; // 0xef00 '\uef00'
                protected int field00;
                public static final boolean field01 = true;
                public static final int field02 = 42; // 0x2a
                public static final long field03 = 42L; // 0x2aL
                public static final short field04 = 5; // 0x5
                public static final byte field05 = 5; // 0x5
                public static final char field06 = 99; // 0x0063 'c'
                public static final float field07 = 98.5f;
                public static final double field08 = 98.5;
                public static final java.lang.String field09 = "String with \"escapes\" and \u00a9...";
                public static final double field10 = (0.0/0.0);
                public static final double field11 = (1.0/0.0);
                public static final boolean field12;
                static { field12 = false; }
                public static final byte field13;
                static { field13 = 0; }
                public static final char field14;
                static { field14 = 0; }
                public static final short field15;
                static { field15 = 0; }
                public static final int field16;
                static { field16 = 0; }
                public static final long field17;
                static { field17 = 0; }
                public static final float field18;
                static { field18 = 0; }
                public static final double field19;
                static { field19 = 0; }
                }
                """,
            checkTextStubEquivalence = true
        )
    }

    @Test
    fun `Generate stubs for various modifier scenarios`() {
        // Include as many modifiers as possible to see which ones are included
        // in the signature files, and the expected sorting order.
        // Note that the signature files treat "deprecated" as a fake modifier.
        // Note also how the "protected" modifier on the interface method gets
        // promoted to public.
        checkStubs(
            warnings = null,
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;

                    @SuppressWarnings("ALL")
                    public abstract class Foo {
                        /** @deprecated */ @Deprecated private static final long field1 = 5;
                        /** @deprecated */ @Deprecated private static volatile long field2 = 5;
                        /** @deprecated */ @Deprecated public static strictfp final synchronized void method1() { }
                        /** @deprecated */ @Deprecated public static final synchronized native void method2();
                        /** @deprecated */ @Deprecated protected static final class Inner1 { }
                        /** @deprecated */ @Deprecated protected static abstract  class Inner2 { }
                        /** @deprecated */ @Deprecated protected interface Inner3 {
                            protected default void method3() { }
                            static void method4() { }
                        }
                    }
                    """
                )
            ),

            source = """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public abstract class Foo {
                public Foo() { throw new RuntimeException("Stub!"); }
                /** @deprecated */
                @Deprecated
                public static final synchronized void method1() { throw new RuntimeException("Stub!"); }
                /** @deprecated */
                @Deprecated
                public static final synchronized native void method2();
                /** @deprecated */
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                @Deprecated
                protected static final class Inner1 {
                @Deprecated
                protected Inner1() { throw new RuntimeException("Stub!"); }
                }
                /** @deprecated */
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                @Deprecated
                protected abstract static class Inner2 {
                @Deprecated
                protected Inner2() { throw new RuntimeException("Stub!"); }
                }
                /** @deprecated */
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                @Deprecated
                protected static interface Inner3 {
                @Deprecated
                public default void method3() { throw new RuntimeException("Stub!"); }
                @Deprecated
                public static void method4() { throw new RuntimeException("Stub!"); }
                }
                }
                """
        )
    }

    @Test
    fun `Generate stubs for class with abstract enum methods`() {
        // As per https://bugs.openjdk.java.net/browse/JDK-6287639
        // abstract methods in enums should not be listed as abstract,
        // but doclava1 does, so replicate this.
        // Also checks that we handle both enum fields and regular fields
        // and that they are listed separately.

        checkStubs(
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;

                    public enum FooBar {
                        /** My 1st documentation */
                        ABC {
                            @Override
                            protected void foo() { }
                        },
                        /** My 2nd documentation */
                        DEF {
                            @Override
                            protected void foo() { }
                        };

                        protected abstract void foo();
                        public static int field1 = 1;
                        public int field2 = 2;
                    }
                    """
                )
            ),
            source = """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public enum FooBar {
                /** My 1st documentation */
                ABC,
                /** My 2nd documentation */
                DEF;
                protected void foo() { throw new RuntimeException("Stub!"); }
                public static int field1 = 1; // 0x1
                public int field2 = 2; // 0x2
                }
                """
        )
    }

    @Test
    fun `Skip hidden enum constants in stubs`() {
        checkStubs(
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;
                    public enum Alignment {
                        ALIGN_NORMAL,
                        ALIGN_OPPOSITE,
                        ALIGN_CENTER,
                        /** @hide */
                        ALIGN_LEFT,
                        /** @hide */
                        ALIGN_RIGHT
                    }
                    """
                )
            ),
            api = """
                package test.pkg {
                  public enum Alignment {
                    enum_constant public static final test.pkg.Alignment ALIGN_CENTER;
                    enum_constant public static final test.pkg.Alignment ALIGN_NORMAL;
                    enum_constant public static final test.pkg.Alignment ALIGN_OPPOSITE;
                  }
                }
            """,
            source = """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public enum Alignment {
                ALIGN_NORMAL,
                ALIGN_OPPOSITE,
                ALIGN_CENTER;
                }
            """
        )
    }

    @Test
    fun `Check correct throws list for generics`() {
        checkStubs(
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;

                    import java.util.function.Supplier;

                    @SuppressWarnings("RedundantThrows")
                    public final class Test<T> {
                        public <X extends Throwable> T orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
                            return null;
                        }
                    }
                    """
                )
            ),
            source = """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public final class Test<T> {
                public Test() { throw new RuntimeException("Stub!"); }
                public <X extends java.lang.Throwable> T orElseThrow(java.util.function.Supplier<? extends X> exceptionSupplier) throws X { throw new RuntimeException("Stub!"); }
                }
                """
        )
    }

    @Test
    fun `Generate stubs for additional generics scenarios`() {
        // Some additional declarations where PSI default type handling diffs from doclava1
        checkStubs(
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;

                    public abstract class Collections {
                        public static <T extends java.lang.Object & java.lang.Comparable<? super T>> T max(java.util.Collection<? extends T> collection) {
                            return null;
                        }
                        public abstract <T extends java.util.Collection<java.lang.String>> T addAllTo(T t);
                        public final class Range<T extends java.lang.Comparable<? super T>> { }
                    }
                    """
                )
            ),

            source = """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public abstract class Collections {
                public Collections() { throw new RuntimeException("Stub!"); }
                public static <T extends java.lang.Object & java.lang.Comparable<? super T>> T max(java.util.Collection<? extends T> collection) { throw new RuntimeException("Stub!"); }
                public abstract <T extends java.util.Collection<java.lang.String>> T addAllTo(T t);
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public final class Range<T extends java.lang.Comparable<? super T>> {
                public Range() { throw new RuntimeException("Stub!"); }
                }
                }
                """
        )
    }

    @Test
    fun `Generate stubs for even more generics scenarios`() {
        // Some additional declarations where PSI default type handling diffs from doclava1
        checkStubs(
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;

                    import java.util.Set;

                    @SuppressWarnings("ALL")
                    public class MoreAsserts {
                        public static void assertEquals(String arg1, Set<? extends Object> arg2, Set<? extends Object> arg3) { }
                        public static void assertEquals(Set<? extends Object> arg1, Set<? extends Object> arg2) { }
                    }
                    """
                )
            ),

            source = """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public class MoreAsserts {
                public MoreAsserts() { throw new RuntimeException("Stub!"); }
                public static void assertEquals(java.lang.String arg1, java.util.Set<?> arg2, java.util.Set<?> arg3) { throw new RuntimeException("Stub!"); }
                public static void assertEquals(java.util.Set<?> arg1, java.util.Set<?> arg2) { throw new RuntimeException("Stub!"); }
                }
                """,
            checkTextStubEquivalence = true
        )
    }

    @Test
    fun `Generate stubs enum instance methods`() {
        checkStubs(
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;

                    public enum ChronUnit implements TempUnit {
                        C(1), B(2), A(3);

                        ChronUnit(int y) {
                        }

                        public String valueOf(int x) {
                            return Integer.toString(x + 5);
                        }

                        public String values(String separator) {
                            return null;
                        }

                        @Override
                        public String toString() {
                            return name();
                        }
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;

                    public interface TempUnit {
                        @Override
                        String toString();
                    }
                     """
                )
            ),
            source = """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public enum ChronUnit implements test.pkg.TempUnit {
                C,
                B,
                A;
                public java.lang.String valueOf(int x) { throw new RuntimeException("Stub!"); }
                public java.lang.String values(java.lang.String separator) { throw new RuntimeException("Stub!"); }
                public java.lang.String toString() { throw new RuntimeException("Stub!"); }
                }
            """
        )
    }

    @Test
    fun `Generate stubs with superclass filtering`() {
        checkStubs(
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;
                    public class MyClass extends HiddenParent {
                        public void method4() { }
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    /** @hide */
                    @SuppressWarnings("ALL")
                    public class HiddenParent extends HiddenParent2 {
                        public static final String CONSTANT = "MyConstant";
                        protected int mContext;
                        public void method3() { }
                        // Static: should be included
                        public static void method3b() { }
                        // References hidden type: don't inherit
                        public void method3c(HiddenParent p) { }
                        // References hidden type: don't inherit
                        public void method3d(java.util.List<HiddenParent> p) { }
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    /** @hide */
                    @SuppressWarnings("ALL")
                    public class HiddenParent2 extends PublicParent {
                        public void method2() { }
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    public class PublicParent {
                        public void method1() { }
                    }
                    """
                )
            ),
            // Notice how the intermediate methods (method2, method3) have been removed
            source = """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public class MyClass extends test.pkg.PublicParent {
                public MyClass() { throw new RuntimeException("Stub!"); }
                public void method4() { throw new RuntimeException("Stub!"); }
                public static void method3b() { throw new RuntimeException("Stub!"); }
                public void method2() { throw new RuntimeException("Stub!"); }
                public void method3() { throw new RuntimeException("Stub!"); }
                public static final java.lang.String CONSTANT = "MyConstant";
                }
                """,
            warnings = """
                src/test/pkg/MyClass.java:2: warning: Public class test.pkg.MyClass stripped of unavailable superclass test.pkg.HiddenParent [HiddenSuperclass]
                """
        )
    }

    @Test
    fun `Check inheriting from package private class`() {
        checkStubs(
            // Note that doclava1 includes fields here that it doesn't include in the
            // signature file.
            // checkDoclava1 = true,
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;
                    public class MyClass extends HiddenParent {
                        public void method1() { }
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    class HiddenParent {
                        public static final String CONSTANT = "MyConstant";
                        protected int mContext;
                        public void method2() { }
                    }
                    """
                )
            ),
            warnings = "",
            source = """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class MyClass {
                    public MyClass() { throw new RuntimeException("Stub!"); }
                    public void method1() { throw new RuntimeException("Stub!"); }
                    public void method2() { throw new RuntimeException("Stub!"); }
                    public static final java.lang.String CONSTANT = "MyConstant";
                    }
                """,
            checkTextStubEquivalence = true
        )
    }

    @Test
    fun `Check implementing a package private interface`() {
        // If you implement a package private interface, we just remove it and inline the members into
        // the subclass

        // BUG: Note that we need to implement the parent
        checkStubs(
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;
                    public class MyClass implements HiddenInterface {
                        @Override public void method() { }
                        @Override public void other() { }
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    public interface OtherInterface {
                        void other();
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    interface HiddenInterface extends OtherInterface {
                        void method() { }
                        String CONSTANT = "MyConstant";
                    }
                    """
                )
            ),
            source = """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public class MyClass implements test.pkg.OtherInterface {
                public MyClass() { throw new RuntimeException("Stub!"); }
                public void method() { throw new RuntimeException("Stub!"); }
                public void other() { throw new RuntimeException("Stub!"); }
                public static final java.lang.String CONSTANT = "MyConstant";
                }
                """,
            checkTextStubEquivalence = true
        )
    }

    @Test
    fun `Check throws list`() {
        // Make sure we format a throws list
        checkStubs(
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;
                    import java.io.IOException;

                    @SuppressWarnings("RedundantThrows")
                    public abstract class AbstractCursor {
                        @Override protected void finalize1() throws Throwable { }
                        @Override protected void finalize2() throws IOException, IllegalArgumentException {  }
                    }
                    """
                )
            ),
            source = """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public abstract class AbstractCursor {
                public AbstractCursor() { throw new RuntimeException("Stub!"); }
                protected void finalize1() throws java.lang.Throwable { throw new RuntimeException("Stub!"); }
                protected void finalize2() throws java.io.IOException, java.lang.IllegalArgumentException { throw new RuntimeException("Stub!"); }
                }
                """,
            checkTextStubEquivalence = true
        )
    }

    @Test
    fun `Check generating constants in interface without inline-able initializers`() {
        checkStubs(
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;
                    public interface MyClass {
                        String[] CONSTANT1 = {"MyConstant","MyConstant2"};
                        boolean CONSTANT2 = Boolean.getBoolean(System.getenv("VAR1"));
                        int CONSTANT3 = Integer.parseInt(System.getenv("VAR2"));
                        String CONSTANT4 = null;
                    }
                    """
                )
            ),
            warnings = "",
            source = """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public interface MyClass {
                public static final java.lang.String[] CONSTANT1 = null;
                public static final boolean CONSTANT2 = false;
                public static final int CONSTANT3 = 0; // 0x0
                public static final java.lang.String CONSTANT4 = null;
                }
                """,
            checkTextStubEquivalence = true
        )
    }

    @Test
    fun `Handle non-constant fields in final classes`() {
        checkStubs(
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;

                    @SuppressWarnings("all")
                    public class FinalFieldTest {
                        public interface TemporalField {
                            String getBaseUnit();
                        }
                        public static final class IsoFields {
                            public static final TemporalField DAY_OF_QUARTER = Field.DAY_OF_QUARTER;
                            IsoFields() {
                                throw new AssertionError("Not instantiable");
                            }

                            private static enum Field implements TemporalField {
                                DAY_OF_QUARTER {
                                    @Override
                                    public String getBaseUnit() {
                                        return "days";
                                    }
                               }
                           };
                        }
                    }
                    """
                )
            ),
            warnings = "",
            source = """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class FinalFieldTest {
                    public FinalFieldTest() { throw new RuntimeException("Stub!"); }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public static final class IsoFields {
                    IsoFields() { throw new RuntimeException("Stub!"); }
                    public static final test.pkg.FinalFieldTest.TemporalField DAY_OF_QUARTER;
                    static { DAY_OF_QUARTER = null; }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public static interface TemporalField {
                    public java.lang.String getBaseUnit();
                    }
                    }
                    """
        )
    }

    @Test
    fun `Test final instance fields`() {
        // Instance fields in a class must be initialized
        checkStubs(
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;

                    @SuppressWarnings("all")
                    public class InstanceFieldTest {
                        public static final class WindowLayout {
                            public WindowLayout(int width, int height, int gravity) {
                                this.width = width;
                                this.height = height;
                                this.gravity = gravity;
                            }

                            public final int width;
                            public final int height;
                            public final int gravity;

                        }
                    }
                    """
                )
            ),
            warnings = "",
            source = """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class InstanceFieldTest {
                    public InstanceFieldTest() { throw new RuntimeException("Stub!"); }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public static final class WindowLayout {
                    public WindowLayout(int width, int height, int gravity) { throw new RuntimeException("Stub!"); }
                    public final int gravity;
                    { gravity = 0; }
                    public final int height;
                    { height = 0; }
                    public final int width;
                    { width = 0; }
                    }
                    }
                    """
        )
    }

    @Test
    fun `Check generating constants in class without inline-able initializers`() {
        checkStubs(
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;
                    public class MyClass {
                        public static String[] CONSTANT1 = {"MyConstant","MyConstant2"};
                        public static boolean CONSTANT2 = Boolean.getBoolean(System.getenv("VAR1"));
                        public static int CONSTANT3 = Integer.parseInt(System.getenv("VAR2"));
                        public static String CONSTANT4 = null;
                    }
                    """
                )
            ),
            warnings = "",
            source = """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public class MyClass {
                public MyClass() { throw new RuntimeException("Stub!"); }
                public static java.lang.String[] CONSTANT1;
                public static boolean CONSTANT2;
                public static int CONSTANT3;
                public static java.lang.String CONSTANT4;
                }
                """
        )
    }

    @Test
    fun `Check generating classes with generics`() {
        checkStubs(
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;

                    public class Generics {
                        public <T> Generics(int surfaceSize, Class<T> klass) {
                        }
                    }
                    """
                )
            ),
            warnings = "",
            source = """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Generics {
                    public <T> Generics(int surfaceSize, java.lang.Class<T> klass) { throw new RuntimeException("Stub!"); }
                    }
                """
        )
    }

    @Test
    fun `Check generating type parameters in interface list`() {
        checkStubs(
            format = FileFormat.V2,
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;

                    @SuppressWarnings("NullableProblems")
                    public class GenericsInInterfaces<T> implements Comparable<GenericsInInterfaces> {
                        @Override
                        public int compareTo(GenericsInInterfaces o) {
                            return 0;
                        }

                        void foo(T bar) {
                        }
                    }
                    """
                )
            ),
            api = """
                package test.pkg {
                  public class GenericsInInterfaces<T> implements java.lang.Comparable<test.pkg.GenericsInInterfaces> {
                    ctor public GenericsInInterfaces();
                    method public int compareTo(test.pkg.GenericsInInterfaces);
                  }
                }
                """,
            source = """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public class GenericsInInterfaces<T> implements java.lang.Comparable<test.pkg.GenericsInInterfaces> {
                public GenericsInInterfaces() { throw new RuntimeException("Stub!"); }
                public int compareTo(test.pkg.GenericsInInterfaces o) { throw new RuntimeException("Stub!"); }
                }
                """
        )
    }

    @Test
    fun `Preserve file header comments`() {
        checkStubs(
            sourceFiles = arrayOf(
                java(
                    """
                    /*
                    My header 1
                     */

                    /*
                    My header 2
                     */

                    // My third comment

                    package test.pkg;

                    public class HeaderComments {
                    }
                    """
                )
            ),
            source = """
                    /*
                    My header 1
                     */
                    /*
                    My header 2
                     */
                    // My third comment
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class HeaderComments {
                    public HeaderComments() { throw new RuntimeException("Stub!"); }
                    }
                    """
        )
    }

    @Test
    fun `Basic Kotlin class`() {
        checkStubs(
            sourceFiles = arrayOf(
                kotlin(
                    """
                    /* My file header */
                    // Another comment
                    @file:JvmName("Driver")
                    package test.pkg
                    /** My class doc */
                    class Kotlin(val property1: String = "Default Value", arg2: Int) : Parent() {
                        override fun method() = "Hello World"
                        /** My method doc */
                        fun otherMethod(ok: Boolean, times: Int) {
                        }

                        /** property doc */
                        var property2: String? = null

                        /** @hide */
                        var hiddenProperty: String? = "hidden"

                        private var someField = 42
                        @JvmField
                        var someField2 = 42
                    }

                    open class Parent {
                        open fun method(): String? = null
                        open fun method2(value1: Boolean, value2: Boolean?): String? = null
                        open fun method3(value1: Int?, value2: Int): Int = null
                    }
                    """
                )
            ),
            source = """
                    /* My file header */
                    // Another comment
                    package test.pkg;
                    /** My class doc */
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public final class Kotlin extends test.pkg.Parent {
                    public Kotlin(@android.annotation.NonNull java.lang.String property1, int arg2) { throw new RuntimeException("Stub!"); }
                    @android.annotation.NonNull
                    public java.lang.String method() { throw new RuntimeException("Stub!"); }
                    /** My method doc */
                    public void otherMethod(boolean ok, int times) { throw new RuntimeException("Stub!"); }
                    /** property doc */
                    @android.annotation.Nullable
                    public java.lang.String getProperty2() { throw new RuntimeException("Stub!"); }
                    /** property doc */
                    public void setProperty2(@android.annotation.Nullable java.lang.String value) { throw new RuntimeException("Stub!"); }
                    @android.annotation.NonNull
                    public java.lang.String getProperty1() { throw new RuntimeException("Stub!"); }
                    public int someField2;
                    }
                """
        )
    }

    @Test
    fun `Parameter Names in Java`() {
        // Java code which explicitly specifies parameter names: make sure stub uses
        // parameter name
        checkStubs(
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;
                    import androidx.annotation.ParameterName;

                    public class Foo {
                        public void foo(int javaParameter1, @ParameterName("publicParameterName") int javaParameter2) {
                        }
                    }
                    """
                ),
                supportParameterName
            ),
            source = """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public class Foo {
                public Foo() { throw new RuntimeException("Stub!"); }
                public void foo(int javaParameter1, int publicParameterName) { throw new RuntimeException("Stub!"); }
                }
                 """
        )
    }

    @Test
    fun `Remove Hidden Annotations`() {
        // When APIs reference annotations that are hidden, make sure the're excluded from the stubs and
        // signature files
        checkStubs(
            format = FileFormat.V2,
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;

                    public class Foo {
                        public void foo(int p1, @MyAnnotation("publicParameterName") java.util.Map<java.lang.String, @MyAnnotation("Something") String> p2) {
                        }
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    import java.lang.annotation.*;
                    import static java.lang.annotation.ElementType.*;
                    import static java.lang.annotation.RetentionPolicy.SOURCE;
                    /** @hide */
                    @SuppressWarnings("WeakerAccess")
                    @Retention(SOURCE)
                    @Target({METHOD, PARAMETER, FIELD, TYPE_USE})
                    public @interface MyAnnotation {
                        String value();
                    }
                    """
                )
            ),
            api = if (SUPPORT_TYPE_USE_ANNOTATIONS) {
                """
                package test.pkg {
                  public class Foo {
                    ctor public Foo();
                    method public void foo(int, java.util.Map<java.lang.String!,java.lang.String!>!);
                  }
                }
                """
            } else {
                """
                package test.pkg {
                  public class Foo {
                    ctor public Foo();
                    method public void foo(int, java.util.Map<java.lang.String,java.lang.String>);
                  }
                }
                """
            },

            source = if (SUPPORT_TYPE_USE_ANNOTATIONS) {
                """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public class Foo {
                public Foo() { throw new RuntimeException("Stub!"); }
                public void foo(int p1, java.util.Map<java.lang.String, java.lang.String> p2) { throw new RuntimeException("Stub!"); }
                }
                """
            } else {
                """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public class Foo {
                public Foo() { throw new RuntimeException("Stub!"); }
                public void foo(int p1, java.util.Map<java.lang.String,java.lang.String> p2) { throw new RuntimeException("Stub!"); }
                }
                """
            }
        )
    }

    @Test
    fun `Arguments to super constructors`() {
        // When overriding constructors we have to supply arguments
        checkStubs(
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;

                    @SuppressWarnings("WeakerAccess")
                    public class Constructors {
                        public class Parent {
                            public Parent(String arg1, int arg2, long arg3, boolean arg4, short arg5) {
                            }
                        }

                        public class Child extends Parent {
                            public Child(String arg1, int arg2, long arg3, boolean arg4, short arg5) {
                                super(arg1, arg2, arg3, arg4, arg5);
                            }

                            private Child(String arg1) {
                                super(arg1, 0, 0, false, 0);
                            }
                        }

                        public class Child2 extends Parent {
                            Child2(String arg1) {
                                super(arg1, 0, 0, false, 0);
                            }
                        }

                        public class Child3 extends Child2 {
                            private Child3(String arg1) {
                                super("something");
                            }
                        }

                        public class Child4 extends Parent {
                            Child4(String arg1, HiddenClass arg2) {
                                super(arg1, 0, 0, true, 0);
                            }
                        }
                        /** @hide */
                        public class HiddenClass {
                        }
                    }
                    """
                )
            ),
            source = """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Constructors {
                    public Constructors() { throw new RuntimeException("Stub!"); }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Child extends test.pkg.Constructors.Parent {
                    public Child(java.lang.String arg1, int arg2, long arg3, boolean arg4, short arg5) { super(null, 0, 0, false, (short)0); throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Child2 extends test.pkg.Constructors.Parent {
                    Child2() { super(null, 0, 0, false, (short)0); throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Child3 extends test.pkg.Constructors.Child2 {
                    Child3() { throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Child4 extends test.pkg.Constructors.Parent {
                    Child4() { super(null, 0, 0, false, (short)0); throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Parent {
                    public Parent(java.lang.String arg1, int arg2, long arg3, boolean arg4, short arg5) { throw new RuntimeException("Stub!"); }
                    }
                    }
                    """,
            checkTextStubEquivalence = true
        )
    }

    @Test
    fun `Arguments to super constructors with showAnnotations`() {
        // When overriding constructors we have to supply arguments
        checkStubs(
            showAnnotations = arrayOf("android.annotation.SystemApi"),
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;

                    @SuppressWarnings("WeakerAccess")
                    public class Constructors {
                        public class Parent {
                            public Parent(String s, int i, long l, boolean b, short sh) {
                            }
                        }

                        public class Child extends Parent {
                            public Child(String s, int i, long l, boolean b, short sh) {
                                super(s, i, l, b, sh);
                            }

                            private Child(String s) {
                                super(s, 0, 0, false, 0);
                            }
                        }

                        public class Child2 extends Parent {
                            Child2(String s) {
                                super(s, 0, 0, false, 0);
                            }
                        }

                        public class Child3 extends Child2 {
                            private Child3(String s) {
                                super("something");
                            }
                        }

                        public class Child4 extends Parent {
                            Child4(String s, HiddenClass hidden) {
                                super(s, 0, 0, true, 0);
                            }
                        }
                        /** @hide */
                        public class HiddenClass {
                        }
                    }
                    """
                )
            ),
            source = """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Constructors {
                    public Constructors() { throw new RuntimeException("Stub!"); }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Child extends test.pkg.Constructors.Parent {
                    public Child(java.lang.String s, int i, long l, boolean b, short sh) { super(null, 0, 0, false, (short)0); throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Child2 extends test.pkg.Constructors.Parent {
                    Child2() { super(null, 0, 0, false, (short)0); throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Child3 extends test.pkg.Constructors.Child2 {
                    Child3() { throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Child4 extends test.pkg.Constructors.Parent {
                    Child4() { super(null, 0, 0, false, (short)0); throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Parent {
                    public Parent(java.lang.String s, int i, long l, boolean b, short sh) { throw new RuntimeException("Stub!"); }
                    }
                    }
                    """
        )
    }

    // TODO: Add test to see what happens if I have Child4 in a different package which can't access the package private constructor of child3?

    @Test
    fun `DocOnly members should be omitted`() {
        // When marked @doconly don't include in stubs or signature files
        // unless specifically asked for (which we do when generating docs-stubs).
        checkStubs(
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;

                    @SuppressWarnings("JavaDoc")
                    public class Outer {
                        /** @doconly Some docs here */
                        public class MyClass1 {
                            public int myField;
                        }

                        public class MyClass2 {
                            /** @doconly Some docs here */
                            public int myField;

                            /** @doconly Some docs here */
                            public int myMethod() { return 0; }
                        }
                    }
                    """
                )
            ),
            source = """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public class Outer {
                public Outer() { throw new RuntimeException("Stub!"); }
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public class MyClass2 {
                public MyClass2() { throw new RuntimeException("Stub!"); }
                }
                }
                    """,
            api = """
                package test.pkg {
                  public class Outer {
                    ctor public Outer();
                  }
                  public class Outer.MyClass2 {
                    ctor public Outer.MyClass2();
                  }
                }
                """
        )
    }

    @Test
    fun `DocOnly members should be included when requested`() {
        // When marked @doconly don't include in stubs or signature files
        // unless specifically asked for (which we do when generating docs).
        checkStubs(
            docStubs = true,
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;

                    @SuppressWarnings("JavaDoc")
                    public class Outer {
                        /** @doconly Some docs here */
                        public class MyClass1 {
                            public int myField;
                        }

                        public class MyClass2 {
                            /** @doconly Some docs here */
                            public int myField;

                            /** @doconly Some docs here */
                            public int myMethod() { return 0; }
                        }
                    }
                    """
                )
            ),
            source = """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Outer {
                    public Outer() { throw new RuntimeException("Stub!"); }
                    /** @doconly Some docs here */
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class MyClass1 {
                    public MyClass1() { throw new RuntimeException("Stub!"); }
                    public int myField;
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class MyClass2 {
                    public MyClass2() { throw new RuntimeException("Stub!"); }
                    /** @doconly Some docs here */
                    public int myMethod() { throw new RuntimeException("Stub!"); }
                    /** @doconly Some docs here */
                    public int myField;
                    }
                    }
                    """
        )
    }

    @Test
    fun `Check generating required stubs from hidden super classes and interfaces`() {
        checkStubs(
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;
                    public class MyClass extends HiddenSuperClass implements HiddenInterface, PublicInterface2 {
                        public void myMethod() { }
                        @Override public void publicInterfaceMethod2() { }
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    class HiddenSuperClass extends PublicSuperParent {
                        @Override public void inheritedMethod2() { }
                        @Override public void publicInterfaceMethod() { }
                        @Override public void publicMethod() {}
                        @Override public void publicMethod2() {}
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    public abstract class PublicSuperParent {
                        public void inheritedMethod1() {}
                        public void inheritedMethod2() {}
                        public abstract void publicMethod() {}
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    interface HiddenInterface extends PublicInterface {
                        int MY_CONSTANT = 5;
                        void hiddenInterfaceMethod();
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    public interface PublicInterface {
                        void publicInterfaceMethod();
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    public interface PublicInterface2 {
                        void publicInterfaceMethod2();
                    }
                    """
                )
            ),
            warnings = "",
            api = """
                    package test.pkg {
                      public class MyClass extends test.pkg.PublicSuperParent implements test.pkg.PublicInterface test.pkg.PublicInterface2 {
                        ctor public MyClass();
                        method public void myMethod();
                        method public void publicInterfaceMethod();
                        method public void publicInterfaceMethod2();
                        method public void publicMethod();
                        method public void publicMethod2();
                        field public static final int MY_CONSTANT = 5; // 0x5
                      }
                      public interface PublicInterface {
                        method public void publicInterfaceMethod();
                      }
                      public interface PublicInterface2 {
                        method public void publicInterfaceMethod2();
                      }
                      public abstract class PublicSuperParent {
                        ctor public PublicSuperParent();
                        method public void inheritedMethod1();
                        method public void inheritedMethod2();
                        method public abstract void publicMethod();
                      }
                    }
                """,
            source = """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public class MyClass extends test.pkg.PublicSuperParent implements test.pkg.PublicInterface, test.pkg.PublicInterface2 {
                public MyClass() { throw new RuntimeException("Stub!"); }
                public void myMethod() { throw new RuntimeException("Stub!"); }
                public void publicInterfaceMethod2() { throw new RuntimeException("Stub!"); }
                public void publicMethod() { throw new RuntimeException("Stub!"); }
                public void publicMethod2() { throw new RuntimeException("Stub!"); }
                public void publicInterfaceMethod() { throw new RuntimeException("Stub!"); }
                public void inheritedMethod2() { throw new RuntimeException("Stub!"); }
                public static final int MY_CONSTANT = 5; // 0x5
                }
                """
        )
    }

    @Test
    fun `Rewrite unknown nullability annotations as sdk stubs`() {
        check(
            format = FileFormat.V2,
            checkCompilation = true,
            sourceFiles = arrayOf(
                java(
                    "package my.pkg;\n" +
                        "public class String {\n" +
                        "public String(@other.NonNull char[] value) { throw new RuntimeException(\"Stub!\"); }\n" +
                        "}\n"
                )
            ),
            expectedIssues = "",
            api = """
                    package my.pkg {
                      public class String {
                        ctor public String(@NonNull char[]);
                      }
                    }
                    """,
            stubFiles = arrayOf(
                java(
                    """
                    package my.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class String {
                    public String(@android.annotation.NonNull char[] value) { throw new RuntimeException("Stub!"); }
                    }
                    """
                )
            )
        )
    }

    @Test
    fun `Rewrite unknown nullability annotations as doc stubs`() {
        check(
            format = FileFormat.V2,
            checkCompilation = true,
            sourceFiles = arrayOf(
                java(
                    "package my.pkg;\n" +
                        "public class String {\n" +
                        "public String(@other.NonNull char[] value) { throw new RuntimeException(\"Stub!\"); }\n" +
                        "}\n"
                )
            ),
            expectedIssues = "",
            api = """
                    package my.pkg {
                      public class String {
                        ctor public String(@NonNull char[]);
                      }
                    }
                    """,
            docStubs = true,
            stubFiles = arrayOf(
                java(
                    """
                        package my.pkg;
                        @SuppressWarnings({"unchecked", "deprecation", "all"})
                        public class String {
                        public String(@androidx.annotation.NonNull char[] value) { throw new RuntimeException("Stub!"); }
                        }
                    """
                )
            )
        )
    }

    @Test
    fun `Rewrite libcore annotations`() {
        check(
            checkCompilation = true,
            sourceFiles = arrayOf(
                java(
                    "package my.pkg;\n" +
                        "public class String {\n" +
                        "public String(char @libcore.util.NonNull [] value) { throw new RuntimeException(\"Stub!\"); }\n" +
                        "}\n"
                )
            ),
            expectedIssues = "",
            api = """
                    package my.pkg {
                      public class String {
                        ctor public String(char[]);
                      }
                    }
                    """,
            stubFiles = if (SUPPORT_TYPE_USE_ANNOTATIONS) {
                arrayOf(
                    java(
                        """
                        package my.pkg;
                        @SuppressWarnings({"unchecked", "deprecation", "all"})
                        public class String {
                        public String(char @androidx.annotation.NonNull [] value) { throw new RuntimeException("Stub!"); }
                        }
                        """
                    )
                )
            } else {
                arrayOf(
                    java(
                        """
                        package my.pkg;
                        @SuppressWarnings({"unchecked", "deprecation", "all"})
                        public class String {
                        public String(char[] value) { throw new RuntimeException("Stub!"); }
                        }
                        """
                    )
                )
            }
        )
    }

    @Test
    fun `Pass through libcore annotations`() {
        check(
            format = FileFormat.V2,
            checkCompilation = true,
            extraArguments = arrayOf(
                ARG_PASS_THROUGH_ANNOTATION, "libcore.util.NonNull"
            ),
            sourceFiles = arrayOf(
                java(
                    """
                    package my.pkg;
                    public class String {
                    public String(@libcore.util.NonNull char[] value) { throw new RuntimeException("Stub!"); }
                    }
                    """
                ),
                libcoreNonNullSource
            ),
            expectedIssues = "",
            api = """
                    package libcore.util {
                      @java.lang.annotation.Documented @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.SOURCE) @java.lang.annotation.Target({java.lang.annotation.ElementType.TYPE_USE}) public @interface NonNull {
                      }
                    }
                    package my.pkg {
                      public class String {
                        ctor public String(@libcore.util.NonNull char[]);
                      }
                    }
                    """,
            stubFiles = arrayOf(
                java(
                    """
                    package my.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class String {
                    public String(@libcore.util.NonNull char[] value) { throw new RuntimeException("Stub!"); }
                    }
                    """
                )
            )
        )
    }

    @Test
    fun `Pass through multiple annotations`() {
        checkStubs(
            extraArguments = arrayOf(
                ARG_PASS_THROUGH_ANNOTATION, "androidx.annotation.RequiresApi,androidx.annotation.Nullable",
                ARG_HIDE_PACKAGE, "androidx.annotation"
            ),
            sourceFiles = arrayOf(
                java(
                    """
                    package my.pkg;
                    public class MyClass {
                        @androidx.annotation.RequiresApi(21)
                        public void testMethod() {}
                        @androidx.annotation.Nullable
                        public String anotherTestMethod() { return null; }
                    }
                    """
                ),
                supportParameterName,
                requiresApiSource,
                androidxNullableSource
            ),
            source = """
                package my.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public class MyClass {
                public MyClass() { throw new RuntimeException("Stub!"); }
                @androidx.annotation.RequiresApi(21)
                public void testMethod() { throw new RuntimeException("Stub!"); }
                @androidx.annotation.Nullable
                public java.lang.String anotherTestMethod() { throw new RuntimeException("Stub!"); }
                }
                 """
        )
    }

    @Test
    fun `Skip RequiresApi annotation`() {
        check(
            extraArguments = arrayOf(
                ARG_EXCLUDE_ANNOTATION, "androidx.annotation.RequiresApi"
            ),
            sourceFiles = arrayOf(
                java(
                    """
                    package my.pkg;
                    public class MyClass {
                        @androidx.annotation.RequiresApi(21)
                        public void testMethod() {}
                    }
                    """
                ),
                requiresApiSource
            ),
            expectedIssues = "",
            api = """
                    package androidx.annotation {
                      @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.SOURCE) @java.lang.annotation.Target({java.lang.annotation.ElementType.TYPE, java.lang.annotation.ElementType.FIELD, java.lang.annotation.ElementType.METHOD, java.lang.annotation.ElementType.CONSTRUCTOR}) public @interface RequiresApi {
                        method public abstract int api() default 1;
                        method public abstract int value() default 1;
                      }
                    }
                    package my.pkg {
                      public class MyClass {
                        ctor public MyClass();
                        method public void testMethod();
                      }
                    }
                    """,
            stubFiles = arrayOf(
                java(
                    """
                    package my.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class MyClass {
                    public MyClass() { throw new RuntimeException("Stub!"); }
                    public void testMethod() { throw new RuntimeException("Stub!"); }
                    }
                    """
                )
            )
        )
    }

    @Test
    fun `Test inaccessible constructors`() {
        // If the constructors of a class are not visible, and the class has subclasses,
        // those subclass stubs will need to reference these inaccessible constructors.
        // This generally only happens when the constructors are package private (and
        // therefore hidden) but the subclass using it is also in the same package.

        check(
            checkCompilation = true,
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;
                    public class MyClass1 {
                        MyClass1(int myVar) { }
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    import java.io.IOException;
                    @SuppressWarnings("RedundantThrows")
                    public class MySubClass1 extends MyClass1 {
                        MySubClass1(int myVar) throws IOException { super(myVar); }
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    public class MyClass2 {
                        /** @hide */
                        public MyClass2(int myVar) { }
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    public class MySubClass2 extends MyClass2 {
                        public MySubClass2() { super(5); }
                    }
                    """
                )
            ),
            expectedIssues = "",
            api = """
                    package test.pkg {
                      public class MyClass1 {
                      }
                      public class MyClass2 {
                      }
                      public class MySubClass1 extends test.pkg.MyClass1 {
                      }
                      public class MySubClass2 extends test.pkg.MyClass2 {
                        ctor public MySubClass2();
                      }
                    }
                    """,
            stubFiles = arrayOf(
                java(
                    """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class MyClass1 {
                    MyClass1() { throw new RuntimeException("Stub!"); }
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class MySubClass1 extends test.pkg.MyClass1 {
                    MySubClass1() { throw new RuntimeException("Stub!"); }
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class MyClass2 {
                    MyClass2() { throw new RuntimeException("Stub!"); }
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class MySubClass2 extends test.pkg.MyClass2 {
                    public MySubClass2() { throw new RuntimeException("Stub!"); }
                    }
                    """
                )
            ),
            stubsSourceList = """
                TESTROOT/stubs/test/pkg/MyClass1.java
                TESTROOT/stubs/test/pkg/MyClass2.java
                TESTROOT/stubs/test/pkg/MySubClass1.java
                TESTROOT/stubs/test/pkg/MySubClass2.java
            """
        )
    }

    @Test
    fun `Generics Variable Rewriting`() {
        // When we move methods from hidden superclasses into the subclass since they
        // provide the implementation for a required method, it's possible that the
        // method we copied in is referencing generics with a different variable than
        // in the current class, so we need to handle this

        checkStubs(
            sourceFiles = arrayOf(
                // TODO: Try using prefixes like "A", and "AA" to make sure my generics
                // variable renaming doesn't do something really unexpected
                java(
                    """
                    package test.pkg;

                    import java.util.List;
                    import java.util.Map;

                    public class Generics {
                        public class MyClass<X extends Number,Y> extends HiddenParent<X,Y> implements PublicParent<X,Y> {
                        }

                        public class MyClass2<W> extends HiddenParent<Float,W> implements PublicParent<Float, W> {
                        }

                        public class MyClass3 extends HiddenParent<Float,Double> implements PublicParent<Float,Double> {
                        }

                        class HiddenParent<M, N> extends HiddenParent2<M, N>  {
                        }

                        class HiddenParent2<T, TT>  {
                            public Map<T,Map<TT, String>> createMap(List<T> list) {
                                return null;
                            }
                        }

                        public interface PublicParent<A extends Number,B> {
                            Map<A,Map<B, String>> createMap(List<A> list);
                        }
                    }
                    """
                )
            ),
            warnings = "",
            source = """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Generics {
                    public Generics() { throw new RuntimeException("Stub!"); }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class MyClass<X extends java.lang.Number, Y> implements test.pkg.Generics.PublicParent<X,Y> {
                    public MyClass() { throw new RuntimeException("Stub!"); }
                    public java.util.Map<X,java.util.Map<Y,java.lang.String>> createMap(java.util.List<X> list) { throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class MyClass2<W> implements test.pkg.Generics.PublicParent<java.lang.Float,W> {
                    public MyClass2() { throw new RuntimeException("Stub!"); }
                    public java.util.Map<java.lang.Float,java.util.Map<W,java.lang.String>> createMap(java.util.List<java.lang.Float> list) { throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class MyClass3 implements test.pkg.Generics.PublicParent<java.lang.Float,java.lang.Double> {
                    public MyClass3() { throw new RuntimeException("Stub!"); }
                    public java.util.Map<java.lang.Float,java.util.Map<java.lang.Double,java.lang.String>> createMap(java.util.List<java.lang.Float> list) { throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public static interface PublicParent<A extends java.lang.Number, B> {
                    public java.util.Map<A,java.util.Map<B,java.lang.String>> createMap(java.util.List<A> list);
                    }
                    }
                    """
        )
    }

    @Test
    fun `Rewriting type parameters in interfaces from hidden super classes and in throws lists`() {
        checkStubs(
            format = FileFormat.V1,
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;

                    import java.io.IOException;
                    import java.util.List;
                    import java.util.Map;

                    @SuppressWarnings({"RedundantThrows", "WeakerAccess"})
                    public class Generics {
                        public class MyClass<X, Y extends Number> extends HiddenParent<X, Y> implements PublicInterface<X, Y> {
                        }

                        class HiddenParent<M, N extends Number> extends PublicParent<M, N> {
                            public Map<M, Map<N, String>> createMap(List<M> list) throws MyThrowable {
                                return null;
                            }

                            protected List<M> foo() {
                                return null;
                            }

                        }

                        class MyThrowable extends IOException {
                        }

                        public abstract class PublicParent<A, B extends Number> {
                            protected abstract List<A> foo();
                        }

                        public interface PublicInterface<A, B> {
                            Map<A, Map<B, String>> createMap(List<A> list) throws IOException;
                        }
                    }
                    """
                )
            ),
            warnings = "",
            api = """
                package test.pkg {
                  public class Generics {
                    ctor public Generics();
                  }
                  public class Generics.MyClass<X, Y extends java.lang.Number> extends test.pkg.Generics.PublicParent<X,Y> implements test.pkg.Generics.PublicInterface<X,Y> {
                    ctor public Generics.MyClass();
                    method public java.util.Map<X,java.util.Map<Y,java.lang.String>> createMap(java.util.List<X>) throws java.io.IOException;
                    method public java.util.List<X> foo();
                  }
                  public static interface Generics.PublicInterface<A, B> {
                    method public java.util.Map<A,java.util.Map<B,java.lang.String>> createMap(java.util.List<A>) throws java.io.IOException;
                  }
                  public abstract class Generics.PublicParent<A, B extends java.lang.Number> {
                    ctor public Generics.PublicParent();
                    method protected abstract java.util.List<A> foo();
                  }
                }
                """,
            source = if (SUPPORT_TYPE_USE_ANNOTATIONS) {
                """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public class Generics {
                public Generics() { throw new RuntimeException("Stub!"); }
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public class MyClass<X, Y extends java.lang.Number> extends test.pkg.Generics.PublicParent<X,Y> implements test.pkg.Generics.PublicInterface<X,Y> {
                public MyClass() { throw new RuntimeException("Stub!"); }
                public java.util.List<X> foo() { throw new RuntimeException("Stub!"); }
                public java.util.Map<X,java.util.Map<Y,java.lang.String>> createMap(java.util.List<X> list) throws java.io.IOException { throw new RuntimeException("Stub!"); }
                }
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public static interface PublicInterface<A, B> {
                public java.util.Map<A,java.util.Map<B,java.lang.String>> createMap(java.util.List<A> list) throws java.io.IOException;
                }
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public abstract class PublicParent<A, B extends java.lang.Number> {
                public PublicParent() { throw new RuntimeException("Stub!"); }
                protected abstract java.util.List<A> foo();
                }
                }
                """
            } else {
                """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public class Generics {
                public Generics() { throw new RuntimeException("Stub!"); }
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public class MyClass<X, Y extends java.lang.Number> extends test.pkg.Generics.PublicParent<X,Y> implements test.pkg.Generics.PublicInterface<X,Y> {
                public MyClass() { throw new RuntimeException("Stub!"); }
                public java.util.List<X> foo() { throw new RuntimeException("Stub!"); }
                public java.util.Map<X,java.util.Map<Y,java.lang.String>> createMap(java.util.List<X> list) throws java.io.IOException { throw new RuntimeException("Stub!"); }
                }
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public static interface PublicInterface<A, B> {
                public java.util.Map<A,java.util.Map<B,java.lang.String>> createMap(java.util.List<A> list) throws java.io.IOException;
                }
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public abstract class PublicParent<A, B extends java.lang.Number> {
                public PublicParent() { throw new RuntimeException("Stub!"); }
                protected abstract java.util.List<A> foo();
                }
                }
                """
            }
        )
    }

    @Test
    fun `Picking super class throwables`() {
        // Like previous test, but without compatibility mode: ensures that we
        // use super classes of filtered throwables
        checkStubs(
            format = FileFormat.V3,
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;

                    import java.io.IOException;
                    import java.util.List;
                    import java.util.Map;

                    @SuppressWarnings({"RedundantThrows", "WeakerAccess"})
                    public class Generics {
                        public class MyClass<X, Y extends Number> extends HiddenParent<X, Y> implements PublicInterface<X, Y> {
                        }

                        class HiddenParent<M, N extends Number> extends PublicParent<M, N> {
                            public Map<M, Map<N, String>> createMap(List<M> list) throws MyThrowable {
                                return null;
                            }

                            protected List<M> foo() {
                                return null;
                            }

                        }

                        class MyThrowable extends IOException {
                        }

                        public abstract class PublicParent<A, B extends Number> {
                            protected abstract List<A> foo();
                        }

                        public interface PublicInterface<A, B> {
                            Map<A, Map<B, String>> createMap(List<A> list) throws IOException;
                        }
                    }
                    """
                )
            ),
            warnings = "",
            api = """
                // Signature format: 3.0
                package test.pkg {
                  public class Generics {
                    ctor public Generics();
                  }
                  public class Generics.MyClass<X, Y extends java.lang.Number> extends test.pkg.Generics.PublicParent<X,Y> implements test.pkg.Generics.PublicInterface<X,Y> {
                    ctor public Generics.MyClass();
                    method public java.util.Map<X!,java.util.Map<Y!,java.lang.String!>!>! createMap(java.util.List<X!>!) throws java.io.IOException;
                    method public java.util.List<X!>! foo();
                  }
                  public static interface Generics.PublicInterface<A, B> {
                    method public java.util.Map<A!,java.util.Map<B!,java.lang.String!>!>! createMap(java.util.List<A!>!) throws java.io.IOException;
                  }
                  public abstract class Generics.PublicParent<A, B extends java.lang.Number> {
                    ctor public Generics.PublicParent();
                    method protected abstract java.util.List<A!>! foo();
                  }
                }
            """,
            source = """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Generics {
                    public Generics() { throw new RuntimeException("Stub!"); }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class MyClass<X, Y extends java.lang.Number> extends test.pkg.Generics.PublicParent<X,Y> implements test.pkg.Generics.PublicInterface<X,Y> {
                    public MyClass() { throw new RuntimeException("Stub!"); }
                    public java.util.List<X> foo() { throw new RuntimeException("Stub!"); }
                    public java.util.Map<X,java.util.Map<Y,java.lang.String>> createMap(java.util.List<X> list) throws java.io.IOException { throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public static interface PublicInterface<A, B> {
                    public java.util.Map<A,java.util.Map<B,java.lang.String>> createMap(java.util.List<A> list) throws java.io.IOException;
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public abstract class PublicParent<A, B extends java.lang.Number> {
                    public PublicParent() { throw new RuntimeException("Stub!"); }
                    protected abstract java.util.List<A> foo();
                    }
                    }
                    """
        )
    }

    @Test
    fun `Rewriting implements class references`() {
        // Checks some more subtle bugs around generics type variable renaming
        checkStubs(
            format = FileFormat.V2,
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;

                    import java.util.Collection;
                    import java.util.Set;

                    @SuppressWarnings("all")
                    public class ConcurrentHashMap<K, V> {
                        public abstract static class KeySetView<K, V> extends CollectionView<K, V, K>
                                implements Set<K>, java.io.Serializable {
                        }

                        abstract static class CollectionView<K, V, E>
                                implements Collection<E>, java.io.Serializable {
                            public final Object[] toArray() { return null; }

                            public final <T> T[] toArray(T[] a) {
                                return null;
                            }

                            @Override
                            public int size() {
                                return 0;
                            }
                        }
                    }
                    """
                )
            ),
            warnings = "",
            api = """
                    package test.pkg {
                      public class ConcurrentHashMap<K, V> {
                        ctor public ConcurrentHashMap();
                      }
                      public abstract static class ConcurrentHashMap.KeySetView<K, V> implements java.util.Collection<K> java.io.Serializable java.util.Set<K> {
                        ctor public ConcurrentHashMap.KeySetView();
                        method public int size();
                        method public final Object[] toArray();
                        method public final <T> T[] toArray(T[]);
                      }
                    }
                    """,
            source = """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class ConcurrentHashMap<K, V> {
                    public ConcurrentHashMap() { throw new RuntimeException("Stub!"); }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public abstract static class KeySetView<K, V> implements java.util.Collection<K>, java.io.Serializable, java.util.Set<K> {
                    public KeySetView() { throw new RuntimeException("Stub!"); }
                    public int size() { throw new RuntimeException("Stub!"); }
                    public final java.lang.Object[] toArray() { throw new RuntimeException("Stub!"); }
                    public final <T> T[] toArray(T[] a) { throw new RuntimeException("Stub!"); }
                    }
                    }
                    """
        )
    }

    @Test
    fun `Arrays in type arguments`() {
        checkStubs(
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;

                    public class Generics2 {
                        public class FloatArrayEvaluator implements TypeEvaluator<float[]> {
                        }

                        @SuppressWarnings("WeakerAccess")
                        public interface TypeEvaluator<T> {
                        }
                    }
                    """
                )
            ),
            warnings = "",
            source = """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Generics2 {
                    public Generics2() { throw new RuntimeException("Stub!"); }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class FloatArrayEvaluator implements test.pkg.Generics2.TypeEvaluator<float[]> {
                    public FloatArrayEvaluator() { throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public static interface TypeEvaluator<T> {
                    }
                    }
                    """,
            checkTextStubEquivalence = true
        )
    }

    @Test
    fun `Interface extending multiple interfaces`() {
        // Ensure that we handle sorting correctly where we're mixing super classes and implementing
        // interfaces
        // Real-world example: XmlResourceParser
        check(
            checkCompilation = true,
            sourceFiles = arrayOf(
                java(
                    """
                    package android.content.res;
                    import android.util.AttributeSet;
                    import org.xmlpull.v1.XmlPullParser;

                    @SuppressWarnings("UnnecessaryInterfaceModifier")
                    public interface XmlResourceParser extends XmlPullParser, AttributeSet, AutoCloseable {
                        public void close();
                    }
                    """
                ),
                java(
                    """
                    package android.util;
                    public interface AttributeSet {
                    }
                    """
                ),
                java(
                    """
                    package java.lang;
                    public interface AutoCloseable {
                    }
                    """
                ),
                java(
                    """
                    package org.xmlpull.v1;
                    public interface XmlPullParser {
                    }
                    """
                )
            ),
            stubFiles = arrayOf(
                java(
                    """
                    package android.content.res;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public interface XmlResourceParser extends org.xmlpull.v1.XmlPullParser,  android.util.AttributeSet, java.lang.AutoCloseable {
                    public void close();
                    }
                    """
                )
            )
        )
    }

// TODO: Add a protected constructor too to make sure my code to make non-public constructors package private
// don't accidentally demote protected constructors to package private!

    @Test
    fun `Picking Super Constructors`() {
        checkStubs(
            format = FileFormat.V2,
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;

                    @SuppressWarnings({"RedundantThrows", "JavaDoc", "WeakerAccess"})
                    public class PickConstructors {
                        public abstract static class FileInputStream extends InputStream {

                            public FileInputStream(String name) throws FileNotFoundException {
                            }

                            public FileInputStream(File file) throws FileNotFoundException {
                            }

                            public FileInputStream(FileDescriptor fdObj) {
                                this(fdObj, false /* isFdOwner */);
                            }

                            /**
                             * @hide
                             */
                            public FileInputStream(FileDescriptor fdObj, boolean isFdOwner) {
                            }
                        }

                        public abstract static class AutoCloseInputStream extends FileInputStream {
                            public AutoCloseInputStream(ParcelFileDescriptor pfd) {
                                super(pfd.getFileDescriptor());
                            }
                        }

                        abstract static class HiddenParentStream extends FileInputStream {
                            public HiddenParentStream(FileDescriptor pfd) {
                                super(pfd);
                            }
                        }

                        public abstract static class AutoCloseInputStream2 extends HiddenParentStream {
                            public AutoCloseInputStream2(ParcelFileDescriptor pfd) {
                                super(pfd.getFileDescriptor());
                            }
                        }

                        public abstract class ParcelFileDescriptor implements Closeable {
                            public abstract FileDescriptor getFileDescriptor();
                        }

                        @SuppressWarnings("UnnecessaryInterfaceModifier")
                        public static interface Closeable extends AutoCloseable {
                        }

                        @SuppressWarnings("UnnecessaryInterfaceModifier")
                        public static interface AutoCloseable {
                        }

                        public static abstract class InputStream implements Closeable {
                        }

                        public static class File {
                        }

                        public static final class FileDescriptor {
                        }

                        public static class FileNotFoundException extends IOException {
                        }

                        public static class IOException extends Exception {
                        }
                    }
                    """
                )
            ),
            warnings = "",
            api = """
                    package test.pkg {
                      public class PickConstructors {
                        ctor public PickConstructors();
                      }
                      public abstract static class PickConstructors.AutoCloseInputStream extends test.pkg.PickConstructors.FileInputStream {
                        ctor public PickConstructors.AutoCloseInputStream(test.pkg.PickConstructors.ParcelFileDescriptor);
                      }
                      public abstract static class PickConstructors.AutoCloseInputStream2 extends test.pkg.PickConstructors.FileInputStream {
                        ctor public PickConstructors.AutoCloseInputStream2(test.pkg.PickConstructors.ParcelFileDescriptor);
                      }
                      public static interface PickConstructors.AutoCloseable {
                      }
                      public static interface PickConstructors.Closeable extends test.pkg.PickConstructors.AutoCloseable {
                      }
                      public static class PickConstructors.File {
                        ctor public PickConstructors.File();
                      }
                      public static final class PickConstructors.FileDescriptor {
                        ctor public PickConstructors.FileDescriptor();
                      }
                      public abstract static class PickConstructors.FileInputStream extends test.pkg.PickConstructors.InputStream {
                        ctor public PickConstructors.FileInputStream(String) throws test.pkg.PickConstructors.FileNotFoundException;
                        ctor public PickConstructors.FileInputStream(test.pkg.PickConstructors.File) throws test.pkg.PickConstructors.FileNotFoundException;
                        ctor public PickConstructors.FileInputStream(test.pkg.PickConstructors.FileDescriptor);
                      }
                      public static class PickConstructors.FileNotFoundException extends test.pkg.PickConstructors.IOException {
                        ctor public PickConstructors.FileNotFoundException();
                      }
                      public static class PickConstructors.IOException extends java.lang.Exception {
                        ctor public PickConstructors.IOException();
                      }
                      public abstract static class PickConstructors.InputStream implements test.pkg.PickConstructors.Closeable {
                        ctor public PickConstructors.InputStream();
                      }
                      public abstract class PickConstructors.ParcelFileDescriptor implements test.pkg.PickConstructors.Closeable {
                        ctor public PickConstructors.ParcelFileDescriptor();
                        method public abstract test.pkg.PickConstructors.FileDescriptor getFileDescriptor();
                      }
                    }
                """,
            source = """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class PickConstructors {
                    public PickConstructors() { throw new RuntimeException("Stub!"); }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public abstract static class AutoCloseInputStream extends test.pkg.PickConstructors.FileInputStream {
                    public AutoCloseInputStream(test.pkg.PickConstructors.ParcelFileDescriptor pfd) { super((test.pkg.PickConstructors.FileDescriptor)null); throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public abstract static class AutoCloseInputStream2 extends test.pkg.PickConstructors.FileInputStream {
                    public AutoCloseInputStream2(test.pkg.PickConstructors.ParcelFileDescriptor pfd) { super((test.pkg.PickConstructors.FileDescriptor)null); throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public static interface AutoCloseable {
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public static interface Closeable extends test.pkg.PickConstructors.AutoCloseable {
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public static class File {
                    public File() { throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public static final class FileDescriptor {
                    public FileDescriptor() { throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public abstract static class FileInputStream extends test.pkg.PickConstructors.InputStream {
                    public FileInputStream(java.lang.String name) throws test.pkg.PickConstructors.FileNotFoundException { throw new RuntimeException("Stub!"); }
                    public FileInputStream(test.pkg.PickConstructors.File file) throws test.pkg.PickConstructors.FileNotFoundException { throw new RuntimeException("Stub!"); }
                    public FileInputStream(test.pkg.PickConstructors.FileDescriptor fdObj) { throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public static class FileNotFoundException extends test.pkg.PickConstructors.IOException {
                    public FileNotFoundException() { throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public static class IOException extends java.lang.Exception {
                    public IOException() { throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public abstract static class InputStream implements test.pkg.PickConstructors.Closeable {
                    public InputStream() { throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public abstract class ParcelFileDescriptor implements test.pkg.PickConstructors.Closeable {
                    public ParcelFileDescriptor() { throw new RuntimeException("Stub!"); }
                    public abstract test.pkg.PickConstructors.FileDescriptor getFileDescriptor();
                    }
                    }
                    """
        )
    }

    @Test
    fun `Picking Constructors`() {
        checkStubs(
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;

                    @SuppressWarnings({"WeakerAccess", "unused"})
                    public class Constructors2 {
                        public class TestSuite implements Test {

                            public TestSuite() {
                            }

                            public TestSuite(final Class<?> theClass) {
                            }

                            public TestSuite(Class<? extends TestCase> theClass, String name) {
                                this(theClass);
                            }

                            public TestSuite(String name) {
                            }
                            public TestSuite(Class<?>... classes) {
                            }

                            public TestSuite(Class<? extends TestCase>[] classes, String name) {
                                this(classes);
                            }
                        }

                        public class TestCase {
                        }

                        public interface Test {
                        }

                        public class Parent {
                            public Parent(int x) throws IOException {
                            }
                        }

                        class Intermediate extends Parent {
                            Intermediate(int x) throws IOException { super(x); }
                        }

                        public class Child extends Intermediate {
                            public Child() throws IOException { super(5); }
                            public Child(float x) throws IOException { this(); }
                        }

                        // ----------------------------------------------------

                        public abstract class DrawableWrapper {
                            public DrawableWrapper(Drawable dr) {
                            }

                            DrawableWrapper(Clipstate state, Object resources) {
                            }
                        }


                        public class ClipDrawable extends DrawableWrapper {
                            ClipDrawable() {
                                this(null);
                            }

                            public ClipDrawable(Drawable drawable, int gravity, int orientation) { this(null); }

                            private ClipDrawable(Clipstate clipstate) {
                                super(clipstate, null);
                            }
                        }

                        public class Drawable {
                        }

                        class Clipstate {
                        }
                    }
                    """
                )
            ),
            warnings = "",
            source = """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Constructors2 {
                    public Constructors2() { throw new RuntimeException("Stub!"); }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Child extends test.pkg.Constructors2.Parent {
                    public Child() { super(0); throw new RuntimeException("Stub!"); }
                    public Child(float x) { super(0); throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class ClipDrawable extends test.pkg.Constructors2.DrawableWrapper {
                    public ClipDrawable(test.pkg.Constructors2.Drawable drawable, int gravity, int orientation) { super(null); throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Drawable {
                    public Drawable() { throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public abstract class DrawableWrapper {
                    public DrawableWrapper(test.pkg.Constructors2.Drawable dr) { throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Parent {
                    public Parent(int x) { throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public static interface Test {
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class TestCase {
                    public TestCase() { throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class TestSuite implements test.pkg.Constructors2.Test {
                    public TestSuite() { throw new RuntimeException("Stub!"); }
                    public TestSuite(java.lang.Class<?> theClass) { throw new RuntimeException("Stub!"); }
                    public TestSuite(java.lang.Class<? extends test.pkg.Constructors2.TestCase> theClass, java.lang.String name) { throw new RuntimeException("Stub!"); }
                    public TestSuite(java.lang.String name) { throw new RuntimeException("Stub!"); }
                    public TestSuite(java.lang.Class<?>... classes) { throw new RuntimeException("Stub!"); }
                    public TestSuite(java.lang.Class<? extends test.pkg.Constructors2.TestCase>[] classes, java.lang.String name) { throw new RuntimeException("Stub!"); }
                    }
                    }
                    """
        )
    }

    @Test
    fun `Another Constructor Test`() {
        // A specific scenario triggered in the API where the right super class detector was not chosen
        checkStubs(
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;

                    @SuppressWarnings({"RedundantThrows", "JavaDoc", "WeakerAccess"})
                    public class PickConstructors2 {
                        public interface EventListener {
                        }

                        public interface PropertyChangeListener extends EventListener {
                        }

                        public static abstract class EventListenerProxy<T extends EventListener>
                                implements EventListener {
                            public EventListenerProxy(T listener) {
                            }
                        }

                        public static class PropertyChangeListenerProxy
                                extends EventListenerProxy<PropertyChangeListener>
                                implements PropertyChangeListener {
                            public PropertyChangeListenerProxy(String propertyName, PropertyChangeListener listener) {
                                super(listener);
                            }
                        }
                    }
                    """
                )
            ),
            warnings = "",
            source = """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class PickConstructors2 {
                    public PickConstructors2() { throw new RuntimeException("Stub!"); }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public static interface EventListener {
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public abstract static class EventListenerProxy<T extends test.pkg.PickConstructors2.EventListener> implements test.pkg.PickConstructors2.EventListener {
                    public EventListenerProxy(T listener) { throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public static interface PropertyChangeListener extends test.pkg.PickConstructors2.EventListener {
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public static class PropertyChangeListenerProxy extends test.pkg.PickConstructors2.EventListenerProxy<test.pkg.PickConstructors2.PropertyChangeListener> implements test.pkg.PickConstructors2.PropertyChangeListener {
                    public PropertyChangeListenerProxy(java.lang.String propertyName, test.pkg.PickConstructors2.PropertyChangeListener listener) { super(null); throw new RuntimeException("Stub!"); }
                    }
                    }
                    """
        )
    }

    @Test
    fun `Overriding protected methods`() {
        // Checks a scenario where the stubs were missing overrides
        checkStubs(
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;

                    @SuppressWarnings("all")
                    public class Layouts {
                        public static class View {
                            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                            }
                        }

                        public static abstract class ViewGroup extends View {
                            @Override
                            protected abstract void onLayout(boolean changed,
                                    int l, int t, int r, int b);
                        }

                        public static class Toolbar extends ViewGroup {
                            @Override
                            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                            }
                        }
                    }
                    """
                )
            ),
            warnings = "",
            api = """
                    package test.pkg {
                      public class Layouts {
                        ctor public Layouts();
                      }
                      public static class Layouts.Toolbar extends test.pkg.Layouts.ViewGroup {
                        ctor public Layouts.Toolbar();
                      }
                      public static class Layouts.View {
                        ctor public Layouts.View();
                        method protected void onLayout(boolean, int, int, int, int);
                      }
                      public abstract static class Layouts.ViewGroup extends test.pkg.Layouts.View {
                        ctor public Layouts.ViewGroup();
                        method protected abstract void onLayout(boolean, int, int, int, int);
                      }
                    }
                    """,
            source = """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Layouts {
                    public Layouts() { throw new RuntimeException("Stub!"); }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public static class Toolbar extends test.pkg.Layouts.ViewGroup {
                    public Toolbar() { throw new RuntimeException("Stub!"); }
                    protected void onLayout(boolean changed, int l, int t, int r, int b) { throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public static class View {
                    public View() { throw new RuntimeException("Stub!"); }
                    protected void onLayout(boolean changed, int left, int top, int right, int bottom) { throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public abstract static class ViewGroup extends test.pkg.Layouts.View {
                    public ViewGroup() { throw new RuntimeException("Stub!"); }
                    protected abstract void onLayout(boolean changed, int l, int t, int r, int b);
                    }
                    }
                    """
        )
    }

    @Test
    fun `Missing overridden method`() {
        // Another special case where overridden methods were missing
        checkStubs(
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;

                    import java.util.Collection;
                    import java.util.Set;

                    @SuppressWarnings("all")
                    public class SpanTest {
                        public interface CharSequence {
                        }
                        public interface Spanned extends CharSequence {
                            public int nextSpanTransition(int start, int limit, Class type);
                        }

                        public interface Spannable extends Spanned {
                        }

                        public class SpannableString extends SpannableStringInternal implements CharSequence, Spannable {
                        }

                        /* package */ abstract class SpannableStringInternal {
                            public int nextSpanTransition(int start, int limit, Class kind) {
                                return 0;
                            }
                        }
                    }
                    """
                )
            ),
            warnings = "",
            source = """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class SpanTest {
                    public SpanTest() { throw new RuntimeException("Stub!"); }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public static interface CharSequence {
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public static interface Spannable extends test.pkg.SpanTest.Spanned {
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class SpannableString implements test.pkg.SpanTest.CharSequence, test.pkg.SpanTest.Spannable {
                    public SpannableString() { throw new RuntimeException("Stub!"); }
                    public int nextSpanTransition(int start, int limit, java.lang.Class kind) { throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public static interface Spanned extends test.pkg.SpanTest.CharSequence {
                    public int nextSpanTransition(int start, int limit, java.lang.Class type);
                    }
                    }
                    """
        )
    }

    @Test
    fun `Skip type variables in casts`() {
        // When generating casts in super constructor calls, use raw types
        checkStubs(
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;

                    @SuppressWarnings("all")
                    public class Properties {
                        public abstract class Property<T, V> {
                            public Property(Class<V> type, String name) {
                            }
                            public Property(Class<V> type, String name, String name2) { // force casts in super
                            }
                        }

                        public abstract class IntProperty<T> extends Property<T, Integer> {

                            public IntProperty(String name) {
                                super(Integer.class, name);
                            }
                        }
                    }
                    """
                )
            ),
            warnings = "",
            source = """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Properties {
                    public Properties() { throw new RuntimeException("Stub!"); }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public abstract class IntProperty<T> extends test.pkg.Properties.Property<T,java.lang.Integer> {
                    public IntProperty(java.lang.String name) { super((java.lang.Class)null, (java.lang.String)null); throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public abstract class Property<T, V> {
                    public Property(java.lang.Class<V> type, java.lang.String name) { throw new RuntimeException("Stub!"); }
                    public Property(java.lang.Class<V> type, java.lang.String name, java.lang.String name2) { throw new RuntimeException("Stub!"); }
                    }
                    }
                    """
        )
    }

    @Test
    fun `Annotation default values`() {
        checkStubs(
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;

                    import java.lang.annotation.ElementType;
                    import java.lang.annotation.Retention;
                    import java.lang.annotation.RetentionPolicy;
                    import java.lang.annotation.Target;

                    import static java.lang.annotation.RetentionPolicy.SOURCE;

                    /**
                     * This annotation can be used to mark fields and methods to be dumped by
                     * the view server. Only non-void methods with no arguments can be annotated
                     * by this annotation.
                     */
                    @Target({ElementType.FIELD, ElementType.METHOD})
                    @Retention(RetentionPolicy.RUNTIME)
                    public @interface ExportedProperty {
                        /**
                         * When resolveId is true, and if the annotated field/method return value
                         * is an int, the value is converted to an Android's resource name.
                         *
                         * @return true if the property's value must be transformed into an Android
                         * resource name, false otherwise
                         */
                        boolean resolveId() default false;
                        String prefix() default "";
                        String category() default "";
                        boolean formatToHexString() default false;
                        boolean hasAdjacentMapping() default false;
                        Class<? extends Number> myCls() default Integer.class;
                        char[] letters1() default {};
                        char[] letters2() default {'a', 'b', 'c'};
                        double from() default Double.NEGATIVE_INFINITY;
                        double fromWithCast() default (double)Float.NEGATIVE_INFINITY;
                        InnerAnnotation value() default @InnerAnnotation;
                        char letter() default 'a';
                        int integer() default 1;
                        long large_integer() default 1L;
                        float floating() default 1.0f;
                        double large_floating() default 1.0;
                        byte small() default 1;
                        short medium() default 1;
                        int math() default 1+2*3;
                        @InnerAnnotation
                        int unit() default PX;
                        int DP = 0;
                        int PX = 1;
                        int SP = 2;
                        @Retention(SOURCE)
                        @interface InnerAnnotation {
                        }
                    }
                    """
                )
            ),
            warnings = "",
            api = """
                package test.pkg {
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) @java.lang.annotation.Target({java.lang.annotation.ElementType.FIELD, java.lang.annotation.ElementType.METHOD}) public @interface ExportedProperty {
                    method public abstract String category() default "";
                    method public abstract float floating() default 1.0f;
                    method public abstract boolean formatToHexString() default false;
                    method public abstract double from() default java.lang.Double.NEGATIVE_INFINITY;
                    method public abstract double fromWithCast() default (double)java.lang.Float.NEGATIVE_INFINITY;
                    method public abstract boolean hasAdjacentMapping() default false;
                    method public abstract int integer() default 1;
                    method public abstract double large_floating() default 1.0;
                    method public abstract long large_integer() default 1L;
                    method public abstract char letter() default 'a';
                    method public abstract char[] letters1() default {};
                    method public abstract char[] letters2() default {'a', 'b', 'c'};
                    method public abstract int math() default 7;
                    method public abstract short medium() default 1;
                    method public abstract Class<? extends java.lang.Number> myCls() default java.lang.Integer.class;
                    method public abstract String prefix() default "";
                    method public abstract boolean resolveId() default false;
                    method public abstract byte small() default 1;
                    method @test.pkg.ExportedProperty.InnerAnnotation public abstract int unit() default test.pkg.ExportedProperty.PX;
                    method public abstract test.pkg.ExportedProperty.InnerAnnotation value() default @test.pkg.ExportedProperty.InnerAnnotation;
                    field public static final int DP = 0; // 0x0
                    field public static final int PX = 1; // 0x1
                    field public static final int SP = 2; // 0x2
                  }
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.SOURCE) public static @interface ExportedProperty.InnerAnnotation {
                  }
                }
            """,
            source = """
                package test.pkg;
                /**
                 * This annotation can be used to mark fields and methods to be dumped by
                 * the view server. Only non-void methods with no arguments can be annotated
                 * by this annotation.
                 */
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
                @java.lang.annotation.Target({java.lang.annotation.ElementType.FIELD, java.lang.annotation.ElementType.METHOD})
                public @interface ExportedProperty {
                /**
                 * When resolveId is true, and if the annotated field/method return value
                 * is an int, the value is converted to an Android's resource name.
                 *
                 * @return true if the property's value must be transformed into an Android
                 * resource name, false otherwise
                 */
                public boolean resolveId() default false;
                public java.lang.String prefix() default "";
                public java.lang.String category() default "";
                public boolean formatToHexString() default false;
                public boolean hasAdjacentMapping() default false;
                public java.lang.Class<? extends java.lang.Number> myCls() default java.lang.Integer.class;
                public char[] letters1() default {};
                public char[] letters2() default {'a', 'b', 'c'};
                public double from() default java.lang.Double.NEGATIVE_INFINITY;
                public double fromWithCast() default (double)java.lang.Float.NEGATIVE_INFINITY;
                public test.pkg.ExportedProperty.InnerAnnotation value() default @test.pkg.ExportedProperty.InnerAnnotation;
                public char letter() default 'a';
                public int integer() default 1;
                public long large_integer() default 1L;
                public float floating() default 1.0f;
                public double large_floating() default 1.0;
                public byte small() default 1;
                public short medium() default 1;
                public int math() default 7;
                public int unit() default test.pkg.ExportedProperty.PX;
                public static final int DP = 0; // 0x0
                public static final int PX = 1; // 0x1
                public static final int SP = 2; // 0x2
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.SOURCE)
                public static @interface InnerAnnotation {
                }
                }
                """
        )
    }

    @Test
    fun `Annotation metadata in stubs`() {
        checkStubs(
            skipEmitPackages = emptyList(),
            sourceFiles = arrayOf(
                java(
                    """
                    package com.android.metalava.test;

                    import java.lang.annotation.*;

                    @Target(ElementType.METHOD)
                    @Retention(RetentionPolicy.SOURCE)
                    public @interface MyAnnotation {
                    }
                    """
                )
            ),
            warnings = "",
            source = """
                package com.android.metalava.test;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.SOURCE)
                @java.lang.annotation.Target(java.lang.annotation.ElementType.METHOD)
                public @interface MyAnnotation {
                }
                """
        )
    }

    @Test
    fun `Functional Interfaces`() {
        checkStubs(
            skipEmitPackages = emptyList(),
            sourceFiles = arrayOf(
                java(
                    """
                    package com.android.metalava.test;

                    @SuppressWarnings("something") @FunctionalInterface
                    public interface MyInterface {
                        void run();
                    }
                    """
                )
            ),
            warnings = "",
            source = """
                package com.android.metalava.test;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                @java.lang.FunctionalInterface
                public interface MyInterface {
                public void run();
                }
                """,
            checkTextStubEquivalence = true
        )
    }

    @Test
    fun `Check writing package info file`() {
        checkStubs(
            format = FileFormat.V2,
            sourceFiles = arrayOf(
                java(
                    """
                    @androidx.annotation.Nullable
                    package test.pkg;
                    """
                ),
                java(
                    """
                    package test.pkg;

                    @SuppressWarnings("all")
                    public class Test {
                    }
                    """
                ),
                androidxNullableSource
            ),
            warnings = "",
            api = """
                package @Nullable test.pkg {
                  public class Test {
                    ctor public Test();
                  }
                }
            """, // WRONG: I should include package annotations in the signature file!
            source = """
                @androidx.annotation.Nullable
                package test.pkg;
                """,
            extraArguments = arrayOf(
                ARG_HIDE_PACKAGE, "androidx.annotation",
                // By default metalava rewrites androidx.annotation.Nullable to
                // android.annotation.Nullable, but the latter does not have target PACKAGE thus
                // fails to compile. This forces stubs keep the androidx annotation.
                ARG_PASS_THROUGH_ANNOTATION, "androidx.annotation.Nullable"
            )
        )
    }

    @Test
    fun `Test package-info documentation`() {
        check(
            sourceFiles = arrayOf(
                java(
                    """
                      /** My package docs */
                      package test.pkg;
                      """
                ).indented(),
                java("""package test.pkg; public abstract class Class1 { }""")
            ),

            api = """
                package test.pkg {
                  public abstract class Class1 {
                    ctor public Class1();
                  }
                }
                """,
            stubFiles = arrayOf(
                java(
                    """
                    /** My package docs */
                    package test.pkg;
                    """
                ),
                java(
                    """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public abstract class Class1 {
                    public Class1() { throw new RuntimeException("Stub!"); }
                    }
                    """
                )
            ),
            docStubs = true
        )
    }

    @Test
    fun `Test package-info annotations`() {
        check(
            sourceFiles = arrayOf(
                java(
                    """
                      @RestrictTo(RestrictTo.Scope.SUBCLASSES)
                      package test.pkg;

                      import androidx.annotation.RestrictTo;
                      """
                ).indented(),
                java("""package test.pkg; public abstract class Class1 { }"""),
                restrictToSource
            ),

            api = """
                package @RestrictTo(androidx.annotation.RestrictTo.Scope.SUBCLASSES) test.pkg {
                  public abstract class Class1 {
                    ctor public Class1();
                  }
                }
                """,
            stubFiles = arrayOf(
                java(
                    """
                    package test.pkg;
                    """
                ),
                java(
                    """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public abstract class Class1 {
                    public Class1() { throw new RuntimeException("Stub!"); }
                    }
                    """
                )
            ),
            extraArguments = arrayOf(ARG_HIDE_PACKAGE, "androidx.annotation")
        )
    }

    @Test
    fun `Ensure we emit both deprecated javadoc and annotation with exclude-all-annotations`() {
        check(
            extraArguments = arrayOf(ARG_EXCLUDE_ALL_ANNOTATIONS),
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;
                    public class Foo {
                        /**
                         * @deprecated Use checkPermission instead.
                         */
                        @Deprecated
                        protected boolean inClass(String name) {
                            return false;
                        }
                    }
                    """
                )
            ),
            stubFiles = arrayOf(
                java(
                    """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Foo {
                    public Foo() { throw new RuntimeException("Stub!"); }
                    /**
                     * @deprecated Use checkPermission instead.
                     */
                    @Deprecated
                    protected boolean inClass(java.lang.String name) { throw new RuntimeException("Stub!"); }
                    }
                    """
                )
            )
        )
    }

    @Test
    fun `Ensure we emit runtime and deprecated annotations in stubs with exclude-annotations`() {
        check(
            extraArguments = arrayOf(ARG_EXCLUDE_ALL_ANNOTATIONS),
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;
                    /** @deprecated */
                    @MySourceRetentionAnnotation
                    @MyClassRetentionAnnotation
                    @MyRuntimeRetentionAnnotation
                    @Deprecated
                    public class Foo {
                        private Foo() {}
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    import java.lang.annotation.Retention;
                    import static java.lang.annotation.RetentionPolicy.SOURCE;
                    @Retention(SOURCE)
                    public @interface MySourceRetentionAnnotation {
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    import java.lang.annotation.Retention;
                    import static java.lang.annotation.RetentionPolicy.CLASS;
                    @Retention(CLASS)
                    public @interface MyClassRetentionAnnotation {
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    import java.lang.annotation.Retention;
                    import static java.lang.annotation.RetentionPolicy.RUNTIME;
                    @Retention(RUNTIME)
                    public @interface MyRuntimeRetentionAnnotation {
                    }
                    """
                )
            ),
            stubFiles = arrayOf(
                java(
                    """
                    package test.pkg;
                    /** @deprecated */
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    @Deprecated
                    @test.pkg.MyRuntimeRetentionAnnotation
                    public class Foo {
                    Foo() { throw new RuntimeException("Stub!"); }
                    }
                    """
                )
            )
        )
    }

    @Test
    fun `Ensure we include class and runtime and not source annotations in stubs with include-annotations`() {
        check(
            extraArguments = arrayOf("--include-annotations"),
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;
                    /** @deprecated */
                    @MySourceRetentionAnnotation
                    @MyClassRetentionAnnotation
                    @MyRuntimeRetentionAnnotation
                    @Deprecated
                    public class Foo {
                        private Foo() {}
                        protected int foo;
                        public void bar();
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    import java.lang.annotation.Retention;
                    import static java.lang.annotation.RetentionPolicy.SOURCE;
                    @Retention(SOURCE)
                    public @interface MySourceRetentionAnnotation {
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    import java.lang.annotation.Retention;
                    import static java.lang.annotation.RetentionPolicy.CLASS;
                    @Retention(CLASS)
                    public @interface MyClassRetentionAnnotation {
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    import java.lang.annotation.Retention;
                    import static java.lang.annotation.RetentionPolicy.RUNTIME;
                    @Retention(RUNTIME)
                    public @interface MyRuntimeRetentionAnnotation {
                    }
                    """
                )
            ),
            stubFiles = arrayOf(
                java(
                    """
                    package test.pkg;
                    /** @deprecated */
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    @Deprecated
                    @test.pkg.MyClassRetentionAnnotation
                    @test.pkg.MyRuntimeRetentionAnnotation
                    public class Foo {
                    Foo() { throw new RuntimeException("Stub!"); }
                    @Deprecated
                    public void bar() { throw new RuntimeException("Stub!"); }
                    @Deprecated protected int foo;
                    }
                    """
                )
            )
        )
    }

    @Test
    fun `Generate stubs with --exclude-documentation-from-stubs`() {
        checkStubs(
            extraArguments = arrayOf(ARG_EXCLUDE_DOCUMENTATION_FROM_STUBS),
            sourceFiles = arrayOf(
                java(
                    """
                    /*
                     * This is the copyright header.
                     */

                    package test.pkg;

                    /** This is the documentation for the class */
                    public class Foo {

                        /** My field doc */
                        protected static final String field = "a\nb\n\"test\"";

                        /**
                         * Method documentation.
                         */
                        protected static void onCreate(String parameter1) {
                            // This is not in the stub
                            System.out.println(parameter1);
                        }
                    }
                    """
                )
            ),
            // Excludes javadoc because of ARG_EXCLUDE_DOCUMENTATION_FROM_STUBS:
            source = """
                /*
                 * This is the copyright header.
                 */
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public class Foo {
                public Foo() { throw new RuntimeException("Stub!"); }
                protected static void onCreate(java.lang.String parameter1) { throw new RuntimeException("Stub!"); }
                protected static final java.lang.String field = "a\nb\n\"test\"";
                }
                """
        )
    }

    @Test
    fun `Generate documentation stubs with --exclude-documentation-from-stubs`() {
        checkStubs(
            extraArguments = arrayOf(ARG_EXCLUDE_DOCUMENTATION_FROM_STUBS),
            sourceFiles = arrayOf(
                java(
                    """
                    /*
                     * This is the copyright header.
                     */

                    package test.pkg;

                    /** This is the documentation for the class */
                    public class Foo {

                        /** My field doc */
                        protected static final String field = "a\nb\n\"test\"";

                        /**
                         * Method documentation.
                         */
                        protected static void onCreate(String parameter1) {
                            // This is not in the stub
                            System.out.println(parameter1);
                        }
                    }
                    """
                )
            ),
            docStubs = true,
            // Includes javadoc despite ARG_EXCLUDE_DOCUMENTATION_FROM_STUBS, because of docStubs:
            source = """
                /*
                 * This is the copyright header.
                 */
                package test.pkg;
                /** This is the documentation for the class */
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public class Foo {
                public Foo() { throw new RuntimeException("Stub!"); }
                /**
                 * Method documentation.
                 */
                protected static void onCreate(java.lang.String parameter1) { throw new RuntimeException("Stub!"); }
                /** My field doc */
                protected static final java.lang.String field = "a\nb\n\"test\"";
                }
                """
        )
    }

    @Test
    fun `Annotation nested rewriting`() {
        checkStubs(
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;

                    import android.view.Gravity;

                    public class ActionBar {
                        @ViewDebug.ExportedProperty(category = "layout", mapping = {
                                @ViewDebug.IntToString(from =  -1,                       to = "NONE"),
                                @ViewDebug.IntToString(from = Gravity.NO_GRAVITY,        to = "NONE"),
                                @ViewDebug.IntToString(from = Gravity.TOP,               to = "TOP"),
                                @ViewDebug.IntToString(from = Gravity.BOTTOM,            to = "BOTTOM"),
                        })
                        public int gravity = Gravity.NO_GRAVITY;
                    }
                    """
                ),
                java(
                    """
                    package android.view;

                    public class Gravity {
                        public static final int NO_GRAVITY = 0;
                        public static final int TOP = 1;
                        public static final int BOTTOM = 2;
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;

                    import java.lang.annotation.ElementType;
                    import java.lang.annotation.Retention;
                    import java.lang.annotation.RetentionPolicy;
                    import java.lang.annotation.Target;

                    public class ViewDebug {
                        @Target({ElementType.FIELD, ElementType.METHOD})
                        @Retention(RetentionPolicy.RUNTIME)
                        public @interface ExportedProperty {
                            boolean resolveId() default false;
                            IntToString[] mapping() default {};
                            IntToString[] indexMapping() default {};
                            boolean deepExport() default false;
                            String prefix() default "";
                            String category() default "";
                            boolean formatToHexString() default false;
                            boolean hasAdjacentMapping() default false;
                        }
                        @Target({ElementType.TYPE})
                        @Retention(RetentionPolicy.RUNTIME)
                        public @interface IntToString {
                            int from();
                            String to();
                        }
                    }
                    """
                )
            ),
            source = """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public class ActionBar {
                public ActionBar() { throw new RuntimeException("Stub!"); }
                @test.pkg.ViewDebug.ExportedProperty(category="layout", mapping={@test.pkg.ViewDebug.IntToString(from=0xffffffff, to="NONE"), @test.pkg.ViewDebug.IntToString(from=android.view.Gravity.NO_GRAVITY, to="NONE"), @test.pkg.ViewDebug.IntToString(from=android.view.Gravity.TOP, to="TOP"), @test.pkg.ViewDebug.IntToString(from=android.view.Gravity.BOTTOM, to="BOTTOM")}) public int gravity = 0; // 0x0
                }
                """
        )
    }

    @Test(expected = FileNotFoundException::class)
    fun `Test update-api should not generate stubs`() {
        check(
            format = FileFormat.V2,
            extraArguments = arrayOf(
                ARG_UPDATE_API,
                ARG_EXCLUDE_ALL_ANNOTATIONS
            ),
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;
                    public class Foo {
                        /**
                         * @deprecated Use checkPermission instead.
                         */
                        @Deprecated
                        protected boolean inClass(String name) {
                            return false;
                        }
                    }
                    """
                )
            ),
            api = """
            package test.pkg {
              public class Foo {
                ctor public Foo();
                method @Deprecated protected boolean inClass(String);
              }
            }
            """,
            stubFiles = arrayOf(
                source(
                    "test/pkg/Foo.java",
                    "This file should not be generated since --update-api is supplied."
                )
            )
        )
    }

    @Test(expected = AssertionError::class)
    fun `Test check-api should not generate stubs or API files`() {
        check(
            extraArguments = arrayOf(
                ARG_CHECK_API,
                ARG_EXCLUDE_ALL_ANNOTATIONS
            ),
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;
                    public class Foo {
                        /**
                         * @deprecated Use checkPermission instead.
                         */
                        @Deprecated
                        protected boolean inClass(String name) {
                            return false;
                        }
                    }
                    """
                )
            ),
            api = """
            package test.pkg {
              public class Foo {
                ctor public Foo();
                method @Deprecated protected boolean inClass(String);
              }
            }
            """
        )
    }

    @Test
    fun `Include package private classes referenced from public API`() {
        // Real world example: android.net.http.Connection in apache-http referenced from RequestHandle
        check(
            format = FileFormat.V2,
            expectedIssues = """
                src/test/pkg/PublicApi.java:4: error: Class test.pkg.HiddenType is not public but was referenced (as return type) from public method test.pkg.PublicApi.getHiddenType() [ReferencesHidden]
                src/test/pkg/PublicApi.java:5: error: Class test.pkg.HiddenType4 is hidden but was referenced (as return type) from public method test.pkg.PublicApi.getHiddenType4() [ReferencesHidden]
                src/test/pkg/PublicApi.java:5: warning: Method test.pkg.PublicApi.getHiddenType4 returns unavailable type HiddenType4 [UnavailableSymbol]
                src/test/pkg/PublicApi.java:4: warning: Method test.pkg.PublicApi.getHiddenType() references hidden type test.pkg.HiddenType. [HiddenTypeParameter]
                src/test/pkg/PublicApi.java:5: warning: Method test.pkg.PublicApi.getHiddenType4() references hidden type test.pkg.HiddenType4. [HiddenTypeParameter]
                """,
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;

                    public class PublicApi {
                        public HiddenType getHiddenType() { return null; }
                        public HiddenType4 getHiddenType4() { return null; }
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;

                    public class PublicInterface {
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;

                    // Class exposed via public api above
                    final class HiddenType extends HiddenType2 implements HiddenType3, PublicInterface {
                        HiddenType(int i1, int i2) { }
                        public HiddenType2 getHiddenType2() { return null; }
                        public int field;
                        @Override public String toString() { return "hello"; }
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;

                    /** @hide */
                    public class HiddenType4 {
                        void foo();
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;

                    // Class not exposed; only referenced from HiddenType
                    class HiddenType2 {
                        HiddenType2(float f) { }
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;

                    // Class not exposed; only referenced from HiddenType
                    interface HiddenType3 {
                    }
                    """
                )
            ),
            api = """
                package test.pkg {
                  public class PublicApi {
                    ctor public PublicApi();
                    method public test.pkg.HiddenType getHiddenType();
                    method public test.pkg.HiddenType4 getHiddenType4();
                  }
                  public class PublicInterface {
                    ctor public PublicInterface();
                  }
                }
                """,
            stubFiles = arrayOf(
                java(
                    """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class PublicApi {
                    public PublicApi() { throw new RuntimeException("Stub!"); }
                    public test.pkg.HiddenType getHiddenType() { throw new RuntimeException("Stub!"); }
                    public test.pkg.HiddenType4 getHiddenType4() { throw new RuntimeException("Stub!"); }
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class PublicInterface {
                    public PublicInterface() { throw new RuntimeException("Stub!"); }
                    }
                    """
                )
            )
        )
    }

    @Test
    fun `Include hidden inner classes referenced from public API`() {
        // Real world example: hidden android.car.vms.VmsOperationRecorder.Writer in android.car-system-stubs
        // referenced from outer class constructor
        check(
            format = FileFormat.V2,
            expectedIssues = """
                src/test/pkg/PublicApi.java:4: error: Class test.pkg.PublicApi.HiddenInner is hidden but was referenced (as parameter type) from public parameter inner in test.pkg.PublicApi(test.pkg.PublicApi.HiddenInner inner) [ReferencesHidden]
                src/test/pkg/PublicApi.java:4: warning: Parameter inner references hidden type test.pkg.PublicApi.HiddenInner. [HiddenTypeParameter]
                """,
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;

                    public class PublicApi {
                        public PublicApi(HiddenInner inner) { }
                        /** @hide */
                        public static class HiddenInner {
                           public void someHiddenMethod(); // should not be in stub
                        }
                    }
                    """
                )
            ),
            api = """
                package test.pkg {
                  public class PublicApi {
                    ctor public PublicApi(test.pkg.PublicApi.HiddenInner);
                  }
                }
                """,
            stubFiles = arrayOf(
                java(
                    """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class PublicApi {
                    public PublicApi(test.pkg.PublicApi.HiddenInner inner) { throw new RuntimeException("Stub!"); }
                    }
                    """
                )
            )
        )
    }

    @Test
    fun `Use type argument in constructor cast`() {
        check(
            format = FileFormat.V2,
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;
                    /** @deprecated */
                    @Deprecated
                    public class BasicPoolEntryRef extends WeakRef<BasicPoolEntry> {
                        public BasicPoolEntryRef(BasicPoolEntry entry) {
                            super(entry);
                        }
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;

                    public class WeakRef<T> {
                        public WeakRef(T foo) {
                        }
                        // need to have more than one constructor to trigger casts in stubs
                        public WeakRef(T foo, int size) {
                        }
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;

                    public class BasicPoolEntry {
                    }
                    """
                )
            ),
            api = """
                package test.pkg {
                  public class BasicPoolEntry {
                    ctor public BasicPoolEntry();
                  }
                  @Deprecated public class BasicPoolEntryRef extends test.pkg.WeakRef<test.pkg.BasicPoolEntry> {
                    ctor @Deprecated public BasicPoolEntryRef(test.pkg.BasicPoolEntry);
                  }
                  public class WeakRef<T> {
                    ctor public WeakRef(T);
                    ctor public WeakRef(T, int);
                  }
                }
                """,
            stubFiles = arrayOf(
                java(
                    """
                    package test.pkg;
                    /** @deprecated */
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    @Deprecated
                    public class BasicPoolEntryRef extends test.pkg.WeakRef<test.pkg.BasicPoolEntry> {
                    @Deprecated
                    public BasicPoolEntryRef(test.pkg.BasicPoolEntry entry) { super((test.pkg.BasicPoolEntry)null); throw new RuntimeException("Stub!"); }
                    }
                    """
                )
            )
        )
    }

    @Test
    fun `Regression test for 116777737`() {
        // Regression test for 116777737: Stub generation broken for Bouncycastle
        // """
        //    It appears as though metalava does not handle the case where:
        //    1) class Alpha extends Beta<Orange>.
        //    2) class Beta<T> extends Charlie<T>.
        //    3) class Beta is hidden.
        //
        //    It should result in a stub where Alpha extends Charlie<Orange> but
        //    instead results in a stub where Alpha extends Charlie<T>, so the
        //    type substitution of Orange for T is lost.
        // """
        check(
            expectedIssues = "src/test/pkg/Alpha.java:2: warning: Public class test.pkg.Alpha stripped of unavailable superclass test.pkg.Beta [HiddenSuperclass]",
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;
                    public class Orange {
                        private Orange() { }
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    public class Alpha extends Beta<Orange> {
                        private Alpha() { }
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    /** @hide */
                    public class Beta<T> extends Charlie<T> {
                        private Beta() { }
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    public class Charlie<T> {
                        private Charlie() { }
                    }
                    """
                )
            ),
            api = """
                package test.pkg {
                  public class Alpha extends test.pkg.Charlie<test.pkg.Orange> {
                  }
                  public class Charlie<T> {
                  }
                  public class Orange {
                  }
                }
                """,
            stubFiles = arrayOf(
                java(
                    """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Orange {
                    Orange() { throw new RuntimeException("Stub!"); }
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Alpha extends test.pkg.Charlie<test.pkg.Orange> {
                    Alpha() { throw new RuntimeException("Stub!"); }
                    }
                    """
                )
            )
        )
    }

    @Test
    fun `Regression test for 124333557`() {
        // Regression test for 124333557: Handle empty java files
        check(
            expectedIssues = """
            TESTROOT/src/test/Something2.java: error: metalava was unable to determine the package name. This usually means that a source file was where the directory does not seem to match the package declaration; we expected the path TESTROOT/src/test/Something2.java to end with /test/wrong/Something2.java [IoError]
            TESTROOT/src/test/Something2.java: error: metalava was unable to determine the package name. This usually means that a source file was where the directory does not seem to match the package declaration; we expected the path TESTROOT/src/test/Something2.java to end with /test/wrong/Something2.java [IoError]
            """,
            sourceFiles = arrayOf(
                java(
                    "src/test/pkg/Something.java",
                    """
                    /** Nothing much here */
                    """
                ),
                java(
                    "src/test/pkg/Something2.java",
                    """
                    /** Nothing much here */
                    package test.pkg;
                    """
                ),
                java(
                    "src/test/Something2.java",
                    """
                    /** Wrong package */
                    package test.wrong;
                    """
                ),
                java(
                    """
                    package test.pkg;
                    public class Test {
                        private Test() { }
                    }
                    """
                )
            ),
            api = """
                package test.pkg {
                  public class Test {
                  }
                }
                """,
            projectSetup = { dir ->
                // Make sure we handle blank/doc-only java doc files in root extraction
                val src = listOf(File(dir, "src"))
                val files = gatherSources(src)
                val roots = extractRoots(files)
                assertEquals(1, roots.size)
                assertEquals(src[0].path, roots[0].path)
            }
        )
    }

    @Test
    fun `Basic Kotlin stubs`() {
        check(
            extraArguments = arrayOf(
                ARG_KOTLIN_STUBS
            ),
            sourceFiles = arrayOf(
                kotlin(
                    """
                    /* My file header */
                    // Another comment
                    @file:JvmName("Driver")
                    package test.pkg
                    /** My class doc */
                    class Kotlin(
                        val property1: String = "Default Value",
                        arg2: Int
                    ) : Parent() {
                        override fun method() = "Hello World"
                        /** My method doc */
                        fun otherMethod(ok: Boolean, times: Int) {
                        }

                        /** property doc */
                        var property2: String? = null

                        /** @hide */
                        var hiddenProperty: String? = "hidden"

                        private var someField = 42
                        @JvmField
                        var someField2 = 42
                    }

                    /** Parent class doc */
                    open class Parent {
                        open fun method(): String? = null
                        open fun method2(value1: Boolean, value2: Boolean?): String? = null
                        open fun method3(value1: Int?, value2: Int): Int = null
                    }
                    """
                ),
                kotlin(
                    """
                    package test.pkg
                    open class ExtendableClass<T>
                """
                )
            ),
            stubFiles = arrayOf(
                kotlin(
                    """
                        /* My file header */
                        // Another comment
                        package test.pkg
                        /** My class doc */
                        @file:Suppress("ALL")
                        class Kotlin : test.pkg.Parent() {
                        open fun Kotlin(open property1: java.lang.String!, open arg2: int): test.pkg.Kotlin! = error("Stub!")
                        open fun method(): java.lang.String = error("Stub!")
                        /** My method doc */
                        open fun otherMethod(open ok: boolean, open times: int): void = error("Stub!")
                        }
                    """
                ),
                kotlin(
                    """
                        package test.pkg
                        @file:Suppress("ALL")
                        open class ExtendableClass<T> {
                        open fun ExtendableClass(): test.pkg.ExtendableClass<T!>! = error("Stub!")
                        }
                    """
                )
            )
        )
    }

    @Test
    fun `Extends and implements multiple interfaces in Kotlin Stubs`() {
        check(
            extraArguments = arrayOf(
                ARG_KOTLIN_STUBS
            ),
            sourceFiles = arrayOf(
                kotlin(
                    """
                    package test.pkg
                    class MainClass: MyParentClass(), MyInterface1, MyInterface2
                    
                    open class MyParentClass
                    interface MyInterface1
                    interface MyInterface2
                """
                )
            ),
            stubFiles = arrayOf(
                kotlin(
                    """
                        package test.pkg
                        @file:Suppress("ALL")
                        class MainClass : test.pkg.MyParentClass(), test.pkg.MyInterface1, test.pkg.MyInterface2 {
                        open fun MainClass(): test.pkg.MainClass! = error("Stub!")
                        }
                    """
                )
            )
        )
    }

    @Test
    fun `Extends and implements multiple interfaces`() {
        check(
            checkCompilation = true,
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;

                    public class MainClass extends MyParentClass implements MyInterface1, MyInterface2 {
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;

                    public interface MyInterface1 { }
                    """
                ),
                java(
                    """
                    package test.pkg;

                    public interface MyInterface2 { }
                    """
                ),
                java(
                    """
                    package test.pkg;

                    public class MyParentClass { }
                    """
                )
            ),
            stubFiles = arrayOf(
                java(
                    """
                        package test.pkg;
                        @SuppressWarnings({"unchecked", "deprecation", "all"})
                        public class MainClass extends test.pkg.MyParentClass implements test.pkg.MyInterface1, test.pkg.MyInterface2 {
                        public MainClass() { throw new RuntimeException("Stub!"); }
                        }
                    """
                )
            )
        )
    }

    @Test
    fun `NaN constants`() {
        check(
            checkCompilation = true,
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;

                    public class MyClass {
                        public static final float floatNaN = 0.0f / 0.0f;
                        public static final double doubleNaN = 0.0d / 0.0;
                    }
                    """
                )
            ),
            stubFiles = arrayOf(
                java(
                    """
                        package test.pkg;
                        @SuppressWarnings({"unchecked", "deprecation", "all"})
                        public class MyClass {
                        public MyClass() { throw new RuntimeException("Stub!"); }
                        public static final double doubleNaN = (0.0/0.0);
                        public static final float floatNaN = (0.0f/0.0f);
                        }
                    """
                )
            )
        )
    }

    @Test
    fun `Translate DeprecatedForSdk to Deprecated`() {
        // See b/144111352
        check(
            expectedIssues = """
                src/test/pkg/PublicApi.java:30: error: Method test.pkg.PublicApi.method4(): Documentation contains `@deprecated` which implies this API is fully deprecated, not just @DeprecatedForSdk [DeprecationMismatch]
            """,
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;
                    import android.annotation.DeprecatedForSdk;
                    import android.annotation.DeprecatedForSdk.*;

                    public class PublicApi {
                        private PublicApi() { }
                        // Normal deprecation:
                        /** @deprecated My deprecation reason 1 */
                        @Deprecated
                        public static void method1() { }

                        // Deprecated in the SDK. No comment; make sure annotation comment
                        // shows up in the doc stubs.
                        @DeprecatedForSdk("My deprecation reason 2")
                        public static void method2() { }

                        // Deprecated in the SDK, and has comment: Make sure comments merged
                        // in the doc stubs.
                        /**
                         * My docs here.
                         * @return the value
                         */
                        @DeprecatedForSdk("My deprecation reason 3")
                        public static void method3() { } // warn about missing annotation

                        // Already implicitly deprecated everywhere (because of @deprecated
                        // comment; complain if combined with @DeprecatedForSdk
                        /** @deprecated Something */
                        @DeprecatedForSdk("Something")
                        public static void method4() { }

                        // Test @DeprecatedForSdk with specific exemptions; none of these are
                        // the current public SDK so make sure it's deprecated there.
                        // A different test will check whath appens when generating the
                        // system API or test API.
                        @DeprecatedForSdk(value = "Explanation", allowIn = { SYSTEM_API, TEST_API })
                        public static void method5() { }
                    }
                    """
                ).indented(),
                deprecatedForSdkSource
            ),
            api = """
                package test.pkg {
                  public class PublicApi {
                    method @Deprecated public static void method1();
                    method @Deprecated public static void method2();
                    method @Deprecated public static void method3();
                    method @Deprecated public static void method4();
                    method @Deprecated public static void method5();
                  }
                }
                """,
            stubFiles = arrayOf(
                java(
                    """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class PublicApi {
                    PublicApi() { throw new RuntimeException("Stub!"); }
                    /** @deprecated My deprecation reason 1 */
                    @Deprecated
                    public static void method1() { throw new RuntimeException("Stub!"); }
                    /**
                     * @deprecated My deprecation reason 2
                     */
                    @Deprecated
                    public static void method2() { throw new RuntimeException("Stub!"); }
                    /**
                     * My docs here.
                     * @deprecated My deprecation reason 3
                     * @return the value
                     */
                    @Deprecated
                    public static void method3() { throw new RuntimeException("Stub!"); }
                    /** @deprecated Something */
                    @Deprecated
                    public static void method4() { throw new RuntimeException("Stub!"); }
                    /**
                     * @deprecated Explanation
                     */
                    @Deprecated
                    public static void method5() { throw new RuntimeException("Stub!"); }
                    }
                    """
                )
            ),
            docStubs = true
        )
    }

    @Test
    fun `Translate DeprecatedForSdk with API Filtering`() {
        // See b/144111352.
        // Remaining: don't include @deprecated in the docs for allowed platforms!
        check(
            showAnnotations = arrayOf("android.annotation.SystemApi"),
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;

                    import android.annotation.SystemApi;
                    import android.annotation.TestApi;
                    import android.annotation.DeprecatedForSdk;

                    public class PublicApi2 {
                        private PublicApi2() {
                        }

                        // This method should be deprecated in the SDK but *not* here in
                        // the system API (this test runs with --show-annotations SystemApi)
                        @DeprecatedForSdk(value = "My deprecation reason 1", allowIn = {SystemApi.class, TestApi.class})
                        public static void method1() {
                        }

                        // Same as method 1 (here we're just using a different annotation
                        // initializer form to test we're handling both types): *not* deprecated.

                        /**
                         * My docs.
                         */
                        @DeprecatedForSdk(value = "My deprecation reason 2", allowIn = SystemApi.class)
                        public static void method2() {
                        }

                        // Finally, this method *is* deprecated in the system API and should
                        // show up as such.

                        /**
                         * My docs.
                         */
                        @DeprecatedForSdk(value = "My deprecation reason 3", allowIn = TestApi.class)
                        public static void method3() {
                        }
                    }
                    """
                ).indented(),
                // Include some Kotlin files too to make sure we correctly handle
                // annotation lookup for Kotlin (which uses UAST instead of plain Java PSI
                // behind the scenes), even if android.util.ArrayMap is really implemented in Java
                kotlin(
                    """
                    package android.util
                    import android.annotation.DeprecatedForSdk
                    import android.annotation.SystemApi;
                    import android.annotation.TestApi;

                    @DeprecatedForSdk(value = "Use androidx.collection.ArrayMap")
                    class ArrayMap

                    @DeprecatedForSdk(value = "Use androidx.collection.ArrayMap", allowIn = [SystemApi::class])
                    class SystemArrayMap

                    @DeprecatedForSdk("Use android.Manifest.permission.ACCESS_FINE_LOCATION instead")
                    const val FINE_LOCATION =  "android.permission.ACCESS_FINE_LOCATION"
                    """
                ).indented(),
                deprecatedForSdkSource,
                systemApiSource,
                testApiSource
            ),
            stubFiles = arrayOf(
                java(
                    """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class PublicApi2 {
                    PublicApi2() { throw new RuntimeException("Stub!"); }
                    public static void method1() { throw new RuntimeException("Stub!"); }
                    /**
                     * My docs.
                     */
                    public static void method2() { throw new RuntimeException("Stub!"); }
                    /**
                     * My docs.
                     * @deprecated My deprecation reason 3
                     */
                    @Deprecated
                    public static void method3() { throw new RuntimeException("Stub!"); }
                    }
                    """
                ),
                java(
                    """
                    package android.util;
                    /**
                     * @deprecated Use androidx.collection.ArrayMap
                     */
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    @Deprecated
                    public final class ArrayMap {
                    @Deprecated
                    public ArrayMap() { throw new RuntimeException("Stub!"); }
                    }
                    """
                ),
                // SystemArrayMap is like ArrayMap, but has allowedIn=SystemApi::class, so
                // it should not be deprecated here in the system api stubs
                java(
                    """
                    package android.util;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public final class SystemArrayMap {
                    public SystemArrayMap() { throw new RuntimeException("Stub!"); }
                    }
                    """
                ),
                java(
                    """
                    package android.util;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public final class ArrayMapKt {
                    /**
                     * @deprecated Use android.Manifest.permission.ACCESS_FINE_LOCATION instead
                     */
                    @Deprecated @androidx.annotation.NonNull public static final java.lang.String FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION";
                    }
                    """
                )
            ),
            docStubs = true
        )
    }

    // TODO: Test what happens when a class extends a hidden extends a public in separate packages,
    // and the hidden has a @hide constructor so the stub in the leaf class doesn't compile -- I should
    // check for this and fail build.
}
