package ru.alox1d.wordnote.huawei.ui.scenes.tasks.dialogs

import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.*
import android.widget.AdapterView.OnItemSelectedListener
import androidx.appcompat.app.AlertDialog
import com.github.irshulx.Editor
import com.github.irshulx.models.EditorType
import com.google.android.material.textfield.TextInputLayout
import ru.alox1d.wordnote.huawei.R
import ru.alox1d.wordnote.huawei.ui.scenes.tasks.TasksActivity
import ru.alox1d.wordnote.huawei.device.alarm.AlarmHelper
import ru.alox1d.wordnote.huawei.domain.model.ModelTask
import ru.alox1d.wordnote.huawei.domain.model.TASK_MODE
import ru.alox1d.wordnote.huawei.domain.model.TaskModes
import ru.alox1d.wordnote.huawei.helpers.util.Utils
import ru.alox1d.wordnote.huawei.helpers.util.Utils.ImageUtils
import ru.alox1d.wordnote.huawei.ui.scenes.pickers.DatePickerController
import ru.alox1d.wordnote.huawei.ui.scenes.pickers.TimePickerController
import java.util.*

class AddingTaskDialogFragment() : TaskDialog() {

    private var horScrollButtons: HorizontalScrollView? = null
    private val addingTaskListeners: MutableList<AddingTaskListener> = ArrayList()
    private var mode: Int? = arguments?.getInt(TASK_MODE)

    interface AddingTaskListener {
        fun onTaskAdd(newTask: ModelTask)
        fun onTaskAddingCancel()
    }

    companion object {
        fun newInstance(mode: TaskModes): AddingTaskDialogFragment {
            val args = Bundle()
            args.putInt(TASK_MODE, mode.ordinal)
            val f = AddingTaskDialogFragment()
            f.arguments = (args)
            return f
        }
    }

    fun addListener(l: AddingTaskListener) {
        addingTaskListeners.add(l)
    }

    private fun onTaskAdded(newTask: ModelTask) {
        if (addingTaskListeners.isEmpty()) {
            (activity as? TasksActivity)?.onTaskAdded(newTask)
        } else {
            for (l in addingTaskListeners) l.onTaskAdd(newTask)
        }
    }

    private fun onTaskAddingCancel() {
        for (l in addingTaskListeners) l.onTaskAddingCancel()
    }

    override fun onAttach(activity: Activity) {
        super.onAttach(activity)
        try {
        } catch (e: ClassCastException) {
            throw ClassCastException("$activity must implement AddingTaskListener")
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreateDialog(savedInstanceState)
        val builder = AlertDialog.Builder(requireActivity(), R.style.WordNote_Task_Dialog_Alert)

        editor = container.findViewById<View>(R.id.editor_dialogDescription) as Editor
        val tilDate = container.findViewById<View>(R.id.tilDialogTaskDate) as TextInputLayout
        etDate = tilDate.editText!!
        val tilTime = container.findViewById<View>(R.id.tilDialogTaskTime) as TextInputLayout
        etTime = tilTime.editText!!
        val spPriority = container.findViewById<View>(R.id.spDialogTaskPriority) as Spinner
        switchHidden = container.findViewById(R.id.switchHidden)
        horScrollButtons = container.findViewById(R.id.horScroll_editor)
        mode = arguments?.getInt(TASK_MODE)

        tilTitle.hint = requireActivity().resources.getString(R.string.task_title)
        editor.render(resources.getString(R.string.empty_string))
        tilDate.hint = requireActivity().resources.getString(R.string.task_date)
        tilTime.hint = requireActivity().resources.getString(R.string.task_time)
        builder.setView(container)
        val task = ModelTask()
        val priorityAdapter = ArrayAdapter(
            requireActivity(),
            android.R.layout.simple_spinner_dropdown_item,  /*ModelTask.PRIORITY_LEVELS*/
            resources.getStringArray(R.array.priorities)
        )
        spPriority.adapter = priorityAdapter
        spPriority.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                task.priority = position
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        switchHidden.setOnCheckedChangeListener { buttonView, isChecked ->
            setHiddenState(isChecked, task)
        }
        calendar = Calendar.getInstance()
        calendar[Calendar.HOUR_OF_DAY] = calendar[Calendar.HOUR_OF_DAY] + 1
        etDate.setOnClickListener {
            val datePickerController = DatePickerController { timeInMillis ->
                if (timeInMillis != null) {
                    calendar.timeInMillis = timeInMillis
                }
                val currentDate = Utils.getDate(calendar.timeInMillis)
                etDate.setText(currentDate)
            }
//            {
//                override fun onDateSet(timeInMillis: Long) {
//                    calendar.timeInMillis = timeInMillis
//                    val currentDate = Utils.getDate(calendar.timeInMillis)
//                    etDate.setText(currentDate)
//                }
//            }
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
            task.status = ModelTask.STATUS_CURRENT
            if (etDate.length() != 0 || etTime.length() != 0) {
                task.date = calendar.timeInMillis
                val alarmHelper = AlarmHelper.instance
                alarmHelper.setTaskAlarm(task)
            }
            task.status = ModelTask.STATUS_CURRENT
            onTaskAdded(task)
            dialog.dismiss()
        }
        builder.setNegativeButton(R.string.dialog_cancel) { dialog, which ->
            val editorContent = editor.content
            for (node in editorContent.nodes) {
                if (node.type == EditorType.img) {
                    ImageUtils.deleteImg(context, node.content[0]) // path
                }
            }
            onTaskAddingCancel()
            dialog.cancel()
        }
        if (mode == TaskModes.VOICE_RECOG.ordinal) {
            isTitleVoiceRecognition = true
            recognizeVoice()
        }
        if (mode == TaskModes.IMAGE_RECOG.ordinal) {
            isDescriptionImageRecognition = true
            recognizeImage()
        }

        return setupAlertDialog(builder)
    }

}