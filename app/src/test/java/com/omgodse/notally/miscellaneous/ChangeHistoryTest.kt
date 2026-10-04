package com.omgodse.notally.miscellaneous

import org.junit.Assert.*
import org.junit.Test

class ChangeHistoryTest {
    @Test fun editingAfterUndoingEverythingDiscardsTheEntireRedoBranch() {
        var value = 0
        val history = ChangeHistory()
        fun change(next: Int) {
            val previous = value
            value = next
            history.addChange(object : Change {
                override fun undo() { value = previous }
                override fun redo() { value = next }
            })
        }
        change(1); change(2)
        history.undo(); history.undo()
        assertEquals(0, value)
        change(3)
        assertFalse(history.canRedo())
        history.redo()
        assertEquals(3, value)
        history.undo(); history.undo()
        assertEquals(0, value)
        history.redo()
        assertEquals(3, value)
    }
}
