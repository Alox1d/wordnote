package ru.alox1d.wordnote.huawei.domain.model

import ru.alox1d.wordnote.huawei.R
import java.io.Serializable
import java.util.*

class ModelTask() : Item, Serializable {
    var title: String? = null
    var description: String? = null
    var descriptionHTML: String? = null
    var date: Long = 0
    var priority = 0
    var status: Int = 0
    var timeStamp: Long = 0
    var dateStatus: Long = 0
        private set
    private var isAnimated = false
    var isHidden = 0
    override val isTask: Boolean
        get() = true
    init {
        status = -1
        timeStamp = Date().time
    }

    constructor(title: String?, description: String?, descriptionHTML: String?, date: Long, priority: Int, status: Int, timeStamp: Long, isHidden:Int) : this() {
        this.title = title
        this.description = description
        this.descriptionHTML = descriptionHTML
        this.date = date
        this.priority = priority
        this.status = status
        this.timeStamp = timeStamp
        this.isHidden = isHidden
    }

    val priorityColor: Int
        get() = when (priority) {
            PRIORITY_HIGH -> if (status == STATUS_CURRENT || status == STATUS_OVERDUE) {
                R.color.priority_high
            } else {
                R.color.priority_high_selected
            }
            PRIORITY_NORMAL -> if (status == STATUS_CURRENT || status == STATUS_OVERDUE) {
                R.color.priority_normal
            } else {
                R.color.priority_normal_selected
            }
            PRIORITY_LOW -> if (status == STATUS_CURRENT || status == STATUS_OVERDUE) {
                R.color.priority_low
            } else {
                R.color.priority_low_selected
            }
            else -> 0
        }



    fun setDateStatus(dateStatus: Int) {
        this.dateStatus = dateStatus.toLong()
    }

    override fun hasAnimated(): Boolean {
        return isAnimated
    }

    override fun setAnimated(animated: Boolean) {
        isAnimated = animated
    }

    companion object {
        const val PRIORITY_LOW = 0
        const val PRIORITY_NORMAL = 1
        const val PRIORITY_HIGH = 2
        const val STATUS_OVERDUE = 0
        const val STATUS_CURRENT = 1
        const val STATUS_DONE = 2
        const val HIDDEN_TRUE = 1
        const val HIDDEN_FALSE = 0
        const val HIDDEN_TRUE_UNLOCKED = 3
    }
}