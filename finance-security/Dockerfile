# 🔨 Stage 1: Сборка проекта
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Копируем только pom.xml и файлы для кэширования зависимостей
COPY pom.xml .
COPY .mvn ./.mvn

# Предзагрузка зависимостей (ускоряет сборку, если pom.xml не меняется)
RUN mvn dependency:go-offline

# Копируем оставшиеся файлы и собираем проект
COPY src ./src
RUN mvn clean package -DskipTests

# 🚀 Stage 2: Финальный контейнер
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Создаем непривилегированного пользователя
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Копируем готовый JAR из build-стадии
COPY --from=build /app/target/*.jar app.jar

# Настройка переменных окружения по умолчанию
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

# Проверка работоспособности
HEALTHCHECK --interval=30s --timeout=3s \
  CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

EXPOSE 8080

# Оптимизированный запуск
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"] 