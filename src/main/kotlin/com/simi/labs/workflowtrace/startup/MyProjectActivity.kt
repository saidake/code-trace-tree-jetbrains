package com.simi.labs.workflowtrace.startup

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class MyProjectActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        // Initialize project-specific resources if needed
    }
}