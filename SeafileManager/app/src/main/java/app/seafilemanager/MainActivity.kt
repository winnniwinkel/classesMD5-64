package app.seafilemanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val ctx = LocalContext.current
            val scheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
            MaterialTheme(colorScheme = scheme) {
                val sf = remember { SfSession(applicationContext) }
                val snack = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()
                var tab by remember { mutableIntStateOf(0) }
                val toast: (String) -> Unit = { msg -> scope.launch { snack.showSnackbar(msg) } }

                Scaffold(
                    snackbarHost = { SnackbarHost(snack) },
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Default.PhoneAndroid, null) }, label = { Text("Dieses Gerät") })
                            NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Default.Cloud, null) }, label = { Text("Seafile") })
                        }
                    },
                ) { pad ->
                    val m = Modifier.padding(pad)
                    if (tab == 0) LocalScreen(sf, toast, m) else SeafileScreen(sf, toast, m)
                }
            }
        }
    }
}
