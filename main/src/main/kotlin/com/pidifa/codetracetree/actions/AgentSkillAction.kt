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
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.pidifa.codetracetree.services.TracePointService
import com.pidifa.codetracetree.skill.AgentSkill
import com.pidifa.codetracetree.skill.AgentSkillNoticeStatus
import com.pidifa.codetracetree.skill.AgentSkillState
import com.pidifa.codetracetree.skill.AgentSkillStatus
import com.pidifa.codetracetree.skill.BundledSkill
import com.pidifa.codetracetree.storage.GlobalSettingsXml
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSeparator

/**
 * Toolbar action: Agent Skill status (Python, per-agent version) and install/update.
 */
class AgentSkillAction : AnAction(
    "Agent Skill",
    "Install or update the bundled Code Trace Tree agent skill",
    AllIcons.Actions.Install
), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        AgentSkillDialog(project).show()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
        e.presentation.text = "Agent Skill"
        e.presentation.description = "Install or update the bundled Code Trace Tree agent skill"
        e.presentation.icon = AllIcons.Actions.Install
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

class AgentSkillDialog(private val project: Project) : DialogWrapper(project) {
    private val pythonLabel = JBLabel()
    private val bundledLabel = JBLabel()
    private val agentsPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        alignmentX = JComponent.LEFT_ALIGNMENT
    }
    private val checkboxes = mutableMapOf<String, JBCheckBox>()

    init {
        title = "Code Trace Tree — Agent Skill"
        init()
        refresh()
    }

    override fun createCenterPanel(): JComponent {
        val wrap = JPanel(BorderLayout())
        wrap.preferredSize = Dimension(560, 380)

        val north = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(8)
            add(JBLabel("The plugin copies the bundled code-trace-tree skill into each agent's global skills folder. This plugin does not include an AI agent.").apply {
                foreground = JBColor.GRAY
                alignmentX = JComponent.LEFT_ALIGNMENT
            })
            add(Box.createVerticalStrut(8))
            bundledLabel.alignmentX = JComponent.LEFT_ALIGNMENT
            add(bundledLabel)
            add(Box.createVerticalStrut(8))
            add(JBLabel("Python 3").apply {
                font = font.deriveFont(font.style or java.awt.Font.BOLD)
                alignmentX = JComponent.LEFT_ALIGNMENT
            })
            pythonLabel.alignmentX = JComponent.LEFT_ALIGNMENT
            add(pythonLabel)
            add(JBLabel("Python is required when an agent runs skill scripts, not to copy the files.").apply {
                foreground = JBColor.GRAY
                alignmentX = JComponent.LEFT_ALIGNMENT
            })
            add(Box.createVerticalStrut(12))
            add(JSeparator())
            add(Box.createVerticalStrut(8))
            add(JBLabel("Agents (global)").apply {
                font = font.deriveFont(font.style or java.awt.Font.BOLD)
                alignmentX = JComponent.LEFT_ALIGNMENT
            })
        }

        val south = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            border = JBUI.Borders.empty(8)
            add(JButton("Install / Update").apply {
                addActionListener { installSelected() }
            })
            add(Box.createHorizontalStrut(8))
            add(JButton("Refresh").apply {
                addActionListener { refresh() }
            })
            add(Box.createHorizontalGlue())
        }

        wrap.add(north, BorderLayout.NORTH)
        wrap.add(JBScrollPane(agentsPanel), BorderLayout.CENTER)
        wrap.add(south, BorderLayout.SOUTH)
        return wrap
    }

    override fun createActions() = arrayOf(cancelAction)

    private fun refresh() {
        val bundled = try {
            BundledSkill.bundledVersion()
        } catch (_: Exception) {
            null
        }
        bundledLabel.text = "Bundled skill version: ${bundled ?: "not found"}"
        val python = AgentSkill.detectPython3()
        pythonLabel.text = if (python.ready) {
            "Ready: Python ${python.version} (${python.command})"
        } else {
            "Python 3 not found on PATH. Skill ops will fail until python3 or python is available."
        }
        pythonLabel.foreground = if (python.ready) JBColor.foreground() else JBColor.GRAY

        val statuses = AgentSkill.scanAgentStatuses(bundled ?: "0")
        agentsPanel.removeAll()
        checkboxes.clear()
        statuses.forEach { status ->
            val box = JBCheckBox(rowLabel(status)).apply {
                isSelected = status.detected &&
                    (status.state == AgentSkillState.MISSING || status.state == AgentSkillState.OUTDATED)
            }
            checkboxes[status.id] = box
            agentsPanel.add(box)
        }
        agentsPanel.revalidate()
        agentsPanel.repaint()
    }

    private fun rowLabel(status: AgentSkillStatus): String {
        val detected = if (status.detected) "detected" else "not detected"
        val installed = status.installedVersion ?: "—"
        val state = when (status.state) {
            AgentSkillState.MISSING -> "not installed"
            AgentSkillState.OUTDATED -> "outdated"
            AgentSkillState.NEWER -> "newer than bundle"
            AgentSkillState.LATEST -> "latest"
        }
        return "${status.label}  ·  $detected  ·  $installed  ·  $state"
    }

    private fun installSelected() {
        val ids = checkboxes.filter { it.value.isSelected }.keys.toList()
        if (ids.isEmpty()) {
            Messages.showWarningDialog(project, "Select at least one agent.", "Agent Skill")
            return
        }
        val bundledVersion = try {
            BundledSkill.bundledVersion()
        } catch (e: Exception) {
            Messages.showErrorDialog(project, "Bundled skill was not found: ${e.message}", "Agent Skill")
            return
        } ?: run {
            Messages.showErrorDialog(project, "Bundled skill has no version.", "Agent Skill")
            return
        }
        try {
            val dest = java.nio.file.Files.createTempDirectory("ctt-skill-install-")
            try {
                BundledSkill.copyTo(dest)
                val installed = AgentSkill.installSkillForAgents(dest, ids)
                val service = project.service<TracePointService>()
                GlobalSettingsXml.upsertAgentSkillNotice(
                    bundledVersion,
                    AgentSkillNoticeStatus.INSTALLED,
                    service.getAdvancedSettings(),
                )
                Messages.showInfoMessage(
                    project,
                    "Installed code-trace-tree v$bundledVersion for ${
                        installed.joinToString(", ") { id ->
                            AgentSkill.AGENTS.find { it.id == id.first }?.label ?: id.first
                        }
                    }.",
                    "Agent Skill",
                )
            } finally {
                dest.toFile().deleteRecursively()
            }
            refresh()
        } catch (e: Exception) {
            Messages.showErrorDialog(project, "Failed to install the agent skill: ${e.message}", "Agent Skill")
        }
    }
}
