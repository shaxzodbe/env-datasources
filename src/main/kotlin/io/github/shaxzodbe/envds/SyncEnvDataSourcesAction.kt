package io.github.shaxzodbe.envds

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project

/** Tools → Sync Data Sources from .env */
class SyncEnvDataSourcesAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        object : Task.Backgroundable(project, "Syncing data sources from .env", true) {
            override fun run(indicator: ProgressIndicator) {
                runSync(project)
            }
        }.queue()
    }

    private fun runSync(project: Project) {
        val settings = EnvDataSourcesSettings.getInstance(project)
        val files = EnvDataSourceSync.envFiles(project, settings)
        if (files.isEmpty()) {
            EnvDataSourcesNotifier.notifyError(
                project,
                "None of these files were found: ${settings.envFileNames.joinToString()}",
            )
            return
        }
        EnvDataSourceSync.syncInBackground(project) { result ->
            EnvDataSourcesNotifier.notify(project, result, silentWhenEmpty = false)
        }
    }
}
