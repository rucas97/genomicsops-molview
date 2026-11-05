package com.genomicsops.molview

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.genomicsops.molview.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WebView.setWebContentsDebuggingEnabled(true)
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

fun getFileName(context: android.content.Context, uri: Uri): String? {
    var name: String? = null
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && nameIndex != -1) {
            name = cursor.getString(nameIndex)
        }
    }
    return name
}

@SuppressLint("JavascriptInterface")
@Composable
fun ViewerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var pdbText by remember { mutableStateOf<String?>(null) }
    var fileExt by remember { mutableStateOf("pdb") }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isPageReady by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    fun injectModel(text: String, ext: String) {
        val safeJsString = JSONObject.quote(text)
        webViewInstance?.evaluateJavascript("loadModel($safeJsString, '$ext')", null)
    }

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
                    val name = getFileName(context, uri) ?: "file.pdb"
                    val ext = name.substringAfterLast('.', "pdb").lowercase()
                    pdbText = text
                    fileExt = ext
                    if (isPageReady) injectModel(text, ext)
                }
                isLoading = false
            }
        }
    }

    // USING BOX INSTEAD OF COLUMN SO WEBVIEW FILLS SCREEN
    Box(Modifier.fillMaxSize().background(GenomicsDark)) {
        if (pdbText == null) {
            // --- HOME SCREEN ---
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(painter = painterResource(id = R.drawable.logo), contentDescription = "Logo", modifier = Modifier.size(100.dp))
                Spacer(Modifier.height(24.dp))
                Text("MolView", color = GenomicsCyan, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text("Structure Visualization", color = GenomicsMuted, fontSize = 16.sp)
                Spacer(Modifier.height(64.dp))
                Card(
                    onClick = { filePicker.launch("*/*") },
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    colors = CardDefaults.cardColors(containerColor = GenomicsSurface),
                    border = BorderStroke(1.dp, Brush.linearGradient(listOf(GenomicsCyan, GenomicsTeal))),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add", tint = GenomicsCyan, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(if (isLoading) "Parsing..." else "Tap to Open Structure", color = GenomicsText, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Text("PDB • CIF • MOL2 • SDF", color = GenomicsMuted, fontSize = 12.sp)
                    }
                }
            }
        } else {
            // --- VIEWER SCREEN (WebView fills whole screen) ---
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = true
                        settings.allowContentAccess = true
                        settings.allowFileAccessFromFileURLs = true
                        settings.allowUniversalAccessFromFileURLs = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        
                        webChromeClient = WebChromeClient()
                        addJavascriptInterface(object {
                            @JavascriptInterface
                            fun log(msg: String) {
                                (ctx as? android.app.Activity)?.runOnUiThread {
                                    Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }, "AndroidLogger")

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isPageReady = true
                                if (pdbText != null) injectModel(pdbText!!, fileExt)
                            }
                        }
                        
                        // Load the NEW viewer5.html
                        loadUrl("file:///android_asset/viewer6.html?v=" + System.currentTimeMillis())
                        webViewInstance = this
                    }
                },
                modifier = Modifier.fillMaxSize() // Fills the Box
            )

            // Overlay UI on top of the WebView
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Buttons
                Surface(color = GenomicsSurface.copy(alpha = 0.9f), shape = RoundedCornerShape(8.dp)) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        listOf("cartoon", "surface", "ballstick", "stick").forEach { rep ->
                            FilterChip(
                                selected = false,
                                onClick = { webViewInstance?.evaluateJavascript("setRep('$rep')", null) },
                                label = { Text(rep, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(labelColor = GenomicsCyan, containerColor = Color.Transparent),
                                border = FilterChipDefaults.filterChipBorder(enabled = true, selected = false, borderColor = GenomicsCyan.copy(alpha = 0.5f))
                            )
                        }
                    }
                }

                // Bottom Close Button
                TextButton(
                    onClick = { 
                        pdbText = null 
                        isPageReady = false
                        webViewInstance?.reload()
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally).background(GenomicsSurface.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                ) { 
                    Text("Close Structure", color = GenomicsMuted) 
                }
            }
        }
    }
}
