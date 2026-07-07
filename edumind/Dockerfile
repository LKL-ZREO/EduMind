# === 构建阶段 ===
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

# 先复制 Maven 配置和依赖描述，利用缓存
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw dependency:go-offline -B

# 复制源码并打包
COPY src src
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw package -DskipTests -B

# === 运行阶段 ===
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 安装时区数据（GMT+8）
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

# 创建上传目录
RUN mkdir -p /root/.homework-grader/uploads

COPY --from=builder /build/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
    "-XX:+UseZGC", \
    "-XX:MaxRAMPercentage=75.0", \
    "-jar", "app.jar"]
