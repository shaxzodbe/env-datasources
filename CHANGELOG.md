# Changelog

## 0.1.0

Первая версия.

- Подключения к БД создаются из `.env` при открытии проекта.
- Группы ключей `DB_<GROUP>_<FIELD>` становятся отдельными data source, недостающие
  поля наследуются от базового набора `DB_*`.
- Ручная синхронизация: **Tools → Sync Data Sources from .env**.
- Настройки: **Settings → Tools → Env Data Sources**.
