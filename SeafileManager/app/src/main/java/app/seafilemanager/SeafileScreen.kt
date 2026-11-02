package app.seafilemanager

import android.os.Environment
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun SeafileScreen(sf: SfSession, toast: (String) -> Unit, modifier: Modifier = Modifier) {
    if (sf.api == null) LoginForm(sf, toast, modifier) else Browser(sf, toast, modifier)
}

@Composable
private fun LoginForm(sf: SfSession, toast: (String) -> Unit, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    var server by remember { mutableStateOf(sf.savedServer) }
    var user by remember { mutableStateOf(sf.savedUser) }
    var pass by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var insecure by remember { mutableStateOf(sf.savedInsecure) }
    var busy by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().padding(24.dp), Arrangement.spacedBy(12.dp), Alignment.CenterHorizontally) {
        Text("Mit Seafile verbinden", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
        OutlinedTextField(server, { server = it }, label = { Text("Server-URL (z. B. https://cloud.example.de)") },
            singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
        OutlinedTextField(user, { user = it }, label = { Text("E-Mail / Benutzername") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(pass, { pass = it }, label = { Text("Passwort") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        OutlinedTextField(otp, { otp = it }, label = { Text("2FA-Code (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(insecure, { insecure = it })
            Text("Zertifikatsprüfung abschalten (selbstsigniert)")
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Button(
            enabled = !busy && server.isNotBlank() && user.isNotBlank() && pass.isNotEmpty(),
            onClick = {
                scope.launch {
                    busy = true
                    val r = io { sf.login(server, user, pass, otp, insecure) }
                    busy = false
                    r.onFailure { toast("Login fehlgeschlagen: ${it.message}") }
                }
            },
        ) { Text("Anmelden") }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun Browser(sf: SfSession, toast: (String) -> Unit, modifier: Modifier) {
    val api = sf.api!!
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var repos by remember { mutableStateOf<List<Repo>>(emptyList()) }
    var entries by remember { mutableStateOf<List<Entry>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var menuFor by remember { mutableStateOf<Entry?>(null) }
    var renaming by remember { mutableStateOf<Entry?>(null) }
    var deleting by remember { mutableStateOf<Entry?>(null) }
    var newFolder by remember { mutableStateOf(false) }

    val repo = sf.repo
    val path = sf.path

    LaunchedEffect(repo?.id, path, refresh) {
        busy = true
        val r = io { if (repo == null) api.repos() to emptyList() else emptyList<Repo>() to api.list(repo.id, path) }
        busy = false
        r.onSuccess { (rs, es) -> repos = rs; entries = es }
            .onFailure {
                toast("Fehler: ${it.message}")
                if (it.message?.startsWith("HTTP 401") == true) sf.logout()
            }
    }

    fun up() { if (path == "/") { sf.repo = null } else sf.path = parentPath(path) }
    BackHandler(repo != null) { up() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        val r = sf.repo ?: return@rememberLauncherForActivityResult
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val res = io {
                uris.forEach { u ->
                    val name = ctx.contentResolver.query(u, null, null, null, null)?.use { c ->
                        if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
                    } ?: "upload_${System.currentTimeMillis()}"
                    val tmp = File(ctx.cacheDir, "up_$name")
                    ctx.contentResolver.openInputStream(u)!!.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                    try { api.upload(r.id, sf.path, tmp, name) } finally { tmp.delete() }
                }
            }
            busy = false
            res.onSuccess { toast("${uris.size} Datei(en) hochgeladen"); refresh++ }
                .onFailure { toast("Fehler: ${it.message}") }
        }
    }

    fun download(e: Entry, open: Boolean) {
        val r = sf.repo ?: return
        scope.launch {
            busy = true
            val dest = if (open) File(ctx.cacheDir, "open/${e.name}")
            else File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Seafile/${e.name}")
            val res = io { api.download(r.id, joinPath(sf.path, e.name), dest) }
            busy = false
            res.onSuccess {
                if (open) runCatching { openFile(ctx, dest) }.onFailure { toast("Keine App zum Öffnen gefunden") }
                else toast("Gespeichert in Download/Seafile/${e.name}")
            }.onFailure { toast("Fehler: ${it.message}") }
        }
    }

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(if (repo == null) "Bibliotheken" else "${repo.name}${if (path == "/") "" else path}", maxLines = 1) },
            navigationIcon = { if (repo != null) IconButton({ up() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
            actions = {
                if (repo != null && repo.permission.contains("w")) {
                    IconButton({ picker.launch("*/*") }) { Icon(Icons.Default.Upload, "Hochladen") }
                    IconButton({ newFolder = true }) { Icon(Icons.Default.CreateNewFolder, "Neuer Ordner") }
                }
                IconButton({ refresh++ }) { Icon(Icons.Default.Refresh, "Aktualisieren") }
                IconButton({ sf.logout() }) { Icon(Icons.AutoMirrored.Filled.Logout, "Abmelden") }
            },
        )
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.fillMaxSize()) {
            if (repo == null) {
                items(repos, key = { it.id }) { r ->
                    ListItem(
                        modifier = Modifier.combinedClickable(onClick = {
                            if (r.encrypted) toast("Verschlüsselte Bibliotheken werden (noch) nicht unterstützt")
                            else { sf.path = "/"; sf.repo = r }
                        }),
                        leadingContent = { Icon(if (r.encrypted) Icons.Default.Lock else Icons.Default.Storage, null) },
                        headlineContent = { Text(r.name) },
                        supportingContent = { Text(formatSize(r.size)) },
                    )
                }
            } else {
                items(entries, key = { it.name }) { e ->
                    ListItem(
                        modifier = Modifier.combinedClickable(
                            onClick = { if (e.isDir) sf.path = joinPath(path, e.name) else download(e, open = true) },
                            onLongClick = { menuFor = e },
                        ),
                        leadingContent = { Icon(if (e.isDir) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile, null) },
                        headlineContent = { Text(e.name) },
                        supportingContent = {
                            val d = formatDate(e.mtime * 1000)
                            Text(if (e.isDir) d else "${formatSize(e.size)} · $d")
                        },
                        trailingContent = {
                            Row {
                                IconButton({ menuFor = e }) { Icon(Icons.Default.MoreVert, "Menü") }
                                DropdownMenu(menuFor == e, { menuFor = null }) {
                                    if (!e.isDir) DropdownMenuItem({ Text("Herunterladen") }, { menuFor = null; download(e, open = false) })
                                    DropdownMenuItem({ Text("Umbenennen") }, { menuFor = null; renaming = e })
                                    DropdownMenuItem({ Text("Löschen") }, { menuFor = null; deleting = e })
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    fun act(block: () -> Unit) = scope.launch {
        busy = true
        val r = io(block)
        busy = false
        r.onFailure { toast("Fehler: ${it.message}") }
        refresh++
    }

    if (newFolder && repo != null) TextDialog("Neuer Ordner", onDismiss = { newFolder = false }) { n ->
        newFolder = false
        act { api.mkdir(repo.id, joinPath(path, n)) }
    }
    renaming?.let { e ->
        TextDialog("Umbenennen", e.name, onDismiss = { renaming = null }) { n ->
            renaming = null
            act { api.rename(repo!!.id, joinPath(path, e.name), e.isDir, n) }
        }
    }
    deleting?.let { e ->
        ConfirmDialog("„${e.name}“ in Seafile löschen?", onDismiss = { deleting = null }) {
            deleting = null
            act { api.delete(repo!!.id, joinPath(path, e.name), e.isDir) }
        }
    }
}
