"""Container entrypoint without a shell; preserve configurable host and port."""
import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import uvicorn

uvicorn.run('app.quyet_service:app', host=os.getenv('QUYET_HOST', '0.0.0.0'),
            port=int(os.getenv('QUYET_PORT', '8091')), workers=1, access_log=False)
