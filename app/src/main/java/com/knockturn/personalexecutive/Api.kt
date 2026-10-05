package com.knockturn.personalexecutive
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

// Service-role key is only used for the sign-up profile insert (bypasses RLS for new users)
private const val SERVICE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InlvZXltYWd6c3VndXRlb3Rqa3FuIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc5MTE4NTk1NCwiZXhwIjoyMTA2NzYxOTU0fQ.ldubosoM_kuzZDcNOEfP6swnpC2kZSGPPXQA5XeoYbI"

data class Profile(val id: String, val name: String, val role: String, val ws: Int = 540, val we: Int = 1080)
data class Question(val id: String, val text: String, val urgent: Boolean, val deadline: String?)

object Api {
    private val http = OkHttpClient()
    private val json = "application/json".toMediaType()
    private var token = ""
    var userId = ""; private set

    internal fun call(path: String, method: String = "GET", body: JSONObject? = null, upsert: Boolean = false): String {
        val b = Request.Builder().url(Config.SUPABASE_URL + path)
            .header("apikey", Config.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer " + token.ifEmpty { Config.SUPABASE_ANON_KEY })
            .header("Prefer", if (upsert) "resolution=merge-duplicates,return=minimal" else "return=minimal")
        when (method) {
            "POST" -> b.post((body ?: JSONObject()).toString().toRequestBody(json))
            "PATCH" -> b.patch((body ?: JSONObject()).toString().toRequestBody(json))
        }
        http.newCall(b.build()).execute().use { r ->
            val s = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw Exception(JSONObject(s.ifEmpty { "{}" }).let { j -> j.optString("msg", j.optString("message", s.ifEmpty { "Error ${r.code}" })) })
            return s
        }
    }

    fun login(email: String, pass: String): Profile {
        val res = JSONObject(call("/auth/v1/token?grant_type=password", "POST",
            JSONObject().put("email", email).put("password", pass)))
        token = res.getString("access_token"); userId = res.getJSONObject("user").getString("id")
        val p = JSONArray(call("/rest/v1/profiles?id=eq.$userId&select=id,full_name,role,work_start,work_end")).getJSONObject(0)
        fun mins(k: String, d: Int) = runCatching { p.getString(k).split(":").let { it[0].toInt() * 60 + it[1].toInt() } }.getOrDefault(d)
        return Profile(p.getString("id"), p.getString("full_name"), p.getString("role"), mins("work_start", 540), mins("work_end", 1080))
    }
    fun logout() { token = ""; userId = "" }
    fun saveToken(fcm: String) { if (userId.isNotEmpty()) call("/rest/v1/profiles?id=eq.$userId", "PATCH", JSONObject().put("fcm_token", fcm)) }

    /** Register a new user (via admin API — no email confirmation, no rate limits). */
    fun signUp(email: String, pass: String, fullName: String, role: String): Profile {
        // Step 1 — Use admin API to create user with email pre-confirmed (no email sent)
        val adminRes = http.newCall(
            Request.Builder()
                .url(Config.SUPABASE_URL + "/auth/v1/admin/users")
                .header("apikey", SERVICE_KEY)
                .header("Authorization", "Bearer $SERVICE_KEY")
                .header("Content-Type", "application/json")
                .post(
                    JSONObject()
                        .put("email", email)
                        .put("password", pass)
                        .put("email_confirm", true)   // skip confirmation email entirely
                        .toString()
                        .toRequestBody(json)
                )
                .build()
        ).execute().use { r ->
            val s = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                val errMsg = runCatching {
                    JSONObject(s).let { j -> j.optString("msg", j.optString("message", "")) }
                }.getOrDefault("")
                throw Exception(errMsg.ifBlank { "Sign up failed (${r.code})" })
            }
            JSONObject(s)
        }
        val newUserId = adminRes.getString("id")

        // Step 2 — Insert profile row (service-role key bypasses RLS)
        http.newCall(
            Request.Builder()
                .url(Config.SUPABASE_URL + "/rest/v1/profiles")
                .header("apikey", SERVICE_KEY)
                .header("Authorization", "Bearer $SERVICE_KEY")
                .header("Content-Type", "application/json")
                .header("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(
                    JSONObject()
                        .put("id", newUserId)
                        .put("full_name", fullName)
                        .put("role", role)
                        .toString()
                        .toRequestBody(json)
                )
                .build()
        ).execute().use { r ->
            if (!r.isSuccessful) throw Exception("Profile creation failed: ${r.body?.string()}")
        }

        // Step 3 — Log in to get a real session token
        val loginRes = JSONObject(call("/auth/v1/token?grant_type=password", "POST",
            JSONObject().put("email", email).put("password", pass)))
        token = loginRes.getString("access_token")
        userId = loginRes.getJSONObject("user").getString("id")
        return Profile(userId, fullName, role)
    }

    fun questions(): List<Question> {
        val a = JSONArray(call("/rest/v1/questions?select=id,text,priority,deadline&order=created_at.desc"))
        return (0 until a.length()).map { val o = a.getJSONObject(it)
            Question(o.getString("id"), o.getString("text"), o.getString("priority") == "urgent", o.optString("deadline").takeIf { d -> d != "null" && d.isNotEmpty() }) }
    }
    fun answeredIds(): Set<String> {
        val a = JSONArray(call("/rest/v1/answers?user_id=eq.$userId&select=question_id"))
        return (0 until a.length()).map { a.getJSONObject(it).getString("question_id") }.toSet()
    }
    fun answer(qid: String, text: String) = call("/rest/v1/answers", "POST",
        JSONObject().put("question_id", qid).put("user_id", userId).put("answer_text", text))
    fun createQuestion(text: String, urgent: Boolean, target: String?) = call("/rest/v1/questions", "POST",
        JSONObject().put("text", text).put("priority", if (urgent) "urgent" else "normal")
            .put("created_by", userId).put("target_user_id", target ?: JSONObject.NULL))
    fun savePlan(plan: String) = call("/rest/v1/plans?on_conflict=user_id,plan_date", "POST",
        JSONObject().put("user_id", userId).put("plan_text", plan), upsert = true)
    fun saveEvening(text: String) = call("/rest/v1/plans?user_id=eq.$userId&plan_date=eq.${java.time.LocalDate.now()}", "PATCH",
        JSONObject().put("evening_text", text))
    fun employees(): List<Profile> {
        val a = JSONArray(call("/rest/v1/profiles?role=eq.employee&select=id,full_name,role"))
        return (0 until a.length()).map { val o = a.getJSONObject(it); Profile(o.getString("id"), o.getString("full_name"), "employee") }
    }
    fun allAnswers(): List<String> {
        val a = JSONArray(call("/rest/v1/answers?select=answer_text,created_at,profiles(full_name),questions(text)&order=created_at.desc&limit=100"))
        return (0 until a.length()).map { val o = a.getJSONObject(it)
            "${o.getJSONObject("profiles").getString("full_name")} — ${o.getJSONObject("questions").getString("text")}\n${o.getString("answer_text")}" }
    }
}
