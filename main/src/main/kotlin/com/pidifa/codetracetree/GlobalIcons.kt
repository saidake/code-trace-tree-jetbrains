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

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object GlobalIcons {
    val CodeTraceTree: Icon = IconLoader.getIcon("/icons/code_trace_tree_icon.svg", GlobalIcons::class.java)
    val CodeTraceTreeDark: Icon = IconLoader.getIcon("/icons/code_trace_tree_icon_dark.svg", GlobalIcons::class.java)
    val CodeTraceTreeSelected: Icon = IconLoader.getIcon("/icons/code_trace_tree_icon_selected.svg", GlobalIcons::class.java)
}