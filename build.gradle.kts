import java.util.Properties
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.4.10"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

// Личные настройки машины живут в local.properties и не коммитятся.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun setting(name: String): String? =
    (localProperties.getProperty(name) ?: providers.gradleProperty(name).orNull)
        ?.takeIf { it.isNotBlank() }

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        val localIdePath = setting("localIdePath") ?: System.getenv("LOCAL_IDE_PATH")
        if (localIdePath.isNullOrBlank()) {
            create(
                providers.gradleProperty("platformType"),
                providers.gradleProperty("platformVersion"),
            )
        } else {
            local(localIdePath)
        }

        // Database Tools & SQL — оттуда LocalDataSource и всё остальное.
        bundledPlugin("com.intellij.database")

        testFramework(TestFrameworkType.Platform)
    }

    testImplementation("junit:junit:4.13.2")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild.set(providers.gradleProperty("pluginSinceBuild"))
            untilBuild.set(provider { null })
        }
    }
    pluginVerification {
        ides {
            recommended()
        }
    }

    // Подпись сборки перед публикацией. Ключи берутся из окружения,
    // в репозиторий они не попадают: см. раздел «Публикация» в README.
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        // Для превью-сборок: -PpublishChannel=eap → отдельный канал вместо стабильного.
        channels = providers.gradleProperty("publishChannel")
            .orElse("default")
            .map { listOf(it) }
    }
}

tasks {
    test {
        useJUnit()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
}
