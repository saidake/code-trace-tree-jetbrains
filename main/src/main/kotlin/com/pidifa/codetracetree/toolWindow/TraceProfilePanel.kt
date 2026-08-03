/*
 * Copyright (C) 2025-2026 Code Trace Tree Contributors
 *
 * SPDX-License-Identifier: MIT
 */
package com.pidifa.codetracetree.toolWindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.pidifa.codetracetree.services.TracePointService
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.accessibility.Accessible
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListCellRenderer
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.plaf.basic.ComboPopup

/**
 * Profile selector shown under the toolbar: ComboBox + Add button.
 * Expanded list items show a delete control (except when only one profile remains).
 */
class TraceProfilePanel(
    private val project: Project,
    private val service: TracePointService
) : JPanel(GridBagLayout()) {

    private val comboModel = DefaultComboBoxModel<String>()
    private val comboBox = ProfileComboBox(comboModel) { name -> deleteProfile(name) }
    private var updatingUi = false

    init {
        border = JBUI.Borders.empty(4, 8, 6, 6)
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT

        val label = JBLabel("Profile:").apply {
            border = JBUI.Borders.emptyRight(8)
        }

        comboBox.apply {
            maximumRowCount = 12
            // Prefer natural size; widen a bit for typical profile names
            preferredSize = Dimension(JBUI.scale(160), preferredSize.height)
            minimumSize = preferredSize
            addActionListener {
                if (updatingUi) return@addActionListener
                val selected = selectedItem as? String ?: return@addActionListener
                if (selected != service.getActiveProfileName()) {
                    service.switchProfile(selected)
                }
            }
        }

        val addButton = JButton(AllIcons.General.Add).apply {
            toolTipText = "Add Trace Profile"
            isOpaque = false
            isContentAreaFilled = false
            border = JBUI.Borders.empty(2)
            preferredSize = Dimension(JBUI.scale(24), JBUI.scale(24))
            minimumSize = preferredSize
            maximumSize = preferredSize
            addActionListener { promptAddProfile() }
        }

        val gbc = GridBagConstraints().apply {
            gridy = 0
            insets = Insets(0, 0, 0, 0)
            anchor = GridBagConstraints.WEST
            fill = GridBagConstraints.NONE
        }

        gbc.gridx = 0
        gbc.weightx = 0.0
        add(label, gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.insets = Insets(0, 0, 0, JBUI.scale(4))
        add(comboBox, gbc)

        gbc.gridx = 2
        gbc.weightx = 0.0
        gbc.fill = GridBagConstraints.NONE
        gbc.insets = Insets(0, 0, 0, 0)
        add(addButton, gbc)

        // Size to the ComboBox so BoxLayout in the tool window does not clip its bottom border
        val rowHeight = comboBox.preferredSize.height + border.getBorderInsets(this).top +
            border.getBorderInsets(this).bottom
        preferredSize = Dimension(Short.MAX_VALUE.toInt(), rowHeight)
        maximumSize = Dimension(Int.MAX_VALUE, rowHeight)
        minimumSize = Dimension(0, rowHeight)

        service.addProfileListener { refreshFromService() }
        refreshFromService()
    }

    private fun promptAddProfile() {
        val name = Messages.showInputDialog(
            project,
            "Enter a name for the new trace profile:",
            "Add Trace Profile",
            null,
            "",
            null
        )?.trim() ?: return

        if (name.isEmpty()) {
            Messages.showWarningDialog(project, "Profile name cannot be empty.", "Add Trace Profile")
            return
        }
        if (!service.addProfile(name)) {
            Messages.showWarningDialog(
                project,
                "A profile named \"$name\" already exists.",
                "Add Trace Profile"
            )
        }
    }

    private fun deleteProfile(name: String) {
        if (service.getProfileNames().size <= 1) return
        comboBox.hidePopup()
        val confirm = Messages.showYesNoDialog(
            project,
            "Delete profile \"$name\" and all of its trace points?",
            "Delete Trace Profile",
            null
        )
        if (confirm != Messages.YES) return
        service.deleteProfile(name)
    }

    private fun refreshFromService() {
        updatingUi = true
        try {
            comboModel.removeAllElements()
            service.getProfileNames().forEach { comboModel.addElement(it) }
            comboModel.selectedItem = service.getActiveProfileName()
        } finally {
            updatingUi = false
        }
    }

    private class ProfileComboBox(
        model: DefaultComboBoxModel<String>,
        private val onDelete: (String) -> Unit
    ) : ComboBox<String>(model) {
        private var mouseHandler: CellButtonsMouseListener? = null
        private var listRenderer: ProfileListCellRenderer? = null

        init {
            ensureRenderer()
            addPopupMenuListener(object : javax.swing.event.PopupMenuListener {
                override fun popupMenuWillBecomeVisible(e: javax.swing.event.PopupMenuEvent?) {
                    configurePopupList()
                    attachMouseHandler()
                }

                override fun popupMenuWillBecomeInvisible(e: javax.swing.event.PopupMenuEvent?) {}
                override fun popupMenuCanceled(e: javax.swing.event.PopupMenuEvent?) {}
            })
        }

        fun popupList(): JList<*>? {
            val child = (this as Accessible).accessibleContext.getAccessibleChild(0)
            return (child as? ComboPopup)?.list
        }

        fun requestDelete(name: String) = onDelete(name)

        fun cellRenderer(): ProfileListCellRenderer = ensureRenderer()

        private fun ensureRenderer(): ProfileListCellRenderer {
            val existing = listRenderer
            if (existing != null) return existing
            return ProfileListCellRenderer().also {
                listRenderer = it
                setRenderer(it)
            }
        }

        private fun configurePopupList() {
            val list = popupList() ?: return
            list.fixedCellHeight = JBUI.scale(24)
            list.border = JBUI.Borders.empty(4, 0, 4, 0)
        }

        private fun attachMouseHandler() {
            val list = popupList() ?: return
            mouseHandler?.let {
                list.removeMouseListener(it)
                list.removeMouseMotionListener(it)
            }
            mouseHandler = CellButtonsMouseListener(this).also {
                list.addMouseListener(it)
                list.addMouseMotionListener(it)
            }
        }

        override fun updateUI() {
            mouseHandler = null
            listRenderer = null
            super.updateUI()
            ensureRenderer()
        }
    }

    private class ProfileListCellRenderer : ListCellRenderer<String> {
        private val labelRenderer = DefaultListCellRenderer()
        private var rolloverIndex = -1

        private val deleteButton = JButton(AllIcons.General.Remove).apply {
            isBorderPainted = false
            isContentAreaFilled = false
            isFocusable = false
            isOpaque = false
            preferredSize = Dimension(JBUI.scale(16), JBUI.scale(16))
            toolTipText = "Delete profile"
        }

        private val panel = JPanel(BorderLayout()).apply {
            isOpaque = true
            border = JBUI.Borders.empty(2, 8, 2, 6)
        }

        fun setRolloverIndex(index: Int, list: JList<*>?) {
            if (rolloverIndex != index) {
                rolloverIndex = index
                list?.repaint()
            }
        }

        fun isHitDeleteButton(list: JList<*>, point: Point): Boolean {
            val index = list.locationToIndex(point)
            if (index < 0 || list.model.size <= 1) return false
            val bounds = list.getCellBounds(index, index) ?: return false
            val buttonWidth = deleteButton.preferredSize.width + JBUI.scale(10)
            return point.x >= bounds.x + bounds.width - buttonWidth
        }

        override fun getListCellRendererComponent(
            list: JList<out String>?,
            value: String?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            val label = labelRenderer.getListCellRendererComponent(
                list, value, index, isSelected, cellHasFocus
            ) as JComponent
            label.isOpaque = false
            label.border = JBUI.Borders.empty()

            // Closed combo (selected value shown in the field) — no delete button
            if (index < 0) {
                return label
            }

            panel.removeAll()
            panel.add(label, BorderLayout.CENTER)

            val canDelete = (list?.model?.size ?: 0) > 1
            if (canDelete) {
                deleteButton.isVisible = true
                val model = deleteButton.model
                model.isRollover = index == rolloverIndex
                model.isArmed = index == rolloverIndex
                panel.add(deleteButton, BorderLayout.EAST)
            }

            panel.background = if (isSelected) list?.selectionBackground else list?.background
            panel.foreground = if (isSelected) list?.selectionForeground else list?.foreground
            return panel
        }
    }

    private class CellButtonsMouseListener(
        private val comboBox: ProfileComboBox
    ) : MouseAdapter() {
        override fun mouseMoved(e: MouseEvent) {
            val list = e.component as? JList<*> ?: return
            val r = comboBox.cellRenderer()
            val index = list.locationToIndex(e.point)
            val hit = index >= 0 && r.isHitDeleteButton(list, e.point)
            r.setRolloverIndex(if (hit) index else -1, list)
            list.cursor = if (hit) {
                Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            } else {
                Cursor.getDefaultCursor()
            }
        }

        override fun mouseExited(e: MouseEvent) {
            val list = e.component as? JList<*>
            comboBox.cellRenderer().setRolloverIndex(-1, list)
            list?.cursor = Cursor.getDefaultCursor()
        }

        override fun mousePressed(e: MouseEvent) {
            val list = e.component as? JList<*> ?: return
            val r = comboBox.cellRenderer()
            if (!r.isHitDeleteButton(list, e.point)) return
            val index = list.locationToIndex(e.point)
            if (index < 0) return
            val name = list.model.getElementAt(index) as? String ?: return
            e.consume()
            comboBox.requestDelete(name)
        }

        override fun mouseReleased(e: MouseEvent) {
            val list = e.component as? JList<*> ?: return
            if (comboBox.cellRenderer().isHitDeleteButton(list, e.point)) {
                e.consume()
            }
        }
    }
}
