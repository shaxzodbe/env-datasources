package io.github.shaxzodbe.envds

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/** Создаёт подключения сразу после открытия проекта. */
class EnvDataSourcesStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        val settings = EnvDataSourcesSettings.getInstance(project)
        if (!settings.state.enabled) return

        // Сервис подписывается на изменения .env.
        EnvDataSourcesService.getInstance(project)

        if (!settings.state.syncOnStartup) return
        EnvDataSourceSync.syncInBackground(project) { result ->
            EnvDataSourcesNotifier.notify(project, result, silentWhenEmpty = true)
        }
    }
}
