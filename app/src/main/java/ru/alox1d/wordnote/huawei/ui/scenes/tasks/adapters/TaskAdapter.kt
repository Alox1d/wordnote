package ru.alox1d.wordnote.huawei.ui.scenes.tasks.adapters

import android.R
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.res.Configuration
import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.github.irshulx.Editor
import com.github.irshulx.models.EditorContent
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import de.hdodenhof.circleimageview.CircleImageView
import ru.alox1d.wordnote.huawei.WordNoteApp.Companion.isHiddenNotesUnlocked
import ru.alox1d.wordnote.huawei.domain.model.Item
import ru.alox1d.wordnote.huawei.domain.model.ModelSeparator
import ru.alox1d.wordnote.huawei.domain.model.ModelTask
import ru.alox1d.wordnote.huawei.helpers.util.Utils


abstract class TaskAdapter() : RecyclerView.Adapter<RecyclerView.ViewHolder?>() {

    var items: MutableList<Item> = mutableListOf()
        set(value) {        field = value
        notifyDataSetChanged()}

    var deletedItems: MutableMap<Int, ModelTask> = mutableMapOf()

    @JvmField
    var containsSeparatorOverdue = false

    @JvmField
    var containsSeparatorToday = false

    @JvmField
    var containsSeparatorTomorrow = false

    @JvmField
    var containsSeparatorFuture = false
    protected var mExpandedPosition = RecyclerView.NO_POSITION
    protected var mExpandedHolder: TaskViewHolder? = null

    private val on_attach = true
    var DURATION: Long = 200


    @JvmField
    var taskAdapterListener: TaskAdapterListener? = null

    interface TaskAdapterListener {
        fun onAdd(newTask: ModelTask?, saveToDB: Boolean)
        fun onStatusChange(task: ModelTask?, status: Int)
        fun onMove(task: ModelTask?)
        fun onClick(task: ModelTask?)
        fun onLongClick(layoutPosition: Int, editor: Editor)
        fun onExpand(position: Int)
    }

    fun getItem(position: Int): Item {
        return items[position]
    }

    fun addItem(item: Item) {
        items.add(item)
        notifyItemInserted(itemCount - 1)
    }

    fun addItem(location: Int, item: Item) {
        items.add(location, item)
        notifyItemInserted(location)
        notifyItemRangeChanged(0, location);

    }

    fun updateTask(newTask: ModelTask) {
        var i = 0
        while (i<itemCount) {
            if (getItem(i).isTask) {
                val task = getItem(i) as ModelTask
                if (newTask.timeStamp == task.timeStamp) {
                    removeItem(i)
                    taskAdapterListener!!.onAdd(newTask, false)
                }
            }
            i++
        }
    }

    fun removeItem(location: Int) {
        if (location >= 0 && location <= itemCount - 1) {
            items.removeAt(location)
            notifyItemRemoved(location)
            if (location - 1 >= 0 && location <= itemCount - 1) {
                if (!getItem(location).isTask && !getItem(location - 1).isTask) {
                    val separator = getItem(location - 1) as ModelSeparator
                    checkSeparators(separator.type)
                    items.removeAt(location - 1)
                    notifyItemRemoved(location - 1)
                }
            } else if (itemCount - 1 >= 0 && !getItem(itemCount - 1).isTask) {
                val separator = getItem(itemCount - 1) as ModelSeparator
                checkSeparators(separator.type)
                val locationTemp = itemCount - 1
                items.removeAt(locationTemp)
                notifyItemRemoved(locationTemp)
            }
        }
    }

    private fun checkSeparators(type: Int) {
        when (type) {
            ModelSeparator.TYPE_OVERDUE -> containsSeparatorOverdue = false
            ModelSeparator.TYPE_TODAY -> containsSeparatorToday = false
            ModelSeparator.TYPE_TOMORROW -> containsSeparatorTomorrow = false
            ModelSeparator.TYPE_FUTURE -> containsSeparatorFuture = false
        }
    }

    fun removeAllItems() {
        if (itemCount != 0) {
            items = ArrayList()
            notifyDataSetChanged()
            containsSeparatorOverdue = false
            containsSeparatorToday = false
            containsSeparatorTomorrow = false
            containsSeparatorFuture = false
        }
    }

    override fun getItemCount(): Int {
        return items.size
    }

    inner class TaskViewHolder(itemView: View?,
                               @JvmField var title: TextView,
                               @JvmField var description: Editor,
                               @JvmField var containerDescription: FrameLayout,
                               @JvmField var ivArrow: ImageButton,
                               @JvmField var date: TextView,
                               @JvmField var priority: CircleImageView,
                               @JvmField var cv: MaterialCardView,
                               @JvmField var ivHidden: ImageView) : RecyclerView.ViewHolder(itemView!!) {

    }

    protected fun initExpand(position: Int, task: ModelTask, holder: TaskViewHolder) {
        val editorContent = holder.description.getContentDeserialized(task.description)
        var isNotEmptyFound = false
        initDescriptionTextColor(editorContent, holder)

        for (node in editorContent.nodes) {
            for (i in node.content.indices) {
                if (!node.content[i].isEmpty()) {
                    isNotEmptyFound = true
                    holder.description.clearAllContents()
                    holder.description.render(editorContent)

                    holder.ivArrow.visibility = View.VISIBLE
                    val isExpanded = position == mExpandedPosition
                    holder.ivArrow.setOnClickListener {
                        toggleLayout(holder, isExpanded, position)
                    }
                    holder.containerDescription.visibility = if (isExpanded) View.VISIBLE else View.GONE
                    break
                } else {
                    // Проверить удаление стрелки после редактирования: onBind
                    holder.ivArrow.visibility = View.GONE
                    holder.containerDescription.visibility = View.GONE
                }
            }
            if (isNotEmptyFound) break
        }
    }

    private fun initDescriptionTextColor(editorContent: EditorContent, holder: TaskViewHolder) {
        for (node in editorContent.nodes) {
            node.textSettings?.let {
                val context = holder.containerDescription.context
                when (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                    Configuration.UI_MODE_NIGHT_YES -> {
                        val black = Utils.colorHex(MaterialColors.getColor(context, R.attr.textColor, Color.BLACK))
                        if (node.textSettings.textColor == black) {
                            val white = Utils.colorHex(MaterialColors.getColor(context, R.attr.textColor, Color.WHITE))
                            it.setTextColor(white)
                        }
                    }
                }
            }
        }
    }

    private fun toggleLayout(holder: TaskViewHolder, isExpanded: Boolean, position: Int) {
        Utils.Animations.toggleArrow(holder.ivArrow, isExpanded)

        expand(position, isExpanded, holder)
    }

    fun expand(position: Int, isExpanded: Boolean, holder: TaskViewHolder?) {
        if (holder != null) {
            expandWithFixedAnimations(isExpanded, holder)
        }
        expandByRecyclerView(position, isExpanded, holder)

    }

    private fun expandWithFixedAnimations(isExpanded: Boolean, holder: TaskViewHolder) {
        if (!isExpanded) {
            Utils.Animations.expand(holder.containerDescription);
        } else {
            Utils.Animations.collapse(holder.containerDescription);
        }
    }

    private fun expandByRecyclerView(position: Int, isExpanded: Boolean, holder: TaskViewHolder?) {
        if (isExpanded) {
            notifyItemChanged(position);
            mExpandedPosition = -1;
        } else {
                notifyItemChanged(mExpandedPosition);
            mExpandedPosition = position;
            mExpandedHolder = holder
            notifyItemChanged(position);
        }
    }

//        // TODO замедлить анимацию of expand
//        // TODO Камера
//        // TODO Календарь
//        // TODO Синхронизация с Гуглом (гугл сервисы?)


    protected inner class SeparatorViewHolder(itemView: View?,
                                              @JvmField var type: TextView) : RecyclerView.ViewHolder(itemView!!)

    protected fun fromRightToLeft(itemView: View, _i: Int) {
        var i = _i
        if (!on_attach) {
            i = -1
        }
        val isNotFirstItem = i == -1
        i++
        itemView.alpha = 0f
        val animatorSet = AnimatorSet()
        val animator = ObjectAnimator.ofFloat(itemView, "alpha", 0f, 0.5f, 1.0f)
        ObjectAnimator.ofFloat(itemView, "alpha", 0f).start()
        animator.startDelay = if (isNotFirstItem) DURATION / 2 else i * DURATION / 3
        animator.duration = 500
        animatorSet.play(animator)
        animator.start()
    }
     fun hideItem(position: Int, task: ModelTask) {
        deletedItems[position] = task
    }
    fun showItem(position: Int) {
        items.add(position, deletedItems[position] as Item)
        deletedItems.remove(position)
        notifyItemInserted(position)
    }
    protected open fun setHiddenTasks(task: ModelTask, taskViewHolder: TaskViewHolder) {
        if (task.isHidden == ModelTask.HIDDEN_TRUE_UNLOCKED || task.isHidden == ModelTask.HIDDEN_TRUE &&
                isHiddenNotesUnlocked) taskViewHolder.ivHidden.visibility = View.VISIBLE else taskViewHolder.ivHidden.visibility = View.GONE
    }
    init {
        items = ArrayList()
    }

}