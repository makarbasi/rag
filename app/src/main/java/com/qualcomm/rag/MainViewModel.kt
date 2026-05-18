package com.qualcomm.rag

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qualcomm.rag.data.SearchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = RagRepository(app)

    // What to show on screen — just one line of text
    val status = MutableStateFlow("Loading AI model…")

    // True while we're doing heavy work (disable buttons)
    val isBusy = MutableStateFlow(true)

    // True once a PDF is indexed and ready to search
    val isReady = MutableStateFlow(false)

    // The search results (empty until user searches)
    val results: StateFlow<List<SearchResult>> = MutableStateFlow(emptyList())
    private val _results = results as MutableStateFlow

    // Load the ONNX model when the app starts (copies it from assets on first run)
    init {
        viewModelScope.launch {
            try {
                repo.embedder.initialize { progress ->
                    status.value = "Loading model: ${(progress * 100).toInt()}%"
                }
                status.value = "Pick a PDF to get started"
            } catch (e: Exception) {
                status.value = "Model load failed: ${e.message}"
            } finally {
                isBusy.value = false
            }
        }
    }

    // Index the PDF the user picked. Clears the previous one automatically.
    fun indexPdf(uri: Uri, name: String) {
        viewModelScope.launch {
            isBusy.value = true
            isReady.value = false
            _results.value = emptyList()
            try {
                repo.indexDocument(uri, name, getApplication()) { msg ->
                    status.value = msg
                }
                status.value = "Ready — ask anything about \"$name\""
                isReady.value = true
            } catch (e: Exception) {
                status.value = "Error: ${e.message}"
            } finally {
                isBusy.value = false
            }
        }
    }

    // Run a search against the indexed PDF
    fun search(query: String) {
        viewModelScope.launch {
            isBusy.value = true
            status.value = "Searching…"
            try {
                _results.value = repo.search(query)
                status.value = if (_results.value.isEmpty()) "No results found" else "Top ${_results.value.size} results"
            } catch (e: Exception) {
                status.value = "Search failed: ${e.message}"
            } finally {
                isBusy.value = false
            }
        }
    }
}
