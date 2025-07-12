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
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultListModel
import javax.swing.JPopupMenu
import javax.swing.ListSelectionModel
import javax.swing.JMenuItem
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
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    val index = locationToIndex(e.point)
                    if (index < 0) return
                    val tracePoint = listModel.getElementAt(index) ?: return
                    thisLogger().info("Mouse clicked on trace point: ${tracePoint.name} in ${tracePoint.fileName} at line ${tracePoint.lineNumber}")

                    if (e.clickCount == 1 && e.button == MouseEvent.BUTTON1) {
                        if (e.isShiftDown) {
                            // Shift+click: Select range from nearest selected item
                            val selectedIndices = selectedIndices
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