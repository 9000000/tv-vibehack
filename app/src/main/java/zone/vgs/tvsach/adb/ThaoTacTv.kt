package zone.vgs.tvsach.adb

import zone.vgs.tvsach.t
import zone.vgs.tvsach.dulieu.BanSaoLuu
import zone.vgs.tvsach.dulieu.KeHoach
import zone.vgs.tvsach.dulieu.KhoSaoLuu
import zone.vgs.tvsach.dulieu.ThongTinTv
import zone.vgs.tvsach.dulieu.TrangThaiGoi

/**
 * Mọi việc làm trên tivi. Thứ tự và các lớp bảo vệ lấy từ tools/disable.sh và
 * launcher/build-and-install.sh của cleanyourtv; ghi chú "(#số)" là số issue bên đó.
 */
class ThaoTacTv(private val adb: KetNoiAdb, private val kho: KhoSaoLuu) {

    /**
     * Tên gói Home sạch trên tivi này. Tivi Xiaomi bản Trung Quốc (Home gốc com.mitv.tvhome) chỉ nhận Home có tên trong
     * danh sách của hãng: dùng bản dựng tên com.ts.launcher (upstream docs/devices/xiaomi-mitv-aetp0-dofus.md), trừ khi
     * tivi đã có app khác mang tên đó. 22/10/2026, MiTV-ASTP0: bản com.gathin.tvhome cài được nhưng nút Home ra màn đen.
     */
    private var pkgHomeDaDo: String? = null
    val pkgHome: String get() = pkgHomeDaDo ?: run {
        val cuaMinh = sh("dumpsys package $PKG_HOME_XIAOMI | grep -c '$PKG_HOME_XIAOMI/$NS_HOME.MainActivity'").trim().toIntOrNull()?.let { it > 0 } == true
        val coTen = sh("pm path $PKG_HOME_XIAOMI").isNotBlank()
        val xiaomi = sh("pm list packages com.mitv.tvhome").lines().any { it.trim() == "package:com.mitv.tvhome" }
        if (cuaMinh || (xiaomi && !coTen)) PKG_HOME_XIAOMI else PKG_HOME
    }.also { pkgHomeDaDo = it }

    /** Gọi khi kết nối tivi mới: quên những gì đã dò của tivi trước. */
    fun quenTivi() { pkgHomeDaDo = null; cvteDaDo = null }
    val cmpHome get() = "$pkgHome/$NS_HOME.MainActivity"
    /** File APK trong assets cho pkgHome. */
    val apkHome get() = if (pkgHome == PKG_HOME_XIAOMI) "tvhome-xiaomi.apk" else "tvhome.apk"

    /** Khởi động lại Home để đọc cấu hình; bật lại dịch vụ trợ năng nếu force-stop làm rơi (theme.sh). */
    private val KHOI_DONG_LAI_HOME get() =
            "a=\$(settings get secure enabled_accessibility_services); am force-stop $pkgHome; am start -n $cmpHome >/dev/null; " +
                "case \"\$a\" in *$pkgHome*) sleep 1; settings put secure enabled_accessibility_services ''; settings put secure accessibility_enabled 0; " +
                "sleep 1; settings put secure enabled_accessibility_services \"\$a\"; settings put secure accessibility_enabled 1 ;; esac"


    private fun sh(lenh: String): String = adb.shell(lenh).replace("\r", "")

    // ---------- Đọc ----------

    fun docThongTin(): Pair<ThongTinTv, String> {
        val gp = sh("getprop")
        return ThongTinTv.tuGetprop(gp) to gp
    }

    fun docTrangThai(): TrangThaiGoi {
        fun goi(lenh: String) = sh(lenh).lines().mapNotNull { l ->
            l.trim().removePrefix("package:").takeIf { it.isNotEmpty() && TEN_GOI.matches(it) }
        }.toSet()
        val caiDat = goi("pm list packages")
        val daTat = goi("pm list packages -d")
        val coBieuTuong = (lopActivity("android.intent.category.LEANBACK_LAUNCHER") +
            lopActivity("android.intent.category.LAUNCHER"))
        val banPhim = sh("ime list -s; settings get secure default_input_method").lines()
            .mapNotNull { it.trim().substringBefore('/').takeIf { p -> TEN_GOI.matches(p) } }.toSet()
        return TrangThaiGoi(caiDat, daTat, coBieuTuong, homeHienTai().substringBefore('/'), banPhim)
    }

    /** Các gói có activity thuộc category này (app hiện biểu tượng). */
    private fun lopActivity(cat: String): Set<String> =
        sh("cmd package query-activities --brief -a android.intent.action.MAIN -c $cat").lines()
            .mapNotNull { Regex("""^\s*([A-Za-z0-9_.]+)/""").find(it)?.groupValues?.get(1) }.toSet()

    fun homeHienTai(): String = sh(HOME_QUERY).lines().lastOrNull { it.isNotBlank() }?.trim() ?: ""

    fun chupManHinh(): ByteArray = adb.chupPng()

    // ---------- Máy chiếu ----------

    /** Có dấu hiệu là máy chiếu: tên máy hoặc tên gói kiểu focus / keystone / hãng máy chiếu. */
    fun coVeMayChieu(tt: TrangThaiGoi, tv: ThongTinTv): Boolean =
        TU_MAY_CHIEU.containsMatchIn("${tv.brand} ${tv.manufacturer} ${tv.model} ${tv.device}") ||
            tt.caiDat.any { TU_MAY_CHIEU.containsMatchIn(it) }

    /**
     * Dò các màn (activity) của hãng có tên giống lấy nét / chỉnh hình / thu phóng trong mọi gói hệ thống.
     * Một lệnh shell chạy vòng lặp trên chính máy chiếu cho nhanh.
     */
    fun doMayChieu(): List<String> {
        val loc = "focus|keystone|trapez|tixing|jiaozheng|duijiao|correct|projector|projection|zoom|scale|calibr|geometr|ladder|picturesize|screensize"
        val out = sh("for p in \$(pm list packages -s | cut -d: -f2 | grep -v ^com.google); do dumpsys package \$p | grep -oE \"\$p/[A-Za-z0-9_.\\\$]+\" ; done | sort -u | grep -iE '$loc'; true")
        return out.lines().map { it.trim() }.filter { it.contains('/') && TEN_GOI.matches(it.substringBefore('/')) }
            // Chỉ lấy màn (activity): bỏ dịch vụ, bộ nhận, nhà cung cấp — không mở được bằng am start.
            .filterNot { Regex("(Service|Receiver|Provider)$").containsMatchIn(it) }.distinct().take(40)
    }

    /** Mở thử một màn của hãng; trả lỗi nếu Android từ chối. */
    fun moThu(comp: String): String? {
        val out = sh("am start -n '$comp' 2>&1")
        return if (out.contains("Error") || out.contains("Exception")) out.trim().lines().lastOrNull() else null
    }

    // ---------- Sao lưu ----------

    /** Lấy bản sao lưu đã có của tivi này, chưa có thì chụp trạng thái hiện tại và ghi xuống máy. */
    /**
     * Ghi bản sao lưu vào điện thoại VÀ một bản lên chính tivi ([TEP_SAO_LUU_TV]), để gỡ app / đổi điện thoại vẫn khôi
     * phục được (22/10/2026). Bản trên tivi bỏ getprop + danh sách gói (chỉ để tham khảo) cho nhẹ: ghi lại sau mỗi gói.
     * Ghi file tạm rồi mv, mất kết nối giữa chừng không hỏng bản cũ. Lỗi ghi lên tivi không chặn việc đang làm.
     */
    fun luuSaoLuu(b: BanSaoLuu) {
        kho.ghi(b)
        runCatching {
            val gon = b.copy(getprop = "", goiTruoc = emptyList()).toJson().toString().toByteArray()
            adb.ghiFile("$TEP_SAO_LUU_TV.tam", gon)
            sh("mv -f $TEP_SAO_LUU_TV.tam $TEP_SAO_LUU_TV")
        }
    }

    /** Bản sao lưu nằm trên tivi (do điện thoại nào cũng được ghi), đúng tivi này; null nếu không có / hỏng. */
    fun docSaoLuuTrenTv(khoa: String): BanSaoLuu? = runCatching {
        BanSaoLuu.tuJson(org.json.JSONObject(sh("cat $TEP_SAO_LUU_TV 2>/dev/null").trim()))
    }.getOrNull()?.takeIf { it.khoa == khoa }

    fun damBaoSaoLuu(tv: ThongTinTv, getprop: String, ip: String): BanSaoLuu {
        (kho.doc(tv.khoaLuu) ?: docSaoLuuTrenTv(tv.khoaLuu))?.let { cu -> cu.ip = ip; luuSaoLuu(cu); return cu }
        val tt = docTrangThai()
        val b = BanSaoLuu(
            khoa = tv.khoaLuu, ten = tv.tenHienThi, ip = ip, luc = System.currentTimeMillis(),
            daTatTruoc = tt.daTat, homeTruoc = homeHienTai(),
            dnsModeTruoc = sh("settings get global private_dns_mode").trim(),
            dnsHostTruoc = sh("settings get global private_dns_specifier").trim(),
            truyCapTruoc = sh("settings get secure enabled_accessibility_services").trim(),
            getprop = getprop, goiTruoc = tt.caiDat.sorted(),
        )
        luuSaoLuu(b)
        return b
    }

    // ---------- Tắt gói ----------

    data class KetQuaTat(val daTat: List<String>, val boQua: List<Pair<String, String>>, val loi: List<Pair<String, String>>)

    /**
     * Lọc qua các lớp bảo vệ rồi tắt từng gói một (mỗi lệnh pm một lần gọi, như PROMPT.md dặn).
     * [epSetupwraith]: tivi chạy dây mạng, cho tắt setupwraith dù nó giữ Wi-Fi.
     */
    fun tatGoi(
        goi: List<String>,
        sl: BanSaoLuu,
        epSetupwraith: Boolean,
        bao: (String) -> Unit,
    ): KetQuaTat {
        val boQua = mutableListOf<Pair<String, String>>()
        var ds = goi.filter { TEN_GOI.matches(it) }.distinct()

        // setupwraith: Wi-Fi lưu lúc cài đặt ban đầu thuộc về nó; tắt nó là Android xoá mạng đó và
        // tivi rớt Wi-Fi, mất luôn kết nối này.
        if (SETUP in ds && !epSetupwraith) {
            bao(t("Kiểm tra ai giữ mạng Wi-Fi đã lưu…", "Checking which app owns the saved Wi-Fi…"))
            val chu = sh("dumpsys wifi | grep -o 'cname=[^ ]*' | sort -u")
            val lyDo = when {
                chu.isBlank() -> t("Không đọc được mạng Wi-Fi đã lưu thuộc app nào, giữ lại cho chắc.", "Could not tell which app owns the saved Wi-Fi, keeping it to be safe.")
                chu.contains("setupwraith") -> t("Wi-Fi đang lưu thuộc về nó: tắt là tivi rớt mạng. Vào Cài đặt > Mạng, ", "It owns the saved Wi-Fi: disabling it drops the TV off the network. In Settings > Network, ") +
                    t("quên mạng rồi kết nối lại, sau đó chạy lại.", "forget the network and join it again, then run this again.")
                else -> null
            }
            if (lyDo != null) { boQua += SETUP to lyDo; ds = ds - SETUP }
        }

        // Không tắt app đang làm màn Home khi không còn app nào khác nhận Home (#239).
        val home = homeHienTai().substringBefore('/')
        if (home.isNotEmpty() && home in ds) {
            val khac = sh("cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
                .lines().mapNotNull { Regex("""^\s*([A-Za-z0-9_.]+)/([A-Za-z0-9_.$]+)""").find(it) }
                .filterNot { it.groupValues[2].endsWith("FallbackHome") }
                .map { it.groupValues[1] }.toSet() - ds.toSet() - home - KHONG_PHAI_HOME
            if (khac.isEmpty()) {
                boQua += home to t("Đang là màn Home và chưa có Home nào khác. Cài Home sạch trước rồi mới tắt.", "It is the Home screen and nothing else can take Home. Install the clean Home first.")
                ds = ds - home
            }
        }

        // Gói xin quyền TV_INPUT_HARDWARE là lớp phần mềm dưới cổng HDMI/AV/ăng-ten: tắt là cổng
        // báo "Không có tín hiệu" mãi, bật lại từng gói cũng không cứu được (#321).
        if (ds.isNotEmpty()) {
            bao(t("Kiểm tra gói nào điều khiển cổng HDMI…", "Checking which packages drive the HDMI inputs…"))
            val ten = ds.joinToString(" ")
            val hw = sh(
                "for p in $ten; do dumpsys package \$p | sed -n -E '/requested permissions:/,/(install|runtime) permissions:|User [0-9]+:/p' " +
                    "| grep -q TV_INPUT_HARDWARE && echo \$p; done; true"
            ).lines().map { it.trim() }.filter { it in ds }
            hw.forEach { boQua += it to t("Điều khiển cổng HDMI/AV/ăng-ten (TV_INPUT_HARDWARE): tắt là mất hình.", "Drives the HDMI/AV/tuner inputs (TV_INPUT_HARDWARE): disabling it means no picture.") }
            ds = ds - hw.toSet()
        }

        val daTat = mutableListOf<String>()
        val loi = mutableListOf<Pair<String, String>>()
        val heThong0 = pidHeThong()
        ds.forEachIndexed { i, p ->
            bao(t("Tắt ${i + 1}/${ds.size}: $p", "Disabling ${i + 1}/${ds.size}: $p"))
            var matKetNoi = false
            var out = try {
                sh("pm disable-user --user 0 $p")
            } catch (e: Exception) {
                if (!laMatKetNoi(e)) t("lỗi: ${e.message}", "error: ${e.message}") else { matKetNoi = true; "" }
            }
            // Tivi đang tắt máy: ADB còn trả lời nhưng dịch vụ gói đã chết ("Failure calling service package: Broken pipe").
            if (DICH_VU_CHET.containsMatchIn(out)) matKetNoi = true
            if (matKetNoi) {
                // 22/10/2026, Sony KD-55X8500D: rớt ADB giữa lúc dọn, app báo "connect() must be called first" rồi
                // dừng. Chờ tivi trả lời lại; tivi vừa khởi động lại thì dừng hẳn, gói vừa tắt là nghi phạm.
                bao(t("Mất kết nối với tivi, đang chờ tivi trả lời lại…", "Lost the connection to the TV, waiting for it to answer…"))
                if (!choTiviTraLoi(bao)) throw MatKetNoiTvException(daTat.size, p)
                val daTatRoi = sh("pm list packages -d").lines().any { it.trim() == "package:$p" }
                if (daTatRoi) { daTat += p; if (p !in sl.daTatTruoc) sl.daTatBoiApp += p; luuSaoLuu(sl) }
                val heThong = pidHeThong()
                if (heThong0 != null && heThong != null && heThong != heThong0)
                    throw TiviKhoiDongLaiException(if (daTatRoi) p else daTat.lastOrNull() ?: p, daTat.size)
                out = if (daTatRoi) "new state: disabled-user" else runCatching { sh("pm disable-user --user 0 $p") }.getOrElse { "lỗi: ${it.message}" }
                if (daTatRoi) return@forEachIndexed
            }
            if (out.contains("disabled-user") || out.contains("new state: disabled")) {
                daTat += p
                if (p !in sl.daTatTruoc) sl.daTatBoiApp += p
                luuSaoLuu(sl) // ghi sau MỖI gói: rớt mạng giữa chừng vẫn khôi phục đúng.
            } else {
                loi += p to out.trim().lines().lastOrNull().orEmpty()
            }
        }
        return KetQuaTat(daTat, boQua, loi)
    }

    /**
     * PID của system_server: đổi = tivi vừa khởi động lại, kể cả kiểu chỉ khởi động lại phần mềm (tắt nhầm gói làm
     * system_server chết) mà /proc/uptime không về 0.
     */
    private fun pidHeThong(): String? = runCatching { sh("pidof system_server").trim().ifBlank { null } }.getOrNull()

    /**
     * Chờ tivi trả lời lại tới mức dịch vụ gói chạy được: tối đa 12 lần × 8 giây (~1,5 phút, đủ cho tivi khởi động
     * lại xong). Lệnh lỗi kiểu mất kết nối thì nối lại ADB.
     */
    private fun choTiviTraLoi(bao: (String) -> Unit): Boolean {
        repeat(12) { lan ->
            Thread.sleep(8000)
            bao(t("Chờ tivi, lần ${lan + 1}/12…", "Waiting for the TV, try ${lan + 1}/12…"))
            val ok = try { sh("pm path android").contains("package:") } catch (e: Exception) { runCatching { adb.ketNoiLai() }; false }
            if (ok) return true
        }
        return false
    }

    // ---------- Home sạch (launcher) ----------

    /**
     * Cài launcher và giao màn Home cho nó, theo đúng thứ tự của build-and-install.sh:
     * cài → mở thử → tắt Home gốc → đặt Home → kiểm tra, sai thì trả Home gốc lại.
     */
    fun caiLauncher(
        apk: ByteArray,
        sl: BanSaoLuu,
        cauHinh: String,
        anhNen: ByteArray?,
        boAnh: List<Pair<String, ByteArray>>?,
        nutInput: Boolean,
        nutHome: Boolean,
        bao: (String) -> Unit,
    ): String? {
        val home = homeHienTai().substringBefore('/')
        val homeGoc = when {
            home == pkgHome || home in HOME_SACH -> "none"
            home in HOME_GOC_BIET -> home
            home == "android" -> {
                val ung = sh("cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
                val tim = HOME_GOC_BIET.filter { ung.contains("$it/") }
                if (tim.size == 1) tim[0] else return t("Tivi đang hỏi chọn Home (có ${tim.size} Home gốc). Chọn một Home trên tivi rồi thử lại.", "The TV is asking which Home to use (${tim.size} stock launchers). Pick one on the TV and try again.")
            }
            home == SETUP -> return t("Home đang là trình cài đặt của Google TV (setupwraith). Xem lại phần tắt gói: setupwraith phải tắt cùng Home gốc.", "Home is the Google TV setup wizard (setupwraith). Disable setupwraith together with the stock Home.")
            laHomeHangLa(home) -> home
            // Launcher CÀI THÊM (Dangbei trên tivi nội địa Trung, Projectivy, ATV Launcher…): không thuộc phần mềm gốc,
            // thay được — giữ nó bật, đặt Home sạch làm mặc định; nút Home vẫn về nó thì mới tắt (ghi sao lưu). 22/10/2026.
            laLauncherCaiThem(home) -> home
            else -> return t("Home hiện là '$home', không phải Home gốc app biết. Để an toàn app không đổi.", "Home is '$home', not a stock launcher this app knows. Leaving it alone to be safe.")
        }
        // Home của hãng lạ (Fengmi, máy chiếu, tivi nội địa…): thử giữ nó bật, chỉ đặt Home mặc định + bắt nút Home trên
        // remote; không được mới tắt (21/10/2026, Formovie Nemo: com.tvfengmi.launcher).
        val hangLa = homeGoc != "none" && homeGoc !in HOME_GOC_BIET
        // Bo mạch CVTE: chỉ đặt Home mặc định, không tắt Home gốc, không bật dịch vụ bắt phím cùng lượt (upstream #741).
        val cvte = laBoMachCvte()
        val giuHomeGoc = KeHoach.laHomeCvte(homeGoc)

        bao(t("Đẩy và cài Home sạch (${apk.size / 1024} KB)…", "Pushing and installing the clean Home (${apk.size / 1024} KB)…"))
        var out = caiApkHome(apk, bao)
        if (!out.contains("Success")) {
            // Dự phòng: đẩy file rồi pm install (Android 14 đôi khi treo kiểu cài luồng).
            bao(t("Cài kiểu luồng không được, thử đẩy file…", "Streamed install failed, trying a file push…"))
            adb.ghiFile("/data/local/tmp/tvhome.apk", apk)
            out = sh("pm install -r /data/local/tmp/tvhome.apk; rm -f /data/local/tmp/tvhome.apk")
        }
        if (!out.contains("Success")) {
            return if (out.contains("UPDATE_INCOMPATIBLE"))
                t("Tivi đã có Home sạch ký bằng khoá khác (cài từ máy khác). Gỡ bản cũ trước: pm uninstall $pkgHome.", "The TV has a clean Home signed with another key (installed from another computer). Uninstall it first: pm uninstall $pkgHome.")
            else t("Cài không được: ${out.trim().lines().lastOrNull()}. Home gốc vẫn nguyên.", "Install failed: ${out.trim().lines().lastOrNull()}. The stock Home is untouched.")
        }
        sl.launcherDaCai = true; luuSaoLuu(sl)

        bao(t("Mở thử Home sạch…", "Starting the clean Home…"))
        val start = sh("am start -W -n $cmpHome")
        if (!start.contains("Status: ok") || start.lines().any { it.startsWith("Error") }) {
            return t("Home sạch không mở được, Home gốc vẫn giữ nguyên.\n$start", "The clean Home did not start; the stock Home is untouched.\n$start")
        }
        sh("pm grant $pkgHome android.permission.ACCESS_FINE_LOCATION 2>/dev/null; true")
        bao(t("Tối ưu cho tivi yếu…", "Optimising for slow TVs…"))
        runCatching { sh("cmd package compile -m speed -f $pkgHome") }

        fun tatHomeGoc() {
            bao(t("Tắt Home gốc ($homeGoc)…", "Disabling the stock Home ($homeGoc)…"))
            sh("pm disable-user --user 0 $homeGoc")
            sl.homeGocDaTat = homeGoc
            if (homeGoc !in sl.daTatTruoc) sl.daTatBoiApp += homeGoc
            luuSaoLuu(sl)
        }
        if (homeGoc != "none" && !hangLa && !giuHomeGoc) tatHomeGoc()
        sh("cmd package set-home-activity --user 0 $cmpHome")
        if (hangLa && !giuHomeGoc) {
            // Bắt nút Home trên remote về Home sạch dù hãng có dịch vụ bắt phím riêng (bo CVTE: không, xem trên).
            if (!cvte) batDichVu("HomeKeyService")
            if (!homeHienTai().startsWith("$pkgHome/")) {
                bao(t("Home của hãng vẫn giữ quyền, tắt nó…", "The maker's Home still wins, disabling it…"))
                tatHomeGoc()
                sh("cmd package set-home-activity --user 0 $cmpHome")
            }
        }

        val bayGio = homeHienTai()
        if (!bayGio.startsWith("$pkgHome/") && giuHomeGoc) {
            traHomeGoc(sl)
            return t("Tivi bo mạch CVTE ('${bayGio.ifBlank { "không rõ" }}' vẫn nhận Home). Với loại bo này app không tắt Home gốc vì đã có tivi treo ở màn khởi động sau khi tắt. Tivi vẫn như cũ; Home sạch vẫn mở được từ danh sách ứng dụng.",
                "CVTE board ('${bayGio.ifBlank { "unknown" }}' still claims Home). On this board the app never disables the stock Home: one TV hung on its boot animation after that. The TV is as before; open the clean Home from the app list.")
        }
        if (!bayGio.startsWith("$pkgHome/")) {
            bao(t("Home không chuyển được, trả lại Home gốc…", "Home did not switch, restoring the stock Home…"))
            traHomeGoc(sl)
            return t("Home sau khi đổi là '${bayGio.ifBlank { "không rõ" }}'. Đã trả Home gốc. Trên Google TV thường do ", "Home resolved to '${bayGio.ifBlank { "unknown" }}'. The stock Home is back. On Google TV this usually means ") +
                t("setupwraith cũng nhận Home: cần tắt nó trong danh sách (sau khi kết nối lại Wi-Fi) rồi làm lại.", "setupwraith also claims Home: disable it in the list (after re-joining Wi-Fi) and try again.")
        }

        // Xiaomi: bản com.gathin.tvhome cài từ trước (bị hãng chặn) thì gỡ, để không còn hai Home sạch.
        if (pkgHome != PKG_HOME && sh("pm path $PKG_HOME").isNotBlank()) {
            bao(t("Gỡ Home sạch bản cũ ($PKG_HOME)…", "Removing the old clean Home ($PKG_HOME)…"))
            sh("pm uninstall $PKG_HOME")
        }
        bao(t("Ghi cấu hình dock…", "Writing the dock settings…"))
        dayCauHinh(cauHinh, anhNen, boAnh)
        if (cvte && (nutInput || nutHome)) {
            bao(t("Tivi bo mạch CVTE: chưa bật bắt phím remote lượt này. Tắt mở tivi một lần, thấy Home sạch lên bình thường rồi vào Đổi giao diện bật lại.",
                "CVTE board: remote-key helpers stay off this pass. Restart the TV once and, once the clean Home comes up, turn them on under Change look."))
        } else {
            if (nutInput) batDichVu("InputKeyService")
            if (nutHome) batDichVu("HomeKeyService")
        }
        return null
    }

    /**
     * Cài Home sạch; tivi đã có Home sạch ký khoá khác (dọn bằng CleanYourTV gốc / Claude, mỗi máy dựng một khoá) thì
     * Android chối cài đè → gỡ bản cũ rồi cài lại, trả Home + dịch vụ trợ năng như trước. Cấu hình nằm trong thư mục
     * của app nên mất theo, người gọi tự đẩy lại (21/10/2026, Formovie L206 nhà anh: app báo xong mà Home vẫn bản 2.2).
     */
    private fun caiApkHome(apk: ByteArray, bao: (String) -> Unit = {}): String {
        val out = runCatching { adb.caiApk(apk) }.getOrElse { "Failure ${it.message}" }
        if (!out.contains("UPDATE_INCOMPATIBLE")) return out
        bao(t("Home sạch cũ ký khoá khác, gỡ rồi cài lại…", "The old clean Home has another signature, reinstalling…"))
        val troNang = sh("settings get secure enabled_accessibility_services").trim()
        val laHome = homeHienTai().startsWith("$pkgHome/")
        sh("pm uninstall $pkgHome")
        val lai = runCatching { adb.caiApk(apk) }.getOrElse { "Failure ${it.message}" }
        if (lai.contains("Success")) {
            if (laHome) sh("cmd package set-home-activity --user 0 $cmpHome")
            if (troNang.contains(pkgHome)) sh("settings put secure enabled_accessibility_services '$troNang'; settings put secure accessibility_enabled 1")
        }
        return lai
    }

    /** Cài đè Home sạch bản đi kèm app; bật lại dịch vụ trợ năng mà Android tắt khi cài lại. */
    fun capNhatLauncher(apk: ByteArray): Boolean {
        val truoc = sh("settings get secure enabled_accessibility_services").trim()
        val out = caiApkHome(apk)
        if (truoc.contains(pkgHome)) {
            sh("settings put secure enabled_accessibility_services '$truoc'; settings put secure accessibility_enabled 1")
        }
        return out.contains("Success")
    }

    /**
     * Màn hình chờ cho nút trên Home sạch: bật lại Backdrop (Android TV) / Ambient (Google TV) nếu chính app đã tắt,
     * rồi đặt nó làm màn chờ và bật màn chờ. Gói vốn tắt từ trước khi dọn thì để nguyên.
     */
    fun chuanBiManCho(sl: BanSaoLuu?) {
        val ung = listOf("com.google.android.apps.tv.dreamx", "com.google.android.backdrop")
        // Bật lại cả khi gói bị tắt từ lần dọn bằng công cụ khác (Formovie nhà anh: dreamx tắt sẵn → nút màn chờ im).
        val daTat = sh("pm list packages -d").lines().map { it.substringAfter("package:").trim() }
        for (p in ung) if (p in daTat) {
            sh("pm enable --user 0 $p")
            if (sl != null && p in sl.daTatBoiApp) { sl.daTatBoiApp -= p; luuSaoLuu(sl) }
        }
        val dv = sh("cmd package query-services --brief -a android.service.dreams.DreamService").lines().map { it.trim() }
        val chon = ung.firstNotNullOfOrNull { p -> dv.firstOrNull { it.startsWith("$p/") } }
        if (chon != null) sh("settings put secure screensaver_components $chon")
        sh("settings put secure screensaver_enabled 1")
    }

    /** Ghi config.json cho launcher rồi khởi động lại Home (theo tools/theme.sh). */
    /**
     * Ghi một file theme vào thư mục của Home sạch. ĐI QUA ThemeProvider TRƯỚC (chạy dưới quyền Home sạch nên file thuộc
     * về nó); `cp` của shell chỉ là dự phòng. 22/10/2026: `cp` tạo file mới mang uid shell (2000) — Android 14 cấm Home
     * sạch đọc (log "TvHome.Config: ignoring … EACCES"), theme/dock người dùng chọn không được áp sau lần cài đầu hoặc
     * sau khi gỡ-cài lại Home. File cũ của shell phải xoá trước, không thì provider cũng không ghi đè được.
     */
    private fun ghiFileTheme(ten: String, du: ByteArray) {
        val dir = "/sdcard/Android/data/$pkgHome/files"
        val tam = "/data/local/tmp/cyt-${ten.replace('/', '-')}"
        adb.ghiFile(tam, du)
        if (ten.contains('/')) sh("mkdir -p $dir/${ten.substringBeforeLast('/')} 2>/dev/null; true")
        sh("rm -f $dir/$ten 2>/dev/null; content write --uri content://$pkgHome.theme/$ten < $tam 2>/dev/null || cp $tam $dir/$ten 2>/dev/null; rm -f $tam")
    }

    fun dayCauHinh(json: String, anhNen: ByteArray? = null, boAnh: List<Pair<String, ByteArray>>? = null) {
        val dir = "/sdcard/Android/data/$pkgHome/files"
        val uri = "content://$pkgHome.theme"
        sh("mkdir -p $dir 2>/dev/null; true")
        ghiFileTheme("config.json", json.toByteArray())
        if (anhNen != null) ghiFileTheme("wallpaper.jpg", anhNen)
        else sh("rm -f $dir/wallpaper.jpg 2>/dev/null; content delete --uri $uri/wallpaper.jpg 2>/dev/null; true")
        // Bộ ảnh xoay vòng (wallpapers/): xoá bộ cũ trước, theme không có bộ ảnh thì chỉ xoá.
        sh("rm -rf $dir/wallpapers 2>/dev/null; content delete --uri $uri/wallpapers 2>/dev/null; true")
        boAnh?.forEach { (ten, du) -> ghiFileTheme("wallpapers/$ten", du) }
        ghiFileTheme("theme_revision", java.util.UUID.randomUUID().toString().replace("-", "").toByteArray())
        sh(KHOI_DONG_LAI_HOME)
    }

    /**
     * Home lạ mà app vẫn dám nhận là Home gốc: app HỆ THỐNG, có màn HOME thật, không phải trình cài đặt / Cài đặt /
     * hộp chọn. App bên thứ ba (launcher người dùng tự cài) thì để nguyên.
     */
    /** Kết quả thử nút Home thật: [gianh] = các gói đã tắt vì giành nút Home, [conLai] = gói vẫn giành (không dám tắt). */
    data class KetQuaPhimHome(val ok: Boolean, val gianh: List<String>, val conLai: String, val lyDo: String = "")

    /** config.json đang có trên tivi (để "Đổi giao diện" mở ra đúng lựa chọn cũ); null nếu chưa có / đọc không được. */
    fun docCauHinhTv(): org.json.JSONObject? {
        val s = sh("content read --uri content://$pkgHome.theme/config.json 2>/dev/null || cat /sdcard/Android/data/$pkgHome/files/config.json 2>/dev/null").trim()
        return runCatching { org.json.JSONObject(s) }.getOrNull()
    }

    /** Gói của màn đang hiện trên tivi (đọc cửa sổ đang giữ tiêu điểm — chạy được từ Android 8 tới 14). */
    fun goiDangHien(): String {
        repeat(3) {
            val w = sh("dumpsys window | grep -E 'mCurrentFocus|mFocusedApp' | head -2")
            val pkg = Regex("""([A-Za-z0-9_.]+)/[A-Za-z0-9_.$]+""").find(w)?.groupValues?.get(1) ?: ""
            if (pkg.isNotBlank()) return pkg
            Thread.sleep(800) // đang chuyển màn, tiêu điểm tạm trống
        }
        return ""
    }

    private fun dangBat(pkg: String) = sh("pm list packages -e $pkg").lines().any { it.trim() == "package:$pkg" }

    /**
     * Bấm Home THẬT trên tivi rồi xem màn nào hiện ra (21/10/2026: nhiều người báo cài xong bấm Home lại về màn cũ).
     * Gói hệ thống nào giành nút Home (có màn Home / tên kiểu launcher, không phải Cài đặt/HDMI/setupwraith) thì tắt
     * (ghi vào sao lưu), lặp tối đa 3 lần.
     *
     * 22/10/2026 (bản 1.1 làm tivi TCL Google TV đen màn): KHÔNG THÀNH CÔNG THÌ BẬT LẠI MỌI GÓI VỪA TẮT — tivi phải về
     * đúng như trước khi thử, không bao giờ bị bỏ lại không có Home. Và Home gốc của Google (launcherx/tvlauncher)
     * chỉ được tắt khi setupwraith đã tắt: còn bật thì nó nhận Home, tivi kẹt ở màn trình cài đặt.
     */
    fun damBaoPhimHome(sl: BanSaoLuu?, bao: (String) -> Unit = {}): KetQuaPhimHome {
        val gianh = mutableListOf<String>()
        var lyDo = ""
        fun tra(pkg: String): KetQuaPhimHome {
            if (gianh.isNotEmpty()) {
                bao(t("Không chuyển được nút Home, bật lại ${gianh.joinToString()}…", "Couldn't take over the Home button, re-enabling ${gianh.joinToString()}…"))
                gianh.forEach { p -> sh("pm enable --user 0 $p"); sl?.daTatBoiApp?.remove(p); if (sl?.homeGocDaTat == p) sl.homeGocDaTat = "" }
                sl?.let { luuSaoLuu(it) }
            }
            // Home gốc đã tắt từ bước cài (caiLauncher) mà nút Home vẫn không ra Home sạch: bật lại + đặt làm mặc định.
            // 22/10/2026, Xiaomi MiTV-ASTP0 bản Trung Quốc: hãng chỉ nhận Home theo danh sách tên gói, nút Home ra
            // FallbackHome (com.android.tv.settings) = màn đen, mà app báo "để tivi như cũ" khi Home Xiaomi vẫn tắt.
            if (sl != null && sl.homeGocDaTat.isNotBlank()) {
                bao(t("Bật lại Home gốc (${sl.homeGocDaTat})…", "Re-enabling the stock Home (${sl.homeGocDaTat})…"))
                traHomeGoc(sl)
            }
            if (gianh.isNotEmpty() || sl != null) sh("input keyevent KEYCODE_HOME")
            return KetQuaPhimHome(false, gianh.toList(), pkg, lyDo)
        }
        repeat(4) { lan ->
            sh("cmd package set-home-activity --user 0 $cmpHome")
            sh("input keyevent KEYCODE_HOME")
            Thread.sleep(2500)
            val pkg = goiDangHien()
            // ganChuyenHomeCu (stock_home_redirect) CHƯA bật: thử trên máy ảo Android TV 14 ngày 22/10/2026 chưa thấy chuyển
            // về được — upstream mới thử trên XGIMI thật. Chưa thử chạy được thì không đưa ra cho người dùng.
            if (pkg == pkgHome) return KetQuaPhimHome(true, gianh, "", "")
            if (KeHoach.laHomeCvte(pkg)) {
                lyDo = "cvte"
                return tra(pkg)
            }
            if (lan == 3 || sl == null || pkg == SETUP || !nenTatVuiGianhHome(pkg)) return tra(pkg)
            if (pkg in HOME_GOC_BIET && dangBat(SETUP)) {
                lyDo = "setupwraith"
                return tra(pkg)
            }
            bao(t("Nút Home vẫn mở '$pkg', tắt nó…", "The Home button still opens '$pkg', disabling it…"))
            sh("pm disable-user --user 0 $pkg")
            if (pkg !in sl.daTatTruoc && pkg !in sl.daTatBoiApp) sl.daTatBoiApp += pkg
            // Nhớ là Home gốc: lần "Đổi giao diện" sau ghi đè config.json vẫn gắn lại stock_home_redirect cho nó.
            if (sl.homeGocDaTat.isBlank() && pkg in HOME_GOC_BIET + listOf(sl.homeTruoc.substringBefore('/'))) sl.homeGocDaTat = pkg
            luuSaoLuu(sl)
            gianh += pkg
        }
        return tra(goiDangHien())
    }

    private fun nenTatVuiGianhHome(pkg: String): Boolean {
        if (!TEN_GOI.matches(pkg) || pkg in HOME_SACH || KeHoach.laHomeCvte(pkg) || pkg in KHONG_PHAI_HOME || pkg == "android" || APP_XUAT_XUONG.containsMatchIn(pkg) ||
            pkg.startsWith("com.android.systemui") || pkg.contains("packageinstaller") || pkg.contains("permissioncontroller")) return false
        if (!sh("pm list packages -s $pkg").lines().any { it.trim() == "package:$pkg" }) return laLauncherCaiThem(pkg)
        if (sh("dumpsys package $pkg | grep -c TV_INPUT_HARDWARE").trim().toIntOrNull()?.let { it > 0 } == true) return false
        val coHome = sh("cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
            .lines().any { it.trim().startsWith("$pkg/") }
        return coHome || Regex("launcher|tvhome|home", RegexOption.IGNORE_CASE).containsMatchIn(pkg.substringAfterLast('.').ifBlank { pkg }) ||
            Regex("launcher", RegexOption.IGNORE_CASE).containsMatchIn(pkg)
    }

    private fun laHomeHangLa(pkg: String): Boolean {
        if (!TEN_GOI.matches(pkg) || pkg in KHONG_PHAI_HOME || pkg == "android" || APP_XUAT_XUONG.containsMatchIn(pkg)) return false
        val heThong = sh("pm list packages -s $pkg").lines().any { it.trim() == "package:$pkg" }
        val coHome = sh("cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
            .lines().any { it.trim().startsWith("$pkg/") && !it.trim().endsWith("FallbackHome") }
        return heThong && coHome
    }

    /**
     * Bật dịch vụ trợ năng của Home sạch. TCL (TclAppBoot) không cho bind dịch vụ của app chưa chạy nếu thiếu app-op
     * AUTO_START (upstream #526): cấp trước; dịch vụ đã có trong danh sách thì xoá đi ghi lại để Android bind lại.
     */
    /** App NGƯỜI DÙNG (không phải hệ thống) có màn HOME thật: launcher cài thêm như Dangbei. */
    private fun laLauncherCaiThem(pkg: String): Boolean {
        if (!TEN_GOI.matches(pkg) || pkg in HOME_SACH || pkg in KHONG_PHAI_HOME || APP_XUAT_XUONG.containsMatchIn(pkg)) return false
        if (sh("pm list packages -s $pkg").lines().any { it.trim() == "package:$pkg" }) return false
        return sh("cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
            .lines().any { it.trim().startsWith("$pkg/") }
    }

    /** Tivi dùng bo mạch CVTE (có gói com.cvte.* / cn.cvte.*). Nhớ theo kết nối. */
    private var cvteDaDo: Boolean? = null
    fun laBoMachCvte(): Boolean = cvteDaDo ?: sh("pm list packages | grep -c -E 'package:(com|cn)\\.cvte\\.'").trim()
        .toIntOrNull().let { (it ?: 0) > 0 }.also { cvteDaDo = it }

    private fun batDichVu(ten: String) {
        val svc = "$pkgHome/$NS_HOME.$ten"
        sh("cmd appops set $pkgHome AUTO_START allow 2>/dev/null; s=\$(settings get secure enabled_accessibility_services); case \"\$s\" in *$ten*) settings put secure enabled_accessibility_services ''; settings put secure accessibility_enabled 0; sleep 1; settings put secure enabled_accessibility_services \"\$s\" ;; null|'') settings put secure enabled_accessibility_services $svc ;; *) settings put secure enabled_accessibility_services \"\$s:$svc\" ;; esac; settings put secure accessibility_enabled 1")
    }

    /**
     * stock_home_redirect (upstream #479): hãng tự bật lại Home cũ (XGIMI bật lại launcherx mỗi lần bấm nút app trên
     * remote) thì HomeKeyService thấy màn Home cũ hiện lên là mở lại Home sạch. Ghi khoá vào config.json đang có trên
     * tivi + bật HomeKeyService.
     */
    fun ganChuyenHomeCu(pkg: String) {
        if (!TEN_GOI.matches(pkg)) return
        val dir = "/sdcard/Android/data/$pkgHome/files"
        val cu = sh("content read --uri content://$pkgHome.theme/config.json 2>/dev/null || cat $dir/config.json 2>/dev/null").trim()
        val j = runCatching { org.json.JSONObject(cu) }.getOrNull() ?: return
        if (j.optString("stock_home_redirect") == pkg) { batDichVu("HomeKeyService"); return }
        j.put("stock_home_redirect", pkg)
        ghiFileTheme("config.json", j.toString().toByteArray())
        batDichVu("HomeKeyService")
    }

    private fun traHomeGoc(sl: BanSaoLuu) {
        val goc = sl.homeGocDaTat.ifBlank { sl.homeTruoc.substringBefore('/') }
        if (goc.isBlank()) return
        sh("pm enable --user 0 $goc")
        sh("cmd package set-home-activity --user 0 \$(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME $goc | tail -n 1)")
        sl.daTatBoiApp -= goc; sl.homeGocDaTat = ""; luuSaoLuu(sl)
    }

    // ---------- DNS chặn quảng cáo ----------

    fun batDnsChanQc(sl: BanSaoLuu) {
        sh("settings put global private_dns_specifier dns.adguard-dns.com")
        sh("settings put global private_dns_mode hostname")
        sl.dnsDaDoi = true; luuSaoLuu(sl)
    }

    // ---------- Khôi phục ----------

    fun xoaSaoLuuTrenTv() { runCatching { sh("rm -f $TEP_SAO_LUU_TV") } }

    /** Gói đang bị tắt bằng tay / bằng shell (enabled=3: disable-user), không gồm gói hãng tắt sẵn từ xưởng. */
    fun goiTatBoiNguoiDung(): List<String> =
        sh("for p in \$(pm list packages -d | cut -d: -f2); do dumpsys package \$p | grep -q 'User 0:.*enabled=3' && echo \$p; done")
            .lines().map { it.trim() }.filter { TEN_GOI.matches(it) }.sorted()

    /**
     * Khôi phục khi KHÔNG có bản sao lưu (gỡ app, đổi máy, người khác dọn — 22/10/2026): bật lại mọi gói đang tắt kiểu
     * disable-user (kể cả gói chủ tivi tự tắt trước đó, màn hình đã hỏi xác nhận), bỏ dịch vụ trợ năng của Home sạch,
     * trả DNS AdGuard của app về tự động, đặt lại Home hệ thống của hãng; tuỳ chọn gỡ Home sạch.
     */
    fun khoiPhucKhongSaoLuu(goLauncher: Boolean, bao: (String) -> Unit): List<String> {
        val loi = mutableListOf<String>()
        val ds = goiTatBoiNguoiDung()
        ds.forEachIndexed { i, p ->
            bao(t("Bật lại ${i + 1}/${ds.size}: $p", "Re-enabling ${i + 1}/${ds.size}: $p"))
            val out = runCatching { sh("pm enable --user 0 $p") }.getOrElse { "${it.message}" }
            if (!out.contains("enabled")) loi += "$p: ${out.trim()}"
        }
        val tc = sh("settings get secure enabled_accessibility_services").trim()
        if (HOME_SACH.any { tc.contains("$it/") }) {
            val con = tc.split(':').filter { d -> d.isNotBlank() && HOME_SACH.none { d.startsWith("$it/") } }.joinToString(":")
            if (con.isBlank()) sh("settings delete secure enabled_accessibility_services") else sh("settings put secure enabled_accessibility_services '$con'")
        }
        if (sh("settings get global private_dns_specifier").trim() == "dns.adguard-dns.com") {
            bao(t("Trả DNS về tự động…", "Setting DNS back to automatic…"))
            sh("settings put global private_dns_mode opportunistic; settings delete global private_dns_specifier")
        }
        if (goLauncher) HOME_SACH.forEach { p ->
            if (sh("pm path $p").isNotBlank()) { bao(t("Gỡ Home sạch ($p)…", "Removing the clean Home ($p)…")); sh("pm uninstall $p") }
        }
        // Home hệ thống của hãng: ưu tiên Home gốc app biết, rồi app hệ thống đầu tiên có màn HOME dùng được.
        val heThong = sh("pm list packages -s").lines().map { it.trim().removePrefix("package:") }.toSet()
        val ung = sh("cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
            .lines().map { it.trim() }.filter { it.contains('/') && !it.endsWith("FallbackHome") }
            .filter { c -> val p = c.substringBefore('/'); p in heThong && p !in HOME_SACH && p !in KHONG_PHAI_HOME && !APP_XUAT_XUONG.containsMatchIn(p) }
        val home = HOME_GOC_BIET.firstNotNullOfOrNull { g -> ung.firstOrNull { it.startsWith("$g/") } } ?: ung.firstOrNull()
        if (home != null) {
            bao(t("Đặt Home gốc: $home", "Setting the stock Home: $home"))
            sh("cmd package set-home-activity --user 0 $home")
        }
        sh("input keyevent KEYCODE_HOME")
        return loi
    }

    /** Đưa tivi về như trước: bật lại gói app đã tắt, trả Home gốc, gỡ Home sạch nếu muốn, trả DNS. */
    fun khoiPhuc(sl: BanSaoLuu, goLauncher: Boolean, bao: (String) -> Unit): List<String> {
        val loi = mutableListOf<String>()
        val ds = sl.daTatBoiApp.toList()
        ds.forEachIndexed { i, p ->
            bao(t("Bật lại ${i + 1}/${ds.size}: $p", "Re-enabling ${i + 1}/${ds.size}: $p"))
            val out = runCatching { sh("pm enable --user 0 $p") }.getOrElse { t("lỗi ${it.message}", "error ${it.message}") }
            if (out.contains("enabled")) { sl.daTatBoiApp -= p; luuSaoLuu(sl) } else loi += "$p: ${out.trim()}"
        }
        if (sl.launcherDaCai) {
            bao(t("Trả Home gốc…", "Restoring the stock Home…"))
            val goc = sl.homeTruoc
            if (goc.isNotBlank() && HOME_SACH.none { goc.startsWith("$it/") }) {
                sh("cmd package set-home-activity --user 0 $goc")
            }
            if (goLauncher) {
                bao(t("Gỡ Home sạch…", "Removing the clean Home…"))
                HOME_SACH.filter { it == PKG_HOME || it == pkgHome }.forEach { sh("pm uninstall $it") }
                // Trả danh sách dịch vụ trợ năng như trước khi làm (app có thể đã bật HomeKeyService / InputKeyService).
                val tc = sl.truyCapTruoc
                if (tc.isBlank() || tc == "null") sh("settings delete secure enabled_accessibility_services")
                else sh("settings put secure enabled_accessibility_services '$tc'")
            }
            sl.launcherDaCai = !goLauncher && sl.launcherDaCai
            sl.homeGocDaTat = ""
            luuSaoLuu(sl)
        }
        if (sl.dnsDaDoi) {
            bao(t("Trả DNS như cũ…", "Restoring DNS…"))
            val mode = sl.dnsModeTruoc.takeIf { it.isNotBlank() && it != "null" } ?: "opportunistic"
            sh("settings put global private_dns_mode $mode")
            if (sl.dnsHostTruoc.isBlank() || sl.dnsHostTruoc == "null") sh("settings delete global private_dns_specifier")
            else sh("settings put global private_dns_specifier ${sl.dnsHostTruoc}")
            sl.dnsDaDoi = false; luuSaoLuu(sl)
        }
        return loi
    }

    // ---------- Đóng ADB ----------

    /** Tắt ADB qua mạng trên tivi. Lệnh cuối làm rớt kết nối này, nên lỗi ở đây là bình thường. */
    fun tatAdb() {
        runCatching { sh("settings put global adb_wifi_enabled 0") }
        runCatching { sh("settings put global adb_enabled 0") }
    }

    /** Rớt kết nối lúc dọn mà nối lại không được: [daTat] gói đã tắt (đều có trong sao lưu), đang làm dở [goi]. */
    class MatKetNoiTvException(val daTat: Int, val goi: String) : Exception("mat-ket-noi")
    /** Tivi tự khởi động lại ngay sau khi tắt [goi] (gói tắt xong gần nhất): dừng dọn, [goi] là nghi phạm. */
    class TiviKhoiDongLaiException(val goi: String, val daTat: Int) : Exception("khoi-dong-lai")

    companion object {
        /** Lỗi kiểu kết nối ADB đã đứt (libadb: "connect() must be called first", luồng đóng, mạng reset). */
        fun laMatKetNoi(e: Throwable): Boolean {
            val m = listOfNotNull(e.message, e.cause?.message).joinToString(" ")
            return Regex("must be called first|not connected|Stream closed|closed|Broken pipe|EPIPE|reset|ECONNRESET|Socket", RegexOption.IGNORE_CASE).containsMatchIn(m) ||
                e is java.io.EOFException || e is java.net.SocketException
        }

        /** Dịch vụ hệ thống không trả lời: tivi đang tắt máy / khởi động lại. */
        private val DICH_VU_CHET = Regex("Failure calling service|Can't find service|DeadObjectException|DeadSystemException")

        val TEN_GOI = Regex("""^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$""")
        /** Tên máy / tên gói gợi ý máy chiếu (21/10/2026, Formovie Nemo). */
        val TU_MAY_CHIEU = Regex("projector|formovie|fengmi|xgimi|jmgo|dangbei|nebula|wanbo|byintek|keystone|autofocus|tixing|duijiao", RegexOption.IGNORE_CASE)
        const val PKG_HOME = "com.gathin.tvhome"
        const val PKG_HOME_XIAOMI = "com.ts.launcher"
        /** Bản sao lưu trên tivi: thư mục của shell, còn sau khi gỡ app / Home sạch, chỉ mất khi khôi phục cài đặt gốc. */
        const val TEP_SAO_LUU_TV = "/data/local/tmp/cleantv-sao-luu.json"
        /** Namespace Java của launcher: không đổi theo tên gói. */
        const val NS_HOME = "com.gathin.tvhome"
        val HOME_SACH = setOf(PKG_HOME, PKG_HOME_XIAOMI)
        const val SETUP = "com.google.android.tungsten.setupwraith"
        const val HOME_QUERY = "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
        val HOME_GOC_BIET = listOf(
            "com.google.android.tvlauncher", "com.google.android.apps.tv.launcherx",
            "com.google.android.leanbacklauncher", "com.mitv.tvhome",
        )
        /** Nhận Home nhưng không phải màn Home dùng được. */
        private val KHONG_PHAI_HOME = setOf(SETUP, "com.android.tv.settings", "com.android.settings", "com.android.provision")
        /**
         * App kiểm tra xuất xưởng cũng khai báo HOME (Xiaomi Mi TV 4A: com.duokan.factorytest/.FactoryLauncher) — không bao
         * giờ là Home của hãng, không tắt (theo upstream 0357546, 22/10/2026).
         */
        private val APP_XUAT_XUONG = Regex("factorytest|factory_test|factorymode", RegexOption.IGNORE_CASE)
    }
}
