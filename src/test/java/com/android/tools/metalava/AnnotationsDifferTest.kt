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

import com.android.tools.metalava.model.text.ApiFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.text.Charsets.UTF_8

class AnnotationsDifferTest {
    @get:Rule
    var temporaryFolder = TemporaryFolder(File(System.getProperty("user.dir")))

    @Test
    fun `Write diff`() {
        options = Options(arrayOf(ARG_CLASS_PATH, DriverTest.getAndroidJar().path))
        val codebase = ApiFile.parseApi(
            "old.txt",
            """
                package test.pkg {
                  public interface Appendable {
                    method public test.pkg.Appendable append(java.lang.CharSequence?);
                    method public test.pkg.Appendable append2(java.lang.CharSequence?);
                    method public java.lang.String! reverse(java.lang.String!);
                  }
                  public interface RandomClass {
                    method public test.pkg.Appendable append(java.lang.CharSequence);
                  }
                }
            """.trimIndent(),
            true
        )

        val codebase2 = ApiFile.parseApi(
            "new.txt",
            """
        package test.pkg {
          public interface Appendable {
            method @androidx.annotation.NonNull public test.pkg.Appendable append(@androidx.annotation.Nullable java.lang.CharSequence);
            method public test.pkg.Appendable append2(java.lang.CharSequence);
          }
        }
            """.trimIndent(),
            false
        )

        val apiFile = temporaryFolder.newFile("diff.txt")
        options = Options(arrayOf(ARG_CLASS_PATH, DriverTest.getAndroidJar().path))
        AnnotationsDiffer(codebase, codebase2).writeDiffSignature(apiFile)
        assertTrue(apiFile.exists())
        val actual = apiFile.readText(UTF_8)
        assertEquals(
            """
            // Signature format: 2.0
            package test.pkg {
              public interface Appendable {
                method @NonNull public test.pkg.Appendable append2(@Nullable CharSequence);
              }
            }
            """.trimIndent(),
            actual.trim()
        )
    }
}
