/*
 * Copyright (C) 2025-2026 Code Trace Tree Contributors
 *
 * SPDX-License-Identifier: MIT
 */
package com.pidifa.codetracetree.domain

data class ToolbarFlags(
    val highlightingEnabled: Boolean,
    val namePromptEnabled: Boolean,
    val descriptionAreaOpened: Boolean,
)

enum class ToolbarFlagKey { HIGHLIGHTING, NAME_PROMPT, DESCRIPTION }

object TreeOps {
    /** Toolbar Move Up: swap each selected node with the sibling above, unless that sibling is also selected. */
    fun <T> moveSiblingsUp(
        siblings: MutableList<T>,
        selectedIds: Set<String>,
        idOf: (T) -> String,
    ) {
        val ordered = siblings.filter { selectedIds.contains(idOf(it)) }
            .sortedBy { siblings.indexOf(it) }
        for (node in ordered) {
            val index = siblings.indexOf(node)
            if (index > 0 && !selectedIds.contains(idOf(siblings[index - 1]))) {
                siblings[index] = siblings[index - 1].also { siblings[index - 1] = siblings[index] }
            }
        }
    }

    /** Toolbar Move Down: swap each selected node with the sibling below, unless that sibling is also selected. */
    fun <T> moveSiblingsDown(
        siblings: MutableList<T>,
        selectedIds: Set<String>,
        idOf: (T) -> String,
    ) {
        val ordered = siblings.filter { selectedIds.contains(idOf(it)) }
            .sortedByDescending { siblings.indexOf(it) }
        for (node in ordered) {
            val index = siblings.indexOf(node)
            if (index >= 0 && index < siblings.size - 1 && !selectedIds.contains(idOf(siblings[index + 1]))) {
                siblings[index] = siblings[index + 1].also { siblings[index + 1] = siblings[index] }
            }
        }
    }

    /**
     * Toolbar Remove Invalid: drop invalid nodes; valid children are reparented in place.
     * @return removed ids
     */
    fun <T> pruneInvalidNodes(
        nodes: MutableList<T>,
        parentId: String?,
        idOf: (T) -> String,
        isValid: (T) -> Boolean,
        childrenOf: (T) -> MutableList<T>,
        setParentId: (T, String?) -> Unit,
    ): List<String> {
        val removed = mutableListOf<String>()
        var i = nodes.size - 1
        while (i >= 0) {
            val node = nodes[i]
            removed.addAll(
                pruneInvalidNodes(childrenOf(node), idOf(node), idOf, isValid, childrenOf, setParentId)
            )
            if (!isValid(node)) {
                removed.add(idOf(node))
                val children = childrenOf(node).toList()
                children.forEach { setParentId(it, parentId) }
                childrenOf(node).clear()
                nodes.removeAt(i)
                nodes.addAll(i, children)
            }
            i--
        }
        return removed
    }

    fun toggleToolbarFlag(flags: ToolbarFlags, key: ToolbarFlagKey): ToolbarFlags = when (key) {
        ToolbarFlagKey.HIGHLIGHTING -> flags.copy(highlightingEnabled = !flags.highlightingEnabled)
        ToolbarFlagKey.NAME_PROMPT -> flags.copy(namePromptEnabled = !flags.namePromptEnabled)
        ToolbarFlagKey.DESCRIPTION -> flags.copy(descriptionAreaOpened = !flags.descriptionAreaOpened)
    }
}
