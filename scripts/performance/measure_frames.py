"""Repeat safe navigation gestures on an already-open page and save Android frame stats.

Requires Python 3 and adb, no third-party packages. Coordinates are physical pixels;
verify them on the device before running. Avoid game/app launch controls.
"""
import argparse
import json
import re
import subprocess
import time
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--package", default="dev.handheld.launcher")
    parser.add_argument("--output", type=Path, required=True)
    gesture = parser.add_mutually_exclusive_group(required=True)
    gesture.add_argument("--swipes", type=int, nargs=8,
                         metavar=("X1", "Y1", "X2", "Y2", "RX1", "RY1", "RX2", "RY2"))
    gesture.add_argument("--tabs", type=int, nargs="+", help="Dock tab X coordinates; use only navigation targets")
    parser.add_argument("--tab-y", type=int, default=905)
    parser.add_argument("--duration-ms", type=int, default=350)
    parser.add_argument("--pause-ms", type=int, default=400)
    parser.add_argument("--cycles", type=int, default=3)
    args = parser.parse_args()
    if args.duration_ms < 100 or args.pause_ms < 0 or args.cycles < 1:
        parser.error("Use a swipe of at least 100 ms, non-negative pause, and at least one cycle")
    args.output.mkdir(parents=True, exist_ok=True)

    def shell(*command):
        return subprocess.run([args.adb, "-s", args.serial, "shell", *map(str, command)],
                              check=True, capture_output=True, text=True, timeout=30).stdout

    activity = shell("dumpsys", "activity", "activities")
    if not any(("mResumedActivity" in line or "topResumedActivity" in line) and
               args.package + "/" in line for line in activity.splitlines()):
        raise RuntimeError("Open the intended app and page before running coordinate-based gestures")
    package_info = shell("dumpsys", "package", args.package)
    metadata = {
        "model": shell("getprop", "ro.product.model").strip(),
        "android": shell("getprop", "ro.build.version.release").strip(),
        "package": [line.strip() for line in package_info.splitlines()
                    if any(key in line for key in ("versionCode=", "versionName=", "flags=", "[status=", "[reason="))],
    }
    (args.output / "device.json").write_text(json.dumps(metadata, indent=2), encoding="utf-8")

    def step(coords):
        if args.swipes:
            shell("input", "swipe", *coords, args.duration_ms)
        else:
            shell("input", "tap", coords, args.tab_y)
        time.sleep(args.pause_ms / 1000)

    sequence = [args.swipes[:4], args.swipes[:4], args.swipes[4:], args.swipes[4:]] if args.swipes else args.tabs
    if args.swipes:
        for _ in range(4):
            step(args.swipes[4:])
    for coords in sequence:
        step(coords)
    if args.swipes:
        for _ in range(4):
            step(args.swipes[4:])

    # Reset frame counters only. This does not clear app data, artwork, or logs.
    shell("dumpsys", "gfxinfo", args.package, "reset")
    for _ in range(args.cycles):
        for coords in sequence:
            step(coords)
    frames = shell("dumpsys", "gfxinfo", args.package, "framestats")
    (args.output / "framestats.txt").write_text(frames, encoding="utf-8")
    for name, command in {
        "memory": ("dumpsys", "meminfo", args.package),
        "battery": ("dumpsys", "battery"),
        "exits": ("dumpsys", "activity", "exit-info", args.package),
    }.items():
        (args.output / f"{name}.txt").write_text(shell(*command), encoding="utf-8")
    summary = {}
    for key in ["Total frames rendered", "Janky frames", "50th percentile", "90th percentile",
                "95th percentile", "99th percentile", "Number Slow UI thread", "Number Slow bitmap uploads"]:
        match = re.search(r"^" + re.escape(key) + r": (.+)$", frames, re.MULTILINE)
        if match:
            summary[key] = match.group(1)
    if not summary:
        raise RuntimeError("No frame statistics returned; verify the app is visible and renders hardware-accelerated frames")
    config = vars(args).copy()
    config["output"] = str(args.output)
    (args.output / "summary.json").write_text(json.dumps({"config": config, "metrics": summary}, indent=2), encoding="utf-8")
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
