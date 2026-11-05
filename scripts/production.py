#!/usr/bin/env python3
"""Compatibility entry point for deployment wrappers installed before the move."""
from ops.production import main

if __name__ == '__main__':
    raise SystemExit(main())
