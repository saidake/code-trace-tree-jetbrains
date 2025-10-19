package com.simi.labs.workflowtrace

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object GlobalIcons {
    val WorkflowTrace: Icon = IconLoader.getIcon("/icons/workflow_trace_icon.svg", GlobalIcons::class.java)
    val WorkflowTraceDark: Icon = IconLoader.getIcon("/icons/workflow_trace_icon_dark.svg", GlobalIcons::class.java)
    val WorkflowTraceSelected: Icon = IconLoader.getIcon("/icons/workflow_trace_icon_selected.svg", GlobalIcons::class.java)
}