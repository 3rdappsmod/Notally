package com.omgodse.notally.activities

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.omgodse.notally.miscellaneous.Operations
import kotlinx.coroutines.CancellationException
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.omgodse.notally.R
import com.omgodse.notally.databinding.ActivityConfigureWidgetBinding
import com.omgodse.notally.miscellaneous.IO
import com.omgodse.notally.preferences.Preferences
import com.omgodse.notally.preferences.View
import com.omgodse.notally.recyclerview.ItemListener
import com.omgodse.notally.recyclerview.adapter.BaseNoteAdapter
import com.omgodse.notally.room.BaseNote
import com.omgodse.notally.room.Header
import com.omgodse.notally.room.NotallyDatabase
import com.omgodse.notally.viewmodels.BaseNoteModel
import com.omgodse.notally.widget.WidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Collections

class ConfigureWidget : AppCompatActivity(), ItemListener {

    private var configuring = false
    private lateinit var adapter: BaseNoteAdapter
    private val id by lazy {
        intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityConfigureWidgetBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val result = Intent()
        result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        setResult(RESULT_CANCELED, result)

        val preferences = Preferences.getInstance(application)

        val maxItems = preferences.maxItems
        val maxLines = preferences.maxLines
        val maxTitle = preferences.maxTitle
        val textSize = preferences.textSize.value
        val dateFormat = preferences.dateFormat.value
        val fullFormat = DateFormat.getDateInstance(DateFormat.FULL)
        val shortFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        val mediaRoot = IO.getExternalImagesDirectory(application)

        adapter = BaseNoteAdapter(Collections.emptySet(), dateFormat, textSize, maxItems, maxLines, maxTitle, fullFormat, shortFormat, mediaRoot, this)

        binding.RecyclerView.adapter = adapter
        binding.RecyclerView.setHasFixedSize(true)

        binding.RecyclerView.layoutManager = if (preferences.view.value == View.grid) {
            StaggeredGridLayoutManager(2, RecyclerView.VERTICAL)
        } else LinearLayoutManager(this)

        val database = NotallyDatabase.getDatabase(application)

        val pinned = Header(getString(R.string.pinned))
        val others = Header(getString(R.string.others))

        lifecycleScope.launch {
            val notes = withContext(Dispatchers.IO) {
                val raw = database.getBaseNoteDao().getAllNotes()
                BaseNoteModel.transform(raw, pinned, others)
            }
            adapter.submitList(notes)
        }
    }


    override fun onClick(position: Int) {
        if (configuring || id == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val note = adapter.currentList.getOrNull(position) as? BaseNote ?: return
        configuring = true
        lifecycleScope.launch {
            try {
                val preferences = Preferences.getInstance(application)
                preferences.updateWidget(id, note.id)
                val manager = AppWidgetManager.getInstance(this@ConfigureWidget)
                WidgetProvider.updateWidget(this@ConfigureWidget, manager, id, note.id)

                val success = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                setResult(RESULT_OK, success)
                finish()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Operations.log(application, exception)
                Toast.makeText(this@ConfigureWidget, R.string.something_went_wrong, Toast.LENGTH_LONG).show()
                configuring = false
            }
        }
    }

    override fun onLongClick(position: Int) {}
}