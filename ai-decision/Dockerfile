FROM python:3.11-slim@sha256:ae952e0a57138a4317b9b61caaa19a8217c2f356803f8d7e63aad08769950937 AS build
WORKDIR /build
COPY config/requirements.lock.txt ./config/requirements.lock.txt
COPY scripts/install_dependencies.py ./install_dependencies.py
RUN python install_dependencies.py config/requirements.lock.txt
RUN python -m pip uninstall -y pip

FROM cgr.dev/chainguard/wolfi-base:latest@sha256:8acb3a7694eda954b3ad4256aec3291bdd92715d7a8c030abd1a21546337b4e4 AS rootfs


RUN apk --root /runtime --initdb --keys-dir /etc/apk/keys \
        --repositories-file /dev/null --repository https://packages.wolfi.dev/os \
        add --no-cache --no-scripts \
        ca-certificates-bundle python-3.11-base=3.11.18-r1 zlib=1.3.2.1_rc20260921-r2 \
        libgcc libstdc++ libgomp libquadmath && \
    chroot /runtime /usr/bin/ldconfig && \
    ln -s python3.11 /runtime/usr/bin/python && \
    rm -r /runtime/usr/lib/python3.11/ensurepip /runtime/usr/share/python-wheels

FROM scratch
COPY --from=rootfs /runtime/ /
COPY --from=build /usr/local/lib/python3.11/site-packages/ /usr/lib/python3.11/site-packages/
ENV HOME=/tmp USER=worker PYTHONUTF8=1 PATH=/usr/bin:/bin PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 HF_HUB_OFFLINE=1 TRANSFORMERS_OFFLINE=1 TOKENIZERS_PARALLELISM=false HF_HUB_DISABLE_TELEMETRY=1
WORKDIR /app
COPY app/ ./app/
COPY config/__init__.py config/rubrics.py ./config/
COPY scripts/container_entrypoint.py ./scripts/container_entrypoint.py
USER 10001:10001
RUN --mount=type=bind,source=scripts/check_runtime.py,target=/check_runtime.py ["/usr/bin/python", "/check_runtime.py"]
EXPOSE 8091
CMD ["/usr/bin/python", "/app/scripts/container_entrypoint.py"]
