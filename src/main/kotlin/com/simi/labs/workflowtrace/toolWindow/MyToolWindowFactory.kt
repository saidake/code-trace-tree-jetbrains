package com.simi.labs.workflowtrace.toolWindow

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.treeStructure.Tree
import com.simi.labs.workflowtrace.services.TracePointService
import com.simi.labs.workflowtrace.actions.MoveUpTracePointAction
import com.simi.labs.workflowtrace.actions.MoveDownTracePointAction
import com.simi.labs.workflowtrace.actions.ExpandSelectedTracePointAction
import com.simi.labs.workflowtrace.actions.CollapseAllTracePointAction
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.ui.components.JBPanel
import java.awt.BorderLayout
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.JPopupMenu
import javax.swing.JMenuItem
import javax.swing.JTree
import java.util.*
import javax.swing.event.TreeExpansionListener
import javax.swing.event.TreeExpansionEvent
import com.intellij.ui.components.JBScrollPane
import java.awt.Point
import javax.swing.JComponent
import javax.swing.TransferHandler

class MyToolWindowFactory : com.intellij.openapi.wm.ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val myToolWindow = MyToolWindow(toolWindow)
        val content = ContentFactory.getInstance().createContent(myToolWindow.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true

    class MyToolWindow(private val toolWindow: ToolWindow) {
        private val service = toolWindow.project.service<TracePointService>()
        private val treeModel = DefaultTreeModel(DefaultMutableTreeNode("Root"))
        private val rootNode get() = treeModel.root as DefaultMutableTreeNode
        private var highlightedPath: TreePath? = null
        private var isUpdatingTree = false
        private lateinit var tree: JTree
        private var anchorPath: TreePath? = null // Fixed anchor for Shift selection

        fun getContent(): JComponent {
            tree = Tree(treeModel).apply {
                isRootVisible = false
                selectionModel.selectionMode = javax.swing.tree.TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION
                toggleClickCount = 0
                service.addTracePointListener { tracePoints, expandedIds ->
                    if (!isUpdatingTree) {
                        println("Updating tool window with ${tracePoints.size} trace points and ${expandedIds.size} expanded IDs")
                        updateTreeModel(tracePoints, this, expandedIds)
                    }
                }
                cellRenderer = TracePointTreeRenderer(service, this@MyToolWindow)
                isEditable = false
                dragEnabled = true
                transferHandler = object : TransferHandler() {
                    private var draggedNode: DefaultMutableTreeNode? = null

                    override fun createTransferable(c: JComponent): Transferable? {
                        val tree = c as? JTree ?: return null
                        val path = tree.selectionPath ?: return null
                        draggedNode = path.lastPathComponent as? DefaultMutableTreeNode ?: return null
                        val tracePoint = draggedNode?.userObject as? TracePointService.TracePoint ?: return null
                        return object : Transferable {
                            override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.stringFlavor)
                            override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean = flavor == DataFlavor.stringFlavor
                            override fun getTransferData(flavor: DataFlavor?): Any = tracePoint.id
                        }
                    }

                    override fun getSourceActions(c: JComponent): Int = MOVE

                    override fun canImport(support: TransferSupport): Boolean {
                        if (!support.isDrop || !support.isDataFlavorSupported(DataFlavor.stringFlavor)) return false
                        val dropLocation = support.dropLocation as? JTree.DropLocation ?: return false
                        val dropPath = dropLocation.path ?: return false
                        val dropNode = dropPath.lastPathComponent as? DefaultMutableTreeNode ?: return false
                        val draggedTracePoint = draggedNode?.userObject as? TracePointService.TracePoint ?: return false
                        val dropTracePoint = dropNode.userObject as? TracePointService.TracePoint ?: return false
                        if (dropTracePoint.id == draggedTracePoint.id) return false
                        var node: DefaultMutableTreeNode? = dropNode
                        while (node != null && node != rootNode) {
                            val nodeTracePoint = node.userObject as? TracePointService.TracePoint
                            if (nodeTracePoint?.id == draggedTracePoint.id) return false
                            node = node.parent as? DefaultMutableTreeNode
                        }
                        if (dropNode.userObject !is TracePointService.TracePoint) return false
                        highlightedPath = dropPath
                        (support.component as? JTree)?.repaint()
                        return true
                    }

                    override fun importData(support: TransferSupport): Boolean {
                        if (!canImport(support)) return false
                        val dropLocation = support.dropLocation as? JTree.DropLocation ?: return false
                        val dropPath = dropLocation.path ?: return false
                        val dropNode = dropPath.lastPathComponent as? DefaultMutableTreeNode ?: return false
                        val transferable = support.transferable
                        val tracePointId = transferable.getTransferData(DataFlavor.stringFlavor) as String
                        val draggedTracePoint = service.getTracePoints().find { it.id == tracePointId } ?: return false
                        val draggedNode = findNodeByTracePointId(tracePointId) ?: return false
                        val tree = support.component as? JTree ?: return false

                        try {
                            val expandedPaths = mutableSetOf<TreePath>()
                            traverseNodes(rootNode) { node ->
                                val tracePoint = (node as? DefaultMutableTreeNode)?.userObject as? TracePointService.TracePoint
                                if (tracePoint != null) {
                                    val nodePath = TreePath(node.path)
                                    if (tree.isExpanded(nodePath)) {
                                        expandedPaths.add(nodePath)
                                    }
                                }
                                true
                            }
                            highlightedPath = null
                            tree.repaint()
                            val parentNode = dropNode
                            val dropIndex = parentNode.childCount
                            val newParentId = (dropNode.userObject as TracePointService.TracePoint).id
                            (draggedNode.parent as? DefaultMutableTreeNode)?.remove(draggedNode)
                            parentNode.insert(draggedNode, dropIndex)
                            val currentTracePoints = service.getTracePoints()
                            val updatedTracePoints = currentTracePoints.map { tracePoint ->
                                if (tracePoint.id == draggedTracePoint.id) {
                                    tracePoint.copy(parentId = newParentId)
                                } else {
                                    tracePoint
                                }
                            }
                            service.updateTracePoints(updatedTracePoints)
                            treeModel.reload()
                            val pathsToExpand = mutableSetOf<TreePath>()
                            val expandedIds = service.getExpandedTracePointIds()
                            traverseNodes(rootNode) { node ->
                                val tracePoint = (node as? DefaultMutableTreeNode)?.userObject as? TracePointService.TracePoint
                                if (tracePoint != null) {
                                    val nodePath = TreePath(node.path)
                                    if (expandedIds.contains(tracePoint.id)) {
                                        pathsToExpand.add(nodePath)
                                    }
                                }
                                true
                            }
                            pathsToExpand.forEach { path ->
                                tree.expandPath(path)
                            }
                            selectionPath = TreePath(draggedNode.path)
                            println("Moved trace point ${draggedTracePoint.name} to parent $newParentId as last child")
                            return true
                        } catch (e: Exception) {
                            thisLogger().warn("Failed to move trace point: ${e.message}", e)
                            return false
                        }
                    }
                }
                addMouseListener(object : MouseAdapter() {
                    override fun mouseClicked(e: MouseEvent) {
                        val tree = this@apply
                        val path = tree.getPathForLocation(e.x, e.y) ?: return
                        val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                        val tracePoint = node.userObject as? TracePointService.TracePoint ?: return
                        println("Mouse clicked on trace point: ${tracePoint.name} in ${tracePoint.fileName} at line ${tracePoint.lineNumber}")

                        if (e.clickCount == 1 && e.button == MouseEvent.BUTTON1) {
                            val bounds = tree.getPathBounds(path)
                            if (bounds != null && node.childCount > 0) {
                                val handleWidth = 20
                                if (e.x < bounds.x + handleWidth) {
                                    val currentExpandedIds = service.getExpandedTracePointIds().toMutableList()
                                    if (tree.isExpanded(path)) {
                                        tree.collapsePath(path)
                                        if (currentExpandedIds.contains(tracePoint.id)) {
                                            currentExpandedIds.remove(tracePoint.id)
                                            isUpdatingTree = true
                                            service.setExpandedTracePointIds(currentExpandedIds)
                                            isUpdatingTree = false
                                        }
                                    } else {
                                        tree.expandPath(path)
                                        if (!currentExpandedIds.contains(tracePoint.id)) {
                                            currentExpandedIds.add(tracePoint.id)
                                            isUpdatingTree = true
                                            service.setExpandedTracePointIds(currentExpandedIds)
                                            isUpdatingTree = false
                                        }
                                    }
                                    return
                                }
                            }

                            // Handle selection
                            val selectedIds = mutableListOf<String>()
                            if (e.isShiftDown) {
                                // Mimic Project view Shift selection: select all visible nodes between anchor and clicked node
                                val currentRow = tree.getRowForPath(path)
                                val anchor = anchorPath ?: tree.selectionPaths?.firstOrNull()
                                if (anchor != null) {
                                    val anchorRow = tree.getRowForPath(anchor)
                                    val startRow = minOf(anchorRow, currentRow)
                                    val endRow = maxOf(anchorRow, currentRow)
                                    val newSelectedPaths = mutableListOf<TreePath>()
                                    for (row in startRow..endRow) {
                                        val rowPath = tree.getPathForRow(row) ?: continue
                                        val rowNode = rowPath.lastPathComponent as? DefaultMutableTreeNode ?: continue
                                        if (rowNode.userObject is TracePointService.TracePoint) {
                                            newSelectedPaths.add(rowPath)
                                        }
                                    }
                                    tree.selectionPaths = newSelectedPaths.toTypedArray()
                                    selectedIds.addAll(newSelectedPaths.mapNotNull { (it.lastPathComponent as? DefaultMutableTreeNode)?.userObject as? TracePointService.TracePoint }.map { it.id })
                                } else {
                                    // No anchor, set as anchor and select single node
                                    tree.selectionPath = path
                                    selectedIds.add(tracePoint.id)
                                    anchorPath = path
                                }
                            } else if (e.isControlDown) {
                                if (tree.isPathSelected(path)) {
                                    tree.removeSelectionPath(path)
                                    service.toggleTracePointSelection(tracePoint.id)
                                    selectedIds.addAll(tree.selectionPaths?.mapNotNull { (it.lastPathComponent as? DefaultMutableTreeNode)?.userObject as? TracePointService.TracePoint }?.map { it.id } ?: emptyList())
                                } else {
                                    tree.addSelectionPath(path)
                                    service.toggleTracePointSelection(tracePoint.id)
                                    selectedIds.addAll(tree.selectionPaths?.mapNotNull { (it.lastPathComponent as? DefaultMutableTreeNode)?.userObject as? TracePointService.TracePoint }?.map { it.id } ?: emptyList())
                                }
                                anchorPath = null // Reset anchor on Control-click
                            } else {
                                tree.selectionPath = path
                                selectedIds.add(tracePoint.id)
                                anchorPath = path // Set anchor on single click
                            }
                            isUpdatingTree = true
                            service.selectTracePoints(selectedIds)
                            isUpdatingTree = false
                        } else if (e.clickCount == 2 && e.button == MouseEvent.BUTTON1 && !e.isControlDown && !e.isShiftDown) {
                            println("Double-clicked trace point: ${tracePoint.name}")
                            tracePoint.navigateTo()
                            isUpdatingTree = true
                            service.selectTracePoints(listOf(tracePoint.id))
                            isUpdatingTree = false
                            anchorPath = path // Set anchor on double-click
                        }
                    }

                    override fun mousePressed(e: MouseEvent) {
                        if (e.isPopupTrigger) {
                            showPopupMenu(e, toolWindow)
                        }
                    }

                    override fun mouseReleased(e: MouseEvent) {
                        if (e.isPopupTrigger) {
                            showPopupMenu(e, toolWindow)
                        }
                    }

                    private fun showPopupMenu(e: MouseEvent, toolWindow: ToolWindow) {
                        val tree = this@apply
                        val path = tree.getPathForLocation(e.x, e.y) ?: return
                        val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                        val tracePoint = node.userObject as? TracePointService.TracePoint ?: return
                        if (!tree.isPathSelected(path)) {
                            tree.selectionPath = path
                            isUpdatingTree = true
                            service.selectTracePoints(listOf(tracePoint.id))
                            isUpdatingTree = false
                            anchorPath = path // Set anchor on popup selection
                        }
                        val selectedTracePoints = tree.selectionPaths?.mapNotNull { (it.lastPathComponent as? DefaultMutableTreeNode)?.userObject as? TracePointService.TracePoint } ?: emptyList()
                        val popupMenu = JPopupMenu()

                        if (selectedTracePoints.size > 1) {
                            val deleteItem = JMenuItem("Delete")
                            deleteItem.addActionListener {
                                val confirm = Messages.showYesNoDialog(
                                    toolWindow.project,
                                    "Are you sure you want to delete ${selectedTracePoints.size} trace points?",
                                    "Confirm Delete",
                                    null
                                )
                                if (confirm == Messages.YES) {
                                    service.deleteTracePoints(selectedTracePoints.map { it.id })
                                }
                            }
                            popupMenu.add(deleteItem)
                        } else {
                            val addChildItem = JMenuItem("Add a child point")
                            addChildItem.addActionListener {
                                val editor = FileEditorManager.getInstance(toolWindow.project).selectedTextEditor
                                val file = FileEditorManager.getInstance(toolWindow.project).selectedFiles.firstOrNull()
                                if (editor == null || file == null || editor.caretModel.getCaretsAndSelections().isEmpty() || !editor.caretModel.currentCaret.isValid) {
                                    Messages.showWarningDialog(
                                        toolWindow.project,
                                        "No valid caret position found in the editor.",
                                        "Add Child Trace Point"
                                    )
                                    return@addActionListener
                                }
                                val lineNumber = editor.document.getLineNumber(editor.caretModel.offset) + 1
                                val tracePointName = Messages.showInputDialog(
                                    toolWindow.project,
                                    "Enter name for the child trace point:",
                                    "Add Child Trace Point",
                                    null
                                )
                                if (!tracePointName.isNullOrBlank()) {
                                    service.addTracePoint(tracePointName, file, lineNumber, editor, parentId = tracePoint.id)
                                }
                            }
                            popupMenu.add(addChildItem)

                            val renameItem = JMenuItem("Rename")
                            renameItem.addActionListener {
                                val newName = Messages.showInputDialog(
                                    toolWindow.project,
                                    "Enter new name for trace point:",
                                    "Rename Trace Point",
                                    null,
                                    tracePoint.name,
                                    null
                                )
                                if (!newName.isNullOrBlank()) {
                                    service.renameTracePoint(tracePoint.id, newName)
                                }
                            }
                            popupMenu.add(renameItem)

                            val deleteItem = JMenuItem("Delete")
                            deleteItem.addActionListener {
                                val confirm = Messages.showYesNoDialog(
                                    toolWindow.project,
                                    "Are you sure you want to delete trace point '${tracePoint.name}'?",
                                    "Confirm Delete",
                                    null
                                )
                                if (confirm == Messages.YES) {
                                    service.deleteTracePoints(listOf(tracePoint.id))
                                }
                            }
                            popupMenu.add(deleteItem)
                        }
                        popupMenu.show(this@apply, e.x, e.y)
                    }
                })
                addTreeExpansionListener(object : TreeExpansionListener {
                    override fun treeExpanded(event: TreeExpansionEvent) {
                        if (isUpdatingTree) return
                        val path = event.path
                        val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                        val tracePoint = node.userObject as? TracePointService.TracePoint ?: return
                        val currentExpandedIds = service.getExpandedTracePointIds().toMutableList()
                        if (!currentExpandedIds.contains(tracePoint.id)) {
                            currentExpandedIds.add(tracePoint.id)
                            isUpdatingTree = true
                            service.setExpandedTracePointIds(currentExpandedIds)
                            isUpdatingTree = false
                            println("Expanded trace point: ${tracePoint.id}")
                        }
                    }

                    override fun treeCollapsed(event: TreeExpansionEvent) {
                        if (isUpdatingTree) return
                        val path = event.path
                        val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                        val tracePoint = node.userObject as? TracePointService.TracePoint ?: return
                        val currentExpandedIds = service.getExpandedTracePointIds().toMutableList()
                        if (currentExpandedIds.contains(tracePoint.id)) {
                            currentExpandedIds.remove(tracePoint.id)
                            isUpdatingTree = true
                            service.setExpandedTracePointIds(currentExpandedIds)
                            isUpdatingTree = false
                            println("Collapsed trace point: ${tracePoint.id}")
                        }
                    }
                })
            }

            val actionGroup = DefaultActionGroup().apply {
                add(MoveUpTracePointAction(this@MyToolWindow))
                add(MoveDownTracePointAction(this@MyToolWindow))
                add(ExpandSelectedTracePointAction(this@MyToolWindow))
                add(CollapseAllTracePointAction(this@MyToolWindow))
            }
            val actionToolbar = ActionManager.getInstance().createActionToolbar(
                ActionPlaces.TOOLWINDOW_TITLE,
                actionGroup,
                true
            )
            actionToolbar.setTargetComponent(tree)
            actionToolbar.component.isOpaque = false

            val panel = JBPanel<JBPanel<*>>().apply {
                layout = BorderLayout()
                add(actionToolbar.component, BorderLayout.NORTH)
                add(JBScrollPane(tree), BorderLayout.CENTER)
            }

            return panel
        }

        fun getTree(): JTree = tree

        fun getHighlightedPath(): TreePath? = highlightedPath
        fun isHighlightOnDivider(): Boolean = false
        fun getDropPoint(): Point? = null

        internal fun traverseNodes(node: DefaultMutableTreeNode, visitor: (DefaultMutableTreeNode) -> Boolean): Boolean {
            if (!visitor(node)) return false
            for (i in 0 until node.childCount) {
                val child = node.getChildAt(i) as? DefaultMutableTreeNode ?: continue
                if (!traverseNodes(child, visitor)) return false
            }
            return true
        }

        private fun updateTreeModel(tracePoints: List<TracePointService.TracePoint>, tree: JTree, expandedIds: List<String>) {
            isUpdatingTree = true
            val selectedIds = service.getTracePoints().filter { service.isTracePointSelected(it.id) }.map { it.id }
            rootNode.removeAllChildren()
            val nodeMap = mutableMapOf<String, DefaultMutableTreeNode>()
            tracePoints.forEach { tracePoint ->
                val node = DefaultMutableTreeNode(tracePoint)
                nodeMap[tracePoint.id] = node
            }
            tracePoints.forEach { tracePoint ->
                val node = nodeMap[tracePoint.id] ?: return@forEach
                if (tracePoint.parentId == null) {
                    rootNode.add(node)
                } else {
                    val parentNode = nodeMap[tracePoint.parentId]
                    if (parentNode != null) {
                        parentNode.add(node)
                    } else {
                        rootNode.add(node)
                        thisLogger().warn("Parent node not found for trace point ${tracePoint.name} with parentId ${tracePoint.parentId}")
                    }
                }
            }
            treeModel.reload()
            val pathsToExpand = mutableSetOf<TreePath>()
            traverseNodes(rootNode) { node ->
                val tracePoint = (node as? DefaultMutableTreeNode)?.userObject as? TracePointService.TracePoint
                if (tracePoint != null && expandedIds.contains(tracePoint.id)) {
                    pathsToExpand.add(TreePath(node.path))
                }
                true
            }
            pathsToExpand.forEach { path ->
                tree.expandPath(path)
            }
            val selectedPaths = mutableListOf<TreePath>()
            traverseNodes(rootNode) { node ->
                val tracePoint = (node as? DefaultMutableTreeNode)?.userObject as? TracePointService.TracePoint
                if (tracePoint != null && selectedIds.contains(tracePoint.id)) {
                    val nodePath = TreePath(node.path)
                    if (tree.isVisible(nodePath)) {
                        selectedPaths.add(nodePath)
                    }
                }
                true
            }
            if (selectedPaths.isNotEmpty()) {
                tree.selectionPaths = selectedPaths.toTypedArray()
            } else {
                tree.clearSelection()
            }
            isUpdatingTree = false
        }

        private fun findNodeByTracePointId(tracePointId: String): DefaultMutableTreeNode? {
            var result: DefaultMutableTreeNode? = null
            traverseNodes(rootNode) { node ->
                val tracePoint = (node as? DefaultMutableTreeNode)?.userObject as? TracePointService.TracePoint
                if (tracePoint?.id == tracePointId) {
                    result = node
                    false
                } else {
                    true
                }
            }
            return result
        }
    }
}