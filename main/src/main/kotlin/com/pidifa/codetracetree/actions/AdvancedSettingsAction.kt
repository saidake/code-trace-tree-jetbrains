/*
 * Copyright (C) 2025-2026 Code Trace Tree Contributors
 *
 * SPDX-License-Identifier: MIT
 */
package com.pidifa.codetracetree.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.ColorPicker
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.pidifa.codetracetree.services.TracePointService
import com.pidifa.codetracetree.storage.AdvancedSettings
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSeparator

/**
 * Right-most toolbar action: opens Advanced Settings (highlight colors, import/export).
 */
class AdvancedSettingsAction : AnAction(
    null,
    "Advanced settings (highlight colors, import/export)",
    AllIcons.Actions.More
), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = project.service<TracePointService>()
        AdvancedSettingsDialog(project, service).show()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
        e.presentation.text = "Advanced Settings"
        e.presentation.description = "Advanced settings (highlight colors, import/export)"
        e.presentation.icon = AllIcons.Actions.More
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

private class AdvancedSettingsDialog(
    private val project: Project,
    private val service: TracePointService
) : DialogWrapper(project) {

    private var lightColor: Color = service.getAdvancedSettings().lightColor()
    private var darkColor: Color = service.getAdvancedSettings().darkColor()
    private val lightSwatch = ColorSwatch(lightColor)
    private val darkSwatch = ColorSwatch(darkColor)

    init {
        title = "Code Trace Tree — Advanced Settings"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val form = JPanel(GridBagLayout()).apply {
            border = JBUI.Borders.empty(8)
        }

        fun constraints(x: Int, y: Int, weightX: Double = 0.0, fill: Int = GridBagConstraints.NONE): GridBagConstraints {
            return GridBagConstraints().apply {
                gridx = x
                gridy = y
                this.weightx = weightX
                this.fill = fill
                anchor = GridBagConstraints.WEST
                insets = Insets(4, 0, 4, 8)
            }
        }

        form.add(JBLabel("Highlight line background (light theme):"), constraints(0, 0))
        form.add(colorRow(lightSwatch) {
            val picked = ColorPicker.showDialog(
                contentPanel,
                "Highlight Line Background (Light Theme)",
                lightColor,
                false,
                null,
                true
            )
            if (picked != null) {
                lightColor = picked
                lightSwatch.setColor(picked)
            }
        }, constraints(1, 0, 1.0, GridBagConstraints.HORIZONTAL))

        form.add(JBLabel("Highlight line background (dark theme):"), constraints(0, 1))
        form.add(colorRow(darkSwatch) {
            val picked = ColorPicker.showDialog(
                contentPanel,
                "Highlight Line Background (Dark Theme)",
                darkColor,
                false,
                null,
                true
            )
            if (picked != null) {
                darkColor = picked
                darkSwatch.setColor(picked)
            }
        }, constraints(1, 1, 1.0, GridBagConstraints.HORIZONTAL))

        val hintConstraints = constraints(0, 2, 1.0, GridBagConstraints.HORIZONTAL).apply {
            gridwidth = 2
            insets = Insets(12, 0, 0, 0)
        }
        form.add(
            JBLabel("Shared across projects and IDEs. Defaults: #FFFFC8 (light), #236C60 (dark).").apply {
                foreground = JBColor.GRAY
            },
            hintConstraints
        )

        val dataPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(16, 8, 0, 8)
            alignmentX = JComponent.LEFT_ALIGNMENT
            add(JSeparator())
            add(Box.createVerticalStrut(12))
            add(JBLabel("Data").apply {
                font = font.deriveFont(font.style or java.awt.Font.BOLD)
                alignmentX = JComponent.LEFT_ALIGNMENT
            })
            add(Box.createVerticalStrut(4))
            add(JBLabel("Export or import profile XML.").apply {
                foreground = JBColor.GRAY
                alignmentX = JComponent.LEFT_ALIGNMENT
            })
            add(Box.createVerticalStrut(8))
            add(JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
                alignmentX = JComponent.LEFT_ALIGNMENT
                add(JButton("Export Trace Points").apply {
                    addActionListener { runToolbarAction(ExportTracePointsAction()) }
                })
                add(JButton("Import Trace Points").apply {
                    addActionListener { runToolbarAction(ImportTracePointsAction()) }
                })
            })
        }

        val wrap = JPanel(BorderLayout())
        wrap.add(form, BorderLayout.NORTH)
        wrap.add(dataPanel, BorderLayout.CENTER)
        wrap.preferredSize = Dimension(480, 260)
        return wrap
    }

    private fun runToolbarAction(action: AnAction) {
        val context: DataContext = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .build()
        val event = AnActionEvent.createFromDataContext("CodeTraceTree.AdvancedSettings", null, context)
        action.actionPerformed(event)
    }

    override fun doOKAction() {
        service.setAdvancedSettings(
            AdvancedSettings(
                highlightLineBackgroundLight = AdvancedSettings.toHex(lightColor),
                highlightLineBackgroundDark = AdvancedSettings.toHex(darkColor)
            )
        )
        super.doOKAction()
    }

    private fun colorRow(swatch: ColorSwatch, onChoose: () -> Unit): JPanel {
        val choose = JButton("Choose…").apply {
            addActionListener { onChoose() }
        }
        return JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            add(swatch)
            add(choose)
        }
    }
}

private class ColorSwatch(initial: Color) : JPanel() {
    init {
        preferredSize = Dimension(48, 24)
        minimumSize = preferredSize
        border = BorderFactory.createLineBorder(JBColor.border())
        setColor(initial)
    }

    fun setColor(color: Color) {
        background = color
        toolTipText = AdvancedSettings.toHex(color)
        repaint()
    }
}
