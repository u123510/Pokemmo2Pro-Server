#!/usr/bin/env python3
"""Validate generated Kanto trainer and item catalogs against map resources."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    root = args.root

    trainer = json.loads((root / "resource/trainer/KantoTrainer.jsonc").read_text(encoding="utf-8"))
    item = json.loads((root / "resource/item/KantoItem.jsonc").read_text(encoding="utf-8"))
    trainer_keys = {(entry["map"], entry["entityIdx"]) for entry in trainer["npcBindings"]}
    trainer_ids = {entry["trainerTeamId"] for entry in trainer["trainerTeams"]}
    missing_trainers = []
    trainer_npcs = 0
    for file in sorted((root / "resource/map/kanto").rglob("*.json")):
        data = json.loads(file.read_text(encoding="utf-8"))
        for npc in data.get("npcs", []):
            script = npc.get("script", "")
            if npc.get("trainerType", 0) == 0 or not script or script == "0x0":
                continue
            if script.startswith("EventScript_"):
                continue
            trainer_npcs += 1
            key = (file.stem, npc["entityIdx"])
            if key not in trainer_keys:
                missing_trainers.append(f"{file.stem}#{npc['entityIdx']}:{script}")

    errors = []
    if missing_trainers:
        errors.append("missing trainer bindings: " + ", ".join(missing_trainers[:20]))
    if not trainer_ids:
        errors.append("trainer catalog is empty")
    if not item.get("pickups"):
        errors.append("item catalog is empty")

    print(f"trainerTeams={len(trainer['trainerTeams'])} trainerBindings={len(trainer['npcBindings'])} "
          f"mapTrainerNpcs={trainer_npcs} itemPickups={len(item['pickups'])}")
    if errors:
        for error in errors:
            print("ERROR " + error)
        return 1
    print("Kanto catalog validation: passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
