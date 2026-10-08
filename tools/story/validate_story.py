#!/usr/bin/env python3
"""Validate generic story JSONC and report legacy story configuration files.

This is an offline tool. It does not change resources or participate in server
startup. The generic format is intentionally stricter than the current
chapter-specific catalogs so migration errors are visible before runtime work.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[2]
JSONC_MODULE = ROOT / "tools" / "web" / "admin_lib"
if str(JSONC_MODULE) not in sys.path:
    sys.path.insert(0, str(JSONC_MODULE))

from jsonc_io import JsoncError, loads, to_plain


GENERIC_TOP_KEYS = {
    "id",
    "version",
    "enabled",
    "triggers",
    "actors",
    "startNode",
    "nodes",
    "text",
    "resources",
}

TRIGGER_KEYS = {
    "NPC": {"type", "map", "mapSuffix", "entityIdx", "npcName", "scriptSuffix",
            "startNode", "conditions", "preemptShop"},
    "COORDINATE": {"type", "map", "x", "y", "script", "startNode", "conditions"},
    "MAP_READY": {"type", "map", "startNode", "conditions"},
    "LOGIN": {"type"},
}

ACTION_KEYS = {
    "DIALOGUE": {"type", "actor", "text"},
    "YES_NO": {"type", "actor", "text", "yes", "no"},
    "MULTI_CHOICE": {"type", "actor", "text", "options"},
    "BRANCH": {"type", "condition", "yes", "no"},
    "GOTO": {"type", "node"},
    "SET_OPENING_STAGE": {"type", "expected", "nextStage"},
    "MOVE_PLAYER": {"type", "x", "y"},
    "MOVE_NPC": {"type", "actor", "target"},
    "GUIDE": {"type", "actor", "npcX", "npcY", "playerX", "playerY"},
    "PLAY_MUSIC": {"type", "musicId"},
    "CHANGE_MAP": {"type", "map", "x", "y", "z", "toward"},
    "START_BATTLE": {"type", "battleType", "finishNode"},
    "START_TRAINER_BATTLE": {
        "type", "battleId", "battleType", "finishNode", "trainerModel", "money", "team",
        "trainerTeamId", "trainerTeamIds"
    },
    "SET_STORY_BIT": {"type", "bit"},
    "SET_EXTRA_STORY_BIT": {"type", "bit"},
    "SET_BADGE": {"type", "bit"},
    "SET_CHAMPION": {"type"},
    "SET_ELITE_STAGE": {"type", "stage"},
    "GRANT_ITEM": {"type", "itemId", "amount"},
    "REMOVE_ITEM": {"type", "itemId", "amount"},
    "GRANT_POKEMON": {"type", "species", "level", "moves"},
    "START_SAFARI": {"type", "fee", "steps", "balls"},
    "RUN_MAP_READY": {"type"},
    "REJECT_MOVE": {"type"},
    "SELECT_STARTER": {"type", "next"},
    "PARCEL_OPERATION": {"type", "operation"},
    "HEAL_PARTY": {"type"},
    "NOTIFY": {"type", "message"},
    "CLOSE_DIALOG": {"type"},
    "CLOSE_SCENE": {"type"},
}

KNOWN_ACTIONS = set(ACTION_KEYS)
NODE_KEYS = {"actions", "next"}


class Validation:
    def __init__(self, root: Path) -> None:
        self.root = root
        self.errors: list[str] = []
        self.warnings: list[str] = []
        self.map_files = self._index_maps()
        self.map_npcs = self._index_map_npcs()

    def error(self, location: str, message: str) -> None:
        self.errors.append(f"{location}: {message}")

    def warning(self, location: str, message: str) -> None:
        self.warnings.append(f"{location}: {message}")

    def _index_maps(self) -> dict[str, Path]:
        result: dict[str, Path] = {}
        directory = self.root / "resource" / "map"
        if not directory.is_dir():
            self.warning("maps", f"地图目录不存在: {directory}")
            return result
        for file in directory.rglob("*.json"):
            result.setdefault(file.stem.lower(), file)
        return result

    def _index_map_npcs(self) -> dict[str, set[int]]:
        result: dict[str, set[int]] = {}
        for map_name, file in self.map_files.items():
            try:
                data = json.loads(file.read_text(encoding="utf-8"))
            except (OSError, json.JSONDecodeError) as exc:
                self.warning("maps", f"无法读取 {file}: {exc}")
                continue
            indexes: set[int] = set()
            for npc in data.get("npcs", []):
                if isinstance(npc, dict) and isinstance(npc.get("entityIdx"), int):
                    indexes.add(npc["entityIdx"])
            result[map_name] = indexes
        return result

    def validate_file(self, file: Path) -> str:
        location = file.relative_to(self.root).as_posix()
        try:
            node = loads(file.read_text(encoding="utf-8"))
            data = to_plain(node)
        except (OSError, JsoncError) as exc:
            self.error(location, str(exc))
            return "invalid"
        if not isinstance(data, dict):
            self.error(location, "根节点必须是对象")
            return "invalid"

        if {"id", "version", "nodes", "startNode"} & data.keys():
            self._validate_generic(location, data)
            return "generic"
        self._validate_legacy(location, data)
        return "legacy"

    def _validate_generic(self, location: str, data: dict[str, Any]) -> None:
        self._check_keys(location, data, GENERIC_TOP_KEYS)
        self._require(location, data, "id", str)
        self._require(location, data, "version", int)
        self._require(location, data, "enabled", bool)
        self._require(location, data, "triggers", list)
        self._require(location, data, "actors", dict)
        self._require(location, data, "startNode", str)
        self._require(location, data, "nodes", dict)
        self._require(location, data, "text", dict)
        if "resources" in data and not isinstance(data["resources"], dict):
            self.error(f"{location}.resources", "必须是对象")

        story_id = data.get("id")
        if isinstance(story_id, str):
            if not story_id or len(story_id) > 120 or any(
                part == "" for part in story_id.split(".")
            ):
                self.error(f"{location}.id", "必须是非空的点分层级 ID，长度不超过 120")
        if isinstance(data.get("version"), int) and data["version"] < 1:
            self.error(f"{location}.version", "必须大于等于 1")

        text = data.get("text")
        if isinstance(text, dict):
            for key, value in text.items():
                if not isinstance(key, str) or not key:
                    self.error(f"{location}.text", "文本键必须是非空字符串")
                if not isinstance(value, int) or value <= 0:
                    self.error(f"{location}.text.{key}", "文本引用必须是正整数")

        actors = data.get("actors")
        if isinstance(actors, dict):
            for name, actor in actors.items():
                actor_location = f"{location}.actors.{name}"
                if not isinstance(name, str) or not name:
                    self.error(f"{location}.actors", "角色名必须是非空字符串")
                    continue
                if not isinstance(actor, dict):
                    self.error(actor_location, "角色定义必须是对象")
                    continue
                self._check_keys(actor_location, actor, {"map", "entityIdx"})
                self._require(actor_location, actor, "map", str)
                self._require(actor_location, actor, "entityIdx", int)
                self._validate_map(actor_location, actor)

        triggers = data.get("triggers")
        if isinstance(triggers, list):
            if not triggers:
                self.error(f"{location}.triggers", "至少需要一个触发器")
            for index, trigger in enumerate(triggers):
                self._validate_trigger(f"{location}.triggers[{index}]", trigger)

        nodes = data.get("nodes")
        if isinstance(nodes, dict):
            if not nodes:
                self.error(f"{location}.nodes", "至少需要一个节点")
            for node_id, node in nodes.items():
                node_location = f"{location}.nodes.{node_id}"
                if not isinstance(node_id, str) or not node_id:
                    self.error(f"{location}.nodes", "节点 ID 必须是非空字符串")
                    continue
                if not isinstance(node, dict):
                    self.error(node_location, "节点必须是对象")
                    continue
                self._check_keys(node_location, node, NODE_KEYS)
                actions = node.get("actions", [])
                if not isinstance(actions, list):
                    self.error(f"{node_location}.actions", "必须是数组")
                else:
                    for index, action in enumerate(actions):
                        self._validate_action(
                            f"{node_location}.actions[{index}]",
                            action,
                            data,
                        )
                if "next" in node:
                    self._validate_node_ref(f"{node_location}.next", node["next"], data)

            if isinstance(data.get("startNode"), str):
                self._validate_node_ref(
                    f"{location}.startNode", data["startNode"], data
                )

        self._warn_unreferenced_nodes(location, data)

    def _validate_trigger(self, location: str, trigger: Any) -> None:
        if not isinstance(trigger, dict):
            self.error(location, "触发器必须是对象")
            return
        trigger_type = trigger.get("type")
        if not isinstance(trigger_type, str) or trigger_type not in TRIGGER_KEYS:
            self.error(location, f"未知触发器类型: {trigger_type!r}")
            return
        self._check_keys(location, trigger, TRIGGER_KEYS[trigger_type])
        self._require(location, trigger, "type", str)
        self._require(location, trigger, "startNode", str)
        if trigger_type != "LOGIN":
            if "map" not in trigger and "mapSuffix" not in trigger:
                self.error(location, "需要 map 或 mapSuffix")
            if "map" in trigger:
                self._require(location, trigger, "map", str)
                self._validate_map(location, trigger)
        if trigger_type == "NPC":
            if "entityIdx" in trigger:
                self._require(location, trigger, "entityIdx", int)
                self._validate_map(location, trigger)
        if trigger_type == "COORDINATE":
            if "script" not in trigger and ("x" not in trigger or "y" not in trigger):
                self.error(location, "坐标触发器需要 x/y 或 script")
            if "x" in trigger:
                self._require(location, trigger, "x", int)
            if "y" in trigger:
                self._require(location, trigger, "y", int)
            if "script" in trigger:
                self._require(location, trigger, "script", str)
        if "conditions" in trigger:
            if not isinstance(trigger["conditions"], list):
                self.error(f"{location}.conditions", "必须是数组")
            else:
                for index, condition in enumerate(trigger["conditions"]):
                    self._validate_condition(f"{location}.conditions[{index}]", condition)

    def _validate_action(
        self, location: str, action: Any, chapter: dict[str, Any]
    ) -> None:
        if not isinstance(action, dict):
            self.error(location, "动作必须是对象")
            return
        action_type = action.get("type")
        if not isinstance(action_type, str) or action_type not in KNOWN_ACTIONS:
            self.error(location, f"未知动作类型: {action_type!r}")
            return
        self._check_keys(location, action, ACTION_KEYS[action_type] | {"next"})
        self._require(location, action, "type", str)

        required: dict[str, tuple[type, ...]] = {
            "DIALOGUE": (("text", str),),
            "YES_NO": (("text", str), ("yes", str), ("no", str)),
            "MULTI_CHOICE": (("actor", str), ("text", str), ("options", list)),
            "BRANCH": (("condition", dict), ("yes", str), ("no", str)),
            "GOTO": (("node", str),),
            "SET_OPENING_STAGE": (("expected", int), ("nextStage", int)),
            "MOVE_PLAYER": (("x", (int, str)), ("y", (int, str))),
            "MOVE_NPC": (("actor", str), ("target", dict)),
            "GUIDE": (("actor", str), ("npcX", int), ("npcY", int),
                      ("playerX", int), ("playerY", int)),
            "PLAY_MUSIC": (("musicId", int),),
            "CHANGE_MAP": (("map", str), ("x", int), ("y", int)),
            "START_BATTLE": (("battleType", str),),
            "START_TRAINER_BATTLE": (("battleId", str), ("battleType", str),
                                      ("finishNode", str), ("team", list)),
            "SET_STORY_BIT": (("bit", int),),
            "SET_EXTRA_STORY_BIT": (("bit", int),),
            "SET_BADGE": (("bit", int),),
            "SET_CHAMPION": (),
            "SET_ELITE_STAGE": (("stage", int),),
            "GRANT_ITEM": (("itemId", int), ("amount", int)),
            "REMOVE_ITEM": (("itemId", int), ("amount", int)),
            "GRANT_POKEMON": (("species", int), ("level", int), ("moves", list)),
            "START_SAFARI": (),
            "RUN_MAP_READY": (),
            "SELECT_STARTER": (("next", str),),
            "PARCEL_OPERATION": (("operation", str),),
            "NOTIFY": (("message", str),),
        }
        for key, expected in required.get(action_type, ()):
            self._require(location, action, key, expected)

        if action_type in {"DIALOGUE", "YES_NO", "MULTI_CHOICE"}:
            actor = action.get("actor")
            if isinstance(actor, str) and actor not in chapter.get("actors", {}):
                self.error(f"{location}.actor", f"未定义角色: {actor}")
            text = action.get("text")
            if isinstance(text, str) and text not in chapter.get("text", {}):
                self.error(f"{location}.text", f"未定义文本键: {text}")
        if action_type in {"YES_NO"}:
            self._validate_node_ref(f"{location}.yes", action.get("yes"), chapter)
            self._validate_node_ref(f"{location}.no", action.get("no"), chapter)
        if action_type == "MULTI_CHOICE":
            options = action.get("options")
            if isinstance(options, list):
                if not options or len(options) > 8:
                    self.error(f"{location}.options", "选项数量必须在 1 到 8 之间")
                for index, option in enumerate(options):
                    option_location = f"{location}.options[{index}]"
                    if not isinstance(option, dict):
                        self.error(option_location, "选项必须是对象")
                        continue
                    self._check_keys(option_location, option, {"text", "next"})
                    self._require(option_location, option, "text", str)
                    self._require(option_location, option, "next", str)
                    if isinstance(option.get("text"), str) and option["text"] not in chapter.get(
                        "text", {}
                    ):
                        self.error(f"{option_location}.text", "未定义文本键")
                    self._validate_node_ref(
                        f"{option_location}.next", option.get("next"), chapter
                    )
        if action_type == "BRANCH":
            self._validate_condition(f"{location}.condition", action.get("condition"))
            self._validate_node_ref(f"{location}.yes", action.get("yes"), chapter)
            self._validate_node_ref(f"{location}.no", action.get("no"), chapter)
        if action_type == "GOTO":
            self._validate_node_ref(f"{location}.node", action.get("node"), chapter)
        if "next" in action:
            self._validate_node_ref(f"{location}.next", action["next"], chapter)
        if action_type == "START_BATTLE" and action.get("battleType") not in {
            "OPENING_RIVAL", "CAPTURE_TUTORIAL"
        }:
            self.error(f"{location}.battleType", "未知剧情战斗类型")
        if action_type == "START_BATTLE":
            self._validate_node_ref(f"{location}.finishNode", action.get("finishNode"), chapter)
        if action_type == "START_TRAINER_BATTLE":
            self._validate_node_ref(f"{location}.finishNode", action.get("finishNode"), chapter)
            if action.get("battleType") not in {"LOW_TRAINER", "ACE_TRAINER", "GYM_LEADER", "RIVAL"}:
                self.error(f"{location}.battleType", "未知剧情训练家战斗类型")
            team = action.get("team")
            if isinstance(team, list) and not 1 <= len(team) <= 6:
                self.error(f"{location}.team", "训练家队伍数量必须在 1 到 6 之间")
            if "trainerTeamId" in action and not isinstance(action["trainerTeamId"], int):
                self.error(f"{location}.trainerTeamId", "原版训练家队伍编号必须是整数")
        if action_type in {"SET_STORY_BIT", "SET_EXTRA_STORY_BIT", "SET_BADGE"}:
            if not 0 <= action.get("bit", -1) <= 15:
                self.error(f"{location}.bit", "标记位必须在 0 到 15 之间")
        if action_type == "GRANT_ITEM" and isinstance(action.get("amount"), int):
            if action["amount"] <= 0:
                self.error(f"{location}.amount", "数量必须大于 0")
        if action_type == "GRANT_POKEMON":
            if isinstance(action.get("level"), int) and not 1 <= action["level"] <= 100:
                self.error(f"{location}.level", "等级必须在 1 到 100 之间")
            if isinstance(action.get("moves"), list) and len(action["moves"]) > 4:
                self.error(f"{location}.moves", "招式数量不能超过 4")

    def _validate_node_ref(
        self, location: str, value: Any, chapter: dict[str, Any]
    ) -> None:
        if not isinstance(value, str):
            self.error(location, "节点引用必须是字符串")
            return
        nodes = chapter.get("nodes")
        if isinstance(nodes, dict) and value not in nodes:
            self.error(location, f"引用了不存在的节点: {value}")

    def _validate_condition(self, location: str, condition: Any) -> None:
        if not isinstance(condition, dict):
            self.error(location, "条件必须是对象")
            return
        allowed = {"type", "value"}
        self._check_keys(location, condition, {"type", "value", "flag"})
        condition_type = condition.get("type")
        if condition_type not in {
            "STORY_STAGE_EQUALS", "STORY_STAGE_AT_LEAST", "STARTER_EQUALS",
            "SEX_EQUALS", "PARCEL_PHASE_EQUALS", "PARCEL_NEEDS_PICKUP",
            "CAPTURE_TUTORIAL_COMPLETE", "CAPTURE_TUTORIAL_INCOMPLETE",
            "HAS_CAPTURE_BALL", "NO_CAPTURE_BALL", "HAS_ITEM", "NO_ITEM", "INTERACTION_IDLE",
            "NOT_IN_BATTLE", "NOT_IN_TRADE", "STORY_BIT_SET", "STORY_BIT_CLEAR",
            "BADGE_SET", "BADGE_CLEAR", "CHAMPION_SET", "CHAMPION_CLEAR",
            "EXTRA_STORY_BIT_SET", "EXTRA_STORY_BIT_CLEAR",
            "EVENT_FLAG_SET", "EVENT_FLAG_CLEAR",
            "ELITE_STAGE_EQUALS"
        }:
            self.error(location, f"未知剧情条件: {condition_type!r}")

    def _validate_map(self, location: str, data: dict[str, Any]) -> None:
        map_name = data.get("map")
        if not isinstance(map_name, str):
            return
        key = map_name.lower()
        if key not in self.map_files:
            self.error(f"{location}.map", f"找不到地图资源: {map_name}")
            return
        entity_idx = data.get("entityIdx")
        if isinstance(entity_idx, int) and entity_idx not in self.map_npcs.get(key, set()):
            self.error(
                f"{location}.entityIdx",
                f"地图 {map_name} 不存在 NPC entityIdx={entity_idx}",
            )

    def _validate_legacy(self, location: str, data: dict[str, Any]) -> None:
        if "starters" in data:
            expected = {"enabled", "trainerModel", "text", "starters"}
            self._check_keys(location, data, expected)
            self._require(location, data, "enabled", bool)
            self._require(location, data, "trainerModel", int)
            self._require(location, data, "text", dict)
            self._require(location, data, "starters", list)
            starters = data.get("starters")
            if isinstance(starters, list) and len(starters) != 3:
                self.error(f"{location}.starters", "真新镇开场必须有 3 个初始宝可梦")
            self._validate_positive_texts(f"{location}.text", data.get("text"))
            return
        if "wildSpecies" in data:
            expected = {"enabled", "wildSpecies", "wildLevel", "text"}
            self._check_keys(location, data, expected)
            self._require(location, data, "enabled", bool)
            self._require(location, data, "wildSpecies", int)
            self._require(location, data, "wildLevel", int)
            self._require(location, data, "text", dict)
            self._validate_positive_texts(f"{location}.text", data.get("text"))
            return
        if set(data).issubset({"enabled", "text"}) and "text" in data:
            self._check_keys(location, data, {"enabled", "text"})
            self._require(location, data, "enabled", bool)
            self._require(location, data, "text", dict)
            self._validate_positive_texts(f"{location}.text", data.get("text"))
            return
        self.warning(
            location,
            "未识别的旧章节格式；当前只做 JSONC 语法检查，尚未纳入通用剧情格式",
        )

    def _validate_positive_texts(self, location: str, text: Any) -> None:
        if not isinstance(text, dict):
            return
        for key, value in text.items():
            if not isinstance(value, int) or value <= 0:
                self.error(f"{location}.{key}", "文本引用必须是正整数")

    def _warn_unreferenced_nodes(self, location: str, chapter: dict[str, Any]) -> None:
        nodes = chapter.get("nodes")
        if not isinstance(nodes, dict):
            return
        referenced = {chapter.get("startNode")}
        for trigger in chapter.get("triggers", []):
            if isinstance(trigger, dict) and isinstance(trigger.get("startNode"), str):
                referenced.add(trigger["startNode"])
        for node in nodes.values():
            if not isinstance(node, dict):
                continue
            referenced.add(node.get("next"))
            for action in node.get("actions", []):
                if not isinstance(action, dict):
                    continue
                referenced.update(
                    value
                    for key, value in action.items()
                    if key in {"next", "yes", "no", "node", "finishNode"} and isinstance(value, str)
                )
                for option in action.get("options", []):
                    if isinstance(option, dict) and isinstance(option.get("next"), str):
                        referenced.add(option["next"])
        unused = sorted(set(nodes) - referenced)
        if unused:
            self.warning(f"{location}.nodes", f"存在不可达节点: {', '.join(unused)}")

    def _require(
        self, location: str, data: dict[str, Any], key: str, expected: type | tuple[type, ...]
    ) -> None:
        if key not in data:
            self.error(location, f"缺少字段: {key}")
        elif not isinstance(data[key], expected):
            names = ", ".join(item.__name__ for item in expected) if isinstance(expected, tuple) else expected.__name__
            self.error(f"{location}.{key}", f"类型必须是 {names}")

    def _check_keys(self, location: str, data: dict[str, Any], allowed: set[str]) -> None:
        for key in sorted(set(data) - allowed):
            self.error(f"{location}.{key}", "未知字段")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="校验通用剧情 JSONC，并报告当前旧章节配置的迁移状态"
    )
    parser.add_argument(
        "paths",
        nargs="*",
        type=Path,
        help="要检查的 JSONC 文件；省略时扫描 resource/story",
    )
    parser.add_argument(
        "--root",
        type=Path,
        default=ROOT,
        help="项目根目录，默认是当前仓库根目录",
    )
    parser.add_argument(
        "--generic-only",
        action="store_true",
        help="遇到旧章节配置时报告错误，而不是作为 legacy 记录",
    )
    return parser.parse_args()


def main() -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    if hasattr(sys.stderr, "reconfigure"):
        sys.stderr.reconfigure(encoding="utf-8")
    args = parse_args()
    root = args.root.resolve()
    files = [path if path.is_absolute() else root / path for path in args.paths]
    if not files:
        files = sorted((root / "resource" / "story").rglob("*.jsonc"))
    files = [path.resolve() for path in files]

    validator = Validation(root)
    counts = {"generic": 0, "legacy": 0, "invalid": 0}
    for file in files:
        if not file.is_file():
            validator.error(str(file), "文件不存在")
            counts["invalid"] += 1
            continue
        kind = validator.validate_file(file)
        counts[kind] += 1
        if args.generic_only and kind == "legacy":
            validator.error(file.relative_to(root).as_posix(), "旧章节格式不允许用于 generic-only")

    print(
        f"剧情配置检查: files={len(files)}, generic={counts['generic']}, "
        f"legacy={counts['legacy']}, invalid={counts['invalid']}"
    )
    for warning in validator.warnings:
        print(f"WARNING {warning}")
    for error in validator.errors:
        print(f"ERROR {error}")
    if validator.errors:
        print(f"结果: 失败，{len(validator.errors)} 个错误，{len(validator.warnings)} 个警告")
        return 1
    print(f"结果: 通过，{len(validator.warnings)} 个警告")
    print("未执行：服务端编译、JUnit、数据库连接、客户端运行和剧情实机验证。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
