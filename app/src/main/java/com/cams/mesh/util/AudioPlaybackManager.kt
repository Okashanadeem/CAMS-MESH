package com.cams.mesh.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class AudioPlaybackManager(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    
    private val _currentlyPlayingPath = MutableStateFlow<String?>(null)
    val currentlyPlayingPath: StateFlow<String?> = _currentlyPlayingPath.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    fun togglePlay(filePathOrUri: String) {
        if (_currentlyPlayingPath.value == filePathOrUri && _isPlaying.value) {
            pause()
        } else if (_currentlyPlayingPath.value == filePathOrUri && mediaPlayer != null) {
            resume()
        } else {
            play(filePathOrUri)
        }
    }

    fun play(filePathOrUri: String) {
        stop()
        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                if (filePathOrUri.startsWith("content://") || filePathOrUri.startsWith("file://")) {
                    setDataSource(context, Uri.parse(filePathOrUri))
                } else {
                    val file = File(filePathOrUri)
                    if (file.exists()) {
                        setDataSource(file.absolutePath)
                    } else {
                        return
                    }
                }
                prepare()
                start()
                setOnCompletionListener {
                    _isPlaying.value = false
                    _currentlyPlayingPath.value = null
                }
                setOnErrorListener { _, _, _ ->
                    stop()
                    true
                }
            }
            mediaPlayer = player
            _currentlyPlayingPath.value = filePathOrUri
            _isPlaying.value = true
        } catch (e: Exception) {
            e.printStackTrace()
            stop()
        }
    }

    fun pause() {
        try {
            mediaPlayer?.pause()
            _isPlaying.value = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun resume() {
        try {
            mediaPlayer?.start()
            _isPlaying.value = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stop() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                reset()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaPlayer = null
        _isPlaying.value = false
        _currentlyPlayingPath.value = null
    }

    fun release() {
        stop()
    }
}
