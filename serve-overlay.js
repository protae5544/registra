// Static file server สำหรับ preview Overlay Composer (ใช้กับ freebuff-preview)
// usage: node serve-overlay.js [dir]  — port มาจาก env PORT (default 8080), bind 0.0.0.0
const http = require("http");
const fs = require("fs");
const path = require("path");

const root = path.resolve(process.cwd(), process.argv[2] || "app/src/main/assets/overlay");
const port = Number(process.env.PORT) || 8080;

const TYPES = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".jpeg": "image/jpeg",
  ".gif": "image/gif",
  ".svg": "image/svg+xml",
  ".webp": "image/webp",
  ".pdf": "application/pdf",
  ".woff2": "font/woff2"
};

const server = http.createServer((req, res) => {
  try {
    let urlPath = decodeURIComponent(req.url.split("?")[0]);
    if (urlPath === "/") urlPath = "/index.html";
    if (urlPath === "/index") urlPath = "/index.html";
    const filePath = path.normalize(path.join(root, urlPath));
    if (!filePath.startsWith(root)) {
      res.writeHead(403);
      return res.end("403");
    }
    fs.readFile(filePath, (err, data) => {
      if (err) {
        res.writeHead(404, { "Content-Type": "text/plain; charset=utf-8" });
        return res.end("404 not found: " + urlPath);
      }
      const type = TYPES[path.extname(filePath).toLowerCase()] || "application/octet-stream";
      res.writeHead(200, { "Content-Type": type, "Cache-Control": "no-store" });
      res.end(data);
    });
  } catch (e) {
    res.writeHead(500);
    res.end("500");
  }
});

server.listen(port, "0.0.0.0", () => {
  console.log("Overlay Composer preview on http://0.0.0.0:" + port + " (root: " + root + ")");
});
