package com.qualcomm.rag

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                RagScreen(vm = vm, onPickPdf = { uri -> vm.indexPdf(uri, fileName(uri)) })
            }
        }
    }

    // Get the human-readable file name from the URI
    private fun fileName(uri: Uri): String {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (col >= 0 && cursor.moveToFirst()) return cursor.getString(col)
        }
        return "document.pdf"
    }
}

@Composable
fun RagScreen(vm: MainViewModel, onPickPdf: (Uri) -> Unit) {

    val status  by vm.status.collectAsState()
    val isBusy  by vm.isBusy.collectAsState()
    val isReady by vm.isReady.collectAsState()
    val results by vm.results.collectAsState()

    var query by remember { mutableStateOf("") }

    // File picker — opens the system file browser for PDFs
    val picker = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        if (uri != null) onPickPdf(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        // Pick a PDF (disabled while busy)
        Button(
            onClick  = { picker.launch(arrayOf("application/pdf")) },
            enabled  = !isBusy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isBusy) "Please wait…" else "Pick PDF")
        }

        // One line of status text telling the user what's happening
        Text(text = status, style = MaterialTheme.typography.bodyMedium)

        // Show the search box only when a PDF is ready
        if (isReady) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value         = query,
                    onValueChange = { query = it },
                    placeholder   = { Text("Ask a question about the PDF…") },
                    modifier      = Modifier.weight(1f),
                    singleLine    = true
                )
                Button(
                    onClick  = { vm.search(query) },
                    enabled  = query.isNotBlank() && !isBusy
                ) {
                    Text("Search")
                }
            }

            // Results list
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(results) { index, result ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {

                            // Rank, page number, and similarity score
                            Text(
                                text  = "#${index + 1}  ·  Page ${result.pageNumber}  ·  ${(result.score * 100).toInt()}% match",
                                style = MaterialTheme.typography.labelMedium
                            )

                            Spacer(Modifier.height(4.dp))

                            // The actual text chunk from the PDF
                            Text(
                                text  = result.chunkText,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}