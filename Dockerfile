# Multi-stage build: compile with a full JDK + Maven image, run on a slim JRE.
# Only Docker is needed on the host — no local JDK or Maven.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
# BuildKit cache mount: the local Maven repository survives between builds,
# so a rebuild after a code change doesn't download every dependency again.
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /build/target/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
