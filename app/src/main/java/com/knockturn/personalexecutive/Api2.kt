package com.knockturn.personalexecutive
import org.json.JSONArray
import org.json.JSONObject

data class Task(val id: String, val title: String)
object Api2 {
    private fun rows(s: String) = JSONArray(s).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
    private val uid get() = Api.userId
    fun tasks() = rows(Api.call("/rest/v1/pms_tasks?assignee_id=eq.$uid&status=neq.done&select=id,title")).map { Task(it.getString("id"), it.getString("title")) }
    fun savePlan(text: String, ids: List<String>) = Api.call("/rest/v1/plans?on_conflict=user_id,plan_date", "POST",
        JSONObject().put("user_id", uid).put("plan_text", text).put("plan_task_ids", JSONArray(ids)), upsert = true)
    /** Saves the update and syncs status/progress back to PMS. */
    fun taskUpdate(t: Task, status: String, pct: Int, note: String) {
        Api.call("/rest/v1/task_updates", "POST", JSONObject().put("user_id", uid).put("task_id", t.id).put("status", status).put("percent", pct).put("note", note))
        val pms = when (status) { "Done" -> "done"; "Blocked" -> "blocked"; else -> "in_progress" }
        Api.call("/rest/v1/pms_tasks?id=eq.${t.id}", "PATCH", JSONObject().put("status", pms).put("progress", if (status == "Done") 100 else pct))
    }
    fun blocker(note: String) = Api.call("/rest/v1/blockers", "POST", JSONObject().put("user_id", uid).put("note", note))
    fun reachedHome() = Api.call("/rest/v1/checkins?on_conflict=user_id,checkin_date", "POST", JSONObject().put("user_id", uid), upsert = true)
    fun mood(n: Int) = Api.call("/rest/v1/plans?user_id=eq.$uid&plan_date=eq.${java.time.LocalDate.now()}", "PATCH", JSONObject().put("mood", n))
    fun seen(qid: String) = Api.call("/rest/v1/reminders?question_id=eq.$qid&user_id=eq.$uid", "PATCH", JSONObject().put("status", "seen"))
    fun snooze(qid: String, reason: String) = Api.call("/rest/v1/snoozes", "POST", JSONObject().put("question_id", qid).put("user_id", uid).put("reason", reason))
    fun ai(kind: String): String = JSONObject(Api.call("/functions/v1/ai", "POST", JSONObject().put("kind", kind))).optString("text")
    fun myReport(): String = rows(Api.call("/rest/v1/reports?user_id=eq.$uid&order=month.desc&limit=1&select=body")).firstOrNull()?.getString("body") ?: "Your first report will appear at the end of the month."
    fun teamReport(): String = rows(Api.call("/rest/v1/reports?user_id=is.null&order=month.desc&limit=1&select=body")).firstOrNull()?.getString("body") ?: "No team report yet."
    fun blockers() = rows(Api.call("/rest/v1/blockers?resolved=eq.false&order=created_at.desc&select=note,created_at,profiles(full_name)")).map {
        "${it.getJSONObject("profiles").getString("full_name")}: ${it.getString("note")}" }
    fun resolveBlockers() = Api.call("/rest/v1/blockers?resolved=eq.false", "PATCH", JSONObject().put("resolved", true))
    fun dashboard() = rows(Api.call("/rest/v1/admin_dashboard?select=*")).map {
        val flag = if (it.getInt("ignoring_steps") >= 3) "  ⚠ ignoring reminders" else ""
        "${it.getString("full_name")}\nPlan: ${if (it.getBoolean("plan_submitted")) "submitted ✓" else "not yet"} · Pending: ${it.getInt("pending")} · Overdue: ${it.getInt("overdue")}$flag" }
    fun pendingLeaves() = rows(Api.call("/rest/v1/leave_requests?status=eq.pending&select=from_date,to_date,kind,profiles(full_name)")).map {
        "${it.getJSONObject("profiles").getString("full_name")}: ${it.getString("kind")} ${it.getString("from_date")} → ${it.getString("to_date")}" }
    fun overdueTasks() = rows(Api.call("/rest/v1/pms_tasks?due_date=lt.${java.time.LocalDate.now()}&status=neq.done&select=title,profiles(full_name)")).map {
        "${it.getString("title")} — ${it.optJSONObject("profiles")?.optString("full_name") ?: "unassigned"}" }
}
