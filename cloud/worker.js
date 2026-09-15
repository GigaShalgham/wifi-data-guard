var __defProp = Object.defineProperty;
var __name = (target, value) => __defProp(target, "name", { value, configurable: true });

// src/app.js
var DASHBOARD_JS = `
"use strict";
var I18N = {
  en: {
    tagline: "Cloud control for your family’s Wi-Fi limits",
    loginTitle: "Parent sign-in",
    loginHint: "We email you a one-time sign-in link. No password, ever.",
    emailPh: "you@example.com",
    sendLink: "Send sign-in link",
    checkEmail: "Check your inbox — the link is valid for 15 minutes.",
    bootstrap: "Email delivery is not configured yet. Use your one-time link:",
    logout: "Sign out",
    devices: "Devices",
    noDevices: "No device paired yet. Generate a code and enter it in the child app. (Unpaired one by accident? A new code re-pairs it — nothing is lost.)",
    pairTitle: "Pair a device",
    genCode: "Generate pairing code",
    codeValid: "Valid for 10 minutes — one device per code.",
    lastSeen: "last seen",
    justNow: "just now",
    minAgo: "min ago",
    hAgo: "h ago",
    dAgo: "d ago",
    never: "never",
    online: "online",
    locked: "LOCKED",
    grace: "grace",
    normal: "ok",
    lockNow: "Lock now",
    unlock15: "Unlock 15m",
    unlockFull: "Unlock",
    confirmUnlockFull: "Fully unlock this device? Internet stays open until you lock it again (or its data limit is reached). Device app v1.3.3+ required \u2014 older apps treat this as a 15-minute window.",
    pendingLock: "Locking\u2026",
    pendingUnlock: "Unlocking\u2026",
    waitDevice: "waiting for device (up to ~1 min)",
    oldApp: "old phone app \u2014 full unlock needs v1.3.3+",
    fastLink: "fast",
    settings: "Settings",
    save: "Save",
    saved: "Saved — the device picks it up on its next poll.",
    revoke: "Unpair device",
    revokeConfirm: "Unpair this device? The app on it will return to local-only mode.",
    revokeConfirmLocked: "⚠️ This device is LOCKED right now. If you unpair it, it STAYS locked and you can NOT unlock it remotely anymore — only the parent PIN on the device itself can unlock it. Unpair anyway?",
    revoked: "Device unpaired.",
    limitMb: "Limit (MB)",
    period: "Period",
    daily: "daily",
    monthly: "monthly",
    hardMode: "Hard mode (VPN)",
    unlockMin: "Unlock window (min)",
    offlineTol: "Offline tolerance (min)",
    audit: "Recent activity",
    noAudit: "No activity yet.",
    usage: "usage",
    errGeneric: "Something went wrong. Try again.",
    errAuth: "Session expired — please sign in again.",
    confirmLock: "Lock internet on this device now?",
    confirmUnlock: "Grant a 15-minute unlock window?",
    cmds: "pending commands",
    loaded: "loaded"
  },
  fa: {
    tagline: "کنترل ابری سهمیت وای‌فای خانواده",
    loginTitle: "ورود والد",
    loginHint: "لینک یکبار مصرف برای ورود برایتات ایمیل می‌شود. بدون رمز عبور.",
    emailPh: "you@example.com",
    sendLink: "ارسال لینک ورود",
    checkEmail: "ایمیلت را چک کن — لینک ۱۵ دقیقه اعتبار دارد.",
    bootstrap: "ارسال ایمیل پیکربندی نشده. لینک یکبارمصرف تو:",
    logout: "خروج",
    devices: "دستگاه‌ها",
    noDevices: "هنوز دستگاهی متصل نشده. یک کد بساز و در اپ فرزند وارد کن. (اشتباهاً دستگاهی را جدا کردی؟ با کد جدید دوباره وصل می‌شود — چیزی از دست نمی‌رود.)",
    pairTitle: "مجهز کردن دستگاه",
    genCode: "ساخت کد اتصال",
    codeValid: "۱۰ دقیقه اعتبار دارد — هر کد یک دستگاه.",
    lastSeen: "آخرین احضا",
    justNow: "لحظاتی پیش",
    minAgo: "دقیقه پیش",
    hAgo: "ساعت پیش",
    dAgo: "روز پیش",
    never: "هرگز",
    online: "آنلاین",
    locked: "قفل",
    grace: "مهلت",
    normal: "سالم",
    lockNow: "قفل فرمان",
    unlock15: "باز ۱۵ دقیقه‌ای",
    unlockFull: "باز کردن قفل",
    confirmUnlockFull: "این دستگاه کاملاً باز شود؟ اینترنت تا قفل بعدی (یا رسیدن به حد مصرف) باز می‌ماند. نیازمند اپ نسخهٔ ۱.۳.۳+ — نسخه‌های قدیمی‌تر آن را پنجرهٔ ۱۵ دقیقه‌ای می‌بینند.",
    pendingLock: "در حال قفل…",
    pendingUnlock: "در حال باز کردن…",
    waitDevice: "در انتظار دستگاه (تا ~۱ دقیقه)",
    oldApp: "برنامهٔ قدیمی — بازکردن کامل نیاز به نسخهٔ ۱.۳.۳+ دارد",
    fastLink: "سریع",
    settings: "تنظیمات",
    save: "ذخیره",
    saved: "ذخیره شد — در poll بعدی اعمال می‌شود.",
    revoke: "جدا کردن دستگاه",
    revokeConfirm: "این دستگاه جدا شود؟ اپ آن به حالت محلی برمی‌گردد.",
    revokeConfirmLocked: "⚠️ این دستگاه همین حالا قفل است. اگر جدا‌اش کنی، قفل می‌ماند و دیگر هیچ راهی برای بازکردنش از راه دور نداری — فقط با رمز والد روی خود دستگاه باز می‌شود. به‌هرحال جدا کنیم؟",
    revoked: "دستگاه جدا شد.",
    limitMb: "حد (مگابایت)",
    period: "دوره",
    daily: "روزانه",
    monthly: "ماهانه",
    hardMode: "حالت سخت (VPN)",
    unlockMin: "مدت بازکردن (دقیقه)",
    offlineTol: "تحمل آفلاین (دقیقه)",
    audit: "رفتار ماجرا",
    noAudit: "هنوز فعالیتی نیست.",
    usage: "مصرف",
    errGeneric: "خطایی رخ داد. دوباره تلاش کن.",
    errAuth: "سشن منقضی شد — دوباره وارد شو.",
    confirmLock: "اینترنت این دستگاه همین حالا قفل شود؟",
    confirmUnlock: "پنجره باز ۱۵ دقیقه‌ای داده شود؟",
    cmds: "فرمان در انتظار",
    loaded: "بارگذاری شد"
  }
};

var lang = localStorage.getItem("dg_lang") || "en";
function t(k){ return (I18N[lang] && I18N[lang][k]) || I18N.en[k] || k; }
function setLang(l){
  lang = l; localStorage.setItem("dg_lang", l);
  document.documentElement.lang = l;
  document.documentElement.dir = (l === "fa") ? "rtl" : "ltr";
  refresh(true);
}

function esc(s){
  return String(s == null ? "" : s).replace(/[&<>"']/g, function(c){
    return {"&":"&amp;","<":"&lt;",">":"&gt;","\\"":"&quot;","'":"&#39;"}[c];
  });
}

function api(path, opts){
  opts = opts || {};
  return fetch(path, {
    method: opts.method || "GET",
    headers: opts.body ? {"content-type":"application/json"} : undefined,
    credentials: "same-origin",
    body: opts.body ? JSON.stringify(opts.body) : undefined
  }).then(function(r){
    if (r.status === 401) { throw { auth: true }; }
    return r.json().catch(function(){ return {}; });
  });
}

function fmtBytes(b){
  b = Number(b) || 0;
  if (b >= 1073741824) return (b/1073741824).toFixed(2) + " GB";
  if (b >= 1048576) return (b/1048576).toFixed(1) + " MB";
  if (b >= 1024) return (b/1024).toFixed(0) + " KB";
  return b + " B";
}
function relTime(ts){
  if (!ts) return t("never");
  var d = Date.now() - ts;
  if (d < 60000) return t("justNow");
  if (d < 3600000) return Math.floor(d/60000) + " " + t("minAgo");
  if (d < 86400000) return Math.floor(d/3600000) + " " + t("hAgo");
  return Math.floor(d/86400000) + " " + t("dAgo");
}
// spec-004 FR-002: apps before v1.3.3 silently turn a full unlock into a
// 15-minute grace window — surface that on the card instead of letting the
// parent discover it when the phone re-locks
function appOld(rep){
  if (!rep || !rep.app_version) return false;
  var parts = String(rep.app_version).split("-")[0].split(".");
  var v0 = Number(parts[0]), v1 = Number(parts[1]), v2 = Number(parts[2]);
  if (isNaN(v0) || isNaN(v1)) return false;
  if (parts.length < 3 || isNaN(v2)) v2 = 0;
  return v0 < 1 || (v0 === 1 && v1 < 3) || (v0 === 1 && v1 === 3 && v2 < 3);
}
function deviceStatus(rep){
  if (!rep) return { cls: "", label: "—", dot: true };
  if (rep.latched) return { cls: "locked", label: t("locked") };
  if (rep.grace_until && rep.grace_until > Date.now()) return { cls: "grace", label: t("grace") };
  return { cls: "", label: t("normal") };
}

// ------------------------------------------------------------------ login

function renderLogin(root){
  document.title = "Wi-Fi Data Guard";
  root.innerHTML =
    '<div class="wrap"><div class="card center">' +
    '<h1><img src="/icon.svg" alt="">Wi-Fi Data Guard</h1>' +
    '<div class="sub">' + esc(t("tagline")) + '</div>' +
    '<div id="loginMsg"></div>' +
    '<div style="margin-top:14px"><input type="email" id="email" placeholder="' + esc(t("emailPh")) + '" autocomplete="email"></div>' +
    '<div style="margin-top:12px"><button id="btnLogin" style="width:100%">' + esc(t("sendLink")) + '</button></div>' +
    '<div class="sub" style="margin-top:12px">' + esc(t("loginHint")) + '</div>' +
    '</div><div class="footer"><button class="ghost small" id="btnLang">' + (lang === "fa" ? "English" : "فارسی") + '</button></div></div>';
  document.getElementById("btnLang").onclick = function(){ setLang(lang === "fa" ? "en" : "fa"); };
  var btn = document.getElementById("btnLogin");
  function submit(){
    var email = document.getElementById("email").value.trim();
    if (!email) return;
    btn.disabled = true;
    api("/api/auth/request-link", { method: "POST", body: { email: email } })
      .then(function(res){
        var box = document.getElementById("loginMsg");
        if (res.dev_link) {
          box.innerHTML = '<div class="msg info">' + esc(t("bootstrap")) +
            '<br><a class="lnk" href="' + esc(res.dev_link) + '">' + esc(res.dev_link) + '</a></div>';
        } else if (res.ok) {
          box.innerHTML = '<div class="msg info">' + esc(t("checkEmail")) + '</div>';
        } else {
          box.innerHTML = '<div class="msg err">' + esc(res.error || t("errGeneric")) + '</div>';
        }
      })
      .catch(function(){ document.getElementById("loginMsg").innerHTML =
        '<div class="msg err">' + esc(t("errGeneric")) + '</div>'; })
      .then(function(){ btn.disabled = false; });
  }
  btn.onclick = submit;
  document.getElementById("email").addEventListener("keydown", function(e){ if (e.key === "Enter") submit(); });
}

// ------------------------------------------------------------------ app

var me = null;
var openSettings = {};
var pairTimer = null;
// spec-003: optimistic command state per device {type, timed, until}
// spec-004 FR-001: pend survives a manual refresh (sessionStorage), so a
// reload mid-command no longer re-enables the buttons and invites a duplicate
var pend = {};
var PEND_TTL_MS = 90 * 1000;
function savePend(){ try { sessionStorage.setItem("dgPend", JSON.stringify(pend)); } catch (e) {} }
try { pend = JSON.parse(sessionStorage.getItem("dgPend") || "{}") || {}; } catch (e) { pend = {}; }

function pendFor(d){
  var p = pend[d.id];
  if (!p) return null;
  if (Date.now() > p.until) { delete pend[d.id]; savePend(); return null; }
  return p;
}
function pendConfirmed(p, rep){
  if (!rep) return false;
  if (p.type === "lock") return !!rep.latched;
  if (p.timed) return !!(rep.grace_until && rep.grace_until > Date.now());
  // spec-004 FR-003: an app older than v1.3.3 turns a full unlock into a
  // 15-minute grace window (the latch stays) — confirm when that window
  // lands instead of freezing at "Unlocking…" for the full 90 s
  if (p.oldApp) return !!(rep.grace_until && rep.grace_until > Date.now()) || !rep.latched;
  return !rep.latched;
}
function clearConfirmedPends(){
  var devs = (me && me.devices) || [];
  for (var i = 0; i < devs.length; i++) {
    var p = pend[devs[i].id];
    if (p && pendConfirmed(p, devs[i].report)) { delete pend[devs[i].id]; savePend(); }
  }
}

function refresh(rerender){
  api("/api/me").then(function(res){
    if (!res.ok) { throw { auth: true }; }
    me = res;
    clearConfirmedPends();
    if (rerender || !document.getElementById("devlist")) renderApp(document.getElementById("root"));
    else updateDynamic();
  }).catch(function(e){
    if (e && e.auth) {
      clearInterval(pairTimer);
      renderLogin(document.getElementById("root"));
    }
  });
}

function renderApp(root){
  document.title = "Wi-Fi Data Guard";
  var h =
  '<div class="wrap">' +
    '<div class="spread" style="margin-bottom:14px">' +
      '<h1><img src="/icon.svg" alt="">Wi-Fi Data Guard</h1>' +
      '<div class="row">' +
        '<button class="ghost small" id="btnLang">' + (lang === "fa" ? "English" : "فارسی") + '</button>' +
        '<button class="ghost small" id="btnLogout">' + esc(t("logout")) + '</button>' +
      '</div>' +
    '</div>' +
    '<div class="sub" style="margin:-8px 0 16px">' + esc(me.parent.email) + '</div>' +

    '<div class="card"><h2>' + esc(t("pairTitle")) + '</h2>' +
      '<button id="btnPair">' + esc(t("genCode")) + '</button>' +
      '<div id="pairBox"></div>' +
      '<div class="sub">' + esc(t("codeValid")) + '</div>' +
    '</div>' +

    '<h2 style="margin:20px 0 10px">' + esc(t("devices")) + ' <span id="devCount" class="sub"></span></h2>' +
    '<div id="devlist"></div>' +
    '<div id="noDev" class="card sub" style="text-align:center">' + esc(t("noDevices")) + '</div>' +

    '<div class="card"><h2>' + esc(t("audit")) + '</h2><ul class="log" id="audit"></ul></div>' +
    '<div class="footer">Wi-Fi Data Guard · ' + esc(t("loaded")) + ' <span id="tick"></span></div>' +
  '</div>';
  root.innerHTML = h;
  document.getElementById("btnLang").onclick = function(){ setLang(lang === "fa" ? "en" : "fa"); };
  document.getElementById("btnLogout").onclick = function(){
    api("/api/auth/logout", { method: "POST" }).then(function(){ clearInterval(pairTimer); renderLogin(root); });
  };
  document.getElementById("btnPair").onclick = doPair;
  updateDynamic();
  loadAudit();
}

function doPair(){
  var btn = document.getElementById("btnPair");
  btn.disabled = true;
  api("/api/pair-code", { method: "POST" }).then(function(res){
    btn.disabled = false;
    if (!res.ok) return;
    var box = document.getElementById("pairBox");
    box.innerHTML = '<div class="code">' + esc(res.code) + '</div><div class="sub" id="pairCd"></div>';
    var left = res.expires_in_sec;
    clearInterval(pairTimer);
    pairTimer = setInterval(function(){
      if (left <= 0) { clearInterval(pairTimer); box.innerHTML = ""; return; }
      left--;
      var el = document.getElementById("pairCd");
      if (el) el.textContent = "⏱ " + left + "s";
    }, 1000);
  });
}

function updateDynamic(){
  var list = document.getElementById("devlist");
  if (!list) return;
  var devs = me.devices || [];
  document.getElementById("devCount").textContent = "(" + devs.length + ")";
  document.getElementById("noDev").className = devs.length ? "hidden" : "card sub";
  var h = "";
  for (var i = 0; i < devs.length; i++) h += deviceCard(devs[i]);
  list.innerHTML = h;
  for (var j = 0; j < devs.length; j++) wireDevice(devs[j]);
  document.getElementById("tick").textContent = new Date().toLocaleTimeString();
}

function deviceCard(d){
  var rep = d.report;
  var p = pendFor(d);
  var lockedNow = p ? p.type === "lock" : !!(rep && rep.latched);
  var st = deviceStatus(rep);
  var used = rep ? Number(rep.used_bytes) || 0 : 0;
  var limit = (d.settings.limit_mb || 0) * 1048576;
  var pct = limit > 0 ? Math.min(100, Math.round(used * 100 / limit)) : 0;
  var barCls = pct >= 100 ? " bad" : (pct >= 80 ? " warn" : "");
  var h =
  '<div class="card">' +
    '<div class="spread">' +
      '<div class="row"><b style="font-size:16px">' + esc(d.name) + '</b>' +
        (d.online
          ? '<span class="badge on"><span class="dot"></span>' + esc(t("online")) + '</span>'
          : '<span class="badge"><span class="dot"></span>' + esc(t("lastSeen")) + ': ' + esc(relTime(d.last_seen_at)) + '</span>') +
        (rep ? '<span class="badge ' + st.cls + '"><span class="dot"></span>' + esc(st.label) + '</span>' : '') +
      '</div>' +
      (p
        ? '<span class="badge grace">' + esc(p.type === "lock" ? t("pendingLock") : t("pendingUnlock")) + ' \u00b7 ' + esc(t("waitDevice")) + '</span>'
        : ((d.pending_types && (d.pending_types.unlock || d.pending_types.lock))
          ? '<span class="badge grace">' + esc(d.pending_types.unlock ? t("pendingUnlock") : t("pendingLock")) + ' \u00b7 ' + esc(t("waitDevice")) + '</span>'
          : (d.pending_commands ? '<span class="badge grace">' + d.pending_commands + " " + esc(t("cmds")) + '</span>' : ''))) +
      (appOld(rep) ? '<span class="badge grace">' + esc(t("oldApp")) + '</span>' : '') +
      (d.fast ? '<span class="badge fastchip">\u26a1 ' + esc(t("fastLink")) + '</span>' : '') +
    '</div>' +
    (rep ? '<div class="bar"><div class="' + barCls.trim() + '" style="width:' + pct + '%"></div></div>' +
      '<div class="spread"><span>' + esc(t("usage")) + ': ' + fmtBytes(used) + " / " + fmtBytes(limit) +
      '</span><span class="pct">' + pct + '%</span></div>' +
      '<div class="spark" id="spark' + d.id + '"></div>' +
      (rep.battery_pct !== undefined ? '<div class="sub">ὐb ' + rep.battery_pct + '%</div>' : '') +
      (rep.app_version ? '<div class="sub">v' + esc(rep.app_version) + '</div>' : '')
      : '<div class="sub" style="margin-top:8px">ℹ ' + esc(t("lastSeen")) + ': ' + esc(relTime(d.last_seen_at)) + '</div>') +
    '<div class="row" style="margin-top:12px">' +
      (lockedNow
        ? '<button class="ok small" data-unlock-full="' + d.id + '"' + (p ? " disabled" : "") + '>' + esc(t("unlockFull")) + '</button>' +
          '<button class="ghost small" data-unlock="' + d.id + '"' + (p ? " disabled" : "") + '>' + esc(t("unlock15")) + '</button>'
        : '<button class="danger small" data-lock="' + d.id + '"' + (p ? " disabled" : "") + '>' + esc(t("lockNow")) + '</button>') +
      '<button class="ghost small" data-settings="' + d.id + '">' + esc(t("settings")) + '</button>' +
    '</div>' +
    '<div class="row sep">' +
      '<button class="ghost small warn" data-revoke="' + d.id + '">' + esc(t("revoke")) + '</button>' +
    '</div>' +
    (openSettings[d.id] ? settingsPanel(d) : '') +
  '</div>';
  if (rep) loadSpark(d.id);
  return h;
}

function settingsPanel(d){
  var s = d.settings;
  return '<div class="settings"><div class="grid">' +
    '<div><label>' + esc(t("limitMb")) + '</label><input type="number" id="cfg-limit' + d.id + '" value="' + esc(s.limit_mb) + '" min="1"></div>' +
    '<div><label>' + esc(t("period")) + '</label><select id="cfg-period' + d.id + '">' +
      '<option value="daily"' + (s.period !== "monthly" ? " selected" : "") + '>' + esc(t("daily")) + '</option>' +
      '<option value="monthly"' + (s.period === "monthly" ? " selected" : "") + '>' + esc(t("monthly")) + '</option></select></div>' +
    '<div><label>' + esc(t("unlockMin")) + '</label><input type="number" id="cfg-unlock' + d.id + '" value="' + esc(s.unlock_minutes) + '" min="0"></div>' +
    '<div><label>' + esc(t("offlineTol")) + '</label><input type="number" id="cfg-tol' + d.id + '" value="' + esc(s.offline_tolerance_min) + '" min="1"></div>' +
    '<div><label>' + esc(t("hardMode")) + '</label><select id="cfg-hard' + d.id + '">' +
      '<option value="0"' + (!s.hard_mode ? " selected" : "") + '>' + esc(t("normal")) + '</option>' +
      '<option value="1"' + (s.hard_mode ? " selected" : "") + '>' + esc(t("locked")) + '</option></select></div>' +
  '</div><div class="row" style="margin-top:12px"><button class="small" data-save="' + d.id + '">' + esc(t("save")) + '</button><span class="sub" id="cfgmsg' + d.id + '"></span></div></div>';
}

function wireDevice(d){
  var q = function(sel){ return document.querySelector("[" + sel + '="' + d.id + '"]'); };
  var lockBtn = q("data-lock"), unlockBtn = q("data-unlock"), fullBtn = q("data-unlock-full"),
      setBtn = q("data-settings"), revBtn = q("data-revoke"), saveBtn = q("data-save");
  if (lockBtn) lockBtn.onclick = function(){
    if (!confirm(t("confirmLock"))) return;
    pend[d.id] = { type: "lock", until: Date.now() + PEND_TTL_MS };
    savePend();
    updateDynamic();
    api("/api/devices/" + d.id + "/command", { method: "POST", body: { type: "lock" } }).then(trackRefresh);
  };
  if (unlockBtn) unlockBtn.onclick = function(){
    if (!confirm(t("confirmUnlock"))) return;
    pend[d.id] = { type: "unlock", timed: true, until: Date.now() + PEND_TTL_MS };
    savePend();
    updateDynamic();
    api("/api/devices/" + d.id + "/command", { method: "POST", body: { type: "unlock", payload: { minutes: 15 } } }).then(trackRefresh);
  };
  if (fullBtn) fullBtn.onclick = function(){
    if (!confirm(t("confirmUnlockFull"))) return;
    pend[d.id] = { type: "unlock", until: Date.now() + PEND_TTL_MS, oldApp: appOld(d.report) };
    savePend();
    updateDynamic();
    api("/api/devices/" + d.id + "/command", { method: "POST", body: { type: "unlock", payload: { full: true } } }).then(trackRefresh);
  };
  if (setBtn) setBtn.onclick = function(){
    openSettings[d.id] = !openSettings[d.id];
    updateDynamic();
  };
  if (revBtn) revBtn.onclick = function(){
    // Safe-unpair (spec-002 FR-001): a LOCKED device gets a much stronger
    // warning — revoking it would leave it locked with no remote way back.
    var rep = d.report;
    var msg = (rep && rep.latched) ? t("revokeConfirmLocked") : t("revokeConfirm");
    if (!confirm(msg)) return;
    api("/api/devices/" + d.id + "/revoke", { method: "POST" }).then(function(){ refresh(true); });
  };
  if (saveBtn) saveBtn.onclick = function(){
    var payload = {
      limit_mb: parseInt(document.getElementById("cfg-limit" + d.id).value, 10),
      period: document.getElementById("cfg-period" + d.id).value,
      unlock_minutes: parseInt(document.getElementById("cfg-unlock" + d.id).value, 10),
      offline_tolerance_min: parseInt(document.getElementById("cfg-tol" + d.id).value, 10),
      hard_mode: document.getElementById("cfg-hard" + d.id).value === "1"
    };
    api("/api/devices/" + d.id + "/command", { method: "POST", body: { type: "config", payload: payload } })
      .then(function(){
        var m = document.getElementById("cfgmsg" + d.id);
        if (m) m.textContent = t("saved");
        trackRefresh();
      });
  };
}

function loadSpark(id){
  api("/api/devices/" + id + "/reports").then(function(res){
    var box = document.getElementById("spark" + id);
    if (!box || !res.reports || !res.reports.length) return;
    var max = 1;
    for (var i = 0; i < res.reports.length; i++) max = Math.max(max, res.reports[i].used_bytes);
    var h = "";
    for (var j = 0; j < res.reports.length; j++) {
      var r = res.reports[j];
      var cls = r.latched ? "hot" : "";
      h += '<i class="' + cls + '" style="height:' + Math.max(4, Math.round(r.used_bytes * 40 / max)) + 'px" title="' + new Date(r.ts).toLocaleTimeString() + ' · ' + fmtBytes(r.used_bytes) + '"></i>';
    }
    box.innerHTML = h;
  });
}

function loadAudit(){
  api("/api/audit").then(function(res){
    var el = document.getElementById("audit");
    if (!el) return;
    if (!res.entries || !res.entries.length) { el.innerHTML = '<li>' + esc(t("noAudit")) + '</li>'; return; }
    var h = "";
    for (var i = 0; i < res.entries.length; i++) {
      var e = res.entries[i];
      h += '<li><b>' + esc(e.action) + '</b><span>' + esc(relTime(e.ts)) + '</span></li>';
    }
    el.innerHTML = h;
  });
}

// spec-003 FR-005: the device confirms on its next poll (~30 s), so keep
// pulling until the new state lands instead of one blind 700 ms refresh
function trackRefresh(){
  var delays = [1000, 5000, 15000, 30000, 45000, 60000, 75000, 90000];
  for (var i = 0; i < delays.length; i++) setTimeout(refresh, delays[i]);
}

// ------------------------------------------------------------------ boot

setLang(lang);
if ("serviceWorker" in navigator && location.protocol === "https:") {
  navigator.serviceWorker.register("/sw.js").catch(function(){});
}
// spec-005 FR-005: the 10 s heartbeat runs only while the tab is visible —
// a hidden tab stops paying for the phone's fast mode (and for worker
// invocations). Visible again: restart + immediate refresh.
var beat = null;
function startBeat(){ if (!beat) beat = setInterval(function(){ refresh(); }, 10000); }
function stopBeat(){ clearInterval(beat); beat = null; }
startBeat();
document.addEventListener("visibilitychange", function(){
  if (document.hidden) stopBeat();
  else { startBeat(); refresh(); }
});
window.addEventListener("focus", function(){ refresh(); });
`;

// src/dashboard.js
var ICON_SVG = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 192 192">
<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
<stop offset="0" stop-color="#4f46e5"/><stop offset="1" stop-color="#06b6d4"/></linearGradient></defs>
<rect width="192" height="192" rx="42" fill="#0b1220"/>
<path d="M96 34 L150 54 V96 C150 132 124 152 96 162 C68 152 42 132 42 96 V54 Z"
 fill="url(#g)" opacity="0.16" stroke="url(#g)" stroke-width="4"/>
<path d="M66 92 a38 38 0 0 1 60 0" fill="none" stroke="url(#g)" stroke-width="9" stroke-linecap="round"/>
<path d="M78 108 a24 24 0 0 1 36 0" fill="none" stroke="url(#g)" stroke-width="9" stroke-linecap="round"/>
<circle cx="96" cy="124" r="8" fill="#06b6d4"/>
</svg>`;
var MANIFEST_JSON = JSON.stringify({
  name: "Wi-Fi Data Guard \u2014 Parent",
  short_name: "DataGuard",
  start_url: "/",
  display: "standalone",
  background_color: "#0b1220",
  theme_color: "#0b1220",
  icons: [{ src: "/icon.svg", sizes: "any", type: "image/svg+xml", purpose: "any" }]
});
var SW_JS = `const CACHE = "dg-v4";
const SHELL = ["/", "/app.js", "/styles.css", "/icon.svg", "/manifest.webmanifest"];
self.addEventListener("install", e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(SHELL)).then(() => self.skipWaiting()));
});
self.addEventListener("activate", e => {
  e.waitUntil(caches.keys().then(ks => Promise.all(ks.filter(k => k !== CACHE).map(k => caches.delete(k)))).then(() => self.clients.claim()));
});
self.addEventListener("fetch", e => {
  if (e.request.method !== "GET") return;
  const url = new URL(e.request.url);
  if (url.pathname.startsWith("/api/")) return; // never cache API calls
  e.respondWith(
    fetch(e.request).then(r => {
      if (r.ok && SHELL.includes(url.pathname)) {
        const copy = r.clone();
        caches.open(CACHE).then(c => c.put(e.request, copy));
      }
      return r;
    }).catch(() => caches.match(e.request).then(c => c || caches.match("/")))
  );
});`;
var DASHBOARD_CSS = `
:root{
  --bg:#0b1220; --panel:#121a2e; --panel2:#0e1526; --line:#223052;
  --txt:#e7ecf6; --mut:#8b98b8; --acc:#6366f1; --acc2:#06b6d4;
  --ok:#10b981; --warn:#f59e0b; --bad:#ef4444; --radius:16px;
}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--txt);
  font:15px/1.55 -apple-system,BlinkMacSystemFont,"Segoe UI",Vazirmatn,Roboto,sans-serif;
  min-height:100vh}
[dir=rtl] body{font-family:Vazirmatn,"Segoe UI",Tahoma,sans-serif}
a{color:var(--acc2)}
.wrap{max-width:760px;margin:0 auto;padding:20px 16px 60px}
.card{background:var(--panel);border:1px solid var(--line);border-radius:var(--radius);
  padding:18px;margin-bottom:16px}
h1{font-size:20px;margin:0 0 2px;display:flex;align-items:center;gap:10px}
h1 img{width:34px;height:34px}
h2{font-size:15px;margin:0 0 12px;color:var(--mut);font-weight:600;text-transform:uppercase;letter-spacing:.4px}
.sub{color:var(--mut);font-size:13px}
.row{display:flex;align-items:center;gap:10px;flex-wrap:wrap}
.spread{display:flex;align-items:center;justify-content:space-between;gap:10px;flex-wrap:wrap}
input[type=email],input[type=number],select{
  background:var(--panel2);border:1px solid var(--line);color:var(--txt);
  border-radius:10px;padding:10px 12px;font:inherit;width:100%}
input:focus,select:focus{outline:none;border-color:var(--acc)}
button{background:var(--acc);color:#fff;border:0;border-radius:10px;padding:10px 16px;
  font:inherit;font-weight:600;cursor:pointer;transition:filter .15s}
button:hover{filter:brightness(1.15)}
button.ghost{background:transparent;border:1px solid var(--line);color:var(--txt)}
.row.sep{margin-top:14px;padding-top:12px;border-top:1px dashed var(--line);justify-content:flex-end}
button.ghost.warn{color:var(--bad);border-color:var(--bad)}
button.danger{background:var(--bad)}
button.ok{background:var(--ok)}
button.small{padding:6px 10px;font-size:13px}
button:disabled{opacity:.45;cursor:not-allowed}
.bar{height:10px;background:var(--panel2);border-radius:6px;overflow:hidden;margin:10px 0 4px}
.bar>div{height:100%;background:linear-gradient(90deg,var(--acc),var(--acc2));border-radius:6px;transition:width .4s}
.bar>div.warn{background:var(--warn)}
.bar>div.bad{background:var(--bad)}
.pct{font-size:12px;color:var(--mut)}
.badge{display:inline-flex;align-items:center;gap:6px;font-size:12px;font-weight:600;
  padding:3px 10px;border-radius:99px;border:1px solid var(--line)}
.badge .dot{width:8px;height:8px;border-radius:50%;background:var(--mut)}
.badge.on .dot{background:var(--ok);box-shadow:0 0 8px var(--ok)}
.badge.locked{color:var(--bad)} .badge.locked .dot{background:var(--bad)}
.badge.grace{color:var(--warn)} .badge.grace .dot{background:var(--warn)}
.badge.fastchip{color:var(--acc2);border-color:var(--acc2)}
.code{font:32px/1.2 ui-monospace,Menlo,monospace;letter-spacing:10px;text-align:center;
  background:var(--panel2);border:1px dashed var(--line);border-radius:12px;padding:16px;margin:12px 0;color:var(--acc2)}
.spark{display:flex;align-items:flex-end;gap:2px;height:44px;margin-top:10px}
.spark i{flex:1;background:var(--panel2);border-radius:2px 2px 0 0;min-height:2px}
.spark i.hot{background:var(--warn)}
.log{list-style:none;margin:0;padding:0;font-size:13px}
.log li{padding:7px 0;border-bottom:1px solid var(--line);color:var(--mut);display:flex;gap:8px;justify-content:space-between}
.log li b{color:var(--txt);font-weight:600}
.hidden{display:none}
.msg{padding:10px 12px;border-radius:10px;margin:10px 0;font-size:14px}
.msg.err{background:rgba(239,68,68,.12);color:#fca5a5}
.msg.info{background:rgba(99,102,241,.12);color:#a5b4fc}
.msg .lnk{word-break:break-all}
.settings{margin-top:12px;border-top:1px dashed var(--line);padding-top:12px}
.settings .grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:10px}
.settings label{font-size:12px;color:var(--mut);display:block;margin-bottom:4px}
.footer{text-align:center;color:var(--mut);font-size:12px;margin-top:28px}
.langbtn{position:absolute;top:18px;inset-inline-end:16px}
.center{max-width:400px;margin:8vh auto 0}
.spin{display:inline-block;width:16px;height:16px;border:2px solid #fff5;border-top-color:#fff;
  border-radius:50%;animation:r 0.8s linear infinite;vertical-align:-3px}
@keyframes r{to{transform:rotate(360deg)}}
`;
var DASHBOARD_HTML = `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="theme-color" content="#0b1220">
<meta name="apple-mobile-web-app-capable" content="yes">
<meta name="apple-mobile-web-app-status-bar-style" content="black-translucent">
<link rel="apple-touch-icon" href="/icon.svg">
<link rel="icon" href="/icon.svg" type="image/svg+xml">
<link rel="manifest" href="/manifest.webmanifest">
<title>Wi-Fi Data Guard</title>
<link rel="stylesheet" href="/styles.css">
</head>
<body>
<div id="root"><div class="wrap"><div class="card center" style="text-align:center">
<span class="spin"></span></div></div></div>
<script src="/app.js" defer></script>
</body>
</html>`;
var DEMO_HTML = `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="theme-color" content="#0b1220">
<title>DataGuard — Child Simulator</title>
<style>
:root{--bg:#0b1220;--panel:#121a2e;--panel2:#0e1526;--line:#223052;--txt:#e7ecf6;--mut:#8b98b8;--acc:#6366f1;--acc2:#06b6d4;--ok:#10b981;--bad:#ef4444}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--txt);font:15px/1.55 -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif}
.wrap{max-width:560px;margin:0 auto;padding:20px 16px 60px}
.card{background:var(--panel);border:1px solid var(--line);border-radius:16px;padding:18px;margin-bottom:16px}
h1{font-size:18px;margin:0 0 4px}
.sub{color:var(--mut);font-size:13px}
input{background:var(--panel2);border:1px solid var(--line);color:var(--txt);border-radius:10px;padding:10px 12px;font:inherit;width:100%}
button{background:var(--acc);color:#fff;border:0;border-radius:10px;padding:10px 16px;font:inherit;font-weight:600;cursor:pointer}
button:hover{filter:brightness(1.15)}
button.ghost{background:transparent;border:1px solid var(--line);color:var(--txt)}
button.bad{background:var(--bad)}
.row{display:flex;gap:10px;flex-wrap:wrap;align-items:center}
label{font-size:12px;color:var(--mut);display:block;margin:10px 0 4px}
.log{list-style:none;margin:8px 0 0;padding:0;font-size:13px;max-height:220px;overflow:auto}
.log li{padding:6px 0;border-bottom:1px solid var(--line);color:var(--mut)}
.log li b{color:var(--txt)}
.badge{display:inline-block;font-size:12px;font-weight:600;padding:2px 10px;border-radius:99px;border:1px solid var(--line)}
.badge.locked{color:var(--bad);border-color:var(--bad)}
.hidden{display:none}
#cfg{font:13px/1.7 ui-monospace,Menlo,monospace;color:var(--acc2);word-break:break-all}
.footer{text-align:center;color:var(--mut);font-size:12px;margin-top:24px}
</style>
</head>
<body>
<div class="wrap">
<h1>📱 Child Device Simulator</h1>
<div class="sub">Pretends to be the child Android app — a testing tool for the cloud loop. It does NOT lock any real internet.</div>

<div id="pairbox" class="card">
  <label>Pairing code (generate it in the parent dashboard)</label>
  <input id="code" inputmode="numeric" maxlength="6" placeholder="123456">
  <label>Device name</label>
  <input id="dname" value="Simulated-Phone">
  <div style="margin-top:14px"><button id="btnPair">Pair</button></div>
  <div id="pairMsg" class="sub" style="margin-top:8px"></div>
</div>

<div id="main" class="hidden">
  <div class="card">
    <div class="row" style="justify-content:space-between">
      <b id="devName">device</b>
      <span id="latchBadge" class="badge">ok</span>
    </div>
    <label>Used this period (MB)</label>
    <input id="usedMb" type="number" value="120" min="0">
    <label><input type="checkbox" id="latched" style="width:auto"> report as latched (locked)</label>
    <div class="row" style="margin-top:14px">
      <button id="btnPoll">Poll now</button>
      <button id="btnAuto" class="ghost">Auto: off</button>
      <button id="btnReset" class="bad" style="padding:6px 10px;font-size:13px">forget</button>
    </div>
    <div class="sub" style="margin-top:10px">config received from cloud:</div>
    <div id="cfg">—</div>
  </div>
  <div class="card">
    <b>Commands received</b>
    <ul class="log" id="cmdlog"><li>none yet</li></ul>
    <div class="sub">Acks are sent with the next poll — same protocol as the real app.</div>
  </div>
</div>
<div class="footer">Wi-Fi Data Guard test tool /demo — remove before production</div>
</div>
<script>
"use strict";
var TOKEN = localStorage.getItem("dg_sim_token") || "";
var DEVNAME = localStorage.getItem("dg_sim_name") || "Simulated-Phone";
var acks = [];
var auto = null;
function $(id){ return document.getElementById(id); }
function msg(t){ $("pairMsg").textContent = t; }
function api(path, opts){
  opts = opts || {};
  var h = { "content-type": "application/json" };
  if (TOKEN) h["authorization"] = "Bearer " + TOKEN;
  return fetch(path, {
    method: opts.method || "GET",
    headers: h,
    body: opts.body ? JSON.stringify(opts.body) : undefined
  }).then(function(r){ return r.json().catch(function(){ return {}; }); });
}
function showMain(){
  if (!TOKEN) { $("pairbox").className = "card"; $("main").className = "hidden"; return; }
  $("pairbox").className = "hidden";
  $("main").className = "";
  $("devName").textContent = DEVNAME;
}
$("btnPair").onclick = function(){
  var code = $("code").value.trim();
  if (!/^\\d{6}$/.test(code)) { msg("enter the 6-digit code from the dashboard"); return; }
  api("/api/child/pair", { method: "POST", body: { code: code, name: $("dname").value.trim() || "Simulated-Phone" } })
    .then(function(res){
      if (!res.ok) { msg(res.error || "pairing failed"); return; }
      TOKEN = res.device_token;
      localStorage.setItem("dg_sim_token", TOKEN);
      DEVNAME = $("dname").value.trim() || "Simulated-Phone";
      localStorage.setItem("dg_sim_name", DEVNAME);
      renderCfg(res.config);
      showMain();
    });
};
$("btnPoll").onclick = doPoll;
$("btnAuto").onclick = function(){
  if (auto) { clearInterval(auto); auto = null; $("btnAuto").textContent = "Auto: off"; }
  else {
    auto = setInterval(function(){
      var u = parseInt($("usedMb").value, 10) || 0;
      $("usedMb").value = u + 25;
      doPoll();
    }, 30000);
    $("btnAuto").textContent = "Auto: 30s, +25MB";
  }
};
$("btnReset").onclick = function(){
  localStorage.removeItem("dg_sim_token");
  localStorage.removeItem("dg_sim_name");
  TOKEN = ""; acks = [];
  if (auto) { clearInterval(auto); auto = null; $("btnAuto").textContent = "Auto: off"; }
  showMain();
};
function renderCfg(c){
  $("cfg").textContent = c ? JSON.stringify(c) : "—";
  var locked = $("latched").checked;
  $("latchBadge").className = "badge" + (locked ? " locked" : "");
  $("latchBadge").textContent = locked ? "LOCKED" : "ok";
}
$("latched").onchange = renderCfg;
function logCmd(cmds){
  var el = $("cmdlog");
  if (!cmds || !cmds.length) return;
  var h = "";
  for (var i = cmds.length - 1; i >= 0; i--) {
    var c = cmds[i];
    h += "<li><b>#" + c.id + " " + c.type + "</b> " + JSON.stringify(c.payload) +
         " <span>" + new Date().toLocaleTimeString() + "</span></li>";
  }
  el.innerHTML = h + el.innerHTML;
}
function doPoll(){
  if (!TOKEN) return;
  var usedMb = parseInt($("usedMb").value, 10) || 0;
  api("/api/child/poll", {
    method: "POST",
    body: {
      report: {
        used_bytes: usedMb * 1048576,
        limit_bytes: 0,
        latched: $("latched").checked,
        battery_pct: 77,
        app_version: "sim"
      },
      acks: acks
    }
  }).then(function(res){
    if (!res.ok) { $("cfg").textContent = "poll failed: " + (res.error || "?") + " (device unpaired?)"; return; }
    if (res.commands && res.commands.length) {
      logCmd(res.commands);
      acks = res.commands.map(function(c){ return c.id; });
    }
    renderCfg(res.config);
  });
}
showMain();
if (TOKEN) doPoll();
</script>
</body>
</html>`;

// src/worker.js
var JSONH = { "content-type": "application/json; charset=utf-8" };
var SESSION_TTL_MS = 30 * 24 * 3600 * 1e3;
var MAGIC_TTL_MS = 15 * 60 * 1e3;
var PAIR_TTL_MS = 10 * 60 * 1e3;
var COOKIE = "dg_session";
var DEFAULT_DEVICE_SETTINGS = {
  limit_mb: 750,
  period: "daily",
  // daily | monthly
  hard_mode: false,
  unlock_minutes: 15,
  offline_tolerance_min: 10,
  // fail-closed threshold for the child app
  poll_interval_sec: 30
};
var now = /* @__PURE__ */ __name(() => Date.now(), "now");
var json = /* @__PURE__ */ __name((data, status = 200, headers = {}) => new Response(JSON.stringify(data), { status, headers: { ...JSONH, ...headers } }), "json");
var badRequest = /* @__PURE__ */ __name((msg) => json({ error: msg }, 400), "badRequest");
var unauthorized = /* @__PURE__ */ __name((msg = "unauthorized") => json({ error: msg }, 401), "unauthorized");
var notFound = /* @__PURE__ */ __name(() => json({ error: "not found" }, 404), "notFound");
async function sha256hex(s) {
  const buf = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s));
  return [...new Uint8Array(buf)].map((b) => b.toString(16).padStart(2, "0")).join("");
}
__name(sha256hex, "sha256hex");
function randomToken(bytes = 32) {
  const arr = new Uint8Array(bytes);
  crypto.getRandomValues(arr);
  return [...arr].map((b) => b.toString(16).padStart(2, "0")).join("");
}
__name(randomToken, "randomToken");
function randomPairCode() {
  const n = crypto.getRandomValues(new Uint32Array(1))[0] % 1e6;
  return String(n).padStart(6, "0");
}
__name(randomPairCode, "randomPairCode");
async function readJsonBody(request, maxBytes = 8192) {
  const text = await request.text();
  if (text.length > maxBytes) throw new Error("body too large");
  if (!text) return {};
  try {
    return JSON.parse(text);
  } catch {
    throw new Error("invalid json");
  }
}
__name(readJsonBody, "readJsonBody");
function getCookie(request, name) {
  const header = request.headers.get("cookie") || "";
  for (const part of header.split(";")) {
    const [k, ...rest] = part.trim().split("=");
    if (k === name) return rest.join("=");
  }
  return null;
}
__name(getCookie, "getCookie");
async function rateLimit(kv, key, limit, windowSec) {
  const k = "rl:" + key;
  const raw = await kv.get(k);
  if (raw === null) {
    await kv.put(k, "1", { expirationTtl: windowSec });
    return { ok: true, count: 1 };
  }
  const count = parseInt(raw, 10) + 1;
  await kv.put(k, String(count), { expirationTtl: windowSec });
  return { ok: count <= limit, count };
}
__name(rateLimit, "rateLimit");
function sessionCookie(token, maxAgeSec) {
  return `${COOKIE}=${token}; HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=${maxAgeSec}`;
}
__name(sessionCookie, "sessionCookie");
function clientIp(request) {
  return request.headers.get("cf-connecting-ip") || "unknown";
}
__name(clientIp, "clientIp");
var EMAIL_RE = /^[^\s@]{1,64}@[^\s@]{1,255}\.[^\s@]{2,}$/;
async function writeAudit(db, { parent_id = null, device_id = null, action, detail = null }) {
  try {
    await db.prepare("INSERT INTO audit_log (parent_id, device_id, action, detail_json, ts) VALUES (?,?,?,?,?)").bind(parent_id, device_id, action, detail ? JSON.stringify(detail) : null, now()).run();
  } catch (e) {
    console.error("audit write failed:", e && e.message);
  }
}
__name(writeAudit, "writeAudit");
async function createSession(db, parent_id) {
  const token = randomToken(32);
  const token_hash = await sha256hex(token);
  const t = now();
  await db.prepare(
    "INSERT INTO sessions (parent_id, token_hash, created_at, expires_at) VALUES (?,?,?,?)"
  ).bind(parent_id, token_hash, t, t + SESSION_TTL_MS).run();
  await db.prepare("DELETE FROM sessions WHERE expires_at < ?").bind(t).run().catch(() => {
  });
  return token;
}
__name(createSession, "createSession");
async function parentFromSession(db, request) {
  const token = getCookie(request, COOKIE);
  if (!token) return null;
  const token_hash = await sha256hex(token);
  const row = await db.prepare(
    `SELECT p.id, p.email, s.expires_at FROM sessions s JOIN parents p ON p.id = s.parent_id
     WHERE s.token_hash = ?`
  ).bind(token_hash).first();
  if (!row || row.expires_at < now()) return null;
  return { id: row.id, email: row.email };
}
__name(parentFromSession, "parentFromSession");
async function deviceFromToken(db, request) {
  const auth = request.headers.get("authorization") || "";
  const m = auth.match(/^Bearer\s+([0-9a-f]{64})$/i);
  if (!m) return null;
  const token_hash = await sha256hex(m[1].toLowerCase());
  const row = await db.prepare(
    "SELECT id, parent_id, name, settings_json, revoked, hot_until FROM devices WHERE token_hash = ?"
  ).bind(token_hash).first();
  if (!row || row.revoked) return null;
  return row;
}
__name(deviceFromToken, "deviceFromToken");
async function ensureParent(db, email) {
  const existing = await db.prepare("SELECT id FROM parents WHERE email = ?").bind(email).first();
  if (existing) return existing.id;
  const r = await db.prepare("INSERT INTO parents (email, created_at) VALUES (?,?)").bind(email, now()).run();
  return r.meta.last_row_id;
}
__name(ensureParent, "ensureParent");
var STATIC = {
  "/": /* @__PURE__ */ __name(() => new Response(DASHBOARD_HTML, { headers: { "content-type": "text/html; charset=utf-8", "cache-control": "no-store", "content-security-policy": "default-src 'self'; style-src 'self' 'unsafe-inline'; connect-src 'self'; img-src 'self' data:; frame-ancestors 'none'", "x-frame-options": "DENY", "referrer-policy": "no-referrer" } }), "/"),
  "/app.js": /* @__PURE__ */ __name(() => new Response(DASHBOARD_JS, { headers: { "content-type": "application/javascript; charset=utf-8", "cache-control": "no-store" } }), "/app.js"),
  "/styles.css": /* @__PURE__ */ __name(() => new Response(DASHBOARD_CSS, { headers: { "content-type": "text/css; charset=utf-8", "cache-control": "no-store" } }), "/styles.css"),
  "/manifest.webmanifest": /* @__PURE__ */ __name(() => new Response(MANIFEST_JSON, { headers: { "content-type": "application/manifest+json; charset=utf-8" } }), "/manifest.webmanifest"),
  "/sw.js": /* @__PURE__ */ __name(() => new Response(SW_JS, { headers: { "content-type": "application/javascript; charset=utf-8", "cache-control": "no-store" } }), "/sw.js"),
  "/icon.svg": /* @__PURE__ */ __name(() => new Response(ICON_SVG, { headers: { "content-type": "image/svg+xml" } }), "/icon.svg"),
  "/demo": /* @__PURE__ */ __name(() => new Response(DEMO_HTML, { headers: { "content-type": "text/html; charset=utf-8", "cache-control": "no-store", "content-security-policy": "default-src 'self'; script-src 'unsafe-inline'; style-src 'self' 'unsafe-inline'; connect-src 'self'; frame-ancestors 'none'", "x-frame-options": "DENY", "referrer-policy": "no-referrer", "robots": "noindex, nofollow" } }), "/demo")
};
async function handleRequestLink(request, env) {
  const body = await readJsonBody(request);
  const email = String(body.email || "").trim().toLowerCase();
  if (!EMAIL_RE.test(email)) return badRequest("invalid email");
  const byIp = await rateLimit(env.KV, `link-ip:${clientIp(request)}`, 20, 3600);
  const byEmail = await rateLimit(env.KV, `link-em:${email}`, 5, 3600);
  if (!byIp.ok || !byEmail.ok) return json({ error: "too many requests, try again later" }, 429);
  const token = randomToken(32);
  const token_hash = await sha256hex(token);
  const t = now();
  await env.DB.prepare(
    "INSERT INTO magic_links (email, token_hash, created_at, expires_at) VALUES (?,?,?,?)"
  ).bind(email, token_hash, t, t + MAGIC_TTL_MS).run();
  await env.DB.prepare("DELETE FROM magic_links WHERE expires_at < ? OR used_at IS NOT NULL").bind(t - 864e5).run().catch(() => {
  });
  const link = `${new URL(request.url).origin}/api/auth/verify?token=${token}`;
  let delivered = false;
  let dev_link = null;
  if (env.RESEND_API_KEY) {
    try {
      const r = await fetch("https://api.resend.com/emails", {
        method: "POST",
        headers: { "authorization": `Bearer ${env.RESEND_API_KEY}`, "content-type": "application/json" },
        body: JSON.stringify({
          from: "Wi-Fi Data Guard <onboarding@resend.dev>",
          to: [email],
          subject: "Your sign-in link",
          html: `<p>Click to sign in to your Wi-Fi Data Guard dashboard:</p>
                 <p><a href="${link}">Sign in</a></p>
                 <p>This link is valid for 15 minutes and can be used once.
                 If you did not request it, ignore this email.</p>`
        })
      });
      delivered = r.ok;
    } catch {
      delivered = false;
    }
  }
  if (!delivered && env.BOOTSTRAP_EMAIL && email === env.BOOTSTRAP_EMAIL.toLowerCase()) {
    dev_link = link;
  }
  await writeAudit(env.DB, { action: "link_requested", detail: { email, delivered, bootstrap: !!dev_link } });
  if (!delivered && !dev_link) {
    return json({ ok: false, error: "email delivery is not configured for this deployment" }, 503);
  }
  return json({ ok: true, delivered, dev_link, note: dev_link ? "bootstrap mode: link shown because email sending is not configured" : void 0 });
}
__name(handleRequestLink, "handleRequestLink");
async function handleVerify(request, env) {
  const url = new URL(request.url);
  const token = url.searchParams.get("token") || "";
  if (!/^[0-9a-f]{64}$/i.test(token)) return badRequest("invalid token");
  const token_hash = await sha256hex(token.toLowerCase());
  const row = await env.DB.prepare(
    "SELECT id, email, expires_at, used_at FROM magic_links WHERE token_hash = ?"
  ).bind(token_hash).first();
  if (!row) return unauthorized("link not found or already used");
  if (row.used_at) return unauthorized("link already used");
  if (row.expires_at < now()) return unauthorized("link expired");
  await env.DB.prepare("UPDATE magic_links SET used_at = ? WHERE id = ?").bind(now(), row.id).run();
  const parent_id = await ensureParent(env.DB, row.email);
  await env.DB.prepare("UPDATE parents SET last_login_at = ? WHERE id = ?").bind(now(), parent_id).run();
  const session = await createSession(env.DB, parent_id);
  await writeAudit(env.DB, { parent_id, action: "login" });
  return new Response(null, {
    status: 302,
    headers: { "location": "/", "set-cookie": sessionCookie(session, SESSION_TTL_MS / 1e3) }
  });
}
__name(handleVerify, "handleVerify");
async function handleLogout(request, env) {
  const token = getCookie(request, COOKIE);
  if (token) {
    await env.DB.prepare("DELETE FROM sessions WHERE token_hash = ?").bind(await sha256hex(token)).run().catch(() => {
    });
  }
  return json({ ok: true }, 200, { "set-cookie": sessionCookie("", 0) });
}
__name(handleLogout, "handleLogout");
async function handleMe(request, env, parent) {
  const devices = await env.DB.prepare(
    "SELECT id, name, created_at, last_seen_at, last_report_json, settings_json, hot_until, last_wait_poll_at FROM devices WHERE parent_id = ? AND revoked = 0 ORDER BY id"
  ).bind(parent.id).all();
  // spec-005 FR-002: an authenticated dashboard heartbeat (every 10 s while
  // the tab is visible) keeps this parent's devices "hot" — eligible for
  // long-poll holds — for 75 s. Tab closed/hidden -> heartbeat stops ->
  // hot expires -> devices return to their normal poll cycle.
  await env.DB.prepare(
    "UPDATE devices SET hot_until = ? WHERE parent_id = ? AND revoked = 0"
  ).bind(now() + 75e3, parent.id).run();
  const pendingCounts = await env.DB.prepare(
    "SELECT device_id, type, COUNT(*) AS n FROM commands WHERE delivered_at IS NULL GROUP BY device_id, type"
  ).all();
  const pending = {};
  const pendingTypes = {};
  for (const r of pendingCounts.results || []) {
    pending[r.device_id] = (pending[r.device_id] || 0) + r.n;
    (pendingTypes[r.device_id] = pendingTypes[r.device_id] || {})[r.type] = r.n;
  }
  return json({
    ok: true,
    parent: { email: parent.email },
    email_configured: !!env.RESEND_API_KEY,
    devices: (devices.results || []).map((d) => ({
      id: d.id,
      name: d.name,
      created_at: d.created_at,
      last_seen_at: d.last_seen_at,
      online: !!(d.last_seen_at && now() - d.last_seen_at < 9e4),
      settings: { ...DEFAULT_DEVICE_SETTINGS, ...JSON.parse(d.settings_json || "{}") },
      report: d.last_report_json ? JSON.parse(d.last_report_json) : null,
      pending_commands: pending[d.id] || 0,
      pending_types: pendingTypes[d.id] || {},
      fast: !!(d.last_wait_poll_at && now() - d.last_wait_poll_at < 45e3),
    }))
  });
}
__name(handleMe, "handleMe");
async function handlePairCode(request, env, parent) {
  const rl = await rateLimit(env.KV, `paircode:${parent.id}`, 20, 3600);
  if (!rl.ok) return json({ error: "too many pairing codes, try again later" }, 429);
  const code = randomPairCode();
  const t = now();
  await env.DB.prepare(
    "INSERT INTO pair_codes (parent_id, code_hash, created_at, expires_at) VALUES (?,?,?,?)"
  ).bind(parent.id, await sha256hex(code), t, t + PAIR_TTL_MS).run();
  await writeAudit(env.DB, { parent_id: parent.id, action: "pair_code_created" });
  return json({ ok: true, code, expires_in_sec: PAIR_TTL_MS / 1e3 });
}
__name(handlePairCode, "handlePairCode");
async function handleCommand(request, env, parent, deviceId) {
  if (!/^\d+$/.test(deviceId)) return badRequest("bad device id");
  const device = await env.DB.prepare(
    "SELECT id, parent_id, settings_json FROM devices WHERE id = ? AND revoked = 0"
  ).bind(deviceId).first();
  if (!device || device.parent_id !== parent.id) return notFound();
  const body = await readJsonBody(request);
  const type = String(body.type || "");
  if (!["lock", "unlock", "config"].includes(type)) return badRequest("unknown command type");
  let payload = {};
  if (type === "unlock") {
    // spec-003 FR-001: full unlock clears the device latch until the next
    // lock; the minutes path is unchanged (clamp 1..480, default 15)
    if (body.payload && body.payload.full === true) {
      payload = { full: true };
    } else {
      const mins = parseInt(body.payload && body.payload.minutes, 10);
      payload.minutes = Number.isFinite(mins) && mins > 0 && mins <= 480 ? mins : 15;
    }
  } else if (type === "config") {
    const p = body.payload || {};
    const s = {};
    if (p.limit_mb !== void 0) s.limit_mb = Math.max(1, Math.min(1e6, parseInt(p.limit_mb, 10) || 0));
    if (p.period !== void 0) s.period = p.period === "monthly" ? "monthly" : "daily";
    if (p.hard_mode !== void 0) s.hard_mode = !!p.hard_mode;
    if (p.unlock_minutes !== void 0) s.unlock_minutes = Math.max(0, Math.min(1440, parseInt(p.unlock_minutes, 10) || 0));
    if (p.offline_tolerance_min !== void 0) s.offline_tolerance_min = Math.max(1, Math.min(1440, parseInt(p.offline_tolerance_min, 10) || 10));
    if (p.poll_interval_sec !== void 0) s.poll_interval_sec = Math.max(15, Math.min(600, parseInt(p.poll_interval_sec, 10) || 30));
    if (Object.keys(s).length === 0) return badRequest("empty config");
    payload = s;
    const current = JSON.parse(device.settings_json || "{}");
    await env.DB.prepare("UPDATE devices SET settings_json = ? WHERE id = ?").bind(JSON.stringify({ ...current, ...s }), device.id).run();
  } else {
    const reason = String(body.payload && body.payload.reason || "").slice(0, 120);
    if (reason) payload.reason = reason;
  }
  await env.DB.prepare(
    "INSERT INTO commands (device_id, parent_id, type, payload_json, created_at) VALUES (?,?,?,?,?)"
  ).bind(device.id, parent.id, type, JSON.stringify(payload), now()).run();
  // spec-005 FR-003: commanding a device extends its hot window so the
  // ack + report land on the fast path too
  await env.DB.prepare("UPDATE devices SET hot_until = MAX(hot_until, ?) WHERE id = ?").bind(now() + 120e3, device.id).run();
  await writeAudit(env.DB, { parent_id: parent.id, device_id: device.id, action: "command_created", detail: { type, payload } });
  return json({ ok: true });
}
__name(handleCommand, "handleCommand");
async function handleRevoke(request, env, parent, deviceId) {
  if (!/^\d+$/.test(deviceId)) return badRequest("bad device id");
  const device = await env.DB.prepare("SELECT id, parent_id FROM devices WHERE id = ?").bind(deviceId).first();
  if (!device || device.parent_id !== parent.id) return notFound();
  await env.DB.prepare("UPDATE devices SET revoked = 1, token_hash = ? WHERE id = ?").bind("revoked-" + randomToken(16), device.id).run();
  await writeAudit(env.DB, { parent_id: parent.id, device_id: device.id, action: "device_revoked" });
  return json({ ok: true });
}
__name(handleRevoke, "handleRevoke");
async function handleReports(request, env, parent, deviceId) {
  if (!/^\d+$/.test(deviceId)) return badRequest("bad device id");
  const device = await env.DB.prepare("SELECT id, parent_id FROM devices WHERE id = ? AND revoked = 0").bind(deviceId).first();
  if (!device || device.parent_id !== parent.id) return notFound();
  const rows = await env.DB.prepare(
    "SELECT ts, used_bytes, latched FROM usage_reports WHERE device_id = ? ORDER BY ts DESC LIMIT 96"
  ).bind(deviceId).all();
  return json({ ok: true, reports: (rows.results || []).reverse() });
}
__name(handleReports, "handleReports");
async function handleAudit(request, env, parent) {
  const rows = await env.DB.prepare(
    "SELECT action, detail_json, ts, device_id FROM audit_log WHERE parent_id = ? ORDER BY ts DESC LIMIT 50"
  ).bind(parent.id).all();
  return json({ ok: true, entries: rows.results || [] });
}
__name(handleAudit, "handleAudit");
async function handleChildPair(request, env) {
  const rl = await rateLimit(env.KV, `pair:${clientIp(request)}`, 30, 3600);
  if (!rl.ok) return json({ error: "too many attempts" }, 429);
  let body;
  try {
    body = await readJsonBody(request);
  } catch {
    return badRequest("invalid json");
  }
  const code = String(body.code || "").trim();
  const name = String(body.name || "").trim().slice(0, 40) || "Android device";
  if (!/^\d{6}$/.test(code)) return badRequest("code must be 6 digits");
  const code_hash = await sha256hex(code);
  const row = await env.DB.prepare(
    "SELECT id, parent_id, expires_at, attempts, used FROM pair_codes WHERE code_hash = ? AND used = 0 ORDER BY id DESC LIMIT 1"
  ).bind(code_hash).first();
  if (!row) return unauthorized("invalid code");
  if (row.expires_at < now()) return unauthorized("code expired");
  if (row.attempts >= 10) return unauthorized("code locked");
  const consume = await env.DB.prepare(
    "UPDATE pair_codes SET used = 1 WHERE id = ? AND used = 0"
  ).bind(row.id).run();
  if (!consume.meta.changes) return unauthorized("code already used");
  const device_token = randomToken(32);
  const t = now();
  const res = await env.DB.prepare(
    "INSERT INTO devices (parent_id, name, token_hash, created_at, settings_json) VALUES (?,?,?,?,?)"
  ).bind(row.parent_id, name, await sha256hex(device_token), t, JSON.stringify(DEFAULT_DEVICE_SETTINGS)).run();
  const device_id = res.meta.last_row_id;
  await writeAudit(env.DB, { parent_id: row.parent_id, device_id, action: "device_paired", detail: { name } });
  return json({ ok: true, device_token, device_id, config: DEFAULT_DEVICE_SETTINGS });
}
__name(handleChildPair, "handleChildPair");
// spec-005: poll-hold helpers (unit-tested in scripts/verify_spec005.js)
function clampWait(v){
  return Math.max(0, Math.min(25, parseInt(v, 10) || 0));
}
function holdEligible(waitSec, hotUntil, nowMs){
  return waitSec > 0 && hotUntil > nowMs;
}
async function handleChildPoll(request, env) {
  const device = await deviceFromToken(env.DB, request);
  if (!device) return unauthorized("invalid device token");
  let body = {};
  try {
    body = await readJsonBody(request);
  } catch {
  }
  const t = now();
  const db = env.DB;
  // spec-005 FR-004: the device may ask the server to HOLD this poll for up
  // to 25 s waiting for a command; the hold only happens while the device
  // is hot (parent's panel active). Old apps never send wait -> 0 -> today's
  // immediate behavior.
  const waitSec = clampWait(body.wait);
  const report = body.report || null;
  if (report && typeof report === "object") {
    const clean = {
      used_bytes: Math.max(0, parseInt(report.used_bytes, 10) || 0),
      limit_bytes: Math.max(0, parseInt(report.limit_bytes, 10) || 0),
      latched: !!report.latched,
      grace_until: Math.max(0, parseInt(report.grace_until, 10) || 0),
      battery_pct: Math.max(0, Math.min(100, parseInt(report.battery_pct, 10) || 0)),
      app_version: String(report.app_version || "").slice(0, 20),
      ts: t
    };
    await db.prepare("UPDATE devices SET last_seen_at = ?, last_wait_poll_at = CASE WHEN ? > 0 THEN ? ELSE last_wait_poll_at END, last_report_json = ? WHERE id = ?").bind(t, waitSec, t, JSON.stringify(clean), device.id).run();
    const last = await db.prepare(
      "SELECT ts FROM usage_reports WHERE device_id = ? ORDER BY ts DESC LIMIT 1"
    ).bind(device.id).first();
    if (!last || t - last.ts > 10 * 60 * 1e3) {
      await db.prepare("INSERT INTO usage_reports (device_id, ts, used_bytes, latched, extra_json) VALUES (?,?,?,?,?)").bind(device.id, t, clean.used_bytes, clean.latched ? 1 : 0, JSON.stringify({ battery_pct: clean.battery_pct })).run().catch(() => {
      });
      await db.prepare("DELETE FROM usage_reports WHERE device_id = ? AND ts < ?").bind(device.id, t - 60 * 864e5).run().catch(() => {
      });
    }
  } else {
    await db.prepare("UPDATE devices SET last_seen_at = ?, last_wait_poll_at = CASE WHEN ? > 0 THEN ? ELSE last_wait_poll_at END WHERE id = ?").bind(t, waitSec, t, device.id).run();
  }
  const acks = Array.isArray(body.acks) ? body.acks.filter((x) => Number.isInteger(x)).slice(0, 50) : [];
  if (acks.length) {
    const ph = acks.map(() => "?").join(",");
    await db.prepare(`UPDATE commands SET acked_at = ? WHERE id IN (${ph}) AND device_id = ? AND acked_at IS NULL`).bind(t, ...acks, device.id).run();
    await writeAudit(db, { parent_id: device.parent_id, device_id: device.id, action: "command_acked", detail: { ids: acks } });
  }
  let pending = await db.prepare(
    "SELECT id, type, payload_json FROM commands WHERE device_id = ? AND delivered_at IS NULL ORDER BY id LIMIT 20"
  ).bind(device.id).all();
  // spec-005 FR-004: hot-mode long-poll — hold up to `wait` seconds for a
  // command to appear, but ONLY while the parent's panel keeps this device
  // hot. The loop checks D1 every 1.5 s; a command inserted by the dashboard
  // lands in ~1.5 s instead of the next poll cycle.
  if (!(pending.results && pending.results.length) && holdEligible(waitSec, device.hot_until, Date.now())) {
    const deadline = Date.now() + waitSec * 1000;
    while (Date.now() < deadline) {
      await new Promise((r) => setTimeout(r, Math.min(1500, deadline - Date.now())));
      pending = await db.prepare(
        "SELECT id, type, payload_json FROM commands WHERE device_id = ? AND delivered_at IS NULL ORDER BY id LIMIT 20"
      ).bind(device.id).all();
      if (pending.results && pending.results.length) break;
    }
  }
  if (pending.results && pending.results.length) {
    const ids = pending.results.map((r) => r.id);
    const ph = ids.map(() => "?").join(",");
    await db.prepare(`UPDATE commands SET delivered_at = ? WHERE id IN (${ph})`).bind(t, ...ids).run();
    await writeAudit(db, { parent_id: device.parent_id, device_id: device.id, action: "command_delivered", detail: { ids } });
  }
  if (Math.random() < 0.05) {
    await db.prepare("DELETE FROM commands WHERE acked_at IS NOT NULL AND created_at < ?").bind(t - 7 * 864e5).run().catch(() => {
    });
  }
  return json({
    ok: true,
    commands: (pending.results || []).map((r) => ({ id: r.id, type: r.type, payload: JSON.parse(r.payload_json || "{}") })),
    config: { ...DEFAULT_DEVICE_SETTINGS, ...JSON.parse(device.settings_json || "{}") },
    server_time: t,
    // spec-005: server-side truth (clock-skew-immune for the app): keep
    // fast-polling while the device is hot
    fast: device.hot_until > Date.now()
  });
}
__name(handleChildPoll, "handleChildPoll");
var worker_default = {
  async fetch(request, env) {
    const url = new URL(request.url);
    const path = url.pathname;
    const method = request.method;
    try {
      if (method === "GET" && STATIC[path]) return STATIC[path]();
      if (path === "/api/auth/request-link" && method === "POST") return await handleRequestLink(request, env);
      if (path === "/api/auth/verify" && method === "GET") return await handleVerify(request, env);
      if (path === "/api/auth/logout" && method === "POST") return await handleLogout(request, env);
      if (path === "/api/child/pair" && method === "POST") return await handleChildPair(request, env);
      if (path === "/api/child/poll" && method === "POST") return await handleChildPoll(request, env);
      const parent = await parentFromSession(env.DB, request);
      if (path === "/api/me" && method === "GET") {
        if (!parent) return unauthorized();
        return await handleMe(request, env, parent);
      }
      if (path === "/api/pair-code" && method === "POST") {
        if (!parent) return unauthorized();
        return await handlePairCode(request, env, parent);
      }
      if (path === "/api/audit" && method === "GET") {
        if (!parent) return unauthorized();
        return await handleAudit(request, env, parent);
      }
      const cmdMatch = path.match(/^\/api\/devices\/(\d+)\/command$/);
      if (cmdMatch && method === "POST") {
        if (!parent) return unauthorized();
        return await handleCommand(request, env, parent, cmdMatch[1]);
      }
      const revokeMatch = path.match(/^\/api\/devices\/(\d+)\/revoke$/);
      if (revokeMatch && method === "POST") {
        if (!parent) return unauthorized();
        return await handleRevoke(request, env, parent, revokeMatch[1]);
      }
      const reportsMatch = path.match(/^\/api\/devices\/(\d+)\/reports$/);
      if (reportsMatch && method === "GET") {
        if (!parent) return unauthorized();
        return await handleReports(request, env, parent, reportsMatch[1]);
      }
      return notFound();
    } catch (err) {
      console.error("worker error:", err && err.message, err && err.stack);
      return json({ error: "internal error" }, 500);
    }
  }
};
export {
  worker_default as default
};
//# sourceMappingURL=worker.js.map
