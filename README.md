# Env Data Sources

Плагин для PhpStorm: при открытии проекта читает `.env` и заводит подключения в
**Database** — по одному на каждый набор ключей.

Для такого `.env`

```
DB_CONNECTION=mysql
DB_HOST=127.0.0.1
DB_PORT=3306
DB_DATABASE=texnomart
DB_USERNAME=root
DB_PASSWORD=secret

DB_CATALOG_DATABASE=catalog
DB_CATALOG_USERNAME=catalog_user
DB_CATALOG_PASSWORD=catalog_pass

DB_ORDER_DATABASE=orders
DB_ORDER_USERNAME=order_user
DB_ORDER_PASSWORD=order_pass
```

создаются три data source: `main`, `catalog`, `order`. Группам `CATALOG` и `ORDER`
не хватает host, port и драйвера — они наследуются от базового набора `DB_*`.

## Установка

`Settings → Plugins → Marketplace` → найдите **Env Data Sources** → `Install` → перезапустите IDE.

Пока версия не прошла ревью Marketplace, плагин ставится файлом:
`Settings → Plugins → ⚙ → Install Plugin from Disk…` и выбрать
`build/distributions/env-datasources-<версия>.zip` (собирается командой `./gradlew buildPlugin`).

Требуется PhpStorm 2025.3 или новее и плагин **Database Tools and SQL** — в PhpStorm
он встроен. В IntelliJ IDEA работает только в Ultimate; в Community базы данных
не поддерживаются.

После установки откройте проект с `.env` — подключения появятся в **Database**
в папке `.env`. Если ничего не появилось, дёрните вручную:
**Tools → Sync Data Sources from .env** — тогда будет уведомление даже при пустом
результате.

## Как работает разбор ключей

`DB_<GROUP>_<FIELD>` → группа `<GROUP>`, поле `<FIELD>`.
`DB_<FIELD>` → базовое подключение. Поля: `CONNECTION`/`DRIVER`, `HOST`, `PORT`,
`DATABASE`/`DB`, `USERNAME`/`USER`, `PASSWORD`/`PASS`, `URL`/`DSN`.

Поэтому `DB_CONNECTION` — это драйвер базового подключения, а не группа `CONNECTION`.
Группа попадает в результат, только если у неё есть своя `DATABASE` или `URL`;
имя группы может состоять из нескольких слов (`DB_ORDER_READ_DATABASE` → `ORDER_READ`).

Драйверы: `mysql`, `mariadb`, `pgsql`/`postgres`, `sqlite`, `sqlsrv`/`mssql`,
`oracle`, `clickhouse` (плюс алиасы Doctrine вида `pdo_mysql`).

## Что происходит с существующими подключениями

Каждый созданный data source помечается свойством `envDataSourcesId`
(`<путь к .env>#DB_CATALOG`). При повторной синхронизации плагин находит подключение
по этой метке и обновляет его, а не создаёт дубликат. Подключения, сделанные руками,
не трогаются. Если группа исчезла из `.env`, помеченный ею data source удаляется —
это отключается в настройках.

Пароли кладутся в хранилище паролей IDE (`Storage.PERSIST`), в `dataSources.xml` они не попадают.

## Настройки

`Settings → Tools → Env Data Sources`:

| Настройка | По умолчанию |
|---|---|
| Синхронизировать при открытии проекта | да |
| Синхронизировать при изменении `.env` | да (с задержкой 1,5 с) |
| Сохранять пароли | да |
| Удалять исчезнувшие подключения | да |
| Файлы | `.env` (несколько — через `;`) |
| Префиксы ключей | `DB` |
| Шаблон имени | `{project} — {group}`, доступны `{group}`, `{database}`, `{env}`, `{project}` |
| Папка в дереве Database | `.env` |

Ручной запуск: **Tools → Sync Data Sources from .env**.

## Сборка

Нужен JDK 21 — подойдёт JBR из самого PhpStorm:

```bash
export JAVA_HOME=~/.local/share/JetBrains/Toolbox/apps/phpstorm/jbr
./gradlew buildPlugin
```

Готовый zip: `build/distributions/env-datasources-<версия>.zip`.

Личные пути держите в `local.properties` (не коммитится, пример —
`local.properties.example`). Полезны два ключа: `localIdePath` — собирать против
установленного PhpStorm вместо скачивания дистрибутива, и `org.gradle.java.home` —
если системная `java` старее 21. Без `localIdePath` Gradle скачает `platformVersion`
из `gradle.properties`.

Отладка в песочнице: `./gradlew runIde`.

## Публикация в Marketplace

Плагин раздаётся через [JetBrains Marketplace](https://plugins.jetbrains.com) — оттуда его
видят все пользователи PhpStorm, отдельный репозиторий плагинов не нужен.

Что нужно один раз:

1. Аккаунт JetBrains и профиль вендора на plugins.jetbrains.com.
2. Ключ подписи — JetBrains рекомендует подписывать сборки, и `publishPlugin` из этого
   проекта настроен на подпись:
   ```bash
   tools/generate-signing-key.sh
   ```
   Ключ создаётся вне репозитория (`~/.env-datasources-signing`) и переиспользуется для
   всех будущих версий: подписать обновление другим ключом нельзя, IDE его не примет.
   Держите каталог в бэкапе.
3. Токен: `plugins.jetbrains.com → профиль → My Tokens`.
4. **Первую** версию Marketplace принимает только через веб-форму
   [`Upload plugin`](https://plugins.jetbrains.com/plugin/add) (укажите категорию и
   лицензию — в репозитории MIT). Дальше идёт ревью JetBrains, обычно пара рабочих
   дней; после одобрения плагин появляется в поиске.

   В самой форме ключ подписи нигде не вводится — она принимает только zip. Подпись
   вшивается в архив на сборке: с выставленными `PRIVATE_KEY`, `CERTIFICATE_CHAIN` и
   `PRIVATE_KEY_PASSWORD` команда `./gradlew signPlugin` кладёт рядом
   `build/distributions/env-datasources-<версия>-signed.zip` — в форму загружайте его.
   Без этих переменных `signPlugin` подписать нечем, и тогда грузится обычный
   `env-datasources-<версия>.zip`.

Выпуск версии, когда плагин уже одобрен:

```bash
# поднять pluginVersion в gradle.properties, дописать CHANGELOG.md и <change-notes> в plugin.xml
export PRIVATE_KEY="$(cat ~/.env-datasources-signing/private.pem)"
export CERTIFICATE_CHAIN="$(cat ~/.env-datasources-signing/chain.crt)"
export PRIVATE_KEY_PASSWORD='...'
export PUBLISH_TOKEN='...'

./gradlew verifyPlugin        # проверка совместимости — то же, что гоняет Marketplace
./gradlew publishPlugin       # подпишет и выложит
```

Превью-версия в отдельный канал, чтобы не задеть стабильных пользователей:
`./gradlew publishPlugin -PpublishChannel=eap` (подписчики канала добавляют его URL
в `Manage Plugin Repositories`).

Требования Marketplace, которые уже учтены: описание и change-notes на английском
в `plugin.xml`, иконки `pluginIcon.svg` / `pluginIcon_dark.svg`, `since-build` без
верхней границы, лицензия в `LICENSE`.

## Тесты

```bash
./gradlew test
```

Юнит-тесты на разбор `.env` и группировку плюс интеграционные — они гоняют
`EnvDataSourceSync.apply` на настоящем `LocalDataSourceManager`: создание, обновление
без дублей, удаление устаревших, резолв драйвера.
