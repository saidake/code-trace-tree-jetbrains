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
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import java.util.*
import javax.swing.*

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

        fun getContent() = Tree(treeModel).apply {
            isRootVisible = false
            selectionModel.selectionMode = javax.swing.tree.TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION
            service.addTracePointListener { tracePoints ->
                thisLogger().info("Updating tool window with ${tracePoints.size} trace points")
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
            cellRenderer = TracePointTreeRenderer(service)
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
                    val dropNode = dropLocation.path?.lastPathComponent as? DefaultMutableTreeNode ?: return false
                    val draggedTracePoint = draggedNode?.userObject as? TracePointService.TracePoint ?: return false
                    val dropTracePoint = (dropNode.userObject as? TracePointService.TracePoint)
                    // Prevent dropping a node onto itself or its own descendants
                    if (dropTracePoint?.id == draggedTracePoint.id) return false
                    var node: DefaultMutableTreeNode? = dropNode
                    while (node != null && node != rootNode) {
                        val nodeTracePoint = node.userObject as? TracePointService.TracePoint
                        if (nodeTracePoint?.id == draggedTracePoint.id) return false
                        node = node.parent as? DefaultMutableTreeNode
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

                    try {
                        // Compute insertion index
                        val parentNode = if (dropNode.userObject is TracePointService.TracePoint) dropNode else dropNode.parent as? DefaultMutableTreeNode ?: rootNode
                        val dropIndex = if (dropNode == parentNode) {
                            parentNode.childCount
                        } else {
                            val parent = dropNode.parent as? DefaultMutableTreeNode
                            parent?.getIndex(dropNode)?.plus(1) ?: parentNode.childCount
                        }
                        // Remove from current parent
                        (draggedNode.parent as? DefaultMutableTreeNode)?.remove(draggedNode)
                        // Insert at new location
                        parentNode.insert(draggedNode, dropIndex)
                        // Update parentId in TracePoint
                        val newParentId = (parentNode.userObject as? TracePointService.TracePoint)?.id
                        val newTracePoint = draggedTracePoint.copy(parentId = newParentId)
                        val index = service.getTracePoints().indexOf(draggedTracePoint)
                        if (index != -1) {
                            val tracePoints = service.getTracePoints().toMutableList()
                            tracePoints[index] = newTracePoint
                            service.reorderTracePoints(tracePoints.map { it.id })
                        }
                        treeModel.reload()
                        // Update selection
                        selectionPath = TreePath(draggedNode.path)
                        thisLogger().info("Reordered trace point ${draggedTracePoint.name} to parent ${newParentId ?: "root"} at index $dropIndex")
                        return true
                    } catch (e: Exception) {
                        thisLogger().warn("Failed to reorder trace point: ${e.message}", e)
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
                    thisLogger().info("Mouse clicked on trace point: ${tracePoint.name} in ${tracePoint.fileName} at line ${tracePoint.lineNumber}")

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
                        thisLogger().info("Double-clicked trace point: ${tracePoint.name}")
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