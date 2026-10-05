// smoke test: โหลด index.html จริงใน jsdom แล้วจำลองการใช้งานหลัก
const fs = require("fs");
const path = require("path");
const { JSDOM } = require("jsdom");

const dir = path.join(__dirname, "app/src/main/assets/overlay");
const html = fs.readFileSync(path.join(dir, "index.html"), "utf8");

const dom = new JSDOM(html, {
  runScripts: "outside-only",
  url: "http://localhost/",
  pretendToBeVisual: true
});
const { window } = dom;
const { document } = window;

// stubs สำหรับไลบรารี CDN ที่ไม่ได้โหลดใน test
window.html2canvas = () => Promise.resolve({
  width: 794, height: 1123,
  toDataURL: () => "data:image/png;base64,AAAA"
});
window.jspdf = { jsPDF: function () { this.addPage = () => {}; this.addImage = () => {}; this.save = () => {}; this.output = () => "data:application/pdf;base64,AAAA"; } };
window.QRCode = function (el) { const c = document.createElement("canvas"); el.appendChild(c); };
window.QRCode.CorrectLevel = { M: 0 };
window.pdfjsLib = undefined;
window.requestAnimationFrame = (cb) => setTimeout(cb, 0);
// stub Image ให้ fire load ทันที — เพื่อทดสอบการโหลดภาพพื้นหลังใน jsdom (ไม่มี image decoder)
window.Image = function () {
  const self = this;
  Object.defineProperty(self, "src", {
    set() { setTimeout(() => { self.naturalWidth = 1000; self.naturalHeight = 1400; if (self.onload) self.onload(); }, 0); },
    get() { return ""; }
  });
};
// jsdom ไม่มี URL.createObjectURL / canvas.getContext — stub ให้เทสต์ครอบคลุม export จนจบ
window.URL.createObjectURL = () => "blob:fake";
window.URL.revokeObjectURL = () => {};
const fakeCtx = { drawImage() {}, fillRect() {}, fillStyle: "", save() {}, restore() {} };
window.HTMLCanvasElement.prototype.getContext = function () { return fakeCtx; };

// โหลด app.js
const appJs = fs.readFileSync(path.join(dir, "app.js"), "utf8");
window.eval(appJs);

const $ = (id) => document.getElementById(id);
let pass = 0, fail = 0;
function check(name, cond) {
  if (cond) { pass++; console.log("PASS  " + name); }
  else { fail++; console.log("FAIL  " + name); }
}
function click(el) {
  el.dispatchEvent(new window.MouseEvent("click", { bubbles: true }));
}
// รอให้เงื่อนไขเป็นจริง (นำเข้าไฟล์เป็น async เพราะอ่าน file.text())
// กดยืนยันในกล่องยืนยัน (การกระทำที่ย้อนกลับไม่ได้ ต้องถามก่อน)
async function confirmYes() {
  const open = $("confirmWrap").classList.contains("show");
  click($("confirmOk"));
  await new Promise((r) => setTimeout(r, 30));
  return open;
}
async function waitFor(cond, ms) {
  const limit = ms || 2000;
  const t0 = Date.now();
  while (Date.now() - t0 < limit) {
    if (cond()) return true;
    await new Promise((r) => setTimeout(r, 15));
  }
  return false;
}
const sumRecords = () => parseInt($("sumRecords").textContent, 10);

async function run() {
  await new Promise((r) => setTimeout(r, 50));

  // 1) boot
  check("หน้าโหลดและมี canvas", !!$("canvasWrap"));
  check("มี page อย่างน้อย 1 หน้า", document.querySelectorAll(".page").length === 1);
  check("status ขึ้นข้อความพร้อม", $("opStatus").textContent.includes("พร้อม"));

  // 2) เพิ่ม text field
  click(document.querySelector('[data-add="text"]'));
  await new Promise((r) => setTimeout(r, 20));
  check("+Text เพิ่ม object บน canvas", document.querySelectorAll(".obj").length === 1);
  check("เลือก object ใหม่อัตโนมัติ", $("editor").style.display === "block");
  check("sumObjects = 1", $("sumObjects").textContent === "1");

  // 3) ปรับตำแหน่งด้วยตัวเลข (free position) + ตั้ง key ตอนยังเลือก text อยู่
  $("edX").value = "33.5";
  $("edX").dispatchEvent(new window.Event("input", { bubbles: true }));
  $("edY").value = "44.2";
  $("edY").dispatchEvent(new window.Event("input", { bubbles: true }));
  await new Promise((r) => setTimeout(r, 20));
  const sel = document.querySelector(".obj.sel");
  check("กรอก X/Y ย้าย object จริง", sel && sel.style.left === "33.5%" && sel.style.top === "44.2%");

  $("edKey").value = "name";
  $("edKey").dispatchEvent(new window.Event("change", { bubbles: true }));
  await new Promise((r) => setTimeout(r, 20));
  check("sumFields = 1 หลังตั้ง key", $("sumFields").textContent === "1");

  // 4) เพิ่ม QR
  click(document.querySelector('[data-add="qr"]'));
  await new Promise((r) => setTimeout(r, 20));
  check("+QR เพิ่ม object", document.querySelectorAll(".obj").length === 2);

  // 5) Duplicate + Delete
  click($("btnDup"));
  await new Promise((r) => setTimeout(r, 20));
  check("Duplicate เพิ่มเป็น 3 objects", $("sumObjects").textContent === "3");
  click($("btnDel"));
  await new Promise((r) => setTimeout(r, 20));
  check("กดลบชิ้นงานแล้วขึ้นกล่องยืนยันก่อน", $("confirmWrap").classList.contains("show"));
  await confirmYes();
  check("ยืนยันแล้วชิ้นงานถูกลบ", $("sumObjects").textContent === "2");
  check("กล่องยืนยันปิดหลังกดยืนยัน", !$("confirmWrap").classList.contains("show"));
  check("Delete ลดเหลือ 2 objects", $("sumObjects").textContent === "2");

  // 6) เพิ่ม/ลบหน้า (แสดงได้ทุกหน้า)
  click($("btnAddPage"));
  await new Promise((r) => setTimeout(r, 20));
  check("เพิ่มหน้า = 2 หน้า", document.querySelectorAll(".page").length === 2);
  check("pageCounter = 2/2", $("pageCounter").textContent === "2/2");
  click($("btnPgPrev"));
  await new Promise((r) => setTimeout(r, 20));
  check("ย้อนกลับหน้า 1", $("pageCounter").textContent === "1/2");
  click($("btnPgNext"));
  await new Promise((r) => setTimeout(r, 20));
  check("ไปหน้า 2 อีกครั้ง", $("pageCounter").textContent === "2/2");
  click($("btnDelPage")); // ลบหน้าว่าง (หน้าปัจจุบัน)
  await new Promise((r) => setTimeout(r, 20));
  check("กดลบหน้าแล้วขึ้นกล่องยืนยัน", $("confirmWrap").classList.contains("show"));
  await confirmYes();
  check("ลบหน้า = 1 หน้า", document.querySelectorAll(".page").length === 1);
  check("object บนหน้าแรกยังอยู่หลังลบหน้า", $("sumObjects").textContent === "2");

  // 8) view all pages
  click($("btnViewAll"));
  await new Promise((r) => setTimeout(r, 20));
  check("โหมดทุกหน้าเปิด", $("canvasWrap").classList.contains("all"));
  click($("btnViewAll"));
  await new Promise((r) => setTimeout(r, 20));
  check("โหมดทุกหน้าปิด", !$("canvasWrap").classList.contains("all"));

  // 9) โหลด records จาก paste JSON
  $("pasteJson").value = JSON.stringify([{ name: "สมชาย", id: "A001" }, { name: "สมหญิง", id: "A002" }]);
  click($("btnPasteLoad2"));
  await new Promise((r) => setTimeout(r, 20));
  check("โหลด 2 records", $("sumRecords").textContent === "2");
  check("record counter = 1/2", $("recordCounter").textContent === "1/2");

  // 10) nav records
  click($("btnNext"));
  await new Promise((r) => setTimeout(r, 20));
  check("record ถัดไป = 2/2", $("recordCounter").textContent === "2/2");
  check("Current record แสดงค่า", $("recordPreview").textContent.includes("สมหญิง"));

  // 11) ข้อความ object แสดงค่าจาก record (key = name)
  click($("btnPrev"));
  await new Promise((r) => setTimeout(r, 50));
  const textEl = document.querySelector('.obj[data-type="text"] .content');
  check("text object ดึงค่า record (สมชาย)", textEl && textEl.textContent.includes("สมชาย"));

  // 12) Fields tab list แสดงฟิลด์และลบได้
  click(document.querySelector('[data-tab="fields"]'));
  await new Promise((r) => setTimeout(r, 20));
  check("Fields list มี 2 รายการ", $("schemaList").querySelectorAll(".item").length === 2);
  const delBtn = Array.from($("schemaList").querySelectorAll("button")).find((b) => b.textContent === "ลบ");
  click(delBtn);
  await new Promise((r) => setTimeout(r, 20));
  check("ลบจาก Fields list ได้", $("sumObjects").textContent === "1");

  // 13) เพิ่ม field จาก Fields tab
  click($("btnAddField"));
  await new Promise((r) => setTimeout(r, 20));
  check("+ เพิ่มฟิลด์ใหม่ ได้", $("sumObjects").textContent === "2");

  // 14) Fill tab: เพิ่ม record ตัวอย่าง
  click(document.querySelector('[data-tab="fill"]'));
  await new Promise((r) => setTimeout(r, 20));
  click($("fillSample"));
  await new Promise((r) => setTimeout(r, 20));
  check("+ Record ตัวอย่าง เพิ่ม records", $("sumRecords").textContent === "3");
  click($("fillDel"));
  await new Promise((r) => setTimeout(r, 20));
  check("กดลบชุดข้อมูลแล้วขึ้นกล่องยืนยัน", $("confirmWrap").classList.contains("show"));
  await confirmYes();
  check("ลบ record ได้", $("sumRecords").textContent === "2");

  // 15) Export keys / layout ไม่ throw
  let ok = true;
  try {
    const origCreate = document.createElement.bind(document);
    // จับ a.download click ไม่ให้ error ใน jsdom
    window.HTMLAnchorElement.prototype.click = function () {};
    click($("btnExportKeys"));
    click($("btnExportLayout"));
  } catch (e) { ok = false; console.log(e); }
  check("Export Keys + Layout ไม่ error", ok);

  // 16) PNG export ผ่าน stub html2canvas
  ok = true;
  try { await click($("btnPNG")); await new Promise((r) => setTimeout(r, 300)); }
  catch (e) { ok = false; console.log(e); }
  check("Export PNG ไม่ error", ok);

  // 17) โหลดภาพตัวอย่างเป็นพื้นหลัง (ข้อกำหนด: ดูภาพเป็นตัวอย่าง)
  let bgOk = true, bgErr = null;
  try {
    const file = new window.File([new Uint8Array([137, 80, 78, 71])], "แบบฟอร์ม.png", { type: "image/png" });
    const input = $("bgFile");
    Object.defineProperty(input, "files", { value: [file], configurable: true });
    input.dispatchEvent(new window.Event("change", { bubbles: true }));
    await new Promise((r) => setTimeout(r, 300));
  } catch (e) { bgOk = false; bgErr = e; }
  if (bgErr) console.log("bg error:", bgErr.message);
  check("โหลดภาพเป็นพื้นหลังสำเร็จ", bgOk && !!document.querySelector(".page .bg"));
  check("bgStatus แจ้งโหลดแล้ว", $("bgStatus").textContent.includes("โหลดแล้ว"));
  check("object ยังอยู่บนหน้าที่มีพื้นหลัง", $("sumObjects").textContent === "2");
  click($("bgClear"));
  await new Promise((r) => setTimeout(r, 20));
  check("Clear BG ลบพื้นหลัง", !document.querySelector(".page .bg"));

  // 18) เพิ่มหน้าว่าง แล้ว text ไม่หาย
  const before = $("sumObjects").textContent;
  click($("btnAddPage"));
  await new Promise((r) => setTimeout(r, 20));
  click($("btnPgPrev"));
  await new Promise((r) => setTimeout(r, 20));
  check("กลับมาหน้าแรก object ยังอยู่", $("sumObjects").textContent === before);

  // 19) Export HTML สำหรับสร้าง PDF + bridge ของ Android (ChbAndroid.saveBase64)
  const saved = [];
  window.ChbAndroid = {
    saveBase64: (n, b64, m) => { saved.push({ n: n, b64: b64, m: m }); return "ok"; }
  };
  click($("btnExportHtml"));
  await new Promise((r) => setTimeout(r, 400));
  check("Export HTML เรียก bridge 1 ครั้ง", saved.length === 1);
  let htmlOut = "";
  if (saved[0]) {
    check("HTML: ชื่อไฟล์ .html + mime text/html", saved[0].n.endsWith(".html") && saved[0].m.startsWith("text/html"));
    htmlOut = Buffer.from(saved[0].b64, "base64").toString("utf8");
  }
  check("HTML: ขึ้นต้นด้วย DOCTYPE", htmlOut.startsWith("<!DOCTYPE html>"));
  check("HTML: มีค่า record (สมชาย) ฝังอยู่", htmlOut.includes("สมชาย"));
  check("HTML: มี script render + ปุ่มพิมพ์/บันทึก PDF", htmlOut.includes("pageObjs") && htmlOut.includes("window.print()"));

  // 20) PNG + PDF ผ่าน bridge (ในแอป WebView anchor download ใช้ไม่ได้)
  saved.length = 0;
  click($("btnPDFone"));
  await new Promise((r) => setTimeout(r, 400));
  check("PDF export ผ่าน bridge (mime application/pdf)",
    saved.length === 1 && saved[0].n.endsWith(".pdf") && saved[0].m === "application/pdf");

  saved.length = 0;
  click($("btnPNG"));
  await new Promise((r) => setTimeout(r, 400));
  check("PNG export ผ่าน bridge (mime image/png)",
    saved.length === 1 && saved[0].n.endsWith(".png") && saved[0].m === "image/png");
  delete window.ChbAndroid;

  // 21) นำเข้าข้อมูลหลายชุดใน session เดียว (เลือกได้หลายไฟล์)
  const beforeImport = parseInt($("sumRecords").textContent, 10);
  check("มี input jsonFile2 รองรับหลายไฟล์", $("jsonFile2").hasAttribute("multiple"));
  check("มีปุ่มนำเข้าในแท็บ Fill", !!$("fillImportJson") && !!$("fillCount"));
  const setFiles = (input, files) => {
    Object.defineProperty(input, "files", { value: files, configurable: true });
    input.dispatchEvent(new window.Event("change", { bubbles: true }));
  };
  const batch1 = new window.File([JSON.stringify([
    { name: "บุญชัย", id: "B001" }, { name: "บุญชัย2", id: "B002" }
  ])], "ชุดที่1.json", { type: "application/json" });
  const batch2 = new window.File([JSON.stringify([
    { name: "ศิริพร", id: "C001" }, { name: "มนัสนันท์", id: "C002" }, { name: "วิภา", id: "C003" }
  ])], "ชุดที่2.json", { type: "application/json" });
  setFiles($("jsonFile2"), [batch1, batch2]);
  await waitFor(() => sumRecords() === beforeImport + 5);
  check("รวมข้อมูล 2 ไฟล์เข้ากับของเดิม (2+2+3)", sumRecords() === beforeImport + 5);
  check("record counter ชี้ที่ชุดล่าสุด",
    $("recordCounter").textContent === "5/7" || $("recordCounter").textContent === "5/" + (beforeImport + 5));
  check("fillCount สรุปจำนวนชุดใน session",
    $("fillCount").textContent.includes(String(beforeImport + 5)));
  check("jsonStatus บอกจำนวนชุดรวม",
    $("jsonStatus").textContent.includes("รวมใน session"));

  // 22) ไฟล์เสียถูกข้าม ไม่ทำให้ของที่โหลดแล้วหาย
  const broken = new window.File(["{ ไม่ใช่ JSON"], "เสีย.json", { type: "application/json" });
  setFiles($("jsonFile2"), [broken]);
  await new Promise((r) => setTimeout(r, 200));
  check("ไฟล์ JSON ผิดไม่ทำให้ชุดข้อมูลที่โหลดแล้วหาย", sumRecords() === beforeImport + 5);

  // 23) รูปแบบ {records:[...]} ก็รับได้
  const wrapped = new window.File([JSON.stringify({ records: [{ name: "ฟอร์แมต" }] })],
    "wrapped.json", { type: "application/json" });
  setFiles($("jsonFile2"), [wrapped]);
  await waitFor(() => sumRecords() === beforeImport + 6);
  check("รองรับ JSON รูปแบบ {records:[...]}", sumRecords() === beforeImport + 6);

  // 23b) ทุกชุดที่นำเข้าใน session ต้องไหลไปถึงไฟล์ผลลัพธ์จริง
  const savedBatches = [];
  window.ChbAndroid = { saveBase64: (n, b64) => { savedBatches.push({ n: n, b64: b64 }); return "ok"; } };
  click($("btnExportHtml"));
  await new Promise((r) => setTimeout(r, 500));
  const batchHtml = savedBatches.length
    ? Buffer.from(savedBatches[0].b64, "base64").toString("utf8")
    : "";
  check("Export HTML มีข้อมูลครบทุกชุดที่นำเข้า",
    ["บุญชัย", "วิภา", "ฟอร์แมต"].every((nm) => batchHtml.includes(nm)));
  check("Export HTML ระบุจำนวน records ทั้งหมด",
    batchHtml.includes(String(beforeImport + 6)));
  delete window.ChbAndroid;

  // 24) toolbar jsonFile = โหลดแทนทั้งหมด (replace) ไม่ใช่ต่อท้าย
  setFiles($("jsonFile"), [new window.File([JSON.stringify([{ name: "คนเดียว" }])], "replace.json", { type: "application/json" })]);
  await waitFor(() => sumRecords() === 1);
  check("แถบ JSON บนสุดโหลดแทนทั้งหมด", $("sumRecords").textContent === "1");

  // ---------- ตัวช่วยสำหรับฟีเจอร์ใหม่ ----------
  const ev = (el, type, init) => el.dispatchEvent(new window.MouseEvent(type, Object.assign({ bubbles: true }, init || {})));
  const wev = (type, init) => window.dispatchEvent(new window.MouseEvent(type, Object.assign({ bubbles: true }, init || {})));
  const norm = (s) => String(s || "").replace(/\s+/g, "");
  // ผ่าน CSSOM ของเบราว์เซอร์เหมือนกันทั้งสองฝั่ง แล้วเทียบทีละตัวอักษร
  const sameCss = (a, b) => {
    const probe = document.createElement("div");
    probe.style.cssText = a;
    return probe.style.cssText === b;
  };
  const payloadOf = (html) => JSON.parse(html.slice(html.indexOf("var D=") + 6, html.indexOf(";\nfunction pad2")));
  const saveAs = (arr) => { window.ChbAndroid = { saveBase64: (n, b64) => { arr.push({ n: n, b64: b64 }); return "ok"; } }; };
  const dropBridge = () => { delete window.ChbAndroid; };

  // 25) แนบไฟล์ฟอนต์หลายไฟล์ + จับคู่ตัวหนาเข้าครอบครัวเดียวกัน
  setFiles($("fontFile"), [
    new window.File(["AAA"], "THSarabunNew.woff2", { type: "font/woff2" }),
    new window.File(["BBB"], "THSarabunNew+Bold.ttf", { type: "font/ttf" })
  ]);
  await waitFor(() => $("fontList").querySelectorAll(".fontRow").length === 2);
  const frows = Array.from($("fontList").querySelectorAll(".fontRow"));
  check("แนบฟอนต์ได้หลายไฟล์พร้อมกัน", frows.length === 2);
  check("fontStatus สรุปจำนวนไฟล์ที่แนบ", $("fontStatus").textContent.includes("2 ไฟล์"));
  check("ไฟล์ธรรมดาและไฟล์ตัวหนาอยู่ครอบครัวฟอนต์เดียวกัน",
    frows.every((r) => r.querySelector(".fname").textContent.includes("THSarabunNew")));
  check("ไฟล์ Bold ถูกจัดเป็นตัวหนาของครอบครัว", frows[1].querySelector(".fname").textContent.includes("ตัวหนา"));
  check("ตัวเลือกฟอนต์ใน Object มองเห็นว่าครอบครัวนี้มีตัวหนา",
    Array.from($("edFamily").options).some((o) => o.textContent.includes("มีตัวหนา")));
  check("มี @font-face ถูกฉีดลงหน้าเว็บตอนแนบฟอนต์",
    /@font-face\{font-family:"THSarabunNew";src:url\(data:font\/woff2;base64,/.test(
      ($("userFontCss") ? $("userFontCss").textContent : "")));

  // 26) ใช้ฟอนต์ที่แนบ + ตัวหนา แล้วพรีวิวต้องเป็นฟอนต์นั้นจริง
  const objEl = document.querySelector(".page .obj");
  ev(objEl, "pointerdown", { clientX: 100, clientY: 100 });
  wev("pointerup", { clientX: 100, clientY: 100 });
  $("edFamily").value = "THSarabunNew";
  $("edFamily").dispatchEvent(new window.Event("change", { bubbles: true }));
  $("edBold").checked = true;
  $("edBold").dispatchEvent(new window.Event("change", { bubbles: true }));
  const contentEl = () => document.querySelector(".page .obj .content");
  check("พรีวิวใช้ฟอนต์ที่ผู้ใช้แนบ", contentEl().style.fontFamily.includes("THSarabunNew"));
  check("พรีวิวใช้ตัวหนาเมื่อฟอนต์ที่แนบมีตัวหนาจริง", contentEl().style.fontWeight === "700");
  check("หมายเหตุบอกว่าฟอนต์นี้มีตัวหนา", $("edBoldNote").textContent.includes("มีตัวหนา"));

  // 27) พรีวิว กับ ไฟล์ export ต้องตรงกัน 100% (CSS ชุดเดียวกัน)
  let saved26 = [];
  saveAs(saved26);
  click($("btnExportHtml"));
  await waitFor(() => saved26.length === 1);
  const html26 = Buffer.from(saved26[0].b64, "base64").toString("utf8");
  const D26 = payloadOf(html26);
  const objNow = document.querySelector(".page .obj");
  check("export ฝังฟอนต์ของผู้ใช้เป็น data URL", /@font-face\{font-family:"THSarabunNew";src:url\(data:font\/woff2;base64,/.test(html26));
  check("export ฝังตัวหนาของครอบครัวด้วย", html26.includes('font-weight:700;font-style:normal'));
  check("CSS ของเนื้อหาในไฟล์ export ตรงกับพรีวิวทุกตัวอักษร",
    sameCss(D26.pageObjs[0][0].oc, contentEl().style.cssText));
  check("กล่อง/ตำแหน่ง/ขนาดในไฟล์ export ตรงกับพรีวิวทุกตัวอักษร",
    sameCss(D26.pageObjs[0][0].box, objNow.style.cssText));
  check("ไฟล์ export ใช้ฟอนต์เดียวกับพรีวิว (font-weight/ตัวอักษร/แนวตั้ง)",
    D26.pageObjs[0][0].oc.indexOf("font-weight:700") >= 0 && D26.pageObjs[0][0].oc.indexOf("font-family:") >= 0);
  dropBridge();

  // 27b) เปิดไฟล์ export จริงแล้วเรนเดอร์ต้องได้ผลเดียวกับพรีวิวทุกตัวอักษร
  const domOut = new JSDOM(html26, { runScripts: "dangerously", url: "http://localhost/" });
  await waitFor(() => domOut.window.document.querySelectorAll("#out .page .o").length > 0);
  const oEls = domOut.window.document.querySelectorAll("#out .page .o");
  check("เปิดไฟล์ export แล้วเรนเดอร์หน้าได้จริง", oEls.length === D26.pageObjs[0].length);
  const pvBox = document.querySelector(".page .obj").style.cssText;
  const pvContent = document.querySelector(".page .obj .content").style.cssText;
  check("ผลลัพธ์ของไฟล์ export ตรงกับพรีวิว (กล่อง+ฟอนต์+ขนาด+ตำแหน่ง)",
    !!oEls[0] && sameCss(oEls[0].style.cssText, pvBox)
    && sameCss(oEls[0].querySelector(".oc").style.cssText, pvContent));
  check("ไฟล์ export ฝังฟอนต์ใน <style> จริง (ไม่พึ่งอินเทอร์เน็ต)",
    /@font-face\{font-family:"THSarabunNew";src:url\(data:font\//.test(
      domOut.window.document.querySelector("style").textContent));

  // 28) ปรับหลายฟิลด์พร้อมกันด้วยการติ้กเลือก
  click(document.querySelector('.tab[data-tab="fields"]'));
  click($("btnAddField"));
  const itemRows = $("schemaList").querySelectorAll(".item");
  check("ทุกฟิลด์มีช่องติ้กสำหรับเลือกหลายฟิลด์", itemRows.length >= 2 && !!itemRows[0].querySelector('input[type="checkbox"]'));
  const picks = $("schemaList").querySelectorAll('.item input[type="checkbox"]');
  picks[0].checked = true;
  picks[0].dispatchEvent(new window.Event("change", { bubbles: true }));
  picks[1].checked = true;
  picks[1].dispatchEvent(new window.Event("change", { bubbles: true }));
  check("นับจำนวนฟิลด์ที่ติ้กได้", $("batchCount").textContent === "2");
  click($("batchSelectAll"));
  check("ปุ่มติ้กทั้งหมดเลือกทุกฟิลด์",
    parseInt($("batchCount").textContent, 10) === itemRows.length && itemRows.length > 1);
  $("bOnFs").checked = true; $("bFs").value = "28";
  $("bOnFamily").checked = true; $("bFamily").value = "THSarabunNew";
  $("bOnBold").checked = true; $("bBold").checked = true;
  $("bOnAlign").checked = true; $("bAlign").value = "center";
  $("bOnValign").checked = true; $("bValign").value = "top";
  $("bOnLetter").checked = true; $("bLetter").value = "1.5";
  $("bOnText").checked = true; $("bText").value = "ทดสอบ";
  click($("batchApply"));
  const contents = Array.from(document.querySelectorAll('.page .obj[data-type="text"] .content'));
  check("ปรับขนาดให้ทุกฟิลด์ที่ติ้กพร้อมกัน", contents.length > 1 && contents.every((c) => c.style.fontSize === "28px"));
  check("ปรับรูปแบบ (ฟอนต์/ตัวหนา/จัดแนว/แนวตั้ง/ตัวอักษร) ให้ทุกฟิลด์ที่ติ้ก",
    contents.every((c) => c.style.fontFamily.includes("THSarabunNew") && c.style.fontWeight === "700"
      && c.style.textAlign === "center" && c.style.alignItems === "flex-start" && c.style.letterSpacing === "1.5px"));
  check("ปรับเนื้อหาให้ทุกฟิลด์ที่ติ้ก", contents.every((c) => c.textContent === "ทดสอบ"));
  click($("batchClearSel"));
  check("ล้างการติ้กได้", $("batchCount").textContent === "0");

  // 29) เป้ากากบาท: แตะวางเป้า แล้วเลื่อนนิ้วจากตำแหน่งคนละที่
  const pageEl = document.querySelector(".page");
  pageEl.getBoundingClientRect = () => ({ left: 0, top: 0, width: 794, height: 1123, right: 794, bottom: 1123 });
  ev(document.querySelector(".page .obj"), "pointerdown", { clientX: 100, clientY: 100 });
  wev("pointerup", { clientX: 100, clientY: 100 });
  click($("btnCross"));
  check("เปิดโหมดเป้ากากบาท", $("btnCross").className === "cross-on" && pageEl.classList.contains("cross-on"));
  $("crossMode").value = "tap"; // โหมด absolute: แตะที่ไหนเป้าไปที่นั้น
  $("crossMode").dispatchEvent(new window.Event("change", { bubbles: true }));
  ev(pageEl, "pointerdown", { clientX: 200, clientY: 300 });
  wev("pointerup", { clientX: 200, clientY: 300 });
  const cm = pageEl.querySelector(".crossLayer .cm");
  const exX = 200 / 794 * 100, exY = 300 / 1123 * 100;
  check("โหมด absolute: แตะแล้วเป้ากากบาทอยู่ที่ตำแหน่งที่แตะ", !!cm && Math.abs(parseFloat(cm.style.left) - exX) < 0.05);
  check("object เดินไปตามเป้าที่วาง", Math.abs(parseFloat($("edX").value) - exX) < 0.2 && Math.abs(parseFloat($("edY").value) - exY) < 0.2);
  check("พิกัดรายงานเป็น %/mm/px ครบ", /X [\d.]+% . [\d.]+mm . [\d.]+px\s+Y [\d.]+% . [\d.]+mm . [\d.]+px/.test($("coordReadout").textContent));
  click($("btnSnap")); // ปิด snap เพื่อให้ตัวเลขทดสอบเป็นค่าตามที่เลื่อนจริง
  $("crossMode").value = "trackpad"; // โหมด trackpad ตามตัวอย่าง VNC: แตะไม่ย้ายเป้า
  $("crossMode").dispatchEvent(new window.Event("change", { bubbles: true }));
  const beforeTap = parseFloat($("edX").value);
  ev(pageEl, "pointerdown", { clientX: 60, clientY: 60 });
  wev("pointerup", { clientX: 60, clientY: 60 });
  check("โหมด trackpad: แตะไม่ย้ายเป้า (นิ้วไม่บังตำแหน่ง)", Math.abs(parseFloat($("edX").value) - beforeTap) < 0.001);
  // เลื่อนแบบสัมพัทธ์: เป้าขยับเท่ากับระยะที่นิ้วเลื่อนทุกเฟรม ไม่ใช่กระโดดไปหาตำแหน่งนิ้ว
  ev(pageEl, "pointerdown", { clientX: 600, clientY: 800 });
  wev("pointermove", { clientX: 620, clientY: 830 });
  wev("pointermove", { clientX: 640, clientY: 860 });
  wev("pointerup", { clientX: 640, clientY: 860 });
  const nx = exX + (640 - 600) / 794 * 100, ny = exY + (860 - 800) / 1123 * 100;
  check("โหมด trackpad: ลากนิ้วแล้วเป้าเดินตามระยะที่นิ้วเลื่อน (สะสมทีละเฟรม)",
    Math.abs(parseFloat($("edX").value) - nx) < 0.02 && Math.abs(parseFloat($("edY").value) - ny) < 0.02);
  check("นิ้วอยู่คนละตำแหน่งกับเป้าจริง (ไม่บังตำแหน่ง)", 640 / 794 * 100 - parseFloat($("edX").value) > 20);
  $("crossGain").value = "0.5";
  $("crossGain").dispatchEvent(new window.Event("change", { bubbles: true }));
  ev(pageEl, "pointerdown", { clientX: 100, clientY: 100 });
  wev("pointermove", { clientX: 140, clientY: 100 });
  wev("pointerup", { clientX: 140, clientY: 100 });
  check("ปรับความไวของการเลื่อนสัมพัทธ์ได้ (0.5 = ครึ่งเดิม)",
    Math.abs(parseFloat($("edX").value) - (nx + 40 / 794 * 100 * 0.5)) < 0.02);
  $("crossGain").value = "1";
  $("crossGain").dispatchEvent(new window.Event("change", { bubbles: true }));
  ev(pageEl, "pointerdown", { clientX: 100, clientY: 100 });
  wev("pointermove", { clientX: 140, clientY: 100 });
  wev("pointerup", { clientX: 140, clientY: 100 });
  const nx2 = parseFloat($("edX").value);
  click($("crossNudgeR"));
  check("ขยับละเอียด 0.1% ได้", Math.abs(parseFloat($("edX").value) - (nx2 + 0.1)) < 0.02);
  click($("btnCross"));
  check("ปิดโหมดเป้ากากบาท", $("btnCross").className === "cross-off");

  // 30) ลำดับการสั่งพิมพ์ + ตั้งชื่อไฟล์จากคีย์ที่เลือก
  setFiles($("jsonFile2"), [new window.File([JSON.stringify([
    { name: "ก", id: "1" }, { name: "ข", id: "2" }, { name: "ค", id: "3" }
  ])], "p.json", { type: "application/json" })]);
  await waitFor(() => sumRecords() === 4);
  click(document.querySelector('.tab[data-tab="print"]'));
  check("รายการสั่งพิมพ์มีครบทุกชุดข้อมูล", $("printList").querySelectorAll(".prow").length === 4);
  $("printNameKey").value = "name";
  $("printNameKey").dispatchEvent(new window.Event("change", { bubbles: true }));
  $("printNameTpl").value = "{n}_{key}";
  $("printNameTpl").dispatchEvent(new window.Event("input", { bubbles: true }));
  check("เลือกคีย์สำหรับตั้งชื่อได้", Array.from($("printNameKey").options).some((o) => o.value === "name"));
  check("ตั้งชื่อไฟล์ตามคีย์ที่เลือก", $("printPreview").textContent.includes("01_คนเดียว.html"));
  ev($("printList").querySelectorAll(".prow")[0].querySelector(".handle"), "pointerdown", { clientY: 0 });
  wev("pointermove", { clientY: 75 });
  wev("pointerup", { clientY: 75 });
  const rowAfterDrag = $("printList").querySelectorAll(".prow");
  check("ลากหูหมายจัดลำดับการพิมพ์ได้", rowAfterDrag[0].textContent.includes("ก") && rowAfterDrag[1].textContent.includes("คนเดียว"));
  const pcbs = $("printList").querySelectorAll('.prow input[type="checkbox"]');
  pcbs[1].checked = false;
  pcbs[1].dispatchEvent(new window.Event("change", { bubbles: true }));
  check("ถอดติ้กชุดที่ไม่ต้องพิมพ์ได้", $("printList").querySelectorAll(".prow")[1].className.indexOf("off") >= 0);

  let saved30 = [];
  saveAs(saved30);
  $("printScope").value = "current";
  $("printScope").dispatchEvent(new window.Event("change", { bubbles: true }));
  click($("printOneFile"));
  await waitFor(() => saved30.length === 1);
  const html30 = Buffer.from(saved30[0].b64, "base64").toString("utf8");
  const D30 = payloadOf(html30);
  check("ไฟล์พิมพ์เรียงลำดับชุดข้อมูลตามที่ลากจัด และข้ามชุดที่ถอดติ้ก",
    D30.recs.map((r) => r.name).join(",") === "ก,ข,ค");
  check("ขอบเขตหน้าที่พิมพ์เป็นหน้าปัจจุบันตามที่เลือก", D30.pageList.length === 1);
  check("ตั้งชื่อไฟล์พิมพ์ได้", /^01_ก-all-03\.html$/.test(saved30[0].n));
  check("ไฟล์พิมพ์ยังฝังฟอนต์ครบ", html30.indexOf("@font-face") >= 0);

  saved30 = [];
  saveAs(saved30);
  click($("printSplit"));
  await waitFor(() => saved30.length === 3);
  check("แยกไฟล์พิมพ์ได้ทีละชุดตามที่ติ้ก", saved30.length === 3);
  check("ชื่อไฟล์แยกเรียงตามลำดับที่จัด", saved30.map((s) => s.n).join(",") === "01_ก.html,02_ข.html,03_ค.html");
  dropBridge();

  // 31) stamp: element ตายตัวในเทมเพลต แต่คำนวณเป็นเวลาปัจจุบัน
  click(document.querySelector('.tab[data-tab="edit"]'));
  click(document.querySelector('[data-add="stamp"]'));
  await new Promise((r) => setTimeout(r, 20));
  const stampText = () => (document.querySelector('.obj[data-type="stamp"] .stampval') || {}).textContent || "";
  check("+Stamp เพิ่ม element ชนิด stamp", document.querySelectorAll('.obj[data-type="stamp"]').length === 1);
  check("แผงตั้งค่า stamp แสดงเฉพาะตอนเลือก object ชนิด stamp", $("stampbox").style.display === "block");
  check("stamp แสดงวันที่+เวลาปัจจุบัน", /^\d{2}\/\d{2}\/\d{4} \d{2}:\d{2}$/.test(stampText()));
  const setStamp = async (id, v) => {
    $(id).value = v;
    $(id).dispatchEvent(new window.Event("change", { bubbles: true }));
    await new Promise((r) => setTimeout(r, 10));
  };
  await setStamp("edStampPart", "date");
  check("เลือกแสดงเฉพาะวันที่", /^\d{2}\/\d{2}\/\d{4}$/.test(stampText()));
  await setStamp("edStampPart", "time");
  check("เลือกแสดงเฉพาะเวลา", /^\d{2}:\d{2}$/.test(stampText()));
  await setStamp("edStampPart", "month");
  check("เลือกแสดงเดือน+ปี", /^\d{2}\/\d{4}$/.test(stampText()));
  await setStamp("edStampPart", "date");
  await setStamp("edStampMode", "base");
  await setStamp("edStampBase", "2024-01-31");
  check("อ้างวันที่ตายตัวในเทมเพลตได้", stampText() === "31/01/2024" && $("stampPreview").textContent === "31/01/2024");
  await setStamp("edStampShift", "1");
  check("เลื่อน +1 เดือน (อนาคต) และครอบจำนวนวันให้ถูกต้อง", stampText() === "29/02/2024");
  await setStamp("edStampShift", "-2");
  check("เลื่อน -2 เดือน (อดีต)", stampText() === "30/11/2023");
  await setStamp("edStampMode", "now");
  check("กลับมาอิงเวลาปัจจุบันแล้วคำนวณใหม่ทันที", /^\d{2}\/\d{2}\/\d{4}$/.test(stampText()));
  await setStamp("edStampShift", "0");
  const nowD = new Date();
  const shiftTxt = (() => {
    const d = new Date(nowD.getFullYear(), nowD.getMonth() + 1, 1);
    d.setDate(Math.min(nowD.getDate(), new Date(d.getFullYear(), d.getMonth() + 1, 0).getDate()));
    return (d.getDate() < 10 ? "0" + d.getDate() : String(d.getDate())) + "/" + (d.getMonth() < 9 ? "0" : "") + (d.getMonth() + 1) + "/" + d.getFullYear();
  })();
  await setStamp("edStampShift", "1");
  check("นับจากเวลาปัจจุบัน +1 เดือน", stampText() === shiftTxt);

  // 32) ตัวเลือกสั่งพิมพ์ต้องอยู่ในไฟล์ template HTML จริง
  let saved32 = [];
  saveAs(saved32);
  $("printScope").value = "all";
  $("printScope").dispatchEvent(new window.Event("change", { bubbles: true }));
  click($("btnExportHtml"));
  await waitFor(() => saved32.length === 1);
  const html32 = Buffer.from(saved32[0].b64, "base64").toString("utf8");
  const D32 = payloadOf(html32);
  const stampInFile = D32.pageObjs.flat().find((o) => o.t === "stamp");
  check("ไฟล์ export เก็บค่าคงที่ของ stamp ไว้ครบ", !!stampInFile && stampInFile.sm
    && stampInFile.sm.mode === "now" && stampInFile.sm.shift === 1 && stampInFile.sm.part === "date");
  check("ไฟล์ export มีแถบเลือกรายการที่จะพิมพ์", html32.indexOf('id="pList"') >= 0 && html32.indexOf('id="pPrint"') >= 0
    && html32.indexOf('id="pEach"') >= 0 && html32.indexOf('id="nmtpl"') >= 0 && html32.indexOf('id="pAll"') >= 0);
  check("ไฟล์ export คำนวณ stamp ใหม่จากเวลาปัจจุบันตอนเปิด/พิมพ์", html32.indexOf("function stampText") >= 0);
  check("ไฟล์ export ตั้งชื่อไฟล์ตาม index ที่เลือกได้", html32.indexOf("function nameFor") >= 0
    && D32.prt && D32.prt.tpl === "{n}_{key}" && D32.prt.key === "name");

  const dom32 = new JSDOM(html32, { runScripts: "dangerously", url: "http://localhost/" });
  const w32 = dom32.window, d32 = w32.document;
  const printed32 = [];
  w32.print = () => printed32.push({ title: d32.title, pages: d32.querySelectorAll("#out .page").length });
  let alerted32 = 0;
  w32.alert = () => { alerted32++; };
  await waitFor(() => d32.querySelectorAll("#pList .prow").length > 0, 4000);
  const rows32 = Array.from(d32.querySelectorAll("#pList .prow"));
  const boxes32 = rows32.map((r) => r.querySelector("input"));
  check("แถบติ้กในไฟล์ export มีครบทุกชุดข้อมูล", rows32.length === D32.recs.length);
  check("ชุดที่ถอดติ้กในแอปถูกส่งมาเป็น “ไม่ติ้ก” ในไฟล์ด้วย",
    D32.prt.on.filter(Boolean).length === D32.prt.on.length - 1 && boxes32.filter((b) => !b.checked).length === 1);
  check("แถบติ้กแสดงชื่อไฟล์ตาม template", rows32[0].querySelector(".nm").textContent.length > 1
    && rows32[0].querySelector(".nm").textContent !== rows32[0].querySelector(".idx").textContent);
  check("ช่องตั้งชื่อไฟล์ถูกส่งมาพร้อม", d32.getElementById("nmtpl").value === "{n}_{key}");
  check("ปุ่มพิมพ์บอกจำนวนที่ติ้ก", d32.getElementById("pPrint").textContent.includes(String(boxes32.filter((b) => b.checked).length)));
  // กดพิมพ์ -> พิมพ์เฉพาะที่ติ้ก และตั้งชื่อไฟล์ตาม index ที่เลือก
  d32.getElementById("pPrint").dispatchEvent(new w32.MouseEvent("click", { bubbles: true }));
  const want32 = boxes32.filter((b) => b.checked).length * D32.pageList.length;
  check("กดพิมพ์แล้วพิมพ์เฉพาะชุดที่ติ้ก", printed32.length === 1 && printed32[0].pages === want32);
  check("ชื่อไฟล์ดาวน์โหลดถูกตั้งตามลำดับที่เลือกก่อนพิมพ์", /^01_\S+$/.test(printed32[0].title));
  // เอาติ้กทั้งหมดแล้วพิมพ์ -> ต้องเตือน ไม่ทำอะไร
  d32.getElementById("pNone").dispatchEvent(new w32.MouseEvent("click", { bubbles: true }));
  d32.getElementById("pPrint").dispatchEvent(new w32.MouseEvent("click", { bubbles: true }));
  check("ไม่ติ้กเลยแล้วสั่งพิมพ์ = เตือนให้ติ้กก่อน", alerted32 === 1 && printed32.length === 1);
  d32.getElementById("pAll").dispatchEvent(new w32.MouseEvent("click", { bubbles: true }));
  check("ปุ่มติ้กทั้งหมด/เอาติ้กทั้งหมดใช้ได้", boxes32.every((b) => b.checked));
  // พิมพ์ทีละชุด -> ได้ PDF แยก ชื่อตาม index
  boxes32[0].checked = false;
  boxes32[2].checked = false;
  d32.getElementById("pEach").dispatchEvent(new w32.MouseEvent("click", { bubbles: true }));
  await waitFor(() => printed32.length === 3, 6000);
  check("พิมพ์ทีละชุดได้ไฟล์แยกตามที่ติ้ก", printed32.length === 3 && printed32.slice(1).every((p) => p.pages === D32.pageList.length));
  check("ชื่อไฟล์แยกเรียงตาม index ที่ติ้กไว้", /^01_\S+$/.test(printed32[1].title) && /^02_\S+$/.test(printed32[2].title));
  // stamp ในไฟล์ที่เปิดจริงต้องเป็นเวลาปัจจุบัน ไม่ใช่ค่าตายตัว
  const stampNode = d32.querySelector('.o .stampval');
  check("stamp ในไฟล์ export แสดงเป็นเวลาปัจจุบันจริง", !!stampNode && stampNode.textContent === shiftTxt);
  dropBridge();

  // 33) เปลี่ยน object เดิมเป็น stamp ต้องได้ค่าเริ่มต้นที่คำนวณจากเวลาปัจจุบัน
  const firstObj = document.querySelector(".page .obj");
  ev(firstObj, "pointerdown", { clientX: 100, clientY: 100 });
  wev("pointerup", { clientX: 100, clientY: 100 });
  $("edType").value = "stamp";
  $("edType").dispatchEvent(new window.Event("change", { bubbles: true }));
  await new Promise((r) => setTimeout(r, 20));
  check("เปลี่ยน Type เป็น stamp แล้วเรนเดอร์เป็น stamp ทันที",
    !!document.querySelector('.obj[data-type="stamp"] .stampval')
    && /^\d{2}\/\d{2}\/\d{4} \d{2}:\d{2}$/.test(document.querySelector(".obj .stampval").textContent));
  check("แผง stamp เปิดเองพร้อมค่าเริ่มต้น และปิดช่องวันที่ตายตัวไว้",
    $("stampbox").style.display === "block" && $("edStampPart").value === "datetime"
    && $("edStampMode").value === "now" && $("edStampShift").value === "0" && $("edStampBase").disabled === true);
  await setStamp("edStampPart", "date");
  await setStamp("edStampMode", "base");
  await setStamp("edStampBase", "2024-01-31");
  await setStamp("edStampShift", "-3");
  check("stamp ที่ตั้งค่าแล้วคำนวณจากวันที่ตายตัวเลื่อนย้อนหลัง",
    document.querySelector(".obj .stampval").textContent === "31/10/2023");
  // PNG export ยังทำงาน และ layout ต้องเก็บค่าคงที่ + โหมดเคอเซอร์
  let saved33 = [];
  saveAs(saved33);
  click($("btnPNG"));
  await waitFor(() => saved33.length === 1, 4000);
  check("PNG export ยังทำงานได้เมื่อมี stamp อยู่บนหน้า", saved33.length === 1 && /\.png$/.test(saved33[0].n));
  saved33.length = 0; // ล้างในตัวเดิม — bridge ถืออ้างอิง array นี้อยู่
  click($("btnExportLayout"));
  await waitFor(() => saved33.length === 1, 5000);
  const layout33 = JSON.parse(Buffer.from(saved33[0].b64, "base64").toString("utf8"));
  const lo = layout33.pages[0].objects.find((o) => o.type === "stamp");
  check("export layout เก็บค่าคงที่ของ stamp และโหมดเคอเซอร์ครบ",
    !!lo && lo.stampPart === "date" && lo.stampMode === "base" && lo.stampBase === "2024-01-31"
    && lo.stampShift === -3 && layout33.crossMode === "trackpad" && layout33.crossGain === 1);
  dropBridge();

  // 34) กล่องยืนยัน: กดยกเลิก = ไม่เกิดการลบ
  click($("btnClearHist"));
  await new Promise((r) => setTimeout(r, 20));
  check("ล้างประวัติก็ต้องยืนยัน", $("confirmWrap").classList.contains("show"));
  click($("confirmCancel"));
  await new Promise((r) => setTimeout(r, 20));
  check("กดยกเลิกแล้วกล่องปิด", !$("confirmWrap").classList.contains("show"));

  // 35) ตั้งค่าการส่งออก: สวิตฝังฟอนต์มีผลกับไฟล์จริง
  const saved34 = [];
  window.ChbAndroid = { saveBase64: (n, b64) => { saved34.push({ n: n, b64: b64 }); return "ok"; } };
  const embedBox = $("expEmbedFonts");
  check("มีสวิตฝังฟอนต์ + ช่องเตือน + ตัวเลือกความละเอียด PNG",
    !!embedBox && !!$("expWarnFont") && !!$("expPngScale") && !!$("expNote"));
  check("ความละเอียด PNG มาตรฐาน = 2×", $("expPngScale").value === "2");

  saved34.length = 0;
  click($("btnExportHtml"));
  await waitFor(() => saved34.length === 1, 4000);
  const htmlOn = Buffer.from(saved34[0].b64, "base64").toString("utf8");
  check("เปิดฝังฟอนต์ -> ไฟล์ export มี @font-face", htmlOn.indexOf("@font-face") >= 0);

  embedBox.checked = false;
  embedBox.dispatchEvent(new window.Event("change", { bubbles: true }));
  await new Promise((r) => setTimeout(r, 20));
  check("ปิดฝังฟอนต์ -> ช่องบอกสถานะเปลี่ยนเป็นคำเตือน",
    $("expNote").className.indexOf("warn") >= 0 && $("expNote").textContent.indexOf("ปิดการฝังฟอนต์") >= 0);

  saved34.length = 0;
  click($("btnExportHtml"));
  await waitFor(() => saved34.length === 1, 4000);
  const htmlOff = Buffer.from(saved34[0].b64, "base64").toString("utf8");
  check("ปิดฝังฟอนต์ -> ไฟล์ export ไม่มี @font-face", htmlOff.indexOf("@font-face") < 0);

  embedBox.checked = true;
  embedBox.dispatchEvent(new window.Event("change", { bubbles: true }));
  await new Promise((r) => setTimeout(r, 20));
  saved34.length = 0;
  delete window.ChbAndroid;

  // 36) แผงจูนเป้ากากบาทยุบ/ขยายได้
  const panel = $("crossPanel"), pbtn = $("btnCrossPanel");
  check("แผงจูนเป้ากากบาทซ่อนเป็นค่าเริ่มต้น", !panel.classList.contains("open"));
  click(pbtn);
  await new Promise((r) => setTimeout(r, 20));
  check("กดปุ่มจูนแล้วแผงเปิด + ปุ่มบอกสถานะถูกต้อง",
    panel.classList.contains("open") && pbtn.getAttribute("aria-expanded") === "true");
  click(pbtn);
  await new Promise((r) => setTimeout(r, 20));
  check("กดซ้ำแล้วแผงปิด", !panel.classList.contains("open"));

  // 37) ซ่อนกลุ่มตั้งค่าที่ใช้กับชนิดนี้ไม่ได้
  $("edType").value = "qr";
  $("edType").dispatchEvent(new window.Event("change", { bubbles: true }));
  await new Promise((r) => setTimeout(r, 30));
  check("เลือกชนิด QR -> ซ่อนกลุ่มตัวอักษร", $("grpText").style.display === "none");
  check("เลือกชนิด QR -> ซ่อนกลุ่มรูปภาพ", $("grpImage").style.display === "none");
  $("edType").value = "text";
  $("edType").dispatchEvent(new window.Event("change", { bubbles: true }));
  await new Promise((r) => setTimeout(r, 30));
  check("เลือกชนิดข้อความ -> กลับมาแสดงกลุ่มตัวอักษร", $("grpText").style.display !== "none");
  check("เลือกชนิดข้อความ -> ยังซ่อนกลุ่มรูปภาพ", $("grpImage").style.display === "none");

  console.log("\n==== RESULT: " + pass + " passed, " + fail + " failed ====");
  process.exit(fail ? 1 : 0);
}

run().catch((e) => { console.error("FATAL", e); process.exit(1); });
