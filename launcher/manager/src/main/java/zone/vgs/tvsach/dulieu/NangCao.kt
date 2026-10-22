package zone.vgs.tvsach.dulieu

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Mục Nâng cao (góp ý github.com/vgscity/vgs-cleantv/issues/1, 22/10/2026): mọi tuỳ chọn của launcher mà app chưa có
 * công tắc riêng. Danh sách sinh từ upstream themes/README.md bằng scripts/lam-nang-cao.py → assets/nang-cao.json.
 * Giá trị giữ dạng chữ như người dùng gõ; "" = để launcher tự quyết (không ghi khoá).
 */
data class TuyChon(
    val khoa: String,
    val kieu: String,
    val min: Int,
    val max: Int,
    val chon: List<String>,
    val moTa: String,
    val canThan: Boolean,
)

object NangCao {
    val KHOA_DA_CO_SAN = setOf(
        "dock_apps", "greeting_name", "lunar_date", "always_show_names", "app_library", "library_button",
        "home_idle_clock_minutes", "reduced_motion", "dock_glass", "app_glow", "wallpaper_dim", "show_stats",
        "handle_view_inputs", "wallpaper", "race_calendar", "wallpaper_rotation_minutes",
        "columns", "rows_below_dock", "dock_row", "dock_title", "fill_dock", "reorder_button",
        "dock_band", "dock_opacity", "glow_style", "ambient_glow", "focus_overshoot", "animations",
        "advanced_effects", "cinematic_home", "wallpaper_true_color", "wallpaper_captions", "wallpaper_order",
        "show_network_pill", "show_inputs_pill", "module_cards", "show_home_settings_shortcut",
        "settings_key_panel", "boot_to_live_tv", "live_tv_package", "key_filter", "vpn_app", "dream_button",
        "top_compact", "weather_lat", "weather_lon", "weather_place", "projector_actions"
    )

    fun nap(ctx: Context): List<TuyChon> = runCatching {
        val a = JSONArray(ctx.assets.open("nang-cao.json").bufferedReader().readText().removePrefix("\uFEFF"))
        (0 until a.length()).mapNotNull { i ->
            val o = a.getJSONObject(i)
            val khoa = o.getString("khoa")
            if (khoa in KHOA_DA_CO_SAN) null
            else TuyChon(khoa, o.getString("kieu"), o.optInt("min", Int.MIN_VALUE), o.optInt("max", Int.MAX_VALUE),
                o.optJSONArray("chon")?.let { c -> (0 until c.length()).map { c.getString(it) } } ?: emptyList(),
                o.optString("moTa"), o.optBoolean("canThan"))
        }
    }.getOrDefault(emptyList())

    /** Chữ người dùng gõ → giá trị JSON; null = sai kiểu / bỏ trống (không ghi). */
    fun giaTri(tc: TuyChon, chu: String): Any? {
        val s = chu.trim()
        if (s.isEmpty()) return null
        return when (tc.kieu) {
            "bool" -> s.toBooleanStrictOrNull()
            "so" -> s.toIntOrNull()?.coerceIn(tc.min, tc.max)
            "ds" -> JSONArray(s.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() })
            "ds-so" -> JSONArray(s.split(',', '\n', ' ').mapNotNull { it.trim().toIntOrNull() })
            "chon" -> s.takeIf { it in tc.chon }
            else -> s
        }
    }

    /** Giá trị đang có trong config.json trên tivi → chữ để hiện lại. */
    fun docChu(tc: TuyChon, j: JSONObject): String? {
        if (!j.has(tc.khoa)) return null
        val v = j.get(tc.khoa)
        return if (v is JSONArray) (0 until v.length()).joinToString(", ") { v.get(it).toString() } else v.toString()
    }
}
