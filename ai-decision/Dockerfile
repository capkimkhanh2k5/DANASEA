FROM python:3.11-slim@sha256:ae952e0a57138a4317b9b61caaa19a8217c2f356803f8d7e63aad08769950937 AS build
WORKDIR /build
COPY config/requirements.lock.txt ./config/requirements.lock.txt
COPY scripts/install_dependencies.py ./install_dependencies.py
RUN python install_dependencies.py config/requirements.lock.txt
# Torch 2.15 CPU ships libgfortran, whose libquadmath dependency is provided by Debian.
RUN apt-get update && apt-get install -y --no-install-recommends libquadmath0 && \
    rm -rf /var/lib/apt/lists/*
COPY scripts/build_runtime.py ./build_runtime.py
RUN python build_runtime.py

FROM gcr.io/distroless/cc-debian13:nonroot@sha256:08476c244888bfb425bae1ae4dd7c17f093e972aab8426883d97f3c6f280f7c1
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
