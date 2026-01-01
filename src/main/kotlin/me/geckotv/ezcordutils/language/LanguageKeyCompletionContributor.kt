package me.geckotv.ezcordutils.language

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import com.jetbrains.python.psi.PyStringLiteralExpression

/**
 * Provides autocomplete for language keys in Python files.
 */
class LanguageKeyCompletionContributor : CompletionContributor() {

    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().inside(PyStringLiteralExpression::class.java),
            LanguageKeyCompletionProvider()
        )
    }
}

class LanguageKeyCompletionProvider : CompletionProvider<CompletionParameters>() {

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val project = parameters.position.project
        val file = parameters.originalFile

        println("[DEBUG Autocomplete] File: ${file.name}")

        // Get the file prefix (e.g., "welcome" from "welcome.py")
        val filePrefix = getFilePrefix(file.name)
        println("[DEBUG Autocomplete] File prefix: $filePrefix")

        // Get all available language keys
        val resolver = LanguageResolver(project)
        val allKeys = resolver.getAllKeys()

        println("[DEBUG Autocomplete] Found ${allKeys.size} total keys")

        // Filter keys based on file prefix if available
        val relevantKeys = if (filePrefix != null) {
            allKeys.filter { it.startsWith("$filePrefix.") || it.startsWith("general.") }
        } else {
            allKeys
        }

        println("[DEBUG Autocomplete] Showing ${relevantKeys.size} relevant keys")

        // Add completion items
        relevantKeys.forEach { fullKey ->
            val translation = resolver.resolve(fullKey)

            // Keep "general." prefix, otherwise remove file prefix
            val displayKey = if (fullKey.startsWith("general.")) {
                fullKey
            } else if (filePrefix != null && fullKey.startsWith("$filePrefix.")) {
                fullKey.removePrefix("$filePrefix.")
            } else {
                fullKey
            }

            println("[DEBUG Autocomplete] Adding key: $displayKey (original: $fullKey) -> $translation")

            val lookupElement = LookupElementBuilder.create(displayKey)
                .withTypeText(translation ?: "⚠️ Not Translated yet", true)
                .bold()

            result.addElement(lookupElement)
        }

        println("[DEBUG Autocomplete] Added ${relevantKeys.size} completion items")
    }

    /**
     * Extracts the prefix from a filename (e.g., "welcome" from "welcome.py").
     * Handles formats like "welcome.py", "welcome.container.py", etc.
     */
    private fun getFilePrefix(filename: String): String? {
        if (!filename.endsWith(".py")) return null

        val nameWithoutExtension = filename.removeSuffix(".py")

        // If the filename contains dots, take everything before the last component
        // e.g., "welcome.container.py" -> "welcome.container"
        // e.g., "welcome.py" -> "welcome"
        return nameWithoutExtension.ifEmpty { null }
    }
}

