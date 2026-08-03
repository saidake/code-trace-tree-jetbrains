/*
 * Copyright (C) 2025-2026 Code Trace Tree Contributors
 *
 * SPDX-License-Identifier: MIT
 */
package com.pidifa.codetracetree.toolWindow

import com.pidifa.codetracetree.domain.enums.TraceType
import com.pidifa.codetracetree.services.TracePointService
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Component
import javax.swing.JLabel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeCellRenderer

class TracePointTreeRenderer(
    private val service: TracePointService,
    private val toolWindow: MyToolWindowFactory.MyToolWindow
) : JLabel(), TreeCellRenderer {
    private var tree: JTree? = null

    override fun getTreeCellRendererComponent(
        tree: JTree,
        value: Any,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean
    ): Component {
        this.tree = tree
        val userObject = (value as? DefaultMutableTreeNode)?.userObject
        text = when (userObject) {
            is TracePointService.TracePointNode -> {
                val restForeground = if (selected || service.isTracePointSelected(userObject.id)) {
                    UIUtil.getTreeSelectionForeground()
                } else {
                    UIUtil.getTreeForeground()
                }
                formatDisplayHtml(userObject.tracePoint, restForeground)
            }
            else -> userObject?.toString() ?: ""
        }
        background = if (selected) JBColor.LIGHT_GRAY else JBColor.WHITE
        // HTML colors drive text paint; keep a sensible default for non-HTML fallback.
        foreground = UIUtil.getTreeForeground()
        isOpaque = true
        return this
    }

    companion object {
        /** Accent for the user-facing trace name (light / dark). */
        val TRACE_NAME_COLOR: Color = JBColor(Color(0x57068E), Color(0xC39BFF))

        /**
         * Plain display text copied to the clipboard / shown in menus.
         * Examples:
         * - LINE: `test233 (TestControllerWebFlux.java:54)`
         * - FILE: `handlers (TestControllerWebFlux.java)`
         * - DIRECTORY: `src (src/)`
         */
        fun formatDisplayText(tp: TracePointService.TracePoint): String {
            val fileName = tp.baseName.substringAfterLast('/')
            return when (tp.traceType) {
                TraceType.LINE -> "${tp.traceName} ($fileName:${tp.lineNumber})"
                TraceType.FILE -> "${tp.traceName} ($fileName)"
                TraceType.DIRECTORY -> "${tp.traceName} ($fileName/)"
            }
        }

        fun formatDisplayHtml(tp: TracePointService.TracePoint, restForeground: Color): String {
            val fileName = tp.baseName.substringAfterLast('/')
            val nameHtml =
                "<font color='${toHtmlHex(TRACE_NAME_COLOR)}'>${escapeHtml(tp.traceName)}</font>"
            val rest = when (tp.traceType) {
                TraceType.LINE -> " ($fileName:${tp.lineNumber})"
                TraceType.FILE -> " ($fileName)"
                TraceType.DIRECTORY -> " ($fileName/)"
            }
            val restHtml = "<font color='${toHtmlHex(restForeground)}'>${escapeHtml(rest)}</font>"
            val body = "$nameHtml$restHtml"
            return if (!tp.isValid) {
                "<html><strike>$body</strike></html>"
            } else {
                "<html>$body</html>"
            }
        }

        private fun escapeHtml(value: String): String =
            value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")

        private fun toHtmlHex(color: Color): String =
            String.format("#%02x%02x%02x", color.red, color.green, color.blue)
    }
}
