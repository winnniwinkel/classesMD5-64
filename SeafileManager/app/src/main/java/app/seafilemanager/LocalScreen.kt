package app.seafilemanager

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LocalScreen(sf: SfSession, toast: (String) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val root = remember { Environment.getExternalStorageDirectory() }

    var hasAccess by remember { mutableStateOf(Environment.isExternalStorageManager()) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) hasAccess = Environment.isExternalStorageManager()
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    if (!hasAccess) {
        Column(modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
            Text("Für den Dateimanager wird der Zugriff auf alle Dateien benötigt.")
            Button(
                onClick = {
                    ctx.startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                    )
                },
                modifier = Modifier.padding(top = 16.dp),
            ) { Text("Zugriff erlauben") }
        }
        return
    }

    var dir by remember { mutableStateOf(root) }
    var refresh by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<File?>(null) }
    var renaming by remember { mutableStateOf<File?>(null) }
    var deleting by remember { mutableStateOf<File?>(null) }
    var newFolder by remember { mutableStateOf(false) }

    val files = remember(dir, refresh) {
        (dir.listFiles()?.toList() ?: emptyList())
            .sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
    }
    val atRoot = dir.absolutePath == root.absolutePath
    BackHandler(!atRoot) { dir = dir.parentFile ?: root }

    fun upload(f: File) {
        val r = sf.repo; val a = sf.api
        if (a == null || r == null) { toast("Erst im Tab „Seafile“ einen Ordner öffnen"); return }
        scope.launch {
            busy = true
            val res = io { if (f.isDirectory) error("Ordner können nicht hochgeladen werden") else a.upload(r.id, sf.path, f) }
            busy = false
            toast(res.fold({ "Hochgeladen nach ${r.name}${sf.path}" }, { "Fehler: ${it.message}" }))
        }
    }

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(if (atRoot) "Interner Speicher" else dir.name) },
            navigationIcon = {
                if (!atRoot) IconButton({ dir = dir.parentFile ?: root }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
            },
            actions = { IconButton({ newFolder = true }) { Icon(Icons.Default.CreateNewFolder, "Neuer Ordner") } },
        )
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.fillMaxSize()) {
            items(files, key = { it.absolutePath }) { f ->
                ListItem(
                    modifier = Modifier.combinedClickable(
                        onClick = { if (f.isDirectory) dir = f else runCatching { openFile(ctx, f) }.onFailure { toast("Keine App zum Öffnen gefunden") } },
                        onLongClick = { menuFor = f },
                    ),
                    leadingContent = { Icon(if (f.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile, null) },
                    headlineContent = { Text(f.name) },
                    supportingContent = {
                        Text(if (f.isDirectory) formatDate(f.lastModified()) else "${formatSize(f.length())} · ${formatDate(f.lastModified())}")
                    },
                    trailingContent = {
                        Row {
                            IconButton({ menuFor = f }) { Icon(Icons.Default.MoreVert, "Menü") }
                            DropdownMenu(menuFor == f, { menuFor = null }) {
                                if (!f.isDirectory) DropdownMenuItem({ Text("Nach Seafile hochladen") }, { menuFor = null; upload(f) })
                                DropdownMenuItem({ Text("Umbenennen") }, { menuFor = null; renaming = f })
                                DropdownMenuItem({ Text("Löschen") }, { menuFor = null; deleting = f })
                            }
                        }
                    },
                )
            }
        }
    }

    if (newFolder) TextDialog("Neuer Ordner", onDismiss = { newFolder = false }) { n ->
        newFolder = false
        if (File(dir, n).mkdirs()) refresh++ else toast("Ordner konnte nicht erstellt werden")
    }
    renaming?.let { f ->
        TextDialog("Umbenennen", f.name, onDismiss = { renaming = null }) { n ->
            renaming = null
            if (f.renameTo(File(f.parentFile, n))) refresh++ else toast("Umbenennen fehlgeschlagen")
        }
    }
    deleting?.let { f ->
        ConfirmDialog("„${f.name}“ löschen?", onDismiss = { deleting = null }) {
            deleting = null
            if (f.deleteRecursively()) refresh++ else toast("Löschen fehlgeschlagen")
        }
    }
}
