# bank-transaction-service

Микросервис расходных операций банка. Принимает транзакции в реальном времени, хранит месячные лимиты в USD по категориям товаров и услуг, конвертирует сумму в USD по курсу закрытия и помечает операции, которые этот лимит превысили.

Стек: Java 21, Spring Boot 4.1, Spring Web MVC, Spring Data JPA, Liquibase, PostgreSQL 15, Redis 7, OpenFeign, springdoc-openapi, Actuator и Micrometer. Сборка — Gradle 9 (`./gradlew`).

## Запуск

Нужны Docker, JDK 21 и доступ в интернет при первой сборке (Gradle скачает дистрибутив и зависимости).

Инфраструктура для локального запуска — только PostgreSQL и Redis:

```bash
docker compose up -d postgres redis
./gradlew bootRun
```

Сервис слушает `http://localhost:8080`.

| Что | Адрес |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI | http://localhost:8080/v3/api-docs |
| Health | http://localhost:8080/actuator/health |
| Пробы | http://localhost:8080/actuator/health/liveness и `/readiness` |
| Метрики провайдера курсов | http://localhost:8080/actuator/metrics/frankfurter.requests |

Подключение к PostgreSQL, Redis, TTL кэша, URL и таймауты Frankfurter заданы в `src/main/resources/application.properties`. Пароль локальной базы совпадает с `docker-compose.yml`.

Весь стек, включая приложение, поднимается одной командой. Образ собирается multi-stage `Dockerfile` (Gradle 9 + JDK 21, затем JRE Temurin 21):

```bash
docker compose up -d --build
```

Контейнер приложения получает URL PostgreSQL через `SPRING_DATASOURCE_URL` и ходит в сервис `postgres`. Порт на хосте — `8080`.

Тесты (нужен запущенный Docker: Testcontainers сам поднимает `postgres:15` и `redis:7`):

```bash
./gradlew test
```

Сборка jar без тестов: `./gradlew bootJar`.

## API

Два REST API, без авторизации. Входящие тела проверяет Bean Validation. Невалидный запрос — HTTP 400. Если курс к USD не найден ни в базе, ни у провайдера, ни среди прошлых закрытий — HTTP 404.

JSON — camelCase, как у Jackson по умолчанию. Категория в теле запроса — `PRODUCT` или `SERVICE`.

### Интеграция: приём транзакции

`POST /api/integration/transactions` → 201 и идентификатор сохранённой операции.

```bash
curl -s -X POST http://localhost:8080/api/integration/transactions \
  -H 'Content-Type: application/json' \
  -d '{
    "accountFrom": "0000000123",
    "accountTo": "9999999999",
    "currencyShortname": "KZT",
    "sum": 10000.45,
    "expenseCategory": "PRODUCT",
    "datetime": "2022-01-30T00:00:00+06:00"
  }'
```

Счета — 10 цифр, валюта — код ISO 4217 из трёх заглавных букв, сумма — неотрицательное число с двумя знаками после точки, `datetime` — ISO 8601 со смещением.

### Клиент: лимиты и превышения

| Метод | Путь | Результат |
|---|---|---|
| `POST` | `/api/client/limits` | Новый лимит, 201. Дату ставит сервер |
| `GET` | `/api/client/limits/{accountFrom}` | История лимитов счёта |
| `GET` | `/api/client/transactions/{accountFrom}/exceeded` | Операции с `limit_exceeded = true` и лимит, который действовал в момент операции: `limitSum`, `limitDatetime`, `limitCurrencyShortname` (`USD`) |

```bash
curl -s -X POST http://localhost:8080/api/client/limits \
  -H 'Content-Type: application/json' \
  -d '{
    "accountFrom": "0000000123",
    "expenseCategory": "PRODUCT",
    "limitSum": 1000.00
  }'
```

Уже сохранённый лимит не изменяется: новая установка — новая строка. Если для пары (счёт, категория) лимита ещё нет, при приёме транзакции берётся 1000 USD.

## Часовой пояс

Границы месяца считаются в **Europe/Minsk** (`MONTH_ZONE` в `IntegrationTransactionServiceImpl`). Тот же пояс у бина `Clock` в `ClockConfig`.

Время операции приходит как `OffsetDateTime` с любым смещением. Перед расчётом оно переводится в Минск, и месяц — это календарный месяц в этой зоне: с полуночи первого числа включительно до полуночи первого числа следующего месяца. Операция `2022-01-01T00:30:00+06:00` в Минске — это `2021-12-31T21:30:00+03:00`, она попадает в декабрь.

Курс берётся на календарную дату операции в том же поясе. Текущее время для даты нового лимита читается только из `Clock`, чтобы тесты могли перевести часы и воспроизвести сценарии задания.

## Курсы валют и сбои внешнего API

Сумма в USD — `sum × курс` с округлением scale 2, `HALF_UP`. USD к USD — это `1`, без кэша, базы и HTTP.

Для остальных валют курс закрытия пары `{валюта}/USD` на день операции ищется по цепочке:

1. Redis, ключ `exchange-rate:{base}:USD:{date}`.
2. Строка за эту дату в PostgreSQL (`exchange_rate`).
3. Frankfurter (`https://api.frankfurter.dev`), дневной курс. Ответ сохраняется в PostgreSQL и в Redis.
4. Последнее закрытие в PostgreSQL строго раньше запрошенного дня (выходной, праздник или сбой API). В Redis оно не пишется: следующий вызов снова спросит провайдера за нужную дату.
5. Если нет и прошлого закрытия — `RateNotFoundException`, HTTP 404. Транзакция не сохраняется, чтобы в базе не оказалась сумма в USD без курса. Повтор запроса после появления курса проходит обычным путём.

Провайдер не роняет приём, пока есть хотя бы одно прошлое закрытие. Клиент `FrankfurterExchangeRateClient` наружу исключений не бросает: пустой ответ, неположительный курс, 404/422 и прочие ошибки Feign становятся пустым `Optional`, и сервис переходит к шагу 4.

Таймауты и повторы заданы в `FrankfurterConfig` и `application.properties`: соединение 2 секунды, чтение 3 секунды, до 3 попыток (`Retryer`, пауза от 200 мс). Ответы 5xx помечаются как повторяемые. Пока идут ретраи, транзакция ждёт курс и не фиксируется с неверной суммой.

Redis — ускорение чтения, не источник истины. Таймауты подключения и команды — по 1 секунде, TTL ключа — 24 часа. Сбой Redis логируется как warn: чтение считается промахом, запись пропускается, приём продолжается через PostgreSQL и Frankfurter. Гонка двух запросов за одну и ту же дату ловится уникальным ключом `(base, quote, rate_date)`: победитель сохраняет строку, второй читает уже записанный курс.

Каждый вызов Frankfurter попадает в таймер `frankfurter.requests` с тегом `outcome`: `success`, `empty`, `missing`, `error`.

## Лимиты и параллельные транзакции

Флаг `limit_exceeded` ставится в момент приёма и потом не пересчитывается. Новый лимит действует только на операции, у которых `datetime` не раньше даты этого лимита. Расходы разных категорий и разных месяцев не смешиваются.

Две параллельные транзакции одного счёта и одной категории не должны увидеть один и тот же остаток. Перед расчётом `BankTransactionService` берёт `PESSIMISTIC_WRITE` по строке `account_category_lock` для пары (счёт, категория). Второй запрос ждёт конца транзакции первого и суммирует уже сохранённые `sum_usd`.

## Данные

Схему создаёт только Liquibase: `src/main/resources/db/changelog/`. Hibernate работает с `ddl-auto=validate` и таблицы не создаёт.

Таблицы: `expense_limit`, `bank_transaction`, `exchange_rate`, `account_category_lock`. У транзакции есть внешний ключ на лимит, который действовал в момент операции. Если действовали неявные 1000 USD, ссылка пустая, а в ответе превышения сумма лимита — `1000.00`, валюта — `USD`.

## Тесты

| Слой | Где |
|---|---|
| Логика лимита, в том числе смена лимита внутри месяца и нулевой остаток | `service/impl/BankTransactionServiceTest` |
| Курсы, фолбэк на последнее закрытие, недоступный провайдер | `service/impl/ExchangeRateServiceTest` |
| Репозитории на реальном PostgreSQL в Testcontainers | `repository/` |
| Redis | `cache/` |
| Сквозной сценарий «1 случай» из задания: лимиты, транзакции и ровно две операции (3 и 13 января) с суммой, датой и валютой превышенного лимита | `endtoend/CaseOneApiTest` |

Сквозной тест поднимает приложение на случайном порту, PostgreSQL и Redis в Testcontainers, Frankfurter подменяет WireMock. Часы подменяются бином `Clock`. Отдельная ручная подготовка окружения не нужна, кроме Docker. Запуск — `./gradlew test`.

## CI

GitHub Actions: `.github/workflows/ci.yml`. На push в `main`/`master` и на pull request два job: сборка `bootJar` и `./gradlew test`.

## AI в проекте

Основной инструмент — Cursor. Им добавлялись интеграционные тесты репозиториев, сквозной API-тест, кэш курсов в Redis, логирование сервисов, Actuator и таймер Frankfurter, workflow GitHub Actions, правки Dockerfile и `AGENTS.md`. Сгенерированный код сверялся с заданием и с прогоном тестов; спорные места разбирались по исходникам, а не оставлялись как есть.

Вручную зафиксированы решения, которые определяют поведение сервиса: схема Liquibase и подход schema first, блокировка пары (счёт, категория) в `account_category_lock`, бин `Clock` в зоне Europe/Minsk, контракт `ExchangeRateClient` (пустой результат вместо исключения) и цепочка «Redis → PostgreSQL → провайдер → последнее закрытие».

Где сгенерированный код был неверным и как это всплыло:

- `BankTransactionServiceApplicationTests` поднимал контекст Spring и подключался к `localhost:5432` из `application.properties`. Локально тест был зелёным, потому что PostgreSQL уже слушал порт из Docker Compose. В GitHub Actions контейнера не было, Liquibase падал с `Connection refused`. В тест добавлены Testcontainers PostgreSQL и Redis, как в остальных тестах контекста.
- Первый вариант Dockerfile опирался на образ `openjdk:17-slim`. Сборка остановилась с `failed to solve: openjdk:17-slim: not found`. Базовый образ заменён на `eclipse-temurin:21-jre`, сборка — на `gradle:9.7-jdk21`.
- `ClientLimitServiceTest.savesLimit` падал с `NullPointerException: clock` в `OffsetDateTime.now(clock)`. В мок сервиса не передали `Clock`. После подстановки часов тест совпал с продовым способом читать время.

### MCP

Конфигурация без секретов лежит в `.cursor/mcp.example.json`. Файл с токеном в репозиторий не коммитится: локально это `.cursor/mcp.json`, ключ подставляется в заголовок `Authorization`.

- **GitHub** (`https://api.githubcopilot.com/mcp/`) — issues, pull request и статус workflow. Им удобно смотреть упавший job, не копируя лог в чат. Так разбиралось падение `contextLoads` в CI: в логе был `Connection refused` к PostgreSQL, которого на раннере нет.
- **Docker** (`docker mcp gateway run`) — шлюз к каталогу MCP-серверов Docker. Сам `docker compose` он не выполняет. Живые контейнеры Postgres, Redis и приложения проверяются командами Docker: после `docker compose up` по `docker compose ps` и логам `bank_transaction-app` видно, что Liquibase применил схему и Tomcat слушает порт из `application.properties`.

### Скилл

Рецепт добавления провайдера курсов: `.agents/skills/add-exchange-rate-provider/SKILL.md`. Cursor и GitHub Copilot читают каталог `.agents/skills/`. Достаточно попросить агента добавить или заменить источник курсов (Frankfurter, `ExchangeRateClient`, другой rates API).

Скилл указывает реальные классы пакета `rates/` (образец — набор `Frankfurter*`), ключи в `application.properties`, unit-тест на Mockito и сквозной тест с WireMock. Отдельно зафиксировано: второй бин `@Component implements ExchangeRateClient` роняет старт с `NoUniqueBeanDefinitionException`, пока в сервисе один клиент без квалификатора.
