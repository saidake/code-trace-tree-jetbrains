/*
 * Copyright (C) 2025-2026 Code Trace Tree Contributors
 *
 * SPDX-License-Identifier: MIT
 */
package com.pidifa.codetracetree.toolWindow

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory
import com.pidifa.codetracetree.services.TracePointService
import com.pidifa.codetracetree.domain.enums.TraceType
import com.pidifa.codetracetree.actions.MoveUpTracePointAction
import com.pidifa.codetracetree.actions.MoveDownTracePointAction
import com.pidifa.codetracetree.actions.ExpandSelectedTracePointAction
import com.pidifa.codetracetree.actions.CollapseAllTracePointAction
import com.pidifa.codetracetree.actions.ExportTracePointsAction
import com.pidifa.codetracetree.actions.ImportTracePointsAction
import com.pidifa.codetracetree.actions.ToggleHighlightTracePointsAction
import com.pidifa.codetracetree.actions.AdvancedSettingsAction
import com.pidifa.codetracetree.actions.ToggleDescriptionAreaAction
import com.pidifa.codetracetree.actions.ToggleMaximizeDescriptionAction
import com.pidifa.codetracetree.actions.ToggleNamePromptAction
import com.pidifa.codetracetree.GlobalIcons
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
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.ui.JBColor
import com.intellij.ui.JBSplitter
import com.pidifa.codetracetree.domain.enums.NodeListenerEventType
import java.awt.Component
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.BoxLayout
import javax.swing.KeyStroke
import javax.swing.TransferHandler


class MyToolWindowFactory : com.intellij.openapi.wm.ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val myToolWindow = MyToolWindow(toolWindow, project)
        val content = ContentFactory.getInstance().createContent(myToolWindow.getContent(), null, false)
        toolWindow.contentManager.addContent(content)

        val service = project.service<TracePointService>()
        service.setTreeRevealer { ids -> myToolWindow.revealTracePoints(ids) }

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
        private val descriptionScrollPane = JBScrollPane(descriptionTextArea).apply {
            minimumSize = Dimension(50, 48)
        }
        /** Vertical split between description (top) and trace tree (bottom); drag the divider to resize. */
        private val contentSplitter = JBSplitter(/* vertical = */ true, /* proportion = */ 0.22f).apply {
            dividerWidth = 3
            setHonorComponentsMinimumSize(true)
            setAndLoadSplitterProportionKey("CodeTraceTree.DescriptionTreeSplitter")
        }
        private lateinit var treeScrollPane: JBScrollPane
        /** When true, the tree is hidden and the description fills the content area. */
        private var descriptionMaximized = false

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
                service.addNodeListener(NodeListenerEventType.FULL_UPDATE) { nodes, restoreSelection ->
                    if (!isUpdatingTree) {
                        fullUpdateTreeModel(this,restoreSelection)
                        // Profile switch (and other full reloads) clear selection; refresh the
                        // description pane so it does not keep the previous profile's text.
                        updateDescriptionArea()
                    }
                }
                service.addNodeListener(NodeListenerEventType.PARTIAL_UPDATE) { nodes, restoreSelection ->
                    if (!isUpdatingTree) {
                        partialUpdateTreeModel( this,nodes)
                    }
                }
                cellRenderer = TracePointTreeRenderer(service, this@MyToolWindow)
                isEditable = false
                dragEnabled = true
                val copyKeyStroke = KeyStroke.getKeyStroke(
                    KeyEvent.VK_C,
                    Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx
                )
                getInputMap(JComponent.WHEN_FOCUSED).put(copyKeyStroke, "copyTraceDisplayText")
                actionMap.put("copyTraceDisplayText", object : AbstractAction() {
                    override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                        copySelectedTraceDisplayText()
                    }
                })
                transferHandler = object : TransferHandler() {
                    override fun createTransferable(c: JComponent): Transferable? {
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
                        @Suppress("UNCHECKED_CAST")
                        val draggedTracePointIds = transferable.getTransferData(tracePointDataFlavor) as? Set<String> ?: return false

                        // Skip if multiple trace points are selected
                        if(draggedTracePointIds.size!=1)return true;

                        // Skip if trying to drop inside itself or its descendant
                        val draggedTreeNode = getTreeNodeById(draggedTracePointIds.first())
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

                        // Prevent dropping on the same node
                        if (dropTracePointNode.id == draggedTracePointNode.id) return false
                        // Prevent dropping on the current parent
                        if (draggedTracePointNode.parentId==dropTracePointNode.id) return false
//                        (support.component as? JTree)?.repaint()
                        return true
                    }

                    override fun importData(support: TransferSupport): Boolean {
                        val dropLocation = support.dropLocation as? JTree.DropLocation ?: return false
                        val dropPath = dropLocation.path



                        val transferable = support.transferable
                        val draggedIds = transferable.getTransferData(tracePointDataFlavor) as? Set<String> ?: return false

                        val tree = support.component as? JTree ?: return false

                        try {
//                            tree.repaint()
                            // For each dragged trace point, move if valid
                            for (tracePointId in draggedIds) {
                                val draggedTreeNode = getTreeNodeById(tracePointId) ?: continue
                                val draggedTracePointNode = (draggedTreeNode.userObject as? TracePointService.TracePointNode) ?: continue
                                val oldParentTracePointNode = draggedTracePointNode.parentId?.let { service.getTracePointNodeById(it) }
                                // If dropping into empty space (root level), position after original parent
                                if (dropPath == null) {
                                    if(draggedTreeNode.parent==rootTreeNode)continue

                                    // Detach from old parent
                                    (draggedTreeNode.parent as? DefaultMutableTreeNode)?.remove(draggedTreeNode)
                                    oldParentTracePointNode?.children?.remove(draggedTracePointNode)

                                    // Attach under new parent
                                    val rootParentId: String? = service.findRootParentId(draggedTracePointNode)
                                    rootTreeNode.add(draggedTreeNode)
                                    draggedTracePointNode.parentId=null;
                                    if(rootParentId!=null)service.addRootTracePointNextTo(draggedTracePointNode,rootParentId)
                                    continue
                                }

                                val dropTreeNode = dropPath.lastPathComponent as? DefaultMutableTreeNode ?: return false
                                val dropTracePoint = dropTreeNode.userObject as? TracePointService.TracePointNode ?: return false

                                // Prevent dropping on the current parent
                                if (draggedTracePointNode.parentId==dropTracePoint.id) continue

                                // Skip if trying to drop inside itself or its descendant
                                var ancestorTreeNode: DefaultMutableTreeNode? = dropTreeNode
                                var invalid = false
                                while (true) {
                                    val current = ancestorTreeNode ?: break
                                    if (current == rootTreeNode) break
                                    val ancestorTracePoint = current.userObject as? TracePointService.TracePointNode
                                    if (ancestorTracePoint?.id == draggedTracePointNode.id) {
                                        invalid = true
                                        break
                                    }
                                    ancestorTreeNode = current.parent as? DefaultMutableTreeNode
                                }
                                if (invalid) continue



                                // Detach from old parent
                                (draggedTreeNode.parent as? DefaultMutableTreeNode)?.remove(draggedTreeNode)
                                oldParentTracePointNode?.children?.remove(draggedTracePointNode)
                                if(draggedTracePointNode.parentId==null)service.removeRootTracePoint(draggedTracePointNode)

                                // Attach under new parent
                                val parentNode = dropTreeNode
                                val dropIndex = parentNode.childCount
                                val newParentId = dropTracePoint.id
                                parentNode.insert(draggedTreeNode, dropIndex)
                                val newParentTracePointNode = service.getTracePointNodeById(newParentId)
                                newParentTracePointNode?.children?.add(draggedTracePointNode)
                                draggedTracePointNode.parentId = newParentId
                            }

                            // Notify listeners and reload
                            //(support.component as? JTree)?.repaint()
                            service.notifyListeners()
                            return true
                        } catch (e: Exception) {
                            thisLogger().warn("Failed to move trace points: ${e.message}", e)
                            return false
                        }
                    }
                }
                addMouseListener(object : MouseAdapter() {
                    override fun mouseClicked(e: MouseEvent) {
                        descriptionTextArea.isEnabled = false
                        val tree = this@apply
                        val path = tree.getPathForLocation(e.x, e.y) ?: run {
                            return
                        }
//                        val treePoint = SwingUtilities.convertPoint(e.component, e.point, tree)
//                        val path = tree.getPathForLocation(treePoint.x, treePoint.y) ?: return
                        val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                        val tracePointNode = node.userObject as? TracePointService.TracePointNode ?: return
                        val tracePoint = tracePointNode.tracePoint
                        if (e.clickCount == 2 && e.button == MouseEvent.BUTTON1 && !e.isControlDown && !e.isShiftDown) {
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
                        val tree = this@apply
                        val path = tree.getPathForLocation(e.x, e.y) ?: run {
                            return
                        }
//                        val treePoint = SwingUtilities.convertPoint(e.component, e.point, tree)
//                        val path = tree.getPathForLocation(treePoint.x, treePoint.y) ?: return
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
                        val copyItem = JMenuItem("Copy")
                        copyItem.addActionListener {
                            val toCopy = if (selectedTracePoints.any { it.id == tracePointNode.id }) {
                                selectedTracePoints
                            } else {
                                listOf(tracePointNode)
                            }
                            val text = toCopy.joinToString("\n") {
                                TracePointTreeRenderer.formatDisplayText(it.tracePoint)
                            }
                            CopyPasteManager.getInstance().setContents(StringSelection(text))
                        }
                        popupMenu.add(copyItem)
                        popupMenu.addSeparator()
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
                                    tracePoint.traceName,
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
                                    "Are you sure you want to delete trace point '${tracePoint.traceName}'?",
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
                        if (tracePoint.traceType == TraceType.LINE) {
                            popupMenu.addSeparator()
                            val showLineContentItem = JMenuItem("Show Line Content")
                            showLineContentItem.addActionListener {
                                val content = tracePoint.lineContent?.trim().orEmpty()
                                Messages.showInputDialog(
                                    toolWindow.project,
                                    "Saved trimmed line content (select and copy as needed):",
                                    "Line Content",
                                    null,
                                    content,
                                    null
                                )
                            }
                            popupMenu.add(showLineContentItem)
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
                            service.setExpandedTracePointIds(currentExpandedIds)
                        }
                    }

                    override fun treeCollapsed(event: TreeExpansionEvent) {
                         if (isUpdatingTree) return
                        // Avoid parent event propagation
                        val path = event.path
                        val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                        val tracePointNode = node.userObject as? TracePointService.TracePointNode ?: return
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
                            service.setExpandedTracePointIds(currentExpandedIds)
                        }
                    }
                })
            }
            // After `tree` is assigned — not inside Tree.apply — so description refresh can
            // safely read selection (lateinit would throw during construction and leave
            // the tool window empty: "Nothing to show").
            service.notifyListeners()

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
                    val selectedPaths = tree.selectionPaths
                    if (selectedPaths?.size == 1) {
                        val node = selectedPaths[0].lastPathComponent as? DefaultMutableTreeNode
                        val tracePointNode = node?.userObject as? TracePointService.TracePointNode
                        val tracePoint = tracePointNode?.tracePoint
                        if (tracePoint != null) {
                            val newDescription = descriptionTextArea.text
                            if (newDescription != tracePoint.description) {
                                service.updateTracePointDescription(tracePointNode.id, newDescription)
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
                add(ToggleHighlightTracePointsAction().apply {
                    templatePresentation.text = "Toggle Highlights"
                    templatePresentation.description = "Toggle the visibility of trace point highlights in files"
                })
                add(ToggleDescriptionAreaAction(this@MyToolWindow).apply {
                    templatePresentation.text = "Toggle Description"
                    templatePresentation.description = "Show or hide the description area for the selected trace point"
                })
                add(ToggleMaximizeDescriptionAction(this@MyToolWindow))
                add(ToggleNamePromptAction().apply {
                    templatePresentation.text = "Prompt for Name on Create"
                    templatePresentation.description =
                        "Ask for a name when creating a new trace point; when off, create with an empty name (rename later via the tree)"
                })
                add(ExportTracePointsAction().apply {
                    templatePresentation.text = "Export Trace Points"
                    templatePresentation.description = "Export the current profile or all profiles to an XML file"
                })
                add(ImportTracePointsAction().apply {
                    templatePresentation.text = "Import Trace Points"
                    templatePresentation.description = "Import trace points from a single- or multi-profile XML file"
                })
                add(ExpandSelectedTracePointAction(this@MyToolWindow).apply {
                    templatePresentation.text = "Expand Selected"
                    templatePresentation.description = "Expand the selected trace point node"
                })
                add(CollapseAllTracePointAction(this@MyToolWindow).apply {
                    templatePresentation.text = "Collapse All"
                    templatePresentation.description = "Collapse all trace point nodes"
                })
                add(AdvancedSettingsAction().apply {
                    templatePresentation.text = "Advanced Settings"
                    templatePresentation.description =
                        "Advanced settings (highlight line background color)"
                })
            }
            val actionToolbar = ActionManager.getInstance().createActionToolbar(
                ActionPlaces.TOOLWINDOW_TITLE,
                actionGroup,
                true
            )
            actionToolbar.component.isOpaque = false

            val profilePanel = TraceProfilePanel(project, service)

            treeScrollPane = JBScrollPane(tree).apply {
                minimumSize = Dimension(50, 80)
                alignmentX = Component.LEFT_ALIGNMENT
                maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
            }
            contentSplitter.apply {
                alignmentX = Component.LEFT_ALIGNMENT
                maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
            }
            applyContentLayout()

            val panel = JBPanel<JBPanel<*>>().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)

                actionToolbar.component.maximumSize = Dimension(Int.MAX_VALUE, actionToolbar.component.preferredSize.height)
                actionToolbar.component.minimumSize = actionToolbar.component.preferredSize
                actionToolbar.component.alignmentX = Component.LEFT_ALIGNMENT
                add(actionToolbar.component)

                add(profilePanel)

                add(contentSplitter)
            }
            // Must stay on a component that remains showing. Targeting the tree disables
            // toolbar actions when the tree is hidden (description maximized).
            actionToolbar.setTargetComponent(panel)

            return panel
        }

        fun getTree(): JTree = tree

        private fun copySelectedTraceDisplayText() {
            val selected = tree.selectionPaths
                ?.mapNotNull { (it.lastPathComponent as? DefaultMutableTreeNode)?.userObject as? TracePointService.TracePointNode }
                .orEmpty()
            if (selected.isEmpty()) return
            val text = selected.joinToString("\n") { TracePointTreeRenderer.formatDisplayText(it.tracePoint) }
            CopyPasteManager.getInstance().setContents(StringSelection(text))
        }

        fun revealTracePoints(ids: Set<String>) {
            if (ids.isEmpty()) return
            val pathsToSelect = mutableListOf<TreePath>()
            for (id in ids) {
                val node = treeNodeMap[id] ?: continue
                val path = TreePath(node.path)
                // Expand ancestors so the node is visible
                var parent = path.parentPath
                while (parent != null) {
                    tree.expandPath(parent)
                    parent = parent.parentPath
                }
                pathsToSelect.add(path)
            }
            if (pathsToSelect.isEmpty()) return
            tree.selectionPaths = pathsToSelect.toTypedArray()
            tree.scrollPathToVisible(pathsToSelect.first())
            tree.requestFocusInWindow()
            updateDescriptionArea()
        }

        fun isDescriptionMaximized(): Boolean = descriptionMaximized

        fun setDescriptionMaximized(maximized: Boolean) {
            if (descriptionMaximized == maximized) return
            descriptionMaximized = maximized
            if (maximized && !service.isDescriptionAreaOpened()) {
                service.setDescriptionAreaOpened(true)
            }
            applyContentLayout()
        }

        fun setDescriptionAreaVisible(visible: Boolean) {
            if (!visible && descriptionMaximized) {
                // Hiding description while maximized would leave an empty content area.
                descriptionMaximized = false
            }
            service.setDescriptionAreaOpened(visible)
            if (visible && !descriptionMaximized && contentSplitter.proportion < 0.05f) {
                contentSplitter.proportion = 0.22f
            }
            applyContentLayout()
        }

        private fun applyContentLayout() {
            if (!::treeScrollPane.isInitialized) return
            when {
                descriptionMaximized -> {
                    contentSplitter.firstComponent = descriptionScrollPane
                    contentSplitter.secondComponent = null
                }
                service.isDescriptionAreaOpened() -> {
                    contentSplitter.firstComponent = descriptionScrollPane
                    contentSplitter.secondComponent = treeScrollPane
                }
                else -> {
                    contentSplitter.firstComponent = null
                    contentSplitter.secondComponent = treeScrollPane
                }
            }
            contentSplitter.revalidate()
            contentSplitter.repaint()
        }


        private fun updateDescriptionArea() {
            if (!::tree.isInitialized) return
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

        private fun partialUpdateTreeModel(
            tree: JTree,
            nodes: List<TracePointService.TracePointNode>
        ) {
            if (nodes.isEmpty()) return
            val model = tree.model as DefaultTreeModel
            for (tp in nodes) {
                val node = treeNodeMap[tp.id]
                if (node == null) {
                    // If it's a new node
                    val parentNode = tp.parentId?.let { treeNodeMap[it] }
                    if (parentNode != null) {
                        val newNode = DefaultMutableTreeNode(tp)
                        parentNode.add(newNode)
                        treeNodeMap[tp.id] = newNode
                        model.nodesWereInserted(parentNode, intArrayOf(parentNode.childCount - 1))
                    } else {
                        // If the node already exists, only update the displayed content.
                        val newNode = DefaultMutableTreeNode(tp)
                        rootTreeNode.add(newNode)
                        treeNodeMap[tp.id] = newNode
                        model.nodesWereInserted(rootTreeNode, intArrayOf(rootTreeNode.childCount - 1))
                    }
                } else {
                    // If the node already exists, only update the displayed content.
                    node.userObject = tp
                    model.nodeChanged(node)
                }
            }

        }


        private fun fullUpdateTreeModel(tree: JTree, restoreSelection: Boolean ) {
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
                            ?: thisLogger().warn("Parent not found for ${tp.tracePoint.traceName} (${tp.parentId})")
                    }
                }
                tp
            }

            treeModel.reload()

            // Restore selection
            if(restoreSelection){
                val pathsToSelect = mutableListOf<TreePath>()
                val prevSelectedTracePointIds=service.getSelectedTracePointIds()
                traverseTreeNodes(rootTreeNode) { node ->
                    val tp = node.userObject as? TracePointService.TracePointNode
                    if (tp != null && prevSelectedTracePointIds.contains(tp.id)) {
                        pathsToSelect.add(TreePath(node.path))
                    }
                    true
                }
                tree.selectionPaths = pathsToSelect.toTypedArray()
                if (pathsToSelect.isNotEmpty()) {
                    tree.requestFocusInWindow()
                    tree.scrollPathToVisible(pathsToSelect.first())
                }
            }


            // Expand trace points
            val pathsToExpand = mutableSetOf<TreePath>()
            val expandedIds=service.getExpandedTracePointIds()
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

        private fun getTreeNodeById(id: String): DefaultMutableTreeNode? {
            return treeNodeMap[id]
        }
    }
}
