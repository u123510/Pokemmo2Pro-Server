"""真实地图渲染：服务端 blockData + pret 反编译图块资源 → PNG，带磁盘缓存。

- 关都（kanto）使用 pokefirered 反编译工程，可用环境变量 OPENMMO_FIRERED_ROOT 覆盖路径。
- 丰缘（hoenn）使用 pokeemerald 反编译工程，环境变量 OPENMMO_EMERALD_ROOT；未提供时回退示意图。
- 神奥（sinnoh）图源尚未接入，回退示意图。
- 渲染规则对齐游戏 ROM 加载逻辑：主/副图块集分界与调色板槽位从反编译工程
  include/fieldmap.h 读取（缺省按火红 640/1024/7/13）。
"""

from __future__ import annotations

import base64
import colorsys
import json
import os
import re
import struct
from pathlib import Path

from PIL import Image, ImageDraw

from . import map_index
from .paths import PROJECT_ROOT

CACHE_DIR = PROJECT_ROOT / "tools" / "web" / "cache" / "maps"

DEFAULT_FIRERED = Path(r"C:\Users\z3407\Desktop\pokefirered-src")
DEFAULT_EMERALD = Path(r"C:\Users\z3407\Desktop\pokeemerald-src")

_FIELDMAP_DEFAULTS = {
    "NUM_TILES_IN_PRIMARY": 640,
    "NUM_TILES_TOTAL": 1024,
    "NUM_METATILES_IN_PRIMARY": 640,
    "NUM_PALS_IN_PRIMARY": 7,
    "NUM_PALS_TOTAL": 13,
}


def _decomp_root(region: str) -> Path | None:
    env = {
        "kanto": os.getenv("OPENMMO_FIRERED_ROOT"),
        "hoenn": os.getenv("OPENMMO_EMERALD_ROOT"),
    }.get(region)
    default = {"kanto": DEFAULT_FIRERED, "hoenn": DEFAULT_EMERALD}.get(region)
    root = Path(env) if env else default
    if root is not None and (root / "data" / "tilesets").is_dir():
        return root
    return None


def _fieldmap_constants(root: Path) -> dict[str, int]:
    constants = dict(_FIELDMAP_DEFAULTS)
    header = root / "include" / "fieldmap.h"
    if header.exists():
        pattern = re.compile(r"#define\s+(NUM_[A-Z_]+)\s+(\d+)")
        for match in pattern.finditer(header.read_text(encoding="utf-8", errors="replace")):
            if match.group(1) in constants:
                constants[match.group(1)] = int(match.group(2))
    return constants


def _normalize(name: str) -> str:
    """图块集名归一化：大小写与下划线差异不影响匹配（SSAnne/ss_anne → ssanne）。"""
    return re.sub(r"[^a-z0-9]", "", name.lower())


_SYMBOL_PATHS: dict[str, str] | None = None
_TILESET_STRUCTS: dict[str, dict[str, str]] | None = None


def _symbol_paths(root: Path) -> dict[str, str]:
    """解析 graphics.h / metatiles.h：图形符号名 → INCBIN 的资源路径。

    兼容单行（tiles.4bpp.lz）与多行数组（调色板符号在上一行、INCBIN 在后续行）。
    """
    global _SYMBOL_PATHS
    if _SYMBOL_PATHS is not None:
        return _SYMBOL_PATHS
    table: dict[str, str] = {}
    for header in ("graphics.h", "metatiles.h"):
        path = root / "src" / "data" / "tilesets" / header
        if not path.exists():
            continue
        pending: str | None = None
        for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
            symbol = re.search(r"\b(g\w+)\[\]", line)
            if symbol and symbol.group(1) not in table:
                pending = symbol.group(1)
            incbin = re.search(r'INCBIN_\w+\("([^"]+)"\)', line)
            if incbin and pending:
                table[pending] = incbin.group(1)
                pending = None
            if line.strip() == "};":
                pending = None
    _SYMBOL_PATHS = table
    return table


def _tileset_structs(root: Path) -> dict[str, dict[str, str]]:
    """解析 headers.h：gTileset_X → {tiles/palettes/metatiles: 符号名}。"""
    global _TILESET_STRUCTS
    if _TILESET_STRUCTS is not None:
        return _TILESET_STRUCTS
    table: dict[str, dict[str, str]] = {}
    path = root / "src" / "data" / "tilesets" / "headers.h"
    if path.exists():
        text = path.read_text(encoding="utf-8", errors="replace")
        for match in re.finditer(
            r"const struct Tileset (gTileset_\w+)\s*=\s*\{(.*?)\};", text, re.S
        ):
            name, body = match.group(1), match.group(2)
            fields: dict[str, str] = {}
            for field in ("tiles", "palettes", "metatiles"):
                field_match = re.search(rf"\.{field}\s*=\s*(g\w+)", body)
                if field_match:
                    fields[field] = field_match.group(1)
            table[name] = fields
    _TILESET_STRUCTS = table
    return table


def _resolve_tileset_dirs(root: Path, kind: str, struct_name: str) -> tuple[Path, Path, Path] | None:
    """按 headers.h 结构体字段解析图块集的 (tiles 目录, metatiles 目录, palettes 目录)。

    共享图形（如 SilphCo 的 tiles 指向 Condominiums）由结构体字段直接给出；
    结构体或符号缺失时回退为按归一化名匹配目录。
    """
    base = root / "data" / "tilesets" / kind
    fields = _tileset_structs(root).get(struct_name)
    paths = _symbol_paths(root)
    if fields and all(fields.get(f) in paths for f in ("tiles", "metatiles", "palettes")):
        tiles_dir = (root / paths[fields["tiles"]]).parent
        metatiles_dir = (root / paths[fields["metatiles"]]).parent
        palettes_dir = (root / paths[fields["palettes"]]).parent
        if tiles_dir.is_dir():
            return tiles_dir, metatiles_dir, palettes_dir
    # 回退：归一化目录名匹配
    wanted = _normalize(struct_name.removeprefix("gTileset_"))
    if base.is_dir():
        for entry in base.iterdir():
            if entry.is_dir() and _normalize(entry.name) == wanted:
                return entry, entry, entry
    return None


def _load_jasc(path: Path) -> list[tuple[int, int, int]] | None:
    try:
        lines = path.read_text(encoding="ascii").splitlines()
    except OSError:
        return None
    colors = [tuple(int(v) for v in line.split()) for line in lines[3:19]]
    return colors if len(colors) == 16 else None


class _Tileset:
    def __init__(self, tiles_dir: Path, metatiles_dir: Path, palettes_dir: Path, slots: range,
                 constants: dict[str, int]) -> None:
        image = Image.open(tiles_dir / "tiles.png").convert("P")
        self.pixels = image.load()
        self.tile_count = (image.width // 8) * (image.height // 8)
        self.tiles_per_row = max(1, image.width // 8)
        data = (metatiles_dir / "metatiles.bin").read_bytes()
        self.metatiles = [
            struct.unpack("<8H", data[i : i + 16]) for i in range(0, len(data) - 15, 16)
        ]
        self.palettes: dict[int, list[tuple[int, int, int]]] = {}
        for slot in slots:
            palette = _load_jasc(palettes_dir / "palettes" / f"{slot:02}.pal")
            if palette is None:
                palette = _load_jasc(palettes_dir / f"{slot:02}.pal")
            if palette is not None:
                self.palettes[slot] = palette

    def tile_index(self, tile_id: int, x: int, y: int, primary_count: int) -> int:
        local = tile_id if tile_id < primary_count else tile_id - primary_count
        return (local % self.tiles_per_row) * 8 + x, (local // self.tiles_per_row) * 8 + y


def _layout(root: Path, map_name: str) -> dict:
    map_json = (root / "data" / "maps" / map_name / "map.json").read_text(encoding="utf-8")
    layout_id = json.loads(map_json)["layout"]
    layouts = json.loads((root / "data" / "layouts" / "layouts.json").read_text(encoding="utf-8"))
    for entry in layouts["layouts"]:
        if entry.get("id") == layout_id or entry.get("name") == layout_id:
            return entry
    raise FileNotFoundError(layout_id)


def _render_real(root: Path, info: dict, out: Path, scale: int) -> None:
    constants = _fieldmap_constants(root)
    tiles_primary = constants["NUM_TILES_IN_PRIMARY"]
    metatiles_primary = constants["NUM_METATILES_IN_PRIMARY"]
    pals_primary = constants["NUM_PALS_IN_PRIMARY"]
    pals_total = constants["NUM_PALS_TOTAL"]

    layout = _layout(root, info["name"])
    resolved_primary = _resolve_tileset_dirs(root, "primary", layout["primary_tileset"])
    resolved_secondary = _resolve_tileset_dirs(root, "secondary", layout["secondary_tileset"])
    if resolved_primary is None or resolved_secondary is None:
        raise FileNotFoundError(
            f"图块目录缺失: {layout['primary_tileset']} / {layout['secondary_tileset']}")
    primary = _Tileset(*resolved_primary, range(0, pals_primary), constants)
    secondary = _Tileset(*resolved_secondary, range(pals_primary, pals_total), constants)

    raw = base64.b64decode(info["_blockData"])
    width, height = info["width"], info["height"]
    image = Image.new("RGB", (width * 16, height * 16), (0, 0, 0))
    pixels = image.load()
    for by in range(height):
        for bx in range(width):
            index = (by * width + bx) * 2
            metatile_id = (raw[index] | raw[index + 1] << 8) & 0x3FF
            if metatile_id < metatiles_primary:
                tileset, meta = primary, metatile_id
            else:
                tileset, meta = secondary, metatile_id - metatiles_primary
            if meta >= len(tileset.metatiles):
                continue
            halfwords = tileset.metatiles[meta]
            for layer in (0, 1):
                for corner in range(4):
                    word = halfwords[layer * 4 + corner]
                    tile_id = word & 0x3FF
                    flip_x, flip_y = (word >> 10) & 1, (word >> 11) & 1
                    pal_slot = (word >> 12) & 0xF
                    source = primary if tile_id < tiles_primary else secondary
                    local = tile_id if tile_id < tiles_primary else tile_id - tiles_primary
                    if local >= source.tile_count:
                        continue  # 图块引用超出该图块集图形范围（个别共享图块集的历史遗留）
                    palette = source.palettes.get(pal_slot)
                    if palette is None:
                        continue
                    cx, cy = (corner % 2) * 8, (corner // 2) * 8
                    for ty in range(8):
                        for tx in range(8):
                            sx, sy = source.tile_index(
                                tile_id,
                                (7 - tx) if flip_x else tx,
                                (7 - ty) if flip_y else ty,
                                tiles_primary,
                            )
                            color_index = source.pixels[sx, sy]
                            if layer == 1 and color_index == 0:
                                continue
                            pixels[bx * 16 + cx + tx, by * 16 + cy + ty] = palette[color_index]
    if scale != 1:
        image = image.resize((width * 16 * scale, height * 16 * scale), Image.NEAREST)
    image.save(out)


def _render_schematic(info: dict, out: Path, scale: int) -> None:
    """无反编译图源时的示意图：按图块编号上色、碰撞格压暗。"""
    grid = map_index.block_grid(info)
    width, height = info["width"], info["height"]
    image = Image.new("RGB", (width * 8, height * 8), (24, 24, 24))
    pixels = image.load()

    def color_of(tile_id: int) -> tuple[int, int, int]:
        hashed = (tile_id * 2654435761) & 0xFFFFFFFF
        hue = (hashed % 360) / 360.0
        sat = 0.25 + (hashed >> 8) % 20 / 100.0
        val = 0.72 + (hashed >> 16) % 20 / 100.0
        return tuple(int(c * 255) for c in colorsys.hsv_to_rgb(hue, sat, val))

    for y in range(height):
        for x in range(width):
            cell = grid[y][x]
            color = color_of(cell["tileId"])
            if cell["collision"]:
                color = tuple(int(c * 0.5) for c in color)
            for dy in range(8):
                for dx in range(8):
                    pixels[x * 8 + dx, y * 8 + dy] = color
    if scale != 1:
        image = image.resize((width * 8 * scale, height * 8 * scale), Image.NEAREST)
    draw = ImageDraw.Draw(image)
    step = 8 * scale
    for gx in range(0, image.width, step):
        draw.line([(gx, 0), (gx, image.height)], fill=(60, 60, 60))
    for gy in range(0, image.height, step):
        draw.line([(0, gy), (image.width, gy)], fill=(60, 60, 60))
    image.save(out)


def render_map(region: str, name: str, scale: int = 2) -> tuple[Path, str]:
    """渲染地图 PNG，返回 (文件路径, 模式 real|schematic)。"""
    info = map_index.get(region, name)
    if info is None:
        raise FileNotFoundError(f"地图不存在: {region}/{name}")
    scale = max(1, min(int(scale), 4))
    CACHE_DIR.mkdir(parents=True, exist_ok=True)
    stem = f"{region}_{name}_{int(info['mtime'])}_x{scale}"
    real = _decomp_root(region) is not None and (region in ("kanto", "hoenn"))
    stem += "_real" if real else "_schem"
    out = CACHE_DIR / (stem + ".png")
    if out.exists():
        return out, ("real" if real else "schematic")
    if real:
        try:
            _render_real(_decomp_root(region), info, out, scale)
            return out, "real"
        except (OSError, KeyError, ValueError, struct.error):
            out.unlink(missing_ok=True)
    _render_schematic(info, out, scale)
    return out, "schematic"
