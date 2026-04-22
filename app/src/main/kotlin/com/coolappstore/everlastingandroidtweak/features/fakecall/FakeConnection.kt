package com.coolappstore.everlastingandroidtweak.features.fakecall

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.telecom.CallAudioState
import android.telecom.Connection
import android.telecom.DisconnectCause
import android.telecom.PhoneAccount
import android.telecom.TelecomManager

class FakeConnection(
    private val context: Context,
    private val callerName: String,
    private val callerNumber: String
) : Connection() {

    private var mediaPlayer: MediaPlayer? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private val audioManager: AudioManager = context.getSystemService(AudioManager::class.java)

    init {
        val displayName = callerName.ifBlank { callerNumber }
        setAddress(Uri.fromParts(PhoneAccount.SCHEME_TEL, callerNumber, null), TelecomManager.PRESENTATION_ALLOWED)
        setCallerDisplayName(displayName, TelecomManager.PRESENTATION_ALLOWED)
        setConnectionCapabilities(CAPABILITY_MUTE)
        setAudioModeIsVoip(true)
        setInitializing()
        setRinging()
    }

    override fun onAnswer() {
        setActive()
        runCatching { audioManager.mode = AudioManager.MODE_IN_COMMUNICATION }
        runCatching {
            setAudioRoute(CallAudioState.ROUTE_EARPIECE)
            audioManager.isSpeakerphoneOn = false
        }
        startVoicePlayback()
    }

    override fun onReject()     { disconnectWithCause(DisconnectCause.REJECTED) }
    override fun onDisconnect() { disconnectWithCause(DisconnectCause.LOCAL) }
    override fun onAbort()      { disconnectWithCause(DisconnectCause.CANCELED) }

    override fun onCallAudioStateChanged(state: CallAudioState) {
        super.onCallAudioStateChanged(state)
        runCatching { audioManager.isMicrophoneMute = state.isMuted }
        runCatching {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            when {
                state.route and CallAudioState.ROUTE_SPEAKER != 0 -> audioManager.isSpeakerphoneOn = true
                else -> audioManager.isSpeakerphoneOn = false
            }
        }
    }

    private fun startVoicePlayback() {
        stopAndReleasePlayer()
        requestAudioFocus()

        // Try to load a custom audio URI from prefs
        val prefs = context.getSharedPreferences("fake_call_prefs", Context.MODE_PRIVATE)
        val uriStr = prefs.getString("audio_uri", "").orEmpty()
        if (uriStr.isBlank()) return

        runCatching {
            val uri = Uri.parse(uriStr)
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(attrs)
                setDataSource(context, uri)
                isLooping = true
                prepare(); start()
            }
        }
    }

    private fun disconnectWithCause(code: Int) {
        stopAndReleasePlayer()
        runCatching { audioManager.mode = AudioManager.MODE_NORMAL }
        setDisconnected(DisconnectCause(code))
        destroy()
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build())
                .setOnAudioFocusChangeListener { }
                .build()
            audioFocusRequest = req
            runCatching { audioManager.requestAudioFocus(req) }
        }
    }

    private fun stopAndReleasePlayer() {
        mediaPlayer?.run { runCatching { if (isPlaying) stop() }; reset(); release() }
        mediaPlayer = null
        audioFocusRequest?.let { runCatching { audioManager.abandonAudioFocusRequest(it) } }
        audioFocusRequest = null
    }
}
