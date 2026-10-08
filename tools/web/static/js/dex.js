/* 图鉴/捕捉配置（CaptureSpecies.jsonc + Pokemon.jsonc 概览） */
"use strict";

import { $, api, post, toast } from "./api.js";

let selectedDex = null;

export function init() {
  $("dex-search").addEventListener("input", loadList);
  $("dex-save").addEventListener("click", saveCapture);
  loadList();
}

async function loadList() {
  const q = $("dex-search").value.trim();
  const data = await api(`/api/dex/capture?q=${encodeURIComponent(q)}`);
  const tbody = $("dex-table").querySelector("tbody");
  tbody.innerHTML = "";
  for (const row of data.rows) {
    const tr = document.createElement("tr");
    tr.innerHTML =
      `<td>${row.pokemonIndexId}</td><td>${row.name || ""}</td>` +
      `<td><input class="dx-catch" type="number" min="0" max="255" value="${row.catchRate ?? 0}" style="width:70px"></td>` +
      `<td><input class="dx-happy" type="number" min="0" max="255" value="${row.baseHappiness ?? 0}" style="width:70px"></td>`;
    const ops = document.createElement("td");
    const btn = document.createElement("button");
    btn.textContent = "保存";
    btn.onclick = async () => {
      try {
        const result = await post("/api/dex/capture/save", {
          pokemonIndexId: row.pokemonIndexId,
          changes: {
            catchRate: Number(tr.querySelector(".dx-catch").value),
            baseHappiness: Number(tr.querySelector(".dx-happy").value),
          },
        });
        toast(result.message);
      } catch (error) { toast(error.message, true); }
    };
    const info = document.createElement("button");
    info.textContent = "详情";
    info.onclick = () => openSpecies(row.pokemonIndexId);
    ops.append(btn, info);
    tr.appendChild(ops);
    tbody.appendChild(tr);
  }
}

function openSpecies(dexId) {
  selectedDex = dexId;
  api(`/api/dex/species?pokemonIndexId=${dexId}`).then((data) => {
    const s = data.species;
    $("dex-title").textContent = `#${dexId} ${s.name || ""}`;
    $("dex-catch").value = s.catchRate ?? 0;
    $("dex-happy").value = s.baseHappiness ?? 0;
    const stats = s.stats || {};
    $("dex-species").innerHTML =
      `<div class="kv">` +
      `<b>名称</b><span>${s.name || ""}</span>` +
      `<b>经验类型</b><span>${s.exp_type ?? "-"}</span>` +
      `<b>性别比</b><span>${s.gender_ratio ?? "-"}</span>` +
      `<b>可获取</b><span>${JSON.stringify(s.obtainable)}</span>` +
      `<b>种族值</b><span>${JSON.stringify(stats)}</span>` +
      `</div>` +
      `<label class="check"><input id="dex-obtainable" type="checkbox" ${s.obtainable ? "checked" : ""}> 可获取</label>`;
    $("dex-panel").classList.remove("hidden");
  }).catch((error) => toast(error.message, true));
}

async function saveCapture() {
  if (selectedDex == null) return;
  try {
    const result = await post("/api/dex/capture/save", {
      pokemonIndexId: selectedDex,
      changes: {
        catchRate: Number($("dex-catch").value),
        baseHappiness: Number($("dex-happy").value),
      },
    });
    toast(result.message);
    const obtainable = document.getElementById("dex-obtainable");
    if (obtainable) {
      const speciesResult = await post("/api/dex/species/save", {
        pokemonIndexId: selectedDex, changes: { obtainable: obtainable.checked },
      });
      toast(speciesResult.message);
    }
    loadList();
  } catch (error) { toast(error.message, true); }
}
