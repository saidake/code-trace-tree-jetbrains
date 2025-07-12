package com.simi.labs.workflowtrace.toolWindow

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.treeStructure.Tree
import com.simi.labs.workflowtrace.services.TracePointService
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.Point
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.JPopupMenu
import javax.swing.JMenuItem
import javax.swing.JTree
import java.util.*
import javax.swing.JComponent
import javax.swing.TransferHandler

class MyToolWindowFactory : ToolWindowFactory {
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
        private var isHighlightOnDivider: Boolean = false
        private var dropPoint: Point? = null

        fun getContent() = Tree(treeModel).apply {
            isRootVisible = false
            selectionModel.selectionMode = javax.swing.tree.TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION
            service.addTracePointListener { tracePoints ->
                println("Updating tool window with ${tracePoints.size} trace points")
                updateTreeModel(tracePoints)
                // Restore selection
                val selectedIds = service.getTracePoints().filter { service.isTracePointSelected(it.id) }.map { it.id }
                val selectedPaths = mutableListOf<TreePath>()
                traverseNodes(rootNode) { node ->
                    val tracePoint = (node as? DefaultMutableTreeNode)?.userObject as? TracePointService.TracePoint
                    if (tracePoint != null && selectedIds.contains(tracePoint.id)) {
                        selectedPaths.add(TreePath(node.path))
                    }
                    true
                }
                if (selectedPaths.isNotEmpty()) {
                    selectionPaths = selectedPaths.toTypedArray()
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
                    val dropTracePoint = dropNode.userObject as? TracePointService.TracePoint
                    // Prevent dropping a node onto itself or its own descendants
                    if (dropTracePoint?.id == draggedTracePoint.id) return false
                    var node: DefaultMutableTreeNode? = dropNode
                    while (node != null && node != rootNode) {
                        val nodeTracePoint = node.userObject as? TracePointService.TracePoint
                        if (nodeTracePoint?.id == draggedTracePoint.id) return false
                        node = node.parent as? DefaultMutableTreeNode
                    }

                    // Update highlight for drag-over feedback
                    val tree = support.component as? JTree ?: return false
                    val dropY = dropLocation.dropPoint.y
                    val row = tree.getRowForPath(dropPath)
                    val bounds = tree.getRowBounds(row)
                    val newIsHighlightOnDivider = if (bounds != null) {
                        val nodeHeight = bounds.height
                        val relativeY = dropY - bounds.y
                        !(relativeY >= nodeHeight * 0.25 && relativeY <= nodeHeight * 0.75)
                    } else {
                        false
                    }
                    if (highlightedPath != dropPath || isHighlightOnDivider != newIsHighlightOnDivider || dropPoint != dropLocation.dropPoint) {
                        highlightedPath = dropPath
                        isHighlightOnDivider = newIsHighlightOnDivider
                        dropPoint = dropLocation.dropPoint
                        tree.repaint()
                    }

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
                        // Determine if dropped on a node (make child) or between nodes (replace divider)
                        val dropY = dropLocation.dropPoint.y
                        val row = tree.getRowForPath(dropPath)
                        val bounds = tree.getRowBounds(row)
                        val isDropOnNode = if (bounds != null) {
                            // Consider drop on node if within the middle 50% of node height
                            val nodeHeight = bounds.height
                            val relativeY = dropY - bounds.y
                            relativeY >= nodeHeight * 0.25 && relativeY <= nodeHeight * 0.75
                        } else {
                            true // Default to on-node if bounds unavailable
                        }

                        // Clear highlight after drop
                        highlightedPath = null
                        isHighlightOnDivider = false
                        dropPoint = null
                        tree.repaint()

                        val parentNode: DefaultMutableTreeNode
                        val dropIndex: Int
                        val newParentId: String?

                        if (isDropOnNode && dropNode.userObject is TracePointService.TracePoint) {
                            // Dropped on a node: make dragged node a child of drop node
                            parentNode = dropNode
                            dropIndex = parentNode.childCount // Append as last child
                            newParentId = (dropNode.userObject as TracePointService.TracePoint).id
                        } else {
                            // Dropped between nodes (on a divider)
                            parentNode = if (dropNode.userObject is TracePointService.TracePoint) {
                                dropNode.parent as? DefaultMutableTreeNode ?: rootNode
                            } else {
                                rootNode
                            }
                            dropIndex = if (bounds != null && dropNode.userObject is TracePointService.TracePoint) {
                                val parentIndex = parentNode.getIndex(dropNode)
                                if (dropY > bounds.y + bounds.height / 2) {
                                    parentIndex + 1 // Insert after drop node
                                } else {
                                    parentIndex // Insert at drop node position
                                }
                            } else {
                                parentNode.childCount // Append to end if dropped on root
                            }
                            newParentId = (parentNode.userObject as? TracePointService.TracePoint)?.id
                        }

                        // Remove dragged node from its current parent
                        (draggedNode.parent as? DefaultMutableTreeNode)?.remove(draggedNode)

                        // Insert dragged node at the new position
                        parentNode.insert(draggedNode, dropIndex)

                        // Update TracePoint parentId and order in the service
                        val updatedTracePoints = mutableListOf<TracePointService.TracePoint>()
                        traverseNodes(rootNode) { node ->
                            val tracePoint = (node as? DefaultMutableTreeNode)?.userObject as? TracePointService.TracePoint
                            if (tracePoint != null) {
                                val currentParentId = if (node.parent == rootNode) null else (node.parent as? DefaultMutableTreeNode)?.userObject?.let { it as? TracePointService.TracePoint }?.id
                                updatedTracePoints.add(tracePoint.copy(parentId = currentParentId))
                            }
                            true
                        }

                        // Update the service with the new order and parent relationships
                        service.reorderTracePoints(updatedTracePoints.map { it.id })

                        // Reload the tree model to reflect changes
                        treeModel.reload()

                        // Update selection to the dragged node
                        selectionPath = TreePath(draggedNode.path)
                        println("Moved trace point ${draggedTracePoint.name} to parent ${newParentId ?: "root"} at index $dropIndex (on node: $isDropOnNode)")
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
                        if (e.isShiftDown) {
                            val selectedPaths = tree.selectionPaths?.toList() ?: emptyList()
                            if (selectedPaths.isNotEmpty()) {
                                val newSelectedPaths = mutableListOf<TreePath>()
                                val paths = mutableListOf<TreePath>()
                                traverseNodes(rootNode) { node ->
                                    if ((node as? DefaultMutableTreeNode)?.userObject is TracePointService.TracePoint) {
                                        paths.add(TreePath(node.path))
                                    }
                                    true
                                }
                                val startIndex = paths.indexOfFirst { it.lastPathComponent == selectedPaths.minByOrNull { paths.indexOf(it) }?.lastPathComponent }
                                val endIndex = paths.indexOf(path)
                                val rangeStart = minOf(startIndex, endIndex)
                                val rangeEnd = maxOf(startIndex, endIndex)
                                for (i in rangeStart..rangeEnd) {
                                    val p = paths.getOrNull(i) ?: continue
                                    if ((p.lastPathComponent as? DefaultMutableTreeNode)?.userObject is TracePointService.TracePoint) {
                                        newSelectedPaths.add(p)
                                    }
                                }
                                tree.selectionPaths = newSelectedPaths.toTypedArray()
                                service.selectTracePoints(newSelectedPaths.mapNotNull { (it.lastPathComponent as? DefaultMutableTreeNode)?.userObject as? TracePointService.TracePoint }.map { it.id })
                            } else {
                                tree.selectionPath = path
                                service.selectTracePoints(listOf(tracePoint.id))
                            }
                        } else if (e.isControlDown) {
                            if (tree.isPathSelected(path)) {
                                tree.removeSelectionPath(path)
                                service.toggleTracePointSelection(tracePoint.id)
                            } else {
                                tree.addSelectionPath(path)
                                service.toggleTracePointSelection(tracePoint.id)
                            }
                        } else {
                            tree.selectionPath = path
                            service.selectTracePoints(listOf(tracePoint.id))
                        }
                    } else if (e.clickCount == 2 && e.button == MouseEvent.BUTTON1 && !e.isControlDown && !e.isShiftDown) {
                        println("Double-clicked trace point: ${tracePoint.name}")
                        tracePoint.navigateTo()
                        service.selectTracePoints(listOf(tracePoint.id))
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
                        service.selectTracePoints(listOf(tracePoint.id))
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
        }

        fun getHighlightedPath(): TreePath? = highlightedPath
        fun isHighlightOnDivider(): Boolean = isHighlightOnDivider
        fun getDropPoint(): Point? = dropPoint

        private fun updateTreeModel(tracePoints: List<TracePointService.TracePoint>) {
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
        }

        private fun findNodeByTracePointId(tracePointId: String): DefaultMutableTreeNode? {
            var result: DefaultMutableTreeNode? = null
            traverseNodes(rootNode) { node ->
                val tracePoint = (node as? DefaultMutableTreeNode)?.userObject as? TracePointService.TracePoint
                if (tracePoint?.id == tracePointId) {
                    result = node
                    false // Stop traversal
                } else {
                    true // Continue traversal
                }
            }
            return result
        }

        private fun traverseNodes(node: DefaultMutableTreeNode, visitor: (DefaultMutableTreeNode) -> Boolean) {
            if (!visitor(node)) return
            for (i in 0 until node.childCount) {
                val child = node.getChildAt(i) as? DefaultMutableTreeNode ?: continue
                traverseNodes(child, visitor)
            }
        }
    }
}