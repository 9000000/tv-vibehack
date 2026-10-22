package zone.vgs.tvsach.ui

import zone.vgs.tvsach.t
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.LinkProperties
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.muntashirakon.adb.AdbPairingRequiredException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import zone.vgs.tvsach.adb.KetNoiAdb
import zone.vgs.tvsach.adb.ThaoTacTv
import zone.vgs.tvsach.dulieu.BanSaoLuu
import zone.vgs.tvsach.dulieu.DuLieu
import zone.vgs.tvsach.dulieu.KeHoach
import zone.vgs.tvsach.dulieu.KhoSaoLuu
import zone.vgs.tvsach.dulieu.THANH_PHO
import zone.vgs.tvsach.dulieu.MauTv
import zone.vgs.tvsach.dulieu.MucGoi
import zone.vgs.tvsach.dulieu.ThongTinTv
import zone.vgs.tvsach.dulieu.TrangThaiGoi
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket

enum class Man { CHINH, KET_NOI, THONG_TIN, CHON_GOI, DANG_TAT, HOME_SACH, XONG, KHOI_PHUC, HUONG_DAN, GIAY_PHEP }

/** Số app tối đa trong dock: bằng số cột lớn nhất của Home sạch (columns 1–8); dock dài hơn số cột thì tràn xuống hàng sau. */
const val DOCK_TOI_DA = 8

class PhienLamViec(app: Application) : AndroidViewModel(app) {
    private val ctx: Context get() = getApplication()
    private val adb by lazy { KetNoiAdb.lay(ctx) }
    val kho by lazy { KhoSaoLuu(ctx) }
    private val tv by lazy { ThaoTacTv(adb, kho) }
    private val prefs by lazy { ctx.getSharedPreferences("tvsach", Context.MODE_PRIVATE) }

    var man by mutableStateOf(Man.CHINH)
    /** Màn để quay về sau khi xem Hướng dẫn / Giấy phép. */
    var manTruoc by mutableStateOf(Man.CHINH)

    fun moHuongDan() { if (man != Man.HUONG_DAN) manTruoc = man; man = Man.HUONG_DAN }
    fun moGiayPhep() { if (man != Man.GIAY_PHEP) manTruoc = man; man = Man.GIAY_PHEP }
    var dangBan by mutableStateOf(false)
    var thongBao by mutableStateOf("")
    var loi by mutableStateOf("")

    // Kết nối
    var ip by mutableStateOf("")
    var cong by mutableStateOf("5555")
    val timThay = mutableStateListOf<String>()
    var dangQuet by mutableStateOf(false)
    var ghepIpCong by mutableStateOf("")
    var ghepMa by mutableStateOf("")

    // Tivi
    var thongTin by mutableStateOf<ThongTinTv?>(null)
    private var getprop = ""
    var trangThai by mutableStateOf<TrangThaiGoi?>(null)
    var ungVien by mutableStateOf<List<Pair<MauTv, Int>>>(emptyList())
    var mauChon by mutableStateOf<MauTv?>(null)
    var saoLuu by mutableStateOf<BanSaoLuu?>(null)

    // Chọn gói
    var muc by mutableStateOf<List<MucGoi>>(emptyList())
    val chon = mutableStateMapOf<String, Boolean>()
    var epSetupwraith by mutableStateOf(false)

    // Kết quả
    val nhatKy = mutableStateListOf<String>()
    var ketQua by mutableStateOf<ThaoTacTv.KetQuaTat?>(null)
    var anhTv by mutableStateOf<Bitmap?>(null)

    // Home sạch
    val dock = mutableStateListOf<String>()
    var tenChao by mutableStateOf("")
    var nutInput by mutableStateOf(false)
    var nutHome by mutableStateOf(false)
    var dnsChanQc by mutableStateOf(false)
    var nutManCho by mutableStateOf(true)
    var gonNut by mutableStateOf(true)
    // Tuỳ chỉnh thêm (1.7, các "switch" của launcher — upstream themes/README.md).
    var amLich by mutableStateOf(true)
    var tenDuoiApp by mutableStateOf(false)
    var thuVienApp by mutableStateOf(false)
    var dongHoKhiYen by mutableStateOf(0)
    var tiviYeu by mutableStateOf(false)
    var hieuUngKinh by mutableStateOf(false)
    var lamToiNen by mutableStateOf(0)
    var hienThongSo by mutableStateOf(false)
    // Tiện ích mở rộng & Switches bổ sung (https://cleanyour.tv/addons)
    var soCot by mutableStateOf(0)
    var hangDuoiDock by mutableStateOf(-1)
    var doMoDock by mutableStateOf(100)
    var cheDoDienAnh by mutableStateOf(false)
    var kieuQuangSang by mutableStateOf("")
    var tuVaoTruyenHinh by mutableStateOf(false)
    var hinhNenTrueColor by mutableStateOf(false)
    var chuThichHinhNen by mutableStateOf(false)
    var thuTuHinhNen by mutableStateOf("shuffle")
    var bangCaiDatNhanh by mutableStateOf(false)
    var nutSapXep by mutableStateOf(false)
    // Toàn bộ các công tắc CleanYourTV bổ sung (https://cleanyour.tv/addons)
    var hienNutWifi by mutableStateOf(true) // show_network_pill
    var hienNutInputs by mutableStateOf(true) // show_inputs_pill
    var tieuDeDock by mutableStateOf(true) // dock_title
    var hangDockRieng by mutableStateOf(true) // dock_row
    var daiNenDock by mutableStateOf(false) // dock_band (tắt nền đen mặc định như theme VGS)
    var dienDayDock by mutableStateOf(true) // fill_dock
    var hieuUngLoXo by mutableStateOf(true) // focus_overshoot
    var chuyenDongCoBan by mutableStateOf(true) // animations
    var hieuUngNangCao by mutableStateOf(false) // advanced_effects
    var toaSangNenDock by mutableStateOf(true) // ambient_glow
    var toaSangIconApp by mutableStateOf(false) // app_glow
    var theModuleCards by mutableStateOf(true) // module_cards
    var nutHomeCaiDat by mutableStateOf(false) // show_home_settings_shortcut
    var boLocPhim by mutableStateOf(true) // key_filter
    var appTruyenHinh by mutableStateOf("") // live_tv_package
    var appVpn by mutableStateOf("") // vpn_app
    var cauHinhTuyY by mutableStateOf("") // JSON tuỳ biến mở rộng
    var homeXong by mutableStateOf(false)
    /** Lần thử nút Home thật gần nhất ra Home sạch (thuPhimHome). */
    var phimHomeOk by mutableStateOf(true)
    var themeChon by mutableStateOf(zone.vgs.tvsach.dulieu.THEMES.first())
    /** Mục Nâng cao: danh sách tuỳ chọn + giá trị người dùng đặt (khoá → chữ; thiếu = mặc định). */
    val dsNangCao: List<zone.vgs.tvsach.dulieu.TuyChon> by lazy { zone.vgs.tvsach.dulieu.NangCao.nap(ctx) }
    val nangCao = androidx.compose.runtime.mutableStateMapOf<String, String>()
    /** Màn mà nút Back ở Home sạch quay về: vừa dọn → màn dọn; Đổi giao diện → thông tin tivi; Bỏ qua → chọn gói. */
    var manTruocHome by mutableStateOf(Man.DANG_TAT)

    val hoSoTheoTheme = mutableMapOf<String, MutableMap<String, Any>>()

    fun taoHoSoMacDinh(th: zone.vgs.tvsach.dulieu.Theme): MutableMap<String, Any> {
        val band = (th.cauHinh["dock_band"] as? Boolean) ?: true
        val op = (th.cauHinh["dock_opacity"] as? Int) ?: if (!band) 0 else 100
        return mutableMapOf(
            "columns" to ((th.cauHinh["columns"] as? Int) ?: 4),
            "rows_below_dock" to ((th.cauHinh["rows_below_dock"] as? Int) ?: 0),
            "dock_band" to band,
            "dock_opacity" to op,
            "cinematic_home" to ((th.cauHinh["cinematic_home"] as? Boolean) ?: false),
            "glow_style" to ((th.cauHinh["glow_style"] as? String) ?: ""),
            "ambient_glow" to ((th.cauHinh["ambient_glow"] as? Boolean) ?: true),
            "app_glow" to ((th.cauHinh["app_glow"] as? Boolean) ?: false),
            "dock_glass" to ((th.cauHinh["dock_glass"] as? Boolean) ?: false),
            "dock_title" to ((th.cauHinh["dock_title"] as? Boolean) ?: true),
            "dock_row" to ((th.cauHinh["dock_row"] as? Boolean) ?: true),
            "fill_dock" to ((th.cauHinh["fill_dock"] as? Boolean) ?: true),
            "focus_overshoot" to ((th.cauHinh["focus_overshoot"] as? Boolean) ?: true),
            "animations" to ((th.cauHinh["animations"] as? Boolean) ?: true),
            "advanced_effects" to ((th.cauHinh["advanced_effects"] as? Boolean) ?: false),
            "show_network_pill" to ((th.cauHinh["show_network_pill"] as? Boolean) ?: true),
            "show_inputs_pill" to ((th.cauHinh["show_inputs_pill"] as? Boolean) ?: true),
            "module_cards" to ((th.cauHinh["module_cards"] as? Boolean) ?: true),
            "show_home_settings_shortcut" to ((th.cauHinh["show_home_settings_shortcut"] as? Boolean) ?: false),
            "key_filter" to ((th.cauHinh["key_filter"] as? Boolean) ?: true),
            "lunar_date" to ((th.cauHinh["lunar_date"] as? Boolean) ?: true),
            "always_show_names" to ((th.cauHinh["always_show_names"] as? Boolean) ?: false),
            "app_library" to ((th.cauHinh["app_library"] as? Boolean) ?: false),
            "home_idle_clock_minutes" to ((th.cauHinh["home_idle_clock_minutes"] as? Int) ?: 0),
            "reduced_motion" to ((th.cauHinh["reduced_motion"] as? Boolean) ?: false),
            "wallpaper_dim" to ((th.cauHinh["wallpaper_dim"] as? Int) ?: 0),
            "show_stats" to ((th.cauHinh["show_stats"] as? Boolean) ?: false),
            "dream_button" to ((th.cauHinh["dream_button"] as? Boolean) ?: true),
            "top_compact" to ((th.cauHinh["top_compact"] as? Boolean) ?: true),
            "handle_view_inputs" to ((th.cauHinh["handle_view_inputs"] as? Boolean) ?: false),
            "boot_to_live_tv" to ((th.cauHinh["boot_to_live_tv"] as? Boolean) ?: false),
            "wallpaper_true_color" to ((th.cauHinh["wallpaper_true_color"] as? Boolean) ?: false),
            "wallpaper_captions" to ((th.cauHinh["wallpaper_captions"] as? Boolean) ?: false),
            "wallpaper_order" to ((th.cauHinh["wallpaper_order"] as? String) ?: "shuffle"),
            "settings_key_panel" to ((th.cauHinh["settings_key_panel"] as? Boolean) ?: false),
            "reorder_button" to ((th.cauHinh["reorder_button"] as? Boolean) ?: false),
            "live_tv_package" to ((th.cauHinh["live_tv_package"] as? String) ?: ""),
            "vpn_app" to ((th.cauHinh["vpn_app"] as? String) ?: ""),
            "cau_hinh_tuy_y" to "",
            "nang_cao" to ""
        )
    }

    fun luuHoSoTheme(id: String) {
        val m = mutableMapOf<String, Any>(
            "columns" to soCot,
            "rows_below_dock" to hangDuoiDock,
            "dock_band" to daiNenDock,
            "dock_opacity" to doMoDock,
            "cinematic_home" to cheDoDienAnh,
            "glow_style" to kieuQuangSang,
            "ambient_glow" to toaSangNenDock,
            "app_glow" to toaSangIconApp,
            "dock_glass" to hieuUngKinh,
            "dock_title" to tieuDeDock,
            "dock_row" to hangDockRieng,
            "fill_dock" to dienDayDock,
            "focus_overshoot" to hieuUngLoXo,
            "animations" to chuyenDongCoBan,
            "advanced_effects" to hieuUngNangCao,
            "show_network_pill" to hienNutWifi,
            "show_inputs_pill" to hienNutInputs,
            "module_cards" to theModuleCards,
            "show_home_settings_shortcut" to nutHomeCaiDat,
            "key_filter" to boLocPhim,
            "lunar_date" to amLich,
            "always_show_names" to tenDuoiApp,
            "app_library" to thuVienApp,
            "home_idle_clock_minutes" to dongHoKhiYen,
            "reduced_motion" to tiviYeu,
            "wallpaper_dim" to lamToiNen,
            "show_stats" to hienThongSo,
            "dream_button" to nutManCho,
            "top_compact" to gonNut,
            "handle_view_inputs" to nutInput,
            "boot_to_live_tv" to tuVaoTruyenHinh,
            "wallpaper_true_color" to hinhNenTrueColor,
            "wallpaper_captions" to chuThichHinhNen,
            "wallpaper_order" to thuTuHinhNen,
            "settings_key_panel" to bangCaiDatNhanh,
            "reorder_button" to nutSapXep,
            "live_tv_package" to appTruyenHinh,
            "vpn_app" to appVpn,
            "cau_hinh_tuy_y" to cauHinhTuyY,
            "nang_cao" to JSONObject(nangCao as Map<*, *>).toString()
        )
        hoSoTheoTheme[id] = m
        runCatching {
            prefs.edit().putString("hoso_theme_$id", JSONObject(m as Map<*, *>).toString()).apply()
        }
    }

    fun napHoSoTheme(th: zone.vgs.tvsach.dulieu.Theme) {
        val m = hoSoTheoTheme[th.id] ?: run {
            val luu = runCatching { prefs.getString("hoso_theme_${th.id}", null) }.getOrNull()
            if (!luu.isNullOrBlank()) {
                runCatching {
                    val j = JSONObject(luu)
                    val map = mutableMapOf<String, Any>()
                    j.keys().forEach { k -> map[k] = j.get(k) }
                    map
                }.getOrNull()
            } else null
        } ?: taoHoSoMacDinh(th).also { hoSoTheoTheme[th.id] = it }

        soCot = (m["columns"] as? Number)?.toInt() ?: (m["columns"] as? String)?.toIntOrNull() ?: 4
        hangDuoiDock = (m["rows_below_dock"] as? Number)?.toInt() ?: (m["rows_below_dock"] as? String)?.toIntOrNull() ?: 0
        daiNenDock = (m["dock_band"] as? Boolean) ?: (m["dock_band"] as? String)?.toBooleanStrictOrNull() ?: true
        doMoDock = (m["dock_opacity"] as? Number)?.toInt() ?: (m["dock_opacity"] as? String)?.toIntOrNull() ?: if (!daiNenDock) 0 else 100
        cheDoDienAnh = (m["cinematic_home"] as? Boolean) ?: (m["cinematic_home"] as? String)?.toBooleanStrictOrNull() ?: false
        kieuQuangSang = (m["glow_style"] as? String) ?: ""
        toaSangNenDock = (m["ambient_glow"] as? Boolean) ?: (m["ambient_glow"] as? String)?.toBooleanStrictOrNull() ?: true
        toaSangIconApp = (m["app_glow"] as? Boolean) ?: (m["app_glow"] as? String)?.toBooleanStrictOrNull() ?: false
        hieuUngKinh = (m["dock_glass"] as? Boolean) ?: (m["dock_glass"] as? String)?.toBooleanStrictOrNull() ?: false
        tieuDeDock = (m["dock_title"] as? Boolean) ?: (m["dock_title"] as? String)?.toBooleanStrictOrNull() ?: true
        hangDockRieng = (m["dock_row"] as? Boolean) ?: (m["dock_row"] as? String)?.toBooleanStrictOrNull() ?: true
        dienDayDock = (m["fill_dock"] as? Boolean) ?: (m["fill_dock"] as? String)?.toBooleanStrictOrNull() ?: true
        hieuUngLoXo = (m["focus_overshoot"] as? Boolean) ?: (m["focus_overshoot"] as? String)?.toBooleanStrictOrNull() ?: true
        chuyenDongCoBan = (m["animations"] as? Boolean) ?: (m["animations"] as? String)?.toBooleanStrictOrNull() ?: true
        hieuUngNangCao = (m["advanced_effects"] as? Boolean) ?: (m["advanced_effects"] as? String)?.toBooleanStrictOrNull() ?: false
        hienNutWifi = (m["show_network_pill"] as? Boolean) ?: (m["show_network_pill"] as? String)?.toBooleanStrictOrNull() ?: true
        hienNutInputs = (m["show_inputs_pill"] as? Boolean) ?: (m["show_inputs_pill"] as? String)?.toBooleanStrictOrNull() ?: true
        theModuleCards = (m["module_cards"] as? Boolean) ?: (m["module_cards"] as? String)?.toBooleanStrictOrNull() ?: true
        nutHomeCaiDat = (m["show_home_settings_shortcut"] as? Boolean) ?: (m["show_home_settings_shortcut"] as? String)?.toBooleanStrictOrNull() ?: false
        boLocPhim = (m["key_filter"] as? Boolean) ?: (m["key_filter"] as? String)?.toBooleanStrictOrNull() ?: true
        amLich = (m["lunar_date"] as? Boolean) ?: (m["lunar_date"] as? String)?.toBooleanStrictOrNull() ?: true
        tenDuoiApp = (m["always_show_names"] as? Boolean) ?: (m["always_show_names"] as? String)?.toBooleanStrictOrNull() ?: false
        thuVienApp = (m["app_library"] as? Boolean) ?: (m["app_library"] as? String)?.toBooleanStrictOrNull() ?: false
        dongHoKhiYen = (m["home_idle_clock_minutes"] as? Number)?.toInt() ?: (m["home_idle_clock_minutes"] as? String)?.toIntOrNull() ?: 0
        tiviYeu = (m["reduced_motion"] as? Boolean) ?: (m["reduced_motion"] as? String)?.toBooleanStrictOrNull() ?: false
        lamToiNen = (m["wallpaper_dim"] as? Number)?.toInt() ?: (m["wallpaper_dim"] as? String)?.toIntOrNull() ?: 0
        hienThongSo = (m["show_stats"] as? Boolean) ?: (m["show_stats"] as? String)?.toBooleanStrictOrNull() ?: false
        nutManCho = (m["dream_button"] as? Boolean) ?: (m["dream_button"] as? String)?.toBooleanStrictOrNull() ?: true
        gonNut = (m["top_compact"] as? Boolean) ?: (m["top_compact"] as? String)?.toBooleanStrictOrNull() ?: true
        nutInput = (m["handle_view_inputs"] as? Boolean) ?: (m["handle_view_inputs"] as? String)?.toBooleanStrictOrNull() ?: false
        tuVaoTruyenHinh = (m["boot_to_live_tv"] as? Boolean) ?: (m["boot_to_live_tv"] as? String)?.toBooleanStrictOrNull() ?: false
        hinhNenTrueColor = (m["wallpaper_true_color"] as? Boolean) ?: (m["wallpaper_true_color"] as? String)?.toBooleanStrictOrNull() ?: false
        chuThichHinhNen = (m["wallpaper_captions"] as? Boolean) ?: (m["wallpaper_captions"] as? String)?.toBooleanStrictOrNull() ?: false
        thuTuHinhNen = (m["wallpaper_order"] as? String) ?: "shuffle"
        bangCaiDatNhanh = (m["settings_key_panel"] as? Boolean) ?: (m["settings_key_panel"] as? String)?.toBooleanStrictOrNull() ?: false
        nutSapXep = (m["reorder_button"] as? Boolean) ?: (m["reorder_button"] as? String)?.toBooleanStrictOrNull() ?: false
        appTruyenHinh = (m["live_tv_package"] as? String) ?: ""
        appVpn = (m["vpn_app"] as? String) ?: ""
        cauHinhTuyY = (m["cau_hinh_tuy_y"] as? String) ?: ""
        val ncStr = (m["nang_cao"] as? String) ?: ""
        nangCao.clear()
        if (ncStr.isNotBlank()) {
            runCatching {
                val j = JSONObject(ncStr)
                j.keys().forEach { k ->
                    val v = j.optString(k, "")
                    if (v.isNotBlank()) nangCao[k] = v
                }
            }
        }
    }

    /** Chọn theme: tự động lưu cài đặt của theme trước và nạp hồ sơ của theme mới (mỗi theme lưu cài đặt độc lập). */
    fun chonTheme(th: zone.vgs.tvsach.dulieu.Theme) {
        if (th.id == themeChon.id) return
        luuHoSoTheme(themeChon.id)
        themeChon = th
        napHoSoTheme(th)
    }

    // Máy chiếu: màn của hãng dò được + những màn đã ghim vào nút "Máy chiếu" trên Home sạch (comp → nhãn).
    var laMayChieu by mutableStateOf(false)
    var mayChieu by mutableStateOf<List<String>?>(null)
    val ghimMayChieu = mutableStateMapOf<String, String>()
    var dangDoMayChieu by mutableStateOf(false)

    fun doMayChieu() {
        if (dangDoMayChieu) return
        dangDoMayChieu = true; loi = ""
        viewModelScope.launch {
            try {
                val ds = io { tv.doMayChieu() }
                mayChieu = ds
                if (ds.isEmpty()) loi = t("Không thấy màn lấy nét / chỉnh hình nào của hãng. Bấm Gửi danh sách để VGS xem giúp.",
                    "No focus / keystone screens found. Tap Send list so VGS can take a look.")
            } catch (e: Throwable) { loi = moTaLoi(e) } finally { dangDoMayChieu = false }
        }
    }

    fun moThuMayChieu(comp: String) = chay(t("Đang mở trên máy chiếu…", "Opening on the projector…")) {
        io { tv.moThu(comp) }?.let { error(t("Máy chiếu không mở được màn này: $it", "The projector refused this screen: $it")) }
    }

    fun doiGhim(comp: String) {
        if (comp in ghimMayChieu) ghimMayChieu.remove(comp) else if (ghimMayChieu.size < 6) ghimMayChieu[comp] = nhanMayChieu(comp)
    }

    /** Đoán nhãn theo tên màn: focus → Lấy nét, keystone/trapez… → Chỉnh hình, zoom/scale → Thu phóng. */
    fun nhanMayChieu(comp: String): String {
        val c = comp.substringAfter('/').lowercase()
        return when {
            Regex("focus|duijiao").containsMatchIn(c) -> t("Lấy nét", "Focus")
            Regex("keystone|trapez|tixing|jiaozheng|correct|geometr|ladder").containsMatchIn(c) -> t("Chỉnh hình", "Keystone")
            Regex("zoom|scale|size").containsMatchIn(c) -> t("Thu phóng", "Zoom")
            Regex("calibr").containsMatchIn(c) -> t("Hiệu chỉnh", "Calibrate")
            else -> c.substringAfterLast('.').removeSuffix("activity").replaceFirstChar { it.uppercase() }
        }
    }

    /** Danh sách màn dò được, để dán vào hộp góp ý vgs.zone. */
    fun chuDanhSachMayChieu(): String = buildString {
        thongTin?.let { append("${it.tenHienThi} · ${it.device} · Android ${it.android}\n") }
        (mayChieu ?: emptyList()).forEach { append(it).append('\n') }
    }
    var thanhPho by mutableStateOf<zone.vgs.tvsach.dulieu.ThanhPho?>(null)

    /** Tên gói Home sạch trên tivi đang nối (Xiaomi bản Trung Quốc: com.ts.launcher), dò lúc kết nối. */
    private var goiHome = ThaoTacTv.PKG_HOME
    /** Tivi đã có Home sạch: chỉ cần đẩy theme, không cài lại. */
    val daCoHomeSach: Boolean get() = trangThai?.let { tt -> goiHome in tt.caiDat && goiHome !in tt.daTat } == true

    var dsSaoLuu by mutableStateOf<List<BanSaoLuu>>(emptyList())

    var banMoi by mutableStateOf<zone.vgs.tvsach.CapNhat.BanMoi?>(null)
    var dangCapNhat by mutableStateOf(false)
    /** Hộp mời nâng cấp: hiện một lần cho mỗi bản mới lúc mở app. */
    var hoiCapNhat by mutableStateOf(false)

    init {
        viewModelScope.launch {
            val b = runCatching { io { zone.vgs.tvsach.CapNhat.kiemTra(ctx) } }.getOrNull()
            banMoi = b
            if (b != null) xuLyBanMoi(b)
        }
        // Chạm thông báo khi app đang mở sẵn: kiểm tra lại cờ mỗi khi quay về.
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1500)
                if (!zone.vgs.tvsach.CapNhat.moTuThongBao) continue
                // Lúc mở app có thể đang mất mạng nên chưa biết bản mới → hỏi lại máy chủ.
                val b = banMoi ?: runCatching { io { zone.vgs.tvsach.CapNhat.kiemTraApp() } }.getOrNull().also { banMoi = it }
                if (b != null) xuLyBanMoi(b) else zone.vgs.tvsach.CapNhat.moTuThongBao = false
            }
        }
        ip = prefs.getString("ip", "") ?: ""
        cong = prefs.getString("cong", "5555") ?: "5555"
        taiLaiSaoLuu()
        napHoSoTheme(themeChon)
    }

    private fun xuLyBanMoi(b: zone.vgs.tvsach.CapNhat.BanMoi) {
        val p = ctx.getSharedPreferences("ota", Context.MODE_PRIVATE)
        if (zone.vgs.tvsach.CapNhat.moTuThongBao) {
            zone.vgs.tvsach.CapNhat.moTuThongBao = false
            hoiCapNhat = false
            capNhatApp()
        } else if (p.getInt("da_hoi", 0) < b.versionCode) {
            p.edit().putInt("da_hoi", b.versionCode).putInt("da_bao", maxOf(p.getInt("da_bao", 0), b.versionCode)).apply()
            hoiCapNhat = true
        }
    }

    fun capNhatApp() {
        val b = banMoi ?: return
        if (dangCapNhat) return
        dangCapNhat = true
        viewModelScope.launch {
            try { io { zone.vgs.tvsach.CapNhat.taiVaCai(ctx, b) } }
            catch (e: Throwable) { loi = t("Tải bản mới không được: ${e.message}", "Could not download the update: ${e.message}") }
            finally { dangCapNhat = false }
        }
    }

    fun taiLaiSaoLuu() { dsSaoLuu = kho.tatCa() }

    private fun chay(viec: String, khoi: suspend () -> Unit) {
        if (dangBan) return
        dangBan = true; loi = ""; thongBao = viec
        viewModelScope.launch {
            try { khoi() } catch (e: Throwable) {
                loi = moTaLoi(e)
            } finally { dangBan = false; thongBao = "" }
        }
    }

    private fun moTaLoi(e: Throwable): String = when {
        e is AdbPairingRequiredException -> t("Tivi đòi ghép đôi (Gỡ lỗi không dây). Dùng phần \"Ghép đôi bằng mã\" bên dưới.", "The TV needs pairing (Wireless debugging). Use \"Pair with code\" below.")
        e.message?.contains("ECONNREFUSED") == true || e.message?.contains("refused") == true ->
            t("Tivi từ chối kết nối ở cổng này. Kiểm tra đã bật Gỡ lỗi USB / Gỡ lỗi qua mạng chưa.", "The TV refused the connection on this port. Check USB debugging / Network debugging is on.")
        e.message?.contains("EHOSTUNREACH") == true || e.message?.contains("No route") == true ->
            t("Không tới được tivi. Điện thoại và tivi phải chung một mạng Wi-Fi.", "Cannot reach the TV. The phone and TV must be on the same Wi-Fi.")
        e is java.net.SocketTimeoutException || e.message?.contains("timed out") == true ->
            t("Hết thời gian chờ. Có thể tivi đang hiện hộp \"Cho phép gỡ lỗi?\" — bấm Cho phép rồi thử lại.", "Timed out. The TV may be showing \"Allow debugging?\" — press Allow and try again.")
        else -> listOfNotNull(e.message, e.cause?.message).firstOrNull()
            ?: t("Lỗi kết nối (${e.javaClass.simpleName}). Kiểm tra IP, cổng và tivi còn bật.", "Connection error (${e.javaClass.simpleName}). Check the IP, port and that the TV is on.")
    }

    private suspend fun <T> io(khoi: () -> T): T = withContext(Dispatchers.IO) { khoi() }

    // ---------- Kết nối ----------

    /** Quét cả mạng /24 của điện thoại tìm máy mở cổng 5555. */
    fun quetMang() {
        if (dangQuet) return
        dangQuet = true; timThay.clear(); loi = ""
        viewModelScope.launch {
            try {
                val goc = ipDienThoai() ?: run { loi = t("Điện thoại chưa vào Wi-Fi.", "The phone is not on Wi-Fi."); return@launch }
                val dau = goc.substringBeforeLast('.')
                val sem = Semaphore(48)
                (1..254).map { i ->
                    async(Dispatchers.IO) {
                        sem.withPermit {
                            val dc = "$dau.$i"
                            if (dc != goc && moCong(dc, 5555, 400)) withContext(Dispatchers.Main) { timThay += dc }
                        }
                    }
                }.awaitAll()
                if (timThay.isEmpty()) loi = t("Không thấy tivi nào mở cổng 5555. Google TV đời mới thường chỉ có Gỡ lỗi không dây: dùng phần ghép đôi.", "No TV with port 5555 open. Newer Google TVs often only offer Wireless debugging: use pairing.")
            } finally { dangQuet = false }
        }
    }

    private fun moCong(host: String, port: Int, ms: Int): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress(host, port), ms) }; true
    }.getOrDefault(false)

    private fun ipDienThoai(): String? {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val lp: LinkProperties = cm.getLinkProperties(cm.activeNetwork) ?: return null
        return lp.linkAddresses.map { it.address }.filterIsInstance<Inet4Address>().firstOrNull()?.hostAddress
    }

    fun ketNoi() = chay(t("Đang kết nối… Nếu tivi hiện \"Cho phép gỡ lỗi USB?\" hãy tích \"Luôn cho phép\" rồi bấm Cho phép.", "Connecting… If the TV shows \"Allow USB debugging?\" tick \"Always allow\" and press Allow.")) {
        val host = ip.trim(); val port = cong.trim().toIntOrNull() ?: 5555
        if (host.isEmpty()) error(t("Nhập địa chỉ IP của tivi.", "Enter the TV's IP address."))
        io {
            runCatching { adb.disconnect() }
            if (!adb.connect(host, port)) error(t("Kết nối không thành công. Thử lại và để ý hộp xác nhận trên tivi.", "Connection failed. Try again and watch for the prompt on the TV."))
        }
        prefs.edit().putString("ip", host).putString("cong", port.toString()).apply()
        sauKhiKetNoi(host)
    }

    /** Chế độ tivi: kết nối ADB vào chính máy này. */
    fun ketNoiTiviNay() { ip = "127.0.0.1"; cong = "5555"; ketNoi() }

    /** Chế độ tivi: các bước bấm Home / khởi động lại Home đẩy app xuống nền — mở lại app cho người dùng thấy kết quả. */
    private suspend fun veApp() {
        if (zone.vgs.tvsach.ManHinh.laTv) io { runCatching { adb.shell("am start -n ${ctx.packageName}/zone.vgs.tvsach.MainActivity") } }
    }

    /** Ghép đôi Gỡ lỗi không dây (Android 11+), rồi tìm cổng kết nối qua mDNS. */
    fun ghepDoi() = chay(t("Đang ghép đôi…", "Pairing…")) {
        val (h, p) = tachIpCong(ghepIpCong) ?: error(t("Nhập dạng 192.168.1.20:37123 (IP và cổng trong hộp ghép đôi).", "Enter it as 192.168.1.20:37123 (IP and port from the pairing dialog)."))
        val ma = ghepMa.trim()
        if (ma.length != 6) error(t("Mã ghép đôi có 6 số.", "The pairing code has 6 digits."))
        io { if (!adb.pair(h, p, ma)) error(t("Ghép đôi không thành công, mã có thể đã đổi. Mở lại hộp ghép đôi trên tivi.", "Pairing failed, the code may have changed. Reopen the pairing dialog on the TV.")) }
        thongBao = t("Đã ghép đôi. Đang tìm cổng kết nối của tivi…", "Paired. Looking for the TV's connect port…")
        val ok = io {
            runCatching { adb.disconnect() }
            runCatching { adb.connectTls(ctx, 15_000) }.getOrDefault(false)
        }
        if (!ok) {
            ip = h
            error(t("Đã ghép đôi nhưng chưa tự tìm được cổng. Nhập IP và cổng ở dòng \"Địa chỉ IP & cổng\" ", "Paired, but the port was not found automatically. Enter the IP and port from the \"IP address & Port\" line ") +
                t("trên màn Gỡ lỗi không dây của tivi (không phải cổng ghép đôi) rồi bấm Kết nối.", "on the TV's Wireless debugging screen (not the pairing port) and press Connect."))
        }
        ip = h
        prefs.edit().putString("ip", h).apply()
        sauKhiKetNoi(h)
    }

    private fun tachIpCong(s: String): Pair<String, Int>? {
        val t = s.trim(); val i = t.lastIndexOf(':')
        if (i <= 0) return null
        val p = t.substring(i + 1).toIntOrNull() ?: return null
        return t.substring(0, i) to p
    }

    private suspend fun sauKhiKetNoi(host: String) {
        daNapCu = false
        tv.quenTivi()
        thongBao = t("Đang đọc thông tin tivi…", "Reading the TV…")
        val (info, gp) = io { tv.docThongTin() }
        goiHome = io { tv.pkgHome }
        thongTin = info; getprop = gp
        val tt = io { tv.docTrangThai() }
        trangThai = tt
        val du = io { DuLieu.nap(ctx) }
        ungVien = du.khopMau(info)
        mauChon = ungVien.firstOrNull()?.first
        saoLuu = kho.doc(info.khoaLuu)?.also { it.ip = host; kho.ghi(it) }
        nutInput = tt.homeHienTai == "com.google.android.apps.tv.launcherx"
        laMayChieu = tv.coVeMayChieu(tt, info)
        man = if (dangKhoiPhuc) Man.KHOI_PHUC else Man.THONG_TIN
    }

    var dangKhoiPhuc by mutableStateOf(false)
    var khoiPhucXong by mutableStateOf(false)

    // ---------- Lập kế hoạch ----------

    fun lapKeHoach() = chay(t("Đang so với 213 mẫu tivi…", "Comparing with 213 TV models…")) {
        val tt = trangThai ?: return@chay
        val du = io { DuLieu.nap(ctx) }
        val ds = io { KeHoach.lap(du, mauChon, tt) }
        muc = ds
        chon.clear(); ds.forEach { chon[it.pkg] = it.chonSan }
        man = Man.CHON_GOI
    }

    fun soDaChon() = chon.count { it.value }

    // ---------- Tắt gói ----------

    fun batDauTat() = chay(t("Đang sao lưu…", "Backing up…")) {
        val info = thongTin ?: return@chay
        nhatKy.clear(); ketQua = null; tienDo = 0f
        manTruocHome = Man.DANG_TAT
        man = Man.DANG_TAT
        val sl = io { tv.damBaoSaoLuu(info, getprop, ip) }
        saoLuu = sl
        ghi(t("Đã lưu bản sao lưu (${sl.goiTruoc.size} gói, ${sl.daTatTruoc.size} gói vốn đã tắt).", "Backup saved (${sl.goiTruoc.size} packages, ${sl.daTatTruoc.size} already disabled)."))
        val ds = muc.filter { chon[it.pkg] == true }.map { it.pkg }
        val kq = io { tv.tatGoi(ds, sl, epSetupwraith) { s -> ghiTuIo(s) } }
        ketQua = kq
        zone.vgs.tvsach.Dem.gui(ctx, "don", tenTiviDem(), kq.daTat.size, if (mauChon != null) "mau:${mauChon!!.id}" else "luat-chung")
        ghi(t("Xong: tắt ${kq.daTat.size}, giữ lại ${kq.boQua.size}, lỗi ${kq.loi.size}.", "Done: disabled ${kq.daTat.size}, kept ${kq.boQua.size}, failed ${kq.loi.size}."))
        trangThai = io { tv.docTrangThai() }
        saoLuu = kho.doc(info.khoaLuu)
        taiLaiSaoLuu()
        chuanBiDock()
    }

    // Đi qua hàng đợi Main như ghiTuIo, để dòng tổng kết không chen lên trước dòng tiến độ.
    /** Tên tivi gửi kèm số liệu ẩn danh: hãng + model + device + Android. */
    private fun tenTiviDem(): String = thongTin?.let { "${it.tenHienThi} (${it.device}) Android ${it.android}" } ?: ""

    private fun ghi(s: String) { viewModelScope.launch(Dispatchers.Main) { nhatKy += s } }
    /** 0..1 khi đang tắt/bật lại từng gói, -1 khi không đếm được. */
    var tienDo by mutableStateOf(-1f)

    private fun ghiTuIo(s: String) {
        viewModelScope.launch(Dispatchers.Main) {
            nhatKy += s; thongBao = s
            Regex("""(\d+)/(\d+)""").find(s)?.let { m ->
                val (a, b) = m.destructured
                if (b.toInt() > 0) tienDo = a.toFloat() / b.toFloat()
            }
        }
    }

    fun chupManHinh() = chay(t("Đang chụp màn hình tivi…", "Taking a TV screenshot…")) {
        val bytes = io { tv.chupManHinh() }
        anhTv = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: error(t("Không chụp được (một số tivi cấm chụp khi đang xem HDMI — bấm Home trên remote rồi thử lại).", "Could not capture (some TVs block it on an HDMI input — press Home on the remote and retry)."))
    }

    // ---------- Home sạch ----------

    /** App có biểu tượng còn bật, để chọn vào dock. */
    fun appChoDock(): List<String> {
        val tt = trangThai ?: return emptyList()
        val khongDua = ThaoTacTv.HOME_SACH + setOf("com.android.tv.settings", "com.android.settings") + ThaoTacTv.HOME_GOC_BIET
        return tt.coBieuTuong.filter { it in tt.caiDat && it !in tt.daTat && it !in khongDua }
            .sortedWith(compareBy({ p -> UU_TIEN_DOCK.indexOf(p).let { if (it < 0) 99 else it } }, { it }))
    }

    /** Từ màn thông tin: đổi theme cho tivi đã có Home sạch. */
    fun moDoiTheme() { chuanBiDock(); manTruocHome = Man.THONG_TIN; man = Man.HOME_SACH; viewModelScope.launch { napCauHinhCu() } }

    /** Bỏ qua bước sao lưu & dọn gói để sang thẳng cài đặt / tuỳ biến Home sạch. */
    fun boQuaDon() = chay(t("Đang sao lưu…", "Backing up…")) {
        val info = thongTin ?: return@chay
        saoLuu = io { tv.damBaoSaoLuu(info, getprop, ip) }
        taiLaiSaoLuu()
        chuanBiDock()
        manTruocHome = Man.CHON_GOI
        man = Man.HOME_SACH
        if (daCoHomeSach) napCauHinhCu()
    }
    fun boQuaDonGoi() = boQuaDon()

    /** Đã nạp config.json của tivi vào các lựa chọn chưa (mỗi lần kết nối một lần — quay lại màn không xoá chỉnh sửa). */
    private var daNapCu = false

    /** "Đổi giao diện": mở ra đúng các lựa chọn đang có trên tivi thay vì mặc định. */
    private suspend fun napCauHinhCu() {
        if (daNapCu) return
        daNapCu = true
        val j = runCatching { io { tv.docCauHinhTv() } }.getOrNull() ?: return
        // Chỉ nạp lại khoá NGƯỜI DÙNG đặt ở Nâng cao (ghi trong vgs_nang_cao), không lấy khoá do theme ghi
        nangCao.clear()
        val daDat = j.optJSONArray("vgs_nang_cao")?.let { a -> (0 until a.length()).map { a.optString(it) }.toSet() } ?: emptySet()
        dsNangCao.filter { it.khoa in daDat }.forEach { tc -> zone.vgs.tvsach.dulieu.NangCao.docChu(tc, j)?.let { nangCao[tc.khoa] = it } }

        amLich = j.optBoolean("lunar_date", amLich)
        tenDuoiApp = j.optBoolean("always_show_names", false)
        thuVienApp = j.optBoolean("app_library", false)
        dongHoKhiYen = j.optInt("home_idle_clock_minutes", 0)
        tiviYeu = j.optBoolean("reduced_motion", false)
        hieuUngKinh = j.optBoolean("dock_glass", false)
        lamToiNen = j.optInt("wallpaper_dim", 0)
        hienThongSo = j.optBoolean("show_stats", false)
        if (j.has("handle_view_inputs")) nutInput = j.optBoolean("handle_view_inputs", nutInput)
        if (j.has("columns")) soCot = j.optInt("columns", soCot)
        if (j.has("rows_below_dock")) hangDuoiDock = j.optInt("rows_below_dock", hangDuoiDock)
        if (j.has("dock_opacity")) doMoDock = j.optInt("dock_opacity", doMoDock)
        if (j.has("dock_band")) daiNenDock = j.optBoolean("dock_band", daiNenDock)
        if (j.optInt("dock_opacity", 100) == 0) daiNenDock = false
        cheDoDienAnh = j.optBoolean("cinematic_home", cheDoDienAnh)
        if (j.has("glow_style")) kieuQuangSang = j.optString("glow_style", kieuQuangSang)
        tuVaoTruyenHinh = j.optBoolean("boot_to_live_tv", tuVaoTruyenHinh)
        hinhNenTrueColor = j.optBoolean("wallpaper_true_color", hinhNenTrueColor)
        chuThichHinhNen = j.optBoolean("wallpaper_captions", chuThichHinhNen)
        if (j.has("wallpaper_order")) thuTuHinhNen = j.optString("wallpaper_order", thuTuHinhNen)
        bangCaiDatNhanh = j.optBoolean("settings_key_panel", bangCaiDatNhanh)
        nutSapXep = j.optBoolean("reorder_button", nutSapXep)
        if (j.has("show_network_pill")) hienNutWifi = j.optBoolean("show_network_pill", hienNutWifi)
        if (j.has("show_inputs_pill")) hienNutInputs = j.optBoolean("show_inputs_pill", hienNutInputs)
        if (j.has("dock_title")) tieuDeDock = j.optBoolean("dock_title", tieuDeDock)
        if (j.has("dock_row")) hangDockRieng = j.optBoolean("dock_row", hangDockRieng)
        if (j.has("fill_dock")) dienDayDock = j.optBoolean("fill_dock", dienDayDock)
        if (j.has("focus_overshoot")) hieuUngLoXo = j.optBoolean("focus_overshoot", hieuUngLoXo)
        if (j.has("animations")) chuyenDongCoBan = j.optBoolean("animations", chuyenDongCoBan)
        if (j.has("advanced_effects")) hieuUngNangCao = j.optBoolean("advanced_effects", hieuUngNangCao)
        if (j.has("ambient_glow")) toaSangNenDock = j.optBoolean("ambient_glow", toaSangNenDock)
        if (j.has("app_glow")) toaSangIconApp = j.optBoolean("app_glow", toaSangIconApp)
        if (j.has("module_cards")) theModuleCards = j.optBoolean("module_cards", theModuleCards)
        if (j.has("show_home_settings_shortcut")) nutHomeCaiDat = j.optBoolean("show_home_settings_shortcut", nutHomeCaiDat)
        if (j.has("key_filter")) boLocPhim = j.optBoolean("key_filter", boLocPhim)
        if (j.has("live_tv_package")) appTruyenHinh = j.optString("live_tv_package", appTruyenHinh)
        if (j.has("vpn_app")) appVpn = j.optString("vpn_app", appVpn)
        nutManCho = j.optBoolean("dream_button", nutManCho)
        gonNut = j.optBoolean("top_compact", gonNut)
        j.optJSONArray("dock_apps")?.let { a -> val ds = (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }; if (ds.isNotEmpty()) { dock.clear(); dock.addAll(ds.take(DOCK_TOI_DA)) } }
        j.optString("greeting_name").takeIf { it.isNotBlank() }?.let { tenChao = it }
        j.optString("weather_place").takeIf { it.isNotBlank() }?.let { ten -> THANH_PHO.firstOrNull { it.ten == ten }?.let { thanhPho = it } }
        luuHoSoTheme(themeChon.id)
    }

    private fun chuanBiDock() {
        if (dock.isNotEmpty()) return
        dock.addAll(appChoDock().filter { it in UU_TIEN_DOCK }.take(4))
    }

    fun doiDock(p: String) {
        if (p in dock) dock.remove(p) else if (dock.size < DOCK_TOI_DA) dock.add(p)
    }

    fun caiHomeSach() = chay(t("Đang cài Home sạch…", "Installing the clean Home…")) {
        val sl = saoLuu ?: io { tv.damBaoSaoLuu(thongTin!!, getprop, ip) }.also { saoLuu = it }
        nhatKy.clear()
        val apk = io { zone.vgs.tvsach.CapNhat.moAsset(ctx, tv.apkHome).readBytes() }
        val cauHinh = cauHinhHome()
        val anh = anhNenTheme()
        val bo = boAnhTheme()
        val loiCai = io { tv.caiLauncher(apk, sl, cauHinh, anh, bo, nutInput, nutHome) { s -> ghiTuIo(s) } }
        if (loiCai != null) error(loiCai)
        if (dnsChanQc) io { tv.batDnsChanQc(sl) }
        if (nutManCho) io { tv.chuanBiManCho(sl) }
        thuPhimHome(sl)
        zone.vgs.tvsach.Dem.gui(ctx, "home", tenTiviDem(), dock.size, "theme:${themeChon.id}")
        if (ghimMayChieu.isNotEmpty()) zone.vgs.tvsach.Dem.gui(ctx, "may-chieu", tenTiviDem(), ghimMayChieu.size, ghimMayChieu.keys.joinToString(",").take(110))
        homeXong = true
        ghi(t("Home sạch đã chạy. Bấm Home trên remote để xem.", "The clean Home is running. Press Home on the remote to see it."))
        taiLaiSaoLuu()
        man = Man.XONG
    }

    /**
     * Bấm Home thật trên tivi, tắt gói còn giành nút Home (ThaoTacTv.damBaoPhimHome). Không được thì báo rõ, không
     * báo "xong" suông; gửi tên gói giành Home về bộ đếm để biết hãng nào bị (22/10/2026).
     */
    private suspend fun thuPhimHome(sl: zone.vgs.tvsach.dulieu.BanSaoLuu?) {
        val kq = io { tv.damBaoPhimHome(sl) { s -> ghiTuIo(s) } }
        veApp()
        phimHomeOk = kq.ok
        if (kq.gianh.isNotEmpty() || !kq.ok) {
            // ok: đã tắt và chạy; lui: đã tắt nhưng không được nên bật lại; loi: không tắt gì.
            val dau = if (kq.ok) "ok:" else if (kq.gianh.isNotEmpty()) "lui:" else "loi:"
            zone.vgs.tvsach.Dem.gui(ctx, "home-phim", tenTiviDem(), kq.gianh.size,
                dau + (kq.gianh + kq.conLai).filter { it.isNotBlank() }.joinToString(",").take(110))
        }
        if (kq.ok && kq.gianh.isNotEmpty()) {
            ghi(t("Đã tắt ${kq.gianh.joinToString()} vì nó giành nút Home. Menu hình ảnh / âm thanh của hãng (nếu nằm trong Home cũ) vào bằng nút Cài đặt trên Home sạch; muốn trả lại thì bấm Khôi phục.",
                "Disabled ${kq.gianh.joinToString()} because it kept the Home button. Reach the maker's picture / sound menus with the Settings button on the clean Home; Restore brings it back."))
            taiLaiSaoLuu()
        }
        if (!kq.ok) {
            val goi = kq.conLai.ifBlank { t("không rõ", "unknown") }
            loi = if (kq.lyDo == "cvte")
                t("Nút Home trên tivi vẫn mở Home gốc ($goi). Tivi bo mạch CVTE: app không tắt Home gốc vì đã có tivi treo ở màn khởi động sau khi tắt. Tắt mở tivi một lần rồi bấm Home thử lại; vẫn về Home cũ thì Home sạch vẫn mở được từ danh sách ứng dụng.",
                    "The TV's Home button still opens the stock Home ($goi). CVTE board: the app never disables it, since one TV hung on its boot animation after that. Restart the TV once and press Home again; if it still opens the old Home, launch the clean Home from the app list.")
            else if (kq.lyDo == "setupwraith")
                t("Nút Home trên tivi vẫn mở Home Google ($goi). Trên Google TV phải tắt cả setupwraith thì mới thay được Home, mà nó đang giữ Wi-Fi nên app không tự tắt. Tivi vẫn dùng bình thường như cũ. Muốn thay hẳn nút Home: cắm dây mạng LAN cho tivi, kết nối lại, ở bước chọn gói tích thêm setupwraith rồi dọn lại.",
                    "The TV's Home button still opens Google's Home ($goi). On Google TV setupwraith must be disabled too before Home can change, and it holds the Wi-Fi, so the app leaves it. The TV works as before. To take over Home fully: plug the TV into Ethernet, reconnect, tick setupwraith in the package list and clean again.")
            else
                t("Nút Home trên tivi vẫn mở '$goi' thay vì Home sạch. App đã để tivi như cũ, không tắt thêm gì. Gửi góp ý kèm hãng, model để VGS thêm hỗ trợ.",
                    "The TV's Home button still opens '$goi' instead of the clean Home. The app left the TV as it was. Send feedback with the brand and model so VGS can add support.")
        }
    }

    private fun cauHinhHome(): String {
        luuHoSoTheme(themeChon.id)
        return JSONObject().apply {
            put("dock_apps", JSONArray(dock.toList()))
            if (tenChao.isNotBlank()) put("greeting_name", tenChao.trim())
            themeChon.ghiVao(this)
            put("dream_button", nutManCho)
            put("top_compact", gonNut)
            thanhPho?.let { put("weather_lat", it.lat); put("weather_lon", it.lon); put("weather_place", it.ten) }
            if (ghimMayChieu.isNotEmpty()) put("projector_actions", JSONArray(ghimMayChieu.map { (comp, nhan) -> "$nhan=$comp" }))
            put("lunar_date", amLich)
            // Launcher 2.6: nút Input trên remote (VIEW_INPUTS) mở bảng chọn nguồn của Home sạch — đi cùng công tắc nút Input.
            if (nutInput) put("handle_view_inputs", true)
            if (tenDuoiApp) put("always_show_names", true)
            if (thuVienApp) { put("app_library", true); put("library_button", true) }
            if (dongHoKhiYen > 0) put("home_idle_clock_minutes", dongHoKhiYen)
            if (tiviYeu) put("reduced_motion", true)
            if (hieuUngKinh) { put("dock_glass", true); put("app_glow", true) }
            if (lamToiNen > 0) put("wallpaper_dim", lamToiNen)
            if (hienThongSo) put("show_stats", true)

            // Các Addons & Switches từ https://cleanyour.tv/addons
            if (soCot in 1..8) put("columns", soCot)
            if (hangDuoiDock in 0..3) put("rows_below_dock", hangDuoiDock)
            if (!daiNenDock) {
                put("dock_band", false)
                put("dock_opacity", 0)
            } else {
                put("dock_band", true)
                if (doMoDock in 30..99) put("dock_opacity", doMoDock)
            }
            put("cinematic_home", cheDoDienAnh)
            if (kieuQuangSang.isNotBlank()) put("glow_style", kieuQuangSang)
            put("boot_to_live_tv", tuVaoTruyenHinh)
            put("wallpaper_true_color", hinhNenTrueColor)
            put("wallpaper_captions", chuThichHinhNen)
            if (thuTuHinhNen == "sequential") put("wallpaper_order", "sequential")
            put("settings_key_panel", bangCaiDatNhanh)
            put("reorder_button", nutSapXep)
            put("show_network_pill", hienNutWifi)
            put("show_inputs_pill", hienNutInputs)
            put("dock_title", tieuDeDock)
            put("dock_row", hangDockRieng)
            put("fill_dock", dienDayDock)
            put("focus_overshoot", hieuUngLoXo)
            put("animations", chuyenDongCoBan)
            put("advanced_effects", hieuUngNangCao)
            put("ambient_glow", toaSangNenDock)
            put("app_glow", toaSangIconApp)
            put("module_cards", theModuleCards)
            put("show_home_settings_shortcut", nutHomeCaiDat)
            put("key_filter", boLocPhim)
            if (appTruyenHinh.isNotBlank()) put("live_tv_package", appTruyenHinh.trim())
            if (appVpn.isNotBlank()) put("vpn_app", appVpn.trim())

            // Nâng cao ghi sau cùng: người dùng đã chủ động đặt thì đè lên giá trị theme và switch.
            val daDat = dsNangCao.mapNotNull { tc ->
                nangCao[tc.khoa]?.let { chu ->
                    zone.vgs.tvsach.dulieu.NangCao.giaTri(tc, chu)?.let {
                        put(tc.khoa, it)
                        tc.khoa
                    }
                }
            }
            if (daDat.isNotEmpty()) put("vgs_nang_cao", JSONArray(daDat))

            // Nhập JSON tuỳ ý (cho phép bất kỳ khoá nào trong 64 switches)
            if (cauHinhTuyY.isNotBlank()) {
                runCatching {
                    val them = JSONObject(cauHinhTuyY.trim())
                    them.keys().forEach { k -> put(k, them.get(k)) }
                }
            }
        }.toString()
    }

    fun xemTruocCauHinh(): String = runCatching {
        JSONObject(cauHinhHome()).toString(2)
    }.getOrDefault(cauHinhHome())

    private suspend fun anhNenTheme(): ByteArray? = themeChon.anhNen?.let { a -> io { ctx.assets.open(a).readBytes() } }

    /** Bộ ảnh xoay vòng của theme: mọi file ảnh trong thư mục assets boAnh. */
    private suspend fun boAnhTheme(): List<Pair<String, ByteArray>>? = themeChon.boAnh?.let { thu ->
        io { (ctx.assets.list(thu) ?: emptyArray()).sorted().map { it to ctx.assets.open("$thu/$it").readBytes() } }
    }

    /** Đổi theme / dock cho tivi đã có Home sạch. */
    fun apDungTheme() = chay(t("Đang đổi giao diện…", "Applying the theme…")) {
        val json = cauHinhHome()
        val anh = anhNenTheme()
        // Cài đè launcher đi kèm app (bản mới có dòng thời tiết…), giữ dữ liệu và dịch vụ trợ năng.
        thongBao = t("Đang cập nhật Home sạch…", "Updating the clean Home…")
        if (!io { tv.capNhatLauncher(zone.vgs.tvsach.CapNhat.moAsset(ctx, tv.apkHome).readBytes()) }) {
            loi = t("Không cập nhật được Home sạch trên tivi, giao diện mới sẽ không hiện đủ. Thử lại hoặc gửi góp ý kèm hãng, model.", "Couldn't update the clean Home on the TV, so the new look won't fully show. Try again or send feedback with the brand and model.")
            return@chay
        }
        if (anh != null) thongBao = t("Đang gửi ảnh nền (${anh.size / 1024} KB)…", "Sending the wallpaper (${anh.size / 1024} KB)…")
        val bo = boAnhTheme()
        if (bo != null) thongBao = t("Đang gửi ${bo.size} ảnh phong cảnh…", "Sending ${bo.size} landscape pictures…")
        io { tv.dayCauHinh(json, anh, bo) }
        if (nutManCho) io { tv.chuanBiManCho(saoLuu) }
        thongBao = t("Đang thử nút Home trên tivi…", "Testing the TV's Home button…")
        val sl = saoLuu ?: thongTin?.let { tt -> runCatching { io { tv.damBaoSaoLuu(tt, getprop, ip) } }.getOrNull() }?.also { saoLuu = it }
        thuPhimHome(sl)
        zone.vgs.tvsach.Dem.gui(ctx, "home", tenTiviDem(), dock.size, "doi:${themeChon.id}")
        if (ghimMayChieu.isNotEmpty()) zone.vgs.tvsach.Dem.gui(ctx, "may-chieu", tenTiviDem(), ghimMayChieu.size, ghimMayChieu.keys.joinToString(",").take(110))
        homeXong = true
        man = Man.XONG
    }

    fun khoiDongLaiTv() = chay(t("Đang khởi động lại tivi…", "Rebooting the TV…")) {
        io { runCatching { adb.shell("reboot") } }
        runCatching { io { adb.disconnect() } }
        thongBao = ""
        loi = t("Tivi đang khởi động lại. Đợi tivi lên rồi bấm Kết nối lại để kiểm tra (gói nào tự bật lại sau khởi động sẽ hiện ra).", "The TV is rebooting. When it is back, press Connect again to check (packages that re-enable at boot will show up).")
        man = Man.KET_NOI
    }

    fun tatAdbVaKetThuc() = chay(t("Đang tắt ADB trên tivi…", "Turning ADB off on the TV…")) {
        io { tv.tatAdb() }
        runCatching { io { adb.disconnect() } }
        val host = ip
        val conMo = io { Thread.sleep(2500); moCong(host, 5555, 1500) }
        loi = if (conMo) t("Cổng 5555 vẫn mở. Vào Tuỳ chọn nhà phát triển trên tivi, tắt Gỡ lỗi USB / Gỡ lỗi qua mạng rồi khởi động lại tivi.", "Port 5555 is still open. In Developer options on the TV, turn off USB / Network debugging and reboot.")
        else ""
        thongBao = ""
        datLai()
        man = Man.CHINH
    }

    // ---------- Khôi phục ----------

    fun moKhoiPhuc(b: BanSaoLuu) {
        dangKhoiPhuc = true
        ip = b.ip
        man = Man.KET_NOI
    }

    fun chayKhoiPhuc(goLauncher: Boolean) = chay(t("Đang khôi phục…", "Restoring…")) {
        val info = thongTin ?: return@chay
        val sl = kho.doc(info.khoaLuu) ?: error(t("Tivi này chưa có bản sao lưu trong máy.", "This phone has no backup for this TV."))
        nhatKy.clear()
        val loiKp = io { tv.khoiPhuc(sl, goLauncher) { s -> ghiTuIo(s) } }
        veApp()
        zone.vgs.tvsach.Dem.gui(ctx, "khoi-phuc", tenTiviDem(), sl.daTatBoiApp.size)
        loiKp.forEach { ghi(t("Lỗi: $it", "Error: $it")) }
        if (loiKp.isEmpty() && sl.daTatBoiApp.isEmpty() && !sl.launcherDaCai && !sl.dnsDaDoi) {
            kho.xoa(sl.khoa)
            khoiPhucXong = true
            ghi(t("Đã khôi phục xong, xoá bản sao lưu.", "Restore complete, backup removed."))
        } else ghi(t("Khôi phục xong một phần, bản sao lưu vẫn giữ để chạy lại.", "Partly restored; the backup is kept so you can run it again."))
        saoLuu = kho.doc(info.khoaLuu)
        taiLaiSaoLuu()
    }

    fun datLai() {
        thongTin = null; trangThai = null; muc = emptyList(); chon.clear(); ketQua = null
        dock.clear(); tenChao = ""; homeXong = false; dnsChanQc = false; dangKhoiPhuc = false; khoiPhucXong = false; anhTv = null
        laMayChieu = false; mayChieu = null; ghimMayChieu.clear()
        nhatKy.clear(); saoLuu = null; epSetupwraith = false
        taiLaiSaoLuu()
    }

    fun veManChinh() {
        viewModelScope.launch { runCatching { io { adb.disconnect() } } }
        datLai(); loi = ""; man = Man.CHINH
    }

    companion object {
        val UU_TIEN_DOCK = listOf(
            "com.google.android.youtube.tv", "com.netflix.ninja", "com.fptplay.tv", "net.fptplay.ottbox",
            "com.vieon.tv", "vn.vieon.tv", "com.viettel.tv360", "com.vnpt.mytv", "com.galaxyplay.tv",
            "com.amazon.amazonvideo.livingroom", "com.disney.disneyplus", "com.spotify.tv.android",
            "com.android.vending",
        )
    }
}
