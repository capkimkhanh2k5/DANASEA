import sys
from pathlib import Path

_base = Path(__file__).resolve().parents[1]
for _sub in ("ai", "ops", "qa", "demo", "governance", "ci"):
    _p = str(_base / _sub)
    if _p not in sys.path:
        sys.path.insert(0, _p)
