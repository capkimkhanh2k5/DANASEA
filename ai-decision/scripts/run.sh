#!/bin/sh
set -eu
QUYET_SERVICE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
QUYET_PYTHON=${QUYET_PYTHON:-$HOME/.cache/danasea/quyet-small/.venv/bin/python}
: "${AI_QUYET_API_KEY:?Set the same AI_QUYET_API_KEY in the worker and backend}"
export HF_HUB_OFFLINE=1
export TRANSFORMERS_OFFLINE=1
export TOKENIZERS_PARALLELISM=false
cd "$QUYET_SERVICE_DIR"
exec "$QUYET_PYTHON" -m uvicorn app.quyet_service:app --host "${QUYET_HOST:-127.0.0.1}" --port "${QUYET_PORT:-8091}" --workers 1 --no-access-log
