"""npc_admin 自测：只读检查 + 沙箱写回测试（不触碰真实资源文件）。

用法（项目根目录）：
    python tools/web/selftest.py
"""

from __future__ import annotations

import shutil
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from admin_lib import custom_npc_repo, jsonc_io, map_index, map_render, paths, shop_repo

PASSED = 0
FAILED = 0


def check(name: str, condition: bool, detail: str = "") -> None:
    global PASSED, FAILED
    if condition:
        PASSED += 1
        print(f"  通过: {name}")
    else:
        FAILED += 1
        print(f"  失败: {name} {detail}")


def sandbox(monkey_dirs: dict) -> None:
    pass


def main() -> None:
    print("== 1. JSONC 注释保留往返 ==")
    shop_files = sorted((paths.SHOP_DIR).rglob("*.jsonc"))
    check("存在商店 jsonc 文件", len(shop_files) >= 1)
    for file in shop_files:
        text = file.read_text(encoding="utf-8")
        node = jsonc_io.loads(text)
        round_text = jsonc_io.dumps(node)
        again = jsonc_io.dumps(jsonc_io.loads(round_text))
        comments_before = text.count("//")
        comments_after = round_text.count("//")
        check(f"{file.name} 数据稳定", round_text == again)
        check(f"{file.name} 注释保留({comments_before}->{comments_after})",
              comments_after >= comments_before)

    print("== 2. 只读索引 ==")
    maps = map_index.all_maps()
    check("地图索引非空", len(maps) > 300, f"实际 {len(maps)}")
    pewter = map_index.get("kanto", "PewterCity_Mart")
    check("PewterCity_Mart 存在", pewter is not None)
    check("尼比商店店员为 2 号", any(n["entityIdx"] == 2 for n in pewter["npcs"]))
    grid = map_index.block_grid(pewter)
    check("碰撞网格尺寸一致", len(grid) == pewter["height"] and len(grid[0]) == pewter["width"])

    print("== 3. 商店校验 ==")
    shops = shop_repo.list_shops()
    check("商店列表非空", len(shops) >= 2)
    good = next(s for s in shops if s["relPath"].endswith("pewter_city/mart_clerk.jsonc"))
    _node, data, file = shop_repo.load_doc(good["relPath"])
    errors = shop_repo.validate(data, self_path=file.resolve().as_posix())
    check("现有尼比商店校验通过", not errors, str(errors))
    bad = dict(data)
    bad["npcs"] = [{"map": "NoSuchMap", "entityIdx": 2}]
    bad["items"] = [{"itemId": 999999, "buyPrice": 1, "sellPrice": None}]
    errors = shop_repo.validate(bad, self_path=file.resolve().as_posix())
    check("非法绑定/道具被拒绝", len(errors) >= 2, str(errors))

    print("== 4. 沙箱写回（temp 目录，不触碰真实资源） ==")
    temp = Path(tempfile.mkdtemp(prefix="npc_admin_selftest_"))
    try:
        old_shop = paths.SHOP_DIR
        old_custom = paths.CUSTOM_NPC_DIR
        old_writable = paths.WRITABLE_DIRS
        paths.SHOP_DIR = temp / "shop"
        paths.CUSTOM_NPC_DIR = temp / "custom"
        paths.WRITABLE_DIRS = (paths.SHOP_DIR, paths.CUSTOM_NPC_DIR)
        shop_repo.SHOP_DIR = paths.SHOP_DIR
        custom_npc_repo.CUSTOM_NPC_DIR = paths.CUSTOM_NPC_DIR
        (paths.SHOP_DIR / "kanto" / "test_city").mkdir(parents=True)
        (paths.CUSTOM_NPC_DIR / "kanto" / "TestMap").mkdir(parents=True)

        created = shop_repo.create(
            "kanto/test_city/test.jsonc",
            {"shopId": "kanto.test_city.test", "buyEnabled": True, "sellEnabled": True,
             "items": [{"itemId": 5004, "buyPrice": 200, "sellPrice": 100}],
             "npcs": [{"map": "PewterCity_Mart", "entityIdx": 2}]})
        check("创建店铺成功", created.exists())
        node, data2, _ = shop_repo.load_doc("kanto/test_city/test.jsonc")
        data2["items"].append({"itemId": 5017, "buyPrice": None, "sellPrice": 50})
        shop_repo.save("kanto/test_city/test.jsonc", data2)
        _n, data3, _ = shop_repo.load_doc("kanto/test_city/test.jsonc")
        check("追加商品成功", len(data3["items"]) == 2 and data3["items"][0]["buyPrice"] == 200)

        dup = dict(data3)
        dup["shopId"] = "kanto.test_city.test"
        errors = shop_repo.validate(dup, self_path="other")
        check("shopId 重复被拒绝", any("重复" in e for e in errors), str(errors))

        # 自定义 NPC 测试放在真实地图上，自动挑一个可站立且无 NPC 的格子
        info = map_index.get("kanto", "PewterCity_Mart")
        grid = map_index.block_grid(info)
        occupied = map_index.occupied_cells(info)
        free = next(
            (x, y)
            for y in range(info["height"]) for x in range(info["width"])
            if grid[y][x]["collision"] == 0 and (x, y) not in occupied
        )
        npc_def = {"version": 1, "enabled": True, "map": "PewterCity_Mart", "regionId": 0,
                   "entityIdx": 100000, "spriteId": 68, "spriteRegion": 0,
                   "movementType": 0, "leashX": 0, "leashY": 0,
                   "x": free[0], "y": free[1], "z": 0, "toward": 2}
        file_npc = custom_npc_repo.save_new(npc_def)
        check("创建自定义 NPC 成功", file_npc.exists())
        check("文件名符合约定", file_npc.name == "npc_100000.jsonc")
        server_text = jsonc_io.dumps(jsonc_io.loads(file_npc.read_text(encoding="utf-8")))
        check("字段顺序与服务端一致", server_text.index('"version"') < server_text.index('"toward"'))

        bad_npc = dict(npc_def, entityIdx=99999)
        errors = custom_npc_repo.validate_full(bad_npc, for_create=True)
        check("低序号被拒绝", any("100000" in e for e in errors))
        bad_npc = dict(npc_def, entityIdx=100001, movementType=99)
        errors = custom_npc_repo.validate_full(bad_npc, for_create=True)
        check("非法移动类型被拒绝", any("移动类型" in e for e in errors))
        overlap = dict(npc_def, entityIdx=100002, x=2, y=3)
        errors = custom_npc_repo.validate_full(overlap, for_create=True)
        check("出生格重叠被拒绝", any("重叠" in e for e in errors), str(errors))

        nxt = custom_npc_repo.next_entity_idx(0, "PewterCity_Mart")
        check("序号自动分配为 100001", nxt == 100001, f"实际 {nxt}")
        custom_npc_repo.set_enabled(file_npc.relative_to(paths.CUSTOM_NPC_DIR).as_posix(), False)
        reloaded = custom_npc_repo.list_all()[0]
        check("停用成功", reloaded["enabled"] is False)

        print("== 5. 地图渲染 ==")
        path_png, mode = map_render.render_map("kanto", "PewterCity_Mart", scale=2)
        check("关都真实渲染", mode == "real" and path_png.exists())
        cached, _ = map_render.render_map("kanto", "PewterCity_Mart", scale=2)
        check("缓存命中", cached == path_png)
    finally:
        paths.SHOP_DIR = old_shop
        paths.CUSTOM_NPC_DIR = old_custom
        paths.WRITABLE_DIRS = old_writable
        shop_repo.SHOP_DIR = old_shop
        custom_npc_repo.CUSTOM_NPC_DIR = old_custom
        shutil.rmtree(temp, ignore_errors=True)

    print(f"\n结果: {PASSED} 通过, {FAILED} 失败")
    sys.exit(1 if FAILED else 0)


if __name__ == "__main__":
    main()
