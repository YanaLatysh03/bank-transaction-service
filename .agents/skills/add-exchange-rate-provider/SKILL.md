---
name: add-exchange-rate-provider
description: Recipe for adding a new exchange-rate provider to bank-transaction-service (Spring Boot + Feign) — which classes to create in the rates/ package, Feign client config, @ConfigurationProperties, resolving multi-bean ambiguity in ExchangeRateServiceImpl, and which tests to write (unit with Mockito + e2e with WireMock). Use whenever the user asks to add, replace, or swap a currency/exchange rate provider, rate source, or mentions ExchangeRateClient, Frankfurter, or integrating any rates API.
---

# Добавление провайдера курсов валют

Рецепт для `bank-transaction-service`. Эталон — пакет
`src/main/java/com/latysh/bank/transactionservice/rates/` (набор `Frankfurter*`).
Перед написанием кода прочитай эти файлы; новый провайдер должен выглядеть так же.

Дальше `<Provider>` — PascalCase-имя провайдера (например `Cbr`, `ExchangerateHost`),
а `<provider>` — его kebab-префикс для properties (например `cbr`, `exchangerate-host`).

## Что создать

| Файл в `rates/` | Роль |
|---|---|
| `<Provider>Properties` | record + `@ConfigurationProperties(prefix = "<provider>")` |
| `<Provider>Api` | Feign-интерфейс с `@RequestLine` |
| `<Provider>Config` | `@Configuration`, собирает Feign-бин |
| `<Provider>RateRs` | record-ответ API |
| `<Provider>ExchangeRateClient` | `implements ExchangeRateClient`, `@Component` |

Плюс ключи в `application.properties` и тесты (см. ниже).

## Авторизация и API-ключи — уточни до кода

Новый провайдер может требовать ключ или авторизацию (query-параметр, заголовок
`Authorization` или кастомный заголовок — зависит от API). Ответ влияет на
`Properties`, `Config` и `application.properties`, поэтому выясни это **до** написания
кода:

1. Спроси пользователя, требует ли выбранный API ключ и есть ли у него готовый.
2. **Ключ есть** — попроси его и положи в `application.properties`
   (`<provider>.api-key=...`). Проект не использует секрет-хранилище (пароль БД лежит
   прямо в properties), так что это конвенция проекта; но предложи env-подстановку
   `<provider>.api-key=${<PROVIDER>_API_KEY:}` — задел на случай публичного репозитория.
3. **Ключа нет** — не выдумывай значение и не оставляй молчаливую заглушку. Подскажи,
   где взять: страница провайдера для разработчиков (разделы «Get API key», «Sign up»,
   «Pricing»); у большинства API курсов есть бесплатный тариф. Если ключ заводить не
   хочется — назови альтернативы без авторизации (например, ЦБ РФ cbr-xml-daily.ru
   или НБ РБ nbrb.by).

Как ключ доезжает до запроса — зависит от API. В Feign это `requestInterceptor`
(заголовок) или параметр в `@RequestLine` (query-параметр):

```java
// в <Provider>Config, при наличии поля apiKey в <Provider>Properties:
.requestInterceptor(template -> template.header("Authorization", "Bearer " + properties.apiKey()))
```

Если ключ нужен — добавь поле `String apiKey` в `<Provider>Properties`.

## Шаги

### 1. Properties

```java
@ConfigurationProperties(prefix = "<provider>")
public record <Provider>Properties(
    String baseUrl,
    Duration connectTimeout,
    Duration readTimeout,
    int attempts
) {}
```

В `application.properties` добавь `<provider>.base-url`, `connect-timeout` (по образцу
Frankfurter — 2s), `read-timeout` (3s), `attempts` (3). Если API требует ключ — добавь
поле `String apiKey` (см. секцию про авторизацию).

### 2. Feign API + ответ

`@RequestLine`-метод, возвращающий `<Provider>RateRs`. У Frankfurter это
`GET /v2/rate/{base}/{quote}?date={date}` — у нового провайдера путь свой, смотри его доку.

### 3. Config

Скопируй структуру `FrankfurterConfig`:

```java
@Configuration
@EnableConfigurationProperties(<Provider>Properties.class)
public class <Provider>Config {
    @Bean
    <Provider>Api <provider>Api(<Provider>Properties properties) {
        return Feign.builder()
            .decoder(new Jackson3Decoder())
            .options(new Request.Options(properties.connectTimeout(), properties.readTimeout(), true))
            .retryer(new Retryer.Default(200, 1000, properties.attempts()))
            .errorDecoder(...)  // см. ниже
            .target(<Provider>Api.class, properties.baseUrl());
    }
}
```

`errorDecoder` — важная часть контракта, не пропускай:
- «нет курса за дату» (у Frankfurter 404/422; у нового API — его статус) → `RateMissing`
- 5xx → `RetryableException` (тогда сработает `Retryer`)
- прочее → `FeignException.errorStatus(methodKey, response)`

### 4. Клиент

`@Component`, конструктор через `@RequiredArgsConstructor`, единственный публичный метод —
реализация `ExchangeRateClient#getExchangeRates(String base, String quote, LocalDate date)`.

Инварианты контракта (почему — см. `ExchangeRateServiceImpl.usdPerUnit`):
- **Никогда не бросает исключений** — недоступность/ошибка API это `Optional.empty()`,
  а не исключение. Сервис трактует `empty` как «курса за день нет» и падает к последнему
  закрытию из БД; исключение сломало бы этот фолбэк.
- Ложный курс отбрасывай: `body == null || rate == null || rate.signum() <= 0` → `empty`.
- Сервис передаёт ISO-коды в верхнем регистре; нормализация под требования API —
  на стороне клиента (Frankfurter, например, требует lowercase).

## Неоднозначность бинов — остановись и спроси

`ExchangeRateServiceImpl` инжектит **единственный** `ExchangeRateClient`:

```java
private final ExchangeRateClient client;
```

Второй `@Component implements ExchangeRateClient` уронит стартап с
`NoUniqueBeanDefinitionException`. Если пользователь не сказал, как сосуществовать
с Frankfurter, — спроси. Варианты:

1. `@Primary` на приоритетном клиенте — минимальная правка, но выбор «тихий».
2. `@Qualifier` в сервисе — явно, но связывает сервис с конкретным провайдером.
3. Инжект `List<ExchangeRateClient>` с обходом по порядку (fallback-цепочка) —
   новый провайдер добавляется вообще без правок сервиса, но это рефакторинг
   `ExchangeRateServiceImpl` и `ExchangeRateServiceTest`.

Если провайдер должен **заменить** Frankfurter — тоже уточни: удалить старые классы
или оставить оба (и тогда снова вопрос приоритета).

## Тесты

### Unit-тест клиента

`src/test/java/.../rates/<Provider>ExchangeRateClientTest.java`,
`@ExtendWith(MockitoExtension.class)`, мокается `<Provider>Api`. Сценарии:

1. happy path — API вернул курс, клиент вернул `Optional.of(rate)`
2. `null`-body / `null`-rate / rate <= 0 → `Optional.empty()`
3. `RateMissing` и `FeignException` от API → `Optional.empty()`, без throw

### E2E (если провайдер участвует в сценариях)

По образцу `endtoend/CaseOneApiTest`: `WireMockServer` на динамическом порту,
`@DynamicPropertySource` подменяет `<provider>.base-url`, стаб
`get(urlPathEqualTo(...)).willReturn(okJson(...))`.

### Запуск

```
./gradlew test
```

Нужен запущенный Docker: repository- и e2e-тесты поднимают `postgres:15` через Testcontainers.

## Чек-лист

- [ ] Прочитан эталон `rates/Frankfurter*`
- [ ] Авторизация уточнена (ключ в properties или известно, где его взять)
- [ ] `<Provider>Properties` (record, `@ConfigurationProperties`)
- [ ] `<Provider>Api` (Feign `@RequestLine`) + `<Provider>RateRs`
- [ ] `<Provider>Config` (таймауты, `Retryer`, `errorDecoder`)
- [ ] `<Provider>ExchangeRateClient` (`@Component`, никогда не бросает)
- [ ] Ключи в `application.properties`
- [ ] Неоднозначность бинов решена (вопрос задан пользователю)
- [ ] Unit-тест клиента: 3 сценария
- [ ] `./gradlew test` зелёный
