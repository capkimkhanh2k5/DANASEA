"""Evaluate server-owned DANASEA Quyet rubrics on calibration and holdout suites."""

from __future__ import annotations

import argparse
import json
import math
import os
import statistics
import sys
import time
from collections import Counter, defaultdict
from datetime import datetime
from pathlib import Path
from typing import Any
from zoneinfo import ZoneInfo

AI_DECISION_DIR = Path(__file__).resolve().parents[1]
if str(AI_DECISION_DIR) not in sys.path:
    sys.path.insert(0, str(AI_DECISION_DIR))

import quyet  # noqa: E402
import torch  # noqa: E402

from app.quyet_service import DEFAULT_MODEL, REVISION, validate_response  # noqa: E402
from config.rubrics import RUBRICS, VERSION  # noqa: E402

DEFAULT_FIXTURE = (
    AI_DECISION_DIR / "tests/fixtures/quyet-small/calibration_holdout.json"
)
INTENT_LABELS = list(RUBRICS["intent"]["intent"]["criteria"].keys())
MODERATION_FIELDS = ("external_payment", "spam", "abuse")
CONFIDENCE_THRESHOLDS = (0.45, 0.50, 0.55, 0.60, 0.65, 0.70, 0.75, 0.80)


def synchronize(device: str) -> None:
    if device == "mps":
        torch.mps.synchronize()


def quantile(values: list[float], fraction: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = max(0, math.ceil(len(ordered) * fraction) - 1)
    return round(ordered[index], 4)


def latency_stats(values: list[float]) -> dict[str, Any]:
    if not values:
        return {"count": 0, "mean_ms": 0.0, "p50_ms": 0.0, "p95_ms": 0.0}
    return {
        "count": len(values),
        "mean_ms": round(statistics.mean(values), 2),
        "p50_ms": round(statistics.median(values), 2),
        "p95_ms": round(quantile(values, 0.95), 2),
    }


def selected_value(answer: dict[str, Any], noul_threshold: float = 0.5) -> str | bool:
    if answer["type"] == "choice":
        return str(answer["choice"])
    if answer["type"] == "noul":
        return bool(answer["noul"] >= noul_threshold)
    probabilities = answer["probabilities"]
    return str(max(probabilities, key=probabilities.get))


def answer_confidence(answer: dict[str, Any]) -> float:
    if "confidence" in answer and answer["confidence"] is not None:
        return float(answer["confidence"])
    if answer["type"] == "noul":
        p = float(answer["noul"])
        return max(p, 1.0 - p)
    return float(max(answer["probabilities"].values()))


def top2_margin(answer: dict[str, Any]) -> float:
    if answer["type"] == "noul":
        p = float(answer["noul"])
        return round(abs(2.0 * p - 1.0), 4)
    ordered = sorted(answer["probabilities"].values(), reverse=True)
    if len(ordered) < 2:
        return round(float(ordered[0]), 4)
    return round(float(ordered[0] - ordered[1]), 4)


def distribution_summary(values: list[float]) -> dict[str, Any]:
    if not values:
        return {"count": 0, "mean": None, "median": None, "min": None, "p10": None, "p90": None}
    return {
        "count": len(values),
        "mean": round(statistics.mean(values), 4),
        "median": round(statistics.median(values), 4),
        "min": round(min(values), 4),
        "p10": quantile(values, 0.10),
        "p90": quantile(values, 0.90),
    }


def evaluate_split(
    model: Any,
    cases: list[dict[str, Any]],
    device: str,
    noul_threshold: float,
) -> dict[str, Any]:
    rows: list[dict[str, Any]] = []
    task_stats: dict[str, dict[str, int]] = defaultdict(
        lambda: {"rows": 0, "rows_matched": 0, "labels": 0, "labels_matched": 0}
    )
    slice_stats: dict[str, dict[str, int]] = defaultdict(
        lambda: {"rows": 0, "rows_matched": 0, "labels": 0, "labels_matched": 0}
    )
    task_latencies: dict[str, list[float]] = defaultdict(list)

    intent_confusion: dict[str, dict[str, int]] = {
        exp: {pred: 0 for pred in INTENT_LABELS} for exp in INTENT_LABELS
    }
    mod_counts: dict[str, dict[str, int]] = {
        field: {"tp": 0, "fp": 0, "tn": 0, "fn": 0} for field in MODERATION_FIELDS
    }

    all_conf_correct: list[float] = []
    all_conf_wrong: list[float] = []
    intent_records: list[dict[str, Any]] = []

    for case in cases:
        task = case["task"]
        questions = RUBRICS[task]
        synchronize(device)
        started = time.perf_counter()
        response = model.predict(case["state"], questions, strict=True)
        synchronize(device)
        latency_ms = round((time.perf_counter() - started) * 1000.0, 2)
        validate_response(response, questions)

        answers = response["answers"]
        actual = {key: selected_value(ans, noul_threshold) for key, ans in answers.items()}
        expected = case["expected"]

        judgments = {}
        for key, exp_val in expected.items():
            act_val = actual[key]
            match = act_val == exp_val
            conf = answer_confidence(answers[key])
            judgments[key] = {
                "expected": exp_val,
                "actual": act_val,
                "match": match,
                "confidence": round(conf, 4),
                "margin": top2_margin(answers[key]),
            }
            if match:
                all_conf_correct.append(conf)
            else:
                all_conf_wrong.append(conf)

            if task == "intent" and key == "intent":
                if exp_val in intent_confusion and act_val in intent_confusion[exp_val]:
                    intent_confusion[exp_val][act_val] += 1
                intent_records.append(
                    {
                        "id": case["id"],
                        "expected": exp_val,
                        "actual": act_val,
                        "match": match,
                        "confidence": round(conf, 4),
                        "margin": top2_margin(answers[key]),
                    }
                )
            if task == "moderation" and key in mod_counts:
                if exp_val is True and act_val is True:
                    mod_counts[key]["tp"] += 1
                elif exp_val is False and act_val is True:
                    mod_counts[key]["fp"] += 1
                elif exp_val is False and act_val is False:
                    mod_counts[key]["tn"] += 1
                else:
                    mod_counts[key]["fn"] += 1

        row_match = all(item["match"] for item in judgments.values())
        lbl_total = len(judgments)
        lbl_match = sum(int(item["match"]) for item in judgments.values())

        for bucket in (task_stats[task], slice_stats[case.get("slice", "default")]):
            bucket["rows"] += 1
            bucket["rows_matched"] += int(row_match)
            bucket["labels"] += lbl_total
            bucket["labels_matched"] += lbl_match
        task_latencies[task].append(latency_ms)

        rows.append(
            {
                "id": case["id"],
                "task": task,
                "slice": case.get("slice", "default"),
                "legacy_ab_29": bool(case.get("legacy_ab_29", False)),
                "state": case["state"],
                "expected": expected,
                "legacy_expected": case.get("legacy_expected"),
                "actual": actual,
                "row_match": row_match,
                "latency_ms": latency_ms,
                "judgments": judgments,
                "answers": answers,
            }
        )

    moderation_metrics: dict[str, dict[str, Any]] = {}
    for field, c in mod_counts.items():
        tp, fp, tn, fn = c["tp"], c["fp"], c["tn"], c["fn"]
        total = tp + fp + tn + fn
        prec = round(tp / (tp + fp), 4) if (tp + fp) else None
        rec = round(tp / (tp + fn), 4) if (tp + fn) else None
        f1 = (
            round(2 * prec * rec / (prec + rec), 4)
            if (prec is not None and rec is not None and (prec + rec) > 0)
            else None
        )
        acc = round((tp + tn) / total, 4) if total else None
        moderation_metrics[field] = {**c, "total": total, "accuracy": acc, "precision": prec, "recall": rec, "f1": f1}

    buckets = [
        ("<0.50", lambda x: x < 0.50),
        ("0.50-0.65", lambda x: 0.50 <= x < 0.65),
        ("0.65-0.80", lambda x: 0.65 <= x < 0.80),
        (">=0.80", lambda x: x >= 0.80),
    ]
    intent_buckets = {}
    for name, pred in buckets:
        subset = [r for r in intent_records if pred(r["confidence"])]
        intent_buckets[name] = {
            "count": len(subset),
            "correct": sum(int(r["match"]) for r in subset),
            "accuracy": round(sum(int(r["match"]) for r in subset) / len(subset), 4) if subset else None,
        }

    total_errors = sum(int(not r["match"]) for r in intent_records)
    threshold_sweep = []
    for tau in CONFIDENCE_THRESHOLDS:
        accepted = [r for r in intent_records if r["confidence"] >= tau]
        clarified = [r for r in intent_records if r["confidence"] < tau]
        threshold_sweep.append(
            {
                "threshold": tau,
                "accepted_count": len(accepted),
                "coverage": round(len(accepted) / len(intent_records), 4) if intent_records else 0.0,
                "accepted_accuracy": round(sum(int(r["match"]) for r in accepted) / len(accepted), 4)
                if accepted
                else None,
                "clarified_count": len(clarified),
                "errors_captured_in_clarified": sum(int(not r["match"]) for r in clarified),
                "error_capture_rate": round(
                    sum(int(not r["match"]) for r in clarified) / total_errors, 4
                )
                if total_errors
                else None,
            }
        )

    total_rows = len(rows)
    matched_rows = sum(int(r["row_match"]) for r in rows)
    total_labels = sum(len(r["judgments"]) for r in rows)
    matched_labels = sum(sum(int(j["match"]) for j in r["judgments"].values()) for r in rows)

    all_latencies = [r["latency_ms"] for r in rows]
    non_zero_confusion = {
        exp: {pred: cnt for pred, cnt in preds.items() if cnt > 0}
        for exp, preds in intent_confusion.items()
        if any(preds.values())
    }

    return {
        "summary": {
            "rows": total_rows,
            "rows_matched": matched_rows,
            "row_accuracy": round(matched_rows / total_rows, 4) if total_rows else 0.0,
            "labels": total_labels,
            "labels_matched": matched_labels,
            "label_accuracy": round(matched_labels / total_labels, 4) if total_labels else 0.0,
        },
        "by_task": {
            t: {
                **s,
                "row_accuracy": round(s["rows_matched"] / s["rows"], 4),
                "label_accuracy": round(s["labels_matched"] / s["labels"], 4),
            }
            for t, s in sorted(task_stats.items())
        },
        "by_slice": {
            sl: {
                **s,
                "row_accuracy": round(s["rows_matched"] / s["rows"], 4),
                "label_accuracy": round(s["labels_matched"] / s["labels"], 4),
            }
            for sl, s in sorted(slice_stats.items())
        },
        "intent_confusion_matrix": non_zero_confusion,
        "moderation_metrics": moderation_metrics,
        "confidence_calibration": {
            "all_labels": {
                "correct": distribution_summary(all_conf_correct),
                "incorrect": distribution_summary(all_conf_wrong),
            },
            "intent": {
                "correct_confidence": distribution_summary(
                    [r["confidence"] for r in intent_records if r["match"]]
                ),
                "incorrect_confidence": distribution_summary(
                    [r["confidence"] for r in intent_records if not r["match"]]
                ),
                "correct_margin": distribution_summary(
                    [r["margin"] for r in intent_records if r["match"]]
                ),
                "incorrect_margin": distribution_summary(
                    [r["margin"] for r in intent_records if not r["match"]]
                ),
                "buckets": intent_buckets,
                "threshold_sweep": threshold_sweep,
            },
        },
        "latency_ms": {
            "overall": latency_stats(all_latencies),
            "by_task": {t: latency_stats(vals) for t, vals in sorted(task_latencies.items())},
        },
        "misses": [
            {
                "id": r["id"],
                "task": r["task"],
                "slice": r["slice"],
                "diffs": {
                    k: {"expected": j["expected"], "actual": j["actual"], "confidence": j["confidence"]}
                    for k, j in r["judgments"].items()
                    if not j["match"]
                },
            }
            for r in rows
            if not r["row_match"]
        ],
        "rows": rows,
    }


def compute_legacy_ab_report(calibration_rows: list[dict[str, Any]], ref: dict[str, Any]) -> dict[str, Any]:
    ab_rows = [r for r in calibration_rows if r.get("legacy_ab_29")]
    legacy_lbl_ok = 0
    legacy_lbl_tot = 0
    legacy_row_ok = 0
    unified_lbl_ok = 0
    unified_lbl_tot = 0
    unified_row_ok = 0
    legacy_misses = []
    unified_misses = []

    for r in ab_rows:
        leg_exp = r["legacy_expected"]
        uni_exp = {k: r["expected"][k] for k in leg_exp}
        act = r["actual"]

        leg_row = all(act[k] == v for k, v in leg_exp.items())
        legacy_row_ok += int(leg_row)
        for k, v in leg_exp.items():
            legacy_lbl_tot += 1
            legacy_lbl_ok += int(act[k] == v)
        if not leg_row:
            legacy_misses.append(
                {
                    "id": r["id"],
                    "diffs": {
                        k: {"expected": v, "actual": act[k]}
                        for k, v in leg_exp.items()
                        if act[k] != v
                    },
                }
            )

        uni_row = all(act[k] == v for k, v in uni_exp.items())
        unified_row_ok += int(uni_row)
        for k, v in uni_exp.items():
            unified_lbl_tot += 1
            unified_lbl_ok += int(act[k] == v)
        if not uni_row:
            unified_misses.append(
                {
                    "id": r["id"],
                    "diffs": {
                        k: {"expected": v, "actual": act[k]}
                        for k, v in uni_exp.items()
                        if act[k] != v
                    },
                }
            )

    baseline_passing_ids = {
        "intent-01",
        "intent-02",
        "intent-03",
        "intent-05",
        "intent-06",
        "intent-07",
        "intent-08",
        "intent-09",
        "intent-10",
        "intent-11",
        "intent-12",
        "intent-16",
        "moderation-04",
        "moderation-05",
    }
    row_by_id = {r["id"]: r for r in ab_rows}
    baseline_regressions = [
        cid
        for cid in sorted(baseline_passing_ids)
        if cid in row_by_id
        and not all(row_by_id[cid]["actual"][k] == v for k, v in row_by_id[cid]["legacy_expected"].items())
    ]

    return {
        "inputs": len(ab_rows),
        "labels": legacy_lbl_tot,
        "historical_reference": ref,
        "legacy_mapped": {
            "rows_matched": legacy_row_ok,
            "labels_matched": legacy_lbl_ok,
            "misses": legacy_misses,
        },
        "unified_taxonomy_37_labels": {
            "rows_matched": unified_row_ok,
            "labels_matched": unified_lbl_ok,
            "misses": unified_misses,
        },
        "baseline_passing_row_regressions": baseline_regressions,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fixture", type=Path, default=DEFAULT_FIXTURE)
    parser.add_argument("--split", choices=["calibration", "holdout", "both"], default="both")
    parser.add_argument("--device", choices=["cpu", "mps"], default="cpu")
    parser.add_argument("--threads", type=int, default=4)
    parser.add_argument(
        "--model-dir",
        type=Path,
        default=Path(os.getenv("QUYET_MODEL_DIR", str(DEFAULT_MODEL))),
    )
    parser.add_argument("--noul-threshold", type=float, default=0.5)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--brief", action="store_true")
    args = parser.parse_args()

    os.environ["HF_HUB_OFFLINE"] = "1"
    os.environ["TRANSFORMERS_OFFLINE"] = "1"
    os.environ["TOKENIZERS_PARALLELISM"] = "false"

    fixture = json.loads(args.fixture.read_text(encoding="utf-8"))
    torch.set_num_threads(args.threads)
    torch.set_num_interop_threads(1)
    torch.manual_seed(0)

    t0 = time.perf_counter()
    model = quyet.load(str(args.model_dir), device=args.device)
    synchronize(args.device)
    load_seconds = round(time.perf_counter() - t0, 3)

    # Warmup on first calibration case
    warmup_case = fixture["calibration"][0]
    for _ in range(2):
        model.predict(warmup_case["state"], RUBRICS[warmup_case["task"]], strict=True)
        synchronize(args.device)

    splits_to_run = ["calibration", "holdout"] if args.split == "both" else [args.split]
    results: dict[str, Any] = {}
    for split_name in splits_to_run:
        results[split_name] = evaluate_split(
            model, fixture[split_name], args.device, args.noul_threshold
        )

    report: dict[str, Any] = {
        "metadata": {
            "rubric_version": VERSION,
            "model_id": "chinhnc/Quyet-1.0-Small",
            "revision": REVISION,
            "model_dir": str(args.model_dir),
            "device": args.device,
            "cpu_threads": args.threads,
            "noul_threshold": args.noul_threshold,
            "load_seconds": load_seconds,
            "recorded_at": datetime.now(ZoneInfo("Asia/Ho_Chi_Minh")).isoformat(),
        },
        "splits": results,
    }
    if "calibration" in results:
        report["legacy_ab_29"] = compute_legacy_ab_report(
            results["calibration"]["rows"],
            fixture["taxonomy"]["legacy_ab_baseline_reference"],
        )

    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(
            json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )

    if args.brief:
        compact = {"metadata": report["metadata"]}
        if "legacy_ab_29" in report:
            compact["legacy_ab_29"] = report["legacy_ab_29"]
        compact["splits"] = {
            name: {
                k: v
                for k, v in data.items()
                if k != "rows"
            }
            for name, data in results.items()
        }
        print(json.dumps(compact, ensure_ascii=False, indent=2))
    else:
        print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
