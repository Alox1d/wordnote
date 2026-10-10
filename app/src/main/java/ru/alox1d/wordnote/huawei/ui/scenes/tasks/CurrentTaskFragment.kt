package ru.alox1d.wordnote.huawei.ui.scenes.tasks

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.irshulx.Editor
import ru.alox1d.wordnote.huawei.R
import ru.alox1d.wordnote.huawei.WordNoteApp
import ru.alox1d.wordnote.huawei.domain.model.ModelSeparator
import ru.alox1d.wordnote.huawei.domain.model.ModelTask
import ru.alox1d.wordnote.huawei.helpers.*
import ru.alox1d.wordnote.huawei.ui.scenes.tasks.adapters.CurrentTaskAdapter
import ru.alox1d.wordnote.huawei.ui.scenes.tasks.adapters.TaskAdapter.TaskAdapterListener
import java.util.*

/**
 * A simple [Fragment] subclass.
 */
class CurrentTaskFragment : TaskFragment() {

    interface OnTaskDoneListener : EventListener {
        fun onTaskDone(task: ModelTask)
    }

    protected fun onTaskDone(task: ModelTask) {
        for (l in listeners) (l as OnTaskDoneListener).onTaskDone(task)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_current_task, container, false)
        recyclerView = rootView.findViewById<View>(R.id.rvCurrentTasks) as RecyclerView
        layoutManager = LinearLayoutManager(activity)
        recyclerView.layoutManager = layoutManager
        adapter = CurrentTaskAdapter()
        recyclerView.adapter = adapter
        removeRecyclerViewBlinks()
        adapter.taskAdapterListener = (object : TaskAdapterListener {
            override fun onAdd(newTask: ModelTask?, saveToDB: Boolean) {
                addTask(newTask!!, saveToDB)
            }

            override fun onStatusChange(task: ModelTask?, status: Int) {
                mUpdateUseCase.buildUseCase(task!!.timeStamp, status)
            }

            override fun onMove(task: ModelTask?) {
                moveTask(task!!)
            }

            override fun onClick(task: ModelTask?) {
                showTaskEditDialog(task)
            }

            override fun onLongClick(layoutPosition: Int, editor: Editor) {
                removeTaskDialog(layoutPosition, editor)
            }

            override fun onExpand(position: Int) {
                smoothScrollToPos(position)
            }
        })
        return rootView
    }

    override fun findTasks(text: String) {
        adapter.removeAllItems()
        val hiddenStatus =
            if (WordNoteApp.isHiddenNotesUnlocked) ModelTask.HIDDEN_TRUE else ModelTask.HIDDEN_FALSE

        val tasks: MutableList<ModelTask> = ArrayList()
        val query = ("(" + SELECTION_LIKE_TITLE + " OR "
                + SELECTION_LIKE_DESCRIPTION_HTML + ") AND ("
                + SELECTION_STATUS + " OR " + SELECTION_STATUS
                + ") AND ("
                + SELECTION_HIDDEN + " OR "
                + SELECTION_HIDDEN + ")")
        tasks.addAll(
            mGetTasksUseCase.buildUseCase(
                query, arrayOf(
                    "%$text%",
                    "%$text%",
                    Integer.toString(ModelTask.STATUS_CURRENT),
                    Integer.toString(ModelTask.STATUS_OVERDUE),
                    hiddenStatus.toString(),
                    ModelTask.HIDDEN_FALSE.toString()
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
                SELECTION_STATUS
                        + " OR "
                        + SELECTION_STATUS, arrayOf(
                    Integer.toString(ModelTask.STATUS_CURRENT),
                    Integer.toString(ModelTask.STATUS_OVERDUE)
                ), TASK_DATE_COLUMN
            )
        )
        for (i in tasks.indices) {
            addTask(tasks[i], false)
        }
    }

    override fun addTask(newTask: ModelTask?, saveToDB: Boolean) {
        var position = -1
        val separator: ModelSeparator?
        if (newTask != null) {
            if (checkHiddenFlag(newTask)) {
                for (i in 0 until adapter.itemCount) {
                    if (adapter.getItem(i).isTask) {
                        val task = adapter.getItem(i) as ModelTask
                        if (newTask.date < task.date) {
                            position = i
                            break
                        }
                    }
                }
                separator = setDateStatus(newTask)
                if (position != -1) {
                    if (!adapter.getItem(position - 1).isTask) {
                        if (position - 2 >= 0 && adapter.getItem(position - 2).isTask) {
                            val task = adapter.getItem(position - 2) as ModelTask
                            if (task.dateStatus == newTask.dateStatus) {
                                position -= 1
                            }
                        } else if (position - 2 < 0 && newTask.date == 0L) {
                            position -= 1
                        }
                    }
                    if (separator != null) {
                        adapter.addItem(position - 1, separator)
                    }
                    adapter.addItem(position, newTask)
                } else {
                    if (separator != null) {
                        adapter.addItem(separator)
                    }
                    adapter.addItem(newTask)
                }
            } else {
                hiddenTasks.add(newTask)
            }
            if (saveToDB) {
                checkHiddenTasksUnlocked(newTask)
                mAddTaskUseCase.buildUseCase(newTask)
            }
        }
    }

    private fun checkHiddenTasksUnlocked(newTask: ModelTask) {
        if (newTask.isHidden == ModelTask.HIDDEN_TRUE_UNLOCKED)
            newTask.isHidden = ModelTask.HIDDEN_TRUE
    }

    private fun checkHiddenFlag(task: ModelTask): Boolean {
        return task.isHidden == ModelTask.HIDDEN_FALSE
                || task.isHidden == ModelTask.HIDDEN_TRUE_UNLOCKED
                || (task.isHidden == ModelTask.HIDDEN_TRUE
                && WordNoteApp.isHiddenNotesUnlocked)
    }

    private fun setDateStatus(
        newTask: ModelTask
    ): ModelSeparator? {
        if (newTask.date != 0L) {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = newTask.date
            if (calendar[Calendar.YEAR] < Calendar.getInstance()[Calendar.YEAR]) {
                newTask.setDateStatus(ModelSeparator.TYPE_OVERDUE)
                if (!adapter.containsSeparatorOverdue) {
                    adapter.containsSeparatorOverdue = true
                    return ModelSeparator(ModelSeparator.TYPE_OVERDUE)
                }
            } else if (calendar[Calendar.YEAR] > Calendar.getInstance()[Calendar.YEAR]) {
                newTask.setDateStatus(ModelSeparator.TYPE_FUTURE)
                if (!adapter.containsSeparatorFuture) {
                    adapter.containsSeparatorFuture = true
                    return ModelSeparator(ModelSeparator.TYPE_FUTURE)
                }
            } else if (calendar[Calendar.DAY_OF_YEAR] < Calendar.getInstance()[Calendar.DAY_OF_YEAR]) {
                newTask.setDateStatus(ModelSeparator.TYPE_OVERDUE)
                if (!adapter.containsSeparatorOverdue) {
                    adapter.containsSeparatorOverdue = true
                    return ModelSeparator(ModelSeparator.TYPE_OVERDUE)
                }
            } else if (calendar[Calendar.DAY_OF_YEAR] == Calendar.getInstance()[Calendar.DAY_OF_YEAR]) {
                newTask.setDateStatus(ModelSeparator.TYPE_TODAY)
                if (!adapter.containsSeparatorToday) {
                    adapter.containsSeparatorToday = true
                    return ModelSeparator(ModelSeparator.TYPE_TODAY)
                }
            } else if (calendar[Calendar.DAY_OF_YEAR] == Calendar.getInstance()[Calendar.DAY_OF_YEAR] + 1) {
                newTask.setDateStatus(ModelSeparator.TYPE_TOMORROW)
                if (!adapter.containsSeparatorTomorrow) {
                    adapter.containsSeparatorTomorrow = true
                    return ModelSeparator(ModelSeparator.TYPE_TOMORROW)
                }
            } else if (calendar[Calendar.DAY_OF_YEAR] > Calendar.getInstance()[Calendar.DAY_OF_YEAR] + 1) {
                newTask.setDateStatus(ModelSeparator.TYPE_FUTURE)
                if (!adapter.containsSeparatorFuture) {
                    adapter.containsSeparatorFuture = true
                    return ModelSeparator(ModelSeparator.TYPE_FUTURE)
                }
            }
        }
        return null
    }


    override fun checkAdapter() {
        if (adapter == null) {
            adapter = CurrentTaskAdapter()
            addTaskFromDB()
        }
    }

    override fun moveTask(task: ModelTask) {
        alarmHelper.removeAlarm(task.timeStamp)
        onTaskDone(task)
    }
}