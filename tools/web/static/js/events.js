/* 事件/节日 NPC 批量管理 */
"use strict";

import { $, api, post, toast } from "./api.js";

export function init() {
  // 数据在 reload 时加载
}

export async function reload() {
  const data = await api("/api/events");
  const wrap = $("event-groups");
  wrap.innerHTML = "";
  for (const group of data.groups) {
    const box = document.createElement("div");
    box.className = "col side-panel";
    box.style.maxWidth = "420px";
    const btnLabel = group.enabled > 0 ? "全部停用" : "全部启用";
    box.innerHTML =
      `<h3>${group.name}（eventId=${group.eventId}）</h3>` +
      `<div class="sub">共 ${group.total} 只，启用 ${group.enabled}</div>`;
    const btn = document.createElement("button");
    btn.textContent = btnLabel;
    btn.className = group.enabled > 0 ? "danger" : "primary";
    btn.onclick = async () => {
      if (!confirm(`确定${btnLabel}分类「${group.name}」的全部 NPC？（需重启游戏服生效）`)) return;
      try {
        const result = await post("/api/events/set-enabled", {
          eventId: group.eventId, enabled: group.enabled === 0,
        });
        toast(result.message);
        reload();
      } catch (error) { toast(error.message, true); }
    };
    box.appendChild(btn);
    const list = document.createElement("ul");
    list.className = "item-list";
    for (const npc of group.npcs) {
      const li = document.createElement("li");
      li.className = npc.enabled ? "" : "off";
      li.textContent = `npc_${npc.entityIdx} @ ${npc.map}`;
      list.appendChild(li);
    }
    box.appendChild(list);
    wrap.appendChild(box);
  }
  if (!data.groups.length) wrap.innerHTML = `<p class="empty">暂无自定义 NPC 配置</p>`;
}
