package com.omgodse.notally.view

import android.graphics.Rect
import android.widget.EditText

/** Request the caret line, not the bounds of a potentially very tall text editor. */
object CursorVisibility {
    fun reveal(editor: EditText) {
        val layout = editor.layout ?: return
        val selection = editor.selectionEnd
        if (!editor.hasFocus() || selection < 0) return
        val offset = selection.coerceAtMost(editor.length())
        val line = layout.getLineForOffset(offset)
        val x = layout.getPrimaryHorizontal(offset).toInt() + editor.totalPaddingLeft - editor.scrollX
        val margin = (8 * editor.resources.displayMetrics.density).toInt()
        val rect = Rect(
            x, layout.getLineTop(line) + editor.totalPaddingTop - editor.scrollY - margin,
            x + 2, layout.getLineBottom(line) + editor.totalPaddingTop - editor.scrollY + margin,
        )
        editor.requestRectangleOnScreen(rect, true)
    }
}
