package zone.vgs.tvsach

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cập nhật qua mạng từ cleantv.alexvgs.tech/cap-nhat.json:
 *  - dữ liệu (danh sách mẫu tivi, APK Home sạch): tải lặng lẽ vào bộ nhớ app, không cần cài lại;
 *  - bản app mới: tải APK rồi mở hộp cài của Android (người dùng bấm Cài đặt).
 */
object CapNhat {
    const val GOC = "https://cleantv.alexvgs.tech"

    data class BanMoi(val versionCode: Int, val versionName: String, val apk: String, val ghiChu: String)

    /** File dữ liệu đã tải về (nếu có) — DuLieu và launcher đọc file này trước assets. */
    fun fileDuLieu(ctx: Context, ten: String) = File(ctx.filesDir, "ota/$ten")

    /**
     * App vừa lên bản mới → dữ liệu OTA tải lúc trước có thể CŨ hơn đồ đóng sẵn trong bản mới (21/10/2026: launcher 0.3
     * tải qua OTA đè launcher có nút Máy chiếu của 0.5). Xoá đi, dùng đồ đóng sẵn cho tới lần tải kế.
     */
    fun donOtaCuNeuDoiBan(ctx: Context) {
        val p = ctx.getSharedPreferences("ota", Context.MODE_PRIVATE)
        if (p.getInt("cho_ban", -1) == BuildConfig.VERSION_CODE) return
        File(ctx.filesDir, "ota").deleteRecursively()
        p.edit().clear().putInt("cho_ban", BuildConfig.VERSION_CODE).apply()
    }

    fun moAsset(ctx: Context, ten: String): java.io.InputStream {
        val f = fileDuLieu(ctx, ten)
        return if (f.isFile && f.length() > 0) f.inputStream() else ctx.assets.open(ten)
    }

    private fun tai(url: String, giay: Int = 20): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = giay * 1000; c.readTimeout = giay * 1000
        c.setRequestProperty("Cache-Control", "no-cache")
        try {
            if (c.responseCode != 200) error("HTTP ${c.responseCode} $url")
            return c.inputStream.use { it.readBytes() }
        } finally { c.disconnect() }
    }

    /**
     * Đọc cap-nhat.json, kéo dữ liệu mới về nếu khác bản đang có.
     * Trả về bản app mới nếu có (versionCode cao hơn bản đang chạy).
     */
    fun kiemTra(ctx: Context): BanMoi? {
        val j = JSONObject(String(tai("$GOC/cap-nhat.json"), Charsets.UTF_8))
        val prefs = ctx.getSharedPreferences("ota", Context.MODE_PRIVATE)
        j.optJSONObject("duLieu")?.let { dl ->
            for (ten in dl.keys()) {
                val o = dl.getJSONObject(ten)
                // Dữ liệu do bản app CŨ HƠN phát hành thì bỏ: đồ đóng sẵn trong app mới hơn (22/10/2026: bản thử 1.7
                // tải lại launcher của 1.5 từ máy chủ rồi dùng nó thay launcher có âm lịch).
                if (o.optInt("tuBan", 0) < BuildConfig.VERSION_CODE) { fileDuLieu(ctx, ten).delete(); continue }
                val phienBan = o.getString("phienBan")
                if (prefs.getString(ten, "") == phienBan && fileDuLieu(ctx, ten).isFile) continue
                runCatching {
                    val bytes = tai(GOC + o.getString("url"), 60)
                    if (bytes.size.toLong() != o.optLong("co", bytes.size.toLong())) error("Sai cỡ $ten")
                    val f = fileDuLieu(ctx, ten).apply { parentFile?.mkdirs() }
                    val tam = File(f.path + ".tam")
                    tam.writeBytes(bytes)
                    if (!tam.renameTo(f)) error("Không ghi được $ten")
                    prefs.edit().putString(ten, phienBan).apply()
                }
            }
        }
        return banApp(j)
    }

    /** Chỉ đọc bản app mới (không kéo dữ liệu) — cho việc kiểm tra chạy nền. */
    fun kiemTraApp(): BanMoi? = banApp(JSONObject(String(tai("$GOC/cap-nhat.json"), Charsets.UTF_8)))

    private fun banApp(j: JSONObject): BanMoi? {
        val app = j.optJSONObject("app") ?: return null
        val code = app.optInt("versionCode")
        return if (code > BuildConfig.VERSION_CODE)
            BanMoi(code, app.optString("versionName"), app.getString("apk").let { if (it.startsWith("http")) it else GOC + it },
                t(app.optString("ghiChu"), app.optString("ghiChuEn").ifBlank { app.optString("ghiChu") }))
        else null
    }

    /** Người dùng chạm thông báo "có bản mới" để mở app → tải và cài luôn, khỏi hỏi lại. */
    @Volatile var moTuThongBao = false

    /** Tải APK bản mới vào cache rồi mở hộp cài. */
    fun taiVaCai(ctx: Context, ban: BanMoi) {
        val f = File(ctx.cacheDir, "cap-nhat/CleanTV-${ban.versionName}.apk").apply { parentFile?.mkdirs() }
        f.writeBytes(tai(ban.apk, 120))
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        ctx.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
