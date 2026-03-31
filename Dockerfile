# =============================================================
# Stage 1 — Builder
# Full JDK + Maven to compile and package the application.
# Nothing from this stage leaks into the final image.
# =============================================================
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /workspace

# Copy Maven wrapper + pom first so dependency downloads are
# cached as a separate layer (skipped on source-only changes).
COPY .mvn/   .mvn/
COPY mvnw    pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B --no-transfer-progress

# Copy source and build the executable JAR.
# -DskipTests: tests should already pass in CI; saves build time here.
COPY src ./src
RUN ./mvnw clean package -DskipTests -B --no-transfer-progress


# =============================================================
# Stage 2 — Runtime
# Slim JRE-only image (~100 MB vs ~300 MB with full JDK).
# No source code, no build tools, no secrets.
# =============================================================
FROM eclipse-temurin:17-jre-alpine AS runtime

# ── Security: non-root user ───────────────────────────────────
# If the container is compromised, the attacker gets a locked-
# down user with no shell, no home directory, no sudo.
RUN addgroup -S appgroup \
 && adduser  -S appuser -G appgroup -H -s /sbin/nologin

WORKDIR /app

# Copy ONLY the fat JAR produced in the builder stage.
# .env, source code, credentials are never present here.
COPY --from=builder /workspace/target/*.jar app.jar

# Transfer ownership before switching users.
RUN chown appuser:appgroup app.jar

USER appuser

EXPOSE 8080

# -XX:MaxRAMPercentage: JVM uses up to 75% of the cgroup memory
# limit automatically — no hardcoded -Xmx needed.
ENTRYPOINT ["java", \
            "-XX:MaxRAMPercentage=75.0", \
            "-Djava.security.egd=file:/dev/./urandom", \
            "-jar", "app.jar"]
