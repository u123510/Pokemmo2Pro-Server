/* 招式编辑器（resource/move/genX） */
"use strict";

import { $, api, post, toast } from "./api.js";

let currentGen = null;
let currentMove = null;

export function init() {
  loadGens();
  $("move-search").addEventListener("change", () => {
    const id = Number($("move-search").value);
    if (id) openMove(id);
  });
  $("move-save").addEventListener("click", saveMove);
}

async function loadGens() {
  try {
    const data = await api("/api/moves/gens");
    const sel = $("move-gen");
    sel.innerHTML = "";
    for (const gen of data.gens) {
      if (!gen.available) continue;
      sel.appendChild(new Option(`${gen.gen} (${gen.count})`, gen.gen));
    }
    sel.addEventListener("change", loadList);
    loadList();
  } catch (error) { toast(error.message, true); }
}

async function loadList() {
  currentGen = $("move-gen").value;
  try {
    const data = await api(`/api/moves/list?gen=${currentGen}`);
    const tbody = $("move-table").querySelector("tbody");
    tbody.innerHTML = "";
    for (const move of data.moves) {
      const tr = document.createElement("tr");
      tr.innerHTML =
        `<td>${move.moveIndexId}</td><td>${move.type || ""}</td><td>${move.damageType || ""}</td>` +
        `<td>${move.power ?? ""}</td><td>${move.accuracy ?? ""}</td><td>${move.pp ?? ""}</td>`;
      tr.onclick = () => openMove(move.moveIndexId);
      tbody.appendChild(tr);
    }
  } catch (error) { toast(error.message, true); }
}

async function openMove(moveId) {
  try {
    const data = await api(`/api/moves/move?gen=${currentGen}&moveId=${moveId}`);
    currentMove = moveId;
    $("move-title").textContent = `${currentGen} / 技能${moveId}`;
    $("move-power").value = data.move.moveBasePower ?? 0;
    $("move-accuracy").value = data.move.moveBaseAccuracy ?? 0;
    $("move-pp").value = data.move.moveBasePp ?? 0;
    $("move-priority").value = data.move.movePriority ?? 0;
    $("move-json").value = JSON.stringify(data.move, null, 1);
    $("move-editor").classList.remove("hidden");
    $("move-empty").classList.add("hidden");
    $("move-errors").textContent = "";
  } catch (error) { toast(error.message, true); }
}

async function saveMove() {
  if (currentMove == null) return;
  let parsed;
  try {
    parsed = JSON.parse($("move-json").value);
  } catch (error) {
    $("move-errors").textContent = `JSON 解析失败: ${error.message}`;
    return;
  }
  parsed.moveBasePower = Number($("move-power").value);
  parsed.moveBaseAccuracy = Number($("move-accuracy").value);
  parsed.moveBasePp = Number($("move-pp").value);
  parsed.movePriority = Number($("move-priority").value);
  try {
    const result = await post("/api/moves/save", { gen: currentGen, moveId: currentMove, move: parsed });
    toast(result.message);
    loadList();
  } catch (error) {
    $("move-errors").textContent = error.message;
  }
}
