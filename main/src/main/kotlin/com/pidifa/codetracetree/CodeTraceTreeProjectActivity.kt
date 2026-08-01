/*
 * Copyright (C) 2025-2026 Code Trace Tree Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
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
