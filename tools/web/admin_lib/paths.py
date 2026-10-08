"""项目根目录定位与写路径白名单校验。"""

from __future__ import annotations

from pathlib import Path, PurePosixPath

# 工具必须从项目根目录运行；资源路径依赖项目根（与游戏服一致）。
PROJECT_ROOT = Path(__file__).resolve().parents[3]
RESOURCE_ROOT = PROJECT_ROOT / "resource"

SHOP_DIR = RESOURCE_ROOT / "shop"
CUSTOM_NPC_DIR = RESOURCE_ROOT / "npc" / "custom"
MAP_DIR = RESOURCE_ROOT / "map"
ITEM_FILE = RESOURCE_ROOT / "item" / "Item.jsonc"

# 只允许写这两个目录；resource/map 等原生资源一律只读。
WRITABLE_DIRS = (SHOP_DIR, CUSTOM_NPC_DIR)


class PathError(ValueError):
    """路径越界或非法。"""


def check_writable(path: Path) -> Path:
    """确保写入目标位于白名单目录内且不含符号链接（与 CustomNpcStore 一致）。"""
    target = path.resolve()
    for base in WRITABLE_DIRS:
        base = base.resolve()
        if target == base or target.is_relative_to(base):
            current: Path | None = target
            while current is not None:
                if current.is_symlink():
                    raise PathError(f"路径包含符号链接，拒绝写入: {current}")
                parent = current.parent
                current = None if parent == current else parent
            return target
    raise PathError(f"只允许写入 {SHOP_DIR} 或 {CUSTOM_NPC_DIR}，拒绝: {path}")


def safe_relative(path: Path, base: Path) -> PurePosixPath:
    """把绝对路径转为相对 base 的 POSIX 相对路径，越界时报错。"""
    resolved = path.resolve()
    root = base.resolve()
    if resolved != root and not resolved.is_relative_to(root):
        raise PathError(f"路径越界: {path}")
    return PurePosixPath(resolved.relative_to(root).as_posix())


def resource_file(*parts: str) -> Path:
    return RESOURCE_ROOT.joinpath(*parts)
