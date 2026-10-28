FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY pom.xml ./
COPY .mvn .mvn
COPY mvnw ./
RUN --mount=type=cache,target=/root/.m2 \
    sh ./mvnw --batch-mode --no-transfer-progress dependency:go-offline

COPY src ./src

# Тесты и проверку форматирования pipeline уже прогнал отдельными стадиями,
# повторять их внутри сборки образа — удваивать время деплоя. Поэтому CI
# собирает с SKIP_IMAGE_CHECKS=true, а локальная сборка
# (`docker compose up --build`) по умолчанию остаётся с полной проверкой.
ARG SKIP_IMAGE_CHECKS=false
RUN --mount=type=cache,target=/root/.m2 \
    sh ./mvnw --batch-mode --no-transfer-progress verify \
        -DskipTests=${SKIP_IMAGE_CHECKS} \
        -Dspotless.check.skip=${SKIP_IMAGE_CHECKS}

FROM eclipse-temurin:21-jre-jammy

RUN useradd --system --create-home --uid 10001 workflow
WORKDIR /app
COPY --from=build /workspace/target/workflow-crm-backend-*.jar app.jar
USER workflow

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]