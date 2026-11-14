#!/usr/bin/env python3
"""Reduces a Gradle log to its compiler errors, failures and tail."""
import sys
from pathlib import Path

src, dst = Path(sys.argv[1]), Path(sys.argv[2])
lines = src.read_text(errors="replace").splitlines() if src.exists() else []
keys = ("e: ", "FAILED", "FAILURE:", "What went wrong:", "error:", "Unresolved", "AssertionError", "Exception")
hits = [x for x in lines if x.startswith("e: ") or any(k in x for k in keys[1:])]
dst.write_text("\n".join(hits[:400]) + "\n\n===== TAIL =====\n" + "\n".join(lines[-120:]) + "\n")
