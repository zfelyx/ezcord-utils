package me.geckotv.ezcordutils.todo

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.treeStructure.Tree
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

data class TodoItem(
    val text: String,
    val file: VirtualFile,
    val line: Int,
    val fileName: String
)

class TodoToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow): Unit {
        val todoPanel = TodoPanel(project)
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(todoPanel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

class TodoPanel(private val project: Project) : JPanel(BorderLayout()) {
    private val tree: Tree
    private val rootNode = DefaultMutableTreeNode("TODOs")
    private val treeModel = DefaultTreeModel(rootNode)

    init {
        tree = Tree(treeModel)
        tree.isRootVisible = false
        tree.showsRootHandles = true

        // Setup custom renderer
        setupTreeRenderer()

        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode
                    val item = node?.userObject as? TodoItem
                    item?.let { navigateToTodo(it) }
                }
            }
        })

        val scrollPane = JBScrollPane(tree)
        add(scrollPane, BorderLayout.CENTER)

        val toolbar = createToolbar()
        add(toolbar, BorderLayout.NORTH)

        refreshTodos()
    }

    private fun createToolbar(): JComponent {
        val actionGroup = DefaultActionGroup()
        actionGroup.add(RefreshAction(this))
        actionGroup.add(AddTodoAction(project, this))

        val toolbar = ActionManager.getInstance().createActionToolbar("TodoToolbar", actionGroup, true)
        toolbar.targetComponent = this
        return toolbar.component
    }

    fun refreshTodos() {
        ApplicationManager.getApplication().runReadAction {
            rootNode.removeAllChildren()

            val todos = findAllTodos()

            if (todos.isEmpty()) {
                val emptyNode = DefaultMutableTreeNode("🎉 No TODOs found!")
                rootNode.add(emptyNode)
            } else {
                todos.forEach { todo ->
                    val todoNode = DefaultMutableTreeNode(todo)
                    rootNode.add(todoNode)
                }
            }

            treeModel.reload()
            expandAll()
        }
    }

    private fun expandAll() {
        for (i in 0 until tree.rowCount) {
            tree.expandRow(i)
        }
    }

    private fun findAllTodos(): List<TodoItem> {
        val todos = mutableListOf<TodoItem>()
        val scope = GlobalSearchScope.projectScope(project)

        val supportedExtensions = listOf("java", "kt", "js", "ts", "py", "cpp", "c", "html", "css")

        supportedExtensions.forEach { ext ->
            val files = FileTypeIndex.getFiles(
                com.intellij.openapi.fileTypes.FileTypeManager.getInstance().getFileTypeByExtension(ext),
                scope
            )

            files.forEach { file ->
                val psiFile = PsiManager.getInstance(project).findFile(file)
                psiFile?.let {
                    val text = it.text
                    val lines = text.split("\n")

                    lines.forEachIndexed { index, line ->
                        val regex = """(?://|#)\s*TODO\s*:\s*(.+)""".toRegex(RegexOption.IGNORE_CASE)
                        val match = regex.find(line)

                        match?.let { m ->
                            val todoText = m.groupValues[1].trim()

                            todos.add(
                                TodoItem(
                                    text = todoText,
                                    file = file,
                                    line = index,
                                    fileName = file.name
                                )
                            )
                        }
                    }
                }
            }
        }

        return todos
    }

    private fun navigateToTodo(item: TodoItem) {
        val descriptor = OpenFileDescriptor(project, item.file, item.line, 0)
        FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
    }

    private fun setupTreeRenderer() {
        tree.cellRenderer = object : javax.swing.tree.DefaultTreeCellRenderer() {
            override fun getTreeCellRendererComponent(
                tree: javax.swing.JTree?,
                value: Any?,
                sel: Boolean,
                expanded: Boolean,
                leaf: Boolean,
                row: Int,
                hasFocus: Boolean
            ): java.awt.Component {
                super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)

                val node = value as? DefaultMutableTreeNode
                val item = node?.userObject as? TodoItem

                item?.let {
                    text = it.text
                    toolTipText = "${it.fileName} (Line ${it.line + 1})"
                    icon = AllIcons.General.TodoDefault
                }

                return this
            }
        }
    }
}

class RefreshAction(private val panel: TodoPanel) : AnAction("Refresh", "Refresh TODO list", AllIcons.Actions.Refresh) {
    override fun actionPerformed(e: AnActionEvent) {
        panel.refreshTodos()
    }
}

class AddTodoAction(private val project: Project, private val panel: TodoPanel) :
    AnAction("Add TODO", "Add new TODO", AllIcons.General.Add) {

    override fun actionPerformed(e: AnActionEvent) {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        if (editor == null) {
            Messages.showErrorDialog(project, "No file opened!", "Error")
            return
        }

        val text = Messages.showInputDialog(
            project,
            "Enter TODO text:",
            "Add TODO",
            AllIcons.General.Add
        ) ?: return

        val document: Document = editor.document
        val caretModel = editor.caretModel
        val lineNumber = caretModel.logicalPosition.line
        val lineStartOffset = document.getLineStartOffset(lineNumber)
        val line = document.getText(com.intellij.openapi.util.TextRange(
            lineStartOffset,
            document.getLineEndOffset(lineNumber)
        ))

        val virtualFile = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
        val fileExtension = virtualFile?.extension?.lowercase() ?: ""
        val commentPrefix = when (fileExtension) {
            "py", "sh", "yml", "yaml", "rb", "r" -> "#"
            else -> "//"
        }

        val indent = line.takeWhile { it.isWhitespace() }
        val todoLine = "$indent$commentPrefix TODO: $text\n"

        WriteCommandAction.runWriteCommandAction(project) {
            document.insertString(lineStartOffset, todoLine)
        }

        ApplicationManager.getApplication().invokeLater {
            panel.refreshTodos()
        }
    }
}