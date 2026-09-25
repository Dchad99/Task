# Multi-stage build: compile with a full JDK + Maven image, run on a slim JRE.
# No local Maven install is required to build the image — only Docker.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# Copy the POM first so dependency resolution is cached across rebuilds that
# only change source files, not dependencies.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
