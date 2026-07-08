# 本地快速迭代用：直接打包 perf-local 编译流程产出的 jar，跳过容器内 Maven 全量构建
# （生产部署仍使用 deploy/docker/backend.Dockerfile 的多阶段构建）
FROM eclipse-temurin:17-jre

WORKDIR /app
COPY perf-local/app.jar /app/app.jar
EXPOSE 8080 8090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
