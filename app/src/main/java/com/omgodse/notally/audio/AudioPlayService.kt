package com.omgodse.notally.audio

import android.app.Service
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.MediaPlayer
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.content.pm.ServiceInfo
import android.os.Build
import com.omgodse.notally.R
import com.omgodse.notally.activities.PlayAudio
import android.os.IBinder
import com.omgodse.notally.miscellaneous.IO
import com.omgodse.notally.miscellaneous.Operations
import com.omgodse.notally.room.Audio
import java.io.File

class AudioPlayService : Service() {

    private var state = IDLE
    private var stateBeforeSeeking = -1
    private var errorType = 0
    private var errorCode = 0
    private lateinit var player: MediaPlayer
    private lateinit var audioManager: AudioManager
    private lateinit var focusRequest: AudioFocusRequest
    private var audio: Audio? = null
    private var foreground = false

    var onStateChange: (() -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        audioManager = getSystemService(AudioManager::class.java)
        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS ||
                    change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
                    change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                    pausePlayback()
                }
            }
            .build()
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.audio_recordings),
            NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        player = MediaPlayer()
        player.setAudioAttributes(attributes)
        player.setOnPreparedListener { setState(PREPARED) }
        player.setOnCompletionListener {
            endForegroundPlayback()
            setState(COMPLETED)
        }
        player.setOnSeekCompleteListener { setState(stateBeforeSeeking) }
        player.setOnErrorListener { _, what, extra ->
            endForegroundPlayback()
            errorType = what
            errorCode = extra
            setState(ERROR)
            return@setOnErrorListener true
        }
    }

    override fun onDestroy() {
        onStateChange = null
        endForegroundPlayback()
        player.release()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PAUSE) pausePlayback()
        // Never resume audio after a process restart without a fresh user action.
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return LocalBinder(this)
    }


    fun initialise(audio: Audio) {
        if (state == IDLE) {
            this.audio = audio
            val audioRoot = IO.getExternalAudioDirectory(application)
            if (audioRoot != null) {
                try {
                    val file = File(audioRoot, audio.name)
                    player.setDataSource(file.absolutePath)
                    setState(INITIALISED)
                    player.prepareAsync()
                } catch (exception: Exception) {
                    setIOError()
                    Operations.log(application, exception)
                }
            } else setIOError()
        }
    }

    fun play() {
        when (state) {
            PREPARED, PAUSED, COMPLETED -> startPlayback()
            STARTED -> pausePlayback()
        }
    }

    private fun startPlayback() {
        try {
            // Called from the visible playback UI, giving the FGS while-in-use
            // capabilities before Android 17 checks focus requests or audio output.
            startService(Intent(this, AudioPlayService::class.java))
            val notification = playbackNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else startForeground(NOTIFICATION_ID, notification)
            foreground = true
            if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                endForegroundPlayback()
                return
            }
            player.start()
            setState(STARTED)
        } catch (exception: Exception) {
            endForegroundPlayback()
            setIOError()
            Operations.log(application, exception)
        }
    }

    private fun pausePlayback() {
        if (state == STARTED || (state == SEEKING && stateBeforeSeeking == STARTED)) {
            player.pause()
            stateBeforeSeeking = PAUSED
            setState(PAUSED)
        }
        endForegroundPlayback()
    }

    private fun endForegroundPlayback() {
        audioManager.abandonAudioFocusRequest(focusRequest)
        if (foreground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foreground = false
        }
    }

    private fun playbackNotification(): Notification {
        val open = Intent(this, PlayAudio::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(PlayAudio.AUDIO, audio)
        val content = PendingIntent.getActivity(this, NOTIFICATION_ID, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val pause = Intent(this, AudioPlayService::class.java).setAction(ACTION_PAUSE)
        val pauseIntent = PendingIntent.getService(this, NOTIFICATION_ID, pause, PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(getString(R.string.audio_recordings))
            .setContentText(getString(R.string.tap_for_more_options))
            .setContentIntent(content)
            .setDeleteIntent(pauseIntent)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, getString(R.string.pause), pauseIntent).build())
            .build()
    }

    fun seek(milliseconds: Long) {
        if (state == PREPARED || state == STARTED || state == PAUSED || state == COMPLETED) {
            stateBeforeSeeking = state
            player.seekTo(milliseconds.toInt())
            setState(SEEKING)
        }
    }


    fun getState(): Int {
        return state
    }

    fun getErrorType(): Int {
        return errorType
    }

    fun getErrorCode(): Int {
        return errorCode
    }

    fun getCurrentPosition(): Int {
        return if (state in PREPARED..COMPLETED) player.currentPosition else 0
    }


    private fun setState(state: Int) {
        this.state = state
        onStateChange?.invoke()
    }

    private fun setIOError() {
        errorCode = 0
        errorType = 15
        setState(ERROR)
    }

    companion object {
        private const val CHANNEL_ID = "com.omgodse.audioPlayback"
        private const val NOTIFICATION_ID = 3
        internal const val ACTION_PAUSE = "com.omgodse.notally.PAUSE_AUDIO"

        const val IDLE = 0
        const val INITIALISED = 1
        const val PREPARED = 2
        const val STARTED = 3
        const val PAUSED = 4
        const val SEEKING = 5
        const val COMPLETED = 6
        const val ERROR = 7
    }
}