package com.omgodse.notally.activities

import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.core.view.doOnPreDraw
import androidx.recyclerview.widget.RecyclerView
import com.omgodse.notally.miscellaneous.setOnNextAction
import com.omgodse.notally.recyclerview.ListItemListener
import com.omgodse.notally.recyclerview.adapter.MakeListAdapter
import com.omgodse.notally.recyclerview.viewholder.MakeListVH
import com.omgodse.notally.room.ListItem
import com.omgodse.notally.room.Type

class MakeList : NotallyActivity(Type.LIST) {

    private lateinit var adapter: MakeListAdapter

    override fun onSaveInstanceState(outState: Bundle) {
        val focused = binding.RecyclerView.findFocus()
        val holder = focused?.let { binding.RecyclerView.findContainingViewHolder(it) } as? MakeListVH
        if (holder != null && focused === holder.binding.EditText &&
            holder.bindingAdapterPosition != RecyclerView.NO_POSITION) {
            outState.putInt("listEditorPosition", holder.bindingAdapterPosition)
            outState.putInt("listSelectionStart", holder.binding.EditText.selectionStart)
            outState.putInt("listSelectionEnd", holder.binding.EditText.selectionEnd)
        }
        super.onSaveInstanceState(outState)
    }

    override fun restoreEditorState(state: Bundle) {
        val position = state.getInt("listEditorPosition", RecyclerView.NO_POSITION)
        if (position !in model.items.indices) {
            super.restoreEditorState(state)
            return
        }
        // Rows share a view ID and are recreated after asynchronous model loading.
        // Restore the specific row only after RecyclerView has laid it out.
        val recycler = binding.RecyclerView
        recycler.scrollToPosition(position)
        recycler.doOnPreDraw {
            if (!isFinishing && !isDestroyed) {
                val holder = recycler.findViewHolderForAdapterPosition(position) as? MakeListVH
                val editor = holder?.binding?.EditText
                if (editor != null && editor.isEnabled && editor.requestFocus()) {
                    val start = state.getInt("listSelectionStart", 0).coerceIn(0, editor.length())
                    val end = state.getInt("listSelectionEnd", start).coerceIn(0, editor.length())
                    editor.setSelection(start, end)
                    super.restoreEditorState(state)
                }
            }
        }
    }

    override fun configureUI() {
        // Keep the checklist visible when editing its title in landscape too.
        binding.EnterTitle.imeOptions = binding.EnterTitle.imeOptions or EditorInfo.IME_FLAG_NO_FULLSCREEN
        binding.EnterTitle.setOnNextAction {
            moveToNext(-1)
        }

        if (model.isNewNote) {
            if (model.items.isEmpty()) {
                addListItem()
            }
        }
    }


    override fun setupListeners() {
        super.setupListeners()
        binding.AddItem.setOnClickListener {
            addListItem()
        }
    }

    override fun setStateFromModel() {
        super.setStateFromModel()
        val elevation = resources.displayMetrics.density * 2

        adapter = MakeListAdapter(model.textSize, elevation, model.items, object : ListItemListener {

            override fun delete(position: Int) {
                model.items.removeAt(position)
                adapter.notifyItemRemoved(position)
            }

            override fun moveToNext(position: Int) {
                this@MakeList.moveToNext(position)
            }

            override fun textChanged(position: Int, text: String) {
                model.items[position].body = text
            }

            override fun checkedChanged(position: Int, checked: Boolean) {
                model.items[position].checked = checked
            }
        })

        binding.RecyclerView.adapter = adapter
    }


    private fun addListItem() {
        val position = model.items.size
        val listItem = ListItem(String(), false)
        model.items.add(listItem)
        adapter.notifyItemInserted(position)
        binding.RecyclerView.post {
            val viewHolder = binding.RecyclerView.findViewHolderForAdapterPosition(position) as MakeListVH?
            if (viewHolder != null) {
                viewHolder.binding.EditText.requestFocus()
                showIme(viewHolder.binding.EditText)
            }
        }
    }

    private fun moveToNext(currentPosition: Int) {
        val viewHolder = binding.RecyclerView.findViewHolderForAdapterPosition(currentPosition + 1) as MakeListVH?
        if (viewHolder != null) {
            if (viewHolder.binding.CheckBox.isChecked) {
                moveToNext(currentPosition + 1)
            } else viewHolder.binding.EditText.requestFocus()
        } else addListItem()
    }
}