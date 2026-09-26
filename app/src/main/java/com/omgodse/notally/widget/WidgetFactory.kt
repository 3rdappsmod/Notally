package com.omgodse.notally.widget

import android.app.Application
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.omgodse.notally.room.NotallyDatabase

/** Compatibility path for Android 8–11 and collections too large for one Binder update. */
class WidgetFactory(private val app: Application, private val id: Long) : RemoteViewsService.RemoteViewsFactory {
    private var views = WidgetViews(app, null)
    private val database = NotallyDatabase.getDatabase(app)

    override fun onCreate() {}
    override fun onDestroy() {}
    override fun getCount() = views.count

    override fun onDataSetChanged() {
        views = WidgetViews(app, database.getBaseNoteDao().get(id))
    }

    override fun getViewAt(position: Int): RemoteViews? = views.getViewAt(position)
    override fun getViewTypeCount() = 3
    override fun hasStableIds() = false
    override fun getLoadingView(): RemoteViews? = null
    override fun getItemId(position: Int) = position.toLong()
}
