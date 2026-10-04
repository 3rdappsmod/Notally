package com.omgodse.notally.miscellaneous

interface Change {
    val weight: Int get() = 1
    fun redo()
    fun undo()
}
