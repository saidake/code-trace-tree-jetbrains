package com.simi.labs.workflowtrace.toolWindow

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBList
import com.intellij.ui.content.ContentFactory
import com.simi.labs.workflowtrace.services.TracePointService
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import kotlin.math.max
import kotlin.math.min

class MyToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val myToolWindow = MyToolWindow(toolWindow)
        val content = ContentFactory.getInstance().createContent(myToolWindow.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true

    class MyToolWindow(private val toolWindow: ToolWindow) {
        private val service = toolWindow.project.service<TracePointService>()
        private val listModel = DefaultListModel<TracePointService.TracePoint>()

        fun getContent() = JBList<TracePointService.TracePoint>(listModel).apply {
            setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
            service.addTracePointListener { tracePoints ->
                thisLogger().info("Updating tool window with ${tracePoints.size} trace points")
                listModel.clear()
                tracePoints.forEach { listModel.addElement(it) }
                val selectedIndices = tracePoints
                    .mapIndexedNotNull { index, tracePoint ->
                        if (service.isTracePointSelected(tracePoint.id)) index else null
                    }
                if (selectedIndices.isNotEmpty()) {
                    setSelectedIndices(selectedIndices.toIntArray())
                }
            }
            cellRenderer = TracePointListRenderer(service)
            // Enable drag-and-drop reordering
            dragEnabled = true
            transferHandler = object : TransferHandler() {
                private var draggedIndex = -1

                override fun createTransferable(c: JComponent): Transferable? {
                    val list = c as? JList<*> ?: return null
                    draggedIndex = list.selectedIndex
                    if (draggedIndex < 0) return null
                    val tracePoint = listModel.getElementAt(draggedIndex)
                    return object : Transferable {
                        override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.stringFlavor)
                        override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean = flavor == DataFlavor.stringFlavor
                        override fun getTransferData(flavor: DataFlavor?): Any = tracePoint.id
                    }
                }

                override fun getSourceActions(c: JComponent): Int = MOVE

                override fun canImport(support: TransferSupport): Boolean {
                    return support.isDrop && support.isDataFlavorSupported(DataFlavor.stringFlavor)
                }

                override fun importData(support: TransferSupport): Boolean {
                    if (!canImport(support)) return false
                    val dropLocation = support.dropLocation as? JList.DropLocation ?: return false
                    val dropIndex = dropLocation.index
                    if (dropIndex < 0 || dropIndex >= listModel.size()) return false
                    if (draggedIndex < 0 || draggedIndex >= listModel.size()) return false

                    try {
                        val transferable = support.transferable
                        val tracePointId = transferable.getTransferData(DataFlavor.stringFlavor) as String
                        val tracePoint = listModel.elements().toList().find { it.id == tracePointId } ?: return false

                        // Reorder in listModel
                        listModel.remove(draggedIndex)
                        listModel.add(dropIndex, tracePoint)

                        // Update TracePointService
                        val tracePoints = listModel.elements().toList()
                        service.reorderTracePoints(tracePoints.map { it.id })

                        // Update selection
                        setSelectedIndex(dropIndex)
                        thisLogger().info("Reordered trace point ${tracePoint.name} from index $draggedIndex to $dropIndex")
                        return true
                    } catch (e: Exception) {
                        thisLogger().warn("Failed to reorder trace point: ${e.message}", e)
                        return false
                    }
                }
            }
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    val index = locationToIndex(e.point)
                    if (index < 0) return
                    val tracePoint = listModel.getElementAt(index) ?: return
                    thisLogger().info("Mouse clicked on trace point: ${tracePoint.name} in ${tracePoint.fileName} at line ${tracePoint.lineNumber}")

                    if (e.clickCount == 1 && e.button == MouseEvent.BUTTON1) {
                        if (e.isShiftDown) {
                            // Shift+click: Select range from nearest selected item
                            val  selectedIndices = selectedIndices
                            if (selectedIndices.isNotEmpty()) {
                                val minSelectedIndex = selectedIndices.minOrNull() ?: index
                                val maxSelectedIndex = selectedIndices.maxOrNull() ?: index
                                val nearestIndex = if (index < minSelectedIndex) minSelectedIndex else maxSelectedIndex
                                val rangeStart = min(index, nearestIndex)
                                val rangeEnd = max(index, nearestIndex)
                                val newSelectedIds = (rangeStart..rangeEnd)
                                    .mapNotNull { if (it < listModel.size()) listModel.getElementAt(it)?.id else null }
                                service.selectTracePoints(newSelectedIds)
                            } else {
                                // No prior selection: Select only clicked item
                                service.selectTracePoints(listOf(tracePoint.id))
                            }
                        } else if (e.isControlDown) {
                            // Ctrl+click: Toggle selection
                            service.toggleTracePointSelection(tracePoint.id)
                        } else {
                            // Single click: Select only this trace point
                            service.selectTracePoints(listOf(tracePoint.id))
                        }
                    } else if (e.clickCount == 2 && e.button == MouseEvent.BUTTON1 && !e.isControlDown && !e.isShiftDown) {
                        // Double-click: Navigate only if neither Ctrl nor Shift is held
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
                    val index = locationToIndex(e.point)
                    if (index < 0) return
                    val tracePoint = listModel.getElementAt(index) ?: return
                    val selectedTracePoints = selectedValuesList ?: emptyList()
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
    }
}