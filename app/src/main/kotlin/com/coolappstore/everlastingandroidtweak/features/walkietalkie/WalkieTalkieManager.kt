package com.coolappstore.everlastingandroidtweak.features.walkietalkie

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.*
import android.net.wifi.WifiManager
import androidx.core.app.NotificationCompat
import com.coolappstore.everlastingandroidtweak.EverlastingApp
import kotlinx.coroutines.*
import java.io.*
import java.net.*
import java.text.SimpleDateFormat
import java.util.*

data class ChatMessage(val text: String, val isMine: Boolean, val time: String)

class WalkieTalkieManager(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val SAMPLE_RATE = 16000
    private val VOICE_PORT  = 50005
    private val CHAT_PORT   = 50006  // TCP for reliable text delivery

    private var serverSocket: DatagramSocket? = null
    private var chatServerSocket: ServerSocket? = null
    private var targetAddress: InetAddress? = null   // null = broadcast

    var isTalking = false
    var statusCallback: ((String) -> Unit)? = null
    var onMessageReceived: ((ChatMessage) -> Unit)? = null

    // ── Auto-detect LAN broadcast address ─────────────────────────────────────
    fun getLocalIp(): String {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val ip = wm.connectionInfo.ipAddress
            if (ip != 0) {
                "${ip and 0xff}.${(ip shr 8) and 0xff}.${(ip shr 16) and 0xff}.${(ip shr 24) and 0xff}"
            } else {
                NetworkInterface.getNetworkInterfaces()?.asSequence()
                    ?.flatMap { it.inetAddresses.asSequence() }
                    ?.firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
                    ?.hostAddress ?: ""
            }
        } catch (_: Exception) {
            NetworkInterface.getNetworkInterfaces()?.asSequence()
                ?.flatMap { it.inetAddresses.asSequence() }
                ?.firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
                ?.hostAddress ?: ""
        }
    }

    /** Derive broadcast address from local IP (e.g. 192.168.1.255) */
    fun getBroadcastAddress(): InetAddress {
        return try {
            val localIp = getLocalIp()
            val parts   = localIp.split(".")
            if (parts.size == 4) InetAddress.getByName("${parts[0]}.${parts[1]}.${parts[2]}.255")
            else                 InetAddress.getByName("255.255.255.255")
        } catch (_: Exception) { InetAddress.getByName("255.255.255.255") }
    }

    fun setTarget(address: String) {
        targetAddress = if (address.isBlank()) null
                        else try { InetAddress.getByName(address) } catch (_: Exception) { null }
    }

    // ── Voice ─────────────────────────────────────────────────────────────────

    fun startTalking() {
        if (isTalking) return
        isTalking = true
        statusCallback?.invoke("Transmitting…")
        scope.launch {
            val bufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val recorder = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize)
            val socket = DatagramSocket().also { it.broadcast = true }
            recorder.startRecording()
            val buf = ByteArray(bufSize)
            try {
                while (isTalking) {
                    val read = recorder.read(buf, 0, bufSize)
                    if (read > 0) {
                        val dest = targetAddress ?: getBroadcastAddress()
                        try { socket.send(DatagramPacket(buf, read, dest, VOICE_PORT)) } catch (_: Exception) {}
                    }
                }
            } finally { recorder.stop(); recorder.release(); socket.close() }
        }
    }

    fun stopTalking() {
        isTalking = false
        statusCallback?.invoke("Ready")
    }

    fun startListening() {
        scope.launch {
            val bufSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(bufSize).setTransferMode(AudioTrack.MODE_STREAM).build()
            serverSocket = try { DatagramSocket(VOICE_PORT) } catch (_: Exception) { return@launch }
            serverSocket?.broadcast = true
            val buf = ByteArray(bufSize)
            val packet = DatagramPacket(buf, buf.size)
            track.play()
            try {
                while (!serverSocket!!.isClosed) {
                    try {
                        serverSocket?.receive(packet)
                        track.write(packet.data, 0, packet.length)
                    } catch (_: Exception) { break }
                }
            } finally { track.stop(); track.release(); serverSocket?.close() }
        }
    }

    fun stopListening() {
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
    }

    // ── Text chat via TCP ────────────────────────────────────────────────────

    fun startChatServer() {
        scope.launch {
            chatServerSocket = try { ServerSocket(CHAT_PORT) } catch (_: Exception) { return@launch }
            try {
                while (!chatServerSocket!!.isClosed) {
                    val client = try { chatServerSocket?.accept() } catch (_: Exception) { break } ?: break
                    launch {
                        try {
                            val line = client.getInputStream().bufferedReader().readLine() ?: return@launch
                            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                            val msg  = ChatMessage(line, isMine = false, time = time)
                            onMessageReceived?.invoke(msg)
                            // Post notification when app might be in background
                            postChatNotification(line)
                        } catch (_: Exception) {} finally { try { client.close() } catch (_: Exception) {} }
                    }
                }
            } finally { chatServerSocket?.close() }
        }
    }

    fun stopChatServer() {
        try { chatServerSocket?.close() } catch (_: Exception) {}
        chatServerSocket = null
    }

    fun sendChatMessage(text: String) {
        scope.launch {
            val dest = targetAddress ?: getBroadcastAddress()
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(dest, CHAT_PORT), 2000)
                socket.getOutputStream().bufferedWriter().use { it.write(text); it.newLine(); it.flush() }
                socket.close()
            } catch (_: Exception) {
                // Fallback: UDP broadcast for text (less reliable but works without target IP)
                try {
                    val udp = DatagramSocket().also { it.broadcast = true }
                    val payload = text.toByteArray(Charsets.UTF_8)
                    udp.send(DatagramPacket(payload, payload.size, getBroadcastAddress(), CHAT_PORT + 1))
                    udp.close()
                } catch (_: Exception) {}
            }
        }
    }

    private fun postChatNotification(text: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(
            CHAT_NOTIF_ID,
            NotificationCompat.Builder(context, EverlastingApp.CHANNEL_ALERTS)
                .setContentTitle("📻 Walkie Talkie Message")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
        )
    }

    fun release() {
        stopTalking()
        stopListening()
        stopChatServer()
        scope.cancel()
    }

    companion object {
        private const val CHAT_NOTIF_ID = 2001
    }
}
