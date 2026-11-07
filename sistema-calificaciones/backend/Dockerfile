# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
ENV MAVEN_OPTS="-Xmx512m -XX:+UseSerialGC -XX:ActiveProcessorCount=2"

COPY pom.xml ./
COPY src/main ./src/main
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -Dmaven.test.skip=true package \
    && cp target/lector-dbf-*.jar /build/application.jar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache fontconfig ttf-dejavu \
    && addgroup -S califica \
    && adduser -S -G califica califica
COPY --from=build --chown=califica:califica /build/application.jar ./application.jar

ENV SPRING_PROFILES_ACTIVE=docker \
    JAVA_TOOL_OPTIONS="-Xms64m -Xmx384m -XX:+UseSerialGC -XX:ActiveProcessorCount=2 -Djava.awt.headless=true"

USER califica
EXPOSE 8080
HEALTHCHECK --interval=45s --timeout=5s --start-period=120s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
