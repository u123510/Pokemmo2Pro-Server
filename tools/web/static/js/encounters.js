/* 遇敌表编辑（地图 JSON 的 encounterFields + wildEncounters） */
"use strict";

import { $, api, post, toast } from "./api.js";

let maps = [];
let currentMap = null;
let speciesOptions = [];
const GROUP_NAMES = { land_mons: "陆地", water_mons: "水上", rock_smash_mons: "碎岩", fishing_mons: "钓鱼" };

export function init(mapsData) {
  maps = mapsData;
  const regionSel = $("enc-region");
  for (const region of new Set(maps.map((m) => m.region))) {
    regionSel.appendChild(new Option(region, region));
  }
  regionSel.addEventListener("change", fillMapSelect);
  fillMapSelect();
  $("enc-load").addEventListener("click", loadEncounters);
  $("enc-save").addEventListener("click", saveEncounters);
  api("/api/species-options").then((data) => { speciesOptions = data.options; }).catch(() => {});
}

function fillMapSelect() {
  const mapSel = $("enc-map");
  mapSel.innerHTML = "";
  for (const m of maps.filter((m) => m.region === $("enc-region").value)) {
    mapSel.appendChild(new Option(m.name, m.name));
  }
}

async function loadEncounters() {
  const region = $("enc-region").value;
  const name = $("enc-map").value;
  if (!name) return;
  try {
    currentMap = await api(`/api/encounters?region=${encodeURIComponent(region)}&name=${encodeURIComponent(name)}`);
    render();
    $("enc-editor").classList.remove("hidden");
    $("enc-empty").classList.add("hidden");
  } catch (error) { toast(error.message, true); }
}

function render() {
  const editor = $("enc-editor");
  editor.innerHTML = "";
  const groups = currentMap.wildEncounters || [];
  if (!groups.length) {
    editor.innerHTML = `<p class="empty">该地图没有遇敌组。可添加：</p>`;
    const addRow = document.createElement("div");
    addRow.className = "toolbar";
    for (const [key, label] of Object.entries(GROUP_NAMES)) {
      const btn = document.createElement("button");
      btn.textContent = `＋ ${label}组`;
      btn.onclick = () => {
        currentMap.wildEncounters.push({ map: currentMap.name, base_label: "", [key]: { encounter_rate: 10, mons: [] } });
        render();
      };
      addRow.appendChild(btn);
    }
    editor.appendChild(addRow);
    return;
  }
  groups.forEach((group, groupIndex) => {
    const box = document.createElement("details");
    box.open = true;
    const label = Object.entries(GROUP_NAMES).filter(([k]) => group[k]).map(([, v]) => v).join("/") || "空组";
    box.innerHTML = `<summary>${label}（${group.map || ""}）
      <button class="g-del danger" style="float:right">删除组</button></summary>`;
    box.querySelector(".g-del").onclick = (e) => {
      e.preventDefault();
      currentMap.wildEncounters.splice(groupIndex, 1);
      render();
    };
    for (const [key, labelCN] of Object.entries(GROUP_NAMES)) {
      const section = group[key];
      if (!section) continue;
      const secBox = document.createElement("div");
      secBox.className = "enc-section";
      secBox.innerHTML = `<b>${labelCN}</b> 遇敌率 <input class="e-rate" type="number" min="0" max="100"
        value="${section.encounter_rate ?? 0}" style="width:60px">
        <button class="e-add">＋ 加一条</button>`;
      secBox.querySelector(".e-rate").addEventListener("change", () => {
        section.encounter_rate = Number(secBox.querySelector(".e-rate").value);
      });
      const table = document.createElement("table");
      table.className = "rows";
      table.innerHTML = `<thead><tr><th>种类</th><th>最低</th><th>最高</th><th></th></tr></thead>`;
      const tbody = document.createElement("tbody");
      (section.mons || []).forEach((mon, monIndex) => {
        tbody.appendChild(monRow(section, monIndex));
      });
      table.appendChild(tbody);
      secBox.appendChild(table);
      secBox.querySelector(".e-add").onclick = () => {
        section.mons = section.mons || [];
        section.mons.push({ species: speciesOptions[0]?.species || "SPECIES_NONE", min_level: 5, max_level: 10 });
        render();
      };
      box.appendChild(secBox);
    }
    editor.appendChild(box);
  });
}

function monRow(section, monIndex) {
  const mon = section.mons[monIndex];
  const tr = document.createElement("tr");
  const sel = document.createElement("select");
  sel.className = "m-species";
  for (const option of speciesOptions) {
    const opt = new Option(`#${option.dexId} ${option.name || option.species}`, option.species);
    if (option.species === mon.species) opt.selected = true;
    sel.appendChild(opt);
  }
  tr.appendChild(sel);
  const minInput = document.createElement("input");
  minInput.type = "number"; minInput.value = mon.min_level; minInput.min = 1; minInput.max = 100;
  const maxInput = document.createElement("input");
  maxInput.type = "number"; maxInput.value = mon.max_level; maxInput.min = 1; maxInput.max = 100;
  minInput.addEventListener("change", () => { mon.min_level = Number(minInput.value); });
  maxInput.addEventListener("change", () => { mon.max_level = Number(maxInput.value); });
  const tdMin = document.createElement("td"); tdMin.appendChild(minInput);
  const tdMax = document.createElement("td"); tdMax.appendChild(maxInput);
  tr.appendChild(tdMin); tr.appendChild(tdMax);
  const ops = document.createElement("td");
  const del = document.createElement("button");
  del.textContent = "删"; del.className = "danger";
  del.onclick = () => { section.mons.splice(monIndex, 1); render(); };
  ops.appendChild(del);
  tr.appendChild(ops);
  return tr;
}

async function saveEncounters() {
  if (!currentMap) return;
  try {
    const result = await post("/api/encounters/save", {
      region: currentMap.region, name: currentMap.name,
      encounterFields: currentMap.encounterFields || [],
      wildEncounters: currentMap.wildEncounters || [],
    });
    toast(result.message);
  } catch (error) { toast(error.message, true); }
}
