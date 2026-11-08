# The backend image, built from source: docker build -t branch-appointments-backend .
# Tests are not run here (they need Docker for Testcontainers); CI runs `mvn verify` before building the image.
# Configuration comes from environment variables at run time (CLAUDE.md, "Container image"); every secret comes from
# Vault (VAULT_URI plus VAULT_TOKEN or AppRole).

# The jar is the same for every platform, so it is built once, natively, even for a multi-platform image
FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn --batch-mode --no-transfer-progress dependency:go-offline
COPY src/main ./src/main
RUN --mount=type=cache,target=/root/.m2 \
    mvn --batch-mode --no-transfer-progress -Dmaven.test.skip=true package \
    && cp target/appointments-*.jar app.jar \
    # Spring Boot's layers (dependencies, loader, snapshots, application), so a code change rebuilds only the last one
    && java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:21-jre-noble
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app --no-create-home app
COPY --from=build /build/extracted/dependencies/ ./
COPY --from=build /build/extracted/spring-boot-loader/ ./
COPY --from=build /build/extracted/snapshot-dependencies/ ./
COPY --from=build /build/extracted/application/ ./
USER app
EXPOSE 8081
# Container-aware heap: three quarters of the container's memory limit
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
