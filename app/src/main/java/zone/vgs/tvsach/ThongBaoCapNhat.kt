package zone.vgs.tvsach

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Báo "có bản mới" bằng thông báo Android cả khi app đang đóng (anh Alex 21/10/2026: "có chuông thông báo trong app
 * báo có update mới mời nâng cấp đc ko"). WorkManager hỏi cap-nhat.json ~12 giờ một lần, mỗi bản chỉ báo một lần.
 */
object ThongBaoCapNhat {
    private const val KENH = "cap-nhat"
    const val MO_CAP_NHAT = "mo_cap_nhat"

    fun hen(ctx: Context) {
        val viec = PeriodicWorkRequestBuilder<KiemTraBanMoi>(12, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(1, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("kiem-tra-ban-moi", ExistingPeriodicWorkPolicy.KEEP, viec)
    }

    fun bao(ctx: Context, b: CapNhat.BanMoi) {
        val p = ctx.getSharedPreferences("ota", Context.MODE_PRIVATE)
        if (p.getInt("da_bao", 0) >= b.versionCode) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(KENH, t("Bản mới của app", "App updates"), NotificationManager.IMPORTANCE_DEFAULT))
        }
        val mo = PendingIntent.getActivity(ctx, 1,
            Intent(ctx, MainActivity::class.java).putExtra(MO_CAP_NHAT, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val tb = NotificationCompat.Builder(ctx, KENH)
            .setSmallIcon(R.drawable.ic_thong_bao)
            .setContentTitle(t("CleanTV ${b.versionName} đã có", "CleanTV ${b.versionName} is out"))
            .setContentText(b.ghiChu.ifBlank { t("Chạm để cập nhật.", "Tap to update.") })
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                (if (b.ghiChu.isNotBlank()) b.ghiChu + "\n" else "") + t("Chạm để cập nhật.", "Tap to update.")))
            .setColor(0xFF8B5CF6.toInt())
            .setContentIntent(mo)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(1, tb) }
        p.edit().putInt("da_bao", b.versionCode).apply()
    }
}

class KiemTraBanMoi(ctx: Context, ts: WorkerParameters) : CoroutineWorker(ctx, ts) {
    override suspend fun doWork(): Result {
        NgonNgu.nap(applicationContext)
        val b = runCatching { CapNhat.kiemTraApp() }.getOrElse { return Result.retry() } ?: return Result.success()
        ThongBaoCapNhat.bao(applicationContext, b)
        return Result.success()
    }
}
