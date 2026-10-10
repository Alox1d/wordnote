package ru.alox1d.wordnote.huawei.ui.scenes.pickers

import android.app.Dialog
import android.app.TimePickerDialog
import android.app.TimePickerDialog.OnTimeSetListener
import android.content.Context
import android.text.format.DateFormat
import android.widget.TimePicker
import ru.alox1d.wordnote.huawei.R
import java.util.*

open class TimePickerController : OnTimeSetListener {
    fun createDialog(context: Context?): Dialog {
        val calendar = Calendar.getInstance()
        val hour = calendar[Calendar.HOUR_OF_DAY]
        val minute = calendar[Calendar.MINUTE]

        // TODO AFTER MaterialTimePicker Builder release
//        boolean isSystem24Hour = DateFormat.is24HourFormat(context);
//        @TimeFormat int clockFormat = isSystem24Hour ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H;
//        MaterialTimePicker materialTimePicker = new MaterialTimePicker();
//        materialTimePicker.setTimeFormat(clockFormat);
//        materialTimePicker.setHour(hour);
//        materialTimePicker.setMinute(minute);
//        materialTimePicker.
        return TimePickerDialog(context, R.style.WordNote_Dialog_Alert, this, hour, minute,
                DateFormat.is24HourFormat(context))
    }

    override fun onTimeSet(view: TimePicker, hourOfDay: Int, minute: Int) {}
}