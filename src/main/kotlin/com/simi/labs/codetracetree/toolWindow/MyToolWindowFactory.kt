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

        fun beginTreeUpdate() {
            isUpdatingTree = true
        }

        fun endTreeUpdate() {
            isUpdatingTree = false
        }


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
                    override fun createTransferable(c: JComponent): Transferable? {
                        println("createTransferable triggered")
                        val tree = c as? JTree ?: return null
                        val path = tree.selectionPath ?: return null
                        return object : Transferable {
                            override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(tracePointDataFlavor)
                            override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean = flavor == tracePointDataFlavor
                            override fun getTransferData(flavor: DataFlavor?): Set<String>{
                                if (flavor == tracePointDataFlavor) {
                                    return service.getSelectedTracePointIds()
                                }
                                throw UnsupportedFlavorException(flavor)
                            }
                        }
                    }

                    override fun getSourceActions(c: JComponent): Int = MOVE

                    override fun canImport(support: TransferSupport): Boolean {
//                        println("canImport triggered")
                        // Ensure drop is valid
                        if (!support.isDrop || support.component !is JTree) return false
                        if (!support.isDataFlavorSupported(tracePointDataFlavor)) return false

                        val dropLocation = support.dropLocation as? JTree.DropLocation ?: return false
                        val dropPath = dropLocation.path

                        // Set the tracking point as a root trace point
                        if (dropPath == null) {
                            return true
                        }

                        val dropTreeNode = dropPath.lastPathComponent as? DefaultMutableTreeNode ?: return false
                        val dropTracePointNode = dropTreeNode.userObject as? TracePointService.TracePointNode ?: return false
                        val transferable = support.transferable
                        val draggedTracePointIds = transferable.getTransferData(tracePointDataFlavor) as? Set<String> ?: return false

                        // Skip if multiple trace points are selected
                        if(draggedTracePointIds.size!=1)return true;

                        // Skip if trying to drop inside itself or its descendant
                        val draggedTreeNode = findTreeNodeById(draggedTracePointIds.first())
                        val draggedTracePointNode = (draggedTreeNode?.userObject as? TracePointService.TracePointNode) ?: return false
                        var ancestorTreeNode: DefaultMutableTreeNode? = dropTreeNode
                        var invalid = false
                        while (ancestorTreeNode != null && ancestorTreeNode != rootTreeNode) {
                            val ancestorTracePoint = ancestorTreeNode.userObject as? TracePointService.TracePointNode
                            if (ancestorTracePoint?.id == draggedTracePointNode.id) {
                                invalid = true
                                break
                            }
                            ancestorTreeNode = ancestorTreeNode.parent as? DefaultMutableTreeNode
                        }
                        if (invalid) return false

                        // Prevent dropping on the same node or its descendants
                        if (dropTracePointNode.id == draggedTracePointNode.id) return false
                        // Prevent dropping on the current parent
                        if (draggedTracePointNode.parentId==dropTracePointNode.id) return false
//                        (support.component as? JTree)?.repaint()
                        return true
                    }

                    override fun importData(support: TransferSupport): Boolean {
                        println("importData triggered")
                        val dropLocation = support.dropLocation as? JTree.DropLocation ?: return false
                        val dropPath = dropLocation.path



                        val transferable = support.transferable
                        val draggedTracePointIds = transferable.getTransferData(tracePointDataFlavor) as? Set<String> ?: return false

                        val tree = support.component as? JTree ?: return false

                        try {
//                            tree.repaint()
                            // For each dragged trace point, move if valid
                            for (tracePointId in draggedTracePointIds) {
                                val draggedTreeNode = findTreeNodeById(tracePointId) ?: continue
                                val draggedTracePointNode = (draggedTreeNode.userObject as? TracePointService.TracePointNode) ?: continue
                                // Set the tracking point as a root trace point
                                if (dropPath == null) {
                                    if(draggedTreeNode.parent==rootTreeNode)continue

                                    // Detach from old parent
                                    (draggedTreeNode.parent as? DefaultMutableTreeNode)?.remove(draggedTreeNode)
                                    val oldParentTracePointNode = draggedTracePointNode.parentId?.let { service.getTracePointById(it) }
                                    oldParentTracePointNode?.children?.remove(draggedTracePointNode)

                                    // Attach under new parent
                                    var tempTP: TracePointService.TracePointNode? = draggedTracePointNode
                                    var rootParentId: String? = null
                                    while (tempTP != null) {
                                        rootParentId = tempTP.id
                                        if (tempTP.parentId == null) {
                                            break
                                        }
                                        tempTP = service.getTracePointById(tempTP.parentId!!)
                                    }

                                    rootTreeNode.add(draggedTreeNode)
                                    draggedTracePointNode.parentId=null;
                                    if(rootParentId!=null)service.addRootTracePointNextTo(draggedTracePointNode,rootParentId)
                                    println("addRootTracePointNextTo: $rootParentId")
                                    continue
                                }

                                val dropTreeNode = dropPath.lastPathComponent as? DefaultMutableTreeNode ?: return false
                                val dropTracePoint = dropTreeNode.userObject as? TracePointService.TracePointNode ?: return false

                                // Skip if trying to drop inside itself or its descendant
                                var ancestorTreeNode: DefaultMutableTreeNode? = dropTreeNode
                                var invalid = false
                                while (ancestorTreeNode != null && ancestorTreeNode != rootTreeNode) {
                                    val ancestorTracePoint = ancestorTreeNode.userObject as? TracePointService.TracePointNode
                                    if (ancestorTracePoint?.id == draggedTracePointNode.id) {
                                        invalid = true
                                        break
                                    }
                                    ancestorTreeNode = ancestorTreeNode.parent as? DefaultMutableTreeNode
                                }
                                if (invalid) continue

                                // Prevent dropping on the current parent
                                if (draggedTracePointNode.parentId==dropTracePoint.id) continue

                                // Detach from old parent
                                (draggedTreeNode.parent as? DefaultMutableTreeNode)?.remove(draggedTreeNode)
                                val oldParentTracePointNode = draggedTracePointNode.parentId?.let { service.getTracePointById(it) }
                                oldParentTracePointNode?.children?.remove(draggedTracePointNode)
                                if(draggedTracePointNode.parentId==null)service.removeRootTracePoint(draggedTracePointNode.id)

                                // Attach under new parent
                                val parentNode = dropTreeNode
                                val dropIndex = parentNode.childCount
                                val newParentId = dropTracePoint.id
                                parentNode.insert(draggedTreeNode, dropIndex)
                                val newParentTracePointNode = service.getTracePointById(newParentId)
                                newParentTracePointNode?.children?.add(draggedTracePointNode)
                                draggedTracePointNode.parentId = newParentId
                            }

                            // Notify listeners and reload
                            //(support.component as? JTree)?.repaint()
                            service.notifyListeners()
                            // println("Moved ${draggedTracePointIds.size} trace points to parent ${dropTracePoint.id}")
                            return true
                        } catch (e: Exception) {
                            thisLogger().warn("Failed to move trace points: ${e.message}", e)
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
                        val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                        val tracePointNode = node.userObject as? TracePointService.TracePointNode ?: return
                        println("tracePointNode: $tracePointNode")
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
                        val selectedIds = tree.selectionPaths
                            ?.mapNotNull { (it.lastPathComponent as? DefaultMutableTreeNode)?.userObject as? TracePointService.TracePointNode }
                            ?.map { it.id }
                            ?.toMutableSet()
                            ?: mutableSetOf()

                        service.selectTracePoints(selectedIds)
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
                        if (isUpdatingTree) return
                        val path = event.path
                        val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                        val tracePointNode = node.userObject as? TracePointService.TracePointNode ?: return
                        println("treeExpanded tracePointNode: $tracePointNode")
                        val currentExpandedIds = service.getExpandedTracePointIds().toMutableSet()
                        // Collapse child trace points that are not in the expandedTracePointIds
//                        isUpdatingTree=true
//                        traverseTreeNodes(node) { node ->
//                            val curTracePointNode = node.userObject as? TracePointService.TracePointNode ?: return@traverseTreeNodes true
//                            if (!currentExpandedIds.contains(curTracePointNode.id)) {
//                                tree.collapsePath(TreePath(node.path))
//                                false
//                            }
//                            true
//                        }
//                        isUpdatingTree=false
                        // Update expandedTracePointIds
                        if (!currentExpandedIds.contains(tracePointNode.id)) {
                            currentExpandedIds.add(tracePointNode.id)
                            println("treeExpanded triggered setExpandedTracePointIds: $currentExpandedIds")
                            service.setExpandedTracePointIds(currentExpandedIds)
                            println("Expanded trace point: ${tracePointNode.id}")
                        }
                    }

                    override fun treeCollapsed(event: TreeExpansionEvent) {
                         if (isUpdatingTree) return
                        // Avoid parent event propagation
                        val path = event.path
                        val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                        val tracePointNode = node.userObject as? TracePointService.TracePointNode ?: return
                        println("treeCollapsed triggered: $tracePointNode")
                        val currentExpandedIds = service.getExpandedTracePointIds().toMutableSet()
                        // Expand child trace points that are not in the expandedTracePointIds
//                        isUpdatingTree=true
//                        traverseTreeNodes(node) { node ->
//                            val curTracePointNode = node.userObject as? TracePointService.TracePointNode ?: return@traverseTreeNodes true
//                            if (currentExpandedIds.contains(curTracePointNode.id)) {
//                                tree.expandPath(TreePath(node.path))
//                                false
//                            }
//                            true
//                        }
//                        isUpdatingTree=false
                        // Update expandedTracePointIds
                        if (currentExpandedIds.contains(tracePointNode.id)) {
                            currentExpandedIds.remove(tracePointNode.id)
                            println("treeCollapsed triggered setExpandedTracePointIds: $currentExpandedIds")
                            service.setExpandedTracePointIds(currentExpandedIds)
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

        fun collapseAll(){
            isUpdatingTree=true
            rootTreeNode.children().toList().forEach { child ->
                val treeNode = child as? DefaultMutableTreeNode
                treeNode?.let {
                    tree.collapsePath(TreePath(child.path))
                }
            }
            isUpdatingTree=false
        }

        private fun fullyUpdateTreeModel(rootTracePointNodes: List<TracePointService.TracePointNode>, tree: JTree, expandedIds: Set<String>) {
            println("fullyUpdateTreeModel triggered")
            isUpdatingTree = true
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

            // Restore selection
            val pathsToSelect = mutableListOf<TreePath>()
            val selectedTracePointIds=service.getSelectedTracePointIds()
            traverseTreeNodes(rootTreeNode) { node ->
                val tp = node.userObject as? TracePointService.TracePointNode
                if (tp != null && selectedTracePointIds.contains(tp.id)) {
                    pathsToSelect.add(TreePath(node.path))
                }
                true
            }
            tree.selectionPaths = pathsToSelect.toTypedArray()

            if (pathsToSelect.isNotEmpty()) {
                tree.requestFocusInWindow()
                tree.scrollPathToVisible(pathsToSelect.first())
            }

            // Expand trace points
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

        private fun findTreeNodeById(id: String): DefaultMutableTreeNode? {
            return treeNodeMap[id]
        }
    }
}
