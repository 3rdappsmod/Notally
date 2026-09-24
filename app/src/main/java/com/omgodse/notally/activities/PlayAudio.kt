package com.omgodse.notally.activities

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.omgodse.notally.R
import com.omgodse.notally.audio.AudioPlayService
import com.omgodse.notally.audio.LocalBinder
import com.omgodse.notally.databinding.ActivityPlayAudioBinding
import com.omgodse.notally.miscellaneous.IO
import com.omgodse.notally.miscellaneous.add
import com.omgodse.notally.room.Audio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.DateFormat

class PlayAudio : AppCompatActivity() {

    private var service: AudioPlayService? = null
    private lateinit var connection: ServiceConnection
    private var bound = false

    private lateinit var audio: Audio
    private lateinit var binding: ActivityPlayAudioBinding

    private val exportFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                writeAudioToUri(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayAudioBinding.inflate(layoutInflater)
        setContentView(binding.root)

        audio = requireNotNull(IntentCompat.getParcelableExtra(intent, AUDIO, Audio::class.java))
        binding.AudioControlView.setDuration(audio.duration)

        val intent = Intent(this, AudioPlayService::class.java)

        connection = object : ServiceConnection {

            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val service = (binder as LocalBinder<AudioPlayService>).getService()
                service.initialise(audio)
                service.onStateChange = { updateUI(service) }
                this@PlayAudio.service = service
                binding.AudioControlView.positionProvider = service::getCurrentPosition
                updateUI(service)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                binding.AudioControlView.setStarted(false)
                binding.AudioControlView.positionProvider = null
                this@PlayAudio.service = null
            }
        }

        bound = bindService(intent, connection, BIND_AUTO_CREATE)

        binding.Play.setOnClickListener { service?.play() }

        binding.AudioControlView.onSeekComplete = { milliseconds -> service?.seek(milliseconds) }

        setupToolbar(binding)
    }

    override fun onDestroy() {
        binding.AudioControlView.setStarted(false)
        binding.AudioControlView.positionProvider = null
        super.onDestroy()
        if (bound) {
            unbindService(connection)
            bound = false
        }
        service?.onStateChange = null
        service = null
        if (isFinishing) {
            val intent = Intent(this, AudioPlayService::class.java)
            stopService(intent)
        }
    }

    private fun setupToolbar(binding: ActivityPlayAudioBinding) {
        binding.Toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        binding.Toolbar.menu.add(R.string.share, R.drawable.share) { share() }
        binding.Toolbar.menu.add(R.string.save_to_device, R.drawable.save) { saveToDevice() }
        binding.Toolbar.menu.add(R.string.delete, R.drawable.delete) { delete() }
    }

    private fun share() {
        val audioRoot = IO.getExternalAudioDirectory(application)
        val file = if (audioRoot != null) File(audioRoot, audio.name) else null
        if (file != null && file.exists()) {
            val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)

            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "audio/mp4"
            intent.putExtra(Intent.EXTRA_STREAM, uri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            val chooser = Intent.createChooser(intent, null)
            startActivity(chooser)
        }
    }

    private fun delete() {
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.delete_audio_recording_forever)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                val intent = Intent()
                intent.putExtra(AUDIO, audio)
                setResult(RESULT_OK, intent)
                finish()
            }
            .show()
    }


    private fun saveToDevice() {
        val audioRoot = IO.getExternalAudioDirectory(application)
        val file = if (audioRoot != null) File(audioRoot, audio.name) else null
        if (file != null && file.exists()) {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            intent.type = "audio/mp4"
            intent.addCategory(Intent.CATEGORY_OPENABLE)

            val formatter = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.SHORT)
            val title = formatter.format(audio.timestamp)

            intent.putExtra(Intent.EXTRA_TITLE, title)
            exportFileLauncher.launch(intent)
        }
    }

    private fun writeAudioToUri(uri: Uri) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val audioRoot = IO.getExternalAudioDirectory(application)
                val file = if (audioRoot != null) File(audioRoot, audio.name) else null
                if (file != null && file.exists()) {
                    val output = contentResolver.openOutputStream(uri) as FileOutputStream
                    output.channel.truncate(0)
                    val input = FileInputStream(file)
                    input.copyTo(output)
                    input.close()
                    output.close()
                }
            }
            Toast.makeText(this@PlayAudio, R.string.saved_to_device, Toast.LENGTH_LONG).show()
        }
    }


    private fun updateUI(service: AudioPlayService) {
        binding.AudioControlView.setCurrentPosition(service.getCurrentPosition())
        when (service.getState()) {
            AudioPlayService.PREPARED, AudioPlayService.PAUSED, AudioPlayService.COMPLETED -> {
                binding.Play.setText(R.string.play)
                binding.AudioControlView.setStarted(false)
            }
            AudioPlayService.STARTED -> {
                binding.Play.setText(R.string.pause)
                binding.AudioControlView.setStarted(true)
            }
            AudioPlayService.ERROR -> {
                binding.Error.text = getString(R.string.something_went_wrong_audio, service.getErrorType(), service.getErrorCode())
            }
        }
    }

    companion object {
        const val AUDIO = "AUDIO"
    }
}