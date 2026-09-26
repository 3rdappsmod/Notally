package com.omgodse.notally.widget

import android.app.Application
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Parcel
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.omgodse.notally.R
import com.omgodse.notally.activities.ConfigureWidget
import com.omgodse.notally.activities.MakeList
import com.omgodse.notally.activities.TakeNote
import com.omgodse.notally.miscellaneous.Constants
import com.omgodse.notally.miscellaneous.Operations
import com.omgodse.notally.preferences.Preferences
import com.omgodse.notally.room.NotallyDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class WidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_NOTES_MODIFIED -> {
                val noteIds = intent.getLongArrayExtra(EXTRA_MODIFIED_NOTES)
                if (noteIds != null) {
                    runAsync(context) { updateWidgets(context, noteIds) }
                }
            }
            ACTION_OPEN_NOTE -> openActivity(context, intent, TakeNote::class.java)
            ACTION_OPEN_LIST -> openActivity(context, intent, MakeList::class.java)
            ACTION_CHECKED_CHANGED -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !intent.hasExtra(RemoteViews.EXTRA_CHECKED)) return
                val noteId = intent.getLongExtra(Constants.SelectedBaseNote, 0)
                val position = intent.getIntExtra(EXTRA_POSITION, -1)
                if (noteId <= 0 || position < 0) return
                val checked = intent.getBooleanExtra(RemoteViews.EXTRA_CHECKED, false)
                val expectedBody = intent.getStringExtra(EXTRA_ITEM_BODY)

                runAsync(context) {
                    val database = NotallyDatabase.getDatabase(context.applicationContext as Application)
                    database.getBaseNoteDao().updateChecked(noteId, position, checked, expectedBody)
                    // Refresh even when the click referred to a removed or changed row.
                    updateWidgets(context, longArrayOf(noteId))
                }
            }
        }
    }

    private fun runAsync(context: Context, action: suspend () -> Unit) {
        val pendingResult = goAsync()
        val app = context.applicationContext as Application
        receiverScope.launch {
            try {
                action()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Operations.log(app, exception)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun updateWidgets(context: Context, noteIds: LongArray) {
        val app = context.applicationContext as Application
        val preferences = Preferences.getInstance(app)

        val manager = AppWidgetManager.getInstance(context)
        val updatableWidgets = preferences.getUpdatableWidgets(noteIds)

        updatableWidgets.forEach { pair -> updateWidget(context, manager, pair.first, pair.second) }
    }

    private fun openActivity(context: Context, originalIntent: Intent, clazz: Class<*>) {
        val id = originalIntent.getLongExtra(Constants.SelectedBaseNote, 0)
        val intent = Intent(context, clazz)
        intent.putExtra(Constants.SelectedBaseNote, id)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }


    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val app = context.applicationContext as Application
        val preferences = Preferences.getInstance(app)

        appWidgetIds.forEach { id -> preferences.deleteWidget(id) }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val app = context.applicationContext as Application
        val preferences = Preferences.getInstance(app)

        runAsync(context) {
            appWidgetIds.forEach { id ->
                val noteId = preferences.getWidgetData(id)
                updateWidget(context, appWidgetManager, id, noteId)
            }
        }
    }

    companion object {

        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val updateMutex = Mutex()

        suspend fun updateWidget(context: Context, manager: AppWidgetManager, id: Int, noteId: Long) = withContext(Dispatchers.IO) {
            // Serialize snapshot loading and publishing so an older snapshot cannot win a race.
            updateMutex.withLock {
                val app = context.applicationContext as Application
                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val note = NotallyDatabase.getDatabase(app).getBaseNoteDao().get(noteId)
                    createCollection(WidgetViews(app, note))
                } else null
                val view = RemoteViews(context.packageName, R.layout.widget)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && collection != null) {
                    view.setRemoteAdapter(R.id.ListView, collection)
                } else {
                    setLegacyAdapter(context, view, noteId)
                }
                view.setEmptyView(R.id.ListView, R.id.Empty)
                view.setOnClickPendingIntent(R.id.Empty, getSelectNoteIntent(context, id))
                view.setPendingIntentTemplate(R.id.ListView, getOpenNoteIntent(context, noteId))
                manager.updateAppWidget(id, view)
                if (collection == null) notifyLegacyAdapter(manager, id)
            }
        }

        @RequiresApi(Build.VERSION_CODES.S)
        internal fun createCollection(views: WidgetViews): RemoteViews.RemoteCollectionItems? {
            // Keep large notes on the lazy service path rather than exceed Binder's payload limit.
            if (views.count > 200) return null
            val builder = RemoteViews.RemoteCollectionItems.Builder()
                .setHasStableIds(false)
                .setViewTypeCount(3)
            for (position in 0 until views.count) {
                builder.addItem(position.toLong(), requireNotNull(views.getViewAt(position)))
            }
            val collection = builder.build()
            val parcel = Parcel.obtain()
            return try {
                collection.writeToParcel(parcel, 0)
                if (parcel.dataSize() <= 256 * 1024) collection else null
            } finally {
                parcel.recycle()
            }
        }

        // Required on API 26–30 and for large collections; do not truncate user content.
        @Suppress("DEPRECATION")
        private fun setLegacyAdapter(context: Context, view: RemoteViews, noteId: Long) {
            val intent = Intent(context, WidgetService::class.java)
            intent.putExtra(Constants.SelectedBaseNote, noteId)
            Operations.embedIntentExtras(intent)
            view.setRemoteAdapter(R.id.ListView, intent)
        }

        @Suppress("DEPRECATION")
        private fun notifyLegacyAdapter(manager: AppWidgetManager, id: Int) {
            manager.notifyAppWidgetViewDataChanged(id, R.id.ListView)
        }

        fun sendBroadcast(app: Application, ids: LongArray) {
            val intent = Intent(app, WidgetProvider::class.java)
            intent.action = ACTION_NOTES_MODIFIED
            intent.putExtra(EXTRA_MODIFIED_NOTES, ids)
            app.sendBroadcast(intent)
        }

        fun sendBroadcast(app: Application, id: Long) = sendBroadcast(app, longArrayOf(id))

        // Each widget has it's own intent since the widget id is embedded
        private fun getSelectNoteIntent(context: Context, id: Int): PendingIntent {
            val intent = Intent(context, ConfigureWidget::class.java)
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            Operations.embedIntentExtras(intent)
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            return PendingIntent.getActivity(context, 0, intent, flags)
        }

        private fun getOpenNoteIntent(context: Context, noteId: Long): PendingIntent {
            val intent = Intent(context, WidgetProvider::class.java)
            intent.putExtra(Constants.SelectedBaseNote, noteId)
            Operations.embedIntentExtras(intent)
            val flags = PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT or Intent.FILL_IN_ACTION
            return PendingIntent.getBroadcast(context, 0, intent, flags)
        }

        private const val EXTRA_MODIFIED_NOTES = "com.omgodse.notally.EXTRA_MODIFIED_NOTES"
        private const val ACTION_NOTES_MODIFIED = "com.omgodse.notally.ACTION_NOTE_MODIFIED"

        const val ACTION_OPEN_NOTE = "com.omgodse.notally.ACTION_OPEN_NOTE"
        const val ACTION_OPEN_LIST = "com.omgodse.notally.ACTION_OPEN_LIST"

        const val ACTION_CHECKED_CHANGED = "com.omgodse.notally.ACTION_CHECKED_CHANGED"
        const val EXTRA_ITEM_BODY = "com.omgodse.notally.EXTRA_ITEM_BODY"
        const val EXTRA_POSITION = "com.omgodse.notally.EXTRA_POSITION"
    }
}