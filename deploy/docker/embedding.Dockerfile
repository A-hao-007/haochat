FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

RUN pip install --no-cache-dir \
    --index-url https://pypi.tuna.tsinghua.edu.cn/simple \
    fastembed==0.8.0 \
    faster-whisper==1.2.1

WORKDIR /app
COPY deploy/embedding/server.py /app/server.py

EXPOSE 8000
CMD ["python", "/app/server.py"]
