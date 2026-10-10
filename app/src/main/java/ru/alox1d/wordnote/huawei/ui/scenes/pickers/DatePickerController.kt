package ru.alox1d.wordnote.huawei.ui.scenes.pickers

import androidx.fragment.app.FragmentManager
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker

class DatePickerController(val onDateSet: (timeInMillis: Long?) -> Unit)  {
    fun show(fragmentManager: FragmentManager?): MaterialDatePicker<Long>? {

        val builder = setupDateSelectorBuilder()
        val constraintsBuilder = setupConstraintsBuilder()
        builder.setCalendarConstraints(constraintsBuilder.build())
        val picker = builder.build()
        addListeners(picker)
        picker.show(fragmentManager!!, picker.toString())
        return null
    }

    private fun setupDateSelectorBuilder(): MaterialDatePicker.Builder<Long> {
        val inputMode = MaterialDatePicker.INPUT_MODE_CALENDAR
        val builder = MaterialDatePicker.Builder.datePicker()
        builder.setInputMode(inputMode)
        return builder
    }

    private fun setupConstraintsBuilder(): CalendarConstraints.Builder {
        val constraintsBuilder = CalendarConstraints.Builder()
        constraintsBuilder.setValidator(DateValidatorPointForward.now())
        return constraintsBuilder
    }

    private fun addListeners(materialCalendarPicker: MaterialDatePicker<Long>) {
        materialCalendarPicker.addOnPositiveButtonClickListener { selection: Long? -> onDateSet(selection) }
    }

}
