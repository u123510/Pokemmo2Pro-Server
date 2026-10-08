#!/usr/bin/env python3
"""Generate map item pickup bindings from pret/pokefirered scripts."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path


def constants(path: Path) -> dict[str, int]:
    values: dict[str, int] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        match = re.match(r"\s*#define\s+([A-Z0-9_]+)\s+(\d+)\s*(?://.*)?$", line)
        if match:
            values[match.group(1)] = int(match.group(2))
    return values


def script_blocks(text: str) -> dict[str, str]:
    pattern = re.compile(
        r"^([A-Za-z0-9_]+)::\s*\n(.*?)(?=^[A-Za-z0-9_]+::|\Z)",
        re.MULTILINE | re.DOTALL,
    )
    return {match.group(1): match.group(2) for match in pattern.finditer(text)}


def load_scripts(reference: Path, map_name: str, global_scripts: dict[str, str]) -> dict[str, str]:
    result = dict(global_scripts)
    path = reference / "data" / "maps" / map_name / "scripts.inc"
    if path.is_file():
        result.update(script_blocks(path.read_text(encoding="utf-8")))
    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--reference", type=Path, required=True)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--output", type=Path,
                        default=Path("resource/item/KantoItem.jsonc"))
    args = parser.parse_args()

    item_ids = constants(args.reference / "include/constants/items.h")
    item_ball_path = args.reference / "data/scripts/item_ball_scripts.inc"
    global_scripts = script_blocks(
        item_ball_path.read_text(encoding="utf-8")
    ) if item_ball_path.is_file() else {}
    global_scripts.update(script_blocks(
        (args.reference / "data/scripts/trainers.inc").read_text(encoding="utf-8")
    ))
    story_scripts = {
        "LavenderTown_VolunteerPokemonHouse_EventScript_MrFuji",
        "MtMoon_B2F_EventScript_DomeFossil",
        "MtMoon_B2F_EventScript_HelixFossil",
        "RocketHideout_B4F_EventScript_SilphScope",
        "RocketHideout_B4F_EventScript_LiftKey",
        "SafariZone_SecretHouse_EventScript_Attendant",
        "SSAnne_CaptainsOffice_EventScript_Captain",
        "SilphCo_11F_EventScript_President",
        "FuchsiaCity_WardensHouse_EventScript_Warden",
    }

    result: list[dict] = []
    for map_file in sorted((args.root / "resource/map/kanto").rglob("*.json")):
        try:
            map_data = json.loads(map_file.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            continue
        map_name = map_file.stem
        scripts = load_scripts(args.reference, map_name, global_scripts)
        for npc in map_data.get("npcs", []):
            script_name = npc.get("script")
            if script_name in story_scripts:
                continue
            if script_name and any(script_name.endswith(name) for name in (
                    "_EventScript_Brock", "_EventScript_Misty",
                    "_EventScript_LtSurge", "_EventScript_Erika",
                    "_EventScript_Koga", "_EventScript_Sabrina",
                    "_EventScript_Blaine", "_EventScript_Giovanni")):
                continue
            body = scripts.get(script_name, "")
            match = re.search(
                r"(?:giveitem(?:_msg)?|finditem)\s+(?:[A-Za-z0-9_]+,\s*)?"
                r"(ITEM_[A-Z0-9_]+)(?:,\s*(\d+))?",
                body,
            )
            if not match:
                continue
            item_name = match.group(1)
            item_id = item_ids.get(item_name)
            if item_id is None:
                continue
            result.append({
                "map": map_name,
                "entityIdx": npc.get("entityIdx"),
                "script": script_name,
                "itemId": item_id,
                "amount": int(match.group(2) or 1),
                "hideFlag": npc.get("hideFlag", ""),
            })

    target = args.root / args.output
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps({
        "version": 1,
        "pickups": result,
    }, ensure_ascii=True, indent=2) + "\n", encoding="utf-8")
    print(f"pickups={len(result)} output={target}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
