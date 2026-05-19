package com.qualcomm.rag

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import java.io.File

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                RagScreen(vm = vm)
            }
        }
    }
}

@Composable
fun RagScreen(vm: MainViewModel) {

    val status  by vm.status.collectAsState()
    val isBusy  by vm.isBusy.collectAsState()
    val isReady by vm.isReady.collectAsState()
    val results by vm.results.collectAsState()

    var pdfPath by remember { mutableStateOf("") }
    var query   by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        // User types the full path to the PDF file on the device
        OutlinedTextField(
            value         = pdfPath,
            onValueChange = { pdfPath = it },
            label         = { Text("PDF file path") },
            placeholder   = { Text("/sdcard/Download/document.pdf") },
            modifier      = Modifier.fillMaxWidth(),
            singleLine    = true,
            enabled       = !isBusy
        )

        // Load the PDF from the typed path
        Button(
            onClick  = {
                val file = File(pdfPath.trim())
                vm.indexPdf(file.toUri(), file.name)
            },
            enabled  = pdfPath.isNotBlank() && !isBusy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isBusy) "Please wait…" else "Load PDF")
        }

        // One line showing what's happening
        Text(text = status, style = MaterialTheme.typography.bodyMedium)

        // Search box — only shown once a PDF is indexed
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
                    onClick = { vm.search(query) },
                    enabled = query.isNotBlank() && !isBusy
                ) {
                    Text("Search")
                }
            }

            // Results
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(results) { index, result ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text  = "#${index + 1}  ·  Page ${result.pageNumber}  ·  ${(result.score * 100).toInt()}% match",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Spacer(Modifier.height(4.dp))
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