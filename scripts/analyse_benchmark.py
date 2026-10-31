#!/usr/bin/env python3
"""Summarise docs/evidence/benchmark/raw_*.csv into summary.csv, summary.md and a chart.

Usage: python3 scripts/analyse_benchmark.py
"""
import csv
import glob
import os
import statistics
from collections import defaultdict

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
EVIDENCE = os.path.join(HERE, "..", "docs", "evidence", "benchmark")


def percentile(sorted_values, p):
    return sorted_values[min(len(sorted_values) - 1, int(len(sorted_values) * p))]


def describe(values_ms):
    s = sorted(values_ms)
    return {
        "n": len(s),
        "median": statistics.median(s),
        "p90": percentile(s, 0.9),
        "min": s[0],
        "max": s[-1],
    }


samples = defaultdict(list)          # (metric, size, strategy) -> ms, pooled over runs
per_run = defaultdict(list)          # (metric, size, strategy, label) -> ms
for path in sorted(glob.glob(os.path.join(EVIDENCE, "raw_*.csv"))):
    with open(path) as f:
        for row in csv.DictReader(f):
            ms = int(row["nanos"]) / 1e6
            key = (row["metric"], int(row["size"]), row["strategy"])
            samples[key].append(ms)
            per_run[key + (row["label"],)].append(ms)

labels = sorted({k[3] for k in per_run})
keys = sorted(samples, key=lambda k: (k[0], k[1], k[2]))

with open(os.path.join(EVIDENCE, "summary.csv"), "w", newline="") as f:
    w = csv.writer(f)
    w.writerow(["metric", "size", "strategy", "n", "median_ms", "p90_ms", "min_ms", "max_ms"]
               + [f"median_{l}_ms" for l in labels])
    for k in keys:
        d = describe(samples[k])
        w.writerow([*k, d["n"], *(round(d[x], 2) for x in ("median", "p90", "min", "max"))]
                   + [round(statistics.median(per_run[k + (l,)]), 2) for l in labels])

lines = ["| Metric | Sessions | Strategy | n | Median (ms) | p90 (ms) | Min | Max | "
         + " | ".join(f"Median {l}" for l in labels) + " | B/A median |",
         "|---|---|---|---|---|---|---|---|" + "---|" * len(labels) + "---|"]
for k in keys:
    d = describe(samples[k])
    ratio = ""
    if k[2] == "B":
        ratio = f"{d['median'] / statistics.median(samples[(k[0], k[1], 'A')]):.1f}x"
    lines.append(
        f"| {k[0]} | {k[1]:,} | {k[2]} | {d['n']} | {d['median']:.2f} | {d['p90']:.2f} | "
        f"{d['min']:.2f} | {d['max']:.2f} | "
        + " | ".join(f"{statistics.median(per_run[k + (l,)]):.2f}" for l in labels)
        + f" | {ratio} |"
    )
with open(os.path.join(EVIDENCE, "summary.md"), "w") as f:
    f.write("\n".join(lines) + "\n")
print("\n".join(lines))

sizes = sorted({k[1] for k in keys if k[0] == "query"})
fig, ax = plt.subplots(figsize=(6.5, 4))
for strategy, name in (("A", "A: SQL aggregation"), ("B", "B: @Relation + Kotlin")):
    med = [statistics.median(samples[("query", s, strategy)]) for s in sizes]
    p90 = [percentile(sorted(samples[("query", s, strategy)]), 0.9) for s in sizes]
    ax.plot(sizes, med, marker="o", label=f"{name} (median)")
    ax.fill_between(sizes, med, p90, alpha=0.15)
ax.axhline(16.7, color="grey", linestyle="--", linewidth=1)
ax.text(sizes[0], 18.5, "one 60 Hz frame (16.7 ms)", color="grey", fontsize=8)
ax.set_xscale("log")
ax.set_yscale("log")
ax.set_xticks(sizes)
ax.set_xticklabels([f"{s:,}" for s in sizes])
ax.set_xlabel("Study sessions in database (log scale)")
ax.set_ylabel("Query latency, ms (log scale)")
ax.set_title(f"Dashboard unit progress query (n={len(labels)} runs x 30, shaded to p90)")
ax.legend()
fig.tight_layout()
fig.savefig(os.path.join(EVIDENCE, "latency_vs_size.png"), dpi=160)
print("wrote summary.csv, summary.md, latency_vs_size.png")
