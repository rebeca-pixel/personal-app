package com.knockturn.personalexecutive
import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppVm(app: Application) : AndroidViewModel(app) {
    var profile by mutableStateOf<Profile?>(null)
    var busy by mutableStateOf(false)
    var msg by mutableStateOf<String?>(null)
    var questions by mutableStateOf<List<Question>>(emptyList())
    var answered by mutableStateOf<Set<String>>(emptySet())
    var employees by mutableStateOf<List<Profile>>(emptyList())
    var answers by mutableStateOf<List<String>>(emptyList())

    private fun <T> run(block: () -> T, ok: (T) -> Unit = {}) = viewModelScope.launch {
        busy = true
        try { ok(withContext(Dispatchers.IO) { block() }) } catch (e: Exception) { msg = e.message ?: "Something went wrong" }
        busy = false
    }
    fun login(e: String, p: String) = run({ Api.login(e.trim(), p) }) { profile = it
        getApplication<Application>().getSharedPreferences("work", 0).edit().putInt("ws", it.ws).putInt("we", it.we).apply()
        Reminders.channels(getApplication<Application>()); refresh()
        getApplication<Application>().getSharedPreferences("fcm", 0).getString("token", null)?.let { t -> run({ Api.saveToken(t) }) } }
    fun logout() { Reminders.clearAll(getApplication<Application>()); Api.logout(); profile = null }
    fun signUp(name: String, e: String, p: String, role: String) = run({ Api.signUp(e.trim(), p, name.trim(), role) }) { profile = it
        Reminders.channels(getApplication<Application>()); refresh()
        getApplication<Application>().getSharedPreferences("fcm", 0).getString("token", null)?.let { t -> run({ Api.saveToken(t) }) } }
    fun refresh() {
        val ctx = getApplication<Application>()
        if (profile?.role == "admin") run({ Triple(Api.questions(), Api.employees(), Api.allAnswers()) }) { questions = it.first; employees = it.second; answers = it.third }
        else run({ Api.questions() to Api.answeredIds() }) { (q, a) -> questions = q; answered = a
            q.filter { it.id !in a }.forEach { Reminders.track(ctx, it.id, it.text, it.urgent) } }
    }
    fun answer(q: Question, text: String) = run({ Api.answer(q.id, text) }) { Reminders.done(getApplication<Application>(), q.id); msg = "Answer sent"; refresh() }
    fun create(text: String, urgent: Boolean, target: String?) = run({ Api.createQuestion(text, urgent, target) }) { msg = "Question sent"; refresh() }
    fun plan(t: String) = run({ Api.savePlan(t) }) { msg = "Plan saved" }
    fun evening(t: String) = run({ Api.saveEvening(t) }) { msg = "Evening update saved" }
}
