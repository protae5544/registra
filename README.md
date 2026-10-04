# CHB Form — Overlay Composer (กรอกฟอร์มบน PDF จริง)

แอป Android ที่ใช้ **Overlay Composer** เป็นหน้าจอหลัก: โหลด PDF เป็นพื้นหลัง วาง/ย้าย/ลบฟิลด์ text·image·QR เอง กรอกข้อมูลหลายชุด แล้ว export เป็น PDF/PNG

## ทำไมเปลี่ยนแนว

ระบบเดิมที่ “ตรวจจับฟิลด์อัตโนมัติจาก PDF” ใช้งานจริงได้ยาก ผู้ใช้มองไม่เห็นตำแหน่งบนฟอร์ม และแก้/เพิ่มฟิลด์เองไม่ได้

แนวทางใหม่ยึด **ประสบการณ์แบบ WYSIWYG** ตามตัวอย่าง Overlay Composer:

1. เลือกไฟล์ PDF (หรือภาพ) เป็นพื้นหลัง
2. กด +Text / +Image / +QR แล้วลากวางบนฟอร์ม
3. ตั้ง Key ให้ตรงกับข้อมูล
4. กรอกข้อมูล (หรือโหลด JSON หลายชุด)
5. Export PDF นี้ / PDF ทั้งหมด / PNG

## โครงสร้าง

| ส่วน | รายละเอียด |
|------|------------|
| `MainActivity` | WebView โหลด `file:///android_asset/overlay/index.html` |
| `assets/overlay/` | Overlay Composer (bootstrap + payload) |
| `AndroidBridge` | บันทึกไฟล์จาก JS แล้วเปิดแชร์ |

## สิทธิ์ที่ต้องใช้

- `INTERNET` — โหลดไลบรารี (pdf.js, html2canvas, jspdf, qrcode, pako) และฟอนต์

## วิธี Build

```bash
./gradlew assembleDebug
```

## หมายเหตุสำหรับนักพัฒนา

หากต้องการแทนที่ UI ด้วยไฟล์ Overlay Composer ฉบับเต็มโดยตรง:

1. คัดลอกไฟล์ HTML ไปที่ `app/src/main/assets/overlay/index.html`
2. ลบไฟล์ `c0.js`…`c7.js` ที่ไม่ใช้แล้วได้
3. Build ใหม่
