"""写前备份：保存/删除配置前把原文件快照到 tools/web/backups。"""

from __future__ import annotations

import shutil
import time
from pathlib import Path

from .paths import PROJECT_ROOT, PathError, safe_relative

BACKUP_ROOT = PROJECT_ROOT / "tools" / "web" / "backups"
KEEP_SNAPSHOTS = 30


def _target_for(file: Path, stamp: str) -> Path:
    try:
        rel = safe_relative(file, PROJECT_ROOT)
    except PathError:
        # 沙箱/外部文件的兜底：只保留文件名，避免自测误触真实备份结构。
        rel = Path("external") / file.name
    return BACKUP_ROOT / stamp / rel


def snapshot(file: Path) -> Path:
    """备份单个文件，返回备份副本路径。"""
    target = _target_for(file, time.strftime("%Y%m%d-%H%M%S"))
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(file, target)
    prune_old_snapshots()
    return target


def record_for_deletion(file: Path) -> Path:
    """删除文件前把它整体挪进备份目录，返回备份路径。"""
    target = _target_for(file, time.strftime("%Y%m%d-%H%M%S"))
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.move(str(file), str(target))
    prune_old_snapshots()
    return target


def prune_old_snapshots() -> None:
    if not BACKUP_ROOT.is_dir():
        return
    stamps = sorted((p for p in BACKUP_ROOT.iterdir() if p.is_dir()), reverse=True)
    for stale in stamps[KEEP_SNAPSHOTS:]:
        shutil.rmtree(stale, ignore_errors=True)
