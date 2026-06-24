FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /workspace
COPY haochat-server/pom.xml ./pom.xml
COPY haochat-server/haochat-tools ./haochat-tools
COPY haochat-server/haochat-chat-server ./haochat-chat-server
RUN mvn -pl haochat-chat-server -am package -DskipTests

FROM eclipse-temurin:17-jre

WORKDIR /app
COPY --from=build /workspace/haochat-chat-server/target/*.jar /app/app.jar
EXPOSE 8080 8090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
