package app.seafilemanager

import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

suspend fun <T> io(block: () -> T): Result<T> = withContext(Dispatchers.IO) { runCatching(block) }

fun formatSize(b: Long): String {
    if (b < 1024) return "$b B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var v = b.toDouble(); var i = -1
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return "%.1f %s".format(v, units[i])
}

fun formatDate(epochMs: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMs))

fun joinPath(dir: String, name: String) = if (dir.endsWith("/")) dir + name else "$dir/$name"

fun parentPath(p: String): String {
    val t = p.trimEnd('/')
    val i = t.lastIndexOf('/')
    return if (i <= 0) "/" else t.substring(0, i)
}

fun openFile(ctx: Context, f: File) {
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"
    val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    ctx.startActivity(Intent.createChooser(i, f.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
fun TextDialog(title: String, initial: String = "", onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, singleLine = true, keyboardOptions = KeyboardOptions.Default) },
        confirmButton = { TextButton({ if (text.isNotBlank()) onOk(text.trim()) }) { Text("OK") } },
        dismissButton = { TextButton(onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
fun ConfirmDialog(title: String, onDismiss: () -> Unit, onOk: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        confirmButton = { TextButton(onOk) { Text("Ja") } },
        dismissButton = { TextButton(onDismiss) { Text("Abbrechen") } },
    )
}
