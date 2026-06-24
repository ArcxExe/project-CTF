FROM gradle:8.14.4-jdk21 AS build
WORKDIR /workspace

COPY --chown=gradle:gradle build.gradle settings.gradle ./

# Используем кэш BuildKit для сохранения скачанных зависимостей между сборками
RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle resolveDependencies --no-daemon

COPY --chown=gradle:gradle src ./src

# Снова монтируем кэш, чтобы сборка jar-файла прошла быстрее
RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle bootJar --no-daemon

# Используем alpine-версию для минимального размера итогового образа
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Создаем пользователя без root-прав для безопасности
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=build /workspace/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
