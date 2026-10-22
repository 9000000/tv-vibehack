package zone.vgs.tvsach.dulieu

import android.content.Context
import org.json.JSONObject

/** Một gói trong danh sách mẫu của cleanyourtv. */
data class GoiMau(val pkg: String, val nhom: String, val ghiChu: String)

/** Một mẫu tivi đã có người thử (examples/<id>/debloat.txt). */
data class MauTv(val id: String, val ten: String, val header: String, val goi: List<GoiMau>)

/** Thống kê toàn cục: gói này bị tắt ở bao nhiêu mẫu, bao nhiêu mẫu ghi là phải giữ. */
data class ThongKeGoi(val soMauTat: Int, val soMauGiu: Int)

class DuLieu(val mau: List<MauTv>, val thongKe: Map<String, ThongKeGoi>) {

    /** Ghi chú đầu tiên có người viết cho mỗi gói (không có thì lấy tên nhóm). */
    val ghiChu: Map<String, String> by lazy {
        val m = HashMap<String, String>()
        for (t in mau) for (g in t.goi) if (g.ghiChu.isNotBlank()) m.putIfAbsent(g.pkg, g.ghiChu)
        for (t in mau) for (g in t.goi) if (g.nhom.isNotBlank()) m.putIfAbsent(g.pkg, g.nhom)
        m
    }

    companion object {
        @Volatile private var cache: DuLieu? = null

        fun nap(context: Context): DuLieu = cache ?: synchronized(this) {
            cache ?: docJson(zone.vgs.tvsach.CapNhat.moAsset(context, "du-lieu.json").bufferedReader().readText()).also { cache = it }
        }

        fun docJson(text: String): DuLieu {
            val root = JSONObject(text)
            val arr = root.getJSONArray("mau")
            val mau = (0 until arr.length()).map { i ->
                val m = arr.getJSONObject(i)
                val g = m.getJSONArray("goi")
                MauTv(
                    id = m.getString("id"),
                    ten = m.getString("ten"),
                    header = m.getString("header"),
                    goi = (0 until g.length()).map { j ->
                        val o = g.getJSONObject(j)
                        GoiMau(o.getString("p"), o.optString("g"), o.optString("n"))
                    },
                )
            }
            val goi = root.getJSONObject("goi")
            val tk = HashMap<String, ThongKeGoi>()
            goi.keys().forEach { k ->
                val o = goi.getJSONObject(k)
                tk[k] = ThongKeGoi(o.optInt("c"), o.optInt("giu"))
            }
            return DuLieu(mau, tk)
        }
    }

    /**
     * Tìm mẫu giống tivi này nhất bằng cách dò tên máy (ro.product.*) trong phần đầu danh sách.
     * Trả về tối đa 6 mẫu có điểm, cao trước.
     */
    fun khopMau(tv: ThongTinTv): List<Pair<MauTv, Int>> {
        val tieuChi = listOf(
            tv.device to 4, tv.productName to 4, tv.buildProduct to 2, tv.model to 3,
        ).filter { (v, _) -> v.length >= 3 && v.lowercase() !in TU_CHUNG }
        val hang = tv.brand.lowercase()
        return mau.map { m ->
            val dau = m.header.lines().take(8).joinToString("\n").lowercase()
            var diem = 0
            val daDung = HashSet<String>()
            for ((v, w) in tieuChi) {
                val key = v.lowercase()
                if (key in daDung) continue
                if (Regex("(?<![a-z0-9_])" + Regex.escape(key) + "(?![a-z0-9_])").containsMatchIn(dau)) {
                    diem += w
                    daDung += key
                }
            }
            if (diem > 0 && hang.isNotEmpty() && m.id.startsWith(hang.take(4))) diem += 1
            m to diem
        }.filter { it.second >= 3 }.sortedByDescending { it.second }.take(6)
    }
}

private val TU_CHUNG = setOf("smart tv", "android tv", "google tv", "tv", "atv", "mt5867", "mt9221", "full_", "generic")

/** Những gì đọc được từ getprop. */
data class ThongTinTv(
    val brand: String,
    val manufacturer: String,
    val model: String,
    val device: String,
    val productName: String,
    val buildProduct: String,
    val android: String,
    val sdk: Int,
    val serial: String,
    val adbSecure: String,
) {
    val tenHienThi: String get() = listOf(manufacturer.ifBlank { brand }, model).filter { it.isNotBlank() }.joinToString(" ")

    /** Khoá lưu bản sao lưu: số serial, không có thì ghép model + device. */
    val khoaLuu: String get() = (serial.ifBlank { "$model-$device" }).replace(Regex("[^A-Za-z0-9._-]"), "_")

    companion object {
        fun tuGetprop(text: String): ThongTinTv {
            val p = HashMap<String, String>()
            Regex("""\[([^\]]+)\]: \[([^\]]*)\]""").findAll(text).forEach { p[it.groupValues[1]] = it.groupValues[2] }
            fun g(vararg k: String) = k.firstNotNullOfOrNull { p[it]?.takeIf { v -> v.isNotBlank() } } ?: ""
            return ThongTinTv(
                brand = g("ro.product.brand", "ro.product.vendor.brand", "ro.product.system.brand"),
                manufacturer = g("ro.product.manufacturer", "ro.product.vendor.manufacturer"),
                model = g("ro.product.model", "ro.product.vendor.model"),
                device = g("ro.product.device", "ro.product.vendor.device"),
                productName = g("ro.product.name", "ro.product.vendor.name"),
                buildProduct = g("ro.build.product"),
                android = g("ro.build.version.release"),
                sdk = g("ro.build.version.sdk").toIntOrNull() ?: 0,
                serial = g("ro.serialno", "ro.boot.serialno"),
                adbSecure = g("ro.adb.secure"),
            )
        }
    }
}

/** Tên dễ đọc cho các app hay gặp (shell không lấy được nhãn app). */
val TEN_APP = mapOf(
    "com.netflix.ninja" to "Netflix",
    "com.netflix.mediaclient" to "Netflix",
    "com.google.android.youtube.tv" to "YouTube",
    "com.google.android.youtube.tvkids" to "YouTube Kids",
    "com.google.android.youtube.tvmusic" to "YouTube Music",
    "com.google.android.videos" to "Google TV / Play Phim",
    "com.google.android.play.games" to "Google Play Games",
    "com.amazon.amazonvideo.livingroom" to "Prime Video",
    "com.disney.disneyplus" to "Disney+",
    "com.spotify.tv.android" to "Spotify",
    "com.apple.atve.androidtv.appletv" to "Apple TV",
    "com.fptplay.tv" to "FPT Play",
    "net.fptplay.ottbox" to "FPT Play",
    "com.vieon.tv" to "VieON",
    "vn.vieon.tv" to "VieON",
    "com.viettel.tv360" to "TV360",
    "com.viettel.tv360.tv" to "TV360",
    "com.vnpt.mytv" to "MyTV",
    "vn.vnpt.mytv.atv" to "MyTV",
    "com.galaxyplay.tv" to "Galaxy Play",
    "com.fptplay.galaxyplay" to "Galaxy Play",
    "com.vtvcab.on" to "VTVcab ON",
    "vn.vtv.vtvgo" to "VTVgo",
    "com.kplus.tv" to "K+",
    "com.clip.tv" to "Clip TV",
    "org.xbmc.kodi" to "Kodi",
    "com.google.android.apps.mediashell" to "Chromecast (truyền từ điện thoại)",
    "com.google.android.katniss" to "Trợ lý Google (giọng nói)",
    "com.google.android.apps.tv.dreamx" to "Màn hình chờ Google",
    "com.google.android.backdrop" to "Màn hình chờ ảnh nền",
    "com.android.vending" to "CH Play",
    "com.google.android.tvlauncher" to "Màn hình Home gốc (Android TV)",
    "com.google.android.apps.tv.launcherx" to "Màn hình Home gốc (Google TV)",
    "com.gathin.tvhome" to "Home sạch (CleanYourTV)",
    "com.ts.launcher" to "Home sạch (bản cho Xiaomi)",
)

private val TEN_APP_EN = mapOf(
    "com.google.android.videos" to "Google TV / Play Movies",
    "com.google.android.apps.mediashell" to "Chromecast (cast from phone)",
    "com.google.android.katniss" to "Google Assistant (voice)",
    "com.google.android.apps.tv.dreamx" to "Google screensaver",
    "com.google.android.backdrop" to "Backdrop screensaver",
    "com.android.vending" to "Play Store",
    "com.google.android.tvlauncher" to "Stock Home (Android TV)",
    "com.google.android.apps.tv.launcherx" to "Stock Home (Google TV)",
    "com.gathin.tvhome" to "Clean Home (CleanYourTV)",
    "com.ts.launcher" to "Clean Home (Xiaomi build)",
)

fun tenDep(pkg: String): String = (if (!zone.vgs.tvsach.NgonNgu.tiengViet) TEN_APP_EN[pkg] else null) ?: TEN_APP[pkg] ?: pkg.substringAfterLast('.')
    .replace('_', ' ').replaceFirstChar { it.uppercase() }
