# AGENTS.md

Единый файл инструкций для агентов (Codex, Cursor, Copilot, Claude Code, ZCode).
Проект — `bank-transaction-service`: тестовое задание «Сервис банковских транзакций»
(исходные требования — `Тестовое_задание_Junior_Java_разработчика_2026.pdf` в корне).

## Стек

- Java 21 (toolchain; Gradle сам докачает JDK, если его нет), Gradle 9+, Spring Boot 4.1.1
- Spring Web MVC, Data JPA (Hibernate, `ddl-auto=validate`), Validation, Liquibase
- PostgreSQL 15 (локально — docker-compose, в тестах — Testcontainers)
- Redis 7 — кэш курсов поверх PostgreSQL (локально — docker-compose, в тестах — Testcontainers)
- Spring Boot Actuator + Micrometer: health-пробы и метрики, без отдельного Prometheus-реестра
- OpenFeign 13.x (core + jackson3, без spring-cloud), springdoc-openapi (Swagger UI)
- Lombok + MapStruct (с `lombok-mapstruct-binding`), тесты — JUnit 5, Mockito, AssertJ, WireMock

Обратите внимание: это Spring Boot 4 — стартеры называются `spring-boot-starter-webmvc`,
`spring-boot-starter-data-jpa-test`, `spring-boot-starter-webmvc-test`, HTTP-клиент тестов —
`RestTestClient`. Не «исправляйте» их на привычные имена из Boot 3.

## Команды

Wrapper (`gradlew`) в репозиторий не закоммичен — используется системный Gradle 9+.

```bash
docker compose up -d   # PostgreSQL 15 (5432) и Redis 7 (6379) для локального запуска
gradle bootRun          # запустить приложение (нужны поднятые Postgres и Redis)
gradle test             # все тесты; ТРЕБУЕТСЯ запущенный Docker (Testcontainers postgres:15 и redis:7)
gradle build            # сборка + тесты
```

- Приложение: `http://localhost:8080`, Swagger UI: `http://localhost:8080/swagger-ui.html`
- Health: `http://localhost:8080/actuator/health`, пробы — `/actuator/health/liveness` и `/actuator/health/readiness`
- Метрики: `http://localhost:8080/actuator/metrics`, таймер провайдера —
  `http://localhost:8080/actuator/metrics/frankfurter.requests`
- Подключение к БД, Redis, TTL кэша и ключи Frankfurter — в `src/main/resources/application.properties`

## Архитектура

Слои по пакетам в `com.latysh.bank.transactionservice`:

| Пакет | Роль |
|---|---|
| `controller/api/` | REST-контракты: интерфейсы с `@RequestMapping`, swagger- и валидационными аннотациями |
| `controller/` | Тонкие `@RestController`, реализуют интерфейсы из `controller/api/`, только делегируют сервису |
| `service/` + `service/impl/` | Бизнес-логика: интерфейс + `*Impl` |
| `repository/` | Spring Data JPA |
| `entity/` | JPA-сущности (+ `ExpenseCategoryConverter`) |
| `rq/`, `rs/` | DTO запросов/ответов — records с `@Schema` и bean validation |
| `rates/` | Интеграция с внешним провайдером курсов валют (Frankfurter через Feign) |
| `cache/` | Кэш курсов: контракт `ExchangeRateCache`, Redis-реализация, properties и config |
| `exception/` | Доменные исключения |
| `config/` | `ClockConfig` — бин `Clock` (европа/Минск), время всегда через него |

### REST API

- `POST /api/integration/transactions` — принять расходную операцию (201)
- `POST /api/client/limits` — установить новый лимит (201)
- `GET /api/client/limits/{accountFrom}` — история лимитов счёта
- `GET /api/client/transactions/{accountFrom}/exceeded` — операции, превысившие лимит

### Домен

- Лимит расходования действует на пару (счёт × категория `product`/`service`), всегда в USD.
  Лимиты неизменяемы: новый лимит — новая строка, история сохраняется.
- Входящая транзакция конвертируется в USD (`ExchangeRateServiceImpl.toUsd`) по цепочке:
  Redis-кэш за дату → курс за дату из БД → провайдер API (Frankfurter) → последнее доступное
  закрытие из БД → `RateNotFoundException` (404 на API). USD в USD — это `1`, без кэша, БД и API.
- Флаг `limit_exceeded` проставляется при приёме транзакции. Чтобы учёт лимита был
  консистентен при конкурентных записях, `BankTransactionService` берёт pessimistic-lock
  (`PESSIMISTIC_WRITE`) по паре (счёт, категория) через `account_category_lock`.
- Бизнес-часовой пояс — `Europe/Minsk` (`MONTH_ZONE` в `IntegrationTransactionServiceImpl`).

### Провайдер курсов (`rates/`)

Контракт `ExchangeRateClient`: метод возвращает `Optional<BigDecimal>` и **никогда не бросает
исключений** — недоступность API это `empty`, сервис трактует его как «курса нет» и делает
фолбэк к последнему закрытию. `errorDecoder` в `*Config` превращает 404/422 в `RateMissing`,
5xx — в `RetryableException` (плюс `Retryer`). Добавление нового провайдера — по рецепту
`.agents/skills/add-exchange-rate-provider/SKILL.md`; помните, что второй
`@Component implements ExchangeRateClient` уронит стартап (`NoUniqueBeanDefinitionException`).
Каждый такой клиент замеряет вызов таймером Micrometer — образец в `FrankfurterExchangeRateClient`,
правила ниже в «Метрики».

### База данных

Схема управляется только Liquibase: `src/main/resources/db/changelog/` (мастер-yaml включает
`changes/NNN-имя.sql`). `spring.jpa.hibernate.ddl-auto=validate` — Hibernate ничего не
создаёт, изменение схемы = новый ченджсет + включение в мастер-файл.

### Кеширование курсов (`cache/`)

Кэш — ускорение чтения курса за конкретный день, не источник истины. PostgreSQL остаётся
хранилищем; недоступный Redis не роняет приём транзакции.

- Контракт `ExchangeRateCache`: `get` → `Optional<BigDecimal>`, `put` ничего не возвращает.
  Реализация `RedisExchangeRateCache` не `@Component`: бин собирает `ExchangeRateCacheConfig`.
- Ключ `exchange-rate:{base}:{quote}:{date}` (`RedisExchangeRateCache.key`), значение —
  `BigDecimal.toPlainString()` через `StringRedisTemplate`. Коды валют — как передал сервис,
  ISO в верхнем регистре.
- TTL — `exchange-rate.cache.ttl` (`ExchangeRateCacheProperties`, по умолчанию `24h`).
  Подключение: `spring.data.redis.host/port`, таймауты connect и command по `1s`.
- В кэш попадает только курс **запрошенного** дня (строка из БД или ответ провайдера после
  сохранения). Последнее закрытие-фолбэк и тождество USD туда не пишутся: иначе следующий
  вызов не спросит API за нужную дату.
- Сбой Redis (`DataAccessException`) — `warn` и деградация: `get` как промах, `put`/`delete`
  проглатываются. Нечисловое значение в ключе удаляется и тоже считается промахом.
- Тесты: unit `cache/RedisExchangeRateCacheTest` мокает `StringRedisTemplate`; интеграция
  `cache/ExchangeRateCacheIntegrationTest` — `@SpringBootTest` + Testcontainers `redis:7`
  (`@ServiceConnection(name = "redis")`) рядом с `postgres:15`. Любой `@SpringBootTest`
  (e2e, `contextLoads`) поднимает тот же Redis-контейнер, иначе контекст не стартует.

### Метрики

Actuator, без своего `MeterRegistry` и без Prometheus. Наружу открыты только `health` и
`metrics` (`management.endpoints.web.exposure.include`). Пробы включены
(`management.endpoint.health.probes.enabled=true`), детали health видны всегда
(`show-details=always`); отдельной авторизации на actuator нет — не «закрывайте» его.

Стартер сам отдаёт метрики HTTP-сервера и JVM; в health входят Postgres и Redis. Своя метрика одна:
таймер `frankfurter.requests` в `FrankfurterExchangeRateClient`
(«Calls to the Frankfurter exchange rate API»). `Timer.start(MeterRegistry)` оборачивает
весь вызов клиента, `stop` — в `finally`; ретраи Feign внутрь одного сэмпла не дробятся.
Тег `outcome`:

| Значение | Когда |
|---|---|
| `success` | провайдер вернул курс `> 0` |
| `empty` | пустое тело, `null`-курс или курс `<= 0` |
| `missing` | пойман `RateMissing` (у Frankfurter это 404/422) |
| `error` | прочий `FeignException` |

Смотреть: `GET /actuator/metrics/frankfurter.requests?tag=outcome:success` (и остальные
значения тега). Новый провайдер заводит свой таймер `<provider>.requests` с тем же тегом
`outcome` и тем же контрактом «клиент не бросает». `MeterRegistry` инжектится в клиент,
статический реестр не заводить.

### Логирование

Отдельного `logback.xml` нет — уровни Spring Boot по умолчанию (root INFO). SQL уже
печатает Hibernate: `spring.jpa.show-sql=true` и `format_sql=true`. Повторно логировать
SQL из сервисов не нужно.

- Логгер — Lombok `@Slf4j` на сервисе или кэше. `LoggerFactory` вручную и логи в
  контроллерах не добавлять: контроллер только делегирует.
- Текст на английском, параметры через `{}`, без конкатенации. Исключение — последний
  аргумент, тогда SLF4J приложит stack trace.
- **INFO** — смена состояния: приём и сохранение транзакции, создание лимита, поход
  к провайдеру за курсом (`IntegrationTransactionServiceImpl`, `BankTransactionService`,
  `ClientLimitServiceImpl`, `ExchangeRateServiceImpl`).
- **DEBUG** — чтение и ожидаемые ветки: списки лимитов и превышений, дефолтный лимит
  1000 USD, курс уже лежит в БД (гонка по уникальности).
- **WARN** — деградация без падения запроса: API не отдал курс дня и взято последнее
  закрытие; Redis не прочитал, не записал или не удалил ключ.
- В сообщениях уже есть счета, суммы и коды валют. Пароль БД, токены MCP и ключи
  провайдеров в логи не попадают.

## Конвенции

- **Контроллеры** не содержат маппингов: `@RequestMapping`/`@GetMapping`/swagger/валидация
  живут на интерфейсе в `controller/api/`, контроллер просто реализует его.
- **DI** — конструктор через `@RequiredArgsConstructor` (Lombok), без `@Autowired` на полях.
- **DTO** — records в `rq/`/`rs/`, описание полей на русском в `@Schema`, ограничения
  jakarta-validation прямо на полях.
- **Деньги** — `BigDecimal`, хранение/сравнение лимитов в USD, округление scale 2 `HALF_UP`.
  Коды валют — ISO в верхнем регистре.
- **Время** — никогда `LocalDate.now()`/`Instant.now()` в прод-коде: инжектить `Clock`
  (бин из `ClockConfig`); в тестах подменять `@Primary`-биной.
- **Язык**: идентификаторы и коммиты — английский; swagger-описания, javadoc и тексты тестов —
  русский.
- **Тесты** (все — AssertJ):
  - unit: `service/impl/` — `@ExtendWith(MockitoExtension.class)`, стиль given/when/then,
    имена методов-сценариев в camelCase (`findsLimitWhen...`);
  - репозиториев: `@DataJpaTest` + `@Testcontainers` + `@ServiceConnection`
    (`PostgreSQLContainer("postgres:15")`, `replace = NONE` — гоняется на реальном Postgres);
  - e2e: `endtoend/` — `@SpringBootTest(RANDOM_PORT)` + `RestTestClient`, внешний API
    подменяется WireMock через `@DynamicPropertySource` (`frankfurter.base-url`), время —
    вложенный `AdjustableClock` как `@Primary` бин `Clock`; рядом контейнер `redis:7`.
- Перед завершением задачи запускайте `gradle test` целиком — изолированный запуск
  одного теста не ловит регрессий контекста.

## AI и MCP

Этот файл — единственные общие инструкции для агентов (Codex, Cursor, Copilot, Claude Code,
ZCode). Повторяемый рецепт живёт отдельно в `.agents/skills/<имя>/SKILL.md` и читается
до кода, если задача ему подходит; в AGENTS.md его не копировать. Сейчас там один навык —
добавление провайдера курсов.

Подключение MCP — образец `.cursor/mcp.example.json`. Файл с настоящим токеном в репозиторий
не коммитить: в примере стоит плейсхолдер `ВАШ_КЛЮЧ`.

| Сервер | Подключение | Когда звать |
|---|---|---|
| `github` | HTTP `https://api.githubcopilot.com/mcp/`, заголовок `Authorization: Bearer <токен>` | issues, pull request, файлы на GitHub, статус CI |
| `docker` | локальная команда `docker mcp gateway run` | живы ли контейнеры Postgres/Redis, образы, логи `docker compose` |

- Токен GitHub, пароль БД и ключи провайдеров не подставлять в чат, логи и аргументы
  инструментов.
- MCP не заменяет команды проекта. Зависимости поднимает `docker compose up -d`, тесты —
  `gradle test`. Docker MCP только показывает состояние контейнеров; Testcontainers
  в тестах по-прежнему стартуют сами.
- Сервер не авторизован или не отвечает — править код по репозиторию и не блокировать
  задачу на этом MCP.
- Коммиты и идентификаторы — английский; swagger, javadoc и тексты тестов — русский.

## Что где ещё

- `HELP.md` — шаблонный файл Spring Initializr (не документация проекта).
- `.agents/skills/` — навыки-рецепты для агентов (см. выше про провайдера курсов).
- `.cursor/mcp.example.json` — образец MCP-серверов GitHub и Docker, без боевых секретов.
