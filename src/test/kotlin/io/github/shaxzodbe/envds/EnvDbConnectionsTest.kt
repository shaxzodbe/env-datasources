package io.github.shaxzodbe.envds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class EnvDbConnectionsTest {

    private val env = """
        APP_ENV=local
        DB_CONNECTION=mysql
        DB_HOST=127.0.0.1
        DB_PORT=3306
        DB_DATABASE=shop
        DB_USERNAME=root
        DB_PASSWORD="secret pass"

        DB_CATALOG_DATABASE=catalog
        DB_CATALOG_USERNAME=catalog_user
        DB_CATALOG_PASSWORD=catalog_pass

        DB_ORDER_DATABASE=orders
        DB_ORDER_USERNAME=order_user
        DB_ORDER_PASSWORD=order_pass
    """.trimIndent()

    private fun connections() = EnvDbConnections.collect(EnvParser.parse(env))

    @Test
    fun `three connections are detected`() {
        assertEquals(listOf("", "CATALOG", "ORDER"), connections().map { it.group })
    }

    @Test
    fun `group inherits host port and driver from base`() {
        val catalog = connections().first { it.group == "CATALOG" }
        assertEquals("mysql", catalog.driver)
        assertEquals("127.0.0.1", catalog.host)
        assertEquals("3306", catalog.port)
        assertEquals("catalog", catalog.database)
        assertEquals("catalog_user", catalog.username)
        assertEquals("catalog_pass", catalog.password)
        assertEquals("jdbc:mysql://127.0.0.1:3306/catalog", catalog.jdbcUrl)
    }

    @Test
    fun `base connection keeps its own credentials`() {
        val main = connections().first { it.group.isEmpty() }
        assertEquals("root", main.username)
        assertEquals("secret pass", main.password)
        assertEquals("jdbc:mysql://127.0.0.1:3306/shop", main.jdbcUrl)
        assertEquals("main", main.displayGroup)
    }

    @Test
    fun `DB_CONNECTION is a field of the base connection, not a group`() {
        assertEquals(3, connections().size)
    }

    @Test
    fun `postgres and multiword groups`() {
        val text = """
            DB_CONNECTION=pgsql
            DB_HOST=db.local
            DB_DATABASE=main
            DB_ORDER_READ_DATABASE=orders_replica
            DB_ORDER_READ_HOST=replica.local
        """.trimIndent()

        val list = EnvDbConnections.collect(EnvParser.parse(text))
        val replica = list.first { it.group == "ORDER_READ" }
        assertEquals("jdbc:postgresql://replica.local:5432/orders_replica", replica.jdbcUrl)
        assertEquals(listOf("postgresql"), JdbcUrls.driverIds(replica.driver))
    }

    @Test
    fun `explicit url wins`() {
        val text = """
            DB_URL=jdbc:postgresql://example.com:6432/app
            DB_HOST=ignored
        """.trimIndent()

        val main = EnvDbConnections.collect(EnvParser.parse(text)).single()
        assertEquals("jdbc:postgresql://example.com:6432/app", main.jdbcUrl)
        assertEquals("pgsql", main.driver)
    }

    @Test
    fun `parser handles quotes comments export and interpolation`() {
        val text = """
            # комментарий
            export DB_HOST=127.0.0.1 # инлайн-комментарий
            DB_PASSWORD='pa#ss'
            DB_DATABASE="${'$'}{APP_NAME}_db"
            APP_NAME=shop
        """.trimIndent()

        val parsed = EnvParser.parse(text)
        assertEquals("127.0.0.1", parsed["DB_HOST"])
        assertEquals("pa#ss", parsed["DB_PASSWORD"])
        // APP_NAME объявлен ниже — подстановка не находит значение.
        assertEquals("_db", parsed["DB_DATABASE"])
        assertNotNull(parsed["APP_NAME"])
    }
}
