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
        // .onnx_data is an ONNX external-data sidecar; must exist next to the .onnx file
        prepareAsset("bge_m3_int8.onnx_data") {}
        val modelFile     = prepareAsset("bge_m3_int8.onnx", onProgress)
        val tokenizerFile = prepareAsset("tokenizer.json") {}

        tokenizer  = XlmRoBertaTokenizer.load(tokenizerFile.inputStream())
        val opts   = OrtSession.SessionOptions().apply { setIntraOpNumThreads(4) }
        session    = env.createSession(modelFile.absolutePath, opts)
        inputNames = session.inputNames
    }

    suspend fun embed(text: String): FloatArray = withContext(Dispatchers.Default) {
        embedSync(text)
    }

    private fun embedSync(text: String): FloatArray {
        val (ids, mask) = tokenizer.encode(text)
        val seqLen = ids.size.toLong()

        val inputs = HashMap<String, OnnxTensor>()
        inputs["input_ids"]      = OnnxTensor.createTensor(env, LongBuffer.wrap(ids),  longArrayOf(1, seqLen))
        inputs["attention_mask"] = OnnxTensor.createTensor(env, LongBuffer.wrap(mask), longArrayOf(1, seqLen))
        if ("token_type_ids" in inputNames) {
            inputs["token_type_ids"] = OnnxTensor.createTensor(
                env, LongBuffer.wrap(LongArray(ids.size)), longArrayOf(1, seqLen))
        }

        val output    = session.run(inputs)
        @Suppress("UNCHECKED_CAST")
        val hidden    = output[0].value as Array<Array<FloatArray>>
        val embedding = meanPool(hidden[0], mask)
        for (t in inputs.values) t.close()
        output.close()
        return normalize(embedding)
    }

    private fun meanPool(hidden: Array<FloatArray>, mask: LongArray): FloatArray {
        val dim    = hidden[0].size
        val result = FloatArray(dim)
        var count  = 0f
        for (i in hidden.indices) {
            if (mask[i] == 1L) {
                for (j in 0 until dim) result[j] += hidden[i][j]
                count++
            }
        }
        if (count > 0f) {
            for (i in result.indices) result[i] /= count
        }
        return result
    }

    // Copies an asset to internal storage on first run; reports progress via onProgress
    private fun prepareAsset(name: String, onProgress: (Float) -> Unit): File {
        val dest = File(context.filesDir, name)
        if (dest.exists()) return dest
        val input = try {
            context.assets.open(name)
        } catch (e: java.io.FileNotFoundException) {
            return dest
        }
        input.use { src ->
            val total = src.available().toLong().coerceAtLeast(1L)
            FileOutputStream(dest).use { out ->
                val buf  = ByteArray(256 * 1024)
                var done = 0L
                var n    = src.read(buf)
                while (n != -1) {
                    out.write(buf, 0, n)
                    done += n
                    onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    n = src.read(buf)
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
