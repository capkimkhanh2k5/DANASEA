FROM python:3.11-slim
WORKDIR /app
ENV QUYET_PYTHON=python PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 HF_HUB_OFFLINE=1 TRANSFORMERS_OFFLINE=1 TOKENIZERS_PARALLELISM=false HF_HUB_DISABLE_TELEMETRY=1
COPY config/requirements.lock.txt ./config/requirements.lock.txt
RUN pip install --no-cache-dir -r config/requirements.lock.txt && useradd --uid 10001 --create-home worker
COPY app/ ./app/
COPY config/__init__.py config/rubrics.py ./config/
COPY scripts/run.sh ./scripts/run.sh
USER worker
EXPOSE 8091
CMD ["sh", "/app/scripts/run.sh"]
