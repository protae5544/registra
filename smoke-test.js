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

  console.log("\n==== RESULT: " + pass + " passed, " + fail + " failed ====");
  process.exit(fail ? 1 : 0);
}

run().catch((e) => { console.error("FATAL", e); process.exit(1); });
