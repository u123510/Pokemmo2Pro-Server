"""操作审计日志：所有写操作追加到 tools/web/audit/audit.jsonl（gitignore）。"""

from __future__ import annotations

import json
import time
from pathlib import Path

from .paths import PROJECT_ROOT

AUDIT_DIR = PROJECT_ROOT / "tools" / "web" / "audit"
AUDIT_FILE = AUDIT_DIR / "audit.jsonl"


def record(action: str, detail: dict, ok: bool = True) -> None:
    entry = {
        "ts": time.strftime("%Y-%m-%d %H:%M:%S"),
        "action": action,
        "ok": ok,
        "detail": detail,
    }
    try:
        AUDIT_DIR.mkdir(parents=True, exist_ok=True)
        with AUDIT_FILE.open("a", encoding="utf-8") as out:
            out.write(json.dumps(entry, ensure_ascii=False) + "\n")
    except OSError:
        pass  # 审计失败不阻断业务，但尽量不发生


def recent(limit: int = 100) -> list[dict]:
    if not AUDIT_FILE.exists():
        return []
    lines = AUDIT_FILE.read_text(encoding="utf-8").splitlines()
    entries = []
    for line in lines[-limit:]:
        try:
            entries.append(json.loads(line))
        except ValueError:
            continue
    entries.reverse()
    return entries
