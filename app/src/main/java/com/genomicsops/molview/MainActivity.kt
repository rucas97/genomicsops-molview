package com.genomicsops.molview

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.genomicsops.molview.mol.PdbParser
import com.genomicsops.molview.mol.Residue
import com.genomicsops.molview.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    // 1. State to hold incoming files from other apps
    var incomingUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WebView.setWebContentsDebuggingEnabled(true)
        
        // Check if app was opened via "Open With"
        incomingUri = intent?.data

        setContent {
            GenomicsTheme {
                AppRoot(incomingUri)
            }
        }
    }

    // Handle "Open With" while the app is already running
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        incomingUri = intent.data
    }
}

@Composable
fun AppRoot(incomingUri: Uri?) {
    var showSplash by remember { mutableStateOf(true) }
    if (showSplash) SplashScreen { showSplash = false } else ViewerScreen(incomingUri)
}

fun getFileName(context: Context, uri: Uri): String? {
    var name: String? = null
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && nameIndex != -1) name = cursor.getString(nameIndex)
    }
    return name
}

fun get1LetterCode(resName: String): Char = when(resName) {
    "ALA" -> 'A'; "ARG" -> 'R'; "ASN" -> 'N'; "ASP" -> 'D'; "CYS" -> 'C'
    "GLN" -> 'Q'; "GLU" -> 'E'; "GLY" -> 'G'; "HIS" -> 'H'; "ILE" -> 'I'
    "LEU" -> 'L'; "LYS" -> 'K'; "MET" -> 'M'; "PHE" -> 'F'; "PRO" -> 'P'
    "SER" -> 'S'; "THR" -> 'T'; "TRP" -> 'W'; "TYR" -> 'Y'; "VAL" -> 'V'
    else -> 'X'
}

fun getSpectrumColor(index: Int, total: Int): Color {
    if (total <= 1) return Color.Red
    val fraction = index.toFloat() / (total - 1)
    val hue = (1f - fraction) * 240f
    return Color.hsv(hue, 1f, 1f)
}

@SuppressLint("JavascriptInterface")
@Composable
fun ViewerScreen(incomingUri: Uri?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var pdbText by remember { mutableStateOf<String?>(null) }
    var fastaResidues by remember { mutableStateOf<List<Residue>>(emptyList()) }
    var fileExt by remember { mutableStateOf("pdb") }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isPageReady by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    
    var showColorMenu by remember { mutableStateOf(false) }
    var isSpinning by remember { mutableStateOf(false) }

    fun injectModel(text: String, ext: String) {
        val safeJsString = JSONObject.quote(text)
        webViewInstance?.evaluateJavascript("loadModel($safeJsString, '$ext')", null)
    }

    fun processLoadedFile(text: String, ext: String) {
        pdbText = text
        fileExt = ext
        scope.launch {
            fastaResidues = withContext(Dispatchers.Default) {
                try {
                    PdbParser.parse(text.byteInputStream()).chains
                        .flatMap { it.residues }
                        .filter { it.isStandardAA }
                } catch (e: Exception) { emptyList() }
            }
            if (isPageReady) injectModel(text, ext)
        }
    }

    fun loadUri(uri: Uri) {
        isLoading = true
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                try { context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() } catch (e: Exception) { null }
            }
            if (text != null) {
                val name = getFileName(context, uri) ?: "file.pdb"
                processLoadedFile(text, name.substringAfterLast('.', "pdb").lowercase())
            } else {
                Toast.makeText(context, "Failed to read file", Toast.LENGTH_SHORT).show()
            }
            isLoading = false
        }
    }

    // 2. Listen for incoming "Open With" URIs
    LaunchedEffect(incomingUri) {
        if (incomingUri != null && isPageReady) {
            loadUri(incomingUri)
        }
    }

    fun loadDefault(assetName: String) {
        isLoading = true
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                try { context.assets.open("defaults/$assetName").bufferedReader().readText() } catch (e: Exception) { null }
            }
            if (text != null) processLoadedFile(text, "pdb")
            else Toast.makeText(context, "Failed to load demo", Toast.LENGTH_SHORT).show()
            isLoading = false
        }
    }

    val filePicker = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) loadUri(uri)
    }

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
                Spacer(Modifier.height(48.dp))
                Text("Try a Demo", color = GenomicsMuted, fontSize = 14.sp, modifier = Modifier.align(Alignment.Start))
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { loadDefault("helix.pdb") }, modifier = Modifier.weight(1f)) { Text("Alpha Helix") }
                    Button(onClick = { loadDefault("sheet.pdb") }, modifier = Modifier.weight(1f)) { Text("Beta Sheet") }
                }
                Spacer(Modifier.height(32.dp))
                Text("Or Load Your Own", color = GenomicsMuted, fontSize = 14.sp, modifier = Modifier.align(Alignment.Start))
                Spacer(Modifier.height(8.dp))
                Card(
                    onClick = { filePicker.launch("*/*") },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    colors = CardDefaults.cardColors(containerColor = GenomicsSurface),
                    border = BorderStroke(1.dp, Brush.linearGradient(listOf(GenomicsCyan, GenomicsTeal))),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add", tint = GenomicsCyan, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(if (isLoading) "Parsing..." else "Select 3D File", color = GenomicsText, fontWeight = FontWeight.Medium)
                    }
                }
            }
        } else {
            // --- VIEWER SCREEN ---
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = true
                        settings.allowContentAccess = true
                        settings.allowFileAccessFromFileURLs = true
                        settings.allowUniversalAccessFromFileURLs = true
                        
                        webChromeClient = WebChromeClient()
                        addJavascriptInterface(object {
                            @JavascriptInterface
                            fun log(msg: String) { (ctx as? android.app.Activity)?.runOnUiThread { Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() } }
                            @JavascriptInterface
                            fun vibrate() {
                                val vibrator = ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                                else @Suppress("DEPRECATION") vibrator.vibrate(200)
                            }
                        }, "AndroidLogger")

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isPageReady = true
                                if (pdbText != null) injectModel(pdbText!!, fileExt)
                                else if (incomingUri != null) loadUri(incomingUri) // Handle "Open With" on cold start
                            }
                        }
                        loadUrl("file:///android_asset/viewer.html?v=" + System.currentTimeMillis())
                        webViewInstance = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                // Top Controls
                Surface(color = GenomicsSurface.copy(alpha = 0.9f), shape = RoundedCornerShape(8.dp)) {
                    Column(Modifier.fillMaxWidth().padding(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
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
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { isSpinning = !isSpinning; webViewInstance?.evaluateJavascript("toggleSpin()", null) }) {
                                Icon(imageVector = if (isSpinning) Icons.Default.Pause else Icons.Default.Refresh, contentDescription = "Spin", tint = GenomicsTeal)
                            }
                            IconButton(onClick = { webViewInstance?.evaluateJavascript("rattle()", null) }) {
                                Icon(Icons.Default.Vibration, contentDescription = "Rattle", tint = GenomicsTeal)
                            }
                            Box {
                                IconButton(onClick = { showColorMenu = true }) {
                                    Icon(Icons.Default.Menu, contentDescription = "Colors", tint = GenomicsTeal)
                                }
                                DropdownMenu(expanded = showColorMenu, onDismissRequest = { showColorMenu = false }) {
                                    listOf("spectrum", "chain", "ssPyMOL", "greenCarbon", "chainHetatm").forEach { color ->
                                        DropdownMenuItem(
                                            text = { Text(color, color = GenomicsText) },
                                            onClick = {
                                                webViewInstance?.evaluateJavascript("setColor('$color')", null)
                                                showColorMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Bottom Section: FASTA + Close Button
                Column(Modifier.fillMaxWidth()) {
                    Surface(color = Color.White.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (fastaResidues.isEmpty()) {
                                item { Text("SEQ: Not available (Try a .pdb file)", color = GenomicsMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
                            } else {
                                item { Text("SEQ: ", color = GenomicsCyan, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) }
                                items(fastaResidues.size) { index ->
                                    val res = fastaResidues[index]
                                    // 3. Make each letter clickable to zoom
                                    Text(
                                        text = get1LetterCode(res.name).toString(),
                                        color = getSpectrumColor(index, fastaResidues.size),
                                        fontSize = 14.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .padding(horizontal = 2.dp)
                                            .clickable {
                                                val chainId = res.chain?.id ?: 'A'
                                                webViewInstance?.evaluateJavascript("highlightResidue('$chainId', ${res.number})", null)
                                            }
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    TextButton(
                        onClick = { 
                            pdbText = null 
                            fastaResidues = emptyList()
                            isSpinning = false
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
}
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { webViewInstance?.evaluateJavascript("resetView()", null) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = GenomicsDark)
                                ) { Text("Reset View", color = GenomicsText) }
                                
                                Button(
                                    onClick = { webViewInstance?.evaluateJavascript("toggleHydrogens()", null) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = GenomicsDark)
                                ) { Text("Toggle H", color = GenomicsText) }
                            }
                            Spacer(Modifier.height(48.dp))
                        }
                    }
                }

                // Bottom Section: FASTA + Close Button
                Column(Modifier.fillMaxWidth()) {
                    Surface(color = Color.White.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (fastaResidues.isEmpty()) {
                                item { Text("SEQ: Not available (Try a .pdb file)", color = GenomicsMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
                            } else {
                                item { Text("SEQ: ", color = GenomicsCyan, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) }
                                items(fastaResidues.size) { index ->
                                    val res = fastaResidues[index]
                                    Text(
                                        text = get1LetterCode(res.name).toString(),
                                        color = getSpectrumColor(index, fastaResidues.size),
                                        fontSize = 14.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .padding(horizontal = 2.dp)
                                            .clickable {
                                                val chainId = res.chain?.id ?: 'A'
                                                webViewInstance?.evaluateJavascript("highlightResidue('$chainId', ${res.number})", null)
                                            }
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    TextButton(
                        onClick = { 
                            pdbText = null 
                            fastaResidues = emptyList()
                            isSpinning = false
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
