/* 入口：登录流、导航切换、模块装配 */
"use strict";

import { $, api, post, setCsrf, toast, onUnauthorized } from "./api.js";
import * as MapView from "./mapview.js";
import * as Shops from "./shops.js";
import * as Npcs from "./npcs.js";
import { init as initAccounts, reload as reloadAccounts } from "./accounts.js";
import { init as initPlayers } from "./players.js";
import { init as initDict } from "./itemsdict.js";
import { init as initGifts } from "./gifts.js";
import { init as initMoves } from "./moves.js";
import { init as initDex } from "./dex.js";
import { init as initEncounters } from "./encounters.js";
import { init as initEvents, reload as reloadEvents } from "./events.js";

const loadedViews = new Set();

document.querySelectorAll(".tab").forEach((tab) => {
  tab.addEventListener("click", () => {
    document.querySelectorAll(".tab").forEach((t) => t.classList.toggle("active", t === tab));
    document.querySelectorAll(".tab-body").forEach((b) =>
      b.classList.toggle("active", b.id === `tab-${tab.dataset.tab}`));
    if (tab.dataset.tab === "maps") MapView.openCurrent();
    if (tab.dataset.tab === "audit") loadAudit();
  });
});

function showLogin(show) {
  $("login-overlay").classList.toggle("hidden", !show);
  if (show) {
    $("login-password").value = "";
    $("login-error").textContent = "";
    setTimeout(() => $("login-password").focus(), 50);
  }
}

async function tryLogin() {
  $("login-error").textContent = "";
  try {
    await post("/api/login", { password: $("login-password").value });
    await startSession();
  } catch (error) {
    $("login-error").textContent = error.message;
  }
}

async function startSession() {
  const me = await api("/api/me");
  setCsrf(me.csrf);
  showLogin(false);
  await boot();
}

async function loadAudit() {
  const data = await api("/api/audit?limit=150");
  const tbody = $("audit-table").querySelector("tbody");
  tbody.innerHTML = "";
  for (const entry of data.entries) {
    const tr = document.createElement("tr");
    tr.innerHTML = `<td>${entry.ts}</td><td>${entry.action}</td>` +
      `<td>${entry.ok ? "✓" : "✗"}</td><td class="sub">${JSON.stringify(entry.detail).slice(0, 160)}</td>`;
    tbody.appendChild(tr);
  }
}

async function boot() {
  const [overview, mapsData] = await Promise.all([api("/api/overview"), api("/api/maps")]);
  const maps = mapsData.maps;

  initAccounts(maps);
  initPlayers();
  initDict();
  initGifts();
  initMoves();
  initDex();
  initEncounters(maps);
  initEvents();
  Shops.init(maps);
  Npcs.init(maps);
  MapView.setHandlers({
    onUseAsSpawn: (x, y) => Npcs.useAsSpawn(x, y),
    onUseAsClerk: (mapName, entityIdx) => Shops.useAsClerk(mapName, entityIdx),
    onEditNpc: (relPath) => Npcs.openCustomNpc(relPath),
    onToggleNpc: (relPath, enabled) => Npcs.toggleByPath(relPath, enabled),
    onNewNpcAt: (x, y) => Npcs.newNpcAt(x, y),
    onMoveNpc: (relPath, x, y) => Npcs.moveNpc(relPath, x, y),
  });
  MapView.init();

  $("overview").innerHTML =
    `<span class="chip">店铺 ${overview.shops}</span>` +
    `<span class="chip">自定义 NPC ${overview.customNpcs}</span>` +
    `<span class="chip">地图 ${overview.maps}</span>` +
    `<span class="chip">道具 ${overview.dictItems}</span>` +
    `<span class="chip">关都图源 ${overview.decomp.kanto ? "✓" : "✗"}</span>` +
    `<span class="chip">丰缘图源 ${overview.decomp.hoenn ? "✓" : "✗"}</span>`;

  if (!loadedViews.has("boot")) {
    loadedViews.add("boot");
    await Promise.all([Shops.reload(), Npcs.reload(), reloadAccounts(), reloadEvents()]);
  }
  for (const hint of overview.hints) toast(hint);
}

$("login-btn").addEventListener("click", tryLogin);
$("login-password").addEventListener("keydown", (e) => { if (e.key === "Enter") tryLogin(); });

$("btn-logout").addEventListener("click", async () => {
  try { await post("/api/logout", {}); } catch (_e) { /* 忽略 */ }
  location.reload();
});

$("btn-password").addEventListener("click", async () => {
  const next = prompt("设置新的后台管理口令（至少 6 位）：");
  if (!next) return;
  try {
    const result = await post("/api/password", { newPassword: next });
    toast(result.message);
  } catch (error) { toast(error.message, true); }
});

onUnauthorized(() => showLogin(true));

(async () => {
  try {
    await startSession();
  } catch (_e) {
    showLogin(true);
  }
})();
