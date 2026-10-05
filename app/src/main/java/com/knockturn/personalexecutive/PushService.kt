package com.knockturn.personalexecutive
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/** Server sends HIGH-priority DATA messages with a long TTL: question, night check-in ("home") or blocker alert. */
class PushService : FirebaseMessagingService() {
    override fun onMessageReceived(m: RemoteMessage) {
        val d = m.data; Reminders.channels(this)
        if (d["type"] == "blocker") { Reminders.alert(this, d["question_id"] ?: "blocker", d["text"] ?: "A team member is blocked"); return }
        val id = d["question_id"] ?: return
        Reminders.track(this, id, d["text"] ?: "You have a new question", d["priority"] == "urgent" || d["type"] == "home")
    }
    override fun onNewToken(token: String) {
        getSharedPreferences("fcm", MODE_PRIVATE).edit().putString("token", token).apply()
        Thread { try { Api.saveToken(token) } catch (_: Exception) {} }.start()
    }
}
