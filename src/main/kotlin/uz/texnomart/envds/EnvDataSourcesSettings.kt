package uz.texnomart.envds

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

@State(name = "EnvDataSourcesSettings", storages = [Storage("envDataSources.xml")])
class EnvDataSourcesSettings : PersistentStateComponent<EnvDataSourcesSettings.State> {

    class State {
        var enabled: Boolean = true
        var syncOnStartup: Boolean = true
        var syncOnEnvChange: Boolean = true

        /** Имена env-файлов относительно корней проекта, через `;`. */
        var envFiles: String = ".env"

        /** Префиксы ключей, через `;`: `DB` покрывает `DB_HOST`, `DB_CATALOG_DATABASE`, ... */
        var keyPrefixes: String = "DB"

        /** Плейсхолдеры: {group}, {database}, {env}, {project}. */
        var nameTemplate: String = "{project} — {group}"

        /** Имя папки в дереве Database. Пусто — без папки. */
        var folderName: String = ".env"

        var savePasswords: Boolean = true

        /** Удалять сгенерированные data source, которых больше нет в .env. */
        var removeStale: Boolean = true
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, this.state)
    }

    val envFileNames: List<String>
        get() = state.envFiles.split(';', ',').map(String::trim).filter(String::isNotEmpty)

    val keyPrefixes: List<String>
        get() = state.keyPrefixes.split(';', ',').map { it.trim().trimEnd('_').uppercase() }.filter(String::isNotEmpty)

    companion object {
        fun getInstance(project: Project): EnvDataSourcesSettings = project.service()
    }
}
