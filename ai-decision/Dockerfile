FROM python:3.11-slim@sha256:ae952e0a57138a4317b9b61caaa19a8217c2f356803f8d7e63aad08769950937 AS build
WORKDIR /build
COPY config/requirements.lock.txt ./config/requirements.lock.txt
RUN pip install --no-cache-dir --index-url https://download.pytorch.org/whl/cpu torch==2.14.1 && \
    pip install --no-cache-dir -r config/requirements.lock.txt
COPY scripts/build_runtime.py ./build_runtime.py
RUN python build_runtime.py

FROM gcr.io/distroless/cc-debian13:nonroot@sha256:3ee00291cf386ec52a552e6a858d555debd74776eb4bfa53fa3df2cd6677515e
COPY --from=build /runtime/ /
ENV HOME=/tmp USER=worker PYTHONUTF8=1 PATH=/usr/local/bin:/usr/bin:/bin LD_LIBRARY_PATH=/usr/local/lib PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 HF_HUB_OFFLINE=1 TRANSFORMERS_OFFLINE=1 TOKENIZERS_PARALLELISM=false HF_HUB_DISABLE_TELEMETRY=1
WORKDIR /app
COPY app/ ./app/
COPY config/__init__.py config/rubrics.py ./config/
COPY scripts/container_entrypoint.py ./scripts/container_entrypoint.py
USER 10001:10001
EXPOSE 8091
ENTRYPOINT []
CMD ["/usr/local/bin/python", "/app/scripts/container_entrypoint.py"]
