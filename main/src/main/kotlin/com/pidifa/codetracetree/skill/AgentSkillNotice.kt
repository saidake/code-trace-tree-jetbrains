/*
 * Copyright (C) 2025-2026 Code Trace Tree Contributors
 *
 * SPDX-License-Identifier: MIT
 */
package com.pidifa.codetracetree.skill

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.components.service
import com.pidifa.codetracetree.actions.AgentSkillDialog
import com.pidifa.codetracetree.services.TracePointService
import com.pidifa.codetracetree.storage.GlobalSettingsXml
import java.util.concurrent.atomic.AtomicBoolean

object AgentSkillNotice {
    private val shownThisSession = AtomicBoolean(false)

    fun maybeNotify(project: Project) {
        if (project.isDisposed) return
        if (!shownThisSession.compareAndSet(false, true)) return
        val bundled = try {
            BundledSkill.bundledVersion()
        } catch (_: Exception) {
            shownThisSession.set(false)
            return
        } ?: run {
            shownThisSession.set(false)
            return
        }
        val statuses = AgentSkill.scanAgentStatuses(bundled)
        val lastHandled = GlobalSettingsXml.readFile()?.agentSkillVersion
        if (!AgentSkill.shouldOfferSkillNotice(bundled, statuses, lastHandled)) {
            return
        }
        val kind = if (statuses.any { it.detected && it.state == AgentSkillState.OUTDATED }) "Update" else "Install"
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("Code Trace Tree")
            .createNotification(
                "$kind the Code Trace Tree agent skill (v$bundled) for your coding agents.",
                NotificationType.INFORMATION,
            )
        notification.addAction(
            NotificationAction.createSimple("Install") {
                if (!project.isDisposed) {
                    AgentSkillDialog(project).show()
                }
                notification.expire()
            }
        )
        notification.addAction(
            NotificationAction.createSimple("Dismiss") {
                val service = project.service<TracePointService>()
                GlobalSettingsXml.upsertAgentSkillNotice(
                    bundled,
                    AgentSkillNoticeStatus.DISMISSED,
                    service.getAdvancedSettings(),
                )
                notification.expire()
            }
        )
        notification.notify(project)
    }

    fun schedule(project: Project) {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) maybeNotify(project)
        }
    }
}
