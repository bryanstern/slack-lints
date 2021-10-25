/*
 * Copyright (C) 2021 Slack Technologies, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package slack.lint.inclusive

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UFile
import org.jetbrains.uast.ULabeledExpression
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.java.JavaUField
import org.jetbrains.uast.kotlin.KotlinUField
import org.jetbrains.uast.kotlin.declarations.KotlinUMethod

@Suppress("UnstableApiUsage")
class InclusiveNamingSourceCodeScanner : Detector(), SourceCodeScanner {

  private lateinit var blocklist: Set<String>

  override fun beforeCheckRootProject(context: Context) {
    super.beforeCheckRootProject(context)
    blocklist = InclusiveNamingChecker.loadBlocklist(context)
  }

  override fun getApplicableUastTypes() = listOf(
    UFile::class.java,
    UClass::class.java,
    UMethod::class.java,
    UVariable::class.java,
    ULabeledExpression::class.java
  )

  override fun createUastHandler(context: JavaContext): UElementHandler? {
    if (blocklist.isEmpty()) return null
    val checker = InclusiveNamingChecker.SourceCodeChecker(context, blocklist)
    context.uastFile?.let { uastFile ->
      checker.check(uastFile, context.file.name, "file", isFile = true)
    }
    return object : UElementHandler() {
      override fun visitFile(node: UFile) {
        checker.check(node, node.packageName, "package")
      }

      override fun visitClass(node: UClass) {
        checker.check(node, node.name, "class")
      }

      override fun visitMethod(node: UMethod) {
        if (node.isConstructor) return
        val type = if (node is KotlinUMethod) "function" else "method"
        checker.check(node, node.name, type)
      }

      // Covers parameters, properties, fields, and local vars
      override fun visitVariable(node: UVariable) {
        val type = when (node) {
          is JavaUField -> "field"
          is KotlinUField -> "property"
          is ULocalVariable -> "local variable"
          is UParameter -> {
            if (node.sourcePsi is KtProperty) {
              "property"
            } else {
              "parameter"
            }
          }
          else -> return
        }
        checker.check(node, node.name, type)
      }

      // Covers things like forEach label@ {}
      override fun visitLabeledExpression(node: ULabeledExpression) {
        checker.check(node, node.label, "label")
      }
    }
  }
}