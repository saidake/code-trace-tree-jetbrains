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
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListTableModel
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
import javax.swing.ListSelectionModel

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

private data class AgentSkillRow(
    val status: AgentSkillStatus,
)

class AgentSkillDialog(private val project: Project) : DialogWrapper(project) {
    private val pythonLabel = JBLabel()
    private val bundledLabel = JBLabel()
    private val chooseButton = JButton("Choose agents to install")
    private val installButton = JButton("Install / Update")
    private val removeButton = JButton("Remove from installed agents")
    private val tableModel = ListTableModel<AgentSkillRow>(
        object : ColumnInfo<AgentSkillRow, String>("Agent") {
            override fun valueOf(item: AgentSkillRow): String = item.status.label
            override fun getPreferredStringValue(): String = "GitHub Copilot"
        },
        object : ColumnInfo<AgentSkillRow, String>("Installed") {
            override fun valueOf(item: AgentSkillRow): String = installedLabel(item.status)
            override fun getPreferredStringValue(): String = "2 (newer than bundle)"
        },
    )
    private val table = TableView(tableModel).apply {
        visibleRowCount = 5
        rowHeight = JBUI.scale(24)
        tableHeader.reorderingAllowed = false
        setShowGrid(false)
        intercellSpacing = Dimension(0, 0)
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        setStriped(true)
        emptyText.text = "The skill is not installed for any agent"
    }

    init {
        title = "Code Trace Tree — Agent Skill"
        init()
        refresh()
    }

    override fun createCenterPanel(): JComponent {
        val wrap = JPanel(BorderLayout())
        wrap.preferredSize = Dimension(640, 420)

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
            add(Box.createVerticalStrut(16))
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
            chooseButton.addActionListener { chooseAgentsToInstall() }
            installButton.addActionListener { installForTableAgents() }
            removeButton.addActionListener { removeFromInstalledAgents() }
            add(chooseButton)
            add(Box.createHorizontalStrut(8))
            add(installButton)
            add(Box.createHorizontalStrut(8))
            add(removeButton)
            add(Box.createHorizontalStrut(8))
            add(JButton("Refresh").apply {
                addActionListener { refresh() }
            })
            add(Box.createHorizontalGlue())
        }

        wrap.add(north, BorderLayout.NORTH)
        wrap.add(JBScrollPane(table), BorderLayout.CENTER)
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
        pythonLabel.foreground = if (python.ready) PYTHON_READY else PYTHON_MISSING

        val statuses = AgentSkill.scanAgentStatuses(bundled ?: "0")
        val installed = AgentSkill.agentsWithInstalledSkill(statuses)
        tableModel.items = installed.map { AgentSkillRow(it) }.toMutableList()
        chooseButton.isEnabled = statuses.any { it.state == AgentSkillState.MISSING }
        installButton.isEnabled = installed.isNotEmpty()
        removeButton.isEnabled = installed.isNotEmpty()
    }

    private fun installedLabel(status: AgentSkillStatus): String {
        val version = status.installedVersion
        if (status.state == AgentSkillState.MISSING || version.isNullOrBlank()) return "—"
        return "$version (${statusLabel(status.state)})"
    }

    private fun statusLabel(state: AgentSkillState): String = when (state) {
        AgentSkillState.MISSING -> "not installed"
        AgentSkillState.OUTDATED -> "outdated"
        AgentSkillState.NEWER -> "newer than bundle"
        AgentSkillState.LATEST -> "latest"
    }

    private fun installForTableAgents() {
        val ids = tableModel.items.map { it.status.id }
        if (ids.isEmpty()) {
            Messages.showWarningDialog(
                project,
                "No agents are listed. Use Choose agents to install.",
                "Agent Skill",
            )
            return
        }
        installForAgents(ids)
    }

    private fun chooseAgentsToInstall() {
        val bundled = try {
            BundledSkill.bundledVersion()
        } catch (_: Exception) {
            null
        }
        val extra = AgentSkill.scanAgentStatuses(bundled ?: "0").filter { it.state == AgentSkillState.MISSING }
        if (extra.isEmpty()) {
            Messages.showInfoMessage(project, "The skill is already installed for all known agents.", "Agent Skill")
            return
        }
        val chooser = ChooseAgentsToInstallDialog(project, extra)
        if (!chooser.showAndGet()) return
        val ids = chooser.selectedIds()
        if (ids.isEmpty()) {
            Messages.showWarningDialog(project, "Select at least one agent.", "Agent Skill")
            return
        }
        installForAgents(ids)
    }

    private fun removeFromInstalledAgents() {
        val bundled = try {
            BundledSkill.bundledVersion()
        } catch (_: Exception) {
            null
        }
        val installed = AgentSkill.agentsWithInstalledSkill(AgentSkill.scanAgentStatuses(bundled ?: "0"))
        if (installed.isEmpty()) {
            Messages.showInfoMessage(project, "The skill is not installed for any known agent.", "Agent Skill")
            return
        }
        val names = installed.joinToString(", ") { it.label }
        val choice = Messages.showYesNoDialog(
            project,
            "Remove the code-trace-tree skill from $names? This deletes each agent's global code-trace-tree folder.",
            "Remove Agent Skill",
            "Remove",
            "Cancel",
            Messages.getWarningIcon(),
        )
        if (choice != Messages.YES) return
        try {
            val removed = AgentSkill.removeSkillForAgents(installed.map { it.id })
            Messages.showInfoMessage(
                project,
                "Removed code-trace-tree from ${
                    removed.joinToString(", ") { id ->
                        AgentSkill.AGENTS.find { it.id == id.first }?.label ?: id.first
                    }
                }.",
                "Agent Skill",
            )
            refresh()
        } catch (e: Exception) {
            Messages.showErrorDialog(project, "Failed to remove the agent skill: ${e.message}", "Agent Skill")
        }
    }

    private fun installForAgents(ids: List<String>) {
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

    companion object {
        // Match VS Code Agent Skill: testing-iconPassed / error-like missing state
        private val PYTHON_READY = JBColor(0x2E7D32, 0x4CAF50)
        private val PYTHON_MISSING = JBColor(0xC62828, 0xF07178)
    }
}

private class ChooseAgentsToInstallDialog(
    project: Project,
    agents: List<AgentSkillStatus>,
) : DialogWrapper(project) {
    private val checkboxes = agents.associate { status ->
        status.id to JBCheckBox(status.label)
    }

    init {
        title = "Choose agents to install"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val list = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(8)
            add(JBLabel("Copy the bundled skill into the selected agents' global skills folders.").apply {
                foreground = JBColor.GRAY
                alignmentX = JComponent.LEFT_ALIGNMENT
            })
            add(Box.createVerticalStrut(8))
        }
        checkboxes.values.forEach { box ->
            box.alignmentX = JComponent.LEFT_ALIGNMENT
            list.add(box)
        }
        return JBScrollPane(list).apply {
            preferredSize = Dimension(420, 360)
        }
    }

    fun selectedIds(): List<String> = checkboxes.filter { it.value.isSelected }.keys.toList()
}
