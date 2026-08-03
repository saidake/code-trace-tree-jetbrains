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
 * Eagerly initializes [TracePointService] when a project opens so hybrid storage
 * (project id file + global XML with the default `main` profile) exists immediately,
 * even before the tool window or any action is used.
 */
class CodeTraceTreeProjectActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.getService(TracePointService::class.java)
    }
}
