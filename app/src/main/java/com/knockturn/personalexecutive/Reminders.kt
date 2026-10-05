package com.knockturn.personalexecutive
import android.app.*
import android.content.*
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat

object Reminders {
    private val gapsMin = longArrayOf(5, 15, 30, 60)   // 5m, 15m, 30m, then hourly
    private const val CH_URGENT = "urgent_alarm"; private const val CH_NORMAL = "normal"
    private fun prefs(c: Context) = c.getSharedPreferences("pending", Context.MODE_PRIVATE)

    fun channels(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        val alarm = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
        nm.createNotificationChannel(NotificationChannel(CH_URGENT, "Urgent questions", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), alarm)
            setBypassDnd(true); enableVibration(true); lockscreenVisibility = Notification.VISIBILITY_PUBLIC })
        nm.createNotificationChannel(NotificationChannel(CH_NORMAL, "Questions", NotificationManager.IMPORTANCE_DEFAULT))
    }

    /** Start repeat-until-answered for a question (called from push or when the app finds it unanswered). */
    fun track(c: Context, id: String, text: String, urgent: Boolean) {
        if (prefs(c).contains("q_$id")) return
        prefs(c).edit().putString("q_$id", "$urgent|$text").apply()
        show(c, id, text, urgent); schedule(c, id, 0)
    }
    fun done(c: Context, id: String) {
        prefs(c).edit().remove("q_$id").remove("snz_$id").apply()
        (c.getSystemService(AlarmManager::class.java)).cancel(pi(c, id, 0))
        c.getSystemService(NotificationManager::class.java).cancel(id.hashCode())
    }
    fun clearAll(c: Context) { prefs(c).all.keys.filter { it.startsWith("q_") }.forEach { done(c, it.removePrefix("q_")) } }
    /** Snooze once: keep tracking, but next reminder in 30 min. */
    fun snooze(c: Context, id: String) { prefs(c).edit().putBoolean("snz_$id", true).apply(); schedule(c, id, 0, 30) }
    fun snoozed(c: Context, id: String) = prefs(c).getBoolean("snz_$id", false)
    fun alert(c: Context, id: String, text: String) = show(c, "a$id", text, false)
    private fun quiet(c: Context): Boolean {
        val w = c.getSharedPreferences("work", Context.MODE_PRIVATE)
        val now = java.util.Calendar.getInstance().let { it.get(java.util.Calendar.HOUR_OF_DAY) * 60 + it.get(java.util.Calendar.MINUTE) }
        return now < w.getInt("ws", 540) || now > w.getInt("we", 1080)
    }
    fun isPending(c: Context, id: String) = prefs(c).contains("q_$id")

    fun schedule(c: Context, id: String, step: Int, delayMin: Long? = null) {
        val am = c.getSystemService(AlarmManager::class.java)
        val at = System.currentTimeMillis() + (delayMin ?: gapsMin[minOf(step, gapsMin.size - 1)]) * 60_000
        val pi = pi(c, id, step)
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms())
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }
    private fun pi(c: Context, id: String, step: Int) = PendingIntent.getBroadcast(c, id.hashCode(),
        Intent(c, ReminderReceiver::class.java).putExtra("id", id).putExtra("step", step),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun fire(c: Context, id: String, step: Int) {
        val v = prefs(c).getString("q_$id", null) ?: return        // answered meanwhile -> stop
        val (u, t) = v.split("|", limit = 2)
        if (!u.toBoolean() && quiet(c)) { schedule(c, id, step + 1); return }   // quiet hours: urgent + night check-in still ring
        show(c, id, t, u.toBoolean()); schedule(c, id, step + 1)
    }
    fun rescheduleAll(c: Context) = prefs(c).all.keys.filter { it.startsWith("q_") }.forEach { schedule(c, it.removePrefix("q_"), 0) }

    private fun show(c: Context, id: String, text: String, urgent: Boolean) {
        val open = PendingIntent.getActivity(c, id.hashCode(), Intent(c, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(c, if (urgent) CH_URGENT else CH_NORMAL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(if (urgent) "URGENT: Question from admin" else "Question from admin")
            .setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).setContentIntent(open)
            .setCategory(if (urgent) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_MESSAGE)
        if (urgent) n.setFullScreenIntent(open, true)
        try { c.getSystemService(NotificationManager::class.java).notify(id.hashCode(), n.build()) } catch (_: SecurityException) {}
    }
}
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getStringExtra("id") ?: return
        Reminders.fire(c, id, i.getIntExtra("step", 0))
    }
}
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) { if (i.action == Intent.ACTION_BOOT_COMPLETED) Reminders.rescheduleAll(c) }
}
