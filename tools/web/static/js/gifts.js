/* 商城礼包（GiftShop.jsonc）与赠送宝可梦（Gift.jsonc） */
"use strict";

import { $, api, post, toast } from "./api.js";

export function init() {
  $("giftshop-add").addEventListener("click", addGiftShopRow);
  $("gift-add").addEventListener("click", addGiftRow);
  loadAll();
}

async function loadAll() {
  try {
    const [shop, gifts] = await Promise.all([api("/api/gifts/shop"), api("/api/gifts/list")]);
    renderShop(shop.rows);
    renderGifts(gifts.rows);
  } catch (error) { toast(error.message, true); }
}

function renderShop(rows) {
  const tbody = $("giftshop-table").querySelector("tbody");
  tbody.innerHTML = "";
  for (const row of rows) {
    const tr = document.createElement("tr");
    tr.innerHTML =
      `<td>${row.id}</td>` +
      `<td><input class="g-item" type="number" value="${row.itemIndexId}" style="width:80px"></td>` +
      `<td><input class="g-price" type="number" value="${row.price}" style="width:80px"></td>` +
      `<td><input class="g-original" type="number" value="${row.originalPrice}" style="width:80px"></td>` +
      `<td><input class="g-qty" type="number" value="${row.quantity}" style="width:60px"></td>` +
      `<td><input class="g-order" type="number" value="${row.featuredOrder}" style="width:60px"></td>`;
    const ops = document.createElement("td");
    const saveBtn = document.createElement("button");
    saveBtn.textContent = "存";
    saveBtn.onclick = async () => {
      try {
        const result = await post("/api/gifts/shop/save", {
          id: row.id,
          changes: {
            itemIndexId: Number(tr.querySelector(".g-item").value),
            price: Number(tr.querySelector(".g-price").value),
            originalPrice: Number(tr.querySelector(".g-original").value),
            quantity: Number(tr.querySelector(".g-qty").value),
            featuredOrder: Number(tr.querySelector(".g-order").value),
          },
        });
        toast(result.message);
      } catch (error) { toast(error.message, true); }
    };
    const delBtn = document.createElement("button");
    delBtn.textContent = "删";
    delBtn.className = "danger";
    delBtn.onclick = async () => {
      if (!confirm(`删除礼包条目 ${row.id}？`)) return;
      try {
        await post("/api/gifts/shop/delete", { id: row.id });
        toast("已删除");
        loadAll();
      } catch (error) { toast(error.message, true); }
    };
    ops.append(saveBtn, delBtn);
    tr.appendChild(ops);
    tbody.appendChild(tr);
  }
}

async function addGiftShopRow() {
  try {
    const result = await post("/api/gifts/shop/add", { row: { itemIndexId: 0, price: 0, originalPrice: 0, quantity: 1 } });
    toast(result.message);
    loadAll();
  } catch (error) { toast(error.message, true); }
}

function renderGifts(rows) {
  const tbody = $("gift-table").querySelector("tbody");
  tbody.innerHTML = "";
  for (const row of rows) {
    const tr = document.createElement("tr");
    tr.innerHTML =
      `<td>${row.giftId}</td>` +
      `<td><input class="gf-dex" type="number" value="${row.pokemonIndexId}" style="width:70px"></td>` +
      `<td><input class="gf-level" type="number" value="${row.level}" style="width:60px" min="1" max="100"></td>` +
      `<td><input class="gf-moves" type="text" value="${(row.moves || []).join(",")}" style="width:130px"></td>` +
      `<td><input class="gf-ball" type="number" value="${row.ballType}" style="width:50px"></td>`;
    const ops = document.createElement("td");
    const saveBtn = document.createElement("button");
    saveBtn.textContent = "存";
    saveBtn.onclick = async () => {
      try {
        const result = await post("/api/gifts/save", {
          giftId: row.giftId,
          changes: {
            pokemonIndexId: Number(tr.querySelector(".gf-dex").value),
            level: Number(tr.querySelector(".gf-level").value),
            moves: tr.querySelector(".gf-moves").value.split(",").map((v) => Number(v.trim())),
            ballType: Number(tr.querySelector(".gf-ball").value),
          },
        });
        toast(result.message);
      } catch (error) { toast(error.message, true); }
    };
    const delBtn = document.createElement("button");
    delBtn.textContent = "删";
    delBtn.className = "danger";
    delBtn.onclick = async () => {
      if (!confirm(`删除赠送条目 ${row.giftId}？`)) return;
      try {
        await post("/api/gifts/delete", { giftId: row.giftId });
        toast("已删除");
        loadAll();
      } catch (error) { toast(error.message, true); }
    };
    ops.append(saveBtn, delBtn);
    tr.appendChild(ops);
    tbody.appendChild(tr);
  }
}

async function addGiftRow() {
  try {
    const result = await post("/api/gifts/add", { row: { pokemonIndexId: 1, level: 5 } });
    toast(result.message);
    loadAll();
  } catch (error) { toast(error.message, true); }
}
