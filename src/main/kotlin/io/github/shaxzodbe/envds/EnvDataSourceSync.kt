package io.github.shaxzodbe.envds

import com.intellij.credentialStore.OneTimeString
import com.intellij.database.access.DatabaseCredentials
import com.intellij.database.dataSource.DatabaseDriver
import com.intellij.database.dataSource.DatabaseDriverManager
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.LocalDataSourceManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import java.nio.file.Files
import java.nio.file.Path

/** Результат одной синхронизации — для уведомления. */
data class SyncResult(
    val created: List<String> = emptyList(),
    val updated: List<String> = emptyList(),
    val removed: List<String> = emptyList(),
    val envFiles: List<Path> = emptyList(),
) {
    val isEmpty: Boolean get() = created.isEmpty() && updated.isEmpty() && removed.isEmpty()
    val total: Int get() = created.size + updated.size + removed.size
}

object EnvDataSourceSync {

    private val LOG = logger<EnvDataSourceSync>()

    /** Свойство-маркер: по нему находим ранее созданные нами data source. */
    const val MARKER_PROPERTY: String = "envDataSourcesId"
    const val ORIGIN_PROPERTY: String = "envDataSourcesOrigin"

    /** Читает `.env`, считает нужные подключения. Может выполняться в фоне. */
    fun collect(project: Project): Map<String, Pair<Path, EnvDbConnection>> {
        val settings = EnvDataSourcesSettings.getInstance(project)
        val result = LinkedHashMap<String, Pair<Path, EnvDbConnection>>()

        for (file in envFiles(project, settings)) {
            val text = runCatching { Files.readString(file) }
                .onFailure { LOG.warn("Failed to read $file", it) }
                .getOrNull() ?: continue

            val connections = EnvDbConnections.collect(EnvParser.parse(text), settings.keyPrefixes)
            for (connection in connections) {
                if (connection.jdbcUrl == null) continue
                result[markerFor(project, file, connection)] = file to connection
            }
        }
        return result
    }

    /** Применяет изменения к дереву Database. Вызывать в EDT. */
    fun apply(project: Project, wanted: Map<String, Pair<Path, EnvDbConnection>>): SyncResult {
        val settings = EnvDataSourcesSettings.getInstance(project)
        val manager = LocalDataSourceManager.getInstance(project)
        val existing = manager.dataSources
            .filterIsInstance<LocalDataSource>()
            .mapNotNull { ds -> ds.getAdditionalProperty(MARKER_PROPERTY)?.let { it to ds } }
            .toMap()

        val created = mutableListOf<String>()
        val updated = mutableListOf<String>()
        val removed = mutableListOf<String>()

        for ((marker, pair) in wanted) {
            val (file, connection) = pair
            val name = formatName(project, file, connection, settings.state.nameTemplate)
            val current = existing[marker]

            if (current == null) {
                val dataSource = create(project, marker, file, connection, name, settings)
                manager.addDataSource(dataSource)
                created += name
            } else if (update(project, current, file, connection, name, settings)) {
                manager.fireDataSourceUpdated(current)
                updated += name
            }
        }

        if (settings.state.removeStale) {
            for ((marker, dataSource) in existing) {
                if (marker in wanted) continue
                removed += dataSource.name
                manager.removeDataSource(dataSource)
            }
        }

        return SyncResult(created, updated, removed, wanted.values.map { it.first }.distinct())
    }

    fun syncInBackground(project: Project, onDone: (SyncResult) -> Unit = {}) {
        val wanted = collect(project)
        ApplicationManager.getApplication().invokeLater({
            if (project.isDisposed) return@invokeLater
            onDone(apply(project, wanted))
        }, project.disposed)
    }

    private fun create(
        project: Project,
        marker: String,
        file: Path,
        connection: EnvDbConnection,
        name: String,
        settings: EnvDataSourcesSettings,
    ): LocalDataSource {
        val dataSource = LocalDataSource.create(name, null, connection.jdbcUrl, connection.username)
        dataSource.setAdditionalProperty(MARKER_PROPERTY, marker)
        update(project, dataSource, file, connection, name, settings)
        return dataSource
    }

    /** @return true, если что-то реально поменялось. */
    private fun update(
        project: Project,
        dataSource: LocalDataSource,
        file: Path,
        connection: EnvDbConnection,
        name: String,
        settings: EnvDataSourcesSettings,
    ): Boolean {
        var changed = false

        fun <T> set(current: T, value: T, apply: (T) -> Unit) {
            if (current != value) {
                apply(value)
                changed = true
            }
        }

        set(dataSource.name, name) { dataSource.name = it }
        set(dataSource.url, connection.jdbcUrl) { dataSource.url = it }
        set(dataSource.username, connection.username.orEmpty()) { dataSource.username = it }
        set(dataSource.comment, comment(file, connection)) { dataSource.comment = it }
        set(dataSource.groupName, settings.state.folderName.takeIf(String::isNotBlank)) {
            dataSource.groupName = it
        }
        set(dataSource.getAdditionalProperty(ORIGIN_PROPERTY), file.toString()) {
            dataSource.setAdditionalProperty(ORIGIN_PROPERTY, it)
        }

        findDriver(connection.driver)?.let { driver ->
            set(dataSource.databaseDriver?.id, driver.id) {
                dataSource.databaseDriver = driver
                dataSource.driverClass = driver.driverClass
            }
        }
        dataSource.isConfiguredByUrl = false

        val password = connection.password
        if (settings.state.savePasswords && !password.isNullOrEmpty()) {
            if (dataSource.passwordStorage != LocalDataSource.Storage.PERSIST) {
                dataSource.passwordStorage = LocalDataSource.Storage.PERSIST
                changed = true
            }
            storePassword(dataSource, password)
        }

        return changed
    }

    /**
     * PasswordSafe идёт в хранилище ОС, а это медленная операция: на EDT платформа её
     * запрещает (SlowOperations) и пишет в лог SEVERE, из-за чего пользователь видит
     * «IDE error occurred». Синхронизация приходит на EDT, поэтому пароль пишем в пуле.
     */
    private fun storePassword(dataSource: LocalDataSource, password: String) {
        val app = ApplicationManager.getApplication()
        val store = Runnable {
            runCatching { DatabaseCredentials.getInstance().storePassword(dataSource, OneTimeString(password)) }
                .onFailure { LOG.warn("Failed to store the password for ${dataSource.name}", it) }
        }
        if (app.isDispatchThread) app.executeOnPooledThread(store) else store.run()
    }

    private fun findDriver(driver: String): DatabaseDriver? {
        val manager = DatabaseDriverManager.getInstance()
        return JdbcUrls.driverIds(driver).firstNotNullOfOrNull { manager.getDriver(it) }
    }

    private fun comment(file: Path, connection: EnvDbConnection): String =
        "Created automatically from ${file.fileName} (${connection.id})"

    private fun markerFor(project: Project, file: Path, connection: EnvDbConnection): String {
        val base = project.basePath?.let { runCatching { Path.of(it).relativize(file).toString() }.getOrNull() }
            ?: file.fileName.toString()
        return "$base#${connection.id}"
    }

    fun formatName(project: Project, file: Path, connection: EnvDbConnection, template: String): String =
        template
            .replace("{group}", connection.displayGroup)
            .replace("{database}", connection.database.orEmpty())
            .replace("{env}", file.fileName.toString())
            .replace("{project}", project.name)
            .trim()
            .ifEmpty { connection.displayGroup }

    /** `.env`-файлы во всех корнях проекта. */
    fun envFiles(project: Project, settings: EnvDataSourcesSettings): List<Path> {
        val names = settings.envFileNames
        if (names.isEmpty()) return emptyList()

        val roots = LinkedHashSet<Path>()
        project.basePath?.let { roots.add(Path.of(it)) }
        ApplicationManager.getApplication().runReadAction {
            if (!project.isDisposed) {
                ProjectRootManager.getInstance(project).contentRoots
                    .mapNotNull { root -> runCatching { root.toNioPath() }.getOrNull() }
                    .forEach(roots::add)
            }
        }

        return roots.flatMap { root -> names.map(root::resolve) }
            .filter { Files.isRegularFile(it) }
            .distinct()
    }
}
