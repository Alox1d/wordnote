package ru.alox1d.wordnote.huawei.ui.scenes.tasks

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.irshulx.Editor
import ru.alox1d.wordnote.huawei.R
import ru.alox1d.wordnote.huawei.domain.model.ModelTask
import ru.alox1d.wordnote.huawei.helpers.SELECTION_LIKE_DESCRIPTION_HTML
import ru.alox1d.wordnote.huawei.helpers.SELECTION_LIKE_TITLE
import ru.alox1d.wordnote.huawei.helpers.SELECTION_STATUS
import ru.alox1d.wordnote.huawei.helpers.TASK_DATE_COLUMN
import ru.alox1d.wordnote.huawei.ui.scenes.tasks.adapters.DoneTaskAdapter
import ru.alox1d.wordnote.huawei.ui.scenes.tasks.adapters.TaskAdapter.TaskAdapterListener
import java.util.*

/**
 * A simple [Fragment] subclass.
 */
class DoneTaskFragment : TaskFragment() {
    interface OnTaskRestoreListener : EventListener {
        fun onTaskRestore(task: ModelTask)
    }

    private fun onTaskRestore(task: ModelTask) {
        for (l in listeners) (l as OnTaskRestoreListener).onTaskRestore(task)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        val rootView = inflater.inflate(R.layout.fragment_done_task, container, false)
        recyclerView = rootView.findViewById<View>(R.id.rvDoneTasks) as RecyclerView
        layoutManager = LinearLayoutManager(activity)
        recyclerView.layoutManager = layoutManager
        adapter = DoneTaskAdapter()
        recyclerView.adapter = adapter
        removeRecyclerViewBlinks()
        val listener: TaskAdapterListener = object : TaskAdapterListener {
            //handler
            override fun onAdd(newTask: ModelTask?, saveToDB: Boolean) {
                addTask(newTask!!, saveToDB)
            }

            //handler
            override fun onStatusChange(task: ModelTask?, status: Int) {
                mUpdateUseCase.buildUseCase(task!!.timeStamp, status)
            }

            //handler
            override fun onMove(task: ModelTask?) {
                moveTask(task!!)
            }

            //handler
            override fun onClick(task: ModelTask?) {
                showTaskEditDialog(task)
            }

            //handler
            override fun onLongClick(layoutPosition: Int, editor: Editor) {
                removeTaskDialog(layoutPosition, editor)
            }

            override fun onExpand(position: Int) {
                smoothScrollToPos(position)
            }
        } // АНОНИМНЫЙ КЛАСС (DELEGATE), представляющий собой СЛУШАТЕЛЯ
        adapter.taskAdapterListener =
            (listener) // adapter - Event Source, DoneTaskFragment - Event Client
        return rootView
    }

    override fun findTasks(text: String) {
        adapter.removeAllItems()
        val tasks: MutableList<ModelTask> = ArrayList()
        val query = ("(" + SELECTION_LIKE_TITLE
                + " OR "
                + SELECTION_LIKE_DESCRIPTION_HTML + ") AND "
                + SELECTION_STATUS)
        tasks.addAll(
            mGetTasksUseCase.buildUseCase(
                query, arrayOf(
                    "%$text%", "%$text%",
                    Integer.toString(ModelTask.STATUS_DONE)
                ), TASK_DATE_COLUMN
            )
        )
        for (i in tasks.indices) {
            addTask(tasks[i], false)
        }
    }

    override fun addTaskFromDB() {
        adapter.removeAllItems()
        val tasks: MutableList<ModelTask> = ArrayList()
        tasks.addAll(
            mGetTasksUseCase.buildUseCase(
                SELECTION_STATUS,
                arrayOf(Integer.toString(ModelTask.STATUS_DONE)),
                TASK_DATE_COLUMN
            )
        )
        for (i in tasks.indices) {
            addTask(tasks[i], false)
        }
    }

    override fun addTask(newTask: ModelTask?, saveToDB: Boolean) {

        var position = -1
        if (newTask != null) {

            for (i in 0 until adapter.itemCount) {
                if (adapter.getItem(i).isTask) {
                    val task = adapter.getItem(i) as ModelTask
                    if (newTask.date < task.date) {
                        position = i
                        break
                    }

                }
            }
            if (position != -1) {
                adapter.addItem(position, newTask)
            } else {
                adapter.addItem(newTask)
            }
            if (saveToDB) {
                mAddTaskUseCase.buildUseCase(newTask)
            }
        }
    }

    override fun checkAdapter() {
        if (adapter == null) {
            adapter = DoneTaskAdapter()
            addTaskFromDB()
        }
    }

    override fun moveTask(task: ModelTask) {
        if (task.date != 0L) {
            alarmHelper.setTaskAlarm(task)
        }
        onTaskRestore(task)
    }
}