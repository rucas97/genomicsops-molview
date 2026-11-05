package com.genomicsops.molview

import android.net.Uri
import android.os.Bundle
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
import com.genomicsops.molview.mol.PdbParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ViewerScreen()
                }
            }
        }
    }
}

@Composable
fun ViewerScreen() {
    val context = LocalContext.current
    var statusText by remember { mutableStateOf("No file loaded.") }
    var isLoading by remember { mutableStateOf(false) }

    // This launches the Android file picker
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        isLoading = true
        statusText = "Parsing..."

        // Parse in the background so we don't freeze the UI
        CoroutineScope(Dispatchers.Main).launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    if (inputStream == null) return@withContext "Error: Could not open file."
                    
                    val model = PdbParser.parse(inputStream)
                    "Success!\n\n" +
                    "Atoms: ${model.atomCount}\n" +
                    "Residues: ${model.residueCount}\n" +
                    "Chains: ${model.chainCount}\n" +
                    "Ligands: ${model.ligandResidues.size}"
                } catch (e: Exception) {
                    "Parse Error:\n${e.message}"
                }
            }
            statusText = result
            isLoading = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "GenomicsOps MolView",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Button(
            onClick = { filePicker.launch("*/*") }, // Allow any file so you can pick .pdb/.ent/.txt
            enabled = !isLoading
        ) {
            Text(if (isLoading) "Loading..." else "Select PDB File")
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(16.dp)
        )
    }
}
