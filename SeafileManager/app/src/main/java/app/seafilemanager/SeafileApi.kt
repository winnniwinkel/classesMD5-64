package app.seafilemanager

import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

data class Repo(val id: String, val name: String, val size: Long, val encrypted: Boolean, val permission: String)
data class Entry(val name: String, val isDir: Boolean, val size: Long, val mtime: Long)

/** Dünner Client für die Seafile Web API v2 (blockierend – auf Dispatchers.IO aufrufen). */
class SeafileApi(server: String, var token: String?, insecure: Boolean = false) {

    val base: String = server.trim().trimEnd('/').let { if (it.startsWith("http")) it else "https://$it" }

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .apply {
            if (insecure) { // nur auf ausdrücklichen Wunsch (selbstsigniertes Zertifikat)
                val tm = object : X509TrustManager {
                    override fun checkClientTrusted(c: Array<X509Certificate>, a: String) {}
                    override fun checkServerTrusted(c: Array<X509Certificate>, a: String) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                }
                val ctx = SSLContext.getInstance("TLS").apply { init(null, arrayOf(tm), SecureRandom()) }
                sslSocketFactory(ctx.socketFactory, tm)
                hostnameVerifier { _, _ -> true }
            }
        }
        .build()

    private fun url(path: String, vararg q: Pair<String, String>): HttpUrl {
        val b = (base + path).toHttpUrl().newBuilder()
        q.forEach { b.addQueryParameter(it.first, it.second) }
        return b.build()
    }

    private fun builder(u: HttpUrl) = Request.Builder().url(u).apply {
        token?.let { header("Authorization", "Token $it") }
    }

    private fun exec(r: Request): String = http.newCall(r).execute().use { resp ->
        val body = resp.body?.string().orEmpty()
        if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}: ${body.take(200)}")
        body
    }

    private fun unquote(s: String) = s.trim().trim('"')

    fun login(user: String, password: String, otp: String? = null): String {
        val form = FormBody.Builder().add("username", user).add("password", password).build()
        val req = Request.Builder().url(url("/api2/auth-token/")).post(form)
            .apply { if (!otp.isNullOrBlank()) header("X-SEAFILE-OTP", otp) }
            .build()
        val t = JSONObject(exec(req)).getString("token")
        token = t
        return t
    }

    fun repos(): List<Repo> {
        val arr = JSONArray(exec(builder(url("/api2/repos/")).build()))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Repo(o.getString("id"), o.getString("name"), o.optLong("size"), o.optBoolean("encrypted"), o.optString("permission", "rw"))
        }.sortedBy { it.name.lowercase() }
    }

    fun list(repoId: String, path: String): List<Entry> {
        val arr = JSONArray(exec(builder(url("/api2/repos/$repoId/dir/", "p" to path)).build()))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Entry(o.getString("name"), o.getString("type") == "dir", o.optLong("size"), o.optLong("mtime"))
        }.sortedWith(compareByDescending<Entry> { it.isDir }.thenBy { it.name.lowercase() })
    }

    fun download(repoId: String, path: String, dest: File) {
        val link = unquote(exec(builder(url("/api2/repos/$repoId/file/", "p" to path, "reuse" to "1")).build()))
        http.newCall(Request.Builder().url(link).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Download fehlgeschlagen: HTTP ${resp.code}")
            dest.parentFile?.mkdirs()
            resp.body!!.byteStream().use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
        }
    }

    fun upload(repoId: String, parentDir: String, file: File, name: String = file.name) {
        val link = unquote(exec(builder(url("/api2/repos/$repoId/upload-link/", "p" to parentDir)).build()))
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("parent_dir", parentDir)
            .addFormDataPart("replace", "1")
            .addFormDataPart("file", name, file.asRequestBody())
            .build()
        val u = link.toHttpUrl().newBuilder().addQueryParameter("ret-json", "1").build()
        exec(builder(u).post(body).build())
    }

    fun delete(repoId: String, path: String, isDir: Boolean) {
        val kind = if (isDir) "dir" else "file"
        exec(builder(url("/api2/repos/$repoId/$kind/", "p" to path)).delete().build())
    }

    fun mkdir(repoId: String, path: String) {
        val form = FormBody.Builder().add("operation", "mkdir").build()
        exec(builder(url("/api2/repos/$repoId/dir/", "p" to path)).post(form).build())
    }

    fun rename(repoId: String, path: String, isDir: Boolean, newName: String) {
        val kind = if (isDir) "dir" else "file"
        val form = FormBody.Builder().add("operation", "rename").add("newname", newName).build()
        exec(builder(url("/api2/repos/$repoId/$kind/", "p" to path)).post(form).build())
    }
}
