package me.geckotv.ezcordutils.language

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiElement
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.awt.RelativePoint
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyStringLiteralExpression
import me.geckotv.ezcordutils.utils.LanguageUtils
import org.jetbrains.yaml.psi.YAMLKeyValue
import java.awt.event.MouseEvent

data class KeyUsageLocation(
    val file: PyFile,
    val element: PyStringLiteralExpression,
    val lineNumber: Int
)

class YamlKeyUsageLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element !is YAMLKeyValue) return null
        if (!element.isValid) return null

        val value = element.value
        if (value is org.jetbrains.yaml.psi.YAMLMapping) return null

        val key = element.key ?: return null

        try {
            val fullKey = buildKeyPath(element)
            if (fullKey.isEmpty()) return null

            val project = element.project

            val usages = findUsagesForBothVariants(project, fullKey)

            if (usages.isEmpty()) return null

            return LineMarkerInfo(
                key,
                key.textRange,
                AllIcons.Gutter.ImplementingMethod,
                { createTooltip(fullKey, usages) },
                { event, _ -> handleNavigationClick(event, project, usages) },
                GutterIconRenderer.Alignment.RIGHT,
                { "Show usages" }
            )
        } catch (e: Exception) {
            return null
        }
    }

    private fun buildKeyPath(keyValue: YAMLKeyValue): String {
        val parts = mutableListOf<String>()
        var current: PsiElement? = keyValue

        while (current is YAMLKeyValue) {
            parts.add(0, current.keyText)
            current = current.parent?.parent
        }

        return parts.joinToString(".")
    }

    private fun findUsagesForBothVariants(project: Project, fullKey: String): List<KeyUsageLocation> {
        val allUsages = mutableSetOf<KeyUsageLocation>()

        allUsages.addAll(findUsagesInPythonFiles(project, fullKey, fullKey, null))

        if (fullKey.contains(".")) {
            val filePrefix = fullKey.substringBefore(".")
            val keyWithoutPrefix = fullKey.substringAfter(".")

            allUsages.addAll(findUsagesInPythonFiles(project, keyWithoutPrefix, fullKey, filePrefix))
        }

        return allUsages.toList()
    }

    private fun findUsagesInPythonFiles(
        project: Project,
        searchKey: String,
        originalKey: String,
        requiredFilePrefix: String?
    ): List<KeyUsageLocation> {
        val results = mutableListOf<KeyUsageLocation>()
        val utils = LanguageUtils()

        try {
            ApplicationManager.getApplication().runReadAction {
                val scope = GlobalSearchScope.projectScope(project)
                val pythonFiles = FileTypeIndex.getFiles(PythonFileType.INSTANCE, scope)

                val searchPatterns = listOf(
                    searchKey,
                    "{$searchKey}"
                )

                val languagePrefixes = utils.findLanguagePrefixes(project)

                val filteredFiles = pythonFiles.filter { vFile ->
                    if (requiredFilePrefix != null) {
                        val filePrefix = utils.getFilePrefix(vFile.name) ?: return@filter false

                        val matches = filePrefix == requiredFilePrefix ||
                                filePrefix.startsWith("$requiredFilePrefix.")

                        if (!matches) return@filter false
                    }

                    val doc = FileDocumentManager.getInstance().getDocument(vFile)
                    val text = doc?.text ?: ""
                    searchPatterns.any { pattern -> text.contains(pattern) }
                }

                for (vFile in filteredFiles) {
                    try {
                        val psiFile = com.intellij.psi.PsiManager.getInstance(project)
                            .findFile(vFile) as? PyFile

                        if (psiFile == null || !psiFile.isValid) continue

                        val filePrefix = utils.getFilePrefix(vFile.name)
                        val doc = com.intellij.psi.PsiDocumentManager.getInstance(project)
                            .getDocument(psiFile)

                        if (doc == null) continue

                        psiFile.accept(object : com.intellij.psi.PsiRecursiveElementWalkingVisitor() {
                            override fun visitElement(element: PsiElement) {
                                super.visitElement(element)

                                if (element is PyStringLiteralExpression && element.isValid) {
                                    val text = utils.extractStringValue(element)

                                    if (isKeyMatch(text, searchKey, originalKey, filePrefix, requiredFilePrefix)) {
                                        val line = doc.getLineNumber(element.textRange.startOffset)
                                        results.add(KeyUsageLocation(psiFile, element, line))
                                    }
                                }
                            }
                        })
                    } catch (e: Exception) {
                        // Skip problematic files
                    }
                }
            }
        } catch (e: Exception) {
            // Handle read action errors
        }

        return results
    }

    private fun isKeyMatch(
        text: String,
        searchKey: String,
        originalKey: String,
        filePrefix: String?,
        requiredFilePrefix: String?
    ): Boolean {
        val keysInText = LanguageUtils.findAllKeysInString(text, filePrefix)

        if (keysInText.contains(searchKey) || keysInText.contains(originalKey)) {
            return true
        }
        if (requiredFilePrefix != null && filePrefix == requiredFilePrefix) {
            val fullKeyVariant = "$filePrefix.$searchKey"
            if (keysInText.contains(fullKeyVariant)) {
                return true
            }
        }
        if (searchKey != originalKey && originalKey.contains(".")) {
            if (text == searchKey || text == "{$searchKey}") {
                return true
            }
        }

        return false
    }

    private fun createTooltip(key: String, usages: List<KeyUsageLocation>): String {
        val lines = usages.take(5).map { "${it.file.name}:${it.lineNumber + 1}" }
        val extra = if (usages.size > 5) "\n... and ${usages.size - 5} more" else ""
        return "Key '$key' used in ${usages.size} location(s):\n" +
                lines.joinToString("\n") { "  • $it" } + extra
    }

    private fun handleNavigationClick(
        event: MouseEvent,
        project: Project,
        usages: List<KeyUsageLocation>
    ) {
        when (usages.size) {
            0 -> return
            1 -> navigateTo(project, usages[0])
            else -> showPopup(event, project, usages)
        }
    }

    private fun navigateTo(project: Project, usage: KeyUsageLocation) {
        try {
            val desc = com.intellij.openapi.fileEditor.OpenFileDescriptor(
                project,
                usage.file.virtualFile,
                usage.lineNumber,
                0
            )
            desc.navigate(true)
        } catch (e: Exception) {
            // Ignore navigation errors
        }
    }

    private fun showPopup(event: MouseEvent, project: Project, usages: List<KeyUsageLocation>) {
        val limitedUsages = usages.take(20)
        val items: List<KeyUsageLocation> = limitedUsages

        val title = if (usages.size > 20) "Select Usage Location (showing first 20 of ${usages.size})" else "Select Usage Location"

        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(items)
            .setTitle(title)
            .setItemChosenCallback { navigateTo(project, it) }
            .createPopup()
            .show(RelativePoint(event))
    }
}