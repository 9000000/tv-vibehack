package zone.vgs.tvsach.dulieu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Bộ lập kế hoạch với dữ liệu thật (assets/du-lieu.json, 213 mẫu). 22/10/2026: Mi Box S mất mic, Google Home mất tivi. */
class KeHoachTest {
    private val du = DuLieu.docJson(File("src/main/assets/du-lieu.json").readText())

    private fun tivi(mauId: String, them: Set<String> = emptySet()): Pair<MauTv, List<MucGoi>> {
        val mau = du.mau.first { it.id == mauId }
        val caiDat = mau.goi.map { it.pkg }.toSet() + them
        val tt = TrangThaiGoi(caiDat = caiDat, daTat = emptySet(), coBieuTuong = emptySet(), homeHienTai = "", banPhim = emptySet())
        return mau to KeHoach.lap(du, mau, tt)
    }

    @Test fun miBoxS_khongTichSanKatniss() {
        val (_, ds) = tivi("xiaomi-mi-box-s")
        val k = ds.first { it.pkg == "com.google.android.katniss" }
        assertEquals(LoaiMuc.GIONG_NOI, k.loai)
        assertFalse("katniss không được tích sẵn", k.chonSan)
    }

    @Test fun mediashell_luonNhomGoogleHome_khongTichSan() {
        val (_, ds) = tivi("xiaomi-mi-box-s", setOf("com.google.android.apps.mediashell"))
        ds.filter { it.pkg == "com.google.android.apps.mediashell" }.forEach {
            assertEquals(LoaiMuc.GOOGLE_HOME, it.loai); assertFalse(it.chonSan)
        }
    }

    @Test fun nhomTheoYRieng_khongTichSan_conQuangCaoVanTich() {
        var tichQc = 0; var tongTheoY = 0
        for (m in du.mau) {
            val (_, ds) = tivi(m.id)
            for (x in ds) {
                if (Regex("by choice|personal choice|owner's choice", RegexOption.IGNORE_CASE).containsMatchIn(x.nhom)) {
                    tongTheoY++; assertFalse("${m.id} ${x.pkg} (${x.nhom}) không được tích sẵn", x.chonSan)
                }
                if (Regex("^(ads|telemetry)", RegexOption.IGNORE_CASE).containsMatchIn(x.nhom) && x.chonSan) tichQc++
            }
        }
        println("dòng theo ý riêng: $tongTheoY, dòng quảng cáo/theo dõi vẫn tích sẵn: $tichQc")
        assertTrue(tongTheoY > 500); assertTrue(tichQc > 50)
    }

    /** JVC LT-43N7115 treo màn khởi động sau khi tắt Home CVTE (upstream #741): không mẫu nào được đưa Home CVTE vào danh sách tắt. */
    @Test fun homeCvte_khongBaoGioVaoDanhSachTat() {
        var coTrongDuLieu = 0
        for (m in du.mau) {
            if (m.goi.any { KeHoach.laHomeCvte(it.pkg) }) coTrongDuLieu++
            val (_, ds) = tivi(m.id, setOf("com.cvte.tv.launcher.space", "com.cvte.tv.launcher.highend"))
            assertTrue("${m.id}: ${ds.filter { KeHoach.laHomeCvte(it.pkg) }.map { it.pkg }}", ds.none { KeHoach.laHomeCvte(it.pkg) })
        }
        assertTrue("dữ liệu phải có mẫu ghi tắt Home CVTE để bài thử có nghĩa", coTrongDuLieu >= 2)
        assertTrue(KeHoach.laHomeCvte("com.cvte.tv.launcher.space.skin"))
        assertFalse(KeHoach.laHomeCvte("com.cvte.tv.appstore"))
    }

    @Test fun chieuManHinh_luonNhomChieuManHinh_khongTichSan() {
        val (_, ds) = tivi("xiaomi-mi-box-s", setOf("com.tcl.miracast", "com.xiaomi.wfdsinkhelperservice"))
        val miracast = ds.filter { it.pkg == "com.tcl.miracast" || it.pkg == "com.xiaomi.wfdsinkhelperservice" }
        assertTrue(miracast.isNotEmpty())
        miracast.forEach {
            assertEquals(LoaiMuc.CHIEU_MAN_HINH, it.loai)
            assertFalse("${it.pkg} không được tích sẵn", it.chonSan)
        }
    }

    @Test fun capNhatOta_luonNhomCapNhat_khongTichSan() {
        val (_, ds) = tivi("xiaomi-mi-box-s", setOf("com.google.android.systemupdater", "com.tcl.ota", "com.xiaomi.mitv.upgrade"))
        val ota = ds.filter { it.pkg in setOf("com.google.android.systemupdater", "com.tcl.ota", "com.xiaomi.mitv.upgrade") }
        assertTrue(ota.isNotEmpty())
        ota.forEach {
            assertEquals(LoaiMuc.CAP_NHAT, it.loai)
            assertFalse("${it.pkg} không được tích sẵn", it.chonSan)
        }
    }

    @Test fun factoryTest_khongBaoGioVaoDanhSachTat() {
        val (_, ds) = tivi("xiaomi-mi-box-s", setOf("com.duokan.factorytest", "com.cvte.tv.hkcfac", "mediatek.factorymenu.ui"))
        assertTrue(ds.none { it.pkg.contains("factory") || it.pkg.contains("hkcfac") })
    }
}

