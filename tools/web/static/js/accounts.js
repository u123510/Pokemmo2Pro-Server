/* 账号与角色管理（数据库） */
"use strict";

import { $, api, post, toast } from "./api.js";

let maps = [];
let selectedCharacter = null;

export function init(mapsData) {
  maps = mapsData;
  bindEvents();
}

export async function reload() {
  await loadAccounts();
}

async function loadAccounts() {
  const keyword = $("acc-search").value.trim();
  const data = await api(`/api/accounts?q=${encodeURIComponent(keyword)}`);
  const tbody = $("acc-table").querySelector("tbody");
  tbody.innerHTML = "";
  for (const account of data.accounts) {
    for (const char of account.characters) {
      const tr = document.createElement("tr");
      tr.innerHTML =
        `<td>${account.account_name} <span class="sub">#${account.account_id}</span></td>` +
        `<td>${account.banned ? `<span class="bad">封禁: ${account.ban_reason || "?"}</span>` : "正常"}</td>` +
        `<td>${char.name} ${char.online ? '<span class="chip">在线</span>' : ""}</td>` +
        `<td>${char.money}</td>` +
        `<td class="sub">${char.region_id}/${char.map_group}/${char.map_id} (${char.x},${char.y})</td>`;
      const ops = document.createElement("td");
      const editBtn = document.createElement("button");
      editBtn.textContent = "编辑";
      editBtn.onclick = () => openCharacter(account, char);
      ops.appendChild(editBtn);
      const banBtn = document.createElement("button");
      banBtn.textContent = account.banned ? "解封" : "封禁";
      banBtn.className = account.banned ? "" : "danger";
      banBtn.onclick = () => toggleBan(account);
      ops.appendChild(banBtn);
      const delBtn = document.createElement("button");
      delBtn.textContent = "删号";
      delBtn.className = "danger";
      delBtn.onclick = () => deleteAccount(account);
      ops.appendChild(delBtn);
      tr.appendChild(ops);
      tbody.appendChild(tr);
    }
    if (!account.characters.length) {
      const tr = document.createElement("tr");
      tr.innerHTML = `<td>${account.account_name} <span class="sub">#${account.account_id}</span></td>` +
        `<td>${account.banned ? "封禁" : "正常"}</td><td class="sub">无角色</td><td></td><td></td><td></td>`;
      tbody.appendChild(tr);
    }
  }
}

function openCharacter(account, char) {
  selectedCharacter = { accountId: account.account_id, ...char };
  $("char-title").textContent = `${char.name}（角色 #${char.id}${char.online ? "，在线" : ""}）`;
  $("char-newname").value = char.name;
  $("char-money").value = char.money;
  $("char-coins").value = char.coins || 0;
  const regionSel = $("char-region");
  regionSel.innerHTML = "";
  for (const region of new Set(maps.map((m) => m.region))) {
    regionSel.appendChild(new Option(region, region));
  }
  fillMapSelect(char.region_id);
  $("char-x").value = char.x;
  $("char-y").value = char.y;
  $("char-z").value = char.z;
  $("char-msg").textContent = "";
  $("char-panel").classList.remove("hidden");
}

function fillMapSelect(regionId) {
  const mapSel = $("char-map");
  mapSel.innerHTML = "";
  const regionName = { 0: "kanto", 1: "hoenn", 3: "sinnoh" }[Number(regionId)] || "kanto";
  for (const m of maps.filter((m) => m.region === regionName)) {
    mapSel.appendChild(new Option(m.name, m.name));
  }
}

function selectedMapInfo() {
  const regionName = { 0: "kanto", 1: "hoenn", 3: "sinnoh" }[Number($("char-region").value)] || "kanto";
  return maps.find((m) => m.region === regionName && m.name === $("char-map").value);
}

async function toggleBan(account) {
  const banning = !account.banned;
  let reason = "";
  if (banning) {
    reason = prompt("封禁原因：", "违规");
    if (reason === null) return;
  }
  try {
    const result = await post("/api/account/ban", { accountId: account.account_id, banned: banning, reason });
    toast(result.message);
    loadAccounts();
  } catch (error) { toast(error.message, true); }
}

async function deleteAccount(account) {
  if (!confirm(`确定删除账号 ${account.account_name}（#${account.account_id}）？\n其所有角色、道具、宝可梦将一并删除，不可恢复！`)) return;
  try {
    const result = await post("/api/account/delete", { accountId: account.account_id, confirm: account.account_id });
    toast(result.message);
    loadAccounts();
  } catch (error) { toast(error.message, true); }
}

function bindEvents() {
  $("acc-search").addEventListener("input", loadAccounts);
  $("char-region").addEventListener("change", () => fillMapSelect($("char-region").value));
  $("char-save").addEventListener("click", async () => {
    if (!selectedCharacter) return;
    try {
      const force = $("char-panel").querySelector(".chip") !== null;
      if (selectedCharacter.name !== $("char-newname").value.trim()) {
        const result = await post("/api/character/rename", {
          characterId: selectedCharacter.id, name: $("char-newname").value.trim(),
          force: confirm("角色可能在线，在线改名会被游戏服覆盖。强制执行？"),
        });
        toast(result.message);
      }
      const result = await post("/api/character/currency", {
        characterId: selectedCharacter.id, field: "money", value: Number($("char-money").value),
        force: confirm("金钱修改：若角色在线会被覆盖，强制执行？"),
      });
      toast(result.message);
      await post("/api/character/currency", {
        characterId: selectedCharacter.id, field: "coins", value: Number($("char-coins").value),
        force: true,
      }).catch(() => {});
      loadAccounts();
    } catch (error) {
      $("char-msg").textContent = error.message;
    }
  });
  $("char-teleport").addEventListener("click", async () => {
    if (!selectedCharacter) return;
    const info = selectedMapInfo();
    if (!info) { toast("请选择目标地图", true); return; }
    try {
      const result = await post("/api/character/teleport", {
        characterId: selectedCharacter.id,
        regionId: info.regionId, mapGroup: info.map_group, mapId: info.map_id,
        x: Number($("char-x").value), y: Number($("char-y").value), z: Number($("char-z").value),
        force: confirm("角色在线时传送会被覆盖，强制执行？"),
      });
      $("char-msg").textContent = "";
      toast(result.message);
    } catch (error) {
      $("char-msg").textContent = error.message;
    }
  });
}
