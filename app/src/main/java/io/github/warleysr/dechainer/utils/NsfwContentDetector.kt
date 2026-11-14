package io.github.warleysr.dechainer.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Wraps the bundled NudeNet v3 detector (YOLOv8n-based, assets/models/nsfw_model.tflite).
 * Model I/O: 320x320 RGB float32 input normalized to [0, 1] with aspect-ratio preserving letterbox;
 * output shape is [1, 22, 2100]: 4 bounding box coordinates + 18 anatomical classes.
 *
 * Not thread-safe: callers must serialize calls to [predict].
 */
class NsfwContentDetector(context: Context) {

    companion object {
        private const val MODEL_PATH = "models/nsfw_model.tflite"
        const val INPUT_SIZE = 320
        private const val BYTES_PER_CHANNEL = 4 // float32
        private const val NUM_CHANNELS = 22
        private const val NUM_ANCHORS = 2100

        // The 18 standard class labels recognized by NudeNet v3 detector in order:
        val LABELS = listOf(
            "FEMALE_GENITALIA_COVERED", // 0
            "FACE_FEMALE",             // 1
            "BUTTOCKS_EXPOSED",        // 2
            "FEMALE_BREAST_EXPOSED",   // 3
            "FEMALE_GENITALIA_EXPOSED",// 4
            "MALE_BREAST_EXPOSED",     // 5
            "ANUS_EXPOSED",            // 6
            "FEET_EXPOSED",            // 7
            "BELLY_COVERED",           // 8
            "FEET_COVERED",            // 9
            "ARMPITS_COVERED",         // 10
            "ARMPITS_EXPOSED",         // 11
            "FACE_MALE",               // 12
            "BELLY_EXPOSED",           // 13
            "MALE_GENITALIA_EXPOSED",  // 14
            "ANUS_COVERED",            // 15
            "FEMALE_BREAST_COVERED",   // 16
            "BUTTOCKS_COVERED"          // 17
        )
    }

    private val interpreter: Interpreter = Interpreter(
        loadModelFile(context),
        Interpreter.Options().apply { setNumThreads(2) }
    )

    // Reused across calls to avoid per-frame allocations/GC pressure.
    private val inputBuffer: ByteBuffer = ByteBuffer
        .allocateDirect(INPUT_SIZE * INPUT_SIZE * 3 * BYTES_PER_CHANNEL)
        .order(ByteOrder.nativeOrder())
    private val output = Array(1) { Array(NUM_CHANNELS) { FloatArray(NUM_ANCHORS) } }
    private val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
    private val letterboxPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    private fun loadModelFile(context: Context): MappedByteBuffer {
        context.assets.openFd(MODEL_PATH).use { fd ->
            FileInputStream(fd.fileDescriptor).use { stream ->
                return stream.channel.map(
                    FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength
                )
            }
        }
    }

    /**
     * Resizes [source] into a 320x320 square using letterboxing (preserving aspect ratio
     * and padding the remaining space with neutral gray, RGB 114, 114, 114).
     */
    private fun letterbox(source: Bitmap): Bitmap {
        if (source.width == INPUT_SIZE && source.height == INPUT_SIZE) {
            return source
        }
        val w = source.width
        val h = source.height
        val scale = minOf(INPUT_SIZE.toFloat() / w, INPUT_SIZE.toFloat() / h)
        val scaledW = (w * scale).toInt().coerceAtLeast(1)
        val scaledH = (h * scale).toInt().coerceAtLeast(1)
        val dx = (INPUT_SIZE - scaledW) / 2
        val dy = (INPUT_SIZE - scaledH) / 2

        val target = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(target)
        canvas.drawColor(Color.rgb(114, 114, 114))
        val srcRect = Rect(0, 0, w, h)
        val dstRect = Rect(dx, dy, dx + scaledW, dy + scaledH)
        canvas.drawBitmap(source, srcRect, dstRect, letterboxPaint)
        return target
    }

    /**
     * Runs the detector on [bitmap] and returns the maximum detection confidence score
     * across all anchor boxes for each class in [LABELS] order.
     * Runs inference synchronously on the calling thread — invoke from a background dispatcher.
     */
    fun predict(bitmap: Bitmap): FloatArray {
        val scaled = letterbox(bitmap)
        scaled.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        if (scaled !== bitmap) scaled.recycle()

        inputBuffer.rewind()
        for (pixel in pixels) {
            inputBuffer.putFloat(((pixel shr 16) and 0xFF) / 255f)
            inputBuffer.putFloat(((pixel shr 8) and 0xFF) / 255f)
            inputBuffer.putFloat((pixel and 0xFF) / 255f)
        }

        interpreter.run(inputBuffer, output)

        val maxScores = FloatArray(LABELS.size)
        val outChannels = output[0]
        for (c in 0 until LABELS.size) {
            val channelData = outChannels[4 + c]
            var max = 0f
            for (i in 0 until NUM_ANCHORS) {
                val score = channelData[i]
                if (score > max) max = score
            }
            maxScores[c] = max
        }
        return maxScores
    }

    /**
     * Maximum detection confidence among [categories] (label names, see [LABELS]) from a [predict] result.
     * With object detection, each detection proposal is an independent candidate; the maximum confidence
     * among the target categories determines the detection strength.
     */
    fun unsafeScore(scores: FloatArray, categories: Set<String>): Float {
        var max = 0f
        for (category in categories) {
            val index = LABELS.indexOf(category)
            if (index >= 0) {
                val score = scores[index]
                if (score > max) max = score
            }
        }
        return max
    }

    fun close() {
        interpreter.close()
    }
}
