#!/usr/bin/env python3
"""Sample the public API during deployment and enforce an explicit outage budget."""
import argparse
import json
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

from release import atomic_write


def longest_outage(samples, ended):
    start = None
    longest = 0
    for timestamp, healthy in samples:
        if not healthy and start is None:
            start = timestamp
        elif healthy and start is not None:
            longest = max(longest, timestamp - start)
            start = None
    return max(longest, ended - start) if start is not None else longest


def monitor(url, stop, output, budget):
    samples = []
    started = time.monotonic()
    while True:
        healthy = False
        try:
            with urllib.request.urlopen(url, timeout=3) as response:
                body = json.loads(response.read(1_048_576))
                healthy = response.status == 200 and isinstance(body.get('content'), list)
        except (OSError, ValueError, AttributeError):
            pass
        samples.append((time.monotonic() - started, healthy))
        if stop.exists():
            break
        time.sleep(1)
    duration = time.monotonic() - started
    outage = longest_outage(samples, duration)
    result = {'duration_seconds': round(duration, 2), 'sampled_max_outage_seconds': round(outage, 2),
              'budget_seconds': budget, 'samples': len(samples), 'failed_samples': sum(not ok for _, ok in samples),
              'passed': outage <= budget and samples[-1][1],
              'scope': 'one-second public API sampling; not a high-availability guarantee'}
    atomic_write(output, json.dumps(result) + '\n')
    return 0 if result['passed'] else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--url', required=True)
    parser.add_argument('--stop-file', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--max-outage-seconds', required=True, type=int)
    args = parser.parse_args()
    if not 1 <= args.max_outage_seconds <= 900:
        parser.error('Outage budget must be between 1 and 900 seconds')
    return monitor(args.url, args.stop_file, args.output, args.max_outage_seconds)


if __name__ == '__main__':
    sys.exit(main())
