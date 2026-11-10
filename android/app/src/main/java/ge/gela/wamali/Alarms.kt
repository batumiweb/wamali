package ge.gela.wamali

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.Calendar

object Alarms {
    const val CHANNEL = "wamali_alarm_v1"
    const val EXTRA_MED = "med"
    const val EXTRA_IDX = "idx"
    const val EXTRA_AT = "at"
    const val ACTION_TAKEN = "ge.gela.wamali.TAKEN"
    const val ACTION_SNOOZE = "ge.gela.wamali.SNOOZE"
    private const val IDX_SNOOZE = 999
    private const val IDX_TEST = 998

    private fun am(ctx: Context): AlarmManager = ctx.getSystemService(AlarmManager::class.java)
    private fun rc(medId: String, idx: Int) = medId.hashCode() * 31 + idx
    fun notifId(medId: String) = medId.hashCode() and 0x7fffffff

    fun canExact(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || am(ctx).canScheduleExactAlarms()

    private fun alarmPi(ctx: Context, medId: String, idx: Int, scheduled: Long): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java)
            .putExtra(EXTRA_MED, medId).putExtra(EXTRA_IDX, idx).putExtra(EXTRA_AT, scheduled)
        return PendingIntent.getBroadcast(
            ctx, rc(medId, idx), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun set(ctx: Context, at: Long, pi: PendingIntent) {
        val show = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        try {
            if (canExact(ctx)) am(ctx).setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
            else am(ctx).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            am(ctx).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun nextAt(t: DoseTime, days: Set<Int>, after: Long): Long? {
        val c = Calendar.getInstance().apply {
            timeInMillis = after
            set(Calendar.HOUR_OF_DAY, t.hour); set(Calendar.MINUTE, t.minute)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        repeat(8) {
            if (c.timeInMillis > after && c.get(Calendar.DAY_OF_WEEK) in days) return c.timeInMillis
            c.add(Calendar.DAY_OF_YEAR, 1)
        }
        return null
    }

    fun schedule(ctx: Context, m: Medication) {
        cancel(ctx, m)
        if (!m.active || m.weekdays.isEmpty()) return
        val after = System.currentTimeMillis() + 5_000
        m.times.forEachIndexed { i, t ->
            nextAt(t, m.weekdays, after)?.let { set(ctx, it, alarmPi(ctx, m.id, i, it)) }
        }
    }

    fun scheduleSlot(ctx: Context, m: Medication, idx: Int) {
        val t = m.times.getOrNull(idx) ?: return
        if (!m.active) return
        nextAt(t, m.weekdays, System.currentTimeMillis() + 60_000)?.let { set(ctx, it, alarmPi(ctx, m.id, idx, it)) }
    }

    fun cancel(ctx: Context, m: Medication) {
        for (i in 0 until maxOf(m.times.size, 12)) am(ctx).cancel(alarmPi(ctx, m.id, i, 0))
    }

    fun scheduleAll(ctx: Context) {
        MedStore.meds.toList().forEach { schedule(ctx, it) }
    }

    fun snooze(ctx: Context, medId: String, scheduled: Long, minutes: Int = 5) {
        set(ctx, System.currentTimeMillis() + minutes * 60_000L, alarmPi(ctx, medId, IDX_SNOOZE, scheduled))
    }

    fun test(ctx: Context, medId: String) {
        val at = System.currentTimeMillis() + 10_000
        set(ctx, at, alarmPi(ctx, medId, IDX_TEST, at))
    }

    // ---- შეტყობინება, რომელიც რეკავს და სრულ ეკრანს ხსნის

    private fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) != null) return
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val ch = NotificationChannel(CHANNEL, "წამლის მაღვიძარა", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(
                sound,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 800, 600, 800, 600, 800)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(ch)
    }

    fun showAlarm(ctx: Context, medId: String, scheduled: Long) {
        val m = MedStore.med(medId) ?: return
        ensureChannel(ctx)
        val id = notifId(medId)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val full = Intent(ctx, RingingActivity::class.java)
            .putExtra(EXTRA_MED, medId).putExtra(EXTRA_AT, scheduled)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        val fullPi = PendingIntent.getActivity(ctx, id, full, flags)

        val takenPi = PendingIntent.getBroadcast(
            ctx, id + 1,
            Intent(ctx, ActionReceiver::class.java).setAction(ACTION_TAKEN)
                .putExtra(EXTRA_MED, medId).putExtra(EXTRA_AT, scheduled),
            flags
        )
        val snoozePi = PendingIntent.getBroadcast(
            ctx, id + 2,
            Intent(ctx, ActionReceiver::class.java).setAction(ACTION_SNOOZE)
                .putExtra(EXTRA_MED, medId).putExtra(EXTRA_AT, scheduled),
            flags
        )

        val text = listOf(m.dose, m.note).filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { "წამლის დროა" }
        val b = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle("💊 ${m.name}")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullPi, true)
            .setContentIntent(fullPi)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(0, "✓ მივიღე", takenPi)
            .addAction(0, "⏰ 5 წუთში", snoozePi)
        MedStore.loadPhoto(m)?.let { b.setLargeIcon(it.fit(256)) }

        val n = b.build()
        n.flags = n.flags or Notification.FLAG_INSISTENT   // ხმა მეორდება, სანამ არ უპასუხებ
        try {
            NotificationManagerCompat.from(ctx).notify(id, n)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun dismiss(ctx: Context, medId: String) {
        NotificationManagerCompat.from(ctx).cancel(notifId(medId))
    }

    fun taken(ctx: Context, medId: String, at: Long) {
        MedStore.record(TAKEN, medId, at)
        dismiss(ctx, medId)
    }

    fun snoozeAction(ctx: Context, medId: String, at: Long) {
        MedStore.record(SNOOZED, medId, at)
        dismiss(ctx, medId)
        snooze(ctx, medId, at)
    }

    fun skip(ctx: Context, medId: String, at: Long) {
        MedStore.record(SKIPPED, medId, at)
        dismiss(ctx, medId)
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        MedStore.init(ctx)
        val medId = intent.getStringExtra(Alarms.EXTRA_MED) ?: return
        val idx = intent.getIntExtra(Alarms.EXTRA_IDX, -1)
        val at = intent.getLongExtra(Alarms.EXTRA_AT, System.currentTimeMillis())
        val m = MedStore.med(medId) ?: return
        if (idx in 0..99) Alarms.scheduleSlot(ctx, m, idx)
        Alarms.showAlarm(ctx, medId, at)
    }
}

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        MedStore.init(ctx)
        val medId = intent.getStringExtra(Alarms.EXTRA_MED) ?: return
        val at = intent.getLongExtra(Alarms.EXTRA_AT, System.currentTimeMillis())
        when (intent.action) {
            Alarms.ACTION_TAKEN -> Alarms.taken(ctx, medId, at)
            Alarms.ACTION_SNOOZE -> Alarms.snoozeAction(ctx, medId, at)
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        MedStore.init(ctx)
        Alarms.scheduleAll(ctx)
    }
}
