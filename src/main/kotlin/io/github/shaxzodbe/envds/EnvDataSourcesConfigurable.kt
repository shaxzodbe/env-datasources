package io.github.shaxzodbe.envds

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
            checkBox("Enable in this project").bindSelected(state::enabled)
        }
        row {
            checkBox("Sync when the project opens").bindSelected(state::syncOnStartup)
        }
        row {
            checkBox("Sync when .env changes").bindSelected(state::syncOnEnvChange)
        }
        row {
            checkBox("Store passwords in the IDE password safe").bindSelected(state::savePasswords)
        }
        row {
            checkBox("Remove data sources that disappeared from .env").bindSelected(state::removeStale)
        }

        group("What to read") {
            row("Files:") {
                textField().bindText(state::envFiles).columns(30)
                    .comment("Separate with \";\"; paths are relative to the project roots")
            }
            row("Key prefixes:") {
                textField().bindText(state::keyPrefixes).columns(30)
                    .comment("DB → DB_HOST, DB_CATALOG_DATABASE, DB_ORDER_USERNAME")
            }
        }

        group("Naming") {
            row("Name template:") {
                textField().bindText(state::nameTemplate).columns(30)
                    .comment("Available: {group}, {database}, {env}, {project}")
            }
            row("Folder in the Database tree:") {
                textField().bindText(state::folderName).columns(30)
                    .comment("Empty means no folder")
            }
        }
    }
}
