package com.isycat.compose.tts

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.DataLine
import kotlin.math.sin

interface SpeechGenerator {
    suspend fun generateSpeech(text: String, voice: String, speed: Double): ByteArray?
}

open class TextToSpeechService(
    private val generator: SpeechGenerator,
    private val cacheDir: File,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : AutoCloseable {
    private var currentClip: Clip? = null
    private var preferredVoice: String = "alloy"

    init {
        cacheDir.mkdirs()
    }

    fun speak(
        text: String,
        voice: String? = null,
        speed: Double = 1.0,
        enableCache: Boolean = true
    ) {
        val voiceToUse = voice ?: preferredVoice
        scope.launch {
            try {
                val audioData = if (enableCache) {
                    getCachedOrGenerateSpeech(text, voiceToUse, speed)
                } else {
                    generator.generateSpeech(text, voiceToUse, speed)
                }

                if (audioData != null) {
                    playAudio(audioData)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setPreferredVoice(voice: String) {
        preferredVoice = voice
    }

    private fun stop() {
        currentClip?.let {
            if (it.isRunning) {
                it.stop()
            }
            it.close()
        }
        currentClip = null
    }

    private suspend fun getCachedOrGenerateSpeech(text: String, voice: String, speed: Double): ByteArray? {
        val cacheKey = generateCacheKey(text, voice, speed)
        val cacheFile = File(cacheDir, "$cacheKey.mp3")

        if (cacheFile.exists()) {
            return cacheFile.readBytes()
        }

        val audioData = generator.generateSpeech(text, voice, speed)
        if (audioData != null) {
            runCatching { cacheFile.writeBytes(audioData) }
        }
        return audioData
    }

    private fun generateCacheKey(text: String, voice: String, speed: Double): String {
        val input = "$text|$voice|$speed"
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun playAudio(audioData: ByteArray) {
        try {
            stop()
            val inputStream = ByteArrayInputStream(audioData)
            val audioStream = AudioSystem.getAudioInputStream(inputStream)
            val format = audioStream.format
            val info = DataLine.Info(Clip::class.java, format)
            val clip = AudioSystem.getLine(info) as Clip
            clip.open(audioStream)
            currentClip = clip
            clip.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Plays a simple beep sound
     */
    suspend fun playBeep() {
        withContext(Dispatchers.IO) {
            try {
                // Generate a simple beep tone
                val sampleRate = 8000.0
                val duration = 0.5 // 500ms for better audibility
                val frequency = 1760.0 // A6 note - higher pitch for clarity
                val samples = (sampleRate * duration).toInt()
                val buffer = ByteArray(samples)

                for (i in 0 until samples) {
                    val angle = 2.0 * Math.PI * i / (sampleRate / frequency)
                    buffer[i] = (sin(angle) * 127.0).toInt().toByte()
                }

                // Play the beep using Java Sound API
                val audioFormat = javax.sound.sampled.AudioFormat(
                    sampleRate.toFloat(),
                    8,
                    1,
                    true, // signed
                    false // little-endian
                )

                val clip = AudioSystem.getClip()
                val audioInputStream = javax.sound.sampled.AudioInputStream(
                    buffer.inputStream(),
                    audioFormat,
                    samples.toLong()
                )

                clip.open(audioInputStream)
                clip.start()

                // Wait for playback to complete
                while (clip.isRunning) {
                    Thread.sleep(10)
                }

                clip.close()
            } catch (e: Exception) {
                println("[TTS] Error playing beep: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    override fun close() {
        stop()
    }
}
