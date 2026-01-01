package me.geckotv.ezcordutils.language

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager

/**
 * Listens for file opening events and automatically loads language keys.
 */
class LanguageFileEditorListener(private val project: Project) : FileEditorManagerListener {

    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        println("[DEBUG FileListener] File opened: ${file.name}")

        // Only process Python files
        if (!file.name.endsWith(".py")) {
            println("[DEBUG FileListener] Not a Python file, skipping")
            return
        }

        // Get the file prefix
        val filePrefix = file.nameWithoutExtension
        println("[DEBUG FileListener] File prefix: $filePrefix")

        // Get all keys for this file
        val resolver = LanguageResolver(project)
        val allKeys = resolver.getAllKeys()

        // Filter keys based on file prefix
        val relevantKeys = allKeys.filter { it.startsWith("$filePrefix.") }
        println("[DEBUG FileListener] Found ${relevantKeys.size} relevant keys for prefix '$filePrefix'")

        if (relevantKeys.isEmpty()) {
            println("[DEBUG FileListener] No keys found for this file")
            return
        }

        // Create a summary of all translations
        val translations = mutableMapOf<String, String>()

        relevantKeys.forEach { fullKey ->
            val shortKey = fullKey.removePrefix("$filePrefix.")
            val allTranslations = resolver.resolveAllLanguages(fullKey)

            // Use the default language or first available
            val translation = allTranslations.values.firstOrNull() ?: "No translation"
            translations[shortKey] = translation
        }

        println("[DEBUG FileListener] Prepared ${translations.size} translations")

        // Update the tool window
        updateLanguageKeyToolWindow("$filePrefix.*", translations)

        // Show the tool window
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Language Keys")
        toolWindow?.show {
            println("[DEBUG FileListener] Tool window shown with file keys")
        }
    }
}

