package com.qualcomm.rag.ml

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.qualcomm.rag.data.VectorStore.Companion.normalize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.nio.LongBuffer

class BgeM3Embedder(private val context: Context) : Closeable {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private lateinit var session: OrtSession
    private lateinit var tokenizer: XlmRoBertaTokenizer
    private lateinit var inputNames: Set<String>

    suspend fun initialize(onProgress: (Float) -> Unit = {}) = withContext(Dispatchers.IO) {
        // Copy companion data file first (no-op if absent)
        prepareAsset("bge_m3_int8.onnx_data") {}
        val modelFile     = prepareAsset("bge_m3_int8.onnx", onProgress)
        val tokenizerFile = prepareAsset("tokenizer.json") {}

        // Pure-Kotlin tokenizer — no native .so needed
        tokenizer = XlmRoBertaTokenizer.load(tokenizerFile.inputStream())

        // ONNX session
        val opts = OrtSession.SessionOptions().apply { setIntraOpNumThreads(4) }
        session    = env.createSession(modelFile.absolutePath, opts)
        inputNames = session.inputNames
    }

    suspend fun embed(text: String): FloatArray = withContext(Dispatchers.Default) {
        embedSync(text)
    }

    suspend fun embedBatch(texts: List<String>): List<FloatArray> =
        withContext(Dispatchers.Default) { texts.map { embedSync(it) } }

    private fun embedSync(text: String): FloatArray {
        val (ids, mask) = tokenizer.encode(text)
        val seqLen = ids.size.toLong()

        val inputs = buildMap<String, OnnxTensor> {
            put("input_ids",      OnnxTensor.createTensor(env, LongBuffer.wrap(ids),  longArrayOf(1, seqLen)))
            put("attention_mask", OnnxTensor.createTensor(env, LongBuffer.wrap(mask), longArrayOf(1, seqLen)))
            if ("token_type_ids" in inputNames) {
                put("token_type_ids", OnnxTensor.createTensor(
                    env, LongBuffer.wrap(LongArray(ids.size)), longArrayOf(1, seqLen)))
            }
        }

        val output = session.run(inputs)
        @Suppress("UNCHECKED_CAST")
        val hidden = output[0].value as Array<Array<FloatArray>>
        val embedding = meanPool(hidden[0], mask)
        inputs.values.forEach { it.close() }
        output.close()
        return normalize(embedding)
    }

    private fun meanPool(hidden: Array<FloatArray>, mask: LongArray): FloatArray {
        val dim = hidden[0].size
        val result = FloatArray(dim)
        var count = 0f
        for (i in hidden.indices) {
            if (mask[i] == 1L) { hidden[i].forEachIndexed { j, v -> result[j] += v }; count++ }
        }
        if (count > 0f) result.forEachIndexed { i, _ -> result[i] /= count }
        return result
    }

    private fun prepareAsset(name: String, onProgress: (Float) -> Unit): File {
        val dest = File(context.filesDir, name)
        if (dest.exists()) return dest
        val input = try { context.assets.open(name) }
                    catch (e: java.io.FileNotFoundException) { return dest }
        input.use { src ->
            val total = src.available().toLong().coerceAtLeast(1L)
            FileOutputStream(dest).use { out ->
                val buf = ByteArray(256 * 1024); var done = 0L; var n: Int
                while (src.read(buf).also { n = it } != -1) {
                    out.write(buf, 0, n); done += n
                    onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        return dest
    }

    override fun close() {
        if (::session.isInitialized)   session.close()
        if (::tokenizer.isInitialized) tokenizer.close()
        env.close()
    }
}
