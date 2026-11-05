"""
DANASEA scripts package root.
Extends package search path so that all categorized modules can be imported directly
as scripts.<module_name> or from their category folders.
"""

from __future__ import annotations

import sys
from pathlib import Path

_ROOT = Path(__file__).resolve().parent

# Extend package search path to all category directories
__path__ = [
    str(_ROOT),
    str(_ROOT / "ai"),
    str(_ROOT / "ops"),
    str(_ROOT / "qa"),
    str(_ROOT / "demo"),
    str(_ROOT / "governance"),
    str(_ROOT / "ci"),
]

for _p in __path__:
    if _p not in sys.path:
        sys.path.insert(0, _p)
