"""带注释保留的 JSONC 读写。

解析结果是一棵 Value 节点树，注释附着在后续的键/元素上（leading）或
同一值/容器的行尾（trailing）。序列化时按 2 空格缩进还原，现有商店
配置文件可以往返保留全部手写注释。游戏服使用 Gson lenient 解析，这里
同样允许 // 与 /* */ 注释，字符串只支持双引号（资源文件均如此）。
"""

from __future__ import annotations

import json
from dataclasses import dataclass, field


class JsoncError(ValueError):
    """JSONC 文本非法。"""


@dataclass
class Value:
    """带注释的 JSON 值节点。"""

    kind: str  # dict | list | scalar
    value: object  # dict[str, Value] | list[Value] | 标量
    leading: list[str] = field(default_factory=list)  # 上方注释行
    trailing: str | None = None  # 行尾或收尾注释（已去掉 // 前缀）


def new_object() -> Value:
    return Value("dict", {})


def new_array() -> Value:
    return Value("list", [])


def new_scalar(raw: object) -> Value:
    return Value("scalar", raw)


def to_plain(node: Value) -> object:
    if node.kind == "dict":
        return {key: to_plain(child) for key, child in node.value.items()}
    if node.kind == "list":
        return [to_plain(child) for child in node.value]
    return node.value


def from_plain(data: object) -> Value:
    if isinstance(data, dict):
        return Value("dict", {key: from_plain(item) for key, item in data.items()})
    if isinstance(data, list):
        return Value("list", [from_plain(item) for item in data])
    return Value("scalar", data)


def get(node: Value, key: str, default: object = None) -> object:
    child = node.value.get(key)
    return to_plain(child) if child is not None else default


def set_value(node: Value, key: str, data: object) -> None:
    """替换键的值，保留原键上的注释。"""
    existing = node.value.get(key)
    fresh = from_plain(data)
    if existing is not None:
        fresh.leading, fresh.trailing = existing.leading, existing.trailing
    node.value[key] = fresh


class _Parser:
    def __init__(self, text: str) -> None:
        self.text = text
        self.pos = 0
        self.length = len(text)

    def error(self, message: str) -> JsoncError:
        line = self.text.count("\n", 0, min(self.pos, self.length)) + 1
        return JsoncError(f"JSONC 解析失败（第 {line} 行）: {message}")

    def peek(self) -> str:
        return self.text[self.pos] if self.pos < self.length else ""

    def skip_blank(self) -> None:
        while self.pos < self.length and self.text[self.pos] in " \t\r\n":
            self.pos += 1

    def try_comment(self) -> str | None:
        if self.text.startswith("//", self.pos):
            end = self.text.find("\n", self.pos)
            if end < 0:
                end = self.length
            content = self.text[self.pos + 2 : end].rstrip("\r").strip()
            self.pos = end
            return content
        if self.text.startswith("/*", self.pos):
            end = self.text.find("*/", self.pos + 2)
            if end < 0:
                raise self.error("块注释缺少结束符")
            content = " ".join(
                part.strip() for part in self.text[self.pos + 2 : end].splitlines() if part.strip()
            )
            self.pos = end + 2
            return content
        return None

    def read_leading(self) -> list[str]:
        comments: list[str] = []
        while True:
            self.skip_blank()
            comment = self.try_comment()
            if comment is None:
                return comments
            comments.append(comment)

    def read_trailing(self) -> str | None:
        save = self.pos
        while self.pos < self.length and self.text[self.pos] in " \t":
            self.pos += 1
        if self.text.startswith("//", self.pos):
            return self.try_comment()
        self.pos = save
        return None

    def parse_document(self) -> Value:
        comments = self.read_leading()
        node = self.parse_value()
        node.leading = comments + node.leading
        footer = self.read_leading()
        if footer:
            node.trailing = " | ".join(filter(None, [node.trailing, *footer]))
        self.skip_blank()
        if self.pos != self.length:
            raise self.error("文档末尾存在多余内容")
        return node

    def parse_value(self) -> Value:
        comments = self.read_leading()
        current = self.peek()
        if current == "{":
            node = self.parse_object()
        elif current == "[":
            node = self.parse_array()
        elif current == "":
            raise self.error("意外的文档结束")
        else:
            node = self.parse_scalar()
        node.leading = comments + node.leading
        node.trailing = self.read_trailing()
        return node

    def parse_string(self) -> str:
        if self.peek() != '"':
            raise self.error("应当是双引号字符串")
        start = self.pos
        self.pos += 1
        while self.pos < self.length:
            ch = self.text[self.pos]
            if ch == "\\":
                self.pos += 2
                continue
            if ch == '"':
                raw = self.text[start : self.pos + 1]
                self.pos += 1
                try:
                    return json.loads(raw)
                except json.JSONDecodeError as exc:
                    raise self.error(f"字符串转义非法: {exc}") from exc
            self.pos += 1
        raise self.error("字符串缺少结束引号")

    def parse_scalar(self) -> Value:
        if self.peek() == '"':
            return new_scalar(self.parse_string())
        start = self.pos
        while self.pos < self.length and self.text[self.pos] not in ",]} \t\r\n":
            self.pos += 1
        raw = self.text[start : self.pos]
        if raw in ("true", "false", "null"):
            return new_scalar({"true": True, "false": False, "null": None}[raw])
        try:
            return new_scalar(int(raw))
        except ValueError:
            pass
        try:
            return new_scalar(float(raw))
        except ValueError as exc:
            raise self.error(f"无法识别的值: {raw[:32]!r}") from exc

    def parse_object(self) -> Value:
        self.pos += 1  # 跳过 {
        node = new_object()
        seen: set[str] = set()
        while True:
            comments = self.read_leading()
            current = self.peek()
            if current == "":
                raise self.error("对象缺少结束大括号")
            if current == "}":
                self.pos += 1
                if comments:
                    node.trailing = " | ".join(comments)
                return node
            if current != '"':
                raise self.error("对象键必须是双引号字符串")
            key = self.parse_string()
            if key in seen:
                raise self.error(f"重复的键: {key}")
            seen.add(key)
            self.skip_blank()
            if self.peek() != ":":
                raise self.error(f"键 {key} 后缺少冒号")
            self.pos += 1
            child = self.parse_value()
            child.leading = comments + child.leading
            node.value[key] = child
            self.skip_blank()
            if self.peek() == ",":
                self.pos += 1
                continue
            if self.peek() == "}":
                self.pos += 1
                return node
            raise self.error("对象中缺少逗号或结束大括号")

    def parse_array(self) -> Value:
        self.pos += 1  # 跳过 [
        node = new_array()
        while True:
            comments = self.read_leading()
            current = self.peek()
            if current == "":
                raise self.error("数组缺少结束中括号")
            if current == "]":
                self.pos += 1
                if comments:
                    node.trailing = " | ".join(comments)
                return node
            child = self.parse_value()
            child.leading = comments + child.leading
            node.value.append(child)
            self.skip_blank()
            if self.peek() == ",":
                self.pos += 1
                continue
            if self.peek() == "]":
                self.pos += 1
                return node
            raise self.error("数组中缺少逗号或结束中括号")


def _encode_scalar(raw: object) -> str:
    if raw is None:
        return "null"
    if raw is True:
        return "true"
    if raw is False:
        return "false"
    if isinstance(raw, str):
        return json.dumps(raw, ensure_ascii=False)
    if isinstance(raw, float):
        return repr(raw)
    if isinstance(raw, int):
        return str(raw)
    raise TypeError(f"不支持的标量类型: {type(raw).__name__}")


def _emit(node: Value, indent: str) -> str:
    pad = indent + "  "
    if node.kind == "dict":
        if not node.value:
            body = "{}"
        else:
            entries = list(node.value.items())
            lines = []
            for index, (key, child) in enumerate(entries):
                comma = "," if index < len(entries) - 1 else ""
                prefix = "".join(f"{pad}// {comment}\n" for comment in child.leading)
                line = f"{pad}{json.dumps(key, ensure_ascii=False)}: {_emit(child, pad)}{comma}"
                if child.trailing:
                    line += f"  // {child.trailing}"
                lines.append(prefix + line)
            body = "{\n" + "\n".join(lines) + "\n" + indent + "}"
    elif node.kind == "list":
        if not node.value:
            body = "[]"
        else:
            lines = []
            for index, child in enumerate(node.value):
                comma = "," if index < len(node.value) - 1 else ""
                prefix = "".join(f"{pad}// {comment}\n" for comment in child.leading)
                line = pad + _emit(child, pad) + comma
                if child.trailing:
                    line += f"  // {child.trailing}"
                lines.append(prefix + line)
            body = "[\n" + "\n".join(lines) + "\n" + indent + "]"
    else:
        body = _encode_scalar(node.value)
    if node.trailing:
        body += f"  // {node.trailing}"
    return body


def loads(text: str) -> Value:
    """解析 JSONC 文本为带注释的节点树。"""
    return _Parser(text).parse_document()


def dumps(node: Value) -> str:
    """序列化节点树为 JSONC 文本（2 空格缩进，尽量保留注释）。"""
    prefix = "".join(f"// {comment}\n" for comment in node.leading)
    return (prefix + _emit(node, "")).rstrip("\n") + "\n"
