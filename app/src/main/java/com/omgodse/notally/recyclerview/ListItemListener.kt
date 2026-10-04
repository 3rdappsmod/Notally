package com.omgodse.notally.recyclerview

interface ListItemListener {

    fun move(from: Int, to: Int)

    fun delete(position: Int)

    fun moveToNext(position: Int)

    fun textChanged(position: Int, text: String)

    fun checkedChanged(position: Int, checked: Boolean)
}