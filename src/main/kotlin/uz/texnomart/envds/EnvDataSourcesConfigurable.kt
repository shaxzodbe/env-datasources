package uz.texnomart.envds

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel

class EnvDataSourcesConfigurable(private val project: Project) : BoundConfigurable("Env Data Sources") {

    override fun createPanel() = panel {
        val state = EnvDataSourcesSettings.getInstance(project).state

        row {
            checkBox("Включить плагин в этом проекте").bindSelected(state::enabled)
        }
        row {
            checkBox("Синхронизировать при открытии проекта").bindSelected(state::syncOnStartup)
        }
        row {
            checkBox("Синхронизировать при изменении .env").bindSelected(state::syncOnEnvChange)
        }
        row {
            checkBox("Сохранять пароли в хранилище IDE").bindSelected(state::savePasswords)
        }
        row {
            checkBox("Удалять подключения, исчезнувшие из .env").bindSelected(state::removeStale)
        }

        group("Что читать") {
            row("Файлы:") {
                textField().bindText(state::envFiles).columns(30)
                    .comment("Через «;», путь относительно корней проекта")
            }
            row("Префиксы ключей:") {
                textField().bindText(state::keyPrefixes).columns(30)
                    .comment("DB → DB_HOST, DB_CATALOG_DATABASE, DB_ORDER_USERNAME")
            }
        }

        group("Как называть") {
            row("Шаблон имени:") {
                textField().bindText(state::nameTemplate).columns(30)
                    .comment("Доступно: {group}, {database}, {env}, {project}")
            }
            row("Папка в дереве Database:") {
                textField().bindText(state::folderName).columns(30)
                    .comment("Пусто — без папки")
            }
        }
    }
}
