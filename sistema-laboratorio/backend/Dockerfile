FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
ENV MAVEN_OPTS="-Xmx512m -XX:+UseSerialGC"

COPY pom.xml ./
COPY src/main ./src/main
RUN mvn -B -ntp -Dmaven.test.skip=true package \
    && cp target/labcore-api-*.jar /build/application.jar

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S labcore && adduser -S -G labcore labcore
COPY --from=build --chown=labcore:labcore /build/application.jar ./application.jar

ENV SPRING_PROFILES_ACTIVE=cloud \
    SERVER_PORT=8080 \
    JAVA_TOOL_OPTIONS="-Xms64m -Xmx320m -XX:+UseSerialGC -XX:ActiveProcessorCount=2" \
    SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=4 \
    SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=0 \
    SPRING_DATASOURCE_HIKARI_IDLE_TIMEOUT=60000 \
    SERVER_TOMCAT_THREADS_MAX=30 \
    SERVER_TOMCAT_THREADS_MIN_SPARE=2

USER labcore
EXPOSE 8080
HEALTHCHECK --interval=60s --timeout=5s --start-period=90s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
