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
}
