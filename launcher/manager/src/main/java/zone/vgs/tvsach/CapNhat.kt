package zone.vgs.tvsach

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cập nhật qua mạng từ GitHub (9000000/tv-vibehack):
 *  - Dữ liệu (danh sách mẫu tivi, APK Home sạch): tải lặng lẽ vào bộ nhớ app, không cần cài lại;
 *  - Bản app mới: tải APK từ GitHub Releases của 9000000/tv-vibehack rồi mở hộp cài của Android.
 */
object CapNhat {
    const val GITHUB_OWNER = "9000000"
    const val GITHUB_REPO = "tv-vibehack"
    const val GITHUB_BRANCH = "app"

    const val GOC = "https://raw.githubusercontent.com/$GITHUB_OWNER/$GITHUB_REPO/$GITHUB_BRANCH"
    const val GOC_PHU = "https://raw.githubusercontent.com/$GITHUB_OWNER/$GITHUB_REPO/main"
    const val GITHUB_API_LATEST = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    data class BanMoi(val versionCode: Int, val versionName: String, val apk: String, val ghiChu: String)

    /** File dữ liệu đã tải về (nếu có) — DuLieu và launcher đọc file này trước assets. */
    fun fileDuLieu(ctx: Context, ten: String) = File(ctx.filesDir, "ota/$ten")

    /**
     * App vừa lên bản mới → dữ liệu OTA tải lúc trước có thể CŨ hơn đồ đóng sẵn trong bản mới.
     * Xoá đi, dùng đồ đóng sẵn cho tới lần tải kế.
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

    private fun tai(url: String, giay: Int = 30): ByteArray {
        var currentUrl = url
        for (i in 0 until 5) {
            val c = URL(currentUrl).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = true
            c.connectTimeout = giay * 1000
            c.readTimeout = giay * 1000
            c.setRequestProperty("Cache-Control", "no-cache")
            c.setRequestProperty("User-Agent", "CleanYourTV-App")
            c.setRequestProperty("Accept", "application/vnd.github+json, application/octet-stream, */*")
            try {
                val code = c.responseCode
                if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP || code == 307 || code == 308) {
                    val loc = c.getHeaderField("Location")
                    if (!loc.isNullOrBlank()) {
                        currentUrl = loc
                        c.disconnect()
                        continue
                    }
                }
                if (code != 200) error("HTTP $code $currentUrl")
                return c.inputStream.use { it.readBytes() }
            } finally {
                c.disconnect()
            }
        }
        error("Quá nhiều lần chuyển hướng: $url")
    }

    private fun soSanhPhienBan(v1: String, v2: String): Int {
        val p1 = v1.trim().removePrefix("v").removePrefix("V").split(".", "-").mapNotNull { it.toIntOrNull() }
        val p2 = v2.trim().removePrefix("v").removePrefix("V").split(".", "-").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(p1.size, p2.size)
        for (i in 0 until maxLen) {
            val num1 = p1.getOrElse(i) { 0 }
            val num2 = p2.getOrElse(i) { 0 }
            if (num1 != num2) return num1.compareTo(num2)
        }
        return 0
    }

    private fun layJsonCapNhat(): JSONObject? {
        val t = System.currentTimeMillis()
        var jsonTuRepo: JSONObject? = null
        // 1. Thử lấy cap-nhat.json từ nhánh app (kèm timestamp chống CDN cache)
        runCatching {
            jsonTuRepo = JSONObject(String(tai("$GOC/cap-nhat.json?t=$t", 15), Charsets.UTF_8))
        }
        // 2. Thử lấy cap-nhat.json từ nhánh main nếu nhánh app lỗi
        if (jsonTuRepo == null) {
            runCatching {
                jsonTuRepo = JSONObject(String(tai("$GOC_PHU/cap-nhat.json?t=$t", 15), Charsets.UTF_8))
            }
        }
        // 3. Dự phòng & Đối chiếu: Truy vấn trực tiếp GitHub Releases API mới nhất của repo
        var jsonTuRelease: JSONObject? = null
        runCatching {
            val releaseObj = JSONObject(String(tai(GITHUB_API_LATEST, 15), Charsets.UTF_8))
            val tag = releaseObj.optString("tag_name", "").removePrefix("v").removePrefix("V")
            val body = releaseObj.optString("body", "")
            val assets = releaseObj.optJSONArray("assets") ?: return@runCatching

            var managerApkUrl = ""
            var managerSize = 0L
            var launcherApkUrl = ""
            var launcherSize = 0L

            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                val dlUrl = asset.optString("browser_download_url", "")
                val size = asset.optLong("size", 0L)
                if (name.endsWith(".apk", ignoreCase = true)) {
                    if (name.contains("Manager", ignoreCase = true) || managerApkUrl.isBlank()) {
                        managerApkUrl = dlUrl
                        managerSize = size
                    } else if (name.contains("Launcher", ignoreCase = true) || name.contains("tvhome", ignoreCase = true)) {
                        launcherApkUrl = dlUrl
                        launcherSize = size
                    }
                }
            }

            if (managerApkUrl.isNotBlank()) {
                val isNewer = soSanhPhienBan(tag, BuildConfig.VERSION_NAME) > 0
                val targetCode = if (isNewer) BuildConfig.VERSION_CODE + 1 else BuildConfig.VERSION_CODE

                val syntheticJson = JSONObject()
                val appJson = JSONObject()
                    .put("versionCode", targetCode)
                    .put("versionName", tag)
                    .put("apk", managerApkUrl)
                    .put("co", managerSize)
                    .put("ghiChu", body)
                syntheticJson.put("app", appJson)

                if (launcherApkUrl.isNotBlank()) {
                    val duLieu = JSONObject()
                    duLieu.put("tvhome.apk", JSONObject()
                        .put("phienBan", tag)
                        .put("url", launcherApkUrl)
                        .put("co", launcherSize)
                        .put("tuBan", targetCode))
                    syntheticJson.put("duLieu", duLieu)
                }
                jsonTuRelease = syntheticJson
            }
        }

        // Chọn nguồn có phiên bản app mới hơn hoặc gộp duLieu
        val repoApp = jsonTuRepo?.optJSONObject("app")
        val relApp = jsonTuRelease?.optJSONObject("app")
        if (jsonTuRepo != null && jsonTuRelease != null) {
            val repoVer = repoApp?.optString("versionName", "") ?: ""
            val relVer = relApp?.optString("versionName", "") ?: ""
            val repoCode = repoApp?.optInt("versionCode", 0) ?: 0
            val relCode = relApp?.optInt("versionCode", 0) ?: 0
            if (relCode > repoCode || soSanhPhienBan(relVer, repoVer) > 0) {
                // Release mới hơn cap-nhat.json (do cache CDN), dùng app từ Release nhưng giữ duLieu từ repo
                if (!jsonTuRelease.has("duLieu") && jsonTuRepo.has("duLieu")) {
                    jsonTuRelease.put("duLieu", jsonTuRepo.optJSONObject("duLieu"))
                }
                return jsonTuRelease
            }
            return jsonTuRepo
        }
        return jsonTuRepo ?: jsonTuRelease
    }

    /**
     * Đọc cap-nhat.json từ GitHub, kéo dữ liệu mới về nếu khác bản đang có.
     * Trả về bản app mới nếu có (versionCode cao hơn hoặc versionName mới hơn).
     */
    fun kiemTra(ctx: Context): BanMoi? {
        val j = layJsonCapNhat() ?: return null
        val prefs = ctx.getSharedPreferences("ota", Context.MODE_PRIVATE)
        j.optJSONObject("duLieu")?.let { dl ->
            for (ten in dl.keys()) {
                val o = dl.getJSONObject(ten)
                if (o.optInt("tuBan", 0) < BuildConfig.VERSION_CODE) { fileDuLieu(ctx, ten).delete(); continue }
                val phienBan = o.getString("phienBan")
                if (prefs.getString(ten, "") == phienBan && fileDuLieu(ctx, ten).isFile) continue
                runCatching {
                    val rawUrl = o.getString("url")
                    val taiUrl = if (rawUrl.startsWith("http")) rawUrl else "$GOC/$rawUrl".replace("//", "/").replace(":/", "://")
                    val bytes = tai(taiUrl, 60)
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

    /** Chỉ đọc bản app mới từ GitHub (không kéo dữ liệu) — cho việc kiểm tra chạy nền. */
    fun kiemTraApp(): BanMoi? = layJsonCapNhat()?.let { banApp(it) }

    private fun banApp(j: JSONObject): BanMoi? {
        val app = j.optJSONObject("app") ?: return null
        val code = app.optInt("versionCode")
        val name = app.optString("versionName")
        val isNewer = code > BuildConfig.VERSION_CODE || (code == BuildConfig.VERSION_CODE && soSanhPhienBan(name, BuildConfig.VERSION_NAME) > 0)
        return if (isNewer) {
            val rawApk = app.getString("apk")
            val apkUrl = if (rawApk.startsWith("http")) rawApk else "$GOC/$rawApk".replace("//", "/").replace(":/", "://")
            BanMoi(code, name, apkUrl, t(app.optString("ghiChu"), app.optString("ghiChuEn").ifBlank { app.optString("ghiChu") }))
        } else null
    }

    /** Người dùng chạm thông báo "có bản mới" để mở app → tải và cài luôn, khỏi hỏi lại. */
    @Volatile var moTuThongBao = false

    /** Tải APK bản mới từ GitHub vào cache rồi mở hộp cài Android. */
    fun taiVaCai(ctx: Context, ban: BanMoi) {
        val safeName = ban.versionName.ifBlank { "new" }
        val f = File(ctx.cacheDir, "cap-nhat/CleanYourTV-$safeName.apk").apply { parentFile?.mkdirs() }
        f.writeBytes(tai(ban.apk, 180))
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        ctx.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
