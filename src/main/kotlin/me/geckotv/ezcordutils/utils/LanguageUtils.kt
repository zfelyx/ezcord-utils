package me.geckotv.ezcordutils.utils

import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.FilenameIndex
import com.jetbrains.python.psi.PyStringLiteralExpression
import me.geckotv.ezcordutils.language.LanguageKeyLocation
import me.geckotv.ezcordutils.language.LanguageResolver

class LanguageUtils {
    /**
     * Extracts the string value from a PyStringLiteralExpression.
     * Uses stable PSI API by removing quotes from text property.
     */
    fun extractStringValue(pyString: PyStringLiteralExpression): String {
        return pyString.text.trim().removeSurrounding("\"").removeSurrounding("'")
    }

    /**
     * Checks if a string value is a valid language key candidate.
     * A valid key must not be blank and must contain a dot.
     */
    fun isValidLanguageKey(stringValue: String): Boolean {
        return stringValue.isNotBlank() && stringValue.contains('.')
    }

    /**
     * Extracts the prefix from a filename (e.g., "welcome" from "welcome.py" or "welcome.container.py").
     * For python files we return the part before the first dot (so "welcome.container.py" -> "welcome").
     */
    fun getFilePrefix(filename: String): String? {
        if (!filename.endsWith(".py")) return null

        val nameWithoutExtension = filename.removeSuffix(".py")
        val prefix = nameWithoutExtension.substringBefore('.') // take first component before any further dots
        return prefix.ifEmpty { null }
    }

    /**
     * Navigates to a specific line in a file.
     *
     * @param project The current project.
     * @param file The file to navigate to.
     * @param lineNumber The line number to navigate to (0-based).
     * @return True if navigation was successful, false otherwise.
     */
    fun gotoLine(project: Project, file: VirtualFile, lineNumber: Int): Boolean {
        return try {
            val descriptor = OpenFileDescriptor(project, file, lineNumber, 0)
            descriptor.navigate(true)
            true
        } catch (e: Exception) {
            println("[DEBUG LanguageUtils] ❌ Error navigating to line: ${e.message}")
            false
        }
    }

    /**
     * Tries to resolve a language key with and without file prefix.
     *
     * @param key The key to resolve.
     * @param filePrefix The optional file prefix (e.g., "welcome").
     * @param resolver The language resolver instance.
     * @return Pair of (resolved key, location) or null if not found.
     */
    fun resolveKeyWithPrefix(
        key: String,
        filePrefix: String?,
        resolver: LanguageResolver
    ): Pair<String, LanguageKeyLocation>? {
        // Try direct resolution first - check in primary language
        var location = resolver.getKeyLocation(key)
        if (location != null) {
            return Pair(key, location)
        }

        // If not found in primary language, check if it exists in ANY language
        val allLanguages = resolver.resolveAllLanguages(key)

        if (allLanguages.isNotEmpty()) {
            // Key exists in at least one language, create a dummy location
            val firstLangWithKey = resolver.getKeyLocationInAnyLanguage(key)
            if (firstLangWithKey != null) {
                return Pair(key, firstLangWithKey)
            }
        }

        // Try with file prefix
        if (filePrefix != null) {
            val fullKey = "$filePrefix.$key"

            // Try primary language first
            location = resolver.getKeyLocation(fullKey)
            if (location != null) {
                return Pair(fullKey, location)
            }

            // Try all languages
            val allLanguagesWithPrefix = resolver.resolveAllLanguages(fullKey)

            if (allLanguagesWithPrefix.isNotEmpty()) {
                val firstLangWithKey = resolver.getKeyLocationInAnyLanguage(fullKey)
                if (firstLangWithKey != null) {
                    return Pair(fullKey, firstLangWithKey)
                }
            }
        }

        return null
    }

    /**
     * Finds all language keys in a string value (with resolution).
     * Supports both direct keys ("general.test") and {key} patterns ("{embed.title}").
     *
     * @param stringValue The string to search in.
     * @param filePrefix The optional file prefix.
     * @param resolver The language resolver instance.
     * @return List of (resolved key, location) pairs.
     */
    fun findAllKeysInString(
        stringValue: String,
        filePrefix: String?,
        resolver: LanguageResolver
    ): List<Pair<String, LanguageKeyLocation>> {
        val foundKeys = mutableListOf<Pair<String, LanguageKeyLocation>>()

        // Check if string contains {key} pattern(s)
        val keyPattern = Regex("""[{]([a-zA-Z0-9_.]+)[}]""")
        val matches = keyPattern.findAll(stringValue).toList()


        if (matches.isNotEmpty()) {
            // Extract all keys from {key} format
            for (match in matches) {
                val extractedKey = match.groupValues[1]
                val resolved = resolveKeyWithPrefix(extractedKey, filePrefix, resolver)
                if (resolved != null) {
                    foundKeys.add(resolved)
                }
            }
        } else {
            // No {key} pattern found, try direct string value
            val resolved = resolveKeyWithPrefix(stringValue, filePrefix, resolver)
            if (resolved != null) {
                foundKeys.add(resolved)
            }
        }

        return foundKeys
    }

    /**
     * Scans the project for YAML language files and returns their base filenames (without extension)
     * as possible language prefixes (e.g. "welcome" for "welcome.yml").
     */
    fun findLanguagePrefixes(project: Project): Set<String> {
        val prefixes = mutableSetOf<String>()
        try {
            val scope = GlobalSearchScope.projectScope(project)
            val ymlFiles = FilenameIndex.getAllFilesByExt(project, "yml", scope)
            val yamlFiles = FilenameIndex.getAllFilesByExt(project, "yaml", scope)
            val files = ymlFiles + yamlFiles
            for (vFile in files) {
                val name = vFile.name
                val base = name.substringBeforeLast('.') // "welcome.yml" -> "welcome"
                if (base.isNotBlank()) prefixes.add(base)
            }
        } catch (e: Exception) {
            // ignore indexing/read errors, return whatever found
        }
        return prefixes
    }

    companion object {
    @JvmStatic
        fun findAllKeysInString(stringValue: String, filePrefix: String?): Set<String> {
            val keys = mutableSetOf<String>()

            val keyPattern = Regex("""[{]([a-zA-Z0-9_.]+)[}]""")
            val matches = keyPattern.findAll(stringValue).toList()

            if (matches.isNotEmpty()) {
                for (match in matches) {
                    val extractedKey = match.groupValues[1]
                    keys.add(extractedKey)

                    if (filePrefix != null && !extractedKey.startsWith("$filePrefix.")) {
                        keys.add("$filePrefix.$extractedKey")
                    }
                }
            } else if (stringValue.contains('.')) {
                keys.add(stringValue)

                if (filePrefix != null && !stringValue.startsWith("$filePrefix.")) {
                    keys.add("$filePrefix.$stringValue")
                }
            }

            return keys
        }
    }
}