package zone.vgs.tvsach.dulieu

import org.json.JSONObject

/**
 * Giao diện cho Home sạch: các khoá config.json của launcher (upstream themes/README.md)
 * cộng một ảnh nền tuỳ chọn trong assets/theme/.
 */
data class Theme(
    val id: String,
    val tenVi: String,
    val tenEn: String,
    val moTaVi: String,
    val moTaEn: String,
    val cauHinh: Map<String, Any>,
    val anhNen: String? = null,
    val mau: List<Long>,
    /** Thư mục assets chứa BỘ ảnh nền xoay vòng (đẩy vào wallpapers/ của launcher); ảnh xem trước trong app. */
    val boAnh: String? = null,
    val anhXem: String? = null,
) {
    val ten: String get() = if (zone.vgs.tvsach.NgonNgu.tiengViet) tenVi else tenEn
    val moTa: String get() = if (zone.vgs.tvsach.NgonNgu.tiengViet) moTaVi else moTaEn

    fun ghiVao(o: JSONObject) {
        cauHinh.forEach { (k, v) -> o.put(k, v) }
        o.put("wallpaper", anhNen != null || boAnh != null)
    }
}

val THEMES = listOf(
    Theme("vgs", "VGS", "VGS", "Logo VGS tím, nền đổi sắc theo giờ", "Violet VGS logo, sky follows the time of day",
        mapOf("background_color" to "#140A3A", "sky_intensity" to 55, "dock_band" to false), "theme/vgs.jpg", listOf(0xFF0D0A38, 0xFF22168A, 0xFF4C1FD0)),
    Theme("aurora", "Cực quang", "Aurora", "Dải xanh ngọc và tím trên nền navy", "Teal and violet bands on navy",
        mapOf("background_color" to "#070A14", "dock_band" to false), "theme/aurora.jpg", listOf(0xFF0E4F5C, 0xFF3B1E6E)),
    Theme("bau-troi", "Bầu trời theo giờ", "Time-of-day sky", "Nền đổi màu theo giờ trong ngày", "Background follows the time of day",
        mapOf("sky_intensity" to 70, "dock_band" to false), null, listOf(0xFF1B2B5A, 0xFF5A3A4A)),
    Theme("dien-anh", "Điện ảnh", "Cinematic", "Ảnh app đang chọn phủ kín nửa trên", "The focused app's artwork fills the top",
        mapOf("cinematic_home" to true, "sky_intensity" to 35), null, listOf(0xFF4A0A0A, 0xFF101010)),
    Theme("glass", "Kính mờ (Glass)", "Glass", "Nền xanh navy, kệ điện ảnh, thanh dock kính mờ", "Dark navy with teal and violet light, cinematic shelf, glass dock",
        mapOf("background_color" to "#090C14", "cinematic_home" to true, "dock_glass" to true, "icon_only_buttons" to true, "app_glow" to true, "glow_style" to "horizon", "focus_overshoot" to false, "advanced_effects" to true),
        "theme/glass.jpg", listOf(0xFF090C14, 0xFF182A45, 0xFF351F52)),
    // Scenes của CleanYourTV (manas-04): 14 tranh phong cảnh tối vẽ bằng code, tự đổi mỗi giờ (22/10/2026).
    Theme("phong-canh", "Phong cảnh", "Scenes", "14 tranh phong cảnh, tự đổi mỗi giờ", "14 landscapes, a new one every hour",
        mapOf("wallpaper_rotation_minutes" to 60, "dock_band" to false, "background_color" to "#05060A"), null,
        listOf(0xFF1A2236, 0xFF4A3A5A), boAnh = "theme/phong-canh", anhXem = "theme/phong-canh/02-golden-coast.jpg"),
    Theme("toi-gian", "Tối giản", "Minimal", "Nền đen tuyền, kiểu Apple TV", "Pure black, Apple TV style",
        mapOf("background_color" to "#000000"), null, listOf(0xFF000000, 0xFF111111)),
    Theme("sau-cot", "Nhiều app", "More apps", "6 cột, thấy thêm một hàng app, tắt nền dock", "6 columns, one more row visible, transparent dock",
        mapOf("columns" to 6, "rows_below_dock" to 1, "background_color" to "#000000", "dock_band" to false), null, listOf(0xFF111111, 0xFF222233)),
    Theme("mac-dinh", "CleanTV Gốc", "CleanTV Default", "Giao diện gốc tối giản của tác giả CleanYourTV", "The built-in CleanYourTV look",
        mapOf("background_color" to "#050505"), null, listOf(0xFF050505, 0xFF1C1C1E)),
)

/** Thành phố cho dòng thời tiết trên Home sạch (weather_lat/lon/place của launcher). */
data class ThanhPho(val ten: String, val lat: Double, val lon: Double)

val THANH_PHO = listOf(
    ThanhPho("Hà Nội", 21.0285, 105.8542), ThanhPho("TP.HCM", 10.7769, 106.7009), ThanhPho("Đà Nẵng", 16.0544, 108.2022),
    ThanhPho("Hải Phòng", 20.8449, 106.6881), ThanhPho("Cần Thơ", 10.0452, 105.7469), ThanhPho("Vinh", 18.6796, 105.6813),
    ThanhPho("Huế", 16.4637, 107.5909), ThanhPho("Nha Trang", 12.2388, 109.1967), ThanhPho("Đà Lạt", 11.9404, 108.4583),
    ThanhPho("Quy Nhơn", 13.7820, 109.2192), ThanhPho("Buôn Ma Thuột", 12.6667, 108.0500), ThanhPho("Vũng Tàu", 10.3460, 107.0843),
    ThanhPho("Biên Hoà", 10.9574, 106.8426), ThanhPho("Thanh Hoá", 19.8067, 105.7852), ThanhPho("Nam Định", 20.4200, 106.1683),
    ThanhPho("Hạ Long", 20.9599, 107.0425), ThanhPho("Thái Nguyên", 21.5942, 105.8482), ThanhPho("Bắc Ninh", 21.1861, 106.0763),
)
