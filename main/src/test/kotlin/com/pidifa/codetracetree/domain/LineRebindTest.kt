/*
 * Copyright (C) 2025-2026 Code Trace Tree Contributors
 *
 * SPDX-License-Identifier: MIT
 */
package com.pidifa.codetracetree.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LineRebindTest {
    private fun tip(
        lineNumber: Int,
        content: String? = "target()",
        isValid: Boolean = true,
        totalOccurrences: Int = 1,
        occurrenceIndex: Int = 1,
    ) = LineAnchor(lineNumber, content, isValid, totalOccurrences, occurrenceIndex)

    @Test
    fun `keeps the tip when the exact line still matches`() {
        val rebound = LineRebind.apply(tip(2), listOf("a()", "target()", "c()"))
        assertEquals(2, rebound.lineNumber)
        assertTrue(rebound.isValid)
        assertEquals(1, rebound.occurrenceIndex)
    }

    @Test
    fun `moves the tip when unique content appears elsewhere on file open`() {
        val rebound = LineRebind.apply(tip(2), listOf("a()", "b()", "target()"))
        assertEquals(3, rebound.lineNumber)
        assertTrue(rebound.isValid)
    }

    @Test
    fun `uses a stable occurrence index when the same line is duplicated`() {
        val rebound = LineRebind.apply(
            tip(5, totalOccurrences = 3, occurrenceIndex = 2),
            listOf("target()", "x", "target()", "y", "changed()", "z", "target()"),
        )
        assertEquals(3, rebound.lineNumber)
        assertEquals(2, rebound.occurrenceIndex)
        assertEquals(3, rebound.totalOccurrences)
    }

    @Test
    fun `picks the nearest remaining match when occurrence count changed`() {
        val rebound = LineRebind.apply(tip(10), listOf("x", "target()", "y", "target()"))
        assertEquals(4, rebound.lineNumber)
        assertTrue(rebound.isValid)
    }

    @Test
    fun `marks the tip invalid when content is gone`() {
        val rebound = LineRebind.apply(tip(2), listOf("a()", "b()"))
        assertFalse(rebound.isValid)
        assertEquals(0, rebound.totalOccurrences)
    }

    @Test
    fun `toolbar Recheck invalidates a missing LINE file`() {
        val result = Recheck.recheckTrace(RecheckKind.LINE, tip(2), exists = false, isDirectory = false)
        assertFalse(result.isValid)
    }

    @Test
    fun `toolbar Recheck rebinds LINE against current bytes`() {
        val result = Recheck.recheckTrace(
            RecheckKind.LINE,
            tip(1),
            exists = true,
            isDirectory = false,
            lines = listOf("a()", "target()"),
        )
        assertEquals(2, result.lineNumber)
        assertTrue(result.isValid)
    }

    @Test
    fun `FILE and DIRECTORY Recheck use path kind`() {
        assertTrue(LineRebind.pathTraceIsValid(PathKind.FILE, exists = true, isDirectory = false))
        assertFalse(LineRebind.pathTraceIsValid(PathKind.FILE, exists = true, isDirectory = true))
        assertTrue(LineRebind.pathTraceIsValid(PathKind.DIRECTORY, exists = true, isDirectory = true))
        val dir = Recheck.recheckTrace(
            RecheckKind.DIRECTORY,
            tip(0, content = null),
            exists = true,
            isDirectory = true,
        )
        assertTrue(dir.isValid)
    }
}
