FROM gradle:8.14.4-jdk21 AS build

WORKDIR /workspace

COPY --chown=gradle:gradle build.gradle settings.gradle ./

RUN gradle resolveDependencies --no-daemon

COPY --chown=gradle:gradle src ./src

RUN gradle bootJar --no-daemon

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /workspace/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
