package ru.alox1d.wordnote.huawei.helpers

const val API_KEY = "client/api_key"

const val TASK_TITLE_COLUMN = "task_title"
const val TASK_DESCRIPTION_COLUMN = "task_description"
const val TASK_DESCRIPTION_HTML_COLUMN = "task_descriptionHTML"
const val TASK_DATE_COLUMN = "task_date"
const val TASK_PRIORITY_COLUMN = "task_priority"
const val TASK_STATUS_COLUMN = "task_status"
const val TASK_TIME_STAMP_COLUMN = "task_time_stamp"
const val TASK_IS_HIDDEN_COLUMN = "TASK_IS_HIDDEN"
const val SELECTION_STATUS = TASK_STATUS_COLUMN + " = ?"
const val SELECTION_HIDDEN = TASK_IS_HIDDEN_COLUMN + " = ?"
const val SELECTION_TIME_STAMP = TASK_TIME_STAMP_COLUMN + " = ?"
const val SELECTION_LIKE_TITLE = TASK_TITLE_COLUMN + " LIKE ?"
const val SELECTION_LIKE_DESCRIPTION_HTML = TASK_DESCRIPTION_HTML_COLUMN + " LIKE ?"