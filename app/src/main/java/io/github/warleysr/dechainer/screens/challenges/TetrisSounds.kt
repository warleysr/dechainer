package io.github.warleysr.dechainer.screens.challenges

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

private const val SAMPLE_RATE = 22_050

internal enum class TetrisSound(val volume: Float) {
    MOVE(0.25f),
    ROTATE(0.35f),
    LOCK(0.5f),
    HARD_DROP(0.7f),
    CLEAR(0.55f),
    CLEAR_BIG(0.6f),
    LEVEL_UP(0.55f),
    TOP_OUT(0.5f);

    fun synthesize(): ShortArray = when (this) {
        MOVE -> tone(18, 1400.0, 1400.0, decay = 12.0)
        ROTATE -> tone(45, 700.0, 1150.0, decay = 6.0)
        LOCK -> tone(70, 230.0, 120.0, decay = 5.0, noise = 0.15)
        HARD_DROP -> tone(120, 190.0, 60.0, decay = 4.0, noise = 0.35)
        CLEAR -> arpeggio(listOf(659.0, 784.0, 988.0), 60)
        CLEAR_BIG -> arpeggio(listOf(523.0, 659.0, 784.0, 1047.0, 1319.0), 60)
        LEVEL_UP -> arpeggio(listOf(784.0, 988.0, 1175.0, 1568.0), 85)
        TOP_OUT -> tone(520, 440.0, 110.0, decay = 1.5, triangle = true)
    }
}

internal class TetrisSounds(private val context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private val ids = ConcurrentHashMap<TetrisSound, Int>()
    private val audioManager = context.getSystemService(AudioManager::class.java)

    @Volatile
    private var released = false

    fun load() {
        val dir = File(context.cacheDir, "tetris_sounds").apply { mkdirs() }
        TetrisSound.entries.forEach { sound ->
            if (released) return
            val file = File(dir, "${sound.name.lowercase()}.wav")
            file.writeBytes(wav(sound.synthesize()))
            runCatching { pool.load(file.path, 1) }.onSuccess { ids[sound] = it }
        }
    }

    fun play(sound: TetrisSound) {
        if (released || audioManager?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val id = ids[sound] ?: return
        pool.play(id, sound.volume, sound.volume, 1, 0, 1f)
    }

    fun release() {
        released = true
        pool.release()
    }
}

private fun tone(
    durationMs: Int,
    startHz: Double,
    endHz: Double,
    decay: Double,
    noise: Double = 0.0,
    triangle: Boolean = false
): ShortArray {
    val count = SAMPLE_RATE * durationMs / 1000
    val attack = SAMPLE_RATE * 3 / 1000
    var phase = 0.0
    return ShortArray(count) { i ->
        val t = i.toDouble() / count
        val hz = startHz + (endHz - startHz) * t
        phase += 2 * PI * hz / SAMPLE_RATE
        val wave = if (triangle) 2 / PI * asin(sin(phase)) else sin(phase)
        val mixed = wave * (1 - noise) + (Random.nextDouble() * 2 - 1) * noise
        val envelope = minOf(1.0, i.toDouble() / attack) * exp(-decay * t)
        (mixed * envelope * Short.MAX_VALUE * 0.9).toInt().toShort()
    }
}

private fun arpeggio(notes: List<Double>, noteMs: Int): ShortArray =
    notes.map { tone(noteMs, it, it, decay = 3.0, triangle = true) }
        .fold(ShortArray(0)) { acc, part -> acc + part }

private fun wav(samples: ShortArray): ByteArray {
    val dataSize = samples.size * 2
    return ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray())
        putInt(36 + dataSize)
        put("WAVE".toByteArray())
        put("fmt ".toByteArray())
        putInt(16)
        putShort(1)
        putShort(1)
        putInt(SAMPLE_RATE)
        putInt(SAMPLE_RATE * 2)
        putShort(2)
        putShort(16)
        put("data".toByteArray())
        putInt(dataSize)
        samples.forEach { putShort(it) }
    }.array()
}
