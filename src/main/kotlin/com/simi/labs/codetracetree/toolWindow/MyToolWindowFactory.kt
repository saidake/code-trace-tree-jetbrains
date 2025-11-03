package com.simi.labs.codetracetree.toolWindow

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory
import com.simi.labs.codetracetree.services.TracePointService
import com.simi.labs.codetracetree.actions.MoveUpTracePointAction
import com.simi.labs.codetracetree.actions.MoveDownTracePointAction
import com.simi.labs.codetracetree.actions.ExpandSelectedTracePointAction
import com.simi.labs.codetracetree.actions.CollapseAllTracePointAction
import com.simi.labs.codetracetree.actions.ExportTracePointsAction
import com.simi.labs.codetracetree.actions.ImportTracePointsAction
import com.simi.labs.codetracetree.actions.GoToTracePointAction
import com.simi.labs.codetracetree.actions.ToggleHighlightTracePointsAction
import com.simi.labs.codetracetree.actions.ToggleDescriptionAreaAction
import com.simi.labs.codetracetree.GlobalIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.ui.components.JBPanel
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
import javax.swing.event.TreeExpansionListener
import javax.swing.event.TreeExpansionEvent
import com.intellij.ui.components.JBScrollPane
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.treeStructure.Tree
import java.awt.Point
import javax.swing.JComponent
import javax.swing.JTextArea
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import com.intellij.ui.JBColor
import java.awt.Component
import java.awt.Dimension
import java.awt.datatransfer.UnsupportedFlavorException
import javax.swing.BoxLayout
import javax.swing.SwingUtilities
import javax.swing.TransferHandler


class MyToolWindowFactory : com.intellij.openapi.wm.ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val myToolWindow = MyToolWindow(toolWindow, project)
        val content = ContentFactory.getInstance().createContent(myToolWindow.getContent(), null, false)
        toolWindow.contentManager.addContent(content)

        // Listen for tool window activation/deactivation to update icon
        ApplicationManager.getApplication().messageBus.connect(project).subscribe(
            ToolWindowManagerListener.TOPIC,
            object : ToolWindowManagerListener {
                override fun toolWindowShown(toolWindow: ToolWindow) {
                    if (toolWindow.id == "Code Trace Tree") {
                        toolWindow.setIcon(GlobalIcons.CodeTraceTreeSelected)
                    }
                }

                override fun stateChanged(toolWindowManager: ToolWindowManager) {
                    val activeToolWindow = toolWindowManager.activeToolWindowId
                    if (activeToolWindow == "Code Trace Tree") {
                        toolWindow.setIcon(GlobalIcons.CodeTraceTreeSelected)
                    } else if (toolWindow.id == "Code Trace Tree") {
                        val isDarkTheme = JBColor.isBright()
                        toolWindow.setIcon(if (isDarkTheme) GlobalIcons.CodeTraceTree else GlobalIcons.CodeTraceTreeDark)
                    }
                }
            }
        )
    }

    override fun shouldBeAvailable(project: Project) = true

    class MyToolWindow(private val toolWindow: ToolWindow, private val project: Project) {
        private val service = toolWindow.project.service<TracePointService>()
        private val treeModel = DefaultTreeModel(DefaultMutableTreeNode("Root"))

        private val rootTreeNode get() = treeModel.root as DefaultMutableTreeNode
        private val treeNodeMap: MutableMap<String, DefaultMutableTreeNode> = mutableMapOf(
            "root" to rootTreeNode
        )
        private var highlightedPath: TreePath? = null
        private var isUpdatingTree = false
        private lateinit var tree: JTree

        private val descriptionTextArea = JTextArea().apply {
            lineWrap = true
            wrapStyleWord = true
            isEnabled = false
            rows = 4
        }
        private val descriptionScrollPane = JBScrollPane(descriptionTextArea)

        // Custom DataFlavor for trace point IDs
        private val tracePointDataFlavor = DataFlavor(
            "${TracePointService.TracePointNode::class.java.canonicalName}/trace-point-id",
            "TracePoint ID"
        )

        fun getContent(): JComponent {
            tree = Tree(treeModel).apply {
                isRootVisible = false
                selectionModel.selectionMode = javax.swing.tree.TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION
                toggleClickCount = 0
                // Moved listener setup here to ensure tree is initialized
                service.addTracePointListener { tracePoints, expandedIds ->
                    if (!isUpdatingTree) {
                        println("Updating tool window with ${tracePoints.size} trace points and ${expandedIds.size} expanded IDs")
                        fullyUpdateTreeModel(tracePoints, this, expandedIds)
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
                        val tracePoint = draggedNode?.userObject as? TracePointService.TracePointNode ?: return null
                        return object : Transferable {
                            override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(tracePointDataFlavor)
                            override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean = flavor == tracePointDataFlavor
                            override fun getTransferData(flavor: DataFlavor?): Any {
                                if (flavor == tracePointDataFlavor) {
                                    return tracePoint.id
                                }
                                throw UnsupportedFlavorException(flavor)
                            }
                        }
                    }

                    override fun getSourceActions(c: JComponent): Int = MOVE

                    override fun canImport(support: TransferHandler.TransferSupport): Boolean {
                        println("canImport triggered")
                        // Only allow drops within the JTree (tool window)
                        if (!support.isDrop || support.component !is JTree) return false
                        if (!support.isDataFlavorSupported(tracePointDataFlavor)) return false
                        val dropLocation = support.dropLocation as? JTree.DropLocation ?: return false
                        val dropPath = dropLocation.path ?: return false
                        val dropNode = dropPath.lastPathComponent as? DefaultMutableTreeNode ?: return false
                        val draggedTracePointNode = draggedNode?.userObject as? TracePointService.TracePointNode ?: return false
                        val dropTracePointNode = dropNode.userObject as? TracePointService.TracePointNode ?: return false
                        // Prevent dropping on the same node or its descendants
                        if (dropTracePointNode.id == draggedTracePointNode.id) return false
                        var node: DefaultMutableTreeNode? = dropNode
                        while (node != null && node != rootTreeNode) {
                            val tracePointNode = node.userObject as? TracePointService.TracePointNode
                            if (tracePointNode?.id == draggedTracePointNode.id) return false
                            node = node.parent as? DefaultMutableTreeNode
                        }
                        // Prevent dropping on the current parent
                        val draggedParent = draggedNode?.parent as? DefaultMutableTreeNode
                        val draggedParentTracePoint = draggedParent?.userObject as? TracePointService.TracePointNode
                        if (draggedParentTracePoint?.id == dropTracePointNode.id) return false
                        if (dropNode.userObject !is TracePointService.TracePointNode) return false
                        highlightedPath = dropPath
                        (support.component as? JTree)?.repaint()
                        return true
                    }

                    override fun importData(support: TransferSupport): Boolean {
                        if (!canImport(support)) return false
                        val dropLocation = support.dropLocation as? JTree.DropLocation ?: return false
                        val dropPath = dropLocation.path ?: return false
                        val dropTreeNode = dropPath.lastPathComponent as? DefaultMutableTreeNode ?: return false
                        val transferable = support.transferable
                        val tracePointId = transferable.getTransferData(tracePointDataFlavor) as? String ?: return false

                        val draggedTracePoints=service.findTracePointNodes { it.id == tracePointId }
                        if (draggedTracePoints.size!=1) return false
                        val draggedTracePoint = draggedTracePoints[0]
                        val draggedTreeNode = findNodeByTracePointId(tracePointId) ?: return false
                        val tree = support.component as? JTree ?: return false

                        try {
                            val expandedPaths = mutableSetOf<TreePath>()
                            traverseTreeNodes(rootTreeNode) { node ->
                                val tracePointNode = (node as? DefaultMutableTreeNode)?.userObject as? TracePointService.TracePointNode
                                if (tracePointNode != null) {
                                    val nodePath = TreePath(node.path)
                                    if (tree.isExpanded(nodePath)) {
                                        expandedPaths.add(nodePath)
                                    }
                                }
                                true
                            }
                            highlightedPath = null
                            tree.repaint()
                            val parentNode = dropTreeNode
                            val dropIndex = parentNode.childCount
                            val newParentId = (dropTreeNode.userObject as TracePointService.TracePointNode).id
                            (draggedTreeNode.parent as? DefaultMutableTreeNode)?.remove(draggedTreeNode)
                            // Append the draggedNode in the parentNode
                            parentNode.insert(draggedTreeNode, dropIndex)

                            // Remove the draggedTracePoint from its parent
                            val oldParentTracePoint = draggedTracePoint.parentId?.let { service.getTracePointById(it) }
                            oldParentTracePoint?.children?.remove(draggedTracePoint)

                            // Insert the draggedTracePoint into the new parent
                            val newParentTracePoint = service.findTracePointNodes { it.id == newParentId }.firstOrNull()
                            newParentTracePoint?.children?.add(draggedTracePoint)
                            draggedTracePoint.parentId = newParentId

                            // Update the related trace points
                            newParentTracePoint?.let {
                                service.notifyListeners()
                            }

                            treeModel.reload()
                            val pathsToExpand = mutableSetOf<TreePath>()
                            val expandedIds = service.getExpandedTracePointIds()
                            traverseTreeNodes(rootTreeNode) { node ->
                                val tracePoint = (node as? DefaultMutableTreeNode)?.userObject as? TracePointService.TracePointNode
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
                            selectionPath = TreePath(draggedTreeNode.path)
                            println("Moved trace point ${draggedTracePoint.tracePoint.name} to parent $newParentId as last child")
                            return true
                        } catch (e: Exception) {
                            thisLogger().warn("Failed to move trace point: ${e.message}", e)
                            return false
                        }
                    }
                }
                addMouseListener(object : MouseAdapter() {
                    override fun mouseClicked(e: MouseEvent) {
                        println("mouseClicked triggered")
                        descriptionTextArea.isEnabled = false
                        val tree = this@apply
                        val path = tree.getPathForLocation(e.x, e.y) ?: run {
                            println("No path found - tree might be empty or coordinates wrong")
                            return
                        }
//                        val treePoint = SwingUtilities.convertPoint(e.component, e.point, tree)
//                        val path = tree.getPathForLocation(treePoint.x, treePoint.y) ?: return
                        println("path: $path")
                        val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                        val tracePointNode = node.userObject as? TracePointService.TracePointNode ?: return
                        val tracePoint = tracePointNode.tracePoint
                        println("Mouse clicked on trace point: ${tracePoint.name} in ${tracePoint.fileName} at line ${tracePoint.lineNumber}")
                        if (e.clickCount == 2 && e.button == MouseEvent.BUTTON1 && !e.isControlDown && !e.isShiftDown) {
                            println("Double-clicked trace point: ${tracePoint.name}")
                            ApplicationManager.getApplication().invokeLater {
                                tracePoint.navigateTo(project)
                            }
                        }
                        val selectedIds = tree.selectionPaths
                            ?.mapNotNull { (it.lastPathComponent as? DefaultMutableTreeNode)?.userObject as? TracePointService.TracePointNode }
                            ?.map { it.id }
                            ?.toMutableSet()
                            ?: mutableSetOf()

                        service.selectTracePoints(selectedIds)
                        updateDescriptionArea()

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
                        println("showPopupMenu triggered")
                        val tree = this@apply
                        val path = tree.getPathForLocation(e.x, e.y) ?: run {
                            println("No path found - tree might be empty or coordinates wrong")
                            return
                        }
//                        val treePoint = SwingUtilities.convertPoint(e.component, e.point, tree)
//                        val path = tree.getPathForLocation(treePoint.x, treePoint.y) ?: return
                        println("path: $path")
                        val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                        val tracePointNode = node.userObject as? TracePointService.TracePointNode ?: return
                        val tracePoint = tracePointNode.tracePoint
                        val selectedTracePoints = tree.selectionPaths?.mapNotNull { (it.lastPathComponent as? DefaultMutableTreeNode)?.userObject as? TracePointService.TracePointNode } ?: emptyList()
                        val popupMenu = JPopupMenu()
                        println("selectedTracePoints.size : ${selectedTracePoints.size}")
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
                                    service.deleteTracePointsWithChildren(selectedTracePoints.map { it.id })
                                }
                            }
                            popupMenu.add(deleteItem)
                        } else {
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
                                    service.renameTracePoint(tracePointNode.id, newName)
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
                                    service.deleteTracePointsWithChildren(listOf(tracePointNode.id))
                                }
                            }
                            popupMenu.add(deleteItem)
                            popupMenu.addSeparator()
                            val goToItem = JMenuItem("Go to Trace Point")
                            goToItem.addActionListener {
                                tracePoint.navigateTo(project)
                            }
                            popupMenu.add(goToItem)
                        }

                        popupMenu.show(this@apply, e.x, e.y)
                    }
                })
                addTreeExpansionListener(object : TreeExpansionListener {
                    override fun treeExpanded(event: TreeExpansionEvent) {
                        println("treeExpanded triggered")
                        val path = event.path
                        val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                        val tracePointNode = node.userObject as? TracePointService.TracePointNode ?: return
                        val currentExpandedIds = service.getExpandedTracePointIds().toMutableSet()
                        if (!currentExpandedIds.contains(tracePointNode.id)) {
                            currentExpandedIds.add(tracePointNode.id)
                            service.setExpandedTracePointIds(currentExpandedIds)
                            println("Expanded trace point: ${tracePointNode.id}")
                        }
                    }

                    override fun treeCollapsed(event: TreeExpansionEvent) {
                        println("treeCollapsed triggered")
                        val path = event.path
                        val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                        val tracePointNode = node.userObject as? TracePointService.TracePointNode ?: return
                        val tracePoint = tracePointNode.tracePoint
                        val currentExpandedIds = service.getExpandedTracePointIds().toMutableSet()
                        if (currentExpandedIds.contains(tracePointNode.id)) {
                            currentExpandedIds.remove(tracePointNode.id)
                            service.setExpandedTracePointIds(currentExpandedIds)
                            isUpdatingTree = false
                            println("Collapsed trace point: ${tracePointNode.id}")
                        }
                    }
                })
            }

            // Add DocumentListener to update description when edited
            descriptionTextArea.document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) {
                    updateTracePointDescription()
                }

                override fun removeUpdate(e: DocumentEvent?) {
                    updateTracePointDescription()
                }

                override fun changedUpdate(e: DocumentEvent?) {
                    updateTracePointDescription()
                }

                private fun updateTracePointDescription() {
                    println("updateTracePointDescription triggered")
                    val selectedPaths = tree.selectionPaths
                    if (selectedPaths?.size == 1) {
                        val node = selectedPaths[0].lastPathComponent as? DefaultMutableTreeNode
                        val tracePointNode = node?.userObject as? TracePointService.TracePointNode
                        val tracePoint = tracePointNode?.tracePoint
                        if (tracePoint != null) {
                            val newDescription = descriptionTextArea.text
                            if (newDescription != tracePoint.description) {
                                service.updateTracePointDescription(tracePointNode.id, newDescription)
                                println("service.updateTracePointDescription triggered")
                            }
                        }
                    }
                }
            })

            val actionGroup = DefaultActionGroup().apply {
                add(GoToTracePointAction(this@MyToolWindow).apply {
                    templatePresentation.text = "Go to Trace Point"
                    templatePresentation.description = "Navigate to the selected trace point's file and line"
                })
                add(MoveUpTracePointAction(this@MyToolWindow).apply {
                    templatePresentation.text = "Move Up"
                    templatePresentation.description = "Move the selected trace point up in the list"
                })
                add(MoveDownTracePointAction(this@MyToolWindow).apply {
                    templatePresentation.text = "Move Down"
                    templatePresentation.description = "Move the selected trace point down in the list"
                })
                add(ExpandSelectedTracePointAction(this@MyToolWindow).apply {
                    templatePresentation.text = "Expand Selected"
                    templatePresentation.description = "Expand the selected trace point node"
                })
                add(CollapseAllTracePointAction(this@MyToolWindow).apply {
                    templatePresentation.text = "Collapse All"
                    templatePresentation.description = "Collapse all trace point nodes"
                })
                add(ToggleHighlightTracePointsAction().apply {
                    templatePresentation.text = "Toggle Highlights"
                    templatePresentation.description = "Toggle the visibility of trace point highlights in files"
                })
                add(ToggleDescriptionAreaAction(this@MyToolWindow).apply {
                    templatePresentation.text = "Toggle Description"
                    templatePresentation.description = "Show or hide the description area for the selected trace point"
                })
                add(ExportTracePointsAction().apply {
                    templatePresentation.text = "Export Trace Points"
                    templatePresentation.description = "Export all trace points to an XML file"
                })
                add(ImportTracePointsAction().apply {
                    templatePresentation.text = "Import Trace Points"
                    templatePresentation.description = "Import trace points from an XML file"
                })
            }
            val actionToolbar = ActionManager.getInstance().createActionToolbar(
                ActionPlaces.TOOLWINDOW_TITLE,
                actionGroup,
                true
            )
            actionToolbar.setTargetComponent(tree)
            actionToolbar.component.isOpaque = false

            val panel = JBPanel<JBPanel<*>>().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)

                actionToolbar.component.maximumSize = Dimension(Int.MAX_VALUE, actionToolbar.component.preferredSize.height)
                actionToolbar.component.minimumSize = actionToolbar.component.preferredSize
                actionToolbar.component.alignmentX = Component.LEFT_ALIGNMENT
                add(actionToolbar.component)

                descriptionScrollPane.alignmentX = Component.LEFT_ALIGNMENT
                add(descriptionScrollPane)

                val treeScrollPane = JBScrollPane(tree)
                treeScrollPane.alignmentX = Component.LEFT_ALIGNMENT
                treeScrollPane.maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
                add(treeScrollPane)
            }
            descriptionScrollPane.isVisible = service.isDescriptionAreaOpened()

            return panel
        }

        fun getTree(): JTree = tree

        fun getHighlightedPath(): TreePath? = highlightedPath
        fun isHighlightOnDivider(): Boolean = false
        fun getDropPoint(): Point? = null


        fun setDescriptionAreaVisible(visible: Boolean) {
            service.setDescriptionAreaOpened(visible)
            descriptionScrollPane.isVisible = visible
            val panel = toolWindow.contentManager.contents[0].component as? JBPanel<*> ?: return
            panel.revalidate()
            panel.repaint()
        }


        private fun updateDescriptionArea() {
            println("updateDescriptionArea triggered")
            val selectedPaths = tree.selectionPaths
            descriptionTextArea.isEnabled = false
            ApplicationManager.getApplication().invokeLater {
                if (selectedPaths?.size == 1) {
                    val node = selectedPaths[0].lastPathComponent as? DefaultMutableTreeNode
                    val tracePointNode = node?.userObject as? TracePointService.TracePointNode
                    val tracePoint = tracePointNode?.tracePoint
                    if (tracePoint != null) {
                        descriptionTextArea.text = tracePoint.description
                        descriptionTextArea.isEnabled = true
                    } else {
                        descriptionTextArea.text = ""
                        descriptionTextArea.isEnabled = false
                    }
                } else {
                    descriptionTextArea.text = ""
                    descriptionTextArea.isEnabled = false
                }
            }
        }


        internal fun traverseTreeNodes(node: DefaultMutableTreeNode, visitor: (DefaultMutableTreeNode) -> Boolean): Boolean {
            if (!visitor(node)) return false
            for (i in 0 until node.childCount) {
                val child = node.getChildAt(i) as? DefaultMutableTreeNode ?: continue
                if (!traverseTreeNodes(child, visitor)) return false
            }
            return true
        }

        private fun fullyUpdateTreeModel(rootTracePointNodes: List<TracePointService.TracePointNode>, tree: JTree, expandedIds: Set<String>) {
            println("fullyUpdateTreeModel triggered")
            isUpdatingTree = true
            val selectedIds = service.getSelectedTracePointIds()
            rootTreeNode.removeAllChildren()
            treeNodeMap.clear()
            service.traverseTracePointNodes {tp ->
                val node = DefaultMutableTreeNode(tp)
                treeNodeMap[tp.id] = node
                tp
            }
            service.traverseTracePointNodes {tp ->
                treeNodeMap[tp.id]?.let { node ->
                    if (tp.parentId == null) {
                        rootTreeNode.add(node)
                    } else {
                        treeNodeMap[tp.parentId]?.add(node)
                            ?: thisLogger().warn("Parent not found for ${tp.tracePoint.name} (${tp.parentId})")
                    }
                }
                tp
            }

            treeModel.reload()
            // Expand
            val pathsToExpand = mutableSetOf<TreePath>()
            traverseTreeNodes(rootTreeNode) { node ->
                val tp = (node.userObject as? TracePointService.TracePointNode)
                if (tp != null && expandedIds.contains(tp.id)) {
                    pathsToExpand.add(TreePath(node.path))
                }
                true
            }
            pathsToExpand.forEach { tree.expandPath(it) }

            isUpdatingTree = false
        }

        private fun findNodeByTracePointId(id: String): DefaultMutableTreeNode? {
            var result: DefaultMutableTreeNode? = null
            traverseTreeNodes(rootTreeNode) { node ->
                val tp = (node.userObject as? TracePointService.TracePointNode)
                if (tp?.id == id) {
                    result = node
                    false
                } else true
            }
            return result
        }
    }
}
