package com.omgodse.notally.recyclerview.viewholder

import android.util.TypedValue
import android.view.MotionEvent
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.omgodse.notally.databinding.RecyclerListItemBinding
import com.omgodse.notally.miscellaneous.setOnNextAction
import com.omgodse.notally.preferences.TextSize
import com.omgodse.notally.recyclerview.ListItemListener
import com.omgodse.notally.room.ListItem

class MakeListVH(
    val binding: RecyclerListItemBinding,
    listener: ListItemListener,
    touchHelper: ItemTouchHelper,
    textSize: String
) : RecyclerView.ViewHolder(binding.root) {

    private var bindingItem = false

    init {
        val body = TextSize.getEditBodySize(textSize)
        binding.EditText.setTextSize(TypedValue.COMPLEX_UNIT_SP, body)

        binding.EditText.setOnNextAction {
            listener.moveToNext(bindingAdapterPosition)
        }

        binding.EditText.doAfterTextChanged { text ->
            if (!bindingItem && bindingAdapterPosition != RecyclerView.NO_POSITION)
                listener.textChanged(bindingAdapterPosition, requireNotNull(text).toString())
        }

        binding.Delete.setOnClickListener {
            listener.delete(bindingAdapterPosition)
        }

        binding.CheckBox.setOnCheckedChangeListener { _, isChecked ->
            binding.EditText.isEnabled = !isChecked
            if (!bindingItem && bindingAdapterPosition != RecyclerView.NO_POSITION) listener.checkedChanged(bindingAdapterPosition, isChecked)
        }

        binding.DragHandle.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                touchHelper.startDrag(this)
            }
            false
        }
    }

    fun bind(item: ListItem) {
        bindingItem = true
        binding.root.reset()
        binding.EditText.setText(item.body)
        binding.CheckBox.isChecked = item.checked
        binding.EditText.isEnabled = !item.checked
        bindingItem = false
    }
}