package ru.alox1d.wordnote.huawei.domain.model

const val TASK_MODE = "TASK_MODE"

enum class TaskModes(i: Int) {
    NONE(1), VOICE_RECOG(2), IMAGE_RECOG(3);
}
