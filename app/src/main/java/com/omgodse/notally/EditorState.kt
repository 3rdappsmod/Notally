package com.omgodse.notally

import com.omgodse.notally.room.ListItem
import com.omgodse.notally.room.SpanRepresentation

/** Immutable copies; list rows in the live editor are mutable. */
data class EditorState(
    val title: String,
    val body: String,
    val spans: List<SpanRepresentation>,
    val items: List<ListItem>,
    val selectionStart: Int = 0,
    val selectionEnd: Int = selectionStart,
    val field: Int = -2, // -2 title, -1 body, >= 0 checklist row
) {
    fun sameContent(other: EditorState) = title == other.title && body == other.body && spans == other.spans && items == other.items
}
