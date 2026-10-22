package zone.vgs.tvsach

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import zone.vgs.tvsach.ui.UngDung

class MainActivity : ComponentActivity() {
    private val xinThongBao = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Conscrypt cho TLS của Gỡ lỗi không dây (ghép đôi + kết nối).
        runCatching { java.security.Security.insertProviderAt(org.conscrypt.Conscrypt.newProvider(), 1) }
        ManHinh.doc(this)
        CapNhat.donOtaCuNeuDoiBan(this)
        Dem.moApp(this)
        docYDinh(intent)
        ThongBaoCapNhat.hen(this)
        // Android 13+: xin quyền thông báo một lần để báo được khi có bản mới.
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            val p = getSharedPreferences("ota", MODE_PRIVATE)
            if (!p.getBoolean("da_xin_tb", false)) {
                p.edit().putBoolean("da_xin_tb", true).apply()
                xinThongBao.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        enableEdgeToEdge()
        setContent { UngDung() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        docYDinh(intent)
    }

    private fun docYDinh(i: Intent?) {
        if (i?.getBooleanExtra(ThongBaoCapNhat.MO_CAP_NHAT, false) == true) {
            CapNhat.moTuThongBao = true
            i.removeExtra(ThongBaoCapNhat.MO_CAP_NHAT)
        }
    }
}
