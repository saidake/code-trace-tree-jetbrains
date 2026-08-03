/*
 * Copyright (C) 2025-2026 Code Trace Tree Contributors
 *
 * SPDX-License-Identifier: MIT
 */
package com.pidifa.codetracetree

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.pidifa.codetracetree.services.TracePointService

/**
 * Initializes [TracePointService] when a project opens so existing hybrid storage
 * (Case A / B) is loaded. New projects stay unbound until the first real use
 * (create trace point, add profile, import, or toolbar toggle).
 */
class CodeTraceTreeProjectActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.getService(TracePointService::class.java)
    }
}
