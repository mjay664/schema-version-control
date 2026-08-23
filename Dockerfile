# ---- build ------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Resolve dependencies against the POM alone so this layer survives source edits.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- runtime ----------------------------------------------------------------
# jammy rather than alpine: the alpine JRE tag is published for amd64 only, so
# an alpine base builds on Render but not on an arm64 laptop. This one is
# multi-arch, which keeps local and deployed images the same.
FROM eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /app

# curl backs the container health check; the JRE image ships without it.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app --home-dir /app app

COPY --from=build /build/target/*.jar app.jar
RUN chown -R app:app /app
USER app

# Render injects PORT; 8080 is the local default.
ENV PORT=8080
EXPOSE 8080

# Respect the container's memory limit rather than the host's. SerialGC keeps
# the footprint down on Render's 512MB free instances.
# TieredStopAtLevel=1 skips C2 compilation for faster startup at the cost of
# peak throughput (acceptable for a low-traffic API on a free tier).
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Dspring.jmx.enabled=false"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
