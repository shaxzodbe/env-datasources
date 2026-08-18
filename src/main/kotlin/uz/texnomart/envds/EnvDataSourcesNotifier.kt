package uz.texnomart.envds

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

object EnvDataSourcesNotifier {

    private const val GROUP_ID = "Env Data Sources"

    fun notify(project: Project, result: SyncResult, silentWhenEmpty: Boolean) {
        if (result.isEmpty && silentWhenEmpty) return

        val details = buildList {
            if (result.created.isNotEmpty()) add("создано: ${result.created.joinToString()}")
            if (result.updated.isNotEmpty()) add("обновлено: ${result.updated.joinToString()}")
            if (result.removed.isNotEmpty()) add("удалено: ${result.removed.joinToString()}")
        }

        val text = if (details.isEmpty()) {
            "Подключения уже соответствуют .env"
        } else {
            details.joinToString("<br>")
        }

        NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification("Подключения из .env", text, NotificationType.INFORMATION)
            .notify(project)
    }

    fun notifyError(project: Project, message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification("Подключения из .env", message, NotificationType.WARNING)
            .notify(project)
    }
}
