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
package com.pidifa.codetracetree.storage

/**
 * Where Claude Assist writes traces when enabled.
 * [CURRENT] = the active profile; [CLAUDE] = the dedicated `CLAUDE` profile.
 */
enum class ClaudeAssistTarget {
    CURRENT,
    CLAUDE;

    fun toStorage(): String = name

    companion object {
        fun fromStorage(raw: String?): ClaudeAssistTarget =
            when (raw?.trim()?.uppercase()) {
                "CLAUDE" -> CLAUDE
                else -> CURRENT
            }
    }
}
