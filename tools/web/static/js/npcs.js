/* 自定义 NPC 标签页：列表、新建、编辑、停用/启用、地图选点与拖拽保存 */
"use strict";

import { $, api, post, toast } from "./api.js";
import * as MapView from "./mapview.js";

const state = {
  npcs: [],
  currentNpc: null, // relPath
  maps: [],
  creating: false,
};

const REGION_NAMES = { 0: "kanto", 1: "hoenn", 3: "sinnoh" };
const MOVEMENTS = {
  gba: [0, 1, 2, 3, 6, 8, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24],
  nds: [0, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20],
};

export function init(maps) {
  state.maps = maps;
  fillMapSelect();
  fillMovementOptions();
  bindEvents();
}

export async function reload() {
  await loadNpcs();
}

// -- 列表 ---------------------------------------------------------------------

async function loadNpcs() {
  state.npcs = (await api("/api/custom-npcs")).npcs;
  const keyword = $("npc-search").value.trim().toLowerCase();
  const list = $("npc-list");
  list.innerHTML = "";
  for (const npc of state.npcs) {
    if (keyword &&
      !(npc.relPath.toLowerCase().includes(keyword) || String(npc.entityIdx).includes(keyword))) continue;
    const li = document.createElement("li");
    if (npc.relPath === state.currentNpc) li.classList.add("active");
    li.className = npc.enabled ? "" : "off";
    li.innerHTML =
      `<div>npc_${npc.entityIdx} @ ${npc.map}</div>` +
      `<div class="sub">${npc.relPath} · 外观 ${npc.spriteId} · (${npc.x},${npc.y},${npc.z})</div>` +
      (npc.parseError ? `<div class="bad sub">解析失败: ${npc.parseError}</div>` : "") +
      (!npc.enabled ? `<div class="sub">已停用</div>` : "");
    li.onclick = () => openCustomNpc(npc.relPath);
    list.appendChild(li);
  }
}

export async function openCustomNpc(relPath) {
  if (!state.npcs.length) await loadNpcs();
  state.currentNpc = relPath;
  state.creating = false;
  const npc = state.npcs.find((n) => n.relPath === relPath);
  if (!npc) { toast(`找不到自定义 NPC: ${relPath}`, true); return; }
  fillNpcEditor(npc, false);
  $("npc-empty").classList.add("hidden");
  $("npc-editor").classList.remove("hidden");
  document.querySelector('[data-tab="npcs"]').click();
  loadNpcs();
}

// -- 表单 ---------------------------------------------------------------------

function fillMovementOptions() {
  const region = Number($("npc-region").value);
  const list = region === 3 ? MOVEMENTS.nds : MOVEMENTS.gba;
  const sel = $("npc-movement");
  const current = sel.value;
  sel.innerHTML = "";
  for (const value of list) sel.appendChild(new Option(String(value), String(value)));
  if (list.includes(Number(current))) sel.value = current;
}

function fillMapSelect() {
  const region = Number($("npc-region").value);
  const sel = $("npc-map");
  const current = sel.value;
  sel.innerHTML = "";
  const regionName = REGION_NAMES[region];
  for (const m of state.maps.filter((m) => m.region === regionName)) {
    sel.appendChild(new Option(m.name, m.name));
  }
  if (current) sel.value = current;
}

function fillNpcEditor(npc, creating) {
  $("npc-title").textContent = creating ? "新建自定义 NPC" : `编辑 npc_${npc.entityIdx}`;
  $("npc-region").value = String(npc.regionId);
  fillMapSelect();
  $("npc-map").value = npc.map;
  $("npc-idx").value = npc.entityIdx ?? "";
  $("npc-sprite").value = npc.spriteId ?? 0;
  $("npc-sprite-region").value = String(npc.spriteRegion ?? 0);
  fillMovementOptions();
  $("npc-movement").value = String(npc.movementType ?? 0);
  $("npc-leash-x").value = npc.leashX ?? 0;
  $("npc-leash-y").value = npc.leashY ?? 0;
  $("npc-toward").value = String(npc.toward ?? 0);
  $("npc-x").value = npc.x ?? "";
  $("npc-y").value = npc.y ?? "";
  $("npc-z").value = npc.z ?? 0;
  $("npc-enabled").checked = creating ? true : Boolean(npc.enabled);
  $("npc-version").value = String(npc.version ?? 1);
  $("npc-version").dispatchEvent(new Event("change"));
  $("npc-event-id").value = npc.eventId ?? -1;
  $("npc-sparkles").checked = Boolean(npc.sparkles);
  $("npc-scale").value = npc.spriteScale ?? 1;
  $("npc-errors").textContent = "";
  $("npc-toggle").textContent = npc.enabled && !creating ? "停用" : "启用";
  $("npc-toggle").classList.toggle("hidden", creating);
}

function collectNpc() {
  const version = Number($("npc-version").value);
  const data = {
    version,
    enabled: $("npc-enabled").checked,
    map: $("npc-map").value,
    regionId: Number($("npc-region").value),
    entityIdx: Number($("npc-idx").value),
    spriteId: Number($("npc-sprite").value),
    spriteRegion: Number($("npc-sprite-region").value),
    movementType: Number($("npc-movement").value),
    leashX: Number($("npc-leash-x").value),
    leashY: Number($("npc-leash-y").value),
    x: Number($("npc-x").value),
    y: Number($("npc-y").value),
    z: Number($("npc-z").value),
    toward: Number($("npc-toward").value),
  };
  if (version === 2) {
    data.eventId = Number($("npc-event-id").value);
    data.sparkles = $("npc-sparkles").checked;
    data.spriteScale = Number($("npc-scale").value);
  }
  return data;
}

async function saveNpc() {
  const data = collectNpc();
  $("npc-errors").textContent = "";
  try {
    const check = await post("/api/custom-npc/validate", {
      data, create: state.creating, relPath: state.currentNpc || "",
    });
    if (check.errors.length) {
      $("npc-errors").textContent = check.errors.join("\n");
      toast("校验未通过，未保存", true);
      return;
    }
    const body = { data };
    if (state.creating) {
      body.create = true;
    } else {
      body.relPath = state.currentNpc;
    }
    const result = await post("/api/custom-npc/save", body);
    toast(result.message);
    if (state.creating) {
      state.creating = false;
      state.currentNpc = result.relPath;
    }
    await loadNpcs();
  } catch (error) {
    $("npc-errors").textContent = error.message;
    toast("保存失败", true);
  }
}

async function toggleNpc() {
  if (state.creating || !state.currentNpc) return;
  const npc = state.npcs.find((n) => n.relPath === state.currentNpc);
  if (!npc) return;
  await toggleByPath(npc.relPath, !npc.enabled);
  const fresh = state.npcs.find((n) => n.relPath === state.currentNpc);
  if (fresh) fillNpcEditor(fresh, false);
}

export async function toggleByPath(relPath, enabled) {
  try {
    const result = await post("/api/custom-npc/toggle", { relPath, enabled });
    toast(result.message);
    await loadNpcs();
    MapView.refresh();
  } catch (error) { toast(error.message, true); }
}

// -- 新建 / 拖拽移动 -------------------------------------------------------------

export async function newNpcAt(x, y) {
  // 右键菜单入口：以当前浏览的地图与点击格预填新建表单
  const current = MapView.currentMap();
  if (!current) { toast("请先在地图浏览中打开一张地图", true); return; }
  const regionNameToId = Object.fromEntries(Object.entries(REGION_NAMES).map(([id, name]) => [name, Number(id)]));
  const regionId = regionNameToId[current.region] ?? 0;
  let entityIdx = 100000;
  try {
    const result = await api(`/api/next-entity-idx?regionId=${regionId}&map=${encodeURIComponent(current.name)}`);
    entityIdx = result.entityIdx;
  } catch (_e) { /* 保底 100000 */ }
  state.creating = true;
  state.currentNpc = null;
  fillNpcEditor({
    regionId, map: current.name, entityIdx, version: 1, enabled: true,
    spriteId: 0, spriteRegion: 0, movementType: 0, leashX: 0, leashY: 0,
    x, y, z: 0, toward: 2,
  }, true);
  $("npc-empty").classList.add("hidden");
  $("npc-editor").classList.remove("hidden");
  document.querySelector('[data-tab="npcs"]').click();
  toast(`在 ${current.name} (${x},${y}) 新建 NPC：填好外观后点「保存」`);
}

export async function moveNpc(relPath, x, y) {
  // 拖拽落点：校验通过即保存，返回 true 让地图刷新
  const npc = state.npcs.find((n) => n.relPath === relPath);
  if (!npc) { toast("NPC 数据未加载", true); return false; }
  const data = { ...npc, x, y };
  try {
    const check = await post("/api/custom-npc/validate", { data, create: false, relPath });
    if (check.errors.length) {
      toast(`移动被拒绝：${check.errors[0]}`, true);
      return false;
    }
    const result = await post("/api/custom-npc/save", { data, relPath });
    toast(result.message);
    await loadNpcs();
    return true;
  } catch (error) {
    toast(`移动失败: ${error.message}`, true);
    return false;
  }
}

export function useAsSpawn(x, y) {
  $("npc-x").value = x;
  $("npc-y").value = y;
  document.querySelector('[data-tab="npcs"]').click();
  toast(`已填入出生点 (${x}, ${y})，请确认 Z/朝向后保存`);
}

// -- 事件绑定 -------------------------------------------------------------------

function bindEvents() {
  $("npc-search").addEventListener("input", loadNpcs);
  $("npc-new").addEventListener("click", () => newNpcPanel());
  $("npc-region").addEventListener("change", () => { fillMapSelect(); fillMovementOptions(); });
  $("npc-version").addEventListener("change", (e) => {
    $("npc-advanced").classList.toggle("hidden", e.target.value !== "2");
  });
  $("npc-idx-auto").addEventListener("click", async () => {
    const regionId = Number($("npc-region").value);
    const mapName = $("npc-map").value;
    try {
      const result = await api(`/api/next-entity-idx?regionId=${regionId}&map=${encodeURIComponent(mapName)}`);
      $("npc-idx").value = result.entityIdx;
    } catch (error) { toast(error.message, true); }
  });
  $("npc-pick").addEventListener("click", () => {
    const region = REGION_NAMES[Number($("npc-region").value)];
    MapView.pick((tile) => useAsSpawn(tile.x, tile.y),
      `选点模式：为 ${$("npc-map").value || "目标地图"} 选择出生格`);
    MapView.open(region, $("npc-map").value);
  });
  $("npc-save").addEventListener("click", saveNpc);
  $("npc-toggle").addEventListener("click", toggleNpc);
}

async function newNpcPanel() {
  let regionId = 0;
  let mapName = "";
  if (state.maps.length) {
    const first = state.maps.find((m) => m.region === "kanto") || state.maps[0];
    regionId = first.regionId ?? 0;
    mapName = first.name;
  }
  let entityIdx = 100000;
  try {
    const result = await api(`/api/next-entity-idx?regionId=${regionId}&map=${encodeURIComponent(mapName)}`);
    entityIdx = result.entityIdx;
  } catch (_e) { /* 保底 100000 */ }
  state.creating = true;
  state.currentNpc = null;
  fillNpcEditor({
    regionId, map: mapName, entityIdx, version: 1, enabled: true,
    spriteId: 0, spriteRegion: 0, movementType: 0, leashX: 0, leashY: 0,
    x: 0, y: 0, z: 0, toward: 2,
  }, true);
  $("npc-empty").classList.add("hidden");
  $("npc-editor").classList.remove("hidden");
  loadNpcs();
}
