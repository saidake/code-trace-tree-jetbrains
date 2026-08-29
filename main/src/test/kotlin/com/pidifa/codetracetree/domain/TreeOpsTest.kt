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

class TreeOpsTest {
    private data class Node(
        val id: String,
        var parentId: String? = null,
        val children: MutableList<Node> = mutableListOf(),
        var valid: Boolean = true,
    )

    private fun prune(nodes: MutableList<Node>, parentId: String? = null) =
        TreeOps.pruneInvalidNodes(
            nodes,
            parentId,
            { it.id },
            { it.valid },
            { it.children },
            { node, id -> node.parentId = id },
        )

    @Test
    fun `Move Up swaps a selected sibling with the one above`() {
        val siblings = mutableListOf(Node("a"), Node("b"), Node("c"))
        TreeOps.moveSiblingsUp(siblings, setOf("b")) { it.id }
        assertEquals(listOf("b", "a", "c"), siblings.map { it.id })
    }

    @Test
    fun `Move Up does not swap when the sibling above is also selected`() {
        val siblings = mutableListOf(Node("a"), Node("b"), Node("c"))
        TreeOps.moveSiblingsUp(siblings, setOf("a", "b")) { it.id }
        assertEquals(listOf("a", "b", "c"), siblings.map { it.id })
    }

    @Test
    fun `Move Down swaps a selected sibling with the one below`() {
        val siblings = mutableListOf(Node("a"), Node("b"), Node("c"))
        TreeOps.moveSiblingsDown(siblings, setOf("a")) { it.id }
        assertEquals(listOf("b", "a", "c"), siblings.map { it.id })
    }

    @Test
    fun `Move Down does not swap when the sibling below is also selected`() {
        val siblings = mutableListOf(Node("a"), Node("b"), Node("c"))
        TreeOps.moveSiblingsDown(siblings, setOf("b", "c")) { it.id }
        assertEquals(listOf("a", "b", "c"), siblings.map { it.id })
    }

    @Test
    fun `Remove Invalid drops invalid nodes and reparents valid children`() {
        val child = Node("child", parentId = "parent")
        val parent = Node("parent", valid = false, children = mutableListOf(child))
        val roots = mutableListOf(parent, Node("ok"))
        val removed = prune(roots)
        assertEquals(listOf("parent"), removed)
        assertEquals(listOf("child", "ok"), roots.map { it.id })
        assertEquals(null, child.parentId)
    }

    @Test
    fun `toolbar toggles flip highlight name-prompt and description independently`() {
        var flags = ToolbarFlags(
            highlightingEnabled = true,
            namePromptEnabled = true,
            descriptionAreaOpened = false,
        )
        flags = TreeOps.toggleToolbarFlag(flags, ToolbarFlagKey.HIGHLIGHTING)
        assertFalse(flags.highlightingEnabled)
        flags = TreeOps.toggleToolbarFlag(flags, ToolbarFlagKey.NAME_PROMPT)
        assertFalse(flags.namePromptEnabled)
        flags = TreeOps.toggleToolbarFlag(flags, ToolbarFlagKey.DESCRIPTION)
        assertTrue(flags.descriptionAreaOpened)
        assertFalse(flags.highlightingEnabled)
    }
}
