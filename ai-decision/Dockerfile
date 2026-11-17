FROM python:3.11-slim@sha256:07d4824036af339fa1b6ef769cc99d9c10a141769538b52b3b597c84f7a7b535 AS build
WORKDIR /build
COPY config/requirements.lock.txt ./config/requirements.lock.txt
COPY scripts/install_dependencies.py ./install_dependencies.py
RUN python install_dependencies.py config/requirements.lock.txt
RUN python -m pip uninstall -y pip

FROM cgr.dev/chainguard/wolfi-base:latest@sha256:833c48f1faf32c90e6f9501a2410a9aa28ceb2c594102edd4470053c30efa089 AS rootfs


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
