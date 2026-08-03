/*
 * Copyright (C) 2025-2026 Code Trace Tree Contributors
 *
 * SPDX-License-Identifier: MIT
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
