# Выпуск версии

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

Сборка и тесты описаны в [README](README.md#сборка).
