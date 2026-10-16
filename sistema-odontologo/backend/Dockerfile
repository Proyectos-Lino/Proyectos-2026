FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
ENV MAVEN_OPTS="-Xmx512m -XX:+UseSerialGC"

COPY pom.xml ./
RUN mvn -B -ntp -Dmaven.test.skip=true dependency:go-offline
COPY src/main ./src/main
RUN mvn -B -ntp -Dmaven.test.skip=true clean package \
    && cp target/odontocare-*.jar /build/application.jar

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
RUN apk add --no-cache fontconfig ttf-dejavu \
    && addgroup -S odontocare && adduser -S -G odontocare odontocare
COPY --from=build --chown=odontocare:odontocare /build/application.jar ./application.jar

ENV SPRING_PROFILES_ACTIVE=prod \
    PORT=8080 \
    JAVA_TOOL_OPTIONS="-Xms64m -Xmx384m -XX:+UseSerialGC -XX:ActiveProcessorCount=2 -Djava.awt.headless=true" \
    SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=4 \
    SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=0 \
    SERVER_TOMCAT_THREADS_MAX=30 \
    SERVER_TOMCAT_THREADS_MIN_SPARE=2

USER odontocare
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
