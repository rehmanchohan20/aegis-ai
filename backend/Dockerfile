FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src src
RUN mvn -B -ntp clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache wget && addgroup -S aegis && adduser -S aegis -G aegis
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
USER aegis
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --retries=10 CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health || exit 1
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
