package com.genomicsops.molview

import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.genomicsops.molview.ui.GenomicsTheme
import com.genomicsops.molview.ui.SplashScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GenomicsTheme {
                AppRoot()
            }
        }
    }
}

@Composable
fun AppRoot() {
    var showSplash by remember { mutableStateOf(true) }
    if (showSplash) {
        SplashScreen { showSplash = false }
    } else {
        ViewerScreen()
    }
}

@Composable
fun ViewerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var pdbText by remember { mutableStateOf<String?>(null) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isLoading = true
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                    } catch (e: Exception) { null }
                }
                if (text != null) {
                    pdbText = text
                    webViewInstance?.evaluateJavascript("loadModel(\`$text\`)", null)
                }
                isLoading = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (pdbText == null) {
            // HOME SCREEN
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Button(onClick = { filePicker.launch("*/*") }) {
                    Text(if (isLoading) "Loading..." else "Select 3D Molecule File")
                }
                Spacer(Modifier.height(16.dp))
                Text("Accepts PDB, CIF, SDF, MOL2, XYZ", style = MaterialTheme.typography.bodySmall)
            }
        } else {
            // VIEWER SCREEN
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf("cartoon", "surface", "ballstick", "stick").forEach { rep ->
                    OutlinedButton(
                        onClick = { webViewInstance?.evaluateJavascript("setRep('$rep')", null) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) { Text(rep, style = MaterialTheme.typography.labelSmall) }
                }
            }

            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.allowFileAccess = true
                        webViewClient = WebViewClient()
                        loadUrl("file:///android_asset/viewer.html")
                        webViewInstance = this
                    }
                },
                modifier = Modifier.weight(1f)
            )

            Button(
                onClick = { 
                    pdbText = null 
                    webViewInstance?.loadUrl("about:blank")
                },
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(8.dp)
            ) { Text("Close") }
        }
    }
}
