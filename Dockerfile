# === ЭТАП 1: Сборка приложения ===
FROM gradle:9.7-jdk21 AS builder
WORKDIR /app

# Копируем файлы конфигурации Gradle для кэширования зависимостей
COPY build.gradle settings.gradle ./

# Скачиваем зависимости (этот шаг закэшируется, если build.gradle не менялся)
RUN gradle build --no-daemon -x test || true

# Копируем исходный код приложения
COPY src ./src

# Собираем чистый production-бинарник (толстый JAR-файл) без запуска тестов
RUN gradle bootJar --no-daemon -x test
# Примечание: Если вы не используете Spring Boot, замените 'bootJar' на обычный 'jar'

# === ЭТАП 2: Финальный минимальный образ для запуска ===
FROM eclipse-temurin:21-jre AS runner
WORKDIR /app

# Создаем системного пользователя для безопасности (не запускаем от root)
RUN useradd -m appuser
USER appuser

# Копируем собранный JAR-файл из этапа сборки
# (Обычно Gradle собирает его в директорию build/libs/)
COPY --from=builder /app/build/libs/*.jar app.jar

# Открываем порт приложения (например, стандартный для Spring Boot — 8080)
EXPOSE 8080

# Переменные окружения для оптимизации работы JVM в контейнере
ENV JAVA_OPTS="-XX:+UseG1GC -XX:+UseContainerSupport"

# Команда запуска приложения
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
