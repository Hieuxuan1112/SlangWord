FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# Dependencies resolve in their own layer so source edits do not re-download the world.
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# Base images lag behind the Alpine package repo, so OS packages are patched at
# build time. Without this the image ships whatever openssl the base was built
# with, and the Trivy job in CI fails on it.
RUN apk --no-cache upgrade \
    && addgroup -S app && adduser -S app -G app
COPY --from=build /build/target/slangword-api.jar app.jar
USER app
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
