/* 道具字典（Item.jsonc 显示层字段 + 引用扫描） */
"use strict";

import { $, api, post, toast } from "./api.js";

let selected = null;

export function init() {
  bindEvents();
  loadList();
}

async function loadList() {
  const q = $("dict-search").value.trim();
  const data = await api(`/api/dict/items?q=${encodeURIComponent(q)}`);
  const tbody = $("dict-table").querySelector("tbody");
  tbody.innerHTML = "";
  for (const row of data.items) {
    const tr = document.createElement("tr");
    tr.innerHTML =
      `<td>${row.itemIndexId}</td><td>${row.name || ""}</td>` +
      `<td>${row.regionIndexId ?? ""}</td><td>${row.iconId ?? ""}</td>`;
    const ops = document.createElement("td");
    const btn = document.createElement("button");
    btn.textContent = "编辑";
    btn.onclick = () => openItem(row.itemIndexId);
    ops.appendChild(btn);
    tr.appendChild(ops);
    tbody.appendChild(tr);
  }
}

async function openItem(itemId) {
  try {
    const data = await api(`/api/dict/item?itemId=${itemId}`);
    selected = itemId;
    const item = data.item;
    $("dict-title").textContent = `道具 ${itemId}`;
    $("dict-name").value = item.name || "";
    $("dict-desc").value = item.desc || "";
    $("dict-region").value = item.regionIndexId ?? 0;
    $("dict-icon").value = item.iconId ?? 0;
    $("dict-name-str").value = item.nameLocalStringIndexId ?? 0;
    $("dict-desc-str").value = item.descLocalStringIndexId ?? 0;
    const refs = $("dict-refs");
    refs.innerHTML = data.refs.length
      ? `<b>被 ${data.refs.length} 处引用：</b>` + data.refs.map((r) =>
        `<div class="sub">${r.file} → ${r.where}</div>`).join("")
      : `<div class="sub">资源文件中无引用</div>`;
    $("dict-panel").classList.remove("hidden");
  } catch (error) { toast(error.message, true); }
}

async function saveItem() {
  if (!selected) return;
  try {
    const result = await post("/api/dict/item/save", {
      itemId: selected,
      changes: {
        name: $("dict-name").value,
        desc: $("dict-desc").value,
        regionIndexId: Number($("dict-region").value),
        iconId: Number($("dict-icon").value),
        nameLocalStringIndexId: Number($("dict-name-str").value),
        descLocalStringIndexId: Number($("dict-desc-str").value),
      },
    });
    toast(result.message);
    loadList();
  } catch (error) { toast(error.message, true); }
}

function bindEvents() {
  $("dict-search").addEventListener("input", loadList);
  $("dict-save").addEventListener("click", saveItem);
  $("dict-delete").addEventListener("click", async () => {
    if (!selected || !confirm(`确定删除道具 ${selected}？`)) return;
    try {
      const result = await post("/api/dict/item/delete", { itemId: selected });
      toast(result.message);
      $("dict-panel").classList.add("hidden");
      loadList();
    } catch (error) { toast(error.message, true); }
  });
  $("dict-create").addEventListener("click", async () => {
    const input = prompt("新道具编号（注意：服务端生效还需要 Item.bin 中存在）：");
    if (!input || !/^\d+$/.test(input)) return;
    const name = prompt("道具名称：", "");
    if (name === null) return;
    try {
      const result = await post("/api/dict/item/create", {
        itemId: Number(input), fields: { name, desc: "" },
      });
      toast(result.message);
      loadList();
      openItem(Number(input));
    } catch (error) { toast(error.message, true); }
  });
}
