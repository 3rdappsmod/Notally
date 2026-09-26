package com.omgodse.notally.widget

import android.app.Application
import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import com.omgodse.notally.R
import com.omgodse.notally.room.BaseNote
import com.omgodse.notally.room.Type
import java.text.DateFormat

/** Shared row rendering for direct collections and the legacy service adapter. */
internal class WidgetViews(private val app: Application, private val note: BaseNote?) {
    val count: Int
        get() = note?.let { if (it.type == Type.NOTE) 1 else 1 + it.items.size } ?: 0

    fun getViewAt(position: Int): RemoteViews? {
        val current = note ?: return null
        if (position !in 0 until count) return null
        return when (current.type) {
            Type.NOTE -> getNoteView(current)
            Type.LIST -> if (position == 0) getListHeaderView(current) else getListItemView(position - 1, current)
        }
    }

    private fun getNoteView(note: BaseNote): RemoteViews {
        val view = RemoteViews(app.packageName, R.layout.widget_note)

        if (note.title.isNotEmpty()) {
            view.setTextViewText(R.id.Title, note.title)
            view.setViewVisibility(R.id.Title, View.VISIBLE)
        } else view.setViewVisibility(R.id.Title, View.GONE)

        val formatter = DateFormat.getDateInstance(DateFormat.FULL)
        val date = formatter.format(note.timestamp)
        view.setTextViewText(R.id.Date, date)

        if (note.body.isNotEmpty()) {
            view.setTextViewText(R.id.Note, note.body)
            view.setViewVisibility(R.id.Note, View.VISIBLE)
        } else view.setViewVisibility(R.id.Note, View.GONE)

        val intent = Intent(WidgetProvider.ACTION_OPEN_NOTE)
        view.setOnClickFillInIntent(R.id.LinearLayout, intent)

        return view
    }


    private fun getListHeaderView(list: BaseNote): RemoteViews {
        val view = RemoteViews(app.packageName, R.layout.widget_list_header)

        if (list.title.isNotEmpty()) {
            view.setTextViewText(R.id.Title, list.title)
            view.setViewVisibility(R.id.Title, View.VISIBLE)
        } else view.setViewVisibility(R.id.Title, View.GONE)

        val formatter = DateFormat.getDateInstance(DateFormat.FULL)
        val date = formatter.format(list.timestamp)
        view.setTextViewText(R.id.Date, date)

        val intent = Intent(WidgetProvider.ACTION_OPEN_LIST)
        view.setOnClickFillInIntent(R.id.LinearLayout, intent)

        return view
    }

    private fun getListItemView(index: Int, list: BaseNote): RemoteViews {
        val view = RemoteViews(app.packageName, R.layout.widget_list_item)

        val item = list.items[index]
        view.setTextViewText(R.id.CheckBox, item.body)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            view.setCompoundButtonChecked(R.id.CheckBox, item.checked)
            val intent = Intent(WidgetProvider.ACTION_CHECKED_CHANGED)
            intent.putExtra(WidgetProvider.EXTRA_POSITION, index)
            intent.putExtra(WidgetProvider.EXTRA_ITEM_BODY, item.body)
            val response = RemoteViews.RemoteResponse.fromFillInIntent(intent)
            view.setOnCheckedChangeResponse(R.id.CheckBox, response)
        } else {
            val intent = Intent(WidgetProvider.ACTION_OPEN_LIST)
            if (item.checked) {
                view.setTextViewCompoundDrawablesRelative(R.id.CheckBox, R.drawable.checkbox_fill, 0, 0, 0)
            } else view.setTextViewCompoundDrawablesRelative(R.id.CheckBox, R.drawable.checkbox_outline, 0, 0, 0)
            view.setOnClickFillInIntent(R.id.CheckBox, intent)
        }

        return view
    }


}
