package com.simi.labs.codetracetree

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object GlobalIcons {
    val CodeTraceTree: Icon = IconLoader.getIcon("/icons/code_trace_tree_icon.svg", GlobalIcons::class.java)
    val CodeTraceTreeDark: Icon = IconLoader.getIcon("/icons/code_trace_tree_icon_dark.svg", GlobalIcons::class.java)
    val CodeTraceTreeSelected: Icon = IconLoader.getIcon("/icons/code_trace_tree_icon_selected.svg", GlobalIcons::class.java)
}