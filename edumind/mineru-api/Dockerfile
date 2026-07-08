FROM python:3.11-slim

WORKDIR /app

# 系统依赖 (PaddleOCR 需要 libgomp1, OpenCV 需要 libgl1)
RUN apt-get update && apt-get install -y --no-install-recommends \
    libgomp1 \
    libgl1 \
    libglib2.0-0 \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Python 依赖
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY main.py .

EXPOSE 18790

# 模型目录挂载点（通过 docker-compose volume 持久化）
VOLUME ["/root/.cache/modelscope"]

# 启动服务，首次请求时自动下载模型（约 3GB）
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "18790", "--workers", "2"]
