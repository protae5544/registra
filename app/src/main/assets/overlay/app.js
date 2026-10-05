"use strict";
/* ============================================================
   Overlay Composer — สร้างเทมเพลตเอกสารจากภาพ/PDF ต้นฉบับ
   ฟีเจอร์หลัก:
   - หลายหน้า (โหลด PDF ได้ทุกหน้า, หลายภาพ = หลายหน้า, เพิ่ม/ลบหน้าเองได้)
   - เพิ่ม/ลบฟิลด์ (text/image/qr) ได้อิสระ
   - ลากย้าย + ลากปรับขนาด + กรอกตำแหน่งตัวเลข (หน่วย % ของหน้า)
   - พื้นหลังเป็นภาพตัวอย่าง (jpg/png/webp/svg) หรือ PDF
   - กรอกข้อมูลหลาย record, Export/Import layout, PNG/PDF
   ============================================================ */
(function () {
  const $ = (id) => document.getElementById(id);
  const uid = () => "o" + Math.random().toString(36).slice(2, 10);
  const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
  const round = (v, d) => { const m = 10 ** (d == null ? 2 : d); return Math.round((v + Number.EPSILON) * m) / m; };
  const A4 = { w: 794, h: 1123 };
  const DRAFT_KEY = "oc_draft_v2";

  /* ---------------- state ---------------- */
  const state = {
    pages: [{ id: uid(), bg: null, objects: [] }], // bg: {kind:'image'|'pdf', src, w, h, name}
    records: [],
    recIdx: 0,
    pageIdx: 0,
    viewAll: false,
    snap: true,
    sel: null, // {page, id}
    fonts: [], // {id, name, family, weight, italic, format, mime, dataUrl, embed}
    defaultFamily: "",
    fieldSel: [], // object ids ที่ติ้กไว้สำหรับปรับหลายฟิลด์พร้อมกัน
    cross: false, // โหมดเป้ากากบาท
    crossPos: null, // {x, y} หน่วย % บนหน้า
    printOrder: null, // ลำดับ record ที่ผู้ใช้จัด (null = ตามธรรมชาติ)
    printOff: {}, // {recIndex:true} = ไม่เอาพิมพ์ชุดนี้
    printNameKey: "",
    printNameTpl: "{n}_{key}",
    printScope: "all"
  };
  let history = []; // {name, time, thumb, dataUrl}
  let pendingImageAdd = null; // callback สำหรับเลือกรูปเพิ่มเป็น object

  /* ---------------- toast / status / loader ---------------- */
  let toastTimer = null;
  function toast(msg, cls) {
    const t = $("toast");
    t.textContent = msg;
    t.className = (cls || "") + " show";
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { t.className = ""; }, 2400);
  }
  function status(msg, cls) {
    const el = $("opStatus");
    el.textContent = msg;
    el.className = "status " + (cls || "");
  }
  function loadShow(msg) {
    $("loadMsg").textContent = msg || "กำลังทำงาน…";
    $("progBar").style.width = "0%";
    $("progPct").textContent = "";
    $("loadWrap").classList.add("show");
  }
  function loadProg(pct, msg) {
    if (msg) $("loadMsg").textContent = msg;
    if (pct != null) {
      $("progBar").style.width = clamp(pct, 0, 100) + "%";
      $("progPct").textContent = Math.round(pct) + "%";
    }
  }
  function loadHide() { $("loadWrap").classList.remove("show"); }

  /* ---------------- helpers ---------------- */
  const curPage = () => state.pages[state.pageIdx] || null;
  function getObj(pageIdx, id) {
    const p = state.pages[pageIdx];
    return p ? p.objects.find((o) => o.id === id) : null;
  }
  function selected() {
    if (!state.sel) return null;
    return getObj(state.sel.page, state.sel.id);
  }
  function maxZ(page) { return page.objects.reduce((m, o) => Math.max(m, o.z || 0), 0); }
  function curRecord() { return state.records[state.recIdx] || null; }
  function objValue(o, rec) {
    if (o.key && rec && rec[o.key] != null && String(rec[o.key]) !== "") return String(rec[o.key]);
    if (o.type === "stamp") return stampText(o);
    if (o.type === "qr") return o.text || "";
    return "";
  }
  function placeholderFor(o) {
    if (o.type === "stamp") return stampText(o);
    return o.text || o.key || (o.type === "qr" ? "qr" : "ข้อความ");
  }
  function allKeys() {
    const set = [];
    state.pages.forEach((p) => p.objects.forEach((o) => {
      if (o.key && set.indexOf(o.key) < 0) set.push(o.key);
    }));
    (curRecord() ? Object.keys(curRecord()) : []).forEach((k) => { if (set.indexOf(k) < 0) set.push(k); });
    state.records.forEach((r) => Object.keys(r || {}).forEach((k) => { if (set.indexOf(k) < 0) set.push(k); }));
    return set;
  }

  /* ---------------- stamp: element ตายตัวในเทมเพลต แต่คำนวณเป็นเวลาปัจจุบัน ----------------
     ผู้ใช้เลือกได้ว่าจะแสดง date / time / date+time / เดือน+ปี และเลื่อนได้ -12..+12 เดือน
     (บวก = อนาคต, ลบ = อดีต) อ้างอิงจากเวลาปัจจุบัน หรือจากวันที่ตายตัวที่ผู้ใช้กำหนดเอง */
  const STAMP_PARTS = { date: 1, time: 1, datetime: 1, month: 1 };
  function stampCfg(o) {
    o = o || {};
    const sh = parseInt(o.stampShift, 10);
    return {
      part: STAMP_PARTS[o.stampPart] ? o.stampPart : "datetime",
      mode: o.stampMode === "base" ? "base" : "now",
      base: typeof o.stampBase === "string" ? o.stampBase.trim() : "",
      shift: isNaN(sh) ? 0 : clamp(sh, -12, 12)
    };
  }
  function parseYmd(s) {
    const m = /^(\d{4})-(\d{1,2})-(\d{1,2})/.exec(String(s || ""));
    if (!m) return null;
    const d = new Date(+m[1], +m[2] - 1, +m[3]);
    if (isNaN(d.getTime()) || d.getMonth() !== +m[2] - 1 || d.getDate() !== +m[3]) return null;
    return d;
  }
  function addMonths(d, n) {
    const day = d.getDate();
    const nd = new Date(d.getFullYear(), d.getMonth() + n, 1, d.getHours(), d.getMinutes(), d.getSeconds(), d.getMilliseconds());
    nd.setDate(Math.min(day, new Date(nd.getFullYear(), nd.getMonth() + 1, 0).getDate()));
    return nd;
  }
  function stampWhen(o, now) {
    const c = stampCfg(o);
    let d = c.mode === "base" ? parseYmd(c.base) : null;
    if (!d) d = new Date(now == null ? Date.now() : now);
    if (c.shift) d = addMonths(d, c.shift);
    return d;
  }
  function stampText(o, now) {
    const d = stampWhen(o, now);
    const c = stampCfg(o);
    const date = pad2(d.getDate()) + "/" + pad2(d.getMonth() + 1) + "/" + d.getFullYear();
    const time = pad2(d.getHours()) + ":" + pad2(d.getMinutes());
    if (c.part === "date") return date;
    if (c.part === "time") return time;
    if (c.part === "month") return pad2(d.getMonth() + 1) + "/" + d.getFullYear();
    return date + " " + time;
  }

  /* ---------------- fonts (ผู้ใช้แนบเอง — ฝังลงไฟล์ export) ---------------- */
  const FONT_FALLBACK = '"THSarabunNew","Sarabun","Noto Sans Thai","Times New Roman",serif';
  const FONT_FORMATS = { ttf: "truetype", otf: "opentype", ttc: "truetype", otc: "opentype", woff: "woff", woff2: "woff2" };
  const FONT_MIMES = { ttf: "font/ttf", otf: "font/otf", ttc: "font/ttf", otc: "font/otf", woff: "font/woff", woff2: "font/woff2" };
  const STYLE_WORDS = /[-_ ](bold|italic|oblique|regular|book|medium|semibold|demibold|heavy|black|extrabold|ultrabold|thin|light|bd|b|it|blackitalic)(?![a-z])/gi;

  const cssEsc = (s) => String(s).replace(/\\/g, "\\\\").replace(/"/g, '\\"');
  const pad2 = (n) => (n < 10 ? "0" + n : String(n));
  function dateStamp() {
    const d = new Date();
    return d.getFullYear() + pad2(d.getMonth() + 1) + pad2(d.getDate());
  }

  // อ่านชื่อไฟล์ฟอนต์แยกครอบครัว/น้ำหนัก/สไตล์ เช่น THSarabunNew+Bold.woff2 -> {family:"THSarabunNew", weight:700}
  function fontInfoFromName(fname) {
    const name = String(fname || "");
    const dot = name.lastIndexOf(".");
    const ext = (dot >= 0 ? name.slice(dot + 1) : "").toLowerCase();
    const base = (dot >= 0 ? name.slice(0, dot) : name).replace(/\+/g, " ").trim();
    const italic = /italic|oblique|(^|[-_ ])it($|[-_ ])/i.test(base);
    const bold = /bold|heavy|black|700|600|(^|[-_ ])(bd|b)(?![a-z])/i.test(base);
    let family = base.replace(STYLE_WORDS, " ");
    family = family.replace(/[<>{}]/g, ""); // กันชื่อไฟล์แปลกชี้ไปจนพัง style ในไฟล์ export
    family = family.replace(/[-_.,#()\[\]]+/g, " ").replace(/\s+/g, " ").trim();
    if (!family) family = "CustomFont";
    return {
      family: family,
      weight: bold ? 700 : 400,
      italic: !!italic,
      format: FONT_FORMATS[ext] || "truetype",
      mime: FONT_MIMES[ext] || "font/ttf",
      ext: ext
    };
  }

  function fontFamilies() {
    const out = [];
    state.fonts.forEach((f) => { if (out.indexOf(f.family) < 0) out.push(f.family); });
    return out;
  }
  function familyHasBold(fam) {
    return state.fonts.some((f) => f.family === fam && (f.weight >= 600 || f.bold));
  }
  // ฟอนต์ที่ object นี้ใช้จริง (fallback ไปฟอนต์หลัก -> ค่าเริ่มต้นเดิม)
  function familyOf(o) {
    const fams = fontFamilies();
    const fam = o && o.family ? o.family : state.defaultFamily;
    return fam && fams.indexOf(fam) >= 0 ? fam : "";
  }
  // ฟอนต์ที่ต้องฝังลงไฟล์ export: ตัวที่ติ้กไว้ + ตัวที่ object ใช้จริง
  function fontsToEmbed() {
    const need = [];
    state.pages.forEach((p) => p.objects.forEach((o) => { const f = familyOf(o); if (f) need.push(f); }));
    if (state.defaultFamily) need.push(state.defaultFamily);
    return state.fonts.filter((f) => f.embed || need.indexOf(f.family) >= 0);
  }
  function fontFaceCss(list) {
    return (list || fontsToEmbed()).map((f) => "@font-face{font-family:\"" + cssEsc(f.family) + "\";src:url("
      + f.dataUrl + ") format(\"" + f.format + "\");font-weight:" + f.weight + ";font-style:"
      + (f.italic ? "italic" : "normal") + ";font-display:block;}").join("\n");
  }
  function syncFontFace() {
    const style = document.createElement("style");
    style.id = "userFontCss";
    style.textContent = fontFaceCss(state.fonts);
    const old = document.getElementById("userFontCss");
    if (old) old.remove();
    document.head.appendChild(style);
  }
  function refreshFontSelectors() {
    const fams = fontFamilies();
    const apply = (sel, val, blankText) => {
      if (!sel) return;
      sel.innerHTML = "";
      const blank = document.createElement("option");
      blank.value = "";
      blank.textContent = blankText;
      sel.appendChild(blank);
      fams.forEach((f) => {
        const op = document.createElement("option");
        op.value = f;
        op.textContent = f + (familyHasBold(f) ? " · มีตัวหนา" : "");
        sel.appendChild(op);
      });
      sel.value = val && fams.indexOf(val) >= 0 ? val : "";
    };
    apply($("edFamily"), (selected() || {}).family || "", "ค่าเริ่มต้นเดิม");
    apply($("bFamily"), $("bFamily") ? $("bFamily").value : "", "ค่าเริ่มต้นเดิม");
    apply($("fontDefault"), state.defaultFamily, "ค่าเริ่มต้นเดิม");
  }

  async function loadFontFiles(files) {
    const list = Array.from(files || []).filter(Boolean);
    if (!list.length) return;
    loadShow("กำลังอ่านไฟล์ฟอนต์…");
    try {
      let added = 0, skipped = 0;
      for (let i = 0; i < list.length; i++) {
        const f = list[i];
        loadProg((i / list.length) * 100, "อ่านฟอนต์ " + f.name);
        const info = fontInfoFromName(f.name);
        try {
          const dataUrl = await readFileDataUrl(f);
          state.fonts.push({
            id: uid(), name: f.name, family: info.family, weight: info.weight,
            italic: info.italic, format: info.format, mime: info.mime, ext: info.ext,
            dataUrl: dataUrl, embed: true
          });
          added++;
        } catch (err) { skipped++; toast("อ่านฟอนต์ไม่ได้: " + f.name, "err"); }
      }
      if (added && !state.defaultFamily) state.defaultFamily = state.fonts[0].family;
      syncFontFace();
      renderFonts();
      refreshFontSelectors();
      refreshAll();
      saveDraftSoon();
      const bolds = fontFamilies().filter(familyHasBold).length;
      $("fontStatus").textContent = "แนบแล้ว " + state.fonts.length + " ไฟล์"
        + (bolds ? " · " + bolds + " ครอบครัวมีตัวหนา" : "");
      $("fontStatus").className = "status ok";
      toast("แนบฟอนต์ " + added + " ไฟล์ (ฝังลงไฟล์ export อัตโนมัติ)" + (skipped ? " · ข้าม " + skipped : ""), "ok");
      status("ฟอนต์ที่แนบ: " + state.fonts.map((x) => x.name).join(", "), "ok");
    } catch (err) {
      console.error(err);
      toast("แนบฟอนต์ไม่สำเร็จ: " + err.message, "err");
    } finally {
      loadHide();
    }
  }

  function clearFonts() {
    state.fonts = [];
    state.defaultFamily = "";
    syncFontFace();
    renderFonts();
    refreshFontSelectors();
    refreshAll();
    saveDraftSoon();
    $("fontStatus").textContent = "ยังไม่มีฟอนต์ที่แนบ";
    $("fontStatus").className = "status muted";
    toast("ลบฟอนต์ที่แนบทั้งหมดแล้ว");
  }

  function renderFonts() {
    const host = $("fontList");
    if (!host) return;
    host.innerHTML = "";
    if (!state.fonts.length) {
      host.innerHTML = '<div class="muted">ยังไม่มีฟอนต์ที่แนบ — เลือกไฟล์ .ttf/.otf/.woff/.woff2 ได้หลายไฟล์</div>';
      return;
    }
    state.fonts.forEach((f) => {
      const row = document.createElement("div");
      row.className = "fontRow" + (f.embed ? "" : " off") + (f.family === state.defaultFamily ? " isdefault" : "");

      const top = document.createElement("div");
      top.className = "row";
      const cb = document.createElement("input");
      cb.type = "checkbox";
      cb.checked = !!f.embed;
      cb.title = "ฝังลงไฟล์ export";
      cb.addEventListener("change", () => {
        f.embed = cb.checked;
        syncFontFace();
        renderFonts();
        saveDraftSoon();
      });
      const nm = document.createElement("div");
      nm.style.cssText = "flex:1;min-width:0";
      const n1 = document.createElement("div");
      n1.className = "fname";
      n1.textContent = f.family + (f.weight >= 600 ? " · ตัวหนา" : "") + (f.italic ? " · เอียง" : "");
      const n2 = document.createElement("div");
      n2.className = "fmeta";
      const kb = Math.max(1, Math.round((f.dataUrl ? f.dataUrl.length : 0) * 0.75 / 1024));
      n2.textContent = f.name + " · " + f.ext.toUpperCase() + " · " + kb + " KB"
        + (familyHasBold(f.family) ? " · ครอบครัวนี้มีตัวหนา" : "");
      nm.appendChild(n1);
      nm.appendChild(n2);

      const acts = document.createElement("span");
      acts.style.cssText = "display:flex;gap:4px";
      const bDef = document.createElement("button");
      bDef.type = "button";
      bDef.className = "mini";
      bDef.textContent = f.family === state.defaultFamily ? "หลัก" : "ทำหลัก";
      bDef.addEventListener("click", () => {
        state.defaultFamily = f.family;
        refreshFontSelectors();
        renderFonts();
        refreshAll();
        saveDraftSoon();
      });
      const bDel = document.createElement("button");
      bDel.type = "button";
      bDel.className = "mini warn";
      bDel.textContent = "ลบ";
      bDel.addEventListener("click", () => {
        state.fonts = state.fonts.filter((x) => x.id !== f.id);
        if (state.defaultFamily === f.family) state.defaultFamily = fontFamilies()[0] || "";
        syncFontFace();
        renderFonts();
        refreshFontSelectors();
        refreshAll();
        saveDraftSoon();
      });
      acts.appendChild(bDef);
      acts.appendChild(bDel);

      top.appendChild(cb);
      top.appendChild(nm);
      top.appendChild(acts);
      row.appendChild(top);

      const sm = document.createElement("div");
      sm.className = "fsample";
      sm.style.fontFamily = '"' + cssEsc(f.family) + '",' + FONT_FALLBACK;
      sm.style.fontWeight = String(f.weight);
      sm.style.fontStyle = f.italic ? "italic" : "normal";
      sm.textContent = "สมชาย 0123 ABC";
      row.appendChild(sm);

      host.appendChild(row);
    });
  }

  /* ---------------- CSS กลาง: ใช้ทั้งพรีวิวและไฟล์ export ตัวเดียวกัน ---------------- */
  function pageScale(pageIdx) {
    const el = document.querySelector('.page[data-idx="' + pageIdx + '"]');
    return el ? (el.clientWidth || A4.w) / A4.w : 1;
  }
  function fontStack(o) {
    const fam = familyOf(o);
    return fam ? '"' + cssEsc(fam) + '",' + FONT_FALLBACK : FONT_FALLBACK;
  }
  // กล่อง object — geometry/transform เดียวกับไฟล์ export
  function objBoxCss(o) {
    return "position:absolute;left:" + round(o.x, 3) + "%;top:" + round(o.y, 3) + "%;"
      + "width:" + round(o.w, 3) + "%;height:" + round(o.h, 3) + "%;"
      + "transform:rotate(" + (o.rot || 0) + "deg);transform-origin:center center;"
      + "z-index:" + (o.z || 1) + ";";
  }
  // เนื้อหา — ฟอนต์/ขนาด/จัดแนว/ระยะ (k = สเกลตามความกว้างหน้าที่แสดง)
  function objContentCss(o, k) {
    const s = k || 1;
    const valign = { top: "flex-start", middle: "center", bottom: "flex-end" }[o.valign || "middle"] || "center";
    const just = { left: "flex-start", center: "center", right: "flex-end" }[o.align || "left"] || "flex-start";
    const align = o.align || "left";
    return "width:100%;height:100%;overflow:hidden;display:flex;align-items:" + valign + ";"
      + "justify-content:" + just + ";color:" + (o.color || "#111111") + ";text-align:" + align + ";"
      + "font-family:" + fontStack(o) + ";"
      + "font-size:" + round(Math.max(4, (o.fs || 16) * s), 2) + "px;"
      + "line-height:" + (o.lh || 1.05) + ";"
      + "font-weight:" + (o.bold ? 700 : 400) + ";"
      + "font-style:" + (o.italic ? "italic" : "normal") + ";"
      + "letter-spacing:" + round((o.ls || 0) * s, 2) + "px;"
      + (o.type === "text" || o.type === "stamp" ? "padding:0 4px;" : "")
      + "white-space:pre-wrap;word-break:break-word;";
  }
  // ขนาดหน้าเป็นมิลลิเมตร (อิงสูง 297mm) เพื่อรายงานพิกัดแบบเดียวกับงานพิมพ์
  function pageMm(pageIdx) {
    const pg = state.pages[pageIdx];
    const ar = pg && pg.bg ? (pg.bg.w / pg.bg.h) : (A4.w / A4.h);
    const hMm = 297;
    return { wMm: Math.round(hMm * ar * 100) / 100, hMm: hMm };
  }

  /* ---------------- rendering ---------------- */
  function renderPages() {
    const wrap = $("canvasWrap");
    wrap.classList.toggle("all", state.viewAll);
    wrap.innerHTML = "";
    const rec = curRecord();

    state.pages.forEach((pg, idx) => {
      const pageEl = document.createElement("div");
      pageEl.className = "page" + (!state.viewAll && idx === state.pageIdx ? " active" : "");
      pageEl.dataset.idx = String(idx);
      const ar = pg.bg ? { w: pg.bg.w, h: pg.bg.h } : A4;
      pageEl.style.aspectRatio = ar.w + "/" + ar.h;

      if (pg.bg && pg.bg.src) {
        const img = document.createElement("img");
        img.className = "bg";
        img.alt = "พื้นหลังหน้า " + (idx + 1);
        img.src = pg.bg.src;
        pageEl.appendChild(img);
      }

      const label = document.createElement("div");
      label.className = "page-label";
      label.textContent = "หน้า " + (idx + 1) + "/" + state.pages.length + (pg.bg && pg.bg.name ? " · " + pg.bg.name : "");
      pageEl.appendChild(label);

      const guide = document.createElement("div");
      guide.className = "guideLayer";
      pageEl.appendChild(guide);

      const cross = document.createElement("div");
      cross.className = "crossLayer";
      pageEl.appendChild(cross);
      pageEl.classList.toggle("cross-on", state.cross);

      const layer = document.createElement("div");
      layer.className = "objLayer";
      pg.objects.forEach((o) => layer.appendChild(buildObj(o, idx, rec)));
      pageEl.appendChild(layer);

      wrap.appendChild(pageEl);
    });

    $("pageEmpty").style.display = state.pages.length ? "none" : "block";
    fitFonts();
    updateSelDom();
  }

  function buildObj(o, pageIdx, rec) {
    const el = document.createElement("div");
    el.className = "obj" + (state.sel && state.sel.id === o.id && state.sel.page === pageIdx ? " sel" : "") + (o.locked ? " locked" : "");
    el.dataset.id = o.id;
    el.dataset.type = o.type;
    el.dataset.align = o.align || "left";
    el.style.cssText = objBoxCss(o);

    const content = document.createElement("div");
    content.className = "content";
    content.style.cssText = objContentCss(o, pageScale(pageIdx));
    fillContent(content, o, rec);
    el.appendChild(content);

    const lab = document.createElement("span");
    lab.className = "obj-label";
    lab.textContent = (o.key || "") + (o.type ? " [" + o.type + "]" : "");
    el.appendChild(lab);

    ["nw", "ne", "sw", "se"].forEach((h) => {
      const hd = document.createElement("i");
      hd.className = "h " + h;
      hd.dataset.h = h;
      el.appendChild(hd);
    });
    return el;
  }

  function fillContent(content, o, rec) {
    content.innerHTML = "";
    content.style.color = o.color || "#111";
    if (o.type === "image") {
      if (o.src) {
        const img = document.createElement("img");
        img.src = o.src;
        img.className = "fit-" + (o.fit || "contain");
        content.appendChild(img);
      } else {
        const s = document.createElement("span");
        s.className = "ph";
        s.textContent = "ยังไม่ได้เลือกรูป";
        content.appendChild(s);
      }
      return;
    }
    if (o.type === "qr") {
      const val = objValue(o, rec) || placeholderFor(o);
      const holder = document.createElement("div");
      holder.style.cssText = "width:100%;height:100%;display:flex;align-items:center;justify-content:center";
      content.appendChild(holder);
      if (typeof QRCode === "function") {
        try {
          new QRCode(holder, { text: val || "-", width: 220, height: 220, correctLevel: QRCode.CorrectLevel.M });
        } catch (e) {
          holder.textContent = val;
        }
      } else {
        holder.textContent = val;
      }
      return;
    }
    // stamp — ค่าตายตัวในเทมเพลต แต่แสดงเป็นเวลาปัจจุบันเสมอ
    if (o.type === "stamp") {
      const s = document.createElement("span");
      s.className = "stampval";
      s.textContent = stampText(o);
      content.appendChild(s);
      return;
    }
    // text
    const val = objValue(o, rec);
    const span = document.createElement("span");
    if (val) { span.textContent = val; }
    else { span.textContent = placeholderFor(o); span.className = "ph"; }
    content.appendChild(span);
  }

  // ปรับสเกลตามความกว้างหน้าจริง (เทียบกับฐาน 794px) โดยใช้ CSS ชุดเดียวกับไฟล์ export
  function fitFonts() {
    const wrap = $("canvasWrap");
    wrap.querySelectorAll(".page").forEach((pageEl) => {
      const k = (pageEl.clientWidth || A4.w) / A4.w;
      const idx = +pageEl.dataset.idx;
      const pg = state.pages[idx];
      if (!pg) return;
      pageEl.querySelectorAll(".obj").forEach((el) => {
        const o = pg.objects.find((x) => x.id === el.dataset.id);
        if (!o) return;
        const c = el.querySelector(".content");
        if (c) c.style.cssText = objContentCss(o, k);
      });
    });
    renderCrosshair();
  }

  function updateSelDom() {
    document.querySelectorAll(".obj.sel").forEach((el) => el.classList.remove("sel"));
    if (state.sel) {
      const el = document.querySelector('.page[data-idx="' + state.sel.page + '"] .obj[data-id="' + state.sel.id + '"]');
      if (el) el.classList.add("sel");
    }
    document.querySelectorAll(".page").forEach((el) => el.classList.remove("active"));
    if (!state.viewAll) {
      const p = document.querySelector('.page[data-idx="' + state.pageIdx + '"]');
      if (p) p.classList.add("active");
    }
  }

  /* ---------------- editor panel ---------------- */
  function renderEditor() {
    const o = selected();
    $("editor").style.display = o ? "block" : "none";
    $("noSelection").style.display = o ? "none" : "block";
    $("sumSelected").textContent = o ? "1" : "0";
    if (!o) { updateBoldNote(); renderCrosshair(); return; }
    $("edX").value = round(o.x, 2);
    $("edY").value = round(o.y, 2);
    $("edW").value = round(o.w, 2);
    $("edH").value = round(o.h, 2);
    $("edKey").value = o.key || "";
    $("edText").value = o.text || "";
    $("edType").value = o.type;
    $("edAlign").value = o.align || "left";
    $("edColor").value = o.color || "#111111";
    $("edImageFit").value = o.fit || "contain";
    $("edFontSize").value = o.fs || 16;
    $("edLineHeight").value = o.lh || 1.05;
    $("edRotation").value = o.rot || 0;
    $("edLockAspect").checked = !!o.lockAspect;
    $("edLocked").checked = !!o.locked;
    $("edLetter").value = round(o.ls || 0, 2);
    $("edItalic").checked = !!o.italic;
    $("edValign").value = o.valign || "middle";
    $("edBold").checked = !!o.bold;
    refreshFontSelectors();
    updateBoldNote();
    renderStampEditor(o);
    renderCrosshair();
  }

  // แผงตั้งค่า stamp — แสดงเฉพาะตอนเลือก object ชนิด stamp
  function renderStampEditor(o) {
    const box = $("stampbox");
    if (!box) return;
    const on = !!(o && o.type === "stamp");
    box.style.display = on ? "block" : "none";
    if (!on) return;
    const c = stampCfg(o);
    $("edStampPart").value = c.part;
    $("edStampMode").value = c.mode;
    $("edStampBase").value = c.base;
    $("edStampShift").value = c.shift;
    $("edStampBase").disabled = c.mode !== "base";
    const pv = $("stampPreview");
    if (pv) pv.textContent = stampText(o);
  }

  // เตือนถ้าฟอนต์ที่เลือกไม่มีตัวหนาจริง (เรนเดอร์จะไม่หนาตามที่ตั้ง)
  function updateBoldNote() {
    const o = selected();
    const fam = familyOf(o || {});
    const note = $("edBoldNote");
    if (note) {
      note.textContent = !o || !o.bold || !fam ? "" : (familyHasBold(fam) ? "· ฟอนต์นี้มีตัวหนา" : "· ฟอนต์นี้ไม่มีตัวหนา");
    }
    const bn = $("bBoldNote");
    if (bn) {
      const bf = $("bFamily") ? $("bFamily").value : "";
      bn.textContent = bf ? (familyHasBold(bf) ? "· มีตัวหนา" : "· ไม่มีตัวหนา") : "";
    }
  }

  function renderCounters() {
    let objs = 0, fields = 0;
    state.pages.forEach((p) => p.objects.forEach((o) => { objs++; if (o.key) fields++; }));
    $("sumRecords").textContent = String(state.records.length);
    $("sumFields").textContent = String(fields);
    $("sumObjects").textContent = String(objs);
    if ($("batchCount")) $("batchCount").textContent = String(state.fieldSel.length);
    $("recordCounter").innerHTML = state.records.length
      ? "<b>" + (state.recIdx + 1) + "/" + state.records.length + "</b>"
      : "<b>—/—</b>";
    ["btnFirst", "btnPrev", "btnNext", "btnLast"].forEach((id) => {
      $(id).disabled = !state.records.length;
    });
    $("btnFirst").disabled = state.recIdx <= 0;
    $("btnPrev").disabled = state.recIdx <= 0;
    $("btnNext").disabled = state.recIdx >= state.records.length - 1;
    $("btnLast").disabled = state.recIdx >= state.records.length - 1;
    $("pageCounter").innerHTML = "<b>" + (state.pages.length ? state.pageIdx + 1 : 0) + "/" + state.pages.length + "</b>";
    $("btnPgPrev").disabled = state.pageIdx <= 0;
    $("btnPgNext").disabled = state.pageIdx >= state.pages.length - 1;
    $("fillRecNo").textContent = state.records.length ? "(" + (state.recIdx + 1) + "/" + state.records.length + ")" : "(ยังไม่มี record)";
    $("fillCount").textContent = state.records.length
      ? state.records.length + " ชุดข้อมูลใน session นี้"
      : "ยังไม่มีชุดข้อมูล — นำเข้า JSON หรือกด + Record";
    const rec = curRecord();
    $("recordPreview").textContent = rec ? JSON.stringify(rec, null, 2) : "ยังไม่มีข้อมูล";
  }

  /* ---------------- Fields list (เพิ่ม/ลบฟิลด์) ---------------- */
  function renderFields() {
    const host = $("schemaList");
    host.innerHTML = "";
    let any = false;
    state.pages.forEach((pg, pi) => {
      pg.objects.forEach((o) => {
        any = true;
        const row = document.createElement("div");
        row.className = "item" + (state.sel && state.sel.id === o.id ? " sel" : "");

        const head = document.createElement("div");
        head.className = "itemhead fhead";

        const pick = document.createElement("input");
        pick.type = "checkbox";
        pick.checked = state.fieldSel.indexOf(o.id) >= 0;
        pick.title = "ติ้กเพื่อไปปรับหลายฟิลด์พร้อมกัน";
        pick.addEventListener("change", () => {
          const at = state.fieldSel.indexOf(o.id);
          if (pick.checked && at < 0) state.fieldSel.push(o.id);
          if (!pick.checked && at >= 0) state.fieldSel.splice(at, 1);
          renderCounters();
        });

        const keyIn = document.createElement("input");
        keyIn.type = "text";
        keyIn.value = o.key || "";
        keyIn.placeholder = "(ไม่มี key)";
        keyIn.addEventListener("change", () => { o.key = keyIn.value.trim(); refreshAll(); saveDraftSoon(); });

        const chip = document.createElement("span");
        chip.className = "chip " + o.type;
        chip.textContent = o.type + " · หน้า " + (pi + 1);

        const acts = document.createElement("div");
        acts.style.cssText = "display:flex;gap:4px";
        const bSel = document.createElement("button");
        bSel.type = "button";
        bSel.className = "mini";
        bSel.textContent = "เลือก";
        bSel.addEventListener("click", () => { state.pageIdx = pi; select(pi, o.id); refreshAll(); });
        const bDel = document.createElement("button");
        bDel.type = "button";
        bDel.className = "mini warn";
        bDel.textContent = "ลบ";
        bDel.addEventListener("click", () => { deleteObj(pi, o.id); });
        acts.appendChild(bSel);
        acts.appendChild(bDel);

        head.appendChild(pick);
        head.appendChild(keyIn);
        head.appendChild(chip);
        head.appendChild(acts);
        row.appendChild(head);

        const sample = document.createElement("div");
        sample.className = "sample";
        sample.textContent = "ข้อความ: " + (o.text || "-") + " · ตำแหน่ง: " + round(o.x, 1) + "%, " + round(o.y, 1) + "%"
          + " · " + (familyOf(o) || "ฟอนต์ปกติ") + (o.bold ? " · ตัวหนา" : "") + " · " + (o.fs || 16) + "px";
        row.appendChild(sample);

        row.addEventListener("click", (e) => {
          if (e.target.tagName === "BUTTON" || e.target.tagName === "INPUT") return;
          state.pageIdx = pi; select(pi, o.id); refreshAll();
        });
        host.appendChild(row);
      });
    });
    if (!any) host.innerHTML = '<div class="muted">ยังไม่มี fields — กด + เพิ่มฟิลด์ใหม่ หรือ +Text ด้านบน</div>';
  }

  /* ---------------- Fill list ---------------- */
  function renderFill() {
    const host = $("fillList");
    host.innerHTML = "";
    const rec = curRecord();
    const keys = allKeys();
    if (!keys.length) {
      host.innerHTML = '<div class="muted">ยังไม่มี key — เพิ่มฟิลด์แล้วตั้ง Key ในแท็บ Object</div>';
      return;
    }
    keys.forEach((k) => {
      const row = document.createElement("div");
      row.className = "item";
      const head = document.createElement("div");
      head.className = "itemhead";
      const lab = document.createElement("b");
      lab.textContent = k;
      lab.style.cssText = "font-size:13px;overflow:hidden;text-overflow:ellipsis";
      const inp = document.createElement("input");
      inp.type = "text";
      inp.value = rec && rec[k] != null ? rec[k] : "";
      inp.placeholder = "ค่าว่าง";
      inp.addEventListener("input", () => {
        if (!state.records.length) return;
        state.records[state.recIdx][k] = inp.value;
        renderCounters();
        refreshPagesSoon();
        saveDraftSoon();
      });
      head.appendChild(lab);
      head.appendChild(inp);
      row.appendChild(head);
      host.appendChild(row);
    });
  }

  function renderHistory() {
    const grid = $("histGrid");
    grid.innerHTML = "";
    $("histEmpty").style.display = history.length ? "none" : "block";
    history.forEach((h, i) => {
      const t = document.createElement("div");
      t.className = "thumb";
      const pic = document.createElement("div");
      pic.className = "pic";
      if (h.thumb) pic.style.backgroundImage = "url(" + h.thumb + ")";
      const meta = document.createElement("div");
      meta.className = "meta";
      meta.textContent = h.name + " · " + new Date(h.time).toLocaleTimeString("th-TH");
      const acts = document.createElement("div");
      acts.className = "acts";
      const dl = document.createElement("button");
      dl.type = "button";
      dl.textContent = "โหลด";
      dl.addEventListener("click", () => { if (h.dataUrl) downloadDataUrl(h.dataUrl, h.name); });
      const rm = document.createElement("button");
      rm.type = "button";
      rm.className = "warn";
      rm.textContent = "ลบ";
      rm.addEventListener("click", () => { history.splice(i, 1); renderHistory(); });
      if (h.dataUrl) acts.appendChild(dl);
      acts.appendChild(rm);
      t.appendChild(pic); t.appendChild(meta); t.appendChild(acts);
      grid.appendChild(t);
    });
  }

  let pagesTimer = null;
  function refreshPagesSoon() {
    clearTimeout(pagesTimer);
    pagesTimer = setTimeout(() => { renderPages(); }, 120);
  }
  function refreshAll() {
    renderPages();
    renderCounters();
    renderEditor();
    renderFields();
    renderFill();
    renderPrint();
  }

  /* ---------------- selection / drag / resize ---------------- */
  function select(pageIdx, id) {
    state.sel = id ? { page: pageIdx, id: id } : null;
    updateSelDom();
    renderEditor();
    renderCounters();
    renderFields();
  }

  let drag = null;

  function onPointerDown(e) {
    if (state.viewAll) return;
    const pageEl = e.target.closest(".page");
    if (!pageEl) return;
    if (state.cross) { crossPointerDown(e, pageEl); return; }
    const objEl = e.target.closest(".obj");
    if (!objEl) { select(-1, null); return; }
    const idx = +pageEl.dataset.idx;
    const o = getObj(idx, objEl.dataset.id);
    if (!o) return;
    select(idx, o.id);
    if (o.locked) { toast("object นี้ล็อกไว้ — ปลดล็อกก่อนขยับ"); return; }

    const handleEl = e.target.closest(".h");
    drag = {
      mode: handleEl ? "resize" : "move",
      handle: handleEl ? handleEl.dataset.h : null,
      page: idx, id: o.id, pageEl: pageEl,
      sx: e.clientX, sy: e.clientY,
      ox: o.x, oy: o.y, ow: o.w, oh: o.h,
      ratio: o.h / (o.w || 1),
      lockRatio: !!o.lockAspect,
      rect: pageEl.getBoundingClientRect(),
      moved: false
    };
    objEl.classList.add("dragging");
    e.preventDefault();
  }

  /* โหมดเคอเซอร์ตามแนว VNC trackpad:
     trackpad = แตะไม่ย้ายเป้า · ลากนิ้วแล้วเป้าขยับตาม "ระยะที่นิ้วเลื่อน" (delta ต่อเฟรม)
               นิ้วจึงอยู่คนละตำแหน่งกับเป้า ไม่บังจุดที่กำลังกด
     tap     = แตะที่ไหน เป้าก็ไปที่นั้นทันที (absolute) */
  function crossMode() { return $("crossMode") ? $("crossMode").value : "trackpad"; }
  function applyCrossMode(data) {
    if (!data) return;
    if ($("crossMode") && (data.crossMode === "trackpad" || data.crossMode === "tap")) $("crossMode").value = data.crossMode;
    if ($("crossGain")) {
      const g = parseFloat(data.crossGain);
      if (!isNaN(g)) $("crossGain").value = String(clamp(g, 0.1, 3));
    }
  }
  function crossGain() {
    const g = parseFloat($("crossGain") ? $("crossGain").value : "1");
    return isNaN(g) ? 1 : clamp(g, 0.1, 3);
  }
  function crossPointerDown(e, pageEl) {
    const idx = +pageEl.dataset.idx;
    const rect = pageEl.getBoundingClientRect();
    if (idx !== state.pageIdx) { state.pageIdx = idx; state.sel = null; renderEditor(); renderCounters(); updateSelDom(); }
    const px = rect.width ? ((e.clientX - rect.left) / rect.width) * 100 : 50;
    const py = rect.height ? ((e.clientY - rect.top) / rect.height) * 100 : 50;
    const x0 = state.crossPos ? state.crossPos.x : px;
    const y0 = state.crossPos ? state.crossPos.y : py;
    const absolute = crossMode() !== "trackpad";
    drag = {
      mode: "cross", page: idx, pageEl: pageEl, rect: rect, absolute: absolute,
      sx: e.clientX, sy: e.clientY,
      lx: e.clientX, ly: e.clientY, // ตำแหน่งนิ้วเฟรมก่อนหน้า — ใช้คิด delta ต่อเฟรม
      ox: x0, oy: y0, cx: x0, cy: y0,
      moved: false
    };
    // โหมด absolute: วางเป้าที่จุดที่แตะทันที (เหมือน onAbsoluteClick)
    if (absolute) {
      const s = snapPoint(px, py, idx);
      setCross(s.x, s.y);
      status("วางเป้าที่ " + coordText(s.x, s.y, idx), "ok");
    }
    try { pageEl.setPointerCapture(e.pointerId); } catch (err) { /* ไม่รองรับ pointer capture */ }
    e.preventDefault();
  }

  function onPointerMove(e) {
    if (!drag) return;
    if (drag.mode === "cross") {
      const cx = e.clientX, cy = e.clientY;
      if (!drag.moved && Math.abs(cx - drag.sx) < 3 && Math.abs(cy - drag.sy) < 3) return;
      drag.moved = true;
      let nx, ny;
      if (drag.absolute) { // เดินตามตำแหน่งนิ้ว (ตำแหน่งสัมบูรณ์)
        nx = drag.ox + (drag.rect.width ? ((cx - drag.sx) / drag.rect.width) * 100 : 0);
        ny = drag.oy + (drag.rect.height ? ((cy - drag.sy) / drag.rect.height) * 100 : 0);
      } else { // สัมพัทธ์: บวกระยะที่นิ้วเลื่อนในแต่ละเฟรม (dragAmount) ลงในตำแหน่งเป้าล่าสุด
        const g = crossGain();
        nx = drag.cx + (drag.rect.width ? ((cx - drag.lx) / drag.rect.width) * 100 * g : 0);
        ny = drag.cy + (drag.rect.height ? ((cy - drag.ly) / drag.rect.height) * 100 * g : 0);
        drag.lx = cx; drag.ly = cy;
      }
      const s = snapPoint(nx, ny, drag.page);
      drag.cx = s.x; drag.cy = s.y;
      setCross(s.x, s.y);
      return;
    }
    const o = getObj(drag.page, drag.id);
    if (!o) return;
    const dx = ((e.clientX - drag.sx) / drag.rect.width) * 100;
    const dy = ((e.clientY - drag.sy) / drag.rect.height) * 100;
    if (!drag.moved && Math.abs(e.clientX - drag.sx) < 3 && Math.abs(e.clientY - drag.sy) < 3) return;
    drag.moved = true;

    if (drag.mode === "move") {
      let nx = drag.ox + dx, ny = drag.oy + dy;
      const snapped = applySnap(o, nx, ny);
      o.x = clamp(snapped.x, -o.w + 2, 98);
      o.y = clamp(snapped.y, -o.h + 2, 98);
      drawGuides(snapped.guides);
    } else {
      let nx = drag.ox, ny = drag.oy, nw = drag.ow, nh = drag.oh;
      const h = drag.handle;
      if (h.indexOf("e") >= 0) nw = drag.ow + dx;
      if (h.indexOf("w") >= 0) { nx = drag.ox + dx; nw = drag.ow - dx; }
      if (h.indexOf("s") >= 0) nh = drag.oh + dy;
      if (h.indexOf("n") >= 0) { ny = drag.oy + dy; nh = drag.oh - dy; }
      nw = Math.max(2, nw);
      nh = Math.max(1.2, nh);
      if (drag.lockRatio) {
        if (h === "n" || h === "s") { nw = nh / (drag.ratio || 1); }
        else { nh = nw * (drag.ratio || 1); }
        if (h.indexOf("n") >= 0) ny = drag.oy + (drag.oh - nh);
        if (h.indexOf("w") >= 0) nx = drag.ox + (drag.ow - nw);
      }
      o.x = round(clamp(nx, -5, 98), 3);
      o.y = round(clamp(ny, -5, 98), 3);
      o.w = round(clamp(nw, 2, 105), 3);
      o.h = round(clamp(nh, 1.2, 105), 3);
      drawGuides(null);
    }
    applyObjDom(o);
    syncEditorPos();
  }

  function onPointerUp(e) {
    if (!drag) return;
    if (drag.mode === "cross") {
      const moved = drag.moved;
      const absolute = drag.absolute;
      const pi = drag.page;
      const fin = state.crossPos || { x: drag.cx, y: drag.cy };
      drag = null;
      drawGuides(null);
      saveDraftSoon();
      if (!moved) {
        if (absolute) status("วางเป้าที่ " + coordText(fin.x, fin.y, pi), "ok");
        else status("โหมด Trackpad — แตะไม่ย้ายเป้า · ลากนิ้วที่ใดก็ได้เพื่อเลื่อนเป้า", "ok");
      } else {
        status("เลื่อนเป้าแบบ" + (absolute ? "ตามนิ้ว" : "สัมพัทธ์ (นิ้วไม่บังเป้า)") + " → " + coordText(fin.x, fin.y, pi), "ok");
      }
      return;
    }
    const el = document.querySelector('.obj[data-id="' + drag.id + '"]');
    if (el) el.classList.remove("dragging");
    drawGuides(null);
    if (drag.moved) { fitFonts(); saveDraftSoon(); status("เลื่อน/ปรับขนาดแล้ว (auto-save)", "ok"); }
    drag = null;
  }

  function applyObjDom(o) {
    if (!state.sel) return;
    const el = document.querySelector('.page[data-idx="' + state.sel.page + '"] .obj[data-id="' + o.id + '"]');
    if (!el) return;
    el.style.cssText = objBoxCss(o);
    const c = el.querySelector(".content");
    if (c) c.style.cssText = objContentCss(o, pageScale(state.sel.page));
  }

  function syncEditorPos() {
    const o = selected();
    if (!o) return;
    $("edX").value = round(o.x, 2);
    $("edY").value = round(o.y, 2);
    $("edW").value = round(o.w, 2);
    $("edH").value = round(o.h, 2);
  }

  /* snap: ขอบ/กลางของ object เข้าขอบหน้า 0/50/100 และขอบวัตถุอื่น */
  function applySnap(o, nx, ny) {
    const res = { x: nx, y: ny, guides: { v: [], h: [] } };
    if (!state.snap) return res;
    const tol = 1.4;
    const page = state.pages[state.pageIdx];
    const tx = [0, 50, 100], ty = [0, 50, 100];
    if (page) page.objects.forEach((t) => {
      if (t.id === o.id) return;
      tx.push(t.x, t.x + t.w / 2, t.x + t.w);
      ty.push(t.y, t.y + t.h / 2, t.y + t.h);
    });
    const trySnap = (val, size, targets, axis) => {
      let best = null;
      [0, 0.5, 1].forEach((frac) => {
        const edge = val + size * frac;
        targets.forEach((t) => {
          const d = t - edge;
          if (Math.abs(d) <= tol && (!best || Math.abs(d) < Math.abs(best.d))) best = { d: d, t: t };
        });
      });
      if (best) { res.guides[axis].push(best.t); return val + best.d; }
      return val;
    };
    res.x = trySnap(nx, o.w, tx, "v");
    res.y = trySnap(ny, o.h, ty, "h");
    return res;
  }

  function drawGuides(guides) {
    const layer = document.querySelector('.page[data-idx="' + state.pageIdx + '"] .guideLayer');
    if (!layer) return;
    layer.innerHTML = "";
    if (!guides) return;
    guides.v.forEach((x) => {
      const g = document.createElement("div");
      g.className = "guide v";
      g.style.left = x + "%";
      layer.appendChild(g);
    });
    guides.h.forEach((y) => {
      const g = document.createElement("div");
      g.className = "guide h";
      g.style.top = y + "%";
      layer.appendChild(g);
    });
  }

  /* ---------------- เป้ากากบาท (crosshair) วางตำแหน่งแบบสัมพัทธ์ ----------------
     แตะสั้น = วางเป้าที่ตำแหน่งจริง · จากนั้นลากนิ้วจากตำแหน่งคนละที่
     เป้าจะเลื่อนตามนิ้ว แต่สายตาดูที่เป้า ไม่ใช่ที่นิ้ว -> กดพิกัดแม่นขึ้น */
  function crossAnchorMode() { return $("crossAnchor") ? $("crossAnchor").value : "tl"; }
  function pageAspect(pageIdx) {
    const pg = state.pages[pageIdx];
    return pg && pg.bg ? (pg.bg.w / pg.bg.h) : (A4.w / A4.h);
  }
  function toggleCross(on) {
    state.cross = on == null ? !state.cross : !!on;
    $("btnCross").className = state.cross ? "cross-on" : "cross-off";
    $("btnCross").textContent = state.cross ? "✛ เป้ากากบาท ON" : "✛ เป้ากากบาท";
    if (state.cross) {
      const o = selected();
      if (!state.crossPos) {
        state.crossPos = o ? { x: round(o.x, 3), y: round(o.y, 3) } : { x: 50, y: 50 };
      }
      renderCrosshair();
      toast("เป้ากากบาท ON — " + (crossMode() === "trackpad"
        ? "โหมด Trackpad: ลากนิ้วที่ใดก็ได้ เป้าขยับตามระยะที่นิ้วเลื่อน (นิ้วไม่บังเป้า)"
        : "โหมดแตะวาง: แตะที่ไหนเป้าก็ไปที่นั้น"), "ok");
    } else {
      renderCrosshair();
      toast("เป้ากากบาท OFF");
    }
  }
  function applyCrossToObj(o) {
    const c = state.crossPos;
    if (!o || !c) return;
    const center = crossAnchorMode() === "c";
    o.x = round(clamp(center ? c.x - o.w / 2 : c.x, -o.w + 2, 98), 3);
    o.y = round(clamp(center ? c.y - o.h / 2 : c.y, -o.h + 2, 98), 3);
    applyObjDom(o);
    syncEditorPos();
  }
  function setCross(x, y, apply) {
    state.crossPos = { x: round(clamp(x, 0, 100), 3), y: round(clamp(y, 0, 100), 3) };
    if (apply !== false) applyCrossToObj(selected());
    renderCrosshair();
  }
  function snapPoint(x, y, pageIdx) {
    if (!state.snap) return { x: x, y: y };
    const tol = 1;
    const pg = state.pages[pageIdx == null ? state.pageIdx : pageIdx];
    const sel = selected();
    const tx = [0, 50, 100], ty = [0, 50, 100];
    if (pg) pg.objects.forEach((t) => {
      if (sel && t.id === sel.id) return;
      tx.push(t.x, t.x + t.w, t.x + t.w / 2);
      ty.push(t.y, t.y + t.h, t.y + t.h / 2);
    });
    const best = (v, list) => {
      let b = null;
      list.forEach((t) => {
        const d = t - v;
        if (Math.abs(d) <= tol && (!b || Math.abs(d) < Math.abs(b.d))) b = { d: d };
      });
      return b ? v + b.d : v;
    };
    return { x: best(x, tx), y: best(y, ty) };
  }
  function coordText(x, y, pageIdx) {
    const mm = pageMm(pageIdx);
    const hPx = A4.w / pageAspect(pageIdx);
    return "X " + round(x, 2) + "% · " + round(x / 100 * mm.wMm, 1) + "mm · " + round(x / 100 * A4.w, 1) + "px"
      + "   Y " + round(y, 2) + "% · " + round(y / 100 * mm.hMm, 1) + "mm · " + round(y / 100 * hPx, 1) + "px";
  }
  function renderCrosshair() {
    document.querySelectorAll(".page").forEach((p) => p.classList.toggle("cross-on", state.cross));
    const ro = $("coordReadout");
    const host = document.querySelector('.page[data-idx="' + state.pageIdx + '"] .crossLayer');
    const o = selected();
    if (ro) {
      if (state.cross && state.crossPos) ro.textContent = coordText(state.crossPos.x, state.crossPos.y, state.pageIdx);
      else if (o) ro.textContent = "object: " + coordText(o.x, o.y, state.sel.page);
      else ro.textContent = state.cross ? "พิกัด: แตะหน้าเพื่อวางเป้า" : "พิกัด: —";
    }
    if (!host) return;
    host.innerHTML = "";
    if (!state.cross || !state.crossPos) return;
    const x = state.crossPos.x, y = state.crossPos.y;
    const v = document.createElement("div");
    v.className = "cl v";
    v.style.left = x + "%";
    const h = document.createElement("div");
    h.className = "cl h";
    h.style.top = y + "%";
    const m = document.createElement("div");
    m.className = "cm";
    m.style.left = x + "%";
    m.style.top = y + "%";
    const tag = document.createElement("div");
    tag.className = "crossTag";
    tag.style.left = x + "%";
    tag.style.top = y + "%";
    tag.style.transform = "translate(-50%,-190%)";
    tag.textContent = "X " + round(x, 2) + "% · Y " + round(y, 2) + "%";
    host.appendChild(v);
    host.appendChild(h);
    host.appendChild(m);
    host.appendChild(tag);
  }
  function nudgeCross(dx, dy) {
    if (!state.crossPos) { setCross(50, 50); return; }
    setCross(state.crossPos.x + dx, state.crossPos.y + dy); // ขยับละเอียด ไม่ snap
    saveDraftSoon();
  }

  /* ---------------- object CRUD ---------------- */
  function addObject(type, extra) {
    const pg = curPage();
    if (!pg) { toast("ยังไม่มีหน้าเอกสาร", "err"); return null; }
    const z = maxZ(pg) + 1;
    const base = {
      id: uid(), type: type, key: "", text: "",
      x: 15, y: 10, w: 40, h: 6, rot: 0, fs: 16, lh: 1.05, ls: 0,
      align: "left", valign: "middle", color: "#111111", fit: "contain",
      family: "", bold: false, italic: false,
      lockAspect: false, locked: false, z: z, src: ""
    };
    if (type === "image") { base.w = 25; base.h = 25; base.y = 35; }
    if (type === "qr") { base.w = 16; base.h = 16; base.y = 60; base.text = "https://example.com"; base.lockAspect = true; base.key = "qr"; }
    if (type === "text") { base.text = "ข้อความ"; }
    if (type === "stamp") {
      base.w = 34; base.h = 5; base.y = 88; base.x = 12;
      base.align = "left";
      base.stampPart = "datetime"; base.stampMode = "now"; base.stampBase = ""; base.stampShift = 0;
    }
    Object.assign(base, extra || {});
    // วางกึ่งกลางของหน้าในมุมมองปัจจุบัน
    base.x = round(clamp(50 - base.w / 2, 0, 90), 2);
    pg.objects.push(base);
    select(state.pageIdx, base.id);
    refreshAll();
    saveDraftSoon();
    toast("เพิ่ม " + type + " แล้ว — ลากวางได้เลย", "ok");
    return base;
  }

  function deleteObj(pageIdx, id) {
    const pg = state.pages[pageIdx];
    if (!pg) return;
    const i = pg.objects.findIndex((o) => o.id === id);
    if (i < 0) return;
    pg.objects.splice(i, 1);
    if (state.sel && state.sel.id === id) state.sel = null;
    refreshAll();
    saveDraftSoon();
    toast("ลบฟิลด์แล้ว", "ok");
  }

  function duplicateSelected() {
    const o = selected();
    if (!o) return;
    const pg = curPage();
    const copy = JSON.parse(JSON.stringify(o));
    copy.id = uid();
    copy.x = round(clamp(o.x + 3, -5, 95), 2);
    copy.y = round(clamp(o.y + 3, -5, 95), 2);
    copy.z = maxZ(pg) + 1;
    pg.objects.push(copy);
    select(state.pageIdx, copy.id);
    refreshAll();
    saveDraftSoon();
  }

  function reorder(dir) {
    const o = selected();
    if (!o) return;
    const pg = curPage();
    pg.objects.sort((a, b) => (a.z || 0) - (b.z || 0));
    const i = pg.objects.findIndex((x) => x.id === o.id);
    const j = dir > 0 ? i + 1 : i - 1;
    if (j < 0 || j >= pg.objects.length) return;
    const tmp = pg.objects[i];
    pg.objects[i] = pg.objects[j];
    pg.objects[j] = tmp;
    pg.objects.forEach((x, k) => { x.z = k + 1; });
    refreshAll();
    saveDraftSoon();
  }

  /* ---------------- pages ---------------- */
  function addBlankPage() {
    state.pages.push({ id: uid(), bg: null, objects: [] });
    state.pageIdx = state.pages.length - 1;
    refreshAll();
    saveDraftSoon();
  }
  function delPage() {
    if (state.pages.length <= 1) { toast("ต้องมีอย่างน้อย 1 หน้า", "err"); return; }
    state.pages.splice(state.pageIdx, 1);
    state.pageIdx = clamp(state.pageIdx, 0, state.pages.length - 1);
    state.sel = null;
    refreshAll();
    saveDraftSoon();
    toast("ลบหน้าแล้ว", "ok");
  }
  function gotoPage(i) {
    state.pageIdx = clamp(i, 0, state.pages.length - 1);
    state.sel = null;
    refreshAll();
    const el = document.querySelector('.page[data-idx="' + state.pageIdx + '"]');
    if (el && !state.viewAll && typeof el.scrollIntoView === "function") el.scrollIntoView({ block: "nearest", behavior: "smooth" });
  }

  /* ---------------- background: ภาพ / PDF (ทุกหน้า) ---------------- */
  function clearBg() {
    const pg = curPage();
    if (!pg) return;
    pg.bg = null;
    renderPages();
    $("bgStatus").textContent = "ยังไม่โหลด";
    $("bgStatus").className = "status muted";
    saveDraftSoon();
  }

  async function loadBgFiles(files) {
    if (!files || !files.length) return;
    loadShow("กำลังโหลดไฟล์…");
    try {
      const pdfs = Array.from(files).filter((f) => /pdf$/i.test(f.type) || /\.pdf$/i.test(f.name));
      const imgs = Array.from(files).filter((f) => !pdfs.includes(f));
      let added = 0;

      for (let i = 0; i < imgs.length; i++) {
        const f = imgs[i];
        loadProg(((i) / (imgs.length + pdfs.length)) * 100, "อ่านภาพ " + f.name);
        const src = await readFileDataUrl(f);
        const dim = await imageDims(src);
        const page = ensurePageForBg(added === 0 && imgs.length === 1 && pdfs.length === 0);
        page.bg = { kind: "image", src: src, w: dim.w, h: dim.h, name: f.name };
        added++;
      }

      for (let i = 0; i < pdfs.length; i++) {
        const f = pdfs[i];
        const buf = await f.arrayBuffer();
        if (typeof pdfjsLib === "undefined") throw new Error("pdf.js ไม่พร้อม (ต้องมีเน็ตครั้งแรก)");
        pdfjsLib.GlobalWorkerOptions.workerSrc = "https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.worker.min.js";
        const doc = await pdfjsLib.getDocument({ data: buf }).promise;
        for (let n = 1; n <= doc.numPages; n++) {
          loadProg(((n - 1) / doc.numPages) * 100, "เรนเดอร์ PDF หน้า " + n + "/" + doc.numPages);
          const pg = await doc.getPage(n);
          const vp0 = pg.getViewport({ scale: 1 });
          const scale = Math.min(2.2, 1700 / vp0.width);
          const vp = pg.getViewport({ scale: scale });
          const canvas = document.createElement("canvas");
          canvas.width = Math.round(vp.width);
          canvas.height = Math.round(vp.height);
          const ctx = canvas.getContext("2d");
          ctx.fillStyle = "#fff";
          ctx.fillRect(0, 0, canvas.width, canvas.height);
          await pg.render({ canvasContext: ctx, viewport: vp }).promise;
          const src = canvas.toDataURL("image/jpeg", 0.9);
          const page = ensurePageForBg(added === 0);
          page.bg = { kind: "pdf", src: src, w: vp0.width, h: vp0.height, name: f.name + " #" + n };
          added++;
          loadProg((n / doc.numPages) * 100);
        }
      }

      if (added) {
        state.pageIdx = clamp(state.pageIdx, 0, state.pages.length - 1);
        refreshAll();
        $("bgStatus").textContent = "โหลดแล้ว " + added + " หน้า (ดูภาพเป็นตัวอย่างบน canvas)";
        $("bgStatus").className = "status ok";
        saveDraftSoon();
        toast("โหลดพื้นหลัง " + added + " หน้าแล้ว", "ok");
      }
    } catch (err) {
      console.error(err);
      toast("โหลดพื้นหลังไม่สำเร็จ: " + err.message, "err");
      status("โหลดพื้นหลังไม่สำเร็จ: " + err.message, "err");
    } finally {
      loadHide();
    }
  }

  // ไฟล์แรก (คนเดียว) ใช้หน้าปัจจุบัน ไฟล์ที่เหลือเพิ่มหน้าใหม่
  function ensurePageForBg(useCurrent) {
    if (useCurrent) {
      const pg = curPage();
      if (pg && !pg.bg && pg.objects.length === 0) return pg;
      if (pg && !pg.bg) return pg;
    }
    const pg = { id: uid(), bg: null, objects: [] };
    state.pages.push(pg);
    return pg;
  }

  function readFileDataUrl(file) {
    return new Promise((res, rej) => {
      const fr = new FileReader();
      fr.onload = () => res(fr.result);
      fr.onerror = () => rej(new Error("อ่านไฟล์ไม่ได้"));
      fr.readAsDataURL(file);
    });
  }
  function imageDims(src) {
    return new Promise((res, rej) => {
      const im = new Image();
      im.onload = () => res({ w: im.naturalWidth || A4.w, h: im.naturalHeight || A4.h });
      im.onerror = () => rej(new Error("เปิดภาพไม่ได้"));
      im.src = src;
    });
  }

  /* ---------------- records / JSON ---------------- */
  // รับทั้ง array ของ object, object เดี่ยว, และ {records:[...]} / {data:[...]} ที่ export มาจากที่อื่น
  function normalizeRecords(arr) {
    let src = arr;
    if (!Array.isArray(src) && src && typeof src === "object") {
      const k = ["records", "data", "rows", "items"].filter((x) => Array.isArray(src[x]))[0];
      if (k) src = src[k];
    }
    if (!Array.isArray(src)) throw new Error("JSON ต้องเป็น array เช่น [{...},{...}]");
    return src.filter((r) => r && typeof r === "object" && !Array.isArray(r));
  }
  // append: true = ต่อท้ายของเดิม (นำเข้าหลายชุดใน session เดียว), false = แทนทั้งหมด
  function loadRecords(arr, srcLabel, append) {
    const recs = normalizeRecords(arr);
    if (!recs.length) throw new Error("ไม่พบ record (object) ใน JSON");
    if (append) {
      state.records = state.records.concat(recs);
      state.recIdx = state.records.length - recs.length; // ชี้ที่ชุดที่เพิ่งโหลด
    } else {
      state.records = recs;
      state.recIdx = 0;
    }
    const label = srcLabel || "";
    $("jsonStatus").textContent = "โหลดแล้ว " + recs.length + " records" + (label ? " (" + label + ")" : "")
      + " · รวมใน session " + state.records.length + " ชุด";
    $("jsonStatus").className = "status ok";
    refreshAll();
    saveDraftSoon();
    toast("โหลด " + recs.length + " records (รวม " + state.records.length + " ชุด)", "ok");
  }

  // อ่านหลายไฟล์แล้วรวมเข้า session เดียว (ต่อท้ายของเดิม) — ไฟล์ไหนผิดจะข้ามแล้วรายงาน
  async function loadRecordFiles(files, append) {
    const list = Array.from(files || []).filter(Boolean);
    if (!list.length) return;
    let loaded = 0;
    const errors = [];
    for (const f of list) {
      try {
        loadRecords(JSON.parse(await f.text()), f.name, append);
        loaded++;
        append = true; // ไฟล์ถัดไปต่อท้ายชุดก่อนหน้า
      } catch (err) {
        errors.push(f.name + ": " + (err && err.message ? err.message : "อ่านไม่ได้"));
      }
    }
    if (loaded) {
      status("นำเข้า " + loaded + " ไฟล์ · รวม " + state.records.length + " ชุดข้อมูลใน session", "ok");
      if (errors.length) toast("ข้ามไฟล์ที่ผิด " + errors.length + " ไฟล์: " + errors.join(" · "), "err");
    } else {
      toast("อ่าน JSON ไม่ได้เลย: " + errors.join(" · "), "err");
      status("อ่าน JSON ไม่สำเร็จ: " + errors.join(" · "), "err");
    }
  }

  function clearRecords() {
    state.records = [];
    state.recIdx = 0;
    $("jsonStatus").textContent = "ยังไม่โหลด";
    $("jsonStatus").className = "status muted";
    refreshAll();
    saveDraftSoon();
  }

  function gotoRecord(i) {
    if (!state.records.length) return;
    state.recIdx = clamp(i, 0, state.records.length - 1);
    renderCounters();
    renderFill();
    renderPages();
    saveDraftSoon();
  }

  /* ---------------- ปรับหลายฟิลด์พร้อมกัน (ติ้กเลือก) ---------------- */
  function selectedObjs() {
    const out = [];
    if (!state.fieldSel.length) return out;
    state.pages.forEach((p, pi) => p.objects.forEach((o) => {
      if (state.fieldSel.indexOf(o.id) >= 0) out.push({ o: o, pi: pi });
    }));
    return out;
  }
  function batchApply() {
    const targets = selectedObjs();
    if (!targets.length) { toast("ติ้กฟิลด์ที่จะปรับก่อน (ช่องข้างชื่อฟิลด์)", "err"); return; }
    const on = (id) => $(id) && $(id).checked;
    const specs = [];
    if (on("bOnFs")) specs.push(["ขนาด", (o) => { o.fs = clamp(parseFloat($("bFs").value) || 16, 4, 200); }]);
    if (on("bOnFamily")) specs.push(["ฟอนต์", (o) => { o.family = $("bFamily").value || ""; }]);
    if (on("bOnBold")) specs.push(["ตัวหนา", (o) => { o.bold = !!$("bBold").checked; }]);
    if (on("bOnItalic")) specs.push(["เอียง", (o) => { o.italic = !!$("bItalic").checked; }]);
    if (on("bOnLetter")) specs.push(["ตัวอักษร", (o) => { o.ls = parseFloat($("bLetter").value) || 0; }]);
    if (on("bOnLh")) specs.push(["ระยะบรรทัด", (o) => { o.lh = clamp(parseFloat($("bLh").value) || 1.05, 0.4, 5); }]);
    if (on("bOnAlign")) specs.push(["จัดแนว", (o) => { o.align = $("bAlign").value; }]);
    if (on("bOnValign")) specs.push(["แนวตั้ง", (o) => { o.valign = $("bValign").value; }]);
    if (on("bOnColor")) specs.push(["สี", (o) => { o.color = $("bColor").value || "#111111"; }]);
    if (on("bOnText")) specs.push(["เนื้อหา", (o) => { o.text = $("bText").value; }]);
    if (on("bOnKey")) specs.push(["Key", (o) => { o.key = $("bKey").value.trim(); }]);
    if (!specs.length) { toast("ติ้กค่าที่ต้องการเปลี่ยนอย่างน้อย 1 ค่า", "err"); return; }

    const warnBold = on("bOnBold") && $("bBold").checked;
    targets.forEach((t) => {
      if (t.o.type !== "text" && t.o.type !== "qr") { specs.forEach((s) => s[1](t.o)); return; }
      specs.forEach((s) => s[1](t.o));
    });
    refreshAll();
    saveDraftSoon();
    const names = specs.map((s) => s[0]).join(", ");
    toast("ปรับ " + targets.length + " ฟิลด์: " + names, "ok");
    status("ปรับค่า " + names + " ให้ " + targets.length + " ฟิลด์ที่ติ้กไว้", "ok");
    if (warnBold) {
      const missing = [];
      targets.forEach((t) => { const f = familyOf(t.o); if (t.o.bold && f && !familyHasBold(f) && missing.indexOf(f) < 0) missing.push(f); });
      if (missing.length) toast("เตือน: ฟอนต์ " + missing.join(", ") + " ที่แนบมาไม่มีตัวหนาจริง", "err");
    }
  }

  /* ---------------- ลำดับการสั่งพิมพ์ + ตั้งชื่อไฟล์ ---------------- */
  function printIndexes() {
    const n = state.records.length;
    const seen = {};
    const out = [];
    if (Array.isArray(state.printOrder)) state.printOrder.forEach((i) => {
      const k = Number(i);
      if (Number.isInteger(k) && k >= 0 && k < n && !seen[k]) { seen[k] = 1; out.push(k); }
    });
    for (let i = 0; i < n; i++) if (!seen[i]) out.push(i);
    state.printOrder = out;
    return out;
  }
  function printSelected() {
    return printIndexes().filter((i) => !state.printOff[i]);
  }
  function printPages() {
    return state.printScope === "current" ? [state.pageIdx] : state.pages.map((_, i) => i);
  }
  function printNameFor(recIdx, pos, total) {
    const rec = state.records[recIdx];
    const key = state.printNameKey;
    const kv = key ? (rec && rec[key] != null && String(rec[key]) !== "" ? String(rec[key]) : "") : "";
    const tpl = state.printNameTpl || "{n}_{key}";
    const sub = (s) => () => s;
    const out = String(tpl)
      .replace(/\{n\}/g, sub(pad2(pos)))
      .replace(/\{i\}/g, sub(String(recIdx + 1)))
      .replace(/\{key\}/g, sub(kv || ("r" + (recIdx + 1))))
      .replace(/\{total\}/g, sub(String(total)))
      .replace(/\{date\}/g, sub(dateStamp()));
    const safe = out.replace(/[\\/:*?"<>|\r\n\t]+/g, "-").replace(/\s+/g, "_")
      .replace(/_+/g, "_").replace(/^[_\-.]+|_[_\-.]+$/g, "").slice(0, 80);
    return safe || ("template_" + dateStamp());
  }
  let printKeySig = "";
  function renderPrintNameOptions() {
    const sel = $("printNameKey");
    if (!sel) return;
    const cur = sel.value;
    const keys = allKeys();
    const sig = keys.join("|");
    if (sig !== printKeySig) { // สร้างรายการใหม่เฉพาะตอนชุดคีย์เปลี่ยน ไม่ให้ตัวเลือกกระตุกตอนเปิดค้างอยู่
      printKeySig = sig;
      sel.innerHTML = "";
      const idxOpt = document.createElement("option");
      idxOpt.value = "";
      idxOpt.textContent = "(ดัชนีลำดับ)";
      sel.appendChild(idxOpt);
      keys.forEach((k) => {
        const op = document.createElement("option");
        op.value = k;
        op.textContent = k;
        sel.appendChild(op);
      });
    }
    sel.value = keys.indexOf(cur) >= 0 ? cur : (state.printNameKey || "");
  }
  function updatePrintPreview() {
    const box = $("printPreview");
    if (!box) return;
    const sel = printSelected();
    if (!sel.length) { box.textContent = "ยังไม่มีรายการที่ติ้กไว้"; return; }
    const names = sel.slice(0, 3).map((ri, i) => printNameFor(ri, i + 1, sel.length) + ".html");
    box.textContent = "ติ้กไว้ " + sel.length + " ชุด → " + names.join(" , ") + (sel.length > 3 ? " …" : "");
  }
  function movePrintRow(pos, to) {
    const ord = printIndexes();
    if (to < 0 || to >= ord.length || pos === to) return;
    const item = ord.splice(pos, 1)[0];
    ord.splice(to, 0, item);
    state.printOrder = ord;
    renderPrint();
    saveDraftSoon();
  }
  function renderPrint() {
    const host = $("printList");
    if (!host) return;
    renderPrintNameOptions();
    const ord = printIndexes();
    host.innerHTML = "";
    if (!ord.length) {
      host.innerHTML = '<div class="muted">ยังไม่มีชุดข้อมูล — นำเข้า JSON ก่อน</div>';
      updatePrintPreview();
      return;
    }
    ord.forEach((ri, pos) => {
      const rec = state.records[ri];
      const off = !!state.printOff[ri];
      const row = document.createElement("div");
      row.className = "prow" + (ri === state.recIdx ? " sel" : "") + (off ? " off" : "");
      row.dataset.pos = String(pos);

      const handle = document.createElement("div");
      handle.className = "handle";
      handle.title = "ลากเพื่อจัดลำดับการพิมพ์";
      handle.textContent = "⠿";

      const idx = document.createElement("div");
      idx.className = "idx";
      idx.textContent = String(pos + 1);

      const cb = document.createElement("input");
      cb.type = "checkbox";
      cb.checked = !off;
      cb.title = "รวมชุดนี้ในการพิมพ์";
      cb.addEventListener("change", () => {
        if (cb.checked) delete state.printOff[ri];
        else state.printOff[ri] = true;
        row.classList.toggle("off", !cb.checked);
        updatePrintPreview();
        saveDraftSoon();
      });

      const lab = document.createElement("div");
      lab.className = "plabel";
      const key = state.printNameKey;
      const nm = key && rec && rec[key] != null && String(rec[key]) !== "" ? String(rec[key]) : "";
      lab.textContent = (nm || "ชุดที่ " + (ri + 1)) + "  ·  " + (rec ? Object.keys(rec).length + " ฟิลด์" : "ว่าง");
      lab.title = rec ? JSON.stringify(rec) : "";

      const acts = document.createElement("div");
      acts.style.cssText = "display:flex;gap:4px";
      const mk = (label, title, fn) => {
        const b = document.createElement("button");
        b.type = "button";
        b.className = "mini";
        b.textContent = label;
        b.title = title;
        b.addEventListener("click", fn);
        acts.appendChild(b);
        return b;
      };
      mk("▲", "ขึ้น", () => movePrintRow(pos, pos - 1)).disabled = pos === 0;
      mk("▼", "ลง", () => movePrintRow(pos, pos + 1)).disabled = pos === ord.length - 1;
      mk("ดู", "ไปที่ชุดนี้", () => gotoRecord(ri));

      row.appendChild(handle);
      row.appendChild(idx);
      const mid = document.createElement("div");
      mid.style.cssText = "display:flex;align-items:center;gap:6px;min-width:0";
      mid.appendChild(cb);
      mid.appendChild(lab);
      row.appendChild(mid);
      row.appendChild(acts);
      host.appendChild(row);
    });
    updatePrintPreview();
  }

  // ลากจัดลำดับด้วยนิ้ว (ทำงานบนมือถือ) — ลากที่หูหมาย ⠿
  function wireSortable(listEl) {
    let st = null;
    const rowH = (row) => {
      const r = row.getBoundingClientRect ? row.getBoundingClientRect() : null;
      return (r && r.height) || row.offsetHeight || 40;
    };
    listEl.addEventListener("pointerdown", (e) => {
      const h = e.target.closest(".handle");
      if (!h) return;
      const row = h.closest(".prow");
      if (!row) return;
      const rows = Array.prototype.slice.call(listEl.querySelectorAll(".prow"));
      st = { row: row, rows: rows, startY: e.clientY, from: rows.indexOf(row), to: rows.indexOf(row), h: rowH(row), moved: false };
      row.classList.add("dragging");
      try { h.setPointerCapture(e.pointerId); } catch (err) {}
      e.preventDefault();
    });
    window.addEventListener("pointermove", (e) => {
      if (!st) return;
      const dy = e.clientY - st.startY;
      if (!st.moved && Math.abs(dy) < 3) return;
      st.moved = true;
      const listRect = listEl.getBoundingClientRect();
      const top0 = listRect && listRect.height ? listRect.top : 0;
      let target = 0;
      st.rows.forEach((row, i) => {
        const mid = top0 + i * st.h + st.h / 2;
        if (e.clientY >= mid) target = i;
      });
      target = clamp(target, 0, st.rows.length - 1);
      st.row.style.transform = "translateY(" + dy + "px)";
      st.rows.forEach((row, i) => {
        if (row === st.row) return;
        let shift = 0;
        if (st.from < i && i <= target) shift = -st.h;
        else if (target <= i && i < st.from) shift = st.h;
        row.style.transform = shift ? "translateY(" + shift + "px)" : "";
      });
      st.to = target;
    });
    window.addEventListener("pointerup", () => {
      if (!st) return;
      const cur = st;
      st = null;
      cur.row.classList.remove("dragging");
      cur.rows.forEach((r) => { r.style.transform = ""; });
      if (cur.moved && cur.to !== cur.from) {
        const ord = printIndexes();
        const item = ord.splice(cur.from, 1)[0];
        ord.splice(cur.to, 0, item);
        state.printOrder = ord;
        renderPrint();
        saveDraftSoon();
        status("จัดลำดับการพิมพ์แล้ว (ลากจาก " + (cur.from + 1) + " ไป " + (cur.to + 1) + ")", "ok");
      }
    });
  }

  async function buildPrintFiles(split) {
    const sel = printSelected();
    if (!sel.length) { toast("ติ้กชุดข้อมูลที่ต้องการพิมพ์ก่อน", "err"); return; }
    loadShow("กำลังสร้างไฟล์พิมพ์…");
    try {
      await ensureFonts();
      const pages = printPages();
      if (split) {
        for (let i = 0; i < sel.length; i++) {
          loadProg((i / sel.length) * 100, "สร้างไฟล์ " + (i + 1) + "/" + sel.length);
          const html = buildExportHtml({ order: [sel[i]], pages: pages });
          downloadBlob(new Blob([html], { type: "text/html;charset=utf-8" }), printNameFor(sel[i], i + 1, sel.length) + ".html");
          await new Promise((r) => setTimeout(r, 120));
        }
        toast("สร้างไฟล์พิมพ์ " + sel.length + " ไฟล์ (แยกตามชื่อที่ตั้ง)", "ok");
      } else {
        const html = buildExportHtml({ order: sel, pages: pages });
        const name = printNameFor(sel[0], 1, sel.length) + "-all-" + pad2(sel.length) + ".html";
        downloadBlob(new Blob([html], { type: "text/html;charset=utf-8" }), name);
        toast("สร้างไฟล์พิมพ์รวม " + sel.length + " ชุดตามลำดับที่จัด", "ok");
        status("ไฟล์พิมพ์: " + name, "ok");
      }
    } catch (err) {
      console.error(err);
      toast("สร้างไฟล์พิมพ์ไม่สำเร็จ: " + err.message, "err");
    } finally {
      loadHide();
    }
  }

  function printNow() {
    const sel = printSelected();
    if (!sel.length) { toast("ติ้กชุดข้อมูลที่ต้องการพิมพ์ก่อน", "err"); return; }
    const pages = printPages();
    const frame = document.createElement("iframe");
    frame.style.cssText = "position:fixed;right:0;bottom:0;width:1px;height:1px;opacity:0;border:0";
    document.body.appendChild(frame);
    let i = 0;
    const next = () => {
      if (i >= sel.length) {
        frame.remove();
        toast("ส่งคิวพิมพ์ครบ " + sel.length + " ชุดแล้ว", "ok");
        status("สั่งพิมพ์ " + sel.length + " ชุดตามลำดับที่จัด", "ok");
        return;
      }
      const name = printNameFor(sel[i], i + 1, sel.length);
      const html = buildExportHtml({ order: [sel[i]], pages: pages });
      i++;
      try {
        const doc = frame.contentWindow.document;
        doc.open();
        doc.write(html);
        doc.close();
        if (typeof frame.contentWindow.focus === "function") frame.contentWindow.focus();
        frame.contentWindow.print();
        toast("สั่งพิมพ์ชุด " + i + "/" + sel.length + " — " + name, "ok");
      } catch (err) {
        console.error(err);
        frame.remove();
        toast("สั่งพิมพ์ไม่ได้: " + err.message + " — ลองใช้ปุ่ม สร้างไฟล์พิมพ์ แล้วเปิดไฟล์", "err");
        return;
      }
      setTimeout(next, 1500);
    };
    next();
  }

  /* ---------------- export ---------------- */
  function androidBridge() {
    return (typeof window !== "undefined" && window.ChbAndroid && typeof window.ChbAndroid.saveBase64 === "function")
      ? window.ChbAndroid : null;
  }
  function dataUrlToParts(dataUrl) {
    const s = String(dataUrl);
    const i = s.indexOf(",");
    if (s.indexOf("data:") !== 0 || i < 0) return null;
    const head = s.slice(5, i); // เหนือ "data:" ถึง comma
    if (!/;base64$/i.test(head)) return null;
    const mime = head.replace(/;base64$/i, "").split(";")[0] || "application/octet-stream";
    return { mime: mime, b64: s.slice(i + 1) };
  }
  function blobToDataUrl(blob) {
    return new Promise((res, rej) => {
      const fr = new FileReader();
      fr.onload = () => res(String(fr.result));
      fr.onerror = () => rej(new Error("อ่านข้อมูลไฟล์ไม่ได้"));
      fr.readAsDataURL(blob);
    });
  }
  function downloadDataUrl(dataUrl, name) {
    // ในแอป Android (WebView): ส่งไฟล์ให้ ChbAndroid.saveBase64 บันทึก+แชร์
    const bridge = androidBridge();
    if (bridge && typeof dataUrl === "string" && dataUrl.indexOf("data:") === 0) {
      const parts = dataUrlToParts(dataUrl);
      if (parts) {
        let res = "ok";
        try { res = bridge.saveBase64(name, parts.b64, parts.mime); }
        catch (err) { res = err && err.message ? err.message : "bridge error"; }
        if (res === "ok") toast("บันทึกไฟล์แล้ว: " + name, "ok");
        else toast("บันทึกไฟล์ไม่สำเร็จ: " + res, "err");
        return;
      }
    }
    const a = document.createElement("a");
    a.href = dataUrl;
    a.download = name;
    document.body.appendChild(a);
    a.click();
    a.remove();
  }
  function downloadBlob(blob, name) {
    const bridge = androidBridge();
    if (bridge) {
      blobToDataUrl(blob)
        .then((du) => downloadDataUrl(du, name))
        .catch((err) => toast("บันทึกไฟล์ไม่สำเร็จ: " + err.message, "err"));
      return;
    }
    const url = URL.createObjectURL(blob);
    downloadDataUrl(url, name);
    setTimeout(() => URL.revokeObjectURL(url), 4000);
  }
  function downloadJson(obj, name) {
    downloadBlob(new Blob([JSON.stringify(obj, null, 2)], { type: "application/json" }), name);
  }

  async function ensureFonts() {
    try {
      if (document.fonts && document.fonts.load) {
        const specs = [];
        state.pages.forEach((p) => p.objects.forEach((o) => {
          const fam = familyOf(o);
          if (fam) specs.push('700 16px "' + fam + '"', '400 16px "' + fam + '"');
        }));
        await Promise.all(specs.map((s) => Promise.resolve(document.fonts.load(s)).catch(() => null)));
      }
      if (document.fonts && document.fonts.ready) await document.fonts.ready;
    } catch (e) { /* ฟอนต์โหลดไม่ได้ — ใช้ค่าเริ่มต้นเดิม */ }
  }

  async function capturePage(pageIdx) {
    const el = document.querySelector('.page[data-idx="' + pageIdx + '"]');
    if (!el) throw new Error("ไม่พบหน้า");
    const prevSel = state.sel;
    state.sel = null;
    updateSelDom();
    await new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)));
    const canvas = await html2canvas(el, { scale: 2, backgroundColor: "#fff", useCORS: true, logging: false });
    state.sel = prevSel;
    updateSelDom();
    return canvas;
  }

  function addHistory(name, thumb, dataUrl) {
    history.unshift({ name: name, time: Date.now(), thumb: thumb, dataUrl: dataUrl });
    if (history.length > 8) history.pop();
    renderHistory();
  }

  async function exportPng() {
    if (!state.pages.length) return;
    loadShow("กำลังสร้าง PNG…");
    try {
      await ensureFonts();
      const canvas = await capturePage(state.pageIdx);
      const dataUrl = canvas.toDataURL("image/png");
      const name = "overlay_page" + (state.pageIdx + 1) + ".png";
      downloadDataUrl(dataUrl, name);
      const th = document.createElement("canvas");
      th.width = 130;
      th.height = Math.round(th.width * canvas.height / canvas.width);
      th.getContext("2d").drawImage(canvas, 0, 0, th.width, th.height);
      addHistory(name, th.toDataURL("image/jpeg", 0.6), dataUrl);
      toast("สร้าง PNG แล้ว", "ok");
      status("สร้าง PNG แล้ว: " + name, "ok");
    } catch (err) {
      console.error(err);
      toast("สร้าง PNG ไม่สำเร็จ: " + err.message, "err");
    } finally {
      loadHide();
    }
  }

  async function exportPdf(all) {
    if (!state.pages.length) return;
    const targets = all ? state.pages.map((_, i) => i) : [state.pageIdx];
    loadShow("กำลังสร้าง PDF…");
    try {
      await ensureFonts();
      const wasAll = state.viewAll;
      if (all && !wasAll) { state.viewAll = true; renderPages(); await new Promise((r) => requestAnimationFrame(r)); }
      const { jsPDF } = window.jspdf;
      let doc = null;
      for (let k = 0; k < targets.length; k++) {
        loadProg((k / targets.length) * 100, "เรนเดอร์หน้า " + (k + 1) + "/" + targets.length);
        const canvas = await capturePage(targets[k]);
        const img = canvas.toDataURL("image/jpeg", 0.95);
        const pg = state.pages[targets[k]];
        const ar = pg.bg ? pg.bg.w / pg.bg.h : A4.w / A4.h;
        const hPt = 842, wPt = Math.round(842 * ar * 100) / 100;
        if (!doc) doc = new jsPDF({ unit: "pt", format: [wPt, hPt], orientation: wPt > hPt ? "l" : "p", compress: true });
        else doc.addPage([wPt, hPt], wPt > hPt ? "l" : "p");
        doc.addImage(img, "JPEG", 0, 0, wPt, hPt);
        if (k === targets.length - 1) loadProg(100);
      }
      if (all && !wasAll) { state.viewAll = false; renderPages(); }
      const name = all ? "overlay_all.pdf" : "overlay_page" + (state.pageIdx + 1) + ".pdf";
      if (androidBridge()) downloadDataUrl(doc.output("datauristring"), name);
      else doc.save(name);
      addHistory(name, null, null);
      toast("สร้าง PDF แล้ว (" + targets.length + " หน้า)", "ok");
      status("สร้าง PDF แล้ว: " + name, "ok");
    } catch (err) {
      console.error(err);
      state.viewAll = false;
      renderPages();
      toast("สร้าง PDF ไม่สำเร็จ: " + err.message, "err");
    } finally {
      loadHide();
    }
  }

  function exportLayout() {
    const obj = {
      v: 3,
      pages: state.pages,
      records: state.records,
      pageIdx: state.pageIdx,
      recIdx: state.recIdx,
      fonts: state.fonts,
      defaultFamily: state.defaultFamily,
      crossMode: crossMode(),
      crossGain: crossGain()
    };
    downloadJson(obj, "overlay_layout.json");
    toast("Export layout แล้ว (รวมฟอนต์ที่แนบ)", "ok");
  }

  /* ---- Export HTML: ไฟล์ยืนยันตัวตนเปิดในเบราว์เซอร์แล้ว พิมพ์/บันทึกเป็น PDF ----
     ใส่ทุก record × ทุกหน้า ค่ากรอกจริง พื้นหลัง/รูป/QR ฝังเป็น data URL ครบ ใช้เน็ตครั้งแรกที่เปิด (ฟอนต์) */
  function qrDataUrlFor(text) {
    if (typeof QRCode !== "function" || !text) return null;
    const box = document.createElement("div");
    box.style.cssText = "position:fixed;left:-99999px;top:0;width:220px;height:220px";
    document.body.appendChild(box);
    try {
      new QRCode(box, { text: text, width: 220, height: 220, correctLevel: QRCode.CorrectLevel.M });
      const cv = box.querySelector("canvas");
      const u = cv && typeof cv.toDataURL === "function" ? cv.toDataURL("image/png") : null;
      return u || null;
    } catch (e) {
      return null;
    } finally {
      box.remove();
    }
  }

  function buildExportHtml(opts) {
    const o = opts || {};
    const pageList = o.pages && o.pages.length ? o.pages : state.pages.map((_, i) => i);
    const recList = o.order && o.order.length ? o.order
      : (state.records.length ? state.records.map((_, i) => i) : [-1]);

    const bgs = state.pages.map((p) => (p.bg && p.bg.src ? p.bg.src : null));
    const ars = state.pages.map((p) => (p.bg ? p.bg.w / p.bg.h : A4.w / A4.h));
    // ใช้ฟังก์ชัน CSS ชุดเดียวกับพรีวิว -> ผลลัพธ์ตรงกัน 100%
    const pageObjs = state.pages.map((p) => p.objects.map((o2) => ({
      id: o2.id, t: o2.type, k: o2.key || "", tx: o2.text || "",
      fit: o2.fit || "contain",
      box: objBoxCss(o2), oc: objContentCss(o2, 1),
      src: o2.type === "image" && o2.src ? o2.src : "",
      sm: o2.type === "stamp" ? stampCfg(o2) : null
    })));
    const recs = [];
    const tv = {};
    const qr = {};
    recList.forEach((ri, n) => {
      const rec = ri < 0 ? null : state.records[ri];
      recs.push(rec);
      pageList.forEach((pi) => pageObjs[pi].forEach((o2) => {
        const val = (rec && o2.k && rec[o2.k] != null && String(rec[o2.k]) !== "") ? String(rec[o2.k]) : "";
        if (o2.t === "text") tv[n + "|" + o2.id] = o2.k ? val : (o2.tx || "");
        if (o2.t === "qr") {
          const q = qrDataUrlFor(val || o2.tx);
          if (q) qr[n + "|" + o2.id] = q;
        }
      }));
    });
    // ตัวเลือก “ติ้กรายการที่จะพิมพ์” + ชื่อไฟล์ ย้ายเข้าไปอยู่ในไฟล์ HTML นี้เลย
    const prt = {
      tpl: state.printNameTpl || "{n}_{key}",
      key: state.printNameKey || "",
      idx: recList.map((ri) => (ri < 0 ? 0 : ri + 1)),
      on: recList.map((ri) => (ri < 0 ? true : !state.printOff[ri]))
    };
    const payload = JSON.stringify({ bgs: bgs, ars: ars, pageObjs: pageObjs, pageList: pageList, recs: recs, tv: tv, qr: qr, prt: prt })
      .replace(/</g, "\\u003c"); // กันข้อมูลมี </script> ทำ HTML พัง

    const ar0 = ars[pageList[0]] || (A4.w / A4.h);
    const pageWmm = Math.round(297 * ar0 * 100) / 100;
    const embedded = fontsToEmbed();

    return `<!DOCTYPE html>
<html lang="th">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>เทมเพลตเอกสาร</title>
<style>
${fontFaceCss(embedded)}
@page{size:${pageWmm}mm 297mm;margin:0}
*{box-sizing:border-box}
html,body{margin:0;background:#555;font-family:${FONT_FALLBACK}}
.bar{position:sticky;top:0;background:#0071e3;color:#fff;padding:10px 14px;display:flex;gap:10px;align-items:center;flex-wrap:wrap;font-size:14px}
.topbar{position:sticky;top:0;z-index:9;box-shadow:0 2px 8px rgba(0,0,0,.25)}
.bar button{font:inherit;border:0;border-radius:8px;padding:8px 16px;background:#fff;color:#0071e3;font-weight:700;cursor:pointer}
.bar .nml{font-size:12px;opacity:.9}
.bar input[type=text]{font:inherit;padding:6px 8px;border:0;border-radius:8px;width:150px}
.selbar{background:#eef4fb;padding:8px 14px;display:flex;gap:8px;flex-wrap:wrap;align-items:center;border-bottom:1px solid #c9d7e8;font-size:13px;max-height:120px;overflow:auto}
.selbar .prow{display:flex;align-items:center;gap:6px;background:#fff;border:1px solid #c9d7e8;border-radius:999px;padding:4px 10px;cursor:pointer}
.selbar .prow .idx{font-weight:700;color:#0071e3;font-size:11px}
.selbar .prow .nm{max-width:190px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.selbar .prow.off{opacity:.45}
#out{padding:14px}
.page{position:relative;width:794px;height:1123px;margin:0 auto 14px;background:#fff;overflow:hidden;box-shadow:0 4px 18px rgba(0,0,0,.4);page-break-after:always;break-after:page}
.page:last-child{page-break-after:auto;break-after:auto}
.page .bg{position:absolute;inset:0;width:100%;height:100%;object-fit:fill;display:block}
.oc .ph{color:#9aa4ae;font-weight:400}
.o img{max-width:100%;max-height:100%;display:block}
.o img.fit-contain{object-fit:contain}.o img.fit-cover{object-fit:cover}.o img.fit-fill{object-fit:fill}
@media print{body{background:#fff}.topbar{display:none}#out{padding:0}.page{margin:0;box-shadow:none}}
</style>
</head>
<body>
<div class="topbar">
<div class="bar">
  <b>เทมเพลตเอกสาร</b>
  <span id="count"></span>
  <span class="nml">ชื่อไฟล์</span>
  <input type="text" id="nmtpl" value="${prt.tpl.replace(/"/g, "&quot;")}" title="ใช้ {n} = ลำดับที่เลือก, {i} = index ชุดข้อมูล, {key} = ค่า key, {total} = จำนวนที่เลือก, {date} = วันที่วันนี้">
  <button id="pAll" type="button">ติ้กทั้งหมด</button>
  <button id="pNone" type="button">เอาติ้กทั้งหมด</button>
  <button id="pPrint" type="button">พิมพ์เฉพาะที่ติ้ก</button>
  <button id="pEach" type="button">พิมพ์ทีละชุด (แยกไฟล์)</button>
</div>
<div class="selbar" id="pList"></div>
</div>
<div id="out"></div>
<script>
var D=${payload};
function pad2(n){return n<10?"0"+n:""+n;}
/* stamp: ค่าตายตัวในเทมเพลต แต่คำนวณใหม่จากเวลาปัจจุบันทุกครั้งที่เรนเดอร์/พิมพ์ */
function stampText(sm){
  sm=sm||{};
  var d=null, m;
  if(sm.mode==="base"){m=/^(\d{4})-(\d{1,2})-(\d{1,2})/.exec(String(sm.base||""));if(m)d=new Date(+m[1],+m[2]-1,+m[3]);}
  if(!d)d=new Date();
  var sh=parseInt(sm.shift,10);if(isNaN(sh))sh=0;if(sh>12)sh=12;if(sh<-12)sh=-12;
  if(sh){var day=d.getDate();d=new Date(d.getFullYear(),d.getMonth()+sh,1);
    d.setDate(Math.min(day,new Date(d.getFullYear(),d.getMonth()+1,0).getDate()));}
  var date=pad2(d.getDate())+"/"+pad2(d.getMonth()+1)+"/"+d.getFullYear();
  var time=pad2(d.getHours())+":"+pad2(d.getMinutes());
  var p=sm.part||"datetime";
  if(p==="date")return date;
  if(p==="time")return time;
  if(p==="month")return pad2(d.getMonth()+1)+"/"+d.getFullYear();
  return date+" "+time;
}
/* ชื่อไฟล์ดาวน์โหลด: {n} = ลำดับที่เลือก {i} = index ชุดข้อมูล {key} {total} {date} */
function nameFor(ri,pos,total){
  var inp=document.getElementById("nmtpl");
  var tpl=(inp&&inp.value)||D.prt.tpl||"{n}_{key}";
  var rec=D.recs[ri]||{};
  var key=D.prt.key||"";
  var kv=(key&&rec[key]!=null&&String(rec[key])!=="")?String(rec[key]):"";
  var d=new Date();
  var out=String(tpl)
    .replace(/\{n\}/g,pad2(pos))
    .replace(/\{i\}/g,String(D.prt.idx[ri]))
    .replace(/\{key\}/g,kv||("r"+D.prt.idx[ri]))
    .replace(/\{total\}/g,String(total))
    .replace(/\{date\}/g,d.getFullYear()+pad2(d.getMonth()+1)+pad2(d.getDate()));
  return out.replace(/[\\\\/:*?"<>|\\r\\n\\t]+/g,"-").replace(/\\s+/g,"_")
    .replace(/_+/g,"_").replace(/^[_\\-.]+|[_\\-.]+$/g,"").slice(0,80)||("template_"+d.getFullYear()+pad2(d.getMonth()+1)+pad2(d.getDate()));
}
function allIdx(){var r=[];for(var i=0;i<D.recs.length;i++)r.push(i);return r;}
function checked(){var r=[],b=document.querySelectorAll("#pList input"),i;for(i=0;i<b.length;i++)if(b[i].checked)r.push(i);return r;}
function render(list){
  var out=document.getElementById("out");
  out.innerHTML="";
  var frag=document.createDocumentFragment();
  var pages=0;
  list.forEach(function(ri){
    D.pageList.forEach(function(pi){
      var objs=D.pageObjs[pi]||[];
      var rec=D.recs[ri];
      var page=document.createElement("div");
      page.className="page";
      page.style.height=Math.round(794/(D.ars[pi]||(794/1123)))+"px";
      if(D.bgs[pi]){var bg=document.createElement("img");bg.className="bg";bg.src=D.bgs[pi];page.appendChild(bg);}
      objs.forEach(function(o){
        var el=document.createElement("div");
        el.className="o";
        el.style.cssText=o.box;
        var oc=document.createElement("div");
        oc.className="oc";
        oc.style.cssText=o.oc;
        if(o.t==="text"){
          var v=D.tv[ri+"|"+o.id]||"";
          var sp=document.createElement("span");
          if(v){sp.textContent=v;}else{sp.textContent=o.tx||"";sp.className="ph";}
          oc.appendChild(sp);
        }else if(o.t==="stamp"){
          var st=document.createElement("span");
          st.className="stampval";
          st.textContent=stampText(o.sm);
          oc.appendChild(st);
        }else if(o.t==="image"){
          if(o.src){var im=document.createElement("img");im.className="fit-"+(o.fit||"contain");im.src=o.src;oc.appendChild(im);}
        }else if(o.t==="qr"){
          var q=D.qr[ri+"|"+o.id];
          if(q){var qi=document.createElement("img");qi.src=q;oc.appendChild(qi);}
        }
        el.appendChild(oc);
        page.appendChild(el);
      });
      frag.appendChild(page);pages++;
    });
  });
  out.appendChild(frag);
  var total=D.recs.length, ck=checked().length;
  document.getElementById("count").textContent=total+" ชุดข้อมูล · ติ้ก "+ck+" ชุด · แสดง "+list.length+" ชุด / "+pages+" หน้า";
}
/* แถบติ้กรายการที่จะพิมพ์ — อยู่ในไฟล์นี้เลย เปิดแล้วเลือกได้ทันที */
function renderSel(){
  var host=document.getElementById("pList");
  host.innerHTML="";
  var lab=document.createElement("b");
  lab.textContent="เลือกพิมพ์:";
  host.appendChild(lab);
  D.recs.forEach(function(rec,i){
    var row=document.createElement("label");
    row.className="prow";
    var cb=document.createElement("input");
    cb.type="checkbox";
    cb.checked=!!D.prt.on[i];
    var idx=document.createElement("span");
    idx.className="idx";
    idx.textContent="#"+D.prt.idx[i];
    var nm=document.createElement("span");
    nm.className="nm";
    nm.textContent=nameFor(i,i+1,D.recs.length);
    row.appendChild(cb);row.appendChild(idx);row.appendChild(nm);
    row.addEventListener("click",function(){setTimeout(updateCount,0);});
    host.appendChild(row);
    row.dataset.i=String(i);
  });
  updateCount();
}
function updateCount(){
  var ck=checked().length;
  var p=document.getElementById("pPrint");
  if(p)p.textContent="พิมพ์ "+ck+" ชุดที่ติ้ก";
  document.querySelectorAll("#pList .prow").forEach(function(r){
    r.classList.toggle("off",!r.querySelector("input").checked);
  });
  if(D.__last)document.getElementById("count").textContent=D.recs.length+" ชุดข้อมูล · ติ้ก "+ck+" ชุด";
}
/* พิมพ์เฉพาะที่ติ้ก — ตั้งชื่อไฟล์ดาวน์โหลดตาม index ที่เลือกก่อนสั่งพิมพ์ */
var printing=false;
function printSel(split){
  var list=checked();
  if(!list.length){alert("ติ้กชุดข้อมูลที่ต้องการพิมพ์ก่อน");return;}
  printing=true;
  D.__last=1;
  if(!split){
    render(list);
    document.title=nameFor(list[0],1,list.length);
    try{window.focus();window.print();}catch(e){}
    setTimeout(function(){printing=false;document.title="เทมเพลตเอกสาร";render(allIdx());D.__last=0;},900);
    return;
  }
  var i=0;
  function next(){
    if(i>=list.length){
      printing=false;document.title="เทมเพลตเอกสาร";render(allIdx());D.__last=0;return;
    }
    render([list[i]]);
    document.title=nameFor(list[i],i+1,list.length);
    try{window.focus();window.print();}catch(e){}
    i++;
    setTimeout(next,1200);
  }
  next();
}
document.getElementById("pAll").addEventListener("click",function(){
  document.querySelectorAll("#pList input").forEach(function(b){b.checked=true;});updateCount();
});
document.getElementById("pNone").addEventListener("click",function(){
  document.querySelectorAll("#pList input").forEach(function(b){b.checked=false;});updateCount();
});
document.getElementById("pPrint").addEventListener("click",function(){printSel(false);});
document.getElementById("pEach").addEventListener("click",function(){printSel(true);});
// ถ้าผู้ใช้สั่งพิมพ์เองจากเมนูเบราว์เซอร์ ให้ใช้เฉพาะที่ติ้ก + คำนวณเวลาใหม่
window.addEventListener("beforeprint",function(){
  if(printing)return;
  var list=checked();
  render(list.length?list:allIdx());
});
renderSel();
render(allIdx());
</script>
</body>
</html>
`;
  }

  async function exportHtml() {
    if (!state.pages.length) { toast("ยังไม่มีหน้าเอกสาร", "err"); return; }
    loadShow("กำลังสร้าง HTML…");
    try {
      await ensureFonts();
      const html = buildExportHtml();
      const name = "template_" + new Date().toISOString().slice(0, 10) + ".html";
      downloadBlob(new Blob([html], { type: "text/html;charset=utf-8" }), name);
      toast("Export HTML แล้ว — เปิดไฟล์แล้วกด พิมพ์/บันทึกเป็น PDF", "ok");
      status("Export HTML แล้ว: " + name + " (" + state.pages.length + " หน้า × " + state.records.length + " records)", "ok");
    } catch (err) {
      console.error(err);
      toast("Export HTML ไม่สำเร็จ: " + err.message, "err");
    } finally {
      loadHide();
    }
  }

  async function importLayout(file) {
    try {
      const txt = await file.text();
      const data = JSON.parse(txt);
      if (!data || !Array.isArray(data.pages)) throw new Error("รูปแบบ layout ไม่ถูกต้อง");
      state.pages = data.pages.map((p) => ({
        id: p.id || uid(),
        bg: p.bg || null,
        objects: Array.isArray(p.objects) ? p.objects : []
      }));
      state.records = Array.isArray(data.records) ? data.records : [];
      state.pageIdx = clamp(data.pageIdx || 0, 0, Math.max(0, state.pages.length - 1));
      state.recIdx = clamp(data.recIdx || 0, 0, Math.max(0, state.records.length - 1));
      state.sel = null;
      applyFontData(data);
      applyCrossMode(data);
      printIndexes();
      renderPrint();
      refreshAll();
      renderHistory();
      saveDraftSoon();
      toast("Import layout แล้ว", "ok");
      status("Import layout แล้ว (" + state.pages.length + " หน้า)", "ok");
    } catch (err) {
      console.error(err);
      toast("Import ไม่สำเร็จ: " + err.message, "err");
    }
  }

  /* ---------------- draft (auto-save) ---------------- */
  let draftTimer = null;
  function saveDraftSoon() {
    clearTimeout(draftTimer);
    draftTimer = setTimeout(saveDraft, 700);
  }
  function draftData(lite) {
    const d = {
      pages: state.pages, records: state.records,
      pageIdx: state.pageIdx, recIdx: state.recIdx,
      defaultFamily: state.defaultFamily,
      printOrder: printIndexes(),
      printOff: state.printOff,
      printNameKey: state.printNameKey,
      printNameTpl: state.printNameTpl,
      crossMode: crossMode(),
      crossGain: crossGain(),
      time: Date.now()
    };
    d.fonts = lite ? state.fonts.map((f) => Object.assign({}, f, { dataUrl: "" })) : state.fonts;
    return d;
  }
  function applyFontData(data) {
    state.fonts = Array.isArray(data && data.fonts)
      ? data.fonts.filter((f) => f && f.family).map((f) => Object.assign({ embed: true }, f)) : [];
    state.defaultFamily = (data && data.defaultFamily) || "";
    if (state.defaultFamily && fontFamilies().indexOf(state.defaultFamily) < 0) state.defaultFamily = "";
    syncFontFace();
    renderFonts();
    refreshFontSelectors();
  }
  function saveDraft() {
    try {
      localStorage.setItem(DRAFT_KEY, JSON.stringify(draftData(false)));
    } catch (e) {
      try { // พื้นที่เต็ม — เซฟแบบไม่รวมภาพพื้นหลังและข้อมูลฟอนต์
        const lite = draftData(true);
        lite.pages.forEach((p) => { if (p.bg) p.bg = { kind: p.bg.kind, src: "", w: p.bg.w, h: p.bg.h, name: p.bg.name }; });
        localStorage.setItem(DRAFT_KEY, JSON.stringify(lite));
        toast("เซฟ draft (ไม่รวมภาพพื้นหลังและไฟล์ฟอนต์ — พื้นที่เต็ม)");
      } catch (e2) { /* ปล่อยผ่าน */ }
    }
  }
  function loadDraftBanner() {
    try {
      const raw = localStorage.getItem(DRAFT_KEY);
      if (!raw) return;
      JSON.parse(raw);
      $("draftBanner").classList.add("show");
    } catch (e) {}
  }
  function restoreDraft() {
    try {
      const data = JSON.parse(localStorage.getItem(DRAFT_KEY) || "null");
      if (!data || !Array.isArray(data.pages)) throw new Error("ไม่พบ draft");
      state.pages = data.pages;
      state.records = Array.isArray(data.records) ? data.records : [];
      state.pageIdx = clamp(data.pageIdx || 0, 0, state.pages.length - 1);
      state.recIdx = clamp(data.recIdx || 0, 0, Math.max(0, state.records.length - 1));
      state.sel = null;
      if (data.printOrder) state.printOrder = data.printOrder;
      if (data.printOff) state.printOff = data.printOff;
      if (typeof data.printNameKey === "string") state.printNameKey = data.printNameKey;
      if (typeof data.printNameTpl === "string") state.printNameTpl = data.printNameTpl;
      applyCrossMode(data);
      applyFontData(data);
      printIndexes();
      renderPrint();
      refreshAll();
      toast("กู้คืน draft แล้ว", "ok");
      status("กู้คืน draft แล้ว", "ok");
    } catch (err) {
      toast("กู้ draft ไม่ได้: " + err.message, "err");
    }
    $("draftBanner").classList.remove("show");
  }
  function dismissDraft() {
    $("draftBanner").classList.remove("show");
    try { localStorage.removeItem(DRAFT_KEY); } catch (e) {}
  }

  /* ---------------- wiring ---------------- */
  function wire() {
    // drag/resize บน canvas
    const wrap = $("canvasWrap");
    wrap.addEventListener("pointerdown", onPointerDown);
    window.addEventListener("pointermove", onPointerMove, { passive: true });
    window.addEventListener("pointerup", onPointerUp);
    window.addEventListener("pointercancel", onPointerUp);

    // BG
    $("bgFile").addEventListener("change", (e) => { loadBgFiles(e.target.files); e.target.value = ""; });
    $("bgClear").addEventListener("click", clearBg);

    // JSON — เลือกได้หลายไฟล์, รวมเข้า session เดียวกัน
    $("jsonFile").addEventListener("change", (e) => {
      const files = Array.from(e.target.files || []);
      e.target.value = "";
      if (files.length) loadRecordFiles(files, false);
    });
    $("jsonFile2").addEventListener("change", (e) => {
      const files = Array.from(e.target.files || []);
      e.target.value = "";
      if (files.length) loadRecordFiles(files, true); // การ์ดฝั่งซ้าย = ต่อท้ายของเดิม
    });
    $("btnLoadFileJson").addEventListener("click", () => $("jsonFile2").click());
    $("fillImportJson").addEventListener("click", () => $("jsonFile2").click());
    const loadPaste = () => {
      const txt = $("pasteJson").value.trim();
      if (!txt) { toast("วาง JSON ก่อน", "err"); return; }
      try { loadRecords(JSON.parse(txt), "paste", false); }
      catch (err) { toast("JSON ไม่ถูกต้อง: " + err.message, "err"); }
    };
    $("btnPasteLoad").addEventListener("click", loadPaste);
    $("btnPasteLoad2").addEventListener("click", loadPaste);
    $("btnClearJson").addEventListener("click", clearRecords);

    // record nav
    $("btnFirst").addEventListener("click", () => gotoRecord(0));
    $("btnPrev").addEventListener("click", () => gotoRecord(state.recIdx - 1));
    $("btnNext").addEventListener("click", () => gotoRecord(state.recIdx + 1));
    $("btnLast").addEventListener("click", () => gotoRecord(state.records.length - 1));

    // page nav (แถวเหนือ canvas)
    $("btnPgPrev").addEventListener("click", () => gotoPage(state.pageIdx - 1));
    $("btnPgNext").addEventListener("click", () => gotoPage(state.pageIdx + 1));
    $("btnAddPage").addEventListener("click", addBlankPage);
    $("btnDelPage").addEventListener("click", delPage);

    // snap / view all
    $("btnSnap").addEventListener("click", () => {
      state.snap = !state.snap;
      $("btnSnap").className = state.snap ? "snap-on" : "snap-off";
      $("btnSnap").textContent = state.snap ? "⊹ Snap ON" : "⊹ Snap OFF";
    });
    $("btnViewAll").addEventListener("click", () => {
      state.viewAll = !state.viewAll;
      $("btnViewAll").classList.toggle("active", state.viewAll);
      state.sel = null;
      renderPages();
      renderEditor();
      toast(state.viewAll ? "ดูทุกหน้าพร้อมกัน" : "กลับหน้าเดียว");
    });

    // ฟอนต์ที่ผู้ใช้แนบ
    $("btnFonts").addEventListener("click", () => $("fontFile").click());
    $("fontFile").addEventListener("change", (e) => {
      const files = Array.from(e.target.files || []);
      e.target.value = "";
      if (files.length) loadFontFiles(files);
    });
    $("fontClear").addEventListener("click", clearFonts);
    $("fontDefault").addEventListener("change", () => {
      state.defaultFamily = $("fontDefault").value || "";
      refreshFontSelectors();
      renderFonts();
      refreshAll();
      saveDraftSoon();
    });

    // เป้ากากบาท (วางตำแหน่งแบบสัมพัทธ์)
    $("btnCross").addEventListener("click", () => toggleCross());
    $("crossAnchor").addEventListener("change", () => { applyCrossToObj(selected()); renderCrosshair(); });
    $("crossMode").addEventListener("change", () => {
      saveDraftSoon();
      status("โหมดเคอเซอร์: " + (crossMode() === "trackpad" ? "เลื่อนสัมพัทธ์ (Trackpad)" : "แตะวางเป้า (Absolute)"), "ok");
    });
    $("crossGain").addEventListener("change", saveDraftSoon);
    $("crossNudgeL").addEventListener("click", () => nudgeCross(-0.1, 0));
    $("crossNudgeR").addEventListener("click", () => nudgeCross(0.1, 0));
    $("crossNudgeU").addEventListener("click", () => nudgeCross(0, -0.1));
    $("crossNudgeD").addEventListener("click", () => nudgeCross(0, 0.1));

    // ปรับหลายฟิลด์พร้อมกัน
    $("batchSelectAll").addEventListener("click", () => {
      state.fieldSel = [];
      state.pages.forEach((p) => p.objects.forEach((o) => state.fieldSel.push(o.id)));
      renderFields();
      renderCounters();
    });
    $("batchClearSel").addEventListener("click", () => { state.fieldSel = []; renderFields(); renderCounters(); });
    $("batchApply").addEventListener("click", batchApply);

    // ลำดับการสั่งพิมพ์ + ชื่อไฟล์
    $("printAllCheck").addEventListener("click", () => { state.printOff = {}; renderPrint(); saveDraftSoon(); });
    $("printNoneCheck").addEventListener("click", () => {
      state.printOff = {};
      printIndexes().forEach((i) => { state.printOff[i] = true; });
      renderPrint();
      saveDraftSoon();
    });
    $("printReset").addEventListener("click", () => {
      state.printOrder = null;
      state.printOff = {};
      renderPrint();
      saveDraftSoon();
      toast("คืนลำดับเดิมแล้ว");
    });
    $("printNameKey").addEventListener("change", () => {
      state.printNameKey = $("printNameKey").value || "";
      renderPrint();
      saveDraftSoon();
    });
    $("printNameTpl").addEventListener("input", () => {
      state.printNameTpl = $("printNameTpl").value || "{n}_{key}";
      updatePrintPreview();
      saveDraftSoon();
    });
    $("printScope").addEventListener("change", () => { state.printScope = $("printScope").value; saveDraftSoon(); });
    $("printOneFile").addEventListener("click", () => buildPrintFiles(false));
    $("printSplit").addEventListener("click", () => buildPrintFiles(true));
    $("printNow").addEventListener("click", printNow);
    wireSortable($("printList"));

    // add object
    document.querySelectorAll("[data-add]").forEach((btn) => {
      btn.addEventListener("click", () => {
        const type = btn.dataset.add;
        if (type === "image") {
          pendingImageAdd = (src) => addObject("image", { src: src });
          $("objImageFile").click();
          return;
        }
        addObject(type);
      });
    });
    $("objImageFile").addEventListener("change", async (e) => {
      const f = e.target.files[0];
      e.target.value = "";
      if (!f || !pendingImageAdd) return;
      try {
        const src = await readFileDataUrl(f);
        pendingImageAdd(src);
      } catch (err) { toast(err.message, "err"); }
      pendingImageAdd = null;
    });
    $("btnAddField").addEventListener("click", () => { addObject("text"); switchTab("edit"); });

    // editor
    const posInputs = ["edX", "edY", "edW", "edH"];
    posInputs.forEach((id) => $(id).addEventListener("input", applyPos));
    $("btnApplyPos").addEventListener("click", applyPos);
    function applyPos() {
      const o = selected();
      if (!o) return;
      const x = parseFloat($("edX").value), y = parseFloat($("edY").value);
      const w = parseFloat($("edW").value), h = parseFloat($("edH").value);
      if (!isNaN(x)) o.x = clamp(x, -50, 100);
      if (!isNaN(y)) o.y = clamp(y, -50, 100);
      if (!isNaN(w)) o.w = clamp(w, 2, 200);
      if (!isNaN(h)) o.h = clamp(h, 1.2, 200);
      applyObjDom(o);
      fitFonts();
      saveDraftSoon();
    }

    const simple = {
      edKey: (o, v) => { o.key = v.trim(); },
      edText: (o, v) => { o.text = v; },
      edType: (o, v) => { o.type = v; },
      edAlign: (o, v) => { o.align = v; },
      edColor: (o, v) => { o.color = v; },
      edImageFit: (o, v) => { o.fit = v; },
      edFontSize: (o, v) => { o.fs = clamp(parseFloat(v) || 16, 6, 120); },
      edLineHeight: (o, v) => { o.lh = clamp(parseFloat(v) || 1.05, 0.6, 4); },
      edLetter: (o, v) => { o.ls = parseFloat(v) || 0; },
      edValign: (o, v) => { o.valign = v; },
      edFamily: (o, v) => { o.family = v; },
      edRotation: (o, v) => { o.rot = clamp(parseFloat(v) || 0, -180, 180); },
      edStampPart: (o, v) => { o.stampPart = v; },
      edStampMode: (o, v) => { o.stampMode = v; },
      edStampBase: (o, v) => { o.stampBase = v.trim(); },
      edStampShift: (o, v) => { o.stampShift = clamp(parseInt(v, 10) || 0, -12, 12); }
    };
    Object.keys(simple).forEach((id) => {
      $(id).addEventListener("change", () => {
        const o = selected();
        if (!o) return;
        simple[id](o, $(id).value);
        refreshAll();
        saveDraftSoon();
      });
    });
    $("edBold").addEventListener("change", () => {
      const o = selected();
      if (o) { o.bold = $("edBold").checked; updateBoldNote(); refreshAll(); saveDraftSoon(); }
    });
    $("edItalic").addEventListener("change", () => {
      const o = selected();
      if (o) { o.italic = $("edItalic").checked; refreshAll(); saveDraftSoon(); }
    });
    $("bFamily").addEventListener("change", updateBoldNote);
    $("edLockAspect").addEventListener("change", () => {
      const o = selected();
      if (o) { o.lockAspect = $("edLockAspect").checked; saveDraftSoon(); }
    });
    $("edLocked").addEventListener("change", () => {
      const o = selected();
      if (o) { o.locked = $("edLocked").checked; refreshAll(); saveDraftSoon(); }
    });
    $("btnApply").addEventListener("click", () => { applyPos(); refreshAll(); toast("Apply แล้ว", "ok"); });
    $("btnDup").addEventListener("click", duplicateSelected);
    $("btnDel").addEventListener("click", () => { if (state.sel) deleteObj(state.sel.page, state.sel.id); });
    $("btnFront").addEventListener("click", () => reorder(1));
    $("btnBack").addEventListener("click", () => reorder(-1));

    // fill pane
    $("fillSample").addEventListener("click", () => {
      const keys = allKeys();
      const rec = {};
      keys.forEach((k, i) => { rec[k] = k.match(/id|เลข|รหัส/i) ? "00" + (i + 1) : "ตัวอย่าง" + (i + 1); });
      state.records.push(rec);
      state.recIdx = state.records.length - 1;
      refreshAll();
      saveDraftSoon();
    });
    $("fillAdd").addEventListener("click", () => {
      state.records.push({});
      state.recIdx = state.records.length - 1;
      refreshAll();
      saveDraftSoon();
    });
    $("fillDup").addEventListener("click", () => {
      const rec = curRecord();
      if (!rec) return;
      state.records.splice(state.recIdx + 1, 0, JSON.parse(JSON.stringify(rec)));
      state.recIdx++;
      refreshAll();
      saveDraftSoon();
    });
    $("fillDel").addEventListener("click", () => {
      if (!state.records.length) return;
      state.records.splice(state.recIdx, 1);
      state.recIdx = clamp(state.recIdx, 0, Math.max(0, state.records.length - 1));
      refreshAll();
      saveDraftSoon();
    });
    $("fillExportKeys").addEventListener("click", () => downloadJson(allKeys(), "keys.json"));
    $("fillExportData").addEventListener("click", () => downloadJson(state.records, "data.json"));

    // export
    $("btnExportKeys").addEventListener("click", () => downloadJson(allKeys(), "keys.json"));
    $("btnExportLayout").addEventListener("click", exportLayout);
    $("btnExportHtml").addEventListener("click", exportHtml);
    $("btnImportLayout").addEventListener("click", () => $("layoutFile").click());
    $("layoutFile").addEventListener("change", (e) => {
      const f = e.target.files[0];
      e.target.value = "";
      if (f) importLayout(f);
    });
    $("btnPNG").addEventListener("click", exportPng);
    $("btnPDFone").addEventListener("click", () => exportPdf(false));
    $("btnPDFall").addEventListener("click", () => exportPdf(true));

    // history
    $("btnClearHist").addEventListener("click", () => { history = []; renderHistory(); });

    // tabs
    document.querySelectorAll(".tab").forEach((t) => {
      t.addEventListener("click", () => switchTab(t.dataset.tab));
    });

    // keyboard (desktop): ลบฟิลด์ / ขยับด้วยลูกศร
    window.addEventListener("keydown", (e) => {
      const tag = (e.target.tagName || "").toLowerCase();
      if (tag === "input" || tag === "textarea" || tag === "select") return;
      const o = selected();
      if (!o) return;
      if (e.key === "Delete" || e.key === "Backspace") {
        deleteObj(state.sel.page, state.sel.id);
        e.preventDefault();
        return;
      }
      const step = e.shiftKey ? 2 : 0.3;
      let moved = true;
      if (e.key === "ArrowLeft") o.x -= step;
      else if (e.key === "ArrowRight") o.x += step;
      else if (e.key === "ArrowUp") o.y -= step;
      else if (e.key === "ArrowDown") o.y += step;
      else moved = false;
      if (moved) {
        o.x = round(clamp(o.x, -o.w + 2, 98), 3);
        o.y = round(clamp(o.y, -o.h + 2, 98), 3);
        applyObjDom(o);
        syncEditorPos();
        if (state.cross) {
          state.crossPos = crossAnchorMode() === "c"
            ? { x: round(o.x + o.w / 2, 3), y: round(o.y + o.h / 2, 3) }
            : { x: o.x, y: o.y };
          renderCrosshair();
        }
        saveDraftSoon();
        e.preventDefault();
      }
    });

    // draft banner
    $("draftRestore").addEventListener("click", restoreDraft);
    $("draftDismiss").addEventListener("click", dismissDraft);

    window.addEventListener("resize", () => { fitFonts(); });
    window.addEventListener("pagehide", () => saveDraft());
  }

  function switchTab(name) {
    document.querySelectorAll(".tab").forEach((t) => t.classList.toggle("active", t.dataset.tab === name));
    document.querySelectorAll(".pane").forEach((p) => p.classList.toggle("active", p.dataset.pane === name));
    if (name === "fields") renderFields();
    if (name === "fill") renderFill();
    if (name === "print") renderPrint();
    if (name === "history") renderHistory();
  }

  /* ---------------- boot ---------------- */
  function boot() {
    wire();
    renderFonts();
    refreshFontSelectors();
    refreshAll();
    renderHistory();
    loadDraftBanner();
    status("พร้อมใช้งาน — โหลดภาพ/PDF เป็นพื้นหลัง แล้วเพิ่มฟิลด์ได้เลย", "ok");
    if (typeof pdfjsLib !== "undefined") {
      pdfjsLib.GlobalWorkerOptions.workerSrc = "https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.worker.min.js";
    }
  }

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", boot);
  else boot();
})();
