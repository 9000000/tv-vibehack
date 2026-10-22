// Bộ đếm ẩn danh của CleanTV (21/10/2026). Không lưu IP, email hay tên: app gửi một mã máy ngẫu nhiên tự sinh,
// lượt tải chỉ lưu băm IP+ngày để đếm số người tải khác nhau trong ngày.
const LOAI = new Set(["mo", "cap-nhat", "don", "home", "home-phim", "khoi-phuc", "may-chieu"]);
const cat = (s, n) => String(s ?? "").replace(/[\u0000-\u001f]/g, "").trim().slice(0, n);

async function bam(chu) {
  const d = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(chu));
  return [...new Uint8Array(d)].slice(0, 8).map(b => b.toString(16).padStart(2, "0")).join("");
}

async function ghi(env, r) {
  await env.DB.prepare(`INSERT INTO su_kien (luc, loai, may, ban, tivi, so, them, quoc_gia) VALUES (?, ?, ?, ?, ?, ?, ?, ?)`)
    .bind(new Date().toISOString(), r.loai, r.may || "", r.ban || "", r.tivi || "", r.so || 0, r.them || "", r.quoc_gia || "").run();
}

export default {
  async fetch(req, env, ctx) {
    const url = new URL(req.url);
    const qg = req.cf?.country || "";

    // Lượt tải APK: đếm rồi chuyển sang file trên R2.
    if (url.pathname === "/tai" || url.pathname.startsWith("/tai/")) {
      const ip = req.headers.get("cf-connecting-ip") || "";
      const ngay = new Date(Date.now() + 7 * 3600e3).toISOString().slice(0, 10);
      const may = await bam(`${ip}|${ngay}|${env.MUOI || "cleantv"}`);
      const nguon = cat(url.searchParams.get("tu"), 40);
      ctx.waitUntil(ghi(env, { loai: "tai", may, them: nguon, quoc_gia: qg }).catch(() => {}));
      return Response.redirect(env.APK_MOI, 302);
    }

    // Tín hiệu từ app.
    if (url.pathname === "/su-kien" && req.method === "POST") {
      const raw = await req.text();
      if (raw.length > 2000) return new Response("qua dai", { status: 413 });
      let b; try { b = JSON.parse(raw); } catch { return new Response("sai", { status: 400 }); }
      if (!LOAI.has(b.loai) || !/^[a-f0-9-]{8,40}$/i.test(b.may || "")) return new Response("sai", { status: 400 });
      ctx.waitUntil(ghi(env, {
        loai: b.loai, may: cat(b.may, 40), ban: cat(b.ban, 10), tivi: cat(b.tivi, 80),
        so: Math.max(0, Math.min(10000, Number(b.so) || 0)), them: cat(b.them, 120), quoc_gia: qg,
      }).catch(() => {}));
      return new Response("ok");
    }

    // Số liệu dạng JSON cho admin vgs.zone (tab CleanTV) — khoá đi bằng header, không nằm trong URL.
    if (url.pathname === "/so-lieu") {
      if (!env.XEM_KHOA || req.headers.get("x-khoa") !== env.XEM_KHOA) return new Response("Không có quyền", { status: 403 });
      const n = [7, 14, 30, 90].includes(Number(url.searchParams.get("ngay"))) ? Number(url.searchParams.get("ngay")) : 14;
      return Response.json(await soLieu(env, n), { headers: { "cache-control": "no-store" } });
    }

    if (url.pathname === "/thong-ke") {
      if (!env.XEM_KHOA || url.searchParams.get("khoa") !== env.XEM_KHOA) return new Response("Không có quyền", { status: 403 });
      return new Response(await trangThongKe(env), { headers: { "content-type": "text/html; charset=utf-8", "cache-control": "no-store" } });
    }
    return new Response("CleanTV", { status: 404 });
  },
};

// ---------- Trang thống kê (giờ VN) ----------
const VN = `datetime(luc, '+7 hours')`;
const html = s => String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;");

async function trangThongKe(env) {
  const q = (sql, ...a) => env.DB.prepare(sql).bind(...a).all().then(r => r.results);
  const mot = async (sql, ...a) => (await q(sql, ...a))[0] || {};
  const [tai, may, cn, don, home, banDs, tivi, ngay, qg] = await Promise.all([
    mot(`SELECT COUNT(*) tong, SUM(${VN} >= datetime('now','+7 hours','-7 days')) tuan, SUM(date(${VN}) = date('now','+7 hours')) nay, COUNT(DISTINCT may) nguoi FROM su_kien WHERE loai='tai'`),
    mot(`SELECT COUNT(DISTINCT may) tong, COUNT(DISTINCT CASE WHEN ${VN} >= datetime('now','+7 hours','-7 days') THEN may END) tuan, COUNT(DISTINCT CASE WHEN date(${VN}) = date('now','+7 hours') THEN may END) nay FROM su_kien WHERE loai='mo'`),
    mot(`SELECT COUNT(*) tong, COUNT(DISTINCT may) may FROM su_kien WHERE loai='cap-nhat'`),
    mot(`SELECT COUNT(*) tong, COUNT(DISTINCT tivi) tivi, SUM(so) goi FROM su_kien WHERE loai='don'`),
    mot(`SELECT COUNT(*) tong FROM su_kien WHERE loai='home'`),
    q(`SELECT ban, COUNT(*) n FROM (SELECT may, ban, MAX(luc) l FROM su_kien WHERE loai='mo' GROUP BY may) GROUP BY ban ORDER BY ban DESC`),
    q(`SELECT tivi, SUM(loai='don') don, SUM(loai='home') home, SUM(loai='khoi-phuc') kp, SUM(loai='may-chieu') mc FROM su_kien WHERE tivi != '' GROUP BY tivi ORDER BY don + home DESC LIMIT 40`),
    q(`SELECT date(${VN}) d, SUM(loai='tai') tai, COUNT(DISTINCT CASE WHEN loai='mo' THEN may END) mo, SUM(loai='don') don FROM su_kien WHERE ${VN} >= datetime('now','+7 hours','-14 days') GROUP BY d ORDER BY d`),
    q(`SELECT quoc_gia, COUNT(*) n FROM su_kien WHERE loai='tai' GROUP BY quoc_gia ORDER BY n DESC LIMIT 8`),
  ]);
  const max = Math.max(1, ...ngay.map(r => Math.max(r.tai, r.mo, r.don)));
  const o = (nhan, so, phu) => `<div class="o"><b>${so ?? 0}</b><span>${nhan}</span>${phu ? `<small>${phu}</small>` : ""}</div>`;
  return `<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>CleanTV — thống kê</title>
<style>body{margin:0;background:#0b0b10;color:#f4f4f8;font:15px/1.5 system-ui,sans-serif;padding:20px;max-width:1000px;margin:auto}
h1{font-size:24px}h2{font-size:17px;margin-top:28px}.luoi{display:grid;grid-template-columns:repeat(auto-fill,minmax(170px,1fr));gap:12px}
.o{background:#15151c;border:1px solid #2a2a36;border-radius:16px;padding:16px}.o b{font-size:30px;display:block}.o span{color:#a1a1b5}.o small{display:block;color:#a1a1b5;margin-top:4px}
table{width:100%;border-collapse:collapse}td,th{padding:8px;border-bottom:1px solid #2a2a36;text-align:left}th{color:#a1a1b5;font-weight:500}
.cot{display:flex;gap:6px;align-items:flex-end;height:140px}.cot div{flex:1;display:flex;flex-direction:column;justify-content:flex-end;gap:2px;align-items:center;font-size:11px;color:#a1a1b5}
.cot i{display:block;width:100%;border-radius:4px}.t{background:#8b5cf6}.m{background:#ec4899}.d{background:#22c55e}</style>
<h1>📺 CleanTV — thống kê</h1>
<div class="luoi">
${o("lượt tải APK", tai.tong, `hôm nay ${tai.nay ?? 0} · 7 ngày ${tai.tuan ?? 0} · ${tai.nguoi ?? 0} người`)}
${o("máy đã mở app", may.tong, `hôm nay ${may.nay ?? 0} · 7 ngày ${may.tuan ?? 0}`)}
${o("lượt cập nhật app", cn.tong, `${cn.may ?? 0} máy`)}
${o("lần dọn tivi", don.tong, `${don.tivi ?? 0} mẫu tivi · ${don.goi ?? 0} gói đã tắt`)}
${o("lần cài Home sạch", home.tong)}
</div>
<h2>14 ngày qua <small style="color:#a1a1b5">(<span style="color:#8b5cf6">■</span> tải · <span style="color:#ec4899">■</span> máy mở app · <span style="color:#22c55e">■</span> dọn tivi)</small></h2>
<div class="cot">${ngay.map(r => `<div title="${r.d}: tải ${r.tai}, mở ${r.mo}, dọn ${r.don}"><i class="t" style="height:${r.tai / max * 110}px"></i><i class="m" style="height:${r.mo / max * 110}px"></i><i class="d" style="height:${r.don / max * 110}px"></i>${r.d.slice(8)}</div>`).join("")}</div>
<h2>Bản app đang dùng</h2><table><tr><th>Bản</th><th>Số máy</th></tr>${banDs.map(r => `<tr><td>${html(r.ban)}</td><td>${r.n}</td></tr>`).join("")}</table>
<h2>Tivi</h2><table><tr><th>Mẫu</th><th>Dọn</th><th>Home sạch</th><th>Khôi phục</th><th>Máy chiếu</th></tr>${tivi.map(r => `<tr><td>${html(r.tivi)}</td><td>${r.don}</td><td>${r.home}</td><td>${r.kp}</td><td>${r.mc}</td></tr>`).join("")}</table>
<h2>Nơi tải</h2><table>${qg.map(r => `<tr><td>${html(r.quoc_gia || "?")}</td><td>${r.n}</td></tr>`).join("")}</table>
<p style="color:#a1a1b5;font-size:13px">Ẩn danh: không lưu IP, email hay tên. Giờ Việt Nam.</p>`;
}

async function soLieu(env, n) {
  const q = (sql, ...a) => env.DB.prepare(sql).bind(...a).all().then(r => r.results);
  const mot = async (sql, ...a) => (await q(sql, ...a))[0] || {};
  const tu = `datetime('now','+7 hours','-${n} days')`;
  const truoc = `datetime('now','+7 hours','-${2 * n} days')`;
  // Mỗi chỉ số: cả đời · trong kỳ · kỳ liền trước (để so lên/xuống).
  const chiSo = (loai, dem = "COUNT(*)") => mot(`SELECT ${dem.replace("X", "1")} tong,
      ${dem.replace("COUNT(*)", `SUM(${VN} >= ${tu})`).replace("COUNT(DISTINCT may)", `COUNT(DISTINCT CASE WHEN ${VN} >= ${tu} THEN may END)`)} ky,
      ${dem.replace("COUNT(*)", `SUM(${VN} >= ${truoc} AND ${VN} < ${tu})`).replace("COUNT(DISTINCT may)", `COUNT(DISTINCT CASE WHEN ${VN} >= ${truoc} AND ${VN} < ${tu} THEN may END)`)} kyTruoc
      FROM su_kien WHERE loai='${loai}'`);
  const [tai, mo, capNhat, don, home, khoiPhuc, mayChieu, goi, ban, nguon, quocGia, tivi, ngay, ganDay] = await Promise.all([
    chiSo("tai"), chiSo("mo", "COUNT(DISTINCT may)"), chiSo("cap-nhat"), chiSo("don"), chiSo("home"), chiSo("khoi-phuc"), chiSo("may-chieu"),
    mot(`SELECT SUM(so) tong, COUNT(DISTINCT tivi) tivi FROM su_kien WHERE loai='don'`),
    q(`SELECT ban, COUNT(*) n FROM (SELECT may, ban, MAX(luc) l FROM su_kien WHERE loai='mo' GROUP BY may) GROUP BY ban ORDER BY n DESC`),
    q(`SELECT CASE WHEN them = '' THEN 'khác' ELSE them END nguon, COUNT(*) n FROM su_kien WHERE loai='tai' AND ${VN} >= ${tu} GROUP BY 1 ORDER BY n DESC LIMIT 8`),
    q(`SELECT CASE WHEN quoc_gia = '' THEN '?' ELSE quoc_gia END qg, COUNT(*) n FROM su_kien WHERE loai='tai' AND ${VN} >= ${tu} GROUP BY 1 ORDER BY n DESC LIMIT 8`),
    q(`SELECT tivi, SUM(loai='don') don, SUM(loai='home') home, SUM(loai='khoi-phuc') khoiPhuc, SUM(loai='may-chieu') mayChieu, MAX(${VN}) lanCuoi
       FROM su_kien WHERE tivi != '' GROUP BY tivi ORDER BY lanCuoi DESC LIMIT 50`),
    q(`SELECT date(${VN}) d, SUM(loai='tai') tai, COUNT(DISTINCT CASE WHEN loai='mo' THEN may END) mo, SUM(loai='don') don, SUM(loai='cap-nhat') capNhat
       FROM su_kien WHERE ${VN} >= ${tu} GROUP BY d ORDER BY d`),
    q(`SELECT ${VN} luc, loai, ban, tivi, so, quoc_gia qg FROM su_kien WHERE loai != 'mo' ORDER BY luc DESC LIMIT 20`),
  ]);
  return { ngay: n, chiSo: { tai, mo, capNhat, don, home, khoiPhuc, mayChieu }, goi, ban, nguon, quocGia, tivi, theoNgay: ngay, ganDay };
}
