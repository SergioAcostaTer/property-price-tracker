# ====== Build stage ======
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Ensure wrapper is executable even if checked out on Windows
# (no dos2unix needed)
SHELL ["/bin/bash", "-c"]

# Copy only files that are guaranteed to exist
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle

# Make wrapper executable and normalize line endings if needed
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

# Cache Gradle deps (mount caches for faster incremental builds)
# If your project has more modules, this still works.
COPY src ./src
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon clean bootJar

# ====== Runtime stage ======
FROM eclipse-temurin:21-jre
WORKDIR /opt/app
ENV TZ=UTC

# Non-root user
RUN useradd -ms /bin/bash appuser
USER appuser

# Copy the built jar (any name)
COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java","-jar","/opt/app/app.jar"]
