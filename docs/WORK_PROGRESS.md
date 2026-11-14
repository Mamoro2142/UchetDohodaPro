# Учёт дохода Pro 1.1.0 — итоговый отчёт

Дата: 14.11.2026. Исходная версия: 1.0.9 (10), коммит df25cecc4ac2e106dd49517cf0c1572bb1a3fd1e.

**Статус функциональных пунктов 1–8: выполнены. Прогресс: 100%.**

Версия APK: **1.1.0**, versionCode **11**. applicationId: `com.example.uchetdohodapro`.
Конфигурация распространения сохранена исходной: **debug APK с постоянным ключом подписи проекта**. Код подготовлен локально, APK и проверки выполнены через GitHub Actions.

Подтверждающая сборка: [Actions №96](https://github.com/Mamoro2142/UchetDohodaPro/actions/runs/43631854310), коммит [21a6d84](https://github.com/Mamoro2142/UchetDohodaPro/commit/21a6d84db9e6082dea3294e5ba1de7b2af16bb9d), результат **success**. В артефакте `UchetDohodaPro-1.1.0`: APK, `SHA256SUMS.txt`, `BUILD_INFO.json`. В метаданных записаны версия, конфигурация, SHA коммита, сертификат, сумма APK и результаты проверок. Итоговый отчёт сохраняется отдельным последующим коммитом; он не меняет код приложения.

## Проверенные ограничения сохранности

- Имя базы `uchet_dohoda.db`, applicationId, иконка и ресурсы оформления сохранены. Изменения UI ограничены необходимыми функциями и навигацией.
- Версия Room: **5**. Сохранены миграции 1→2→3; добавлены 3→4 (nullable savedAt) и 4→5 (финансовые операции). Разрушительных миграций нет.
- Обновление проверено установкой поверх исходного подписанного APK 1.0.9 без очистки базы. Сертификаты обоих APK совпадают.
- SHA-256 сертификата: `9a5bab4b807bab65ff52ae478effde0835dd7e937c1bff3037f927d6f69c9ca1`.
- Исторические ставки дней и формула начисленного дохода сохранены. Авансы и штрафы хранятся отдельно.
- **Пустые показатели остаются null и скрываются в истории и подробной статистике дня. Явно введённый ноль сохраняется и отображается как ноль.**

## Отчёты по пунктам

Все перечисленные коммиты отправлены в GitHub и сверены с локальными файлами. После каждого функционального пункта был успешный workflow до перехода к следующему; исправления неуспешных запусков сохранялись отдельными коммитами.

| № | Задача / статус | Коммиты | Проверки | Прогресс |
| --- | --- | --- | --- | --- |
| 1 | Резервное копирование — выполнено | [84d860f](https://github.com/Mamoro2142/UchetDohodaPro/commit/84d860f47a3ebb8ddb8f44e2318100a933a68912), [800c6a4](https://github.com/Mamoro2142/UchetDohodaPro/commit/800c6a48e82eb50b8676fa60abeaf29eb26368ab) | [Actions](https://github.com/Mamoro2142/UchetDohodaPro/actions/runs/43582160528): 5 unit-тестов, lint без ошибок, подписанный APK | 12,5% |
| 2 | Тарифы и дата сохранения — выполнено | [25425fd](https://github.com/Mamoro2142/UchetDohodaPro/commit/25425fd822488a835a9eec30da2b488afe2fef09) | [Actions](https://github.com/Mamoro2142/UchetDohodaPro/actions/runs/43582817060): 8 unit-тестов, lint без ошибок, подписанный APK | 25% |
| 3 | Авансы и штрафы — выполнено | [4c61097](https://github.com/Mamoro2142/UchetDohodaPro/commit/4c61097376f305e4f7990d73090866e3a66f976c) | [Actions](https://github.com/Mamoro2142/UchetDohodaPro/actions/runs/43583534963): 11 unit-тестов, lint без ошибок, подписанный APK | 37,5% |
| 4 | Связи и даты вахт — выполнено | [f9bba06](https://github.com/Mamoro2142/UchetDohodaPro/commit/f9bba06db365f9c4b121c8071bc35128af4883e9) | [Actions](https://github.com/Mamoro2142/UchetDohodaPro/actions/runs/43584165611): 15 unit-тестов, lint без ошибок, подписанный APK | 50% |
| 5 | Дробный ввод — выполнено | [631add8](https://github.com/Mamoro2142/UchetDohodaPro/commit/631add87e955f33769fc3cdfe36e9a9274cf3f8b) | [Actions](https://github.com/Mamoro2142/UchetDohodaPro/actions/runs/43604061060): 18 unit-тестов, lint без ошибок, подписанный APK | 62,5% |
| 6 | Добавление дня за одно нажатие — выполнено | [85afe0e](https://github.com/Mamoro2142/UchetDohodaPro/commit/85afe0e962f2b0cbe445a016bf6ceb82a22a2e27) | [Actions](https://github.com/Mamoro2142/UchetDohodaPro/actions/runs/43604424318): 18 unit-тестов, lint без ошибок, подписанный APK | 75% |
| 7 | Регрессия и дополнительные исправления — выполнено | [1c53e2a](https://github.com/Mamoro2142/UchetDohodaPro/commit/1c53e2ab2d6470c090832a36c578ebfefb7d4cae), [4afd655](https://github.com/Mamoro2142/UchetDohodaPro/commit/4afd65555b0d3e1c5f75073b61cc8b910695a96b), [81dc3bf](https://github.com/Mamoro2142/UchetDohodaPro/commit/81dc3bf6080a54344a515282c0b3bfa6454037f5), [d59908a](https://github.com/Mamoro2142/UchetDohodaPro/commit/d59908a404a6b35a058293f625c91f9b2f0e5a3c), [7260658](https://github.com/Mamoro2142/UchetDohodaPro/commit/72606589745adef5454e8a837c1899be73094165), [88be410](https://github.com/Mamoro2142/UchetDohodaPro/commit/88be410c958b1f99f04d7b30ef5f6708e2925233) | [Actions](https://github.com/Mamoro2142/UchetDohodaPro/actions/runs/43608549254): 19 unit-тестов и 2 Android-сценария, lint без ошибок, APK | 87,5% |
| 8 | Версия 1.1.0 и выдача APK — выполнено | [66d1ee1](https://github.com/Mamoro2142/UchetDohodaPro/commit/66d1ee1c5ce14a3e21529aa77c5091426780fde0), [60c07d1](https://github.com/Mamoro2142/UchetDohodaPro/commit/60c07d177c5b04b2b0e8560a62c3a084e89aa733), [21a6d84](https://github.com/Mamoro2142/UchetDohodaPro/commit/21a6d84db9e6082dea3294e5ba1de7b2af16bb9d) | [Actions](https://github.com/Mamoro2142/UchetDohodaPro/actions/runs/43631854310): 19 unit-тестов и 2 Android-сценария, lintDebug/lintRelease без ошибок, APK и SHA-256 | 100% |

Общие ограничения проверки и предупреждения приведены ниже; они относятся ко всем пунктам.

## Какие проблемы найдены именно в Pro и что изменено

1. Резервная копия в профиле была заглушкой. Добавлены SAF-экспорт/импорт, формат с версиями 1–3 и SHA-256, проверка ID/связей/дат/чисел, подтверждение замены и атомарное восстановление. Ошибка вставки откатывает все таблицы. Доступ ко всему хранилищу не запрашивается.
2. «Тарифы сохранены» появлялось до завершения записи Room. Подтверждение теперь следует после успешной записи; повторное сохранение блокируется, при ошибке введённые значения сохраняются.
3. Начальный null от Flow мог преждевременно завершить инициализацию формы тарифов и скрыть существующие ставки. Инициализация исправлена.
4. Даты последнего сохранения не было. Добавлен nullable savedAt; дата обновляется после успешного сохранения, переживает перезапуск и резервирование. Старым тарифам дата задним числом не назначается.
5. Авансы и штрафы были постоянными нулями. Добавлены таблица, DAO, миграция, CRUD с суммой в копейках, датой, комментарием и вахтой. Общие, месячные и вахтовые итоги обновляются через Flow. Остаток = начислено − авансы − штрафы.
6. Создание активной вахты проверяло наличие другой отдельно от INSERT. Операция стала транзакционной; быстрые повторные запросы допускают только одну новую активную вахту.
7. DAO не проверял существование назначаемой вахты и границы дат. Добавлены проверки связей и дат при создании, редактировании и переназначении. Старые несовпадения дат можно редактировать без автоматической перепривязки и изменения исторических ставок.
8. Разбор дат допускал некорректные даты или лишний суффикс. Добавлен строгий разбор; даты вахт и дней учитываются в обоих существующих форматах без переписывания истории.
9. Мог отображаться фиктивный номер вахты 1 при неизвестном ID. Убрано ложное значение; номера строятся только по существующим ID и последовательно перенумеровываются после удаления.
10. Счётчики дней могли различаться и завышаться при исторических дублях. Считаются уникальные календарные даты; исторические строки и их доход не удаляются. Новое случайное дублирование даты блокируется.
11. Ввод с запятой обрабатывался неодинаково в расчёте и сохранении. Добавлен общий строгий преобразователь для тарифов, создания и редактирования дня. Некорректные/отрицательные/бесконечные числа и переполнение не сохраняются; точность старых значений при редактировании не обрезается.
12. Новый день открывался через лишнее подтверждение. Форма открывается сразу, состояние маршрута исключает несколько форм от быстрых нажатий.
13. Системный Back из редактора дня закрывал историю целиком. Он возвращает в список дней; проверено на эмуляторе.
14. java.time использовался при minSdk 23 без desugaring. Добавлена совместимость с Android 6–7, без повышения минимальной версии.
15. Исходный CI собирал APK без unit-тестов и lint. Добавлены проверки Room, миграций, восстановления, расчётов и Android-сценарии обновления/переустановки.
16. Во время регрессии новой функции обнаружено, что редактирование аванса после удаления вахты могло автоматически назначить другую вахту либо блокировалось без вахт. Существующая непривязанная операция теперь редактируется без перепривязки. Новая операция требует выбора вахты. Добавлены unit- и UI-проверки.

В исходной Pro уже работали хранение исторических ставок, скрытие пустых показателей, сохранение дней при удалении вахты, предупреждение с количеством дней и переназначение дня. Эти функции сохранены и проверены; они не выдаются за отсутствовавшие функции Pro.

## Регрессионные сценарии

| Сценарий ТЗ | Подтверждение |
| --- | --- |
| Создание дня | Android: одно нажатие, ввод 1,5, совпадение предварительной и сохранённой суммы |
| Изменение и удаление дня | Android: сохранение старой точности и удаление тестовой записи; DAO-проверки |
| Новые тарифы без пересчёта старых дней | Unit: замена текущего тарифа и изменение количества старого дня с его собственной ставкой |
| Создание и завершение вахты | Unit: 20 конкурентных созданий, одна активная, завершение и создание следующей |
| Удаление вахты без потери дней | Unit и Android: дни/операции остаются, доход дня не меняется |
| Переназначение дня | Unit: действующий ID, проверка дат, сохранение ставок и дохода |
| Общая и месячная статистика | Unit расчётов и Android-проверка октябрьских/ноябрьских итогов |
| Авансы и штрафы | Unit CRUD обоих типов и точности копеек; Android CRUD аванса и редактирование после удаления вахты |
| Экспорт и восстановление | Unit: форматы 1–3, повреждения, ID/связи, повторный импорт, откат всех таблиц; Android: настоящий SAF |
| Сохранность при обновлении | Android: подписанный APK 1.0.9 + база v3 → APK 1.1.0 + база v5, сохранены ID, ставки, показатели, связи |
| Работа без интернета | Android: Wi-Fi и мобильные данные отключены перед запуском сценариев |
| Малый экран | Android 8 / API 26, 480×800, 240 dpi; проверены формы, прокрутка, навигация и SAF |

Android дополнительно проверяет отмену восстановления поверх существующих данных, подтверждённую замену, а также восстановление той же публичной SAF-копии после удаления приложения и повторной установки. Использовалась только синтетическая база эмулятора.

Финальные Gradle-задачи: `testDebugUnitTest lintDebug lintRelease assembleDebug assembleDebugAndroidTest`.
Результат: **19 unit-тестов + 2 инструментальных сценария, без ошибок и пропусков**. Оба lint-отчёта: **0 ошибок, по 31 предупреждению**.

## Изменённые файлы по пунктам

Пути относительны к корню репозитория. Общие файлы могут повторяться, поскольку функции добавлялись последовательно.

**Пункт 1: Резервное копирование.**

- `.github/workflows/android.yml`
- `.gitignore`
- `app/build.gradle.kts`
- `app/src/main/java/com/example/uchetdohodapro/BackupControls.kt`
- `app/src/main/java/com/example/uchetdohodapro/MainActivity.kt`
- `app/src/main/java/com/example/uchetdohodapro/data/BackupRepository.kt`
- `app/src/main/java/com/example/uchetdohodapro/data/WorkDatabase.kt`
- `app/src/test/java/com/example/uchetdohodapro/data/BackupTest.kt`
- `docs/WORK_PROGRESS.md`

**Пункт 2: Тарифы и дата сохранения.**

- `app/src/main/java/com/example/uchetdohodapro/TariffsScreen.kt`
- `app/src/main/java/com/example/uchetdohodapro/data/BackupRepository.kt`
- `app/src/main/java/com/example/uchetdohodapro/data/WorkDatabase.kt`
- `app/src/test/java/com/example/uchetdohodapro/data/BackupTest.kt`
- `docs/WORK_PROGRESS.md`

**Пункт 3: Авансы и штрафы.**

- `app/src/main/java/com/example/uchetdohodapro/BackupControls.kt`
- `app/src/main/java/com/example/uchetdohodapro/MainActivity.kt`
- `app/src/main/java/com/example/uchetdohodapro/MoneyOperationsPanel.kt`
- `app/src/main/java/com/example/uchetdohodapro/ShiftDetailsScreen.kt`
- `app/src/main/java/com/example/uchetdohodapro/ShiftScreen.kt`
- `app/src/main/java/com/example/uchetdohodapro/StatisticsScreen.kt`
- `app/src/main/java/com/example/uchetdohodapro/data/BackupRepository.kt`
- `app/src/main/java/com/example/uchetdohodapro/data/WorkDatabase.kt`
- `app/src/test/java/com/example/uchetdohodapro/data/BackupTest.kt`
- `docs/WORK_PROGRESS.md`

**Пункт 4: Связи и даты вахт.**

- `app/src/main/java/com/example/uchetdohodapro/MainActivity.kt`
- `app/src/main/java/com/example/uchetdohodapro/ShiftDetailsScreen.kt`
- `app/src/main/java/com/example/uchetdohodapro/ShiftScreen.kt`
- `app/src/main/java/com/example/uchetdohodapro/StatisticsScreen.kt`
- `app/src/main/java/com/example/uchetdohodapro/WorkDayScreen.kt`
- `app/src/main/java/com/example/uchetdohodapro/WorkHistoryScreen.kt`
- `app/src/main/java/com/example/uchetdohodapro/data/WorkDatabase.kt`
- `app/src/test/java/com/example/uchetdohodapro/data/BackupTest.kt`
- `docs/WORK_PROGRESS.md`

**Пункт 5: Дробный ввод.**

- `app/src/main/java/com/example/uchetdohodapro/TariffsScreen.kt`
- `app/src/main/java/com/example/uchetdohodapro/WorkDayScreen.kt`
- `app/src/main/java/com/example/uchetdohodapro/WorkHistoryScreen.kt`
- `app/src/main/java/com/example/uchetdohodapro/data/WorkDatabase.kt`
- `app/src/test/java/com/example/uchetdohodapro/data/BackupTest.kt`
- `docs/WORK_PROGRESS.md`

**Пункт 6: Добавление дня за одно нажатие.**

- `app/src/main/java/com/example/uchetdohodapro/MainActivity.kt`
- `docs/WORK_PROGRESS.md`

**Пункт 7: Регрессия и дополнительные исправления.**

- `.github/workflows/android.yml`
- `app/build.gradle.kts`
- `app/src/androidTest/java/com/example/uchetdohodapro/RegressionTest.kt`
- `app/src/main/java/com/example/uchetdohodapro/MoneyOperationsPanel.kt`
- `app/src/main/java/com/example/uchetdohodapro/WorkHistoryScreen.kt`
- `app/src/main/java/com/example/uchetdohodapro/data/WorkDatabase.kt`
- `app/src/test/java/com/example/uchetdohodapro/data/BackupTest.kt`
- `docs/WORK_PROGRESS.md`
- `scripts/emulator-regression.sh`
- `scripts/seed_legacy_database.py`

**Пункт 8: Версия 1.1.0 и выдача APK.**

- `.github/workflows/android.yml`
- `app/build.gradle.kts`
- `docs/CHANGELOG.md`
- `docs/WORK_PROGRESS.md`
- `scripts/emulator-regression.sh`
- `scripts/package_apk.py`

## Риски и границы проверки

- Проверка на физическом телефоне пользователя ещё не выполнялась. Установка обновления и переустановка проверены на Android-эмуляторе; Android 6–7 дополнительно покрыты настройкой desugaring и lint, но не отдельным прогоном на этих версиях.
- Предупреждения lint относятся к версиям зависимостей/target SDK и существующей иконке (форма, размеры, дубли). Иконка сохранена по прямому требованию пользователя; массовое обновление зависимостей не выполнялось.
- Попытка добавить отдельную release-конфигурацию выявила конфликт сокращённых L8-библиотек приложения и тестового APK. Для версии 1.1.0 сохранена исходная debug-конфигурация распространения с постоянной подписью. Пользователю выдаётся именно APK, на котором успешно пройдены все проверки.

Пользовательский сценарий обновления: установить APK 1.1.0 поверх имеющейся версии. Затем в «Профиль» можно создать переносимую резервную копию. Исторические ставки и пустые показатели дней сохраняются.
