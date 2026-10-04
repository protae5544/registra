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
    sel: null // {page, id}
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
    if (o.type === "qr") return o.text || "";
    return "";
  }
  function placeholderFor(o) { return o.text || o.key || (o.type === "qr" ? "qr" : "ข้อความ"); }
  function allKeys() {
    const set = [];
    state.pages.forEach((p) => p.objects.forEach((o) => {
      if (o.key && set.indexOf(o.key) < 0) set.push(o.key);
    }));
    (curRecord() ? Object.keys(curRecord()) : []).forEach((k) => { if (set.indexOf(k) < 0) set.push(k); });
    state.records.forEach((r) => Object.keys(r || {}).forEach((k) => { if (set.indexOf(k) < 0) set.push(k); }));
    return set;
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
    el.style.left = o.x + "%";
    el.style.top = o.y + "%";
    el.style.width = o.w + "%";
    el.style.height = o.h + "%";
    el.style.transform = "rotate(" + (o.rot || 0) + "deg)";
    el.style.zIndex = String(o.z || 1);

    const content = document.createElement("div");
    content.className = "content";
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
    // text
    const val = objValue(o, rec);
    const span = document.createElement("span");
    if (val) { span.textContent = val; }
    else { span.textContent = placeholderFor(o); span.className = "ph"; }
    content.appendChild(span);
  }

  // ปรับขนาดฟอนต์ตามความกว้างหน้าจริง (เทียบกับฐาน 794px) กันภาพย่อแล้วฟอนต์เหลือ
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
        if (c && o.type === "text") c.style.fontSize = Math.max(8, (o.fs || 16) * k) + "px";
      });
    });
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
    if (!o) return;
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
  }

  function renderCounters() {
    let objs = 0, fields = 0;
    state.pages.forEach((p) => p.objects.forEach((o) => { objs++; if (o.key) fields++; }));
    $("sumRecords").textContent = String(state.records.length);
    $("sumFields").textContent = String(fields);
    $("sumObjects").textContent = String(objs);
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
        head.className = "itemhead";

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

        head.appendChild(keyIn);
        head.appendChild(chip);
        head.appendChild(acts);
        row.appendChild(head);

        const sample = document.createElement("div");
        sample.className = "sample";
        sample.textContent = "ข้อความ: " + (o.text || "-") + " · ตำแหน่ง: " + round(o.x, 1) + "%, " + round(o.y, 1) + "%";
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

  function onPointerMove(e) {
    if (!drag) return;
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

  function onPointerUp() {
    if (!drag) return;
    const el = document.querySelector('.obj[data-id="' + drag.id + '"]');
    if (el) el.classList.remove("dragging");
    drawGuides(null);
    if (drag.moved) { fitFonts(); saveDraftSoon(); status("เลื่อน/ปรับขนาดแล้ว (auto-save)", "ok"); }
    drag = null;
  }

  function applyObjDom(o) {
    const el = document.querySelector('.page[data-idx="' + state.sel.page + '"] .obj[data-id="' + o.id + '"]');
    if (!el) return;
    el.style.left = o.x + "%";
    el.style.top = o.y + "%";
    el.style.width = o.w + "%";
    el.style.height = o.h + "%";
    el.style.transform = "rotate(" + (o.rot || 0) + "deg)";
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

  /* ---------------- object CRUD ---------------- */
  function addObject(type, extra) {
    const pg = curPage();
    if (!pg) { toast("ยังไม่มีหน้าเอกสาร", "err"); return null; }
    const z = maxZ(pg) + 1;
    const base = {
      id: uid(), type: type, key: "", text: "",
      x: 15, y: 10, w: 40, h: 6, rot: 0, fs: 16, lh: 1.05,
      align: "left", color: "#111111", fit: "contain",
      lockAspect: false, locked: false, z: z, src: ""
    };
    if (type === "image") { base.w = 25; base.h = 25; base.y = 35; }
    if (type === "qr") { base.w = 16; base.h = 16; base.y = 60; base.text = "https://example.com"; base.lockAspect = true; base.key = "qr"; }
    if (type === "text") { base.text = "ข้อความ"; }
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
    try { if (document.fonts && document.fonts.ready) await document.fonts.ready; } catch (e) {}
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
      v: 2,
      pages: state.pages,
      records: state.records,
      pageIdx: state.pageIdx,
      recIdx: state.recIdx
    };
    downloadJson(obj, "overlay_layout.json");
    toast("Export layout แล้ว", "ok");
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

  function buildExportHtml() {
    const bgs = state.pages.map((p) => (p.bg && p.bg.src ? p.bg.src : null));
    const ars = state.pages.map((p) => (p.bg ? p.bg.w / p.bg.h : A4.w / A4.h));
    const pageObjs = state.pages.map((p) => p.objects.map((o) => ({
      id: o.id, t: o.type,
      x: round(o.x, 3), y: round(o.y, 3), w: round(o.w, 3), h: round(o.h, 3),
      r: o.rot || 0, k: o.key || "", tx: o.text || "", al: o.align || "left",
      c: o.color || "#111111", fs: o.fs || 16, lh: o.lh || 1.05,
      fit: o.fit || "contain", z: o.z || 1,
      src: o.type === "image" && o.src ? o.src : ""
    })));
    const records = state.records.length ? state.records : [null];
    const qr = {};
    records.forEach((rec, ri) => {
      pageObjs.forEach((objs) => objs.forEach((o) => {
        if (o.t !== "qr") return;
        const val = (rec && o.k && rec[o.k]) ? String(rec[o.k]) : o.tx;
        const u = qrDataUrlFor(val);
        if (u) qr[ri + "|" + o.id] = u;
      }));
    });
    const payload = JSON.stringify({ bgs: bgs, ars: ars, pageObjs: pageObjs, records: records, qr: qr })
      .replace(/</g, "\\u003c"); // กัน record มี </script> ทำ HTML พัง

    return `<!DOCTYPE html>
<html lang="th">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>เทมเพลตเอกสาร</title>
<style>
@page{size:A4;margin:0}
*{box-sizing:border-box}
html,body{margin:0;background:#555;font-family:"THSarabunNew","Sarabun","Noto Sans Thai",system-ui,sans-serif}
.bar{position:sticky;top:0;background:#0071e3;color:#fff;padding:10px 14px;display:flex;gap:12px;align-items:center;font-size:14px;z-index:9}
.bar button{font:inherit;border:0;border-radius:8px;padding:8px 16px;background:#fff;color:#0071e3;font-weight:700;cursor:pointer}
#out{padding:14px}
.page{position:relative;width:794px;height:1123px;margin:0 auto 14px;background:#fff;overflow:hidden;box-shadow:0 4px 18px rgba(0,0,0,.4);page-break-after:always;break-after:page}
.page:last-child{page-break-after:auto;break-after:auto}
.page .bg{position:absolute;inset:0;width:100%;height:100%;object-fit:fill;display:block}
.o{position:absolute;display:flex;align-items:center;overflow:hidden;white-space:pre-wrap;word-break:break-word}
.o.al-center{justify-content:center;text-align:center}
.o.al-right{justify-content:flex-end;text-align:right}
.o img{max-width:100%;max-height:100%;display:block}
.o img.fit-contain{object-fit:contain}.o img.fit-cover{object-fit:cover}.o img.fit-fill{object-fit:fill}
@media print{body{background:#fff}.bar{display:none}#out{padding:0}.page{margin:0;box-shadow:none}}
</style>
</head>
<body>
<div class="bar"><b>เทมเพลตเอกสาร</b><span id="count"></span><span style="flex:1"></span><button onclick="window.print()">พิมพ์ / บันทึกเป็น PDF</button></div>
<div id="out"></div>
<script>
var D=${payload};
function render(){
  var out=document.getElementById("out");
  var frag=document.createDocumentFragment();
  var pages=0;
  D.records.forEach(function(rec,ri){
    D.pageObjs.forEach(function(objs,pi){
      var page=document.createElement("div");
      page.className="page";
      page.style.height=Math.round(794/(D.ars[pi]||(794/1123)))+"px";
      if(D.bgs[pi]){var bg=document.createElement("img");bg.className="bg";bg.src=D.bgs[pi];page.appendChild(bg);}
      objs.forEach(function(o){
        var el=document.createElement("div");
        el.className="o al-"+(o.al||"left");
        el.style.left=o.x+"%";el.style.top=o.y+"%";el.style.width=o.w+"%";el.style.height=o.h+"%";
        el.style.transform="rotate("+(o.r||0)+"deg)";
        el.style.color=o.c||"#111";el.style.zIndex=String(o.z||1);
        if(o.t==="text"){el.style.fontSize=(o.fs||16)+"px";el.style.lineHeight=String(o.lh||1.05);}
        if(o.t==="image"){
          if(o.src){var im=document.createElement("img");im.className="fit-"+(o.fit||"contain");im.src=o.src;el.appendChild(im);}
        }else if(o.t==="qr"){
          var q=D.qr[ri+"|"+o.id];
          if(q){var qi=document.createElement("img");qi.src=q;el.appendChild(qi);}
        }else{
          var val="";
          if(o.k){val=(rec&&rec[o.k]!=null&&String(rec[o.k])!=="")?String(rec[o.k]):"";}
          else{val=o.tx||"";}
          if(val){var sp=document.createElement("span");sp.textContent=val;el.appendChild(sp);}
        }
        page.appendChild(el);
      });
      frag.appendChild(page);pages++;
    });
  });
  out.appendChild(frag);
  var rc=D.records.filter(function(r){return r;}).length;
  document.getElementById("count").textContent=pages+" หน้า · "+rc+" records";
}
render();
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
  function saveDraft() {
    try {
      localStorage.setItem(DRAFT_KEY, JSON.stringify({
        pages: state.pages, records: state.records,
        pageIdx: state.pageIdx, recIdx: state.recIdx, time: Date.now()
      }));
    } catch (e) {
      try { // พื้นที่เต็ม — เซฟแบบไม่รวมพื้นหลัง
        const lite = JSON.parse(JSON.stringify({ pages: state.pages, records: state.records, pageIdx: state.pageIdx, recIdx: state.recIdx, time: Date.now() }));
        lite.pages.forEach((p) => { if (p.bg) p.bg = { kind: p.bg.kind, src: "", w: p.bg.w, h: p.bg.h, name: p.bg.name }; });
        localStorage.setItem(DRAFT_KEY, JSON.stringify(lite));
        toast("เซฟ draft (ไม่รวมภาพพื้นหลัง — พื้นที่เต็ม)");
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
      edRotation: (o, v) => { o.rot = clamp(parseFloat(v) || 0, -180, 180); }
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
    if (name === "history") renderHistory();
  }

  /* ---------------- boot ---------------- */
  function boot() {
    wire();
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
