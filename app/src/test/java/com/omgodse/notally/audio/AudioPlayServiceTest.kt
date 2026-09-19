package com.omgodse.notally.audio

import android.app.Application
import android.app.Service
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Looper
import com.omgodse.notally.miscellaneous.IO
import com.omgodse.notally.room.Audio
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.util.DataSource
import org.robolectric.util.ReflectionHelpers
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = AudioPlayServiceTest.TestApplication::class, sdk = [26, 37])
class AudioPlayServiceTest {
    class TestApplication : Application() {
        override fun getExternalMediaDirs(): Array<File> = arrayOf(cacheDir)
    }

    private lateinit var controller: ServiceController<AudioPlayService>
    private lateinit var service: AudioPlayService
    private lateinit var player: MediaPlayer
    private lateinit var manager: AudioManager

    @Before
    fun prepareRecording() {
        controller = Robolectric.buildService(AudioPlayService::class.java).create()
        service = controller.get()
        player = ReflectionHelpers.getField(service, "player")
        manager = service.getSystemService(AudioManager::class.java)
        val file = File(requireNotNull(IO.getExternalAudioDirectory(service.application)), "test.m4a")
        ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(file.absolutePath),
            ShadowMediaPlayer.MediaInfo(60_000, 0))
        service.initialise(Audio(file.name, 60_000L, 0L))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(AudioPlayService.PREPARED, service.getState())
    }

    @After
    fun destroyService() {
        controller.destroy()
    }

    @Test
    fun playPromotesServiceAndPauseReleasesFocusAndNotification() {
        assertNull(shadowOf(service).lastForegroundNotification)
        service.play()
        assertEquals(AudioPlayService.STARTED, service.getState())
        assertTrue(player.isPlaying)
        assertNotNull(shadowOf(service).lastForegroundNotification)
        assertNotNull(shadowOf(manager).lastAudioFocusRequest)
        service.play()
        assertEquals(AudioPlayService.PAUSED, service.getState())
        assertFalse(player.isPlaying)
        assertTrue(shadowOf(service).isForegroundStopped)
        assertNotNull(shadowOf(manager).lastAbandonedAudioFocusRequest)
    }

    @Test
    fun deniedFocusDoesNotPlayOrLeaveForegroundService() {
        shadowOf(manager).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        service.play()
        assertEquals(AudioPlayService.PREPARED, service.getState())
        assertFalse(player.isPlaying)
        assertTrue(shadowOf(service).isForegroundStopped)
    }

    @Test
    fun notificationCanPausePlayback() {
        service.play()
        val notification = requireNotNull(shadowOf(service).lastForegroundNotification)
        val intent = shadowOf(notification.actions.single().actionIntent).savedIntent
        assertEquals(AudioPlayService.ACTION_PAUSE, intent.action)
        assertEquals(Service.START_NOT_STICKY, service.onStartCommand(intent, 0, 1))
        assertEquals(AudioPlayService.PAUSED, service.getState())
        assertFalse(player.isPlaying)
        assertTrue(shadowOf(service).isForegroundStopped)
    }

    @Test
    fun focusLossPausesWithoutAutomaticResume() {
        service.play()
        val listener = requireNotNull(shadowOf(manager).lastAudioFocusRequest).listener
        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        assertEquals(AudioPlayService.PAUSED, service.getState())
        assertFalse(player.isPlaying)
        assertTrue(shadowOf(service).isForegroundStopped)
        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        assertFalse(player.isPlaying)
    }

    @Test
    fun completionReleasesForegroundAndFocus() {
        service.play()
        shadowOf(player).invokeCompletionListener()
        assertEquals(AudioPlayService.COMPLETED, service.getState())
        assertTrue(shadowOf(service).isForegroundStopped)
        assertNotNull(shadowOf(manager).lastAbandonedAudioFocusRequest)
    }

    @Test
    fun playbackErrorReleasesForegroundAndFocus() {
        service.play()
        shadowOf(player).invokeErrorListener(MediaPlayer.MEDIA_ERROR_UNKNOWN, 0)
        assertEquals(AudioPlayService.ERROR, service.getState())
        assertTrue(shadowOf(service).isForegroundStopped)
        assertNotNull(shadowOf(manager).lastAbandonedAudioFocusRequest)
    }

    @Test
    fun processRestartDoesNotResumeAudio() {
        assertEquals(Service.START_NOT_STICKY, service.onStartCommand(null, 0, 1))
        assertFalse(player.isPlaying)
        assertNull(shadowOf(service).lastForegroundNotification)
    }
}
