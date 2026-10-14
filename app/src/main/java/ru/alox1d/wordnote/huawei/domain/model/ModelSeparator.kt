package ru.alox1d.wordnote.huawei.domain.model

import ru.alox1d.wordnote.huawei.R
import java.io.Serializable

class ModelSeparator(var type: Int) : Item, Serializable {
    private var isAnimated = false
    override val isTask: Boolean
        get() = false

    override fun hasAnimated(): Boolean {
        return isAnimated
    }

    override fun setAnimated(animated: Boolean) {
        isAnimated = animated
    }

    companion object {
        val TYPE_OVERDUE = R.string.separator_overdue
        val TYPE_TODAY = R.string.separator_today
        val TYPE_TOMORROW = R.string.separator_tomorrow
        val TYPE_FUTURE = R.string.separator_future
    }
}