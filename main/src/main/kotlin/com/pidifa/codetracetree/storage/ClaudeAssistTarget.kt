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
 * Where Agent Notes writes traces when enabled.
 * [CURRENT] = the active profile; [AGENT] = the dedicated `AGENT` profile.
 *
 * Storage value `CLAUDE` is accepted on read and migrated to [AGENT].
 */
enum class ClaudeAssistTarget {
    CURRENT,
    AGENT;

    fun toStorage(): String = name

    companion object {
        /** Legacy storage / profile name before the AGENT rename. */
        const val LEGACY_CLAUDE = "CLAUDE"

        fun fromStorage(raw: String?): ClaudeAssistTarget =
            when (raw?.trim()?.uppercase()) {
                "AGENT", LEGACY_CLAUDE -> AGENT
                else -> CURRENT
            }
    }
}
