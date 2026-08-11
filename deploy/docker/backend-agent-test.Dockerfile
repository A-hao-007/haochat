FROM maven:3.9-eclipse-temurin-17

WORKDIR /workspace
COPY haochat-server/pom.xml ./pom.xml
COPY haochat-server/haochat-tools ./haochat-tools
COPY haochat-server/haochat-chat-server ./haochat-chat-server

RUN --mount=type=cache,target=/root/.m2 \
    mvn -pl haochat-chat-server -am -Dtest=AgentToolExecutionServiceTest \
    -DskipTests=false -Dmaven.test.skip=false \
    -Dsurefire.failIfNoSpecifiedTests=false test
