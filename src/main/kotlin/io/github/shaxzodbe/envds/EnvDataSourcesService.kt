package io.github.shaxzodbe.envds

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.Alarm

/** Следит за изменениями `.env` и пересоздаёт подключения. */
@Service(Service.Level.PROJECT)
class EnvDataSourcesService(private val project: Project) : Disposable {

    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)

    init {
        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    val settings = EnvDataSourcesSettings.getInstance(project)
                    if (!settings.state.enabled || !settings.state.syncOnEnvChange) return

                    val names = settings.envFileNames
                    val touched = events.any { event ->
                        names.any { name -> event.path.endsWith("/$name") }
                    }
                    if (touched) scheduleSync()
                }
            })
    }

    /** Небольшая задержка, чтобы не дёргаться на каждое сохранение. */
    private fun scheduleSync() {
        alarm.cancelAllRequests()
        alarm.addRequest({
            if (project.isDisposed) return@addRequest
            EnvDataSourceSync.syncInBackground(project) { result ->
                EnvDataSourcesNotifier.notify(project, result, silentWhenEmpty = true)
            }
        }, SYNC_DELAY_MS)
    }

    override fun dispose() = Unit

    companion object {
        private const val SYNC_DELAY_MS = 1_500

        fun getInstance(project: Project): EnvDataSourcesService = project.service()
    }
}
