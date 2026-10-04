package com.omgodse.notally.room.livedata

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import com.omgodse.notally.room.BaseNote
import com.omgodse.notally.room.Item

/** Recompute visible notes for both database and preference changes. */
class Content(source: LiveData<List<BaseNote>>, transform: (List<BaseNote>) -> List<Item>, vararg triggers: LiveData<String>) : MediatorLiveData<List<Item>>() {
    init {
        addSource(source) { value = transform(it) }
        triggers.forEach { trigger -> addSource(trigger) { source.value?.let { value = transform(it) } } }
    }
}
