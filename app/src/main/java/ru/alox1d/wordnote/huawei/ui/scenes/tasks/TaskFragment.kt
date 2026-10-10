package ru.alox1d.wordnote.huawei.ui.scenes.tasks

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.SmoothScroller
import com.example.toothpick.annotation.ApplicationScope
import com.github.irshulx.Editor
import com.github.irshulx.models.EditorType
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import ru.alox1d.wordnote.huawei.R
import ru.alox1d.wordnote.huawei.device.alarm.AlarmHelper
import ru.alox1d.wordnote.huawei.device.alarm.AlarmHelper.Companion.instance
import ru.alox1d.wordnote.huawei.di.module.MainModule
import ru.alox1d.wordnote.huawei.domain.model.Item
import ru.alox1d.wordnote.huawei.domain.model.ModelTask
import ru.alox1d.wordnote.huawei.domain.usecase.*
import ru.alox1d.wordnote.huawei.helpers.util.Utils
import ru.alox1d.wordnote.huawei.ui.scenes.tasks.adapters.TaskAdapter
import ru.alox1d.wordnote.huawei.ui.scenes.tasks.adapters.TaskAdapter.TaskViewHolder
import ru.alox1d.wordnote.huawei.ui.scenes.tasks.dialogs.EditTaskDialogFragment.Companion.newInstance
import ru.alox1d.wordnote.huawei.ui.scenes.tasks.dialogs.EditTaskDialogFragment.EditingTaskListener
import toothpick.ktp.KTP
import toothpick.ktp.delegate.inject
import toothpick.smoothie.lifecycle.closeOnDestroy
import java.util.*

abstract class TaskFragment : Fragment() {

    protected var listeners: MutableList<EventListener> = ArrayList()
    protected var hiddenTasks: MutableList<ModelTask> = arrayListOf()

    protected lateinit var recyclerView: RecyclerView
    protected var layoutManager: RecyclerView.LayoutManager? = null
    protected lateinit var adapter: TaskAdapter
    protected lateinit var alarmHelper: AlarmHelper

    protected val mAddTaskUseCase: AddTaskUseCase by inject()
    protected val mGetTaskUseCase: GetTaskUseCase by inject()
    protected val mGetTasksUseCase: GetTasksUseCase by inject()
    protected val mUpdateUseCase: UpdateTaskUseCase by inject()
    protected val mDeleteTaskUseCase: DeleteTaskUseCase by inject()

    private fun setDI() {
        KTP.openScopes(ApplicationScope::class.java, this)
            .installModules(
                MainModule(requireContext()),
            )
            .closeOnDestroy(this)
            .inject(this)
    }

    fun addListener(l: EventListener) {
        listeners.add(l)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        alarmHelper = instance
        setDI()
        addTaskFromDB()
    }

    abstract fun addTask(newTask: ModelTask?, saveToDB: Boolean)
    fun updateTask(task: ModelTask?) {
        adapter.updateTask(task!!)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    fun removeTaskDialog(location: Int, editor: Editor) {
        val dialogBuilder = MaterialAlertDialogBuilder(requireActivity(), R.style.ThemeOverlay_Crane_MaterialComponents_Dialog_Alert)
        dialogBuilder.setMessage(R.string.dialog_removing_message)
        val item = adapter.getItem(location)
        if (item.isTask) {
            val removingTask = item as ModelTask
            val timeStamp = removingTask.timeStamp
            val isRemoved = booleanArrayOf(false)
            dialogBuilder.setPositiveButton(R.string.dialog_ok, object : DialogInterface.OnClickListener {
                override fun onClick(dialog: DialogInterface, which: Int) {
                    removeTask()
                    dialog.dismiss()
                }

                private fun removeTask() {
                    adapter.removeItem(location)
                    adapter.notifyItemRangeChanged(location, adapter.itemCount)
                    isRemoved[0] = true
                    val snackbar = Snackbar.make(
                        activity!!.findViewById(R.id.coordinator),
                        R.string.removed, Snackbar.LENGTH_LONG
                    )
                    snackbar.setAction(R.string.dialog_cancel) {
                        cancel()
                    }
                    snackbar.view.addOnAttachStateChangeListener(object :
                        View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(v: View) {}
                        override fun onViewDetachedFromWindow(v: View) {
                            if (isRemoved[0]) {
                                alarmHelper.removeAlarm(timeStamp)
                                mDeleteTaskUseCase.buildUseCase(timeStamp)
                                removeTaskImgs()
                            }
                        }
                    })
                    snackbar.show()
                }

                private fun cancel() {
                    addTask(mGetTaskUseCase.buildUseCase(timeStamp), false)
                    isRemoved[0] = false
                }

                private fun removeTaskImgs() {
                    val description = removingTask.description
                    val editorContent = editor.getContentDeserialized(description)
                    for (node in editorContent.nodes) {
                        if (node.type == EditorType.img) {
                            Utils.ImageUtils.deleteImg(context, node.content[0]) // path
                        }
                    }
                }
            })
            dialogBuilder.setNegativeButton(R.string.dialog_cancel) { dialog, which -> dialog.cancel() }
        }
        dialogBuilder.show()
    }

    fun showTaskEditDialog(task: ModelTask?) {
        val editingTaskDialog = newInstance(task!!)
        editingTaskDialog.addListener(object : EditingTaskListener {
            override fun onTaskEdited(updatedTask: ModelTask?) {
                updateTask(updatedTask)
                if (updatedTask?.isHidden == ModelTask.HIDDEN_TRUE_UNLOCKED) updatedTask.isHidden = ModelTask.HIDDEN_TRUE
                if (updatedTask != null) {
                    mUpdateUseCase.buildUseCase(updatedTask)
                }
            }
        })
        editingTaskDialog.show(requireActivity().supportFragmentManager, "EditTaskDialogFragment")
    }

    abstract fun checkAdapter()
    abstract fun findTasks(title: String)
    abstract fun addTaskFromDB()

    fun addHiddenTasks() {
        hiddenTasks.forEach { task ->
            task.isHidden = ModelTask.HIDDEN_TRUE_UNLOCKED
            addTask(task, false)
        }
        hiddenTasks.clear()
    }
    fun removeHiddenTasks(){
        adapter.removeAllItems()
        addTaskFromDB()
//        adapter.notifyItemRangeChanged(location, adapter.itemCount)
    }

    fun addTaskFromList(items: List<Item>) {
        adapter.items = (items.toMutableList())
    }

    val tasks: ArrayList<Item>?
        get() = if (adapter.items != null) {
            adapter.items as ArrayList<Item>?
        } else {
            ArrayList()
        }

    abstract fun moveTask(task: ModelTask)

    //TODO показывать горизонтальную форматку только при открытой клавиатуре
    protected fun removeRecyclerViewBlinks() {
        (recyclerView.getItemAnimator() as DefaultItemAnimator).supportsChangeAnimations = false
//        recyclerView.setItemAnimator(MyRecyclerViewAnimator()); // set the default animator to your extended class
    }

    override fun onResume() {
        super.onResume()
        checkAdapter()
    }

    protected fun smoothScrollToPos(position: Int) {
        val smoothScroller: SmoothScroller = object : LinearSmoothScroller(context) {
            override fun getVerticalSnapPreference(): Int {
                return SNAP_TO_START
            }
        }
        smoothScroller.targetPosition = position
        recyclerView.layoutManager!!.startSmoothScroll(smoothScroller)
    }

    fun expandTask(timeStamp: Long) {
        val items = adapter.items
        for (position in items!!.indices) {
            if (items[position].isTask) {
                val modelTask = items[position] as ModelTask
                if (modelTask.timeStamp == timeStamp) {
                    smoothScrollToPos(position)
                    recyclerView.findViewHolderForAdapterPosition(position)
                    recyclerView.post {
                        try {
                            Thread.sleep(EXPAND_APPEAR_DURATION.toLong())
                        } catch (e: InterruptedException) {
                            e.printStackTrace()
                        }
                        adapter.expand(position, false, recyclerView.findViewHolderForAdapterPosition(position) as TaskViewHolder?)
                    }
                }
            }
        }
    }


    companion object {
        private const val EXPAND_APPEAR_DURATION = 1000
    }
}