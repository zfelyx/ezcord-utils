package me.geckotv.ezcordutils.language

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.python.psi.PyStringLiteralExpression

/**
 * Provides documentation for language keys in Python files.
 * Shows the translated text when hovering over strings like "general.test".
 */
class LanguageDocumentationProvider : AbstractDocumentationProvider() {

    @Suppress("UnstableApiUsage")
    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        println("[DEBUG] generateDoc called! element=$element, originalElement=$originalElement")

        if (element !is PyStringLiteralExpression) {
            println("[DEBUG] Element is not PyStringLiteralExpression, it's: ${element?.javaClass?.name}")
            return null
        }

        println("[DEBUG] Found PyStringLiteralExpression!")
        val project = element.project
        val stringValue = element.stringValue
        println("[DEBUG] String value: '$stringValue'")

        // Check if the string looks like a language key (contains a dot)
        if (!stringValue.contains(".")) {
            println("[DEBUG] String doesn't contain dot, skipping")
            return null
        }

        println("[DEBUG] Resolving language key: '$stringValue'")
        // Resolve the language key
        val resolver = LanguageResolver(project)
        val translatedText = resolver.resolve(stringValue)

        if (translatedText == null) {
            println("[DEBUG] Could not resolve key '$stringValue'")
            return null
        }

        println("[DEBUG] ✅ Resolved language key '$stringValue' to '$translatedText'")

        // Format the documentation
        return buildDocumentation(stringValue, translatedText)
    }

    override fun getCustomDocumentationElement(
        editor: Editor,
        file: PsiFile,
        contextElement: PsiElement?,
        targetOffset: Int
    ): PsiElement? {
        // Return the element if it's a Python string literal
        return if (contextElement is PyStringLiteralExpression) contextElement else null
    }

    /**
     * Builds the HTML documentation to display.
     */
    private fun buildDocumentation(key: String, translation: String): String {
        return buildString {
            append("<div class='definition'><pre>")
            append("<b>Language Key:</b> $key")
            append("</pre></div>")
            append("<div class='content'>")
            append("<p><b>Translation:</b></p>")
            append("<p style='margin-left: 10px; font-style: italic;'>")
            append(escapeHtml(translation))
            append("</p>")
            append("</div>")
        }
    }

    /**
     * Escapes HTML special characters.
     */
    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}

