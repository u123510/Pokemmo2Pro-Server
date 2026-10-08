#!/usr/bin/env python3
"""Generate the server trainer catalog from a pret/pokefirered checkout.

The generated file is intentionally a data artifact. The server never parses C
source at runtime; this tool translates trainer constants, party definitions,
and map-script bindings into the existing TrainerTeamManager JSON contract.
"""

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


def blocks(text: str, start: str, next_start: str) -> dict[str, str]:
    pattern = re.compile(
        rf"^{re.escape(start)}([A-Za-z0-9_]+).*?\{{(.*?)(?=^{re.escape(next_start)}|\Z)",
        re.MULTILINE | re.DOTALL,
    )
    return {match.group(1): match.group(2) for match in pattern.finditer(text)}


def trainer_blocks(text: str) -> dict[str, str]:
    pattern = re.compile(
        r"^\s*\[(TRAINER_[A-Z0-9_]+)\]\s*=\s*\{(.*?)(?=^\s*\[TRAINER_[A-Z0-9_]+\]|\Z)",
        re.MULTILINE | re.DOTALL,
    )
    return {match.group(1): match.group(2) for match in pattern.finditer(text)}


def party_blocks(text: str) -> dict[str, str]:
    pattern = re.compile(
        r"static const struct [^{]+\s+(sParty_[A-Za-z0-9_]+)\[\]\s*=\s*\{(.*?)(?=^static const struct|\Z)",
        re.MULTILINE | re.DOTALL,
    )
    return {match.group(1): match.group(2) for match in pattern.finditer(text)}


def field(block: str, name: str) -> str | None:
    match = re.search(rf"\.{re.escape(name)}\s*=\s*([^,\n]+)", block)
    return match.group(1).strip() if match else None


def array_field(block: str, name: str) -> list[str]:
    match = re.search(rf"\.{re.escape(name)}\s*=\s*\{{([^}}]*)\}}", block, re.DOTALL)
    if not match:
        return []
    return [item.strip() for item in match.group(1).split(",") if item.strip()]


def party_members(block: str, species_ids: dict[str, int], move_ids: dict[str, int]) -> list[dict]:
    result: list[dict] = []
    for member in re.findall(r"\n\s{4}\{(.*?)(?=\n\s{4}\},)", block, re.DOTALL):
        species = field(member, "species")
        level = field(member, "lvl")
        if species not in species_ids or level is None:
            continue
        iv = int(field(member, "iv") or "0")
        iv_value = min(31, max(0, iv // 8 if iv > 31 else iv))
        moves = [move_ids.get(move, 0) for move in array_field(member, "moves")]
        moves = (moves + [0, 0, 0, 0])[:4]
        result.append({
            "pokemonIndexId": species_ids[species],
            "containerPos": len(result),
            "level": int(level),
            "personalityValue": 0,
            "pokemonIvs": [iv_value] * 6,
            "pokemonEvs": [0] * 6,
            "ability": 0,
            "moves": moves,
            "movesPp": [0, 0, 0, 0],
            "item": -1,
            "ballType": 3,
        })
    return result


def trainer_level(trainer_class: str | None) -> str:
    if trainer_class in {"RIVAL_EARLY", "RIVAL_LATE", "CHAMPION"}:
        return "RivalTrainer"
    if trainer_class == "ELITE_FOUR":
        return "EliteTrainer"
    if trainer_class in {"LEADER", "BOSS"}:
        return "GymLeaderTrainer"
    if trainer_class in {"ACE_TRAINER", "COOLTRAINER"}:
        return "AceTrainer"
    return "LowTrainer"


def battle_script_map(reference: Path, map_name: str,
                      global_scripts: dict[str, str]) -> dict[str, str]:
    script_file = reference / "data" / "maps" / map_name / "scripts.inc"
    result: dict[str, str] = {}
    if script_file.is_file():
        text = script_file.read_text(encoding="utf-8")
        pattern = re.compile(
            r"^([A-Za-z0-9_]+)::\s*\n(.*?)(?=^[A-Za-z0-9_]+::|\Z)",
            re.MULTILINE | re.DOTALL,
        )
        for match in pattern.finditer(text):
            battle = re.search(r"trainerbattle_[a-z_]+\s+(TRAINER_[A-Z0-9_]+)", match.group(2))
            if battle:
                result[match.group(1)] = battle.group(1)
    result.update({key: value for key, value in global_scripts.items()
                   if key.startswith(map_name + "_")})
    return result


def sight_ranges(reference: Path, map_name: str) -> dict[str, int]:
    path = reference / "data/maps" / map_name / "map.json"
    if not path.is_file():
        return {}
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return {}
    result: dict[str, int] = {}
    for event in data.get("object_events", []):
        script = event.get("script")
        sight = event.get("trainer_sight_or_berry_tree_id")
        if not script or sight is None:
            continue
        try:
            result[script] = max(1, int(sight))
        except (TypeError, ValueError):
            continue
    return result


def story_npc_keys(root: Path) -> set[tuple[str, int]]:
    keys: set[tuple[str, int]] = set()
    for file in (root / "resource/story").rglob("*.jsonc"):
        try:
            chapter = json.loads(file.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            continue
        for trigger in chapter.get("triggers", []):
            if trigger.get("type") == "NPC" and "map" in trigger and "entityIdx" in trigger:
                keys.add((trigger["map"], trigger["entityIdx"]))
    return keys


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--reference", type=Path, required=True)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--output", type=Path,
                        default=Path("resource/trainer/KantoTrainer.jsonc"))
    args = parser.parse_args()
    story_keys = story_npc_keys(args.root)

    species_ids = constants(args.reference / "include/constants/species.h")
    move_ids = constants(args.reference / "include/constants/moves.h")
    trainer_ids = constants(args.reference / "include/constants/opponents.h")
    trainer_text = (args.reference / "src/data/trainers.h").read_text(encoding="utf-8")
    party_text = (args.reference / "src/data/trainer_parties.h").read_text(encoding="utf-8")
    trainer_defs = trainer_blocks(trainer_text)
    parties = party_blocks(party_text)
    global_script_text = (args.reference / "data/scripts/trainers.inc").read_text(encoding="utf-8")
    global_scripts: dict[str, str] = {}
    script_pattern = re.compile(
        r"^([A-Za-z0-9_]+)::\s*\n(.*?)(?=^[A-Za-z0-9_]+::|\Z)",
        re.MULTILINE | re.DOTALL,
    )
    for match in script_pattern.finditer(global_script_text):
        battle = re.search(r"trainerbattle_[a-z_]+\s+(TRAINER_[A-Z0-9_]+)", match.group(2))
        if battle:
            global_scripts[match.group(1)] = battle.group(1)

    teams: list[dict] = []
    for trainer_name, trainer_id in sorted(trainer_ids.items(), key=lambda item: item[1]):
        definition = trainer_defs.get(trainer_name)
        if not definition:
            continue
        party_match = re.search(r"\(sParty_([A-Za-z0-9_]+)\)", definition)
        if not party_match:
            continue
        party = parties.get("sParty_" + party_match.group(1))
        if not party:
            continue
        pokemons = party_members(party, species_ids, move_ids)
        if not pokemons:
            continue
        level = field(definition, "trainerClass")
        teams.append({
            "trainerTeamId": trainer_id,
            "trainerLevelType": trainer_level(level.replace("TRAINER_CLASS_", "") if level else None),
            "battleTeamType": "Trainer",
            "trainerRegionIndexId": 0,
            "trainerModelIndexId": 328,
            "money": max(member["level"] for member in pokemons) * 20,
            "battleTeams": [{"teamName": trainer_name, "pokemons": pokemons}],
        })

    bindings: list[dict] = []
    map_root = args.root / "resource/map/kanto"
    for map_file in sorted(map_root.rglob("*.json")):
        try:
            map_data = json.loads(map_file.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            continue
        map_name = map_file.stem
        script_map = battle_script_map(args.reference, map_name, global_scripts)
        sight_map = sight_ranges(args.reference, map_name)
        if not script_map:
            continue
        for npc in map_data.get("npcs", []):
            script = npc.get("script")
            trainer_name = script_map.get(script)
            trainer_id = trainer_ids.get(trainer_name or "")
            if trainer_id is not None:
                bindings.append({
                    "map": map_name,
                    "entityIdx": npc.get("entityIdx"),
                    "script": script,
                    "trainerTeamId": trainer_id,
                    "sightRange": 0 if (map_name, npc.get("entityIdx")) in story_keys
                    else sight_map.get(script, 1),
                })

    output = {
        "version": 1,
        "trainerTeams": teams,
        "npcBindings": bindings,
    }
    target = args.root / args.output
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(output, ensure_ascii=True, indent=2) + "\n", encoding="utf-8")
    print(f"teams={len(teams)} bindings={len(bindings)} output={target}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
