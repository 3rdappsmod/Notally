package com.omgodse.notally.miscellaneous

/** Adapted from NotallyX 59a0a8cc; keep redo invalidation and limits correct at every position. */
class ChangeHistory(private val onStackChanged: (Int) -> Unit = {}) {
    private val changeStack = ArrayList<Change>()
    private var stackPointer = -1

    fun addChange(change: Change) {
        if (stackPointer + 1 < changeStack.size) changeStack.subList(stackPointer + 1, changeStack.size).clear()
        changeStack.add(change)
        stackPointer++
        while (changeStack.size > 1 && (changeStack.size > 100 || changeStack.sumOf { it.weight.toLong() } > 2_000_000)) {
            changeStack.removeAt(0)
            stackPointer--
        }
        onStackChanged(stackPointer)
    }

    fun undo() {
        if (!canUndo()) return
        changeStack[stackPointer--].undo()
        onStackChanged(stackPointer)
    }

    fun redo() {
        if (!canRedo()) return
        changeStack[++stackPointer].redo()
        onStackChanged(stackPointer)
    }

    fun canUndo() = stackPointer >= 0
    fun canRedo() = stackPointer + 1 < changeStack.size
}
