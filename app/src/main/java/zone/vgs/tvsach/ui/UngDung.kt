package zone.vgs.tvsach.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import zone.vgs.tvsach.BuildConfig
import zone.vgs.tvsach.NgonNgu
import zone.vgs.tvsach.R
import zone.vgs.tvsach.dulieu.LoaiMuc
import zone.vgs.tvsach.dulieu.MucGoi
import zone.vgs.tvsach.dulieu.THEMES
import zone.vgs.tvsach.dulieu.HUONG_DAN
import zone.vgs.tvsach.dulieu.THANH_PHO
import zone.vgs.tvsach.dulieu.Theme
import zone.vgs.tvsach.dulieu.tenDep
import zone.vgs.tvsach.t
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------- Màu (gradient tím–hồng như logo) ----------------

private val Tim = Color(0xFF7C3AED)
private val Hong = Color(0xFFEC4899)
private val Xanh = Color(0xFF22C55E)
private val Cam = Color(0xFFF59E0B)
private val Nen = Color(0xFF0B0B10)
private val The1 = Color(0xFF15151C)
private val The2 = Color(0xFF1E1E28)
private val ChuPhu = Color(0xFFA1A1B5)
private val Vien = Color(0xFF2A2A36)
private val Gradient = Brush.linearGradient(listOf(Tim, Hong))
private val NenXam = Brush.linearGradient(listOf(The2, The2))

private val BangMau = darkColorScheme(
    primary = Tim, onPrimary = Color.White, secondary = Hong, background = Nen, surface = The1,
    surfaceVariant = The2, onSurfaceVariant = ChuPhu, outline = Vien,
)

// ---------------- Khung ----------------

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun UngDung(vm: PhienLamViec = viewModel()) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { NgonNgu.nap(ctx) }
    MaterialTheme(colorScheme = BangMau) {
        // Chế độ tivi: đi bằng remote nên ô đang chọn phải thấy rõ — lớp sáng khi focus đậm hơn hẳn mặc định.
        val gon = if (zone.vgs.tvsach.ManHinh.laTv)
            androidx.compose.material3.RippleConfiguration(Color.White, androidx.compose.material.ripple.RippleAlpha(0.3f, 0.35f, 0.3f, 0.2f))
        else androidx.compose.material3.LocalRippleConfiguration.current
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalRippleConfiguration provides gon) {
        Surface(Modifier.fillMaxSize(), color = Nen) {
            BackHandler(enabled = vm.man != Man.CHINH && !vm.dangBan) { quayLai(vm) }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.fillMaxHeight().systemBarsPadding().then(
                if (zone.vgs.tvsach.ManHinh.laTv) Modifier.widthIn(max = 820.dp).padding(top = 12.dp) else Modifier.fillMaxWidth())) {
                ThanhTren(vm)
                buoc(vm.man)?.let { ThanhBuoc(it) }
                Box(Modifier.weight(1f)) {
                    when (vm.man) {
                        Man.CHINH -> ManChinh(vm)
                        Man.KET_NOI -> ManKetNoi(vm)
                        Man.THONG_TIN -> ManThongTin(vm)
                        Man.CHON_GOI -> ManChonGoi(vm)
                        Man.DANG_TAT -> ManDangTat(vm)
                        Man.HOME_SACH -> ManHomeSach(vm)
                        Man.XONG -> ManXong(vm)
                        Man.KHOI_PHUC -> ManKhoiPhuc(vm)
                        Man.HUONG_DAN -> ManHuongDan(vm)
                        Man.GIAY_PHEP -> ManGiayPhep()
                    }
                }
                if (vm.loi.isNotEmpty() || (vm.dangBan && vm.man != Man.DANG_TAT)) ThanhTrangThai(vm)
            }
            }
            vm.banMoi?.takeIf { vm.hoiCapNhat }?.let { b ->
                AlertDialog(
                    onDismissRequest = { vm.hoiCapNhat = false },
                    icon = { Icon(Icons.Rounded.SystemUpdate, null, tint = Hong) },
                    title = { Text(t("Có bản mới ${b.versionName}", "CleanTV ${b.versionName} is out")) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (b.ghiChu.isNotBlank()) Text(b.ghiChu)
                            Phu(t("Bạn đang dùng bản ${BuildConfig.VERSION_NAME}. Cập nhật mất chừng một phút, các tivi đã dọn vẫn giữ nguyên.",
                                "You're on ${BuildConfig.VERSION_NAME}. Updating takes about a minute; your cleaned TVs stay as they are."))
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { vm.hoiCapNhat = false; vm.capNhatApp() }) { Text(t("Cập nhật ngay", "Update now"), fontWeight = FontWeight.SemiBold) }
                    },
                    dismissButton = { TextButton(onClick = { vm.hoiCapNhat = false }) { Text(t("Để sau", "Later")) } },
                    containerColor = The1,
                )
            }
            vm.anhTv?.let { anh ->
                AlertDialog(
                    onDismissRequest = { vm.anhTv = null },
                    confirmButton = { TextButton(onClick = { vm.anhTv = null }) { Text(t("Đóng", "Close")) } },
                    title = { Text(t("Màn hình tivi", "TV screen")) },
                    text = { Image(anh.asImageBitmap(), null, Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))) },
                    containerColor = The1,
                )
            }
        }
    }
    }
}

private fun buoc(m: Man): Int? = when (m) {
    Man.KET_NOI -> 0
    Man.THONG_TIN, Man.CHON_GOI -> 1
    Man.DANG_TAT -> 2
    Man.HOME_SACH, Man.XONG -> 3
    else -> null
}

private fun quayLai(vm: PhienLamViec) {
    vm.loi = ""
    vm.man = when (vm.man) {
        Man.KET_NOI -> { vm.veManChinh(); Man.CHINH }
        Man.THONG_TIN, Man.KHOI_PHUC -> Man.KET_NOI
        Man.CHON_GOI -> Man.THONG_TIN
        Man.DANG_TAT -> Man.CHON_GOI
        Man.HOME_SACH -> vm.manTruocHome
        Man.XONG -> Man.HOME_SACH
        Man.HUONG_DAN, Man.GIAY_PHEP -> vm.manTruoc
        Man.CHINH -> Man.CHINH
    }
}

@Composable
private fun ThanhTren(vm: PhienLamViec) {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (vm.man != Man.CHINH) {
            IconButton(onClick = { if (!vm.dangBan) quayLai(vm) }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t("Quay lại", "Back")) }
        } else Spacer(Modifier.width(12.dp))
        Image(painterResource(R.drawable.logo_cleantv), "CleanTV", Modifier.size(32.dp))
        Spacer(Modifier.width(8.dp))
        Text("Clean", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text("TV", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Hong)
        Spacer(Modifier.weight(1f))
        val ten = vm.thongTin?.tenHienThi
        if (vm.man != Man.CHINH && ten != null) {
            Text(ten, fontSize = 12.sp, color = ChuPhu, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp))
            Spacer(Modifier.width(12.dp))
        } else {
            Surface(onClick = { NgonNgu.doi(ctx) }, shape = RoundedCornerShape(50), color = The2, border = BorderStroke(1.dp, Vien)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Language, null, Modifier.size(16.dp), tint = ChuPhu)
                    Spacer(Modifier.width(6.dp))
                    Text(if (NgonNgu.tiengViet) "VI" else "EN", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.width(8.dp))
        }
    }
}

/** Thanh 4 bước: Kết nối · Chọn · Dọn · Home. */
@Composable
private fun ThanhBuoc(dangO: Int) {
    val ten = listOf(t("Kết nối", "Connect"), t("Chọn", "Choose"), t("Dọn", "Clean"), "Home")
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ten.forEachIndexed { i, s ->
            Column(Modifier.weight(1f)) {
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                    .background(if (i <= dangO) Gradient else Brush.linearGradient(listOf(Vien, Vien))))
                Text(s, fontSize = 11.sp, color = if (i == dangO) Color.White else ChuPhu, modifier = Modifier.padding(top = 4.dp),
                    fontWeight = if (i == dangO) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun ThanhTrangThai(vm: PhienLamViec) {
    val laLoi = vm.loi.isNotEmpty()
    Row(Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(14.dp))
        .background(if (laLoi) Color(0xFF3A1320) else The2).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        if (laLoi) Icon(Icons.Rounded.ErrorOutline, null, tint = Hong)
        else CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text(if (laLoi) vm.loi else vm.thongBao, Modifier.weight(1f), fontSize = 14.sp)
        if (laLoi && vm.man == Man.KET_NOI) TextButton(onClick = { vm.loi = ""; vm.moHuongDan() }) { Text(t("Hướng dẫn", "Guide")) }
        if (laLoi) TextButton(onClick = { vm.loi = "" }) { Text(t("Ẩn", "Hide")) }
    }
}

// ---------------- Mảnh dùng chung ----------------

@Composable
private fun Cuon(noiDung: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = noiDung)
}

@Composable
private fun The(tieuDe: String? = null, icon: ImageVector? = null, noiDung: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(The1).border(1.dp, Vien, RoundedCornerShape(20.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (tieuDe != null) Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, Modifier.size(20.dp), tint = Hong); Spacer(Modifier.width(8.dp)) }
            Text(tieuDe, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        noiDung()
    }
}

@Composable
private fun NutChinh(chu: String, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null, onClick: () -> Unit) {
    val mo = if (enabled) 1f else 0.4f
    Box(modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp)).background(Gradient, alpha = mo)
        .clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, tint = Color.White.copy(alpha = mo)); Spacer(Modifier.width(8.dp)) }
            Text(chu, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White.copy(alpha = mo))
        }
    }
}

@Composable
private fun NutPhu(chu: String, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Vien)) {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(chu, color = Color.White)
    }
}

@Composable
private fun Phu(chu: String, mau: Color = ChuPhu) = Text(chu, fontSize = 13.sp, color = mau, lineHeight = 19.sp)

@Composable
private fun OIcon(icon: ImageVector, size: Dp = 44.dp, nen: Brush = Gradient) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size / 3.2f)).background(nen), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
private fun Chip(chu: String, mau: Color = ChuPhu) {
    Text(chu, fontSize = 12.sp, color = mau, modifier = Modifier.clip(RoundedCornerShape(50)).background(The2).padding(horizontal = 10.dp, vertical = 4.dp))
}

/** Mục gập/mở. */
@Composable
private fun Gap(tieuDe: String, icon: ImageVector, moSan: Boolean = false, noiDung: @Composable ColumnScope.() -> Unit) {
    var mo by rememberSaveable(icon.name) { mutableStateOf(moSan) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(The1).border(1.dp, Vien, RoundedCornerShape(20.dp))) {
        Row(Modifier.fillMaxWidth().clickable { mo = !mo }.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp), tint = Hong); Spacer(Modifier.width(10.dp))
            Text(tieuDe, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Icon(if (mo) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = ChuPhu)
        }
        AnimatedVisibility(mo) {
            Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = noiDung)
        }
    }
}

@Composable
private fun BuocSo(so: Int, chu: String) {
    Row {
        Box(Modifier.size(24.dp).clip(CircleShape).background(The2), contentAlignment = Alignment.Center) {
            Text("$so", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Hong)
        }
        Spacer(Modifier.width(10.dp))
        Text(chu, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
    }
}

/** Màn kết quả lớn ở giữa: icon + tiêu đề + dòng phụ. */
@Composable
private fun DauTrang(icon: @Composable () -> Unit, tieuDe: String, phu: String?) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        icon()
        Text(tieuDe, fontWeight = FontWeight.Black, fontSize = 24.sp, textAlign = TextAlign.Center)
        if (phu != null) Phu(phu)
    }
}

/** Mở hộp góp ý chung của vgs.zone (mục App CleanTV tự chọn theo chữ "CleanTV"), kèm bản app + model tivi. */
@Composable
private fun NutGopY(vm: PhienLamViec) {
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    NutPhu(t("Góp ý / báo lỗi", "Feedback / report a bug"), Icons.Rounded.Feedback) {
        val tu = "CleanTV ${BuildConfig.VERSION_NAME}" + (vm.thongTin?.let { " · ${it.tenHienThi} (${it.device}, Android ${it.android})" } ?: "")
        uri.openUri("https://vgs.zone/gop-y?tu=" + java.net.URLEncoder.encode(tu, "UTF-8"))
    }
}

private fun ngay(ms: Long) = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(ms))

// ---------------- Màn chính ----------------

@Composable
private fun ManChinh(vm: PhienLamViec) = Cuon {
    vm.banMoi?.let { b ->
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Tim.copy(alpha = 0.18f)).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.SystemUpdate, null, tint = Hong); Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(t("Có bản mới ${b.versionName}", "Update ${b.versionName} available"), fontWeight = FontWeight.SemiBold)
                if (b.ghiChu.isNotBlank()) Phu(b.ghiChu)
            }
            TextButton(onClick = { vm.capNhatApp() }, enabled = !vm.dangCapNhat) {
                Text(if (vm.dangCapNhat) t("Đang tải…", "Downloading…") else t("Cập nhật", "Update"))
            }
        }
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
        .background(Brush.verticalGradient(listOf(Tim.copy(alpha = 0.35f), The1))).border(1.dp, Vien, RoundedCornerShape(24.dp)).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(painterResource(R.drawable.logo_cleantv), null, Modifier.size(64.dp))
        Text(t("Tivi hết\nquảng cáo", "No more\nTV ads"), fontWeight = FontWeight.Black, fontSize = 26.sp, lineHeight = 32.sp)
        Phu(t("Kết nối tivi qua Wi-Fi, tắt quảng cáo với app rác của hãng, rồi thay màn Home cho gọn. Muốn trả lại như cũ thì bấm Khôi phục.",
            "Connect to the TV over Wi-Fi, switch off the maker's ads and junk apps, then give it a clean Home. Want it back? Press Restore."))
        NutChinh(t("Dọn một tivi", "Clean a TV"), icon = Icons.Rounded.AutoAwesome) { vm.datLai(); vm.man = Man.KET_NOI }
    }
    TheNhacNpt(vm)
    if (vm.dsSaoLuu.isNotEmpty()) {
        Text(t("Tivi đã dọn", "Cleaned TVs"), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp, start = 4.dp))
        vm.dsSaoLuu.forEach { b ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(The1).border(1.dp, Vien, RoundedCornerShape(18.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                OIcon(Icons.Rounded.Tv, 42.dp, NenXam)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(b.ten, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Phu("${b.ip} · ${ngay(b.luc)}")
                    Phu(t("tắt ${b.daTatBoiApp.size} gói", "${b.daTatBoiApp.size} disabled") + if (b.launcherDaCai) t(" · Home sạch", " · Clean Home") else "")
                }
                TextButton(onClick = { vm.moKhoiPhuc(b) }) {
                    Icon(Icons.Rounded.Restore, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(t("Khôi phục", "Restore"))
                }
            }
        }
    }
    NutGopY(vm)
    TextButton(onClick = { vm.moGiayPhep() }) { Icon(Icons.Rounded.Gavel, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(t("Giấy phép mã nguồn mở", "Open-source licenses")) }
    Phu(t("Dựa trên CleanYourTV (github.com/Gathin23/cleanyourtv, MIT) của Gathin và cộng đồng: 213 danh sách tivi và màn Home sạch là của dự án đó.",
        "Built on CleanYourTV (github.com/Gathin23/cleanyourtv, MIT) by Gathin and contributors: the 213 TV lists and the clean Home come from that project.") +
        "\nCleanTV ${BuildConfig.VERSION_NAME} · AlexVGS")
}

// ---------------- Kết nối ----------------

@Composable
private fun Radar(dangQuet: Boolean) {
    val tr = rememberInfiniteTransition(label = "radar")
    val p by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "p")
    Box(Modifier.size(170.dp).drawBehind {
        val r = size.minDimension / 2
        if (dangQuet) for (k in 0..2) {
            val f = (p + k / 3f) % 1f
            drawCircle(Hong.copy(alpha = (1f - f) * 0.5f), radius = r * (0.35f + 0.65f * f), style = Stroke(2.dp.toPx()))
        }
        drawCircle(Vien, radius = r * 0.98f, style = Stroke(1.dp.toPx()))
        drawCircle(Vien, radius = r * 0.66f, style = Stroke(1.dp.toPx()))
    }, contentAlignment = Alignment.Center) {
        OIcon(Icons.Rounded.Tv, 64.dp)
    }
}

@Composable
private fun ManKetNoi(vm: PhienLamViec) = Cuon {
    if (zone.vgs.tvsach.ManHinh.laTv) { KetNoiTiviNay(vm); return@Cuon }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF3A2A10)).clickable { vm.moHuongDan() }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Warning, null, tint = Cam); Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(t("Tivi đã bật Gỡ lỗi USB chưa?", "Is USB debugging on?"), fontWeight = FontWeight.SemiBold)
            Phu(t("Chưa bật thì app không kết nối được. Bấm để xem hướng dẫn có hình.", "Without it the app can't connect. Tap for the picture guide."), Color.White.copy(alpha = 0.8f))
        }
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Cam)
    }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Radar(vm.dangQuet || vm.dangBan)
        Text(
            when {
                vm.dangKhoiPhuc -> t("Kết nối lại tivi cần khôi phục", "Reconnect the TV to restore")
                vm.dangQuet -> t("Đang tìm tivi trong nhà…", "Looking for TVs…")
                else -> t("Tìm tivi trong nhà", "Find the TV")
            }, fontWeight = FontWeight.Bold, fontSize = 22.sp, textAlign = TextAlign.Center)
        Phu(t("Điện thoại và tivi chung Wi-Fi, tivi đã bật Gỡ lỗi USB.", "Phone and TV on the same Wi-Fi, USB debugging on."))
    }
    NutPhu(if (vm.dangQuet) t("Đang quét…", "Scanning…") else t("Quét mạng tìm tivi", "Scan the network"), Icons.Rounded.WifiFind, enabled = !vm.dangQuet) { vm.quetMang() }
    vm.timThay.forEach { dc ->
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(The1).border(1.dp, Vien, RoundedCornerShape(18.dp)).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            OIcon(Icons.Rounded.Tv, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(dc, fontWeight = FontWeight.SemiBold)
                Phu(t("Android TV · cổng 5555 mở", "Android TV · port 5555 open"))
            }
            Button(onClick = { vm.ip = dc; vm.cong = "5555"; vm.ketNoi() }, enabled = !vm.dangBan, shape = RoundedCornerShape(12.dp)) {
                Text(t("Kết nối", "Connect"))
            }
        }
    }
    Gap(t("Nhập IP tay", "Enter IP manually"), Icons.Rounded.Keyboard, moSan = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(vm.ip, { vm.ip = it.trim() }, label = { Text(t("IP tivi", "TV IP")) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
            OutlinedTextField(vm.cong, { vm.cong = it.filter(Char::isDigit) }, label = { Text(t("Cổng", "Port")) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(100.dp), shape = RoundedCornerShape(12.dp))
        }
        NutChinh(t("Kết nối", "Connect"), enabled = !vm.dangBan) { vm.ketNoi() }
        Phu(t("IP tivi: Cài đặt → Mạng → mạng đang dùng. Lần đầu tivi hỏi \"Cho phép gỡ lỗi?\": tích Luôn cho phép rồi bấm Cho phép.",
            "TV IP: Settings → Network → current network. The first time the TV asks \"Allow debugging?\": tick Always allow and press Allow."))
    }
    Gap(t("Ghép đôi bằng mã (Gỡ lỗi không dây)", "Pair with code (Wireless debugging)"), Icons.Rounded.Link) {
        Phu(t("Trên tivi: Tuỳ chọn nhà phát triển → Gỡ lỗi không dây → Ghép nối thiết bị bằng mã.", "On the TV: Developer options → Wireless debugging → Pair device with pairing code."))
        OutlinedTextField(vm.ghepIpCong, { vm.ghepIpCong = it.trim() }, label = { Text(t("IP:cổng ghép đôi", "Pairing IP:port")) }, placeholder = { Text("192.168.1.20:37123") },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
        OutlinedTextField(vm.ghepMa, { vm.ghepMa = it.filter(Char::isDigit).take(6) }, label = { Text(t("Mã 6 số", "6-digit code")) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
        NutPhu(t("Ghép đôi & kết nối", "Pair & connect"), Icons.Rounded.Link, enabled = !vm.dangBan) { vm.ghepDoi() }
    }
}

/** Chế độ tivi: app chạy ngay trên tivi, kết nối ADB vào chính nó. */
@Composable
private fun ColumnScope.KetNoiTiviNay(vm: PhienLamViec) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OIcon(Icons.Rounded.Tv, 64.dp)
        Text(if (vm.dangKhoiPhuc) t("Kết nối lại để khôi phục tivi này", "Reconnect to restore this TV") else t("Dọn chính tivi này", "Clean this TV"),
            fontWeight = FontWeight.Bold, fontSize = 24.sp, textAlign = TextAlign.Center)
        Phu(t("CleanTV đang chạy ngay trên tivi, không cần điện thoại.", "CleanTV is running on the TV itself, no phone needed."))
    }
    The(t("Trước khi bấm", "Before you start"), Icons.Rounded.Warning) {
        BuocSo(1, t("Tivi đã bật Gỡ lỗi USB (Tùy chọn nhà phát triển).", "USB debugging is on (Developer options)."))
        BuocSo(2, t("Bấm Kết nối. Tivi hiện \"Cho phép gỡ lỗi USB?\": chọn Luôn cho phép rồi bấm Cho phép.", "Press Connect. When the TV asks \"Allow USB debugging?\", tick Always allow and press Allow."))
        BuocSo(3, t("Trong lúc dọn, màn hình có thể nháy sang Home vài giây rồi tự quay lại app.", "While it works the screen may jump to Home for a few seconds and then come back."))
    }
    NutChinh(t("Kết nối tivi này", "Connect this TV"), enabled = !vm.dangBan, icon = Icons.Rounded.Link) { vm.ketNoiTiviNay() }
    NutPhu(t("Hướng dẫn bật Gỡ lỗi USB có hình", "Picture guide to USB debugging"), Icons.Rounded.Warning) { vm.moHuongDan() }
}

// ---------------- Thông tin + chọn mẫu ----------------

@Composable
private fun ManThongTin(vm: PhienLamViec) = Cuon {
    val info = vm.thongTin ?: return@Cuon
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.verticalGradient(listOf(Tim.copy(alpha = 0.3f), The1)))
        .border(1.dp, Vien, RoundedCornerShape(24.dp)).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
        OIcon(Icons.Rounded.Tv, 56.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, null, Modifier.size(16.dp), tint = Xanh); Spacer(Modifier.width(4.dp))
                Text(t("Đã kết nối", "Connected"), fontSize = 12.sp, color = Xanh)
            }
            Text(info.tenHienThi, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("Android ${info.android}")
                vm.trangThai?.let { Chip(t("${it.caiDat.size} gói", "${it.caiDat.size} packages")) }
            }
        }
    }
    if (info.adbSecure == "0") Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF3A2A10)).padding(12.dp)) {
        Icon(Icons.Rounded.Warning, null, tint = Cam); Spacer(Modifier.width(8.dp))
        Phu(t("Tivi mở ADB không cần xác nhận: ai cùng mạng cũng điều khiển được. Nhớ tắt ADB khi xong.",
            "This TV accepts ADB without a prompt: anyone on the network can control it. Turn ADB off when done."), Color.White)
    }
    vm.saoLuu?.let {
        Phu(t("Tivi này đã có bản sao lưu từ ${ngay(it.luc)}", "A backup from ${ngay(it.luc)} exists") +
            (if (vm.saoLuuTuTv) t(" (lấy từ bản lưu trên tivi)", " (read from the copy on the TV)") else "") +
            t(". Làm tiếp vẫn giữ mốc khôi phục cũ.", ". Continuing keeps that restore point."))
    }
    The(t("Danh sách mẫu", "Model list"), Icons.Rounded.FactCheck) {
        if (vm.ungVien.isEmpty()) Phu(t("Chưa có mẫu nào khớp đúng tivi này. App dùng luật chung: chỉ tích sẵn gói đã tắt trên nhiều tivi khác và không ai ghi là phải giữ.",
            "No tested list matches this TV. The app uses general rules: it pre-ticks only packages disabled on many other TVs that nobody marked as needed."))
        vm.ungVien.forEach { (m, diem) ->
            LuaChon(vm.mauChon == m, m.ten.take(90), "${m.goi.size} " + t("gói", "packages") + " · " + t("độ khớp", "match") + " $diem") { vm.mauChon = m }
        }
        LuaChon(vm.mauChon == null, t("Không dùng mẫu, chỉ luật chung", "No list, general rules only"), null) { vm.mauChon = null }
    }
    TheMayChieu(vm)
    NutChinh(t("Xem danh sách gói", "See packages"), enabled = !vm.dangBan, icon = Icons.AutoMirrored.Rounded.ArrowForward) { vm.lapKeHoach() }
    NutPhu(t("Xem màn hình tivi", "View TV screen"), Icons.Rounded.Screenshot) { vm.chupManHinh() }
    if (vm.daCoHomeSach) NutPhu(t("Đổi giao diện Home sạch", "Change the clean Home theme"), Icons.Rounded.Palette) { vm.moDoiTheme() }
    if (vm.saoLuu != null) NutPhu(t("Khôi phục tivi này", "Restore this TV"), Icons.Rounded.Restore) { vm.man = Man.KHOI_PHUC }
    else if (vm.coDauCleanTv) NutPhu(t("Khôi phục (không có sao lưu)", "Restore (no backup)"), Icons.Rounded.Restore) { vm.man = Man.KHOI_PHUC }
}

@Composable
private fun LuaChon(chon: Boolean, chu: String, phu: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (chon) Tim.copy(alpha = 0.18f) else The2)
        .border(1.dp, if (chon) Tim else Color.Transparent, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(chu, fontSize = 14.sp)
            if (phu != null) Phu(phu)
        }
        RadioButton(chon, onClick)
    }
}

// ---------------- Chọn gói ----------------

@Composable
private fun ManChonGoi(vm: PhienLamViec) {
    val nhom = vm.muc.groupBy { it.loai }
    val so = vm.soDaChon()
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(The1).border(1.dp, Vien, RoundedCornerShape(20.dp)).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("$so", fontWeight = FontWeight.Black, fontSize = 40.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(t("gói sẽ tắt", "to disable") + " / ${vm.muc.size}", color = ChuPhu, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    LinearProgressIndicator(progress = { if (vm.muc.isEmpty()) 0f else so.toFloat() / vm.muc.size },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = Hong, trackColor = The2)
                    Phu(t("Đã tích sẵn gói an toàn. App có biểu tượng thì hỏi chủ nhà. Gói tivi cần (HDMI, remote, bàn phím, CH Play) app tự giữ, không hiện ở đây.",
                        "Safe packages are pre-ticked. Ask the owner about apps with icons. Packages the TV needs (HDMI, remote, keyboard, Play Store) are kept and not listed."))
                }
            }
            LoaiMuc.entries.forEach { loai ->
                val ds = nhom[loai].orEmpty()
                if (ds.isEmpty()) return@forEach
                item(key = "h-$loai") {
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(loai.tieuDe, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Phu(loai.moTa)
                        }
                        val tatCa = ds.all { vm.chon[it.pkg] == true }
                        TextButton(onClick = { ds.forEach { vm.chon[it.pkg] = !tatCa } }) {
                            Text(if (tatCa) t("Bỏ hết", "None") else t("Chọn hết", "All"))
                        }
                    }
                }
                items(ds, key = { it.pkg }) { m -> DongGoi(m, vm.chon[m.pkg] == true) { vm.chon[m.pkg] = it } }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(14.dp)).clickable { vm.epSetupwraith = !vm.epSetupwraith }.padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(vm.epSetupwraith, { vm.epSetupwraith = it })
                    Phu(t("Tivi cắm dây LAN (cho tắt setupwraith dù nó giữ Wi-Fi)", "TV is on Ethernet (allow disabling setupwraith even if it owns the Wi-Fi)"))
                }
            }
        }
        var hoi by remember { mutableStateOf(false) }
        Column(Modifier.background(Nen).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NutChinh(t("Sao lưu & dọn $so gói", "Back up & clean $so packages"), enabled = !vm.dangBan && so > 0, icon = Icons.Rounded.CleaningServices) { hoi = true }
            NutPhu(if (vm.daCoHomeSach) t("Bỏ qua, chỉ đổi giao diện", "Skip, just change the theme") else t("Bỏ qua, chỉ cài Home sạch", "Skip, just install the clean Home"),
                Icons.Rounded.Home, enabled = !vm.dangBan) { vm.boQuaDon() }
        }
        if (hoi) AlertDialog(
            onDismissRequest = { hoi = false }, containerColor = The1,
            icon = { Icon(Icons.Rounded.Shield, null, tint = Hong) },
            title = { Text(t("Dọn $so gói?", "Clean $so packages?")) },
            text = { Text(t("App lưu trạng thái tivi vào điện thoại trước, rồi tắt từng gói (không gỡ). Khôi phục được bất cứ lúc nào.",
                "The app saves the TV's state to this phone first, then disables each package (nothing uninstalled). You can restore any time.")) },
            confirmButton = { TextButton(onClick = { hoi = false; vm.batDauTat() }) { Text(t("Dọn", "Clean")) } },
            dismissButton = { TextButton(onClick = { hoi = false }) { Text(t("Thôi", "Cancel")) } },
        )
    }
}

@Composable
private fun DongGoi(m: MucGoi, chon: Boolean, doi: (Boolean) -> Unit) {
    val ten = tenDep(m.pkg)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(The1)
        .border(1.dp, if (chon) Tim.copy(alpha = 0.6f) else Vien, RoundedCornerShape(16.dp)).clickable { doi(!chon) }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(The2), contentAlignment = Alignment.Center) {
            Text(ten.take(1).uppercase(), fontWeight = FontWeight.Bold, color = if (chon) Hong else ChuPhu)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(ten, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
            Text(m.pkg, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = ChuPhu, maxLines = 1)
            val ghi = m.ghiChu.ifBlank { m.nhom }
            if (ghi.isNotBlank()) Text(ghi, fontSize = 12.sp, color = ChuPhu, maxLines = 2, lineHeight = 16.sp)
            if (m.soMauTat > 0 || m.soMauGiu > 0) Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (m.soMauTat > 0) Chip(t("tắt trên ${m.soMauTat} mẫu", "off on ${m.soMauTat} models"))
                if (m.soMauGiu > 0) Chip(t("⚠ ${m.soMauGiu} mẫu nên giữ", "⚠ kept on ${m.soMauGiu}"), Cam)
            }
        }
        Checkbox(chon, doi)
    }
}

// ---------------- Đang dọn / kết quả ----------------

@Composable
private fun ManDangTat(vm: PhienLamViec) = Cuon {
    val kq = vm.ketQua
    if (kq == null) {
        Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
                val p = vm.tienDo
                if (p <= 0f) CircularProgressIndicator(Modifier.fillMaxSize(), strokeWidth = 10.dp, color = Hong, trackColor = The2)
                else CircularProgressIndicator(progress = { p }, modifier = Modifier.fillMaxSize(), strokeWidth = 10.dp, color = Hong, trackColor = The2)
                Text(if (p <= 0f) "…" else "${(p * 100).toInt()}%", fontWeight = FontWeight.Black, fontSize = 34.sp)
            }
            Text(t("Đang dọn tivi", "Cleaning the TV"), fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text(vm.thongBao, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = ChuPhu, textAlign = TextAlign.Center, maxLines = 2)
            Phu(t("Đừng tắt tivi hay đổi Wi-Fi lúc này.", "Don't turn off the TV or change Wi-Fi now."))
        }
    } else {
        DauTrang({
            Box(Modifier.size(84.dp).clip(CircleShape).background(Xanh.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.CheckCircle, null, Modifier.size(52.dp), tint = Xanh)
            }
        }, t("Đã dọn ${kq.daTat.size} gói", "Cleaned ${kq.daTat.size} packages"), null)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Chip(t("${kq.boQua.size} giữ lại", "${kq.boQua.size} kept"), Cam)
            Spacer(Modifier.width(8.dp))
            Chip(t("${kq.loi.size} lỗi", "${kq.loi.size} failed"), if (kq.loi.isEmpty()) ChuPhu else Hong)
        }
        if (kq.boQua.isNotEmpty()) Gap(t("Giữ lại để an toàn (${kq.boQua.size})", "Kept for safety (${kq.boQua.size})"), Icons.Rounded.Shield, moSan = true) {
            kq.boQua.forEach { (p, ly) -> Column { Text(tenDep(p), fontWeight = FontWeight.SemiBold, fontSize = 14.sp); Phu(ly) } }
        }
        if (kq.loi.isNotEmpty()) Gap(t("Không tắt được (${kq.loi.size})", "Could not disable (${kq.loi.size})"), Icons.Rounded.ErrorOutline) {
            kq.loi.forEach { (p, ly) -> Text("$p: $ly", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = ChuPhu) }
            Phu(t("Thường là gói hệ thống được bảo vệ, bỏ qua được.", "Usually protected system packages; safe to ignore."))
        }
        The(t("Thử trên tivi", "Check on the TV"), Icons.Rounded.Tv) {
            Phu(t("Bấm Home, mở YouTube/Netflix, chuyển HDMI, mở Cài đặt, thử remote và giọng nói.", "Press Home, open YouTube/Netflix, switch HDMI, open Settings, try the remote and voice."))
            NutPhu(t("Xem màn hình tivi", "View TV screen"), Icons.Rounded.Screenshot) { vm.chupManHinh() }
        }
        NutChinh(t("Tiếp: Home sạch", "Next: clean Home"), icon = Icons.Rounded.Home) { vm.man = Man.HOME_SACH }
        NutPhu(t("Bỏ qua Home sạch", "Skip clean Home")) { vm.man = Man.XONG }
        NutGopY(vm)
    }
}

// ---------------- Home sạch ----------------

@Composable
private fun ManHomeSach(vm: PhienLamViec) = Cuon {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(t("Home sạch kiểu Apple TV", "Apple TV-style Home"), fontWeight = FontWeight.Black, fontSize = 22.sp)
        Phu(t("Đồng hồ, thời tiết, dock app hay dùng và giao diện tuỳ chọn — không còn hàng quảng cáo.", "Clock, weather, a dock of favourite apps and a theme of your choice — no sponsored rows."))
    }
    The(t("Giao diện", "Theme"), Icons.Rounded.Palette) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            THEMES.forEach { th -> TheTheme(th, th == vm.themeChon) { vm.themeChon = th } }
        }
        Phu(t(vm.themeChon.moTaVi, vm.themeChon.moTaEn))
    }
    The(t("Dock (tối đa $DOCK_TOI_DA, theo thứ tự chạm)", "Dock (up to $DOCK_TOI_DA, in tap order)"), Icons.Rounded.Apps) {
        vm.appChoDock().forEach { p ->
            val viTri = vm.dock.indexOf(p)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (viTri >= 0) Tim.copy(alpha = 0.2f) else The2)
                .border(1.dp, if (viTri >= 0) Tim else Color.Transparent, RoundedCornerShape(14.dp)).clickable { vm.doiDock(p) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(if (viTri >= 0) Gradient else Brush.linearGradient(listOf(Vien, Vien))),
                    contentAlignment = Alignment.Center) {
                    Text(if (viTri >= 0) "${viTri + 1}" else "", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(tenDep(p), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(p, fontSize = 11.sp, color = ChuPhu, fontFamily = FontFamily.Monospace, maxLines = 1)
                }
            }
        }
    }
    TheMayChieu(vm)
    The(t("Tuỳ chọn", "Options"), Icons.Rounded.Tune) {
        OutlinedTextField(vm.tenChao, { vm.tenChao = it }, label = { Text(t("Tên chào (vd: Nhà bác Hùng)", "Greeting name (e.g. The Smiths)")) },
            singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
        Text(t("Thời tiết trên Home", "Weather on Home"), fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChonChip(t("Không hiện", "Off"), vm.thanhPho == null) { vm.thanhPho = null }
            THANH_PHO.forEach { tp -> ChonChip(tp.ten, vm.thanhPho == tp) { vm.thanhPho = tp } }
        }
        Phu(t("Dữ liệu thời tiết: Open-Meteo.com (CC BY 4.0), tivi tự lấy nửa giờ một lần.", "Weather data: Open-Meteo.com (CC BY 4.0), refreshed by the TV every 30 minutes."))
        Hang(t("Âm lịch dưới ngày dương", "Lunar date under the date"), t("Ví dụ: 2/9 âm lịch · Bính Ngọ", "e.g. Lunar 2/9 · Year of the Horse"), vm.amLich) { vm.amLich = it }
        Hang(t("Nút màn hình chờ", "Screensaver button"), t("Bấm là bật ảnh / đồng hồ, tiện khi nghe nhạc", "Starts the photo / clock screensaver, handy with music"), vm.nutManCho) { vm.nutManCho = it }
        Hang(t("Gom nút trên cùng thành icon", "Compact icon buttons"), t("Một cụm gọn, bỏ chữ", "One tidy cluster, no labels"), vm.gonNut) { vm.gonNut = it }
        Hang(t("Nút Input trên remote mở bảng chọn HDMI", "Remote Input button opens the input picker"), t("Nên bật cho Google TV", "Recommended on Google TV"), vm.nutInput) { vm.nutInput = it }
        Hang(t("Remote gửi phím Home lạ", "Remote sends an odd Home key"), t("Que Xiaomi…", "Xiaomi sticks…"), vm.nutHome) { vm.nutHome = it }
        Hang(t("DNS chặn quảng cáo (AdGuard)", "Ad-blocking DNS (AdGuard)"), t("Không chặn được quảng cáo YouTube", "Does not block YouTube ads"), vm.dnsChanQc) { vm.dnsChanQc = it }
    }
    The(t("Tuỳ chỉnh thêm", "More options"), Icons.Rounded.Tune) {
        Hang(t("Hiện tên dưới mọi app", "Names under every app"), t("Ông bà dễ nhận app hơn", "Easier to recognise apps"), vm.tenDuoiApp) { vm.tenDuoiApp = it }
        Hang(t("Thư viện app", "App library"), t("Nút xem mọi app trên tivi, thêm / bớt khỏi Home", "A button listing every app; add or remove from Home"), vm.thuVienApp) { vm.thuVienApp = it }
        Hang(t("Chế độ tivi yếu", "Slow TV mode"), t("Bớt hiệu ứng chuyển động cho tivi cũ, ít RAM", "Calmer motion for old, low-memory TVs"), vm.tiviYeu) { vm.tiviYeu = it }
        Hang(t("Hiệu ứng kính mờ", "Frosted glass effects"), t("Dock kính mờ, ánh sáng theo màu app", "Frosted dock, glow in the app's colour"), vm.hieuUngKinh) { vm.hieuUngKinh = it }
        Hang(t("Hiện CPU / bộ nhớ", "Show CPU / memory"), t("Một dòng nhỏ dưới đồng hồ", "One small line under the clock"), vm.hienThongSo) { vm.hienThongSo = it }
        Text(t("Đồng hồ khi để yên", "Clock when idle"), fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 5, 15, 30).forEach { p -> ChonChip(if (p == 0) t("Tắt", "Off") else t("$p phút", "$p min"), vm.dongHoKhiYen == p) { vm.dongHoKhiYen = p } }
        }
        Text(t("Làm tối hình nền", "Dim the wallpaper"), fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 20, 40).forEach { p -> ChonChip(if (p == 0) t("Không", "None") else "$p%", vm.lamToiNen == p) { vm.lamToiNen = p } }
        }
    }
    MucNangCao(vm)
    if (vm.daCoHomeSach) NutChinh(if (vm.dangBan) t("Đang áp dụng…", "Applying…") else t("Áp dụng giao diện", "Apply theme"),
        enabled = !vm.dangBan, icon = Icons.Rounded.Palette) { vm.apDungTheme() }
    else NutChinh(if (vm.dangBan) t("Đang cài…", "Installing…") else t("Cài Home sạch", "Install clean Home"), enabled = !vm.dangBan, icon = Icons.Rounded.Home) { vm.caiHomeSach() }
    vm.nhatKy.takeLast(4).forEach { Text(it, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = ChuPhu) }
}

/** Mục Nâng cao: mọi tuỳ chọn còn lại của launcher (mô tả tiếng Anh lấy từ tài liệu CleanYourTV). */
@Composable
private fun MucNangCao(vm: PhienLamViec) {
    if (vm.dsNangCao.isEmpty()) return
    var tim by remember { mutableStateOf("") }
    val daDat = vm.nangCao.count { it.value.isNotBlank() }
    Gap(t("Nâng cao · ${vm.dsNangCao.size} tuỳ chọn của Home sạch", "Advanced · ${vm.dsNangCao.size} clean Home options") +
        if (daDat > 0) t(" · đã đặt $daDat", " · $daDat set") else "", Icons.Rounded.Tune) {
        Phu(t("Cho người dùng nâng cao. Để trống = launcher tự quyết. Mô tả lấy nguyên văn từ tài liệu CleanYourTV (tiếng Anh). Nhóm \"Cẩn thận\" đụng tới remote, cổng HDMI, app của hãng: đặt sai có thể làm remote hoặc nguồn vào không chạy — bấm Khôi phục là về như cũ.",
            "For advanced users. Empty = the launcher decides. Descriptions come from the CleanYourTV docs. The \"Careful\" group touches the remote, HDMI inputs and the maker's apps: a wrong value can break them — Restore puts everything back."))
        OutlinedTextField(tim, { tim = it }, label = { Text(t("Tìm tuỳ chọn", "Search options")) }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
        if (daDat > 0) TextButton(onClick = { vm.nangCao.clear() }) { Text(t("Đặt lại tất cả về mặc định", "Reset all to default")) }
        val q = tim.trim().lowercase()
        listOf(false, true).forEach { canThan ->
            val ds = vm.dsNangCao.filter { it.canThan == canThan && (q.isEmpty() || it.khoa.contains(q) || it.moTa.lowercase().contains(q)) }
            if (ds.isEmpty()) return@forEach
            Text(if (canThan) t("⚠ Cẩn thận", "⚠ Careful") else t("Giao diện & hiệu ứng", "Look & effects"),
                fontWeight = FontWeight.Bold, fontSize = 15.sp, color = if (canThan) Cam else Color.White, modifier = Modifier.padding(top = 8.dp))
            ds.forEach { tc -> DongNangCao(tc, vm.nangCao[tc.khoa] ?: "") { v -> if (v.isBlank()) vm.nangCao.remove(tc.khoa) else vm.nangCao[tc.khoa] = v } }
        }
    }
}

@Composable
private fun DongNangCao(tc: zone.vgs.tvsach.dulieu.TuyChon, gt: String, doi: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(The2).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(tc.khoa, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, color = if (gt.isNotBlank()) Hong else Color.White)
        Phu(tc.moTa)
        when (tc.kieu) {
            "bool" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChonChip(t("Mặc định", "Default"), gt.isBlank()) { doi("") }
                ChonChip(t("Bật", "On"), gt == "true") { doi("true") }
                ChonChip(t("Tắt", "Off"), gt == "false") { doi("false") }
            }
            "chon" -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChonChip(t("Mặc định", "Default"), gt.isBlank()) { doi("") }
                tc.chon.forEach { c -> ChonChip(c, gt == c) { doi(c) } }
            }
            else -> OutlinedTextField(gt, { doi(it) }, singleLine = tc.kieu == "so" || tc.kieu == "chu",
                placeholder = { Text(when (tc.kieu) {
                    "so" -> t("Số từ ${tc.min} đến ${tc.max}", "${tc.min} to ${tc.max}")
                    "ds", "ds-so" -> t("Cách nhau bằng dấu phẩy", "Comma-separated")
                    else -> t("Để trống = mặc định", "Empty = default")
                }, fontSize = 13.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = if (tc.kieu == "so") KeyboardType.Number else KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp))
        }
    }
}

@Composable
private fun ChonChip(chu: String, chon: Boolean, onClick: () -> Unit) {
    Text(chu, fontSize = 13.sp, fontWeight = if (chon) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (chon) Tim.copy(alpha = 0.3f) else The2)
            .border(1.dp, if (chon) Tim else Vien, RoundedCornerShape(50)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp))
}

/** Thẻ xem trước theme: ảnh nền thật (nếu có) hoặc dải màu, kèm dock giả. */
@Composable
private fun TheTheme(th: Theme, chon: Boolean, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val anh = remember(th.id) {
        (th.anhNen ?: th.anhXem)?.let { a -> runCatching {
            ctx.assets.open(a).use { android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = 8 }) }
        }.getOrNull()?.asImageBitmap() }
    }
    Column(Modifier.width(132.dp).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(th.mau.map { Color(it) }))
            .border(if (chon) 2.dp else 1.dp, if (chon) Hong else Vien, RoundedCornerShape(12.dp))) {
            if (anh != null) Image(anh, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(4) { i -> Box(Modifier.size(width = 20.dp, height = 12.dp).clip(RoundedCornerShape(3.dp)).background(if (i == 0) Color.White else Color.White.copy(alpha = 0.55f))) }
            }
            Text("5:05", Modifier.align(Alignment.TopStart).padding(8.dp), fontSize = 13.sp, fontWeight = FontWeight.Light)
            if (chon) Icon(Icons.Rounded.CheckCircle, null, Modifier.align(Alignment.TopEnd).padding(6.dp).size(18.dp), tint = Hong)
        }
        Text(t(th.tenVi, th.tenEn), fontSize = 13.sp, fontWeight = if (chon) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp))
    }
}

@Composable
private fun Hang(chu: String, phu: String, bat: Boolean, doi: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { doi(!bat) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(chu, fontSize = 14.sp); Phu(phu) }
        Switch(bat, doi, colors = SwitchDefaults.colors(checkedTrackColor = Tim))
    }
}

// ---------------- Xong ----------------

@Composable
private fun ManXong(vm: PhienLamViec) = Cuon {
    DauTrang({
        Box(Modifier.size(96.dp).clip(RoundedCornerShape(30.dp)).background(Gradient), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(52.dp), tint = Color.White)
        }
    }, t("Tivi đã sạch!", "The TV is clean!"),
        if (vm.homeXong && vm.phimHomeOk) t("Home sạch đang chạy — bấm Home trên remote để xem.", "The clean Home is running — press Home on the remote.")
        else if (vm.homeXong) t("Home sạch đã cài nhưng nút Home trên remote vẫn mở Home cũ (xem lời báo bên dưới).", "The clean Home is installed, but the remote's Home button still opens the old Home (see the note below).")
        else null)
    The(t("Trước khi về", "Before you leave"), Icons.Rounded.Checklist) {
        BuocSo(1, t("Thử lại YouTube, Netflix, HDMI, Cài đặt, remote.", "Try YouTube, Netflix, HDMI, Settings and the remote."))
        BuocSo(2, t("Khởi động lại một lần xem gói nào tự bật lại.", "Reboot once to see if anything re-enables itself."))
        BuocSo(3, t("Tắt ADB: để mở là ai cùng mạng cũng điều khiển được tivi.", "Turn ADB off: left on, anyone on the network can control the TV."))
        NutPhu(t("Xem màn hình tivi", "View TV screen"), Icons.Rounded.Screenshot) { vm.chupManHinh() }
        NutPhu(t("Khởi động lại tivi", "Reboot the TV"), Icons.Rounded.RestartAlt) { vm.khoiDongLaiTv() }
    }
    // Mời vào cộng đồng VGS (22/10/2026, anh Alex duyệt): ?tu=cleantv-app để vgs.zone ghi nguồn đăng ký từ app.
    The(t("Cộng đồng VGS", "The VGS community"), Icons.Rounded.Groups) {
        Phu(t("CleanTV miễn phí, do cộng đồng VGS làm. Tham gia VGSZone để hỏi đáp, nhận bản mới sớm và săn quà công nghệ.",
            "CleanTV is free, made by the VGS community. Join VGSZone for help, early builds and tech giveaways."))
        val uri = androidx.compose.ui.platform.LocalUriHandler.current
        NutPhu(t("Tham gia cộng đồng VGS", "Join the VGS community"), Icons.Rounded.Groups) {
            runCatching { uri.openUri("https://vgs.zone/dang-ky?tu=cleantv-app&next=/cleantv") }
        }
    }
    NutGopY(vm)
    NutChinh(t("Tắt ADB & kết thúc", "Turn off ADB & finish"), enabled = !vm.dangBan, icon = Icons.Rounded.Lock) { vm.tatAdbVaKetThuc() }
    Phu(t("Sau đó trên tivi: Tuỳ chọn nhà phát triển → tắt Gỡ lỗi USB, chọn \"Thu hồi uỷ quyền gỡ lỗi USB\". Bản sao lưu vẫn trong điện thoại để khôi phục.",
        "Then on the TV: Developer options → turn off USB debugging and \"Revoke USB debugging authorisations\". The backup stays on this phone for restoring."))
}

// ---------------- Khôi phục ----------------

@Composable
private fun ManKhoiPhuc(vm: PhienLamViec) = Cuon {
    val sl = vm.saoLuu
    if (vm.khoiPhucXong) {
        DauTrang({ Icon(Icons.Rounded.CheckCircle, null, Modifier.size(72.dp), tint = Xanh) },
            t("Đã khôi phục", "Restored"), t("Tivi đã về như trước khi dọn.", "The TV is back to how it was."))
        NutChinh(t("Tiếp: tắt ADB", "Next: turn off ADB"), icon = Icons.Rounded.Lock) { vm.man = Man.XONG }
        return@Cuon
    }
    if (sl == null) {
        // Không có bản sao lưu cả trên điện thoại lẫn trên tivi: bật lại mọi gói đang tắt, hỏi xác nhận rõ (22/10/2026).
        DauTrang({ OIcon(Icons.Rounded.Restore, 72.dp) }, t("Khôi phục không cần sao lưu", "Restore without a backup"),
            t("Không thấy bản sao lưu của ${vm.thongTin?.tenHienThi} trên điện thoại này lẫn trên tivi.",
                "No backup of ${vm.thongTin?.tenHienThi} on this phone or on the TV."))
        val ds = vm.goiTatKhongSaoLuu
        if (ds == null) {
            The {
                Text(t("App có thể bật lại MỌI gói đang bị tắt trên tivi, gỡ Home sạch và trả Home gốc của hãng. Gói chính bạn tự tắt từ trước cũng sẽ bật lại.",
                    "The app can re-enable EVERY disabled package on the TV, remove the clean Home and bring back the maker's Home. Packages you disabled yourself earlier come back too."))
            }
            NutChinh(if (vm.dangBan) t("Đang đọc…", "Reading…") else t("Xem gói đang tắt", "See disabled packages"), enabled = !vm.dangBan, icon = Icons.Rounded.Search) { vm.docGoiTatKhongSaoLuu() }
            return@Cuon
        }
        var dongY by remember { mutableStateOf(false) }
        var goHomeKsl by remember { mutableStateOf(true) }
        The(t("${ds.size} gói đang tắt", "${ds.size} disabled packages"), Icons.Rounded.Restore) {
            if (ds.isEmpty()) Phu(t("Không có gói nào đang tắt.", "No packages are disabled."))
            ds.take(12).forEach { Text(it, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = ChuPhu) }
            if (ds.size > 12) Phu(t("…và ${ds.size - 12} gói nữa.", "…and ${ds.size - 12} more."))
        }
        The {
            Hang(t("Gỡ luôn Home sạch", "Also uninstall the clean Home"), t("Bỏ chọn để giữ Home sạch trên tivi", "Untick to keep it on the TV"), goHomeKsl) { goHomeKsl = it }
            Hang(t("Tôi hiểu: bật lại tất cả", "I understand: re-enable all"), t("Kể cả gói tôi tự tắt trước khi dùng CleanTV", "Including ones I disabled before using CleanTV"), dongY) { dongY = it }
        }
        NutChinh(if (vm.dangBan) t("Đang khôi phục…", "Restoring…") else t("Khôi phục", "Restore"), enabled = dongY && !vm.dangBan, icon = Icons.Rounded.Restore) {
            vm.chayKhoiPhucKhongSaoLuu(goHomeKsl)
        }
        vm.nhatKy.takeLast(4).forEach { Text(it, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = ChuPhu) }
        return@Cuon
    }
    DauTrang({ OIcon(Icons.Rounded.Restore, 72.dp) }, t("Khôi phục ${sl.ten}", "Restore ${sl.ten}"),
        t("Bật lại ${sl.daTatBoiApp.size} gói", "Re-enable ${sl.daTatBoiApp.size} packages") +
            (if (sl.launcherDaCai) t(", trả Home gốc", ", restore the stock Home") else "") +
            (if (sl.dnsDaDoi) t(", trả DNS", ", restore DNS") else "") + ".")
    var goHome by remember { mutableStateOf(true) }
    if (sl.launcherDaCai) The {
        Hang(t("Gỡ luôn Home sạch", "Also uninstall the clean Home"), t("Bỏ chọn để giữ Home sạch trên tivi", "Untick to keep it on the TV"), goHome) { goHome = it }
    }
    NutChinh(if (vm.dangBan) t("Đang khôi phục…", "Restoring…") else t("Khôi phục", "Restore"), enabled = !vm.dangBan, icon = Icons.Rounded.Restore) {
        vm.chayKhoiPhuc(sl.launcherDaCai && goHome)
    }
    vm.nhatKy.takeLast(4).forEach { Text(it, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = ChuPhu) }
}

// ---------------- Nhắc + hướng dẫn chế độ nhà phát triển ----------------

/** Thẻ nổi bật ở màn chính: việc BẮT BUỘC làm trên tivi trước khi dùng app. */
@Composable
private fun TheNhacNpt(vm: PhienLamViec) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFF2A1F0C))
        .border(1.5.dp, Cam.copy(alpha = 0.6f), RoundedCornerShape(20.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Warning, null, tint = Cam); Spacer(Modifier.width(8.dp))
            Text(t("Trước tiên: bật chế độ nhà phát triển trên tivi", "First: turn on developer mode on the TV"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Phu(t("Không có bước này app không kết nối được tivi. Làm một lần, khoảng 1 phút: Cài đặt → Giới thiệu → bấm Bản dựng 7 lần → bật Gỡ lỗi qua USB.",
            "The app can't reach the TV without this. One time, about a minute: Settings → About → press Build 7 times → turn on USB debugging."), Color.White.copy(alpha = 0.85f))
        Box(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)).background(Cam).clickable { vm.moHuongDan() }, contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.MenuBook, null, tint = Color.Black); Spacer(Modifier.width(8.dp))
                Text(t("Xem hướng dẫn có hình (7 bước)", "Picture guide (7 steps)"), fontWeight = FontWeight.Bold, color = Color.Black)
            }
        }
        Phu(t("Chỉ Android TV / Google TV. Samsung (Tizen), LG (webOS) không làm được.", "Android TV / Google TV only. Samsung (Tizen) and LG (webOS) are not supported."))
    }
}

@Composable
private fun ManHuongDan(vm: PhienLamViec) {
    val ctx = LocalContext.current
    val thuMuc = if (NgonNgu.tiengViet) "vi" else "en"
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(t("Bật chế độ nhà phát triển", "Turn on developer mode"), fontWeight = FontWeight.Black, fontSize = 24.sp)
                Phu(t("Cầm remote, làm lần lượt 7 bước. Ảnh chụp từ Android TV — tên mục có thể khác chút tuỳ hãng (Hệ thống / Giới thiệu / Thông tin…).",
                    "Grab the remote and follow the 7 steps. Pictures are from Android TV — names may differ slightly by brand (System / About / Information…)."))
            }
        }
        itemsIndexed(HUONG_DAN) { i, b ->
            val anh = remember(thuMuc, b.anh) {
                runCatching { ctx.assets.open("huongdan/$thuMuc/${b.anh}.webp").use { android.graphics.BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull()
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(The1).border(1.dp, Vien, RoundedCornerShape(20.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(30.dp).clip(CircleShape).background(Gradient), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(t(b.tieuDeVi, b.tieuDeEn), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                if (anh != null) Image(anh, null, Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, Vien, RoundedCornerShape(12.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.FillWidth)
                Text(t(b.chuVi, b.chuEn), fontSize = 14.sp, lineHeight = 21.sp)
            }
        }
        item {
            The(t("Muốn đổi giao diện sau này", "Changing the look later"), Icons.Rounded.Palette) {
                BuocSo(1, t("Trên tivi bật lại \"Gỡ lỗi qua USB\" (Tùy chọn nhà phát triển vẫn còn, không phải bấm Bản dựng nữa).",
                    "On the TV turn \"USB debugging\" back on (Developer options stays, no need to press Build again)."))
                BuocSo(2, t("Mở app → Dọn một tivi → kết nối lại tivi đó. Đã tích \"Luôn cho phép\" thì tivi không hỏi lại.",
                    "Open the app → Clean a TV → connect to that TV again. If you ticked \"Always allow\" the TV won't ask again."))
                BuocSo(3, t("Bấm \"Đổi giao diện Home sạch\", chọn theme, dock, thời tiết, tên chào → Áp dụng giao diện. Các gói đã tắt giữ nguyên, không phải dọn lại.",
                    "Tap \"Change the clean Home theme\", pick a theme, dock, weather and greeting → Apply theme. Disabled packages stay as they are."))
                BuocSo(4, t("Xong thì bấm Tắt ADB như lần đầu.", "When done, press Turn off ADB as before."))
                Phu(t("Chỉnh nhanh không cần điện thoại: trên tivi giữ OK ở nút Cài đặt của Home sạch để đổi số cột, hiệu ứng, sắp xếp app (theme và hình nền vẫn phải đổi qua app).",
                    "Quick tweaks without the phone: on the TV hold OK on the clean Home's Settings button to change columns, motion and app order (themes and wallpapers still need the app)."))
            }
        }
        item {
            The(t("Dọn xong thì tắt lại", "Turn it off when you're done"), Icons.Rounded.Lock) {
                Phu(t("Bước cuối trong app sẽ tắt ADB. Cho chắc, vào lại \"Tùy chọn nhà phát triển\": tắt \"Gỡ lỗi qua USB\" và chọn \"Thu hồi các lượt ủy quyền gỡ lỗi qua USB\".",
                    "The app's last step turns ADB off. To be sure, go back to \"Developer options\": turn off \"USB debugging\" and choose \"Revoke USB debugging authorizations\"."))
            }
        }
        item {
            NutChinh(t("Đã bật xong — kết nối tivi", "Done — connect to the TV"), icon = Icons.Rounded.WifiFind) { vm.man = Man.KET_NOI }
        }
    }
}

@Composable
private fun ManGiayPhep() {
    val ctx = LocalContext.current
    val chu = remember { runCatching { ctx.assets.open("giay-phep.txt").bufferedReader().readText() }.getOrDefault("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(t("Giấy phép mã nguồn mở", "Open-source licenses"), fontWeight = FontWeight.Black, fontSize = 22.sp)
        Text(chu, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 16.sp, color = ChuPhu)
    }
}

// ---------------- Máy chiếu ----------------

/**
 * Dò màn lấy nét / chỉnh hình của hãng, mở thử trên máy chiếu, ghim vào nút "Máy chiếu" trên Home sạch.
 * Tivi thường không hiện thẻ này, trừ khi bấm dòng "Máy chiếu?" nhỏ.
 */
@Composable
private fun TheMayChieu(vm: PhienLamViec) {
    var moTay by rememberSaveable { mutableStateOf(false) }
    if (!vm.laMayChieu && vm.mayChieu == null && !moTay) {
        TextButton(onClick = { moTay = true }) {
            Icon(Icons.Rounded.Videocam, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
            Text(t("Đây là máy chiếu? Thêm nút lấy nét, chỉnh hình", "Is this a projector? Add focus / keystone buttons"))
        }
        return
    }
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    val clip = androidx.compose.ui.platform.LocalClipboardManager.current
    The(t("Máy chiếu: lấy nét, chỉnh hình", "Projector: focus & keystone"), Icons.Rounded.Videocam) {
        Phu(t("App dò các màn chỉnh của hãng trên máy chiếu. Bấm Mở thử để xem đúng màn chưa, rồi Ghim: Home sạch sẽ có nút \"Máy chiếu\" mở thẳng các màn đó. Gói lo lấy nét, chỉnh hình không bao giờ bị tắt.",
            "The app looks for the maker's adjustment screens. Tap Try to check one, then Pin it: the clean Home gets a \"Projector\" button that opens them. Focus / keystone packages are never disabled."))
        val ds = vm.mayChieu
        if (ds == null) {
            NutPhu(if (vm.dangDoMayChieu) t("Đang dò… (khoảng 30 giây)", "Scanning… (about 30 seconds)") else t("Dò màn lấy nét / chỉnh hình", "Find focus / keystone screens"),
                Icons.Rounded.Search, enabled = !vm.dangDoMayChieu) { vm.doMayChieu() }
        } else {
            ds.forEach { comp ->
                val ghim = comp in vm.ghimMayChieu
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (ghim) Tim.copy(alpha = 0.2f) else The2)
                    .border(1.dp, if (ghim) Tim else Color.Transparent, RoundedCornerShape(14.dp)).padding(12.dp)) {
                    Text(vm.ghimMayChieu[comp] ?: vm.nhanMayChieu(comp), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(comp, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = ChuPhu, maxLines = 2)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        TextButton(onClick = { vm.moThuMayChieu(comp) }, enabled = !vm.dangBan) { Text(t("Mở thử", "Try")) }
                        TextButton(onClick = { vm.doiGhim(comp) }) { Text(if (ghim) t("Bỏ ghim", "Unpin") else t("Ghim", "Pin")) }
                    }
                }
            }
            if (vm.ghimMayChieu.isNotEmpty()) Phu(t("Đã ghim ${vm.ghimMayChieu.size}. Bấm Cài Home sạch / Áp dụng giao diện để đưa lên máy chiếu.",
                "${vm.ghimMayChieu.size} pinned. Install the clean Home / Apply theme to send them to the projector."), Xanh)
        }
        NutPhu(t("Gửi danh sách cho VGS", "Send the list to VGS"), Icons.Rounded.Feedback) {
            clip.setText(androidx.compose.ui.text.AnnotatedString(vm.chuDanhSachMayChieu()))
            val tu = "CleanTV máy chiếu · " + (vm.thongTin?.tenHienThi ?: "")
            uri.openUri("https://vgs.zone/gop-y?tu=" + java.net.URLEncoder.encode(tu, "UTF-8"))
        }
        Phu(t("Danh sách đã chép sẵn — dán vào ô nội dung trong hộp góp ý.", "The list is copied — paste it into the feedback box."))
    }
}
