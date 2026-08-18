package io.github.shaxzodbe.envds

/** Сопоставление значения `DB_CONNECTION` с JDBC-драйвером IDE и шаблоном URL. */
object JdbcUrls {

    /** Кандидаты id драйверов IDE в порядке предпочтения. */
    fun driverIds(driver: String): List<String> = when (normalize(driver)) {
        "mysql" -> listOf("mysql.8", "mysql")
        "mariadb" -> listOf("mariadb", "mysql.8")
        "pgsql" -> listOf("postgresql")
        "sqlite" -> listOf("sqlite.xerial")
        "sqlsrv" -> listOf("sqlserver.ms", "sqlserver.jb")
        "oracle" -> listOf("oracle.19", "oracle")
        "clickhouse" -> listOf("clickhouse")
        else -> emptyList()
    }

    fun build(driver: String, host: String?, port: String?, database: String?): String? {
        val db = database.orEmpty()
        return when (normalize(driver)) {
            "mysql" -> "jdbc:mysql://${hostPort(host, port, "3306")}/$db"
            "mariadb" -> "jdbc:mariadb://${hostPort(host, port, "3306")}/$db"
            "pgsql" -> "jdbc:postgresql://${hostPort(host, port, "5432")}/$db"
            "sqlite" -> if (db.isEmpty()) null else "jdbc:sqlite:$db"
            "sqlsrv" -> "jdbc:sqlserver://${hostPort(host, port, "1433")};databaseName=$db"
            "oracle" -> "jdbc:oracle:thin:@${hostPort(host, port, "1521")}:$db"
            "clickhouse" -> "jdbc:clickhouse://${hostPort(host, port, "8123")}/$db"
            else -> null
        }
    }

    fun driverFromUrl(url: String?): String? {
        val prefix = url?.removePrefix("jdbc:")?.substringBefore(':') ?: return null
        return when (prefix) {
            "mysql", "mariadb", "sqlite", "oracle", "clickhouse" -> prefix
            "postgresql" -> "pgsql"
            "sqlserver" -> "sqlsrv"
            else -> null
        }
    }

    private fun hostPort(host: String?, port: String?, defaultPort: String): String {
        val h = host?.takeIf(String::isNotBlank) ?: "localhost"
        val p = port?.takeIf(String::isNotBlank) ?: defaultPort
        return if (h.contains(':') && !h.startsWith("[")) "[$h]:$p" else "$h:$p"
    }

    /** Приводит алиасы Laravel/Doctrine/Symfony к внутреннему имени. */
    private fun normalize(driver: String): String = when (driver.lowercase().trim()) {
        "mysql", "mysql2", "pdo_mysql" -> "mysql"
        "mariadb", "pdo_mariadb" -> "mariadb"
        "pgsql", "postgres", "postgresql", "pdo_pgsql" -> "pgsql"
        "sqlite", "sqlite3", "pdo_sqlite" -> "sqlite"
        "sqlsrv", "mssql", "sqlserver", "pdo_sqlsrv" -> "sqlsrv"
        "oracle", "oci", "oci8" -> "oracle"
        "clickhouse" -> "clickhouse"
        else -> driver.lowercase().trim()
    }
}
