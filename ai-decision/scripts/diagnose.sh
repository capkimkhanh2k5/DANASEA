#!/bin/sh
set -eu
QUYET_SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
QUYET_PYTHON=${QUYET_PYTHON:-$HOME/.cache/danasea/quyet-small/.venv/bin/python}
export HF_HUB_OFFLINE=1
export TRANSFORMERS_OFFLINE=1
export HF_HUB_DISABLE_TELEMETRY=1
export TOKENIZERS_PARALLELISM=false
exec "$QUYET_PYTHON" -u "$QUYET_SCRIPT_DIR/diagnose.py" "$@"
