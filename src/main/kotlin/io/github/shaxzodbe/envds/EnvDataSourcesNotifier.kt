package io.github.shaxzodbe.envds

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

object EnvDataSourcesNotifier {

    private const val GROUP_ID = "Env Data Sources"

    fun notify(project: Project, result: SyncResult, silentWhenEmpty: Boolean) {
        if (result.isEmpty && silentWhenEmpty) return

        val details = buildList {
            if (result.created.isNotEmpty()) add("created: ${result.created.joinToString()}")
            if (result.updated.isNotEmpty()) add("updated: ${result.updated.joinToString()}")
            if (result.removed.isNotEmpty()) add("removed: ${result.removed.joinToString()}")
        }

        val text = if (details.isEmpty()) {
            "Data sources already match .env"
        } else {
            details.joinToString("<br>")
        }

        NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification("Data sources from .env", text, NotificationType.INFORMATION)
            .notify(project)
    }

    fun notifyError(project: Project, message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification("Data sources from .env", message, NotificationType.WARNING)
            .notify(project)
    }
}
