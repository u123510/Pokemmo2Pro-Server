/* 商店管理标签页：列表、编辑、商品搜索选择、店员绑定 */
"use strict";

import { $, api, post, toast } from "./api.js";

const state = {
  shops: [],
  currentShop: null, // relPath
  maps: [],
  itemCache: new Map(),
  creating: false,
};

export function init(maps) {
  state.maps = maps;
  bindEvents();
}

export async function reload() {
  await loadShops();
}

// -- 店铺列表 -----------------------------------------------------------------

async function loadShops() {
  state.shops = (await api("/api/shops")).shops;
  const keyword = $("shop-search").value.trim().toLowerCase();
  const list = $("shop-list");
  list.innerHTML = "";
  for (const shop of state.shops) {
    if (keyword &&
      !(shop.relPath.toLowerCase().includes(keyword) ||
        String(shop.shopId || "").toLowerCase().includes(keyword))) continue;
    const li = document.createElement("li");
    if (shop.relPath === state.currentShop) li.classList.add("active");
    const clerk = shop.npcs && shop.npcs.length
      ? shop.npcs.map((n) => `${n.map}/${n.entityIdx}`).join(", ")
      : (shop.hasNpcsField ? "无绑定" : "地图旧绑定");
    li.innerHTML =
      `<div>${shop.shopId || "?"}</div>` +
      `<div class="sub">${shop.relPath} · ${shop.itemCount ?? "?"} 商品 · ${clerk}</div>` +
      (shop.parseError ? `<div class="bad sub">解析失败: ${shop.parseError}</div>` : "");
    li.onclick = () => openShop(shop.relPath);
    list.appendChild(li);
  }
}

export async function openShop(relPath) {
  state.currentShop = relPath;
  state.creating = false;
  const data = await api(`/api/shop?relPath=${encodeURIComponent(relPath)}`);
  fillShopEditor(relPath, data.data, data.itemNames, data.errors);
  $("shop-empty").classList.add("hidden");
  $("shop-create-panel").classList.add("hidden");
  $("shop-editor").classList.remove("hidden");
  loadShops();
}

// -- 道具搜索选择器 -------------------------------------------------------------

let pickerPanel = null;
let pickerActive = null;

function closePicker() {
  if (pickerPanel) {
    pickerPanel.remove();
    pickerPanel = null;
    pickerActive = null;
  }
}

function displayText(id, name) {
  return id ? `${id}  ${name || ""}` : "";
}

async function lookupItem(id, cell, searchInput) {
  const found = await api(`/api/items?q=${id}`);
  if (found.items.length && found.items[0].itemId === id) {
    state.itemCache.set(id, found.items[0].name);
    cell.textContent = found.items[0].name;
    if (searchInput) searchInput.placeholder = displayText(id, found.items[0].name);
  }
}

function openItemPicker(search, hidden, nameCell, refresh) {
  pickerActive = { search, hidden, nameCell, refresh };
  if (!pickerPanel) {
    pickerPanel = document.createElement("div");
    pickerPanel.className = "item-picker";
    document.body.appendChild(pickerPanel);
    pickerPanel.addEventListener("pointerdown", (e) => e.preventDefault());
  }
  const rect = search.getBoundingClientRect();
  pickerPanel.style.left = `${rect.left + window.scrollX}px`;
  pickerPanel.style.top = `${rect.bottom + window.scrollY}px`;
  pickerPanel.style.width = `${Math.max(rect.width, 340)}px`;
  const query = search.value.replace(/^\d+\s+/, "").trim();
  api(`/api/items?q=${encodeURIComponent(query)}`).then((data) => {
    if (!pickerActive || pickerActive.search !== search) return;
    pickerPanel.innerHTML = "";
    if (!data.items.length) pickerPanel.innerHTML = `<div class="picker-none">没有匹配的道具</div>`;
    for (const item of data.items) {
      const opt = document.createElement("div");
      opt.className = "picker-row";
      opt.innerHTML = `<span class="picker-id">${item.itemId}</span><span>${item.name}</span>`;
      opt.addEventListener("pointerdown", (e) => e.preventDefault());
      opt.addEventListener("click", () => {
        state.itemCache.set(item.itemId, item.name);
        hidden.value = item.itemId;
        nameCell.textContent = item.name;
        search.value = displayText(item.itemId, item.name);
        closePicker();
        refresh();
      });
      pickerPanel.appendChild(opt);
    }
    pickerPanel.classList.add("open");
  }).catch((error) => toast(error.message, true));
}

function itemRow(item) {
  const tr = document.createElement("tr");
  tr.innerHTML =
    `<td><input class="i-search" type="text" autocomplete="off" spellcheck="false" placeholder="编号或名称搜索">` +
    `<input class="i-id" type="hidden" value="${item.itemId}"></td>` +
    `<td class="item-name"></td>` +
    `<td><input class="i-buy" type="number" min="0" value="${item.buyPrice ?? ""}" placeholder="null"></td>` +
    `<td><input class="i-sell" type="number" min="0" value="${item.sellPrice ?? ""}" placeholder="null"></td>` +
    `<td><button class="i-del danger">删</button></td>`;
  const search = tr.querySelector(".i-search");
  const hidden = tr.querySelector(".i-id");
  const nameCell = tr.querySelector(".item-name");
  const refresh = () => {
    const id = Number(hidden.value);
    if (!id) {
      nameCell.textContent = "";
      search.placeholder = "编号或名称搜索";
      return;
    }
    if (state.itemCache.has(id)) {
      nameCell.textContent = state.itemCache.get(id);
      search.value = displayText(id, state.itemCache.get(id));
      search.placeholder = search.value;
    } else {
      lookupItem(id, nameCell, search);
    }
  };
  search.addEventListener("focus", () => {
    search.select();
    openItemPicker(search, hidden, nameCell, refresh);
  });
  search.addEventListener("input", () => openItemPicker(search, hidden, nameCell, refresh));
  search.addEventListener("blur", () => setTimeout(() => {
    if (pickerActive && pickerActive.search === search) closePicker();
    refresh();
  }, 150));
  tr.querySelector(".i-del").onclick = () => { tr.remove(); closePicker(); };
  refresh();
  return tr;
}

document.addEventListener("pointerdown", (event) => {
  if (pickerPanel && !pickerPanel.contains(event.target) &&
    (!pickerActive || event.target !== pickerActive.search)) {
    closePicker();
  }
});

// -- 店员绑定 -------------------------------------------------------------------

function bindingRows(npcs) {
  const tbody = $("shop-npcs").querySelector("tbody");
  tbody.innerHTML = "";
  for (const npc of npcs || []) addBindingRow(npc);
}

function addBindingRow(npc) {
  const tbody = $("shop-npcs").querySelector("tbody");
  const tr = document.createElement("tr");
  tr.innerHTML =
    `<td><select class="b-region"></select></td>` +
    `<td><select class="b-map"></select></td>` +
    `<td><select class="b-idx"></select></td>` +
    `<td><button class="b-del danger">删</button></td>`;
  const regionSel = tr.querySelector(".b-region");
  const mapSel = tr.querySelector(".b-map");
  const idxSel = tr.querySelector(".b-idx");
  for (const region of new Set(state.maps.map((m) => m.region))) {
    regionSel.appendChild(new Option(region, region));
  }
  const fillIdx = async () => {
    idxSel.innerHTML = "";
    try {
      const detail = await api(`/api/map?region=${encodeURIComponent(regionSel.value)}&name=${encodeURIComponent(mapSel.value)}`);
      for (const n of detail.npcs) idxSel.appendChild(new Option(`${n.entityIdx}（原生）`, n.entityIdx));
      for (const n of detail.customs || []) {
        if (!n.enabled) continue;
        idxSel.appendChild(new Option(`${n.entityIdx}（自定义）`, n.entityIdx));
      }
      if (npc && idxSel.value !== String(npc.entityIdx)) {
        idxSel.appendChild(new Option(`${npc.entityIdx}`, npc.entityIdx));
        idxSel.value = npc.entityIdx;
      }
    } catch (error) { toast(error.message, true); }
  };
  const fillMaps = async () => {
    mapSel.innerHTML = "";
    for (const m of state.maps.filter((m) => m.region === regionSel.value)) {
      mapSel.appendChild(new Option(m.name, m.name));
    }
    if (npc) mapSel.value = npc.map;
    await fillIdx();
  };
  regionSel.onchange = () => { npc = null; fillMaps(); };
  mapSel.onchange = fillIdx;
  if (npc) {
    regionSel.value = state.maps.find((m) => m.name === npc.map)?.region || regionSel.value;
  }
  fillMaps();
  tr.querySelector(".b-del").onclick = () => tr.remove();
  tbody.appendChild(tr);
}

// -- 编辑与保存 -----------------------------------------------------------------

function fillShopEditor(relPath, data, names, errors) {
  state.currentShop = relPath;
  $("shop-title").textContent = "编辑店铺";
  $("shop-id").value = data.shopId || "";
  $("shop-file").textContent = relPath;
  $("shop-buy").checked = Boolean(data.buyEnabled);
  $("shop-sell").checked = Boolean(data.sellEnabled);
  for (const [id, name] of Object.entries(names || {})) {
    if (name) state.itemCache.set(Number(id), name);
  }
  const itemsBody = $("shop-items").querySelector("tbody");
  itemsBody.innerHTML = "";
  for (const item of data.items || []) itemsBody.appendChild(itemRow(item));
  const hasField = "npcs" in data;
  const mode = !hasField ? "none" : (data.npcs && data.npcs.length ? "list" : "empty");
  document.querySelector(`input[name=bind-mode][value=${mode}]`).checked = true;
  bindingRows(mode === "list" ? data.npcs : []);
  $("shop-errors").textContent = (errors || []).join("\n");
}

function collectShop() {
  const mode = document.querySelector("input[name=bind-mode]:checked").value;
  const items = [];
  for (const tr of $("shop-items").querySelectorAll("tbody tr")) {
    const buy = tr.querySelector(".i-buy").value;
    const sell = tr.querySelector(".i-sell").value;
    items.push({
      itemId: Number(tr.querySelector(".i-id").value),
      buyPrice: buy === "" ? null : Number(buy),
      sellPrice: sell === "" ? null : Number(sell),
    });
  }
  const data = {
    shopId: $("shop-id").value.trim(),
    buyEnabled: $("shop-buy").checked,
    sellEnabled: $("shop-sell").checked,
    items,
  };
  if (mode === "list") {
    data.npcs = [];
    for (const tr of $("shop-npcs").querySelectorAll("tbody tr")) {
      data.npcs.push({
        map: tr.querySelector(".b-map").value,
        entityIdx: Number(tr.querySelector(".b-idx").value),
      });
    }
  } else if (mode === "empty") {
    data.npcs = [];
  }
  return data;
}

async function saveShop() {
  if (state.currentShop == null) return;
  const data = collectShop();
  $("shop-errors").textContent = "";
  try {
    const check = await post("/api/shop/validate", { relPath: state.currentShop, data });
    if (check.errors.length) {
      $("shop-errors").textContent = check.errors.join("\n");
      toast("校验未通过，未保存", true);
      return;
    }
    const result = await post("/api/shop/save", { relPath: state.currentShop, data });
    toast(result.message);
    loadShops();
  } catch (error) {
    $("shop-errors").textContent = error.message;
    toast("保存失败", true);
  }
}

async function validateShop() {
  const data = collectShop();
  try {
    const result = await post("/api/shop/validate", { relPath: state.currentShop, data });
    $("shop-errors").textContent = result.errors.length ? result.errors.join("\n") : "校验通过，可以保存。";
  } catch (error) {
    $("shop-errors").textContent = error.message;
  }
}

async function deleteShop() {
  if (!state.currentShop || !confirm(`确定删除店铺 ${state.currentShop}？原文件会移入备份目录。`)) return;
  try {
    const result = await post("/api/shop/delete", { relPath: state.currentShop });
    toast(result.message);
    state.currentShop = null;
    $("shop-editor").classList.add("hidden");
    $("shop-empty").classList.remove("hidden");
    loadShops();
  } catch (error) { toast(error.message, true); }
}

function showCreatePanel() {
  state.creating = true;
  $("shop-editor").classList.add("hidden");
  $("shop-empty").classList.add("hidden");
  $("shop-create-panel").classList.remove("hidden");
}

async function createShop() {
  const dir = $("shop-create-dir").value.trim().replace(/\\/g, "/").replace(/^\/+|\/+$/g, "");
  const name = $("shop-create-name").value.trim();
  if (!dir || !name) { toast("请填写子目录和文件名", true); return; }
  const data = {
    shopId: `${dir.replace(/\//g, ".")}.${name.replace(/\.(json|jsonc)$/, "")}`,
    buyEnabled: true,
    sellEnabled: true,
    items: [],
    npcs: [],
  };
  try {
    const result = await post("/api/shop/create", { relPath: `${dir}/${name}`, data });
    toast(result.message);
    await loadShops();
    openShop(result.relPath);
  } catch (error) { toast(error.message, true); }
}

// -- 跨标签入口 -----------------------------------------------------------------

export function useAsClerk(mapName, entityIdx) {
  document.querySelector('[data-tab="shops"]').click();
  if (!state.currentShop) { toast("请先在左侧选择要编辑的店铺", true); return; }
  document.querySelector("input[name=bind-mode][value=list]").checked = true;
  addBindingRow({ map: mapName, entityIdx });
  toast(`已添加店员绑定 ${mapName}/${entityIdx}，保存后执行 //reloadshops`);
}

// -- 事件绑定 -----------------------------------------------------------------

function bindEvents() {
  $("shop-search").addEventListener("input", loadShops);
  $("shop-new").addEventListener("click", showCreatePanel);
  $("shop-create-cancel").addEventListener("click", () => {
    state.creating = false;
    $("shop-create-panel").classList.add("hidden");
    $("shop-empty").classList.remove("hidden");
  });
  $("shop-create-ok").addEventListener("click", createShop);
  $("shop-add-item").addEventListener("click", () => {
    const tbody = $("shop-items").querySelector("tbody");
    tbody.appendChild(itemRow({ itemId: 0, buyPrice: null, sellPrice: null }));
  });
  $("shop-add-npc").addEventListener("click", () => {
    document.querySelector("input[name=bind-mode][value=list]").checked = true;
    addBindingRow(null);
  });
  $("shop-save").addEventListener("click", saveShop);
  $("shop-validate").addEventListener("click", validateShop);
  $("shop-delete").addEventListener("click", deleteShop);
}
