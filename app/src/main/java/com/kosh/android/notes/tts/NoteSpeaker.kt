package com.kosh.android.notes.tts

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

/** A voice the phone's text-to-speech engine (usually Google's) can use. */
data class VoiceOption(val name: String, val label: String, val isDeviceLanguage: Boolean)

enum class Reading { IDLE, SPEAKING, PAUSED }

data class SpeakerState(
    /** The engine has started and the list of voices is known. */
    val ready: Boolean = false,
    /** No text-to-speech engine could be started on this phone. */
    val unavailable: Boolean = false,
    val voices: List<VoiceOption> = emptyList(),
    val voiceName: String? = null,
    /** 0.5 to 2.0; 1.0 is normal speed. */
    val rate: Float = 1f,
    val reading: Reading = Reading.IDLE,
    /** The part of the note being read right now (a word, or the sentence if the voice can't report words). */
    val highlight: IntRange? = null,
)

/**
 * Reads a note aloud with Android's text-to-speech and reports the word being spoken. The text is spoken a sentence at a
 * time; Android has no pause, so Pause stops and Resume starts again from the word where it stopped.
 */
class NoteSpeaker(context: Context) {

    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("note_speech", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(SpeakerState(rate = prefs.getFloat(KEY_RATE, 1f).coerceIn(MIN_RATE, MAX_RATE)))
    val state: StateFlow<SpeakerState> = _state.asStateFlow()

    private var tts: TextToSpeech? = null
    private var voiceObjects: Map<String, Voice> = emptyMap()

    private var speakable: Speakable = Speakable("", intArrayOf(0))
    private var chunks: List<SpeechChunk> = emptyList()
    /** Bumped whenever speaking is restarted or stopped, so late callbacks from the old run are ignored. */
    private var generation = 0
    private var nextToQueue = 0
    /** Where the current utterance starts in the spoken text, and the position of the word being read. */
    private var utteranceBase = 0
    private var currentChunk = 0
    private var position = 0

    init {
        tts = TextToSpeech(app) { status ->
            main.post {
                val engine = tts
                if (status != TextToSpeech.SUCCESS || engine == null) {
                    _state.update { it.copy(unavailable = true, ready = false) }
                } else {
                    engine.setOnUtteranceProgressListener(listener)
                    loadVoices(engine)
                }
            }
        }
    }

    private fun loadVoices(engine: TextToSpeech) {
        val deviceLanguage = Locale.getDefault().language
        val installed = runCatching { engine.voices.orEmpty() }.getOrDefault(emptySet())
            .filter { !it.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
            .sortedWith(
                compareByDescending<Voice> { it.locale.language == deviceLanguage }
                    .thenBy { it.locale.getDisplayName(Locale.getDefault()) }
                    .thenBy { it.isNetworkConnectionRequired }
                    .thenBy { it.name },
            )
        voiceObjects = installed.associateBy { it.name }
        val counters = HashMap<String, Int>()
        val options = installed.map { v ->
            val place = v.locale.getDisplayName(Locale.getDefault())
            val n = (counters[place] ?: 0) + 1
            counters[place] = n
            val online = if (v.isNetworkConnectionRequired) " · online" else ""
            VoiceOption(v.name, "$place · Voice $n$online", v.locale.language == deviceLanguage)
        }
        val saved = prefs.getString(KEY_VOICE, null)
        val current = runCatching { engine.voice?.name }.getOrNull()
        val chosen = listOfNotNull(saved, current).firstOrNull { it in voiceObjects } ?: options.firstOrNull()?.name
        chosen?.let { name -> voiceObjects[name]?.let { runCatching { engine.voice = it } } }
        _state.update { it.copy(ready = true, voices = options, voiceName = chosen) }
        engine.setSpeechRate(_state.value.rate)
    }

    // ---- choices -----------------------------------------------------------------------------------------------------------

    fun setVoice(name: String) {
        val engine = tts ?: return
        val voice = voiceObjects[name] ?: return
        runCatching { engine.voice = voice }
        prefs.edit().putString(KEY_VOICE, name).apply()
        _state.update { it.copy(voiceName = name) }
        restartIfSpeaking()
    }

    fun setRate(rate: Float) {
        val value = (Math.round(rate * 10) / 10f).coerceIn(MIN_RATE, MAX_RATE)
        tts?.setSpeechRate(value)
        prefs.edit().putFloat(KEY_RATE, value).apply()
        _state.update { it.copy(rate = value) }
        restartIfSpeaking()
    }

    // ---- reading -----------------------------------------------------------------------------------------------------------

    /** Starts reading [note]; from the note position [fromNoteIndex] if given. Returns false if there is nothing to read. */
    fun start(note: String, fromNoteIndex: Int = 0): Boolean {
        val engine = tts ?: return false
        if (!_state.value.ready) return false
        speakable = buildSpeakable(note)
        val maxLength = minOf(300, TextToSpeech.getMaxSpeechInputLength() - 1).coerceAtLeast(100)
        chunks = speechChunks(speakable.text, maxLength)
        if (chunks.isEmpty()) return false
        engine.setSpeechRate(_state.value.rate)
        speakFrom(speakable.positionOfNote(fromNoteIndex))
        return true
    }

    fun pause() {
        if (_state.value.reading != Reading.SPEAKING) return
        generation++
        tts?.stop()
        _state.update { it.copy(reading = Reading.PAUSED) }
    }

    fun resume() {
        if (_state.value.reading != Reading.PAUSED) return
        speakFrom(position)
    }

    fun stop() {
        generation++
        tts?.stop()
        _state.update { it.copy(reading = Reading.IDLE, highlight = null) }
    }

    fun shutdown() {
        generation++
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    private fun restartIfSpeaking() {
        if (_state.value.reading == Reading.SPEAKING) speakFrom(position)
    }

    /** Speaks from [pos] (a position in the spoken text) to the end, a sentence at a time. */
    private fun speakFrom(pos: Int) {
        val engine = tts ?: return
        generation++
        engine.stop()
        position = pos
        currentChunk = chunkIndexAt(chunks, pos)
        nextToQueue = currentChunk
        _state.update { it.copy(reading = Reading.SPEAKING) }
        queueNext(first = true)
        queueNext(first = false)
    }

    private fun queueNext(first: Boolean) {
        val engine = tts ?: return
        val index = nextToQueue
        if (index > chunks.lastIndex) return
        nextToQueue = index + 1
        val chunk = chunks[index]
        val from = if (first) maxOf(chunk.start, position).coerceAtMost(chunk.end - 1) else chunk.start
        val text = speakable.text.substring(from, chunk.end)
        val id = "$generation:$index:$from"
        val mode = if (first) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        engine.speak(text, mode, Bundle(), id)
    }

    private fun highlightSpoken(from: Int, to: Int) {
        _state.update { it.copy(highlight = speakable.noteRange(from, to).takeIf { r -> !r.isEmpty() }) }
    }

    private val listener = object : UtteranceProgressListener() {
        private fun parts(id: String?): Triple<Int, Int, Int>? {
            val p = id?.split(':') ?: return null
            if (p.size != 3) return null
            return Triple(p[0].toIntOrNull() ?: return null, p[1].toIntOrNull() ?: return null, p[2].toIntOrNull() ?: return null)
        }

        override fun onStart(utteranceId: String?) {
            val (gen, index, from) = parts(utteranceId) ?: return
            main.post {
                if (gen != generation) return@post
                currentChunk = index
                utteranceBase = from
                position = from
                // Voices that don't report words still highlight the sentence being read.
                highlightSpoken(from, chunks[index].end)
                queueNext(first = false)
            }
        }

        override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
            val (gen, _, from) = parts(utteranceId) ?: return
            main.post {
                if (gen != generation) return@post
                val a = from + start
                val b = from + end
                if (a >= speakable.text.length) return@post
                position = a
                highlightSpoken(a, b.coerceAtMost(speakable.text.length))
            }
        }

        override fun onDone(utteranceId: String?) {
            val (gen, index, _) = parts(utteranceId) ?: return
            main.post {
                if (gen != generation) return@post
                if (index >= chunks.lastIndex) {
                    generation++
                    _state.update { it.copy(reading = Reading.IDLE, highlight = null) }
                }
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            val (gen, _, _) = parts(utteranceId) ?: return
            main.post {
                if (gen != generation) return@post
                generation++
                _state.update { it.copy(reading = Reading.IDLE, highlight = null) }
            }
        }
    }

    companion object {
        const val MIN_RATE = 0.5f
        const val MAX_RATE = 2.0f
        private const val KEY_VOICE = "voice"
        private const val KEY_RATE = "rate"
    }
}
