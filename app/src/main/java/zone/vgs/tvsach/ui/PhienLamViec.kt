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

/** Số app tối đa trong dock: bằng số cột lớn nhất của Home sạch (columns 1–8); dock dài hơn số cột thì tràn xuống hàng sau (22/10/2026, góp ý muốn hàng đầu nhiều app hơn). */
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
    /** Bản sao lưu vừa lấy từ tivi (điện thoại này không có). */
    var saoLuuTuTv by mutableStateOf(false)

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
    /** Mục Nâng cao: danh sách tuỳ chọn + giá trị người dùng đặt (khoá → chữ; thiếu = mặc định). */
    val dsNangCao: List<zone.vgs.tvsach.dulieu.TuyChon> by lazy { zone.vgs.tvsach.dulieu.NangCao.nap(ctx) }
    val nangCao = androidx.compose.runtime.mutableStateMapOf<String, String>()
    var homeXong by mutableStateOf(false)
    /** Lần thử nút Home thật gần nhất ra Home sạch (thuPhimHome). */
    var phimHomeOk by mutableStateOf(true)
    var themeChon by mutableStateOf(zone.vgs.tvsach.dulieu.THEMES.first())

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

    /** Tivi đã có Home sạch: chỉ cần đẩy theme, không cài lại. */
    /** Tên gói Home sạch trên tivi đang nối (Xiaomi bản Trung Quốc: com.ts.launcher), dò lúc kết nối. */
    private var goiHome = ThaoTacTv.PKG_HOME
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
        // Điện thoại không có bản sao lưu (gỡ app, đổi máy, người khác dọn) thì lấy bản trên tivi.
        val slMay = kho.doc(info.khoaLuu)
        saoLuu = (slMay ?: io { tv.docSaoLuuTrenTv(info.khoaLuu) })?.also { b ->
            b.ip = host; io { tv.luuSaoLuu(b) }
        }
        saoLuuTuTv = slMay == null && saoLuu != null
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

    /**
     * Bỏ qua bước dọn, chỉ cài / đổi Home sạch (góp ý github.com/vgscity/vgs-cleantv/issues/1, 22/10/2026). Sao lưu VẪN
     * chạy (vài giây): đó là đường lui duy nhất để Khôi phục.
     */
    fun boQuaDon() = chay(t("Đang sao lưu…", "Backing up…")) {
        val info = thongTin ?: return@chay
        saoLuu = io { tv.damBaoSaoLuu(info, getprop, ip) }
        taiLaiSaoLuu()
        chuanBiDock()
        manTruocHome = Man.CHON_GOI
        man = Man.HOME_SACH
        if (daCoHomeSach) napCauHinhCu()
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
    /** Màn mà nút Back ở Home sạch quay về: vừa dọn → màn dọn; Đổi giao diện → thông tin tivi; Bỏ qua → chọn gói. */
    var manTruocHome by mutableStateOf(Man.DANG_TAT)

    fun moDoiTheme() { chuanBiDock(); manTruocHome = Man.THONG_TIN; man = Man.HOME_SACH; viewModelScope.launch { napCauHinhCu() } }

    /** "Đổi giao diện": mở ra đúng các lựa chọn đang có trên tivi thay vì mặc định. */
    /** Đã nạp config.json của tivi vào các lựa chọn chưa (mỗi lần kết nối một lần — quay lại màn không xoá chỉnh sửa). */
    private var daNapCu = false

    private suspend fun napCauHinhCu() {
        if (daNapCu) return
        daNapCu = true
        val j = runCatching { io { tv.docCauHinhTv() } }.getOrNull() ?: return
        amLich = j.optBoolean("lunar_date", amLich)
        tenDuoiApp = j.optBoolean("always_show_names", false)
        thuVienApp = j.optBoolean("app_library", false)
        dongHoKhiYen = j.optInt("home_idle_clock_minutes", 0)
        tiviYeu = j.optBoolean("reduced_motion", false)
        hieuUngKinh = j.optBoolean("dock_glass", false)
        lamToiNen = j.optInt("wallpaper_dim", 0)
        hienThongSo = j.optBoolean("show_stats", false)
        if (j.has("handle_view_inputs")) nutInput = j.optBoolean("handle_view_inputs", nutInput)
        // Chỉ nạp lại khoá NGƯỜI DÙNG đặt ở Nâng cao (ghi trong vgs_nang_cao), không lấy khoá do theme ghi
        // (background_color, sky_intensity…) — nếu không, đổi theme sau đó sẽ bị kẹt giá trị của theme cũ.
        nangCao.clear()
        val daDat = j.optJSONArray("vgs_nang_cao")?.let { a -> (0 until a.length()).map { a.optString(it) }.toSet() } ?: emptySet()
        dsNangCao.filter { it.khoa in daDat }.forEach { tc -> zone.vgs.tvsach.dulieu.NangCao.docChu(tc, j)?.let { nangCao[tc.khoa] = it } }
        nutManCho = j.optBoolean("dream_button", nutManCho)
        gonNut = j.optBoolean("top_compact", gonNut)
        j.optJSONArray("dock_apps")?.let { a -> val ds = (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }; if (ds.isNotEmpty()) { dock.clear(); dock.addAll(ds.take(DOCK_TOI_DA)) } }
        j.optString("greeting_name").takeIf { it.isNotBlank() }?.let { tenChao = it }
        j.optString("weather_place").takeIf { it.isNotBlank() }?.let { ten -> THANH_PHO.firstOrNull { it.ten == ten }?.let { thanhPho = it } }
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

    private fun cauHinhHome(): String = JSONObject().apply {
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
        // Nâng cao ghi sau cùng: người dùng đã chủ động đặt thì đè lên giá trị theme.
        val daDat = dsNangCao.mapNotNull { tc -> nangCao[tc.khoa]?.let { chu -> zone.vgs.tvsach.dulieu.NangCao.giaTri(tc, chu)?.let { put(tc.khoa, it); tc.khoa } } }
        if (daDat.isNotEmpty()) put("vgs_nang_cao", JSONArray(daDat))
    }.toString()

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
            io { tv.xoaSaoLuuTrenTv() }
            khoiPhucXong = true
            ghi(t("Đã khôi phục xong, xoá bản sao lưu.", "Restore complete, backup removed."))
        } else ghi(t("Khôi phục xong một phần, bản sao lưu vẫn giữ để chạy lại.", "Partly restored; the backup is kept so you can run it again."))
        saoLuu = kho.doc(info.khoaLuu)
        taiLaiSaoLuu()
    }

    /** Tivi có Home sạch (bản nào cũng được): đã từng dọn bằng CleanTV / CleanYourTV. */
    val coDauCleanTv: Boolean get() = trangThai?.let { tt -> ThaoTacTv.HOME_SACH.any { it in tt.caiDat } } == true

    /** Gói đang tắt trên tivi khi không có bản sao lưu (null = chưa đọc). */
    var goiTatKhongSaoLuu by mutableStateOf<List<String>?>(null)

    fun docGoiTatKhongSaoLuu() = chay(t("Đang đọc gói đang tắt…", "Reading disabled packages…")) {
        goiTatKhongSaoLuu = io { tv.goiTatBoiNguoiDung() }
    }

    fun chayKhoiPhucKhongSaoLuu(goLauncher: Boolean) = chay(t("Đang khôi phục…", "Restoring…")) {
        nhatKy.clear()
        val loiKp = io { tv.khoiPhucKhongSaoLuu(goLauncher) { s -> ghiTuIo(s) } }
        veApp()
        zone.vgs.tvsach.Dem.gui(ctx, "khoi-phuc", tenTiviDem(), goiTatKhongSaoLuu?.size ?: 0, "khong-sao-luu")
        loiKp.forEach { ghi(t("Lỗi: $it", "Error: $it")) }
        if (loiKp.isEmpty()) khoiPhucXong = true
        else ghi(t("Khôi phục xong một phần, chạy lại để thử tiếp.", "Partly restored; run it again to retry."))
        goiTatKhongSaoLuu = null
    }

    fun datLai() {
        thongTin = null; trangThai = null; muc = emptyList(); chon.clear(); ketQua = null
        dock.clear(); tenChao = ""; homeXong = false; dnsChanQc = false; dangKhoiPhuc = false; khoiPhucXong = false; anhTv = null
        laMayChieu = false; mayChieu = null; ghimMayChieu.clear()
        nhatKy.clear(); saoLuu = null; saoLuuTuTv = false; goiTatKhongSaoLuu = null; epSetupwraith = false
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
