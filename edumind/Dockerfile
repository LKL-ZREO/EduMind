# === 构建阶段 ===
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

ARG MAVEN_MIRROR_URL=https://repo.maven.apache.org/maven2
ENV MAVEN_MIRROR_URL=${MAVEN_MIRROR_URL}

# 先复制 Maven 配置；BuildKit 的 .m2 cache 会跨源码变更复用依赖
COPY mvnw pom.xml ./
COPY .mvn .mvn

# 复制源码并打包
COPY src src
RUN --mount=type=cache,target=/root/.m2 \
    set -eu; \
    chmod 0755 mvnw; \
    attempt=1; \
    while ! ./mvnw --settings .mvn/settings-docker.xml \
        -Daether.transport.http.retryHandler.count=5 \
        -Daether.transport.http.retryHandler.interval=2000 \
        package -DskipTests -B; do \
      if [ "$attempt" -ge 3 ]; then \
        echo "Maven build failed after ${attempt} attempts" >&2; \
        exit 1; \
      fi; \
      find /root/.m2 -name '*.lastUpdated' -delete; \
      attempt=$((attempt + 1)); \
      echo "Maven download failed; retrying build (attempt ${attempt}/3)" >&2; \
      sleep $((attempt * 5)); \
    done

# === 运行阶段 ===
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 安装时区数据并创建非 root 运行用户
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone && \
    addgroup -S edumind && \
    adduser -S -G edumind -h /home/edumind edumind && \
    mkdir -p \
      /home/edumind/.homework-grader/uploads \
      /home/edumind/.djl \
      /home/edumind/.edumind/logs \
      /app/models/reranker && \
    chown -R edumind:edumind /home/edumind /app/models

COPY --from=builder /build/target/*.jar app.jar

ENV HOME=/home/edumind

USER edumind

EXPOSE 8080

ENTRYPOINT ["java", \
    "-XX:+UseZGC", \
    "-XX:MaxRAMPercentage=75.0", \
    "-jar", "app.jar"]
