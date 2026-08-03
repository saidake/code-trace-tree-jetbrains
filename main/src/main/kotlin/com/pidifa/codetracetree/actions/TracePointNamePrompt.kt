/*
 * Copyright (C) 2025-2026 Code Trace Tree Contributors
 *
 * SPDX-License-Identifier: MIT
 */
package com.pidifa.codetracetree.actions

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.pidifa.codetracetree.services.TracePointService

/**
 * Resolves the name used when creating a trace point.
 * Returns `null` when the user cancels a name prompt (caller should abort).
 * Returns `""` when name prompting is disabled.
 */
internal fun resolveNewTracePointName(
    project: Project,
    service: TracePointService,
    message: String,
    title: String,
    initialValue: String? = null
): String? {
    if (!service.isNamePromptEnabled()) return ""
    return Messages.showInputDialog(project, message, title, null, initialValue, null)
}
