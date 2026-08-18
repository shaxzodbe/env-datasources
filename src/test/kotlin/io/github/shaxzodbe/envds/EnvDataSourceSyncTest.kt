package io.github.shaxzodbe.envds

import com.intellij.database.dataSource.DatabaseDriverManager
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.LocalDataSourceManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Path

class EnvDataSourceSyncTest : BasePlatformTestCase() {

    private val envFile: Path get() = Path.of(project.basePath ?: ".", ".env")

    private fun wanted(vararg connections: EnvDbConnection) =
        connections.associate { "test.env#${it.id}" to (envFile to it) }

    private fun connection(group: String, database: String, host: String = "127.0.0.1") = EnvDbConnection(
        prefix = "DB",
        group = group,
        driver = "mysql",
        host = host,
        port = "3306",
        database = database,
        username = "user_$database",
        password = "pass_$database",
        url = null,
    )

    private fun ours(): List<LocalDataSource> =
        LocalDataSourceManager.getInstance(project).dataSources
            .filterIsInstance<LocalDataSource>()
            .filter { it.getAdditionalProperty(EnvDataSourceSync.MARKER_PROPERTY) != null }

    override fun tearDown() {
        try {
            val manager = LocalDataSourceManager.getInstance(project)
            ours().forEach(manager::removeDataSource)
        } finally {
            super.tearDown()
        }
    }

    fun testCreatesOneDataSourcePerGroup() {
        val result = EnvDataSourceSync.apply(
            project,
            wanted(connection("", "shop"), connection("CATALOG", "catalog"), connection("ORDER", "orders")),
        )

        assertEquals(3, result.created.size)
        assertEquals(3, ours().size)

        val catalog = ours().first { it.getAdditionalProperty(EnvDataSourceSync.MARKER_PROPERTY) == "test.env#DB_CATALOG" }
        assertEquals("jdbc:mysql://127.0.0.1:3306/catalog", catalog.url)
        assertEquals("user_catalog", catalog.username)
        assertTrue(catalog.name.contains("catalog"))
    }

    fun testSecondRunUpdatesInsteadOfDuplicating() {
        EnvDataSourceSync.apply(project, wanted(connection("CATALOG", "catalog")))
        val second = EnvDataSourceSync.apply(project, wanted(connection("CATALOG", "catalog", host = "10.0.0.5")))

        assertEquals(1, ours().size)
        assertEquals(1, second.updated.size)
        assertEquals(0, second.created.size)
        assertEquals("jdbc:mysql://10.0.0.5:3306/catalog", ours().single().url)
    }

    fun testUnchangedRunIsNoop() {
        EnvDataSourceSync.apply(project, wanted(connection("CATALOG", "catalog")))
        val second = EnvDataSourceSync.apply(project, wanted(connection("CATALOG", "catalog")))

        assertTrue(second.isEmpty)
        assertEquals(1, ours().size)
    }

    fun testStaleDataSourceIsRemoved() {
        EnvDataSourceSync.apply(project, wanted(connection("CATALOG", "catalog"), connection("ORDER", "orders")))
        val second = EnvDataSourceSync.apply(project, wanted(connection("CATALOG", "catalog")))

        assertEquals(1, second.removed.size)
        assertEquals(1, ours().size)
    }

    fun testDriverIsResolved() {
        val driver = DatabaseDriverManager.getInstance().getDriver("mysql.8")
        if (driver == null) return // в тестовой среде драйверы могут быть не зарегистрированы

        EnvDataSourceSync.apply(project, wanted(connection("CATALOG", "catalog")))
        val dataSource = ours().single()
        assertEquals("mysql.8", dataSource.databaseDriver?.id)
        assertEquals(driver.driverClass, dataSource.driverClass)
        assertEquals(LocalDataSource.Storage.PERSIST, dataSource.passwordStorage)
    }
}
