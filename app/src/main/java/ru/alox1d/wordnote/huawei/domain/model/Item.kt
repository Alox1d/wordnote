package ru.alox1d.wordnote.huawei.domain.model

interface Item {
    val isTask: Boolean
    fun hasAnimated(): Boolean
    fun setAnimated(animated: Boolean)
}