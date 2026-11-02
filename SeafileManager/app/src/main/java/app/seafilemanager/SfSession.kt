package app.seafilemanager

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Gemeinsamer Zustand: Login + aktuell geöffneter Seafile-Ordner (Ziel für Uploads aus dem Dateimanager). */
class SfSession(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("seafile", Context.MODE_PRIVATE)

    var api by mutableStateOf<SeafileApi?>(null)
    var repo by mutableStateOf<Repo?>(null)
    var path by mutableStateOf("/")

    val savedServer get() = prefs.getString("server", "") ?: ""
    val savedUser get() = prefs.getString("user", "") ?: ""
    val savedInsecure get() = prefs.getBoolean("insecure", false)

    init {
        val token = prefs.getString("token", null)
        if (token != null && savedServer.isNotBlank()) api = SeafileApi(savedServer, token, savedInsecure)
    }

    fun login(server: String, user: String, pass: String, otp: String?, insecure: Boolean) {
        val a = SeafileApi(server, null, insecure)
        val t = a.login(user, pass, otp)
        prefs.edit().putString("server", a.base).putString("user", user)
            .putString("token", t).putBoolean("insecure", insecure).apply()
        api = a
    }

    fun logout() {
        prefs.edit().remove("token").apply()
        api = null; repo = null; path = "/"
    }
}
