/* 公共请求与提示工具：会话管理、CSRF、401 自动弹出登录 */
"use strict";

let csrfToken = "";
const unauthorizedListeners = [];

export function onUnauthorized(fn) {
  unauthorizedListeners.push(fn);
}

function notifyUnauthorized() {
  for (const fn of unauthorizedListeners) fn();
}

export function setCsrf(token) {
  csrfToken = token;
}

export function $(id) {
  return document.getElementById(id);
}

export async function api(path, options) {
  const res = await fetch(path, options);
  if (res.status === 401) {
    notifyUnauthorized();
    throw new Error("未登录或会话过期");
  }
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.error || res.statusText);
  return data;
}

export function post(path, body) {
  return api(path, {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-CSRF": csrfToken },
    body: JSON.stringify(body),
  });
}

let toastTimer = null;
export function toast(message, isError) {
  const el = $("toast");
  el.textContent = message;
  el.classList.toggle("error", Boolean(isError));
  el.classList.remove("hidden");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.add("hidden"), isError ? 6000 : 3500);
}
