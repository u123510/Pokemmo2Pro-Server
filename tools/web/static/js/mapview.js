/* 地图浏览：真实渲染图 + 标记层 + 右键菜单 + 拖拽移动自定义 NPC */
"use strict";

import { $, api, toast } from "./api.js";

const state = {
  region: null,
  name: null,
  detail: null,
  tilePx: 32,
  collisionOn: false,
  warpsOn: true,
  countersOn: true,
  pickHandler: null,
  collision: null,
  allMaps: [],
  handlers: {}, // app.js 注入: onUseAsSpawn/onUseAsClerk/onEditNpc/onToggleNpc/onNewNpcAt/onMoveNpc
  drag: null,   // {relPath, entityIdx, moved}
};

export function setHandlers(handlers) {
  state.handlers = handlers;
}

async function loadRegions() {
  const data = await api("/api/maps");
  const regionSelect = $("map-region");
  regionSelect.innerHTML = "";
  for (const [rid, name] of Object.entries(data.regions)) {
    const count = data.maps.filter((m) => m.region === name).length;
    if (!count) continue;
    const opt = document.createElement("option");
    opt.value = name;
    opt.textContent = `${name} (${count})`;
    regionSelect.appendChild(opt);
  }
  state.allMaps = data.maps;
  fillMapOptions();
}

function fillMapOptions() {
  const region = $("map-region").value;
  const keyword = $("map-search").value.trim().toLowerCase();
  const select = $("map-select");
  select.innerHTML = "";
  for (const m of state.allMaps) {
    if (m.region !== region) continue;
    if (keyword && !m.name.toLowerCase().includes(keyword)) continue;
    const opt = document.createElement("option");
    opt.value = m.name;
    opt.textContent = `${m.name} (${m.npcs.length} NPC${m.customCount ? "+" + m.customCount : ""})`;
    select.appendChild(opt);
  }
}

export async function open(region, name) {
  state.region = region;
  state.name = name;
  state.detail = await api(`/api/map?region=${encodeURIComponent(region)}&name=${encodeURIComponent(name)}`);
  state.collision = null;
  if (state.detail.collisionB64) {
    const bin = atob(state.detail.collisionB64);
    state.collision = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) state.collision[i] = bin.charCodeAt(i);
  }
  await renderBase();
  drawMarkers();
  hideInfo();
}

function renderBase() {
  return new Promise((resolve, reject) => {
    const img = document.createElement("img");
    img.className = "base";
    img.onload = () => {
      state.tilePx = img.naturalWidth / state.detail.width;
      const layers = $("map-layers");
      layers.innerHTML = "";
      layers.appendChild(img);
      const canvas = document.createElement("canvas");
      canvas.width = img.naturalWidth;
      canvas.height = img.naturalHeight;
      layers.appendChild(canvas);
      state.canvas = canvas;
      resolve();
    };
    img.onerror = () => reject(new Error("地图渲染失败"));
    img.src =
      `/api/map/image?region=${encodeURIComponent(state.region)}` +
      `&name=${encodeURIComponent(state.name)}&scale=2`;
  });
}

function customAt(x, y) {
  return (state.detail.customs || []).find((n) => n.x === x && n.y === y) || null;
}

function customAtOrigin(relPath) {
  return (state.detail.customs || []).find((n) => n.relPath === relPath) || null;
}

function drawMarkers() {
  const canvas = state.canvas;
  if (!canvas) return;
  const ctx = canvas.getContext("2d");
  const t = state.tilePx;
  const d = state.detail;
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  if (state.collisionOn && state.collision) {
    ctx.fillStyle = "rgba(220,40,40,0.38)";
    for (let y = 0; y < d.height; y++) {
      for (let x = 0; x < d.width; x++) {
        if (state.collision[y * d.width + x]) ctx.fillRect(x * t, y * t, t, t);
      }
    }
  }
  if (state.countersOn) {
    ctx.strokeStyle = "#ff8c00";
    ctx.lineWidth = 3;
    for (const c of d.counters) ctx.strokeRect(c.x * t + 1, c.y * t + 1, t - 2, t - 2);
  }
  if (state.warpsOn) {
    ctx.fillStyle = "rgba(140,60,220,0.9)";
    for (const w of d.warps) {
      ctx.beginPath();
      ctx.arc(w.x * t + t / 2, w.y * t + t / 2, t * 0.28, 0, Math.PI * 2);
      ctx.fill();
    }
  }
  ctx.lineWidth = 2;
  ctx.font = `${Math.max(10, Math.floor(t * 0.32))}px sans-serif`;
  ctx.textBaseline = "top";
  for (const npc of d.npcs) {
    ctx.strokeStyle = "rgba(30,90,255,0.95)";
    ctx.strokeRect(npc.x * t + 1, npc.y * t + 1, t - 2, t - 2);
    ctx.fillStyle = "rgba(30,90,255,0.85)";
    ctx.fillText(String(npc.entityIdx), npc.x * t + 2, npc.y * t + t + 1);
  }
  for (const npc of d.customs || []) {
    if (state.drag && state.drag.relPath === npc.relPath) continue;
    ctx.strokeStyle = npc.enabled ? "rgba(20,150,60,0.95)" : "rgba(120,130,120,0.8)";
    ctx.strokeRect(npc.x * t + 1, npc.y * t + 1, t - 2, t - 2);
    ctx.fillStyle = npc.enabled ? "rgba(20,150,60,0.9)" : "rgba(120,130,120,0.8)";
    ctx.fillText(String(npc.entityIdx), npc.x * t + 2, npc.y * t + t + 1);
  }
  if (state.drag && state.drag.ghost) {
    ctx.strokeStyle = "#00c8ff";
    ctx.lineWidth = 3;
    ctx.strokeRect(state.drag.ghost.x * t + 1, state.drag.ghost.y * t + 1, t - 2, t - 2);
  }
}

function tileFromEvent(event) {
  const rect = state.canvas.getBoundingClientRect();
  const t = state.tilePx;
  const x = Math.floor((event.clientX - rect.left) / t);
  const y = Math.floor((event.clientY - rect.top) / t);
  const d = state.detail;
  if (x < 0 || y < 0 || x >= d.width || y >= d.height) return null;
  return { x, y };
}

function nativeAt(x, y) {
  return state.detail.npcs.find((n) => n.x === x && n.y === y) || null;
}

function showInfo(x, y) {
  const d = state.detail;
  const native = nativeAt(x, y);
  const custom = customAt(x, y);
  const hit = native ? { kind: "native", npc: native } : custom ? { kind: "custom", npc: custom } : null;
  const panel = $("map-info");
  const cell = state.collision ? state.collision[y * d.width + x] : 1;
  let html = `<h3>格子 (${x}, ${y})</h3><div class="kv">` +
    `<b>碰撞</b><span>${cell ? "不可站立" : "可站立"}</span>`;
  if (hit) {
    const n = hit.npc;
    html += `<b>类型</b><span>${hit.kind === "native" ? "原生 NPC（不可编辑）" : "自定义 NPC（可拖动）"}</span>` +
      `<b>序号</b><span>${n.entityIdx}</span>` +
      `<b>外观</b><span>${n.graphicsId !== undefined ? n.graphicsId : n.spriteId}</span>` +
      `<b>朝向</b><span>${n.facing !== undefined ? n.facing : n.toward}</span>` +
      `<b>脚本</b><span>${(n.script || "-")}</span>`;
    if (n.shopId) html += `<b>旧商店</b><span>${n.shopId}</span>`;
    html += `</div><button id="info-clerk">绑定为店员</button>`;
    html += `<button id="info-spawn">用作出生点</button>`;
    if (hit.kind === "custom") html += `<button id="info-edit">编辑此 NPC</button>`;
  } else {
    const counter = d.counters.find((c) => c.x === x && c.y === y);
    const warp = d.warps.find((w) => w.x === x && w.y === y);
    if (counter) html += `<b>柜台</b><span>interactionCounter</span>`;
    if (warp) html += `<b>传送点</b><span>目标 bank=${warp.targetBankId} map=${warp.targetMapId}</span>`;
    html += `</div><button id="info-spawn">用作出生点</button>`;
  }
  html += `<div class="legend"><span style="background:rgba(30,90,255,.8)"></span>原生 ` +
    `<span style="background:rgba(20,150,60,.8)"></span>自定义 ` +
    `<span style="background:#8c3cdc"></span>传送 ` +
    `<span style="background:#ff8c00"></span>柜台</div>`;
  panel.innerHTML = html;
  panel.classList.remove("hidden");
  bindInfoButtons(x, y, hit);
}

function bindInfoButtons(x, y, hit) {
  const h = state.handlers;
  const clerk = document.getElementById("info-clerk");
  if (clerk) clerk.onclick = () => h.onUseAsClerk && h.onUseAsClerk(state.detail.name, hit.npc.entityIdx);
  const spawn = document.getElementById("info-spawn");
  if (spawn) spawn.onclick = () => h.onUseAsSpawn && h.onUseAsSpawn(x, y);
  const edit = document.getElementById("info-edit");
  if (edit) edit.onclick = () => h.onEditNpc && h.onEditNpc(hit.npc.relPath);
}

function hideInfo() {
  $("map-info").classList.add("hidden");
}

// -- 右键菜单 -----------------------------------------------------------------

let menuEl = null;

function closeMenu() {
  if (menuEl) {
    menuEl.remove();
    menuEl = null;
  }
}

function showMenu(px, py, items) {
  closeMenu();
  menuEl = document.createElement("div");
  menuEl.className = "ctx-menu";
  for (const item of items) {
    const row = document.createElement("div");
    row.className = "ctx-row" + (item.disabled ? " disabled" : "");
    row.textContent = item.label;
    if (!item.disabled && item.action) {
      row.addEventListener("click", () => {
        closeMenu();
        item.action();
      });
    }
    menuEl.appendChild(row);
  }
  document.body.appendChild(menuEl);
  const w = menuEl.offsetWidth;
  const h = menuEl.offsetHeight;
  menuEl.style.left = `${Math.min(px, window.innerWidth - w - 8)}px`;
  menuEl.style.top = `${Math.min(py, window.innerHeight - h - 8)}px`;
}

function contextMenuFor(event) {
  event.preventDefault();
  closeMenu();
  const tile = tileFromEvent(event);
  if (!tile || !state.detail) return;
  const h = state.handlers;
  const native = nativeAt(tile.x, tile.y);
  const custom = customAt(tile.x, tile.y);
  const items = [];
  if (custom) {
    items.push({ label: `编辑 npc_${custom.entityIdx}`, action: () => h.onEditNpc && h.onEditNpc(custom.relPath) });
    items.push({ label: custom.enabled ? "停用此 NPC" : "启用此 NPC", action: () => h.onToggleNpc && h.onToggleNpc(custom.relPath, !custom.enabled) });
    items.push({ label: "拖动可移动位置（直接拖绿框）" });
  } else if (native) {
    items.push({ label: `查看原生 NPC ${native.entityIdx}`, action: () => showInfo(tile.x, tile.y) });
  } else {
    items.push({ label: `在此新建自定义 NPC (${tile.x},${tile.y})`, action: () => h.onNewNpcAt && h.onNewNpcAt(tile.x, tile.y) });
  }
  items.push({ label: "绑定为店员", disabled: !native && !custom, action: () => h.onUseAsClerk && h.onUseAsClerk(state.detail.name, (custom || native).entityIdx) });
  items.push({ label: "用作出生点", action: () => h.onUseAsSpawn && h.onUseAsSpawn(tile.x, tile.y) });
  showMenu(event.clientX, event.clientY, items);
}

// -- 事件绑定 -------------------------------------------------------------------

$("map-region").addEventListener("change", () => {
  fillMapOptions();
  if ($("map-select").options.length) open($("map-region").value, $("map-select").value);
});
$("map-select").addEventListener("change", () => open($("map-region").value, $("map-select").value));
$("map-search").addEventListener("input", fillMapOptions);
$("map-collision").addEventListener("change", (e) => { state.collisionOn = e.target.checked; drawMarkers(); });
$("map-warps").addEventListener("change", (e) => { state.warpsOn = e.target.checked; drawMarkers(); });
$("map-counters").addEventListener("change", (e) => { state.countersOn = e.target.checked; drawMarkers(); });

$("map-layers").addEventListener("mousedown", (event) => {
  if (event.button !== 0 || state.pickHandler || !state.detail) return;
  const tile = tileFromEvent(event);
  if (!tile) return;
  const custom = customAt(tile.x, tile.y);
  if (!custom) return;
  state.drag = { relPath: custom.relPath, entityIdx: custom.entityIdx, moved: false, ghost: null };
  state.canvas.style.cursor = "grabbing";
  event.preventDefault();
});

$("map-layers").addEventListener("mousemove", (event) => {
  if (!state.drag) return;
  const tile = tileFromEvent(event);
  if (!tile) return;
  state.drag.moved = true;
  state.drag.ghost = tile;
  drawMarkers();
});

$("map-layers").addEventListener("mouseleave", () => {
  if (state.drag) {
    state.drag = null;
    if (state.canvas) state.canvas.style.cursor = "";
    drawMarkers();
  }
  closeMenu();
});

$("map-layers").addEventListener("mouseup", (event) => {
  // 选点模式优先
  if (state.pickHandler) {
    const tile = tileFromEvent(event);
    if (tile) {
      const handler = state.pickHandler;
      cancelPick();
      handler(tile);
    }
    state.drag = null;
    if (state.canvas) state.canvas.style.cursor = "";
    return;
  }
  const drag = state.drag;
  state.drag = null;
  if (state.canvas) state.canvas.style.cursor = "";
  const tile = tileFromEvent(event);
  if (!drag) {
    if (tile) showInfo(tile.x, tile.y);
    return;
  }
  drawMarkers();
  const origin = customAtOrigin(drag.relPath);
  if (!tile || !origin || !drag.moved || (tile.x === origin.x && tile.y === origin.y)) {
    if (tile) showInfo(tile.x, tile.y);
    return;
  }
  const handler = state.handlers.onMoveNpc;
  if (!handler) return;
  Promise.resolve(handler(drag.relPath, tile.x, tile.y))
    .then((ok) => { if (ok) open(state.region, state.name); });
});

$("map-layers").addEventListener("contextmenu", contextMenuFor);
$("map-pick-cancel").addEventListener("click", cancelPick);
document.addEventListener("pointerdown", closeMenu);

export function pick(handler, banner) {
  state.pickHandler = handler;
  $("map-pick-banner").textContent = banner || "请在地图上点击一个格子";
  $("map-pick-banner").classList.remove("hidden");
  $("map-pick-cancel").classList.remove("hidden");
  document.querySelector('[data-tab="maps"]').click();
}

function cancelPick() {
  state.pickHandler = null;
  $("map-pick-banner").classList.add("hidden");
  $("map-pick-cancel").classList.add("hidden");
}

export function openCurrent() {
  const select = $("map-select");
  if (select.value && !state.pickHandler) open($("map-region").value, select.value);
}

export function refresh() {
  if (state.region) open(state.region, state.name);
}

export function currentMap() {
  return state.detail ? { region: state.region, name: state.name } : null;
}

export async function init() {
  await loadRegions();
  if ($("map-select").options.length) open($("map-region").value, $("map-select").value);
}
