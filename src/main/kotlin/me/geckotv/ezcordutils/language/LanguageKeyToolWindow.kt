package me.geckotv.ezcordutils.language

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import org.jetbrains.jewel.bridge.addComposeTab
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.Orientation

/**
 * Tool window factory for displaying language key translations.
 */
class LanguageKeyToolWindowFactory : ToolWindowFactory {

    override fun shouldBeAvailable(project: Project) = true

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        currentProject = project

        toolWindow.addComposeTab("Language Keys", focusOnClickInside = true) {
            val state = remember { mutableStateOf(TranslationState()) }
            updateState = state

            LanguageKeyContent(project, state)
        }
    }
}

data class TranslationState(
    val key: String = "",
    val translations: Map<String, String> = emptyMap()
)

@Composable
private fun LanguageKeyContent(project: Project, state: State<TranslationState>) {
    val currentState = state.value

    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🌍", modifier = Modifier.padding(end = 8.dp))
            Text("Language Key Viewer")
        }

        Divider(Orientation.Horizontal, Modifier.padding(bottom = 20.dp))

        if (currentState.key.isEmpty()) {
            // Empty state with centered content
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("📂", modifier = Modifier.padding(bottom = 16.dp))
                Text(
                    "Open a Python file to see language keys automatically",
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        } else {
            // Key display with icon
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🔑 ", modifier = Modifier.padding(end = 5.dp))
                Text(currentState.key)
            }

            if (currentState.translations.isEmpty()) {
                Text(
                    "❌ No translations found for this key.",
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            } else {
                // Translation count
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .padding(horizontal = 8.dp)
                ) {
                    Text("Found ")
                    Text("${currentState.translations.size}")
                    Text(" translation(s)")
                }

                // Table with better styling
                Column(Modifier.fillMaxWidth()) {
                    // Table header with darker background
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF2B2D30))
                            .border(1.dp, Color(0xFF3C3F41))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text("Language", Modifier.weight(0.25f))
                        Text("Translation", Modifier.weight(0.75f))
                    }

                    // Table rows with better spacing
                    currentState.translations.forEach { (language, translation) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1E1F22))
                                .border(1.dp, Color(0xFF3C3F41))
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(language, Modifier.weight(0.25f))
                            Text(translation, Modifier.weight(0.75f))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Updates the language key tool window with new translations.
 */
fun updateLanguageKeyToolWindow(key: String, translations: Map<String, String>) {
    println("[DEBUG] updateLanguageKeyToolWindow called with key='$key', ${translations.size} translations")

    updateState?.value = TranslationState(
        key = key,
        translations = translations
    )
}

var currentProject: Project? = null
var updateState: MutableState<TranslationState>? = null