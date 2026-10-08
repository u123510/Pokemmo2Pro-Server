"""OpenMMO 后台管理系统库。

- auth / audit / db: 登录鉴权、操作审计、数据库玩家域
- jsonc_io / paths / backups: JSONC 读写、路径白名单、写前备份
- item_index / map_index: 只读资源索引
- item_repo / gift_repo / move_repo / dex_repo / encounter_repo / event_repo: 内容资源仓库
- shop_repo / custom_npc_repo / map_render: 商店、自定义 NPC、真实地图渲染

校验规则逐条对齐 server.game 的 ShopConfigLoader、ShopNpcBindings、
CustomNpcCodec/Definition/Store/Catalog，错误消息使用中文。
"""
