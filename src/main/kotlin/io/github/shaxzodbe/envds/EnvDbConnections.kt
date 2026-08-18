package io.github.shaxzodbe.envds

/**
 * Одно подключение, вытащенное из `.env`.
 *
 * [group] — пустая строка для базового набора (`DB_HOST`, `DB_DATABASE`, ...),
 * иначе средний сегмент ключа: `DB_CATALOG_DATABASE` -> `CATALOG`.
 */
data class EnvDbConnection(
    val prefix: String,
    val group: String,
    val driver: String,
    val host: String?,
    val port: String?,
    val database: String?,
    val username: String?,
    val password: String?,
    val url: String?,
) {
    /** Ключ, по которому data source опознаётся при повторной синхронизации. */
    val id: String get() = if (group.isEmpty()) prefix else "${prefix}_$group"

    val displayGroup: String get() = if (group.isEmpty()) "main" else group.lowercase()

    val jdbcUrl: String? get() = url ?: JdbcUrls.build(driver, host, port, database)
}

object EnvDbConnections {

    /** Суффиксы, которые считаются полями, а не частью имени группы. */
    private val FIELDS = setOf(
        "CONNECTION", "DRIVER", "HOST", "PORT", "DATABASE", "DB",
        "USERNAME", "USER", "PASSWORD", "PASS", "URL", "DSN",
    )

    fun collect(env: Map<String, String>, prefixes: List<String> = listOf("DB")): List<EnvDbConnection> =
        prefixes.flatMap { collectForPrefix(env, it) }

    private fun collectForPrefix(env: Map<String, String>, prefix: String): List<EnvDbConnection> {
        val groups = LinkedHashMap<String, MutableMap<String, String>>()

        for ((key, value) in env) {
            if (!key.startsWith("${prefix}_")) continue
            val rest = key.removePrefix("${prefix}_")

            val (group, field) = when {
                rest in FIELDS -> "" to rest
                else -> {
                    val cut = rest.lastIndexOf('_')
                    if (cut <= 0) continue
                    val field = rest.substring(cut + 1)
                    if (field !in FIELDS) continue
                    rest.substring(0, cut) to field
                }
            }
            groups.getOrPut(group) { LinkedHashMap() }[field] = value
        }

        val main = groups[""].orEmpty()
        return groups.entries
            .mapNotNull { (group, fields) -> build(prefix, group, fields, if (group.isEmpty()) emptyMap() else main) }
    }

    private fun build(
        prefix: String,
        group: String,
        fields: Map<String, String>,
        fallback: Map<String, String>,
    ): EnvDbConnection? {
        fun pick(vararg names: String): String? = names.firstNotNullOfOrNull { fields[it]?.takeIf(String::isNotBlank) }
        fun inherited(vararg names: String): String? =
            pick(*names) ?: names.firstNotNullOfOrNull { fallback[it]?.takeIf(String::isNotBlank) }

        val url = pick("URL", "DSN")
        val database = pick("DATABASE", "DB")
        val host = inherited("HOST")

        // Группа без собственной базы и без url — это не отдельное подключение.
        if (url == null && database == null && (group.isNotEmpty() || host == null)) return null

        return EnvDbConnection(
            prefix = prefix,
            group = group,
            driver = (inherited("CONNECTION", "DRIVER") ?: JdbcUrls.driverFromUrl(url) ?: "mysql").lowercase(),
            host = host,
            port = inherited("PORT"),
            database = database,
            username = inherited("USERNAME", "USER"),
            password = inherited("PASSWORD", "PASS"),
            url = url?.takeIf { it.startsWith("jdbc:") },
        )
    }
}
