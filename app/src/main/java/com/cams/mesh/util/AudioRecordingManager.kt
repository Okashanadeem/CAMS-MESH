package com.cams.mesh.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.io.FileOutputStream

class AudioRecordingManager(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var currentOutputFile: File? = null

    fun startRecording(): File? {
        stopRecording()
        return try {
            val file = File(context.cacheDir, "audio_rec_${System.currentTimeMillis()}.m4a")
            currentOutputFile = file

            val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(FileOutputStream(file).fd)
                prepare()
                start()
            }

            recorder = mediaRecorder
            file
        } catch (e: Exception) {
            e.printStackTrace()
            stopRecording()
            null
        }
    }

    fun stopRecording(): File? {
        val file = currentOutputFile
        try {
            recorder?.apply {
                stop()
                reset()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        recorder = null
        currentOutputFile = null
        return if (file != null && file.exists() && file.length() > 0) file else null
    }

    fun isRecording(): Boolean = recorder != null
}
