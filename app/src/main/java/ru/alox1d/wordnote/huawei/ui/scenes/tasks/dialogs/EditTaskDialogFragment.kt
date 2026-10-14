package ru.alox1d.wordnote.huawei.ui.scenes.tasks.dialogs

import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TimePicker
import androidx.appcompat.app.AlertDialog
import com.github.irshulx.Editor
import com.google.android.material.textfield.TextInputLayout
import ru.alox1d.wordnote.huawei.R
import ru.alox1d.wordnote.huawei.ui.scenes.tasks.TasksActivity
import ru.alox1d.wordnote.huawei.device.alarm.AlarmHelper.Companion.instance
import ru.alox1d.wordnote.huawei.domain.model.ModelTask
import ru.alox1d.wordnote.huawei.helpers.util.Utils
import ru.alox1d.wordnote.huawei.ui.scenes.pickers.DatePickerController
import ru.alox1d.wordnote.huawei.ui.scenes.pickers.TimePickerController
import java.util.*

class EditTaskDialogFragment : TaskDialog() {
    private val editingTaskListeners: MutableList<EditingTaskListener> = ArrayList()

    interface EditingTaskListener {
        fun onTaskEdited(updatedTask: ModelTask?)
    }

    fun addListener(l: EditingTaskListener) {
        editingTaskListeners.add(l)
    }

    private fun onTaskEdited(updatedTask: ModelTask) {
        if (editingTaskListeners.isEmpty()) {
            (activity as? TasksActivity)?.onTaskEdited(updatedTask)
        } else {
            for (l in editingTaskListeners) l.onTaskEdited(updatedTask)
        }
    }

    override fun onAttach(activity: Activity) {
        super.onAttach(activity)
        try {
        } catch (e: ClassCastException) {
            throw ClassCastException("$activity must implement EditingTaskListener")
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreateDialog(savedInstanceState)
        val args = arguments
        val title = args!!.getString("title")
        val description = args.getString("description")
        val descriptionHTML = args.getString("descriptionHTML")
        val date = args.getLong("date", 0)
        val priority = args.getInt("priority", 0)
        val status = args.getInt("status", ModelTask.STATUS_CURRENT)
        val timeStamp = args.getLong("time_stamp", 0)
        val isHidden = args.getInt("is_hidden", 0)

        val task = ModelTask(title, description, descriptionHTML, date, priority, status, timeStamp, isHidden)
        val builder = AlertDialog.Builder(requireActivity(), R.style.WordNote_Task_Dialog_Alert)
        editor = container.findViewById<View>(R.id.editor_dialogDescription) as Editor
        val tilTitle: TextInputLayout = container.findViewById(R.id.til_DialogTaskTitle)
        val etTitle = tilTitle.editText
        val tilDate = container.findViewById<View>(R.id.tilDialogTaskDate) as TextInputLayout
        etDate = tilDate.editText!!
        val tilTime = container.findViewById<View>(R.id.tilDialogTaskTime) as TextInputLayout
        etTime = tilTime.editText!!
        val spPriority = container.findViewById<View>(R.id.spDialogTaskPriority) as Spinner
        switchHidden = container.findViewById(R.id.switchHidden)
        tilTitle.hint = resources.getString(R.string.task_title)
        etTitle!!.setText(task.title)
        etTitle.setSelection(etTitle.length())
        etTitle.requestFocus()
        if (task.date != 0L) {
            etDate.setText(Utils.getDate(task.date))
            etTime.setText(Utils.getTime(task.date))
        }
//        val html = editor.getContentAsHTML(task.descriptionHTML)
        val editorContent = editor.getContentDeserialized(task.description)
        initDescriptionTextColor(editorContent, this.requireContext())
        editor.render(editorContent)
        tilDate.hint = resources.getString(R.string.task_date)
        tilTime.hint = resources.getString(R.string.task_time)
        builder.setView(container)
        val priorityAdapter = ArrayAdapter(requireActivity(),
                android.R.layout.simple_spinner_dropdown_item, resources.getStringArray(R.array.priorities))
        spPriority.adapter = priorityAdapter
        spPriority.setSelection(task.priority)
        spPriority.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                task.priority = position
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        switchHidden.isChecked = task.isHidden == ModelTask.HIDDEN_TRUE || task.isHidden == ModelTask.HIDDEN_TRUE_UNLOCKED
        switchHidden.setOnCheckedChangeListener { buttonView, isChecked ->
            setHiddenState(isChecked, task)
        }
        calendar = Calendar.getInstance()
        calendar[Calendar.HOUR_OF_DAY] = calendar[Calendar.HOUR_OF_DAY] + 1
        if (etDate.length() != 0 || etTime.length() != 0) {
            calendar.timeInMillis = task.date
        }
        etDate.setOnClickListener {
            val datePickerController=  DatePickerController {
                timeInMillis ->
                    calendar.timeInMillis = timeInMillis!!
                    val currentDate = Utils.getDate(calendar.timeInMillis)
                    etDate.setText(currentDate)
                }
            datePickerController.show(fragmentManager)
        }
        etTime.setOnClickListener {
            val timePickerFragment: TimePickerController = object : TimePickerController() {
                override fun onTimeSet(view: TimePicker, hourOfDay: Int, minute: Int) {
                    calendar[Calendar.HOUR_OF_DAY] = hourOfDay
                    calendar[Calendar.MINUTE] = minute
                    calendar[Calendar.SECOND] = 0
                    etTime.setText(Utils.getTime(calendar.timeInMillis))
                }
            }
            timePickerFragment.createDialog(etTime.context).show()
        }
        builder.setPositiveButton(R.string.dialog_ok) { dialog, which ->
            task.title = etTitle.text.toString()
            task.description = editor.contentAsSerialized
            task.descriptionHTML = editor.contentAsHTML
            if (etDate.length() != 0 || etTime.length() != 0) {
                task.date = calendar.timeInMillis
                val alarmHelper = instance
                alarmHelper.setTaskAlarm(task)
            }
            onTaskEdited(task)
            dialog.dismiss()
        }
        builder.setNegativeButton(R.string.dialog_cancel) { dialog, which -> dialog.cancel() }
        return setupAlertDialog(builder)
    }

    companion object {
        @JvmStatic
        fun newInstance(task: ModelTask): EditTaskDialogFragment {
            val editTaskDialogFragment = EditTaskDialogFragment()
            val args = Bundle()
            args.putString("title", task.title)
            args.putString("description", task.description)
            args.putString("descriptionHTML", task.descriptionHTML)
            args.putLong("date", task.date)
            args.putInt("priority", task.priority)
            args.putInt("status", task.status)
            args.putLong("time_stamp", task.timeStamp)
            args.putInt("is_hidden", task.isHidden)
            editTaskDialogFragment.arguments = args
            return editTaskDialogFragment
        }
    }
}