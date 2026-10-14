package ru.alox1d.wordnote.huawei.ui.scenes.tasks.adapters

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.github.irshulx.Editor
import com.google.android.material.card.MaterialCardView
import de.hdodenhof.circleimageview.CircleImageView
import ru.alox1d.wordnote.huawei.R
import ru.alox1d.wordnote.huawei.domain.model.ModelSeparator
import ru.alox1d.wordnote.huawei.domain.model.ModelTask
import ru.alox1d.wordnote.huawei.helpers.util.Utils
import java.util.*

class CurrentTaskAdapter : TaskAdapter() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_TASK -> {
                val v = LayoutInflater.from(parent.context)
                        .inflate(R.layout.model_task, parent, false)
                val title = v.findViewById<TextView>(R.id.tvTaskTitle)
                var editorDescription: Editor? = v.findViewById<View>(R.id.editor_fragDescription) as Editor
                val descriptionNew = Editor(parent.context, null)
                Utils.ViewGroupUtils.replaceView(editorDescription, descriptionNew)
                editorDescription = descriptionNew
                val containerDescription = v.findViewById<FrameLayout>(R.id.cv_taskDescriptionContainer)
                val ivArrow = v.findViewById<ImageButton>(R.id.iv_taskArrow)
                val ivHidden = v.findViewById<ImageView>(R.id.iv_hidden)
                val date = v.findViewById<View>(R.id.tvTaskDate) as TextView
                val priority = v.findViewById<View>(R.id.cvTaskPriority) as CircleImageView
                val cv = v.findViewById<View>(R.id.card_view_task) as MaterialCardView
                TaskViewHolder(v, title, editorDescription, containerDescription, ivArrow, date, priority, cv, ivHidden)
            }
            TYPE_SEPARATOR -> {
                val separator = LayoutInflater.from(parent.context)
                        .inflate(R.layout.model_separator, parent, false)
                val type = separator.findViewById<View>(R.id.tvSeparatorName) as TextView
                SeparatorViewHolder(separator, type)
            }
            else -> {
                val separator = LayoutInflater.from(parent.context)
                        .inflate(R.layout.model_separator, parent, false)
                val type = separator.findViewById<View>(R.id.tvSeparatorName) as TextView
                SeparatorViewHolder(separator, type)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        val resources = holder.itemView.resources
        if (item.isTask) {
            holder.itemView.isEnabled = true
            val task = item as ModelTask
            val taskViewHolder = holder as TaskViewHolder
            val itemView = taskViewHolder.itemView
            taskViewHolder.title.text = task.title
            if (task.date != 0L) {
                taskViewHolder.date.text = Utils.getFullDate(task.date)
            } else {
                taskViewHolder.date.text = null
            }
            itemView.visibility = View.VISIBLE
            taskViewHolder.priority.isEnabled = true

            // TODO Неверное закрашивание (почему по нажатию стрелки?)
//            MaterialCardView materialCardView = itemView.findViewById(R.id.card_view_task);
            if (task.date != 0L && task.date < Calendar.getInstance().timeInMillis) {
//                materialCardView.setCardBackgroundColor(resources.getColor(R.color.gray_200));
//                taskViewHolder.cv.setAlpha(0.5f);
            } else {
//                materialCardView.setCardBackgroundColor(Utils.getThemeColor(itemView.getContext(), R.attr.colorSurface));
            }
            taskViewHolder.title.alpha = 1.0f
            taskViewHolder.date.alpha = 0.6f
            taskViewHolder.priority.setColorFilter(resources.getColor(task.priorityColor))
            taskViewHolder.priority.setImageResource(R.drawable.ic_checkbox_blank_circle_white_48dp)
            taskViewHolder.cv.setOnLongClickListener {
                val handler = Handler()
                handler.postDelayed({
                    val currentPosition = items.indexOf(task)
                    if (currentPosition != RecyclerView.NO_POSITION) {
                        taskAdapterListener?.onLongClick(currentPosition, taskViewHolder.description)
                    }
                }, 1000)
                true
            }
            initExpand(task, taskViewHolder)
            taskViewHolder.priority.setOnClickListener {
                taskViewHolder.priority.isEnabled = false
                task.status = ModelTask.STATUS_DONE
                taskAdapterListener!!.onStatusChange(task, ModelTask.STATUS_DONE)
                taskViewHolder.title.alpha = 0.2f
                taskViewHolder.date.alpha = 0.2f
                taskViewHolder.priority.setColorFilter(resources.getColor(task.priorityColor))
                val flipIn = ObjectAnimator.ofFloat(taskViewHolder.priority, "rotationY", -180f, 0f)
                flipIn.addListener(object : Animator.AnimatorListener {
                    override fun onAnimationStart(animation: Animator) {}
                    override fun onAnimationEnd(animation: Animator) {
                        if (task.status == ModelTask.STATUS_DONE) {
                            taskViewHolder.priority.setImageResource(R.drawable.ic_check_circle_white_48dp)
                            val translationX = ObjectAnimator.ofFloat(itemView,
                                    "translationX", 0f, itemView.width.toFloat())
                            val translationXBack = ObjectAnimator.ofFloat(itemView,
                                    "translationX", itemView.width.toFloat(), 0f)
                            translationX.addListener(object : Animator.AnimatorListener {
                                override fun onAnimationStart(animation: Animator) {}
                                override fun onAnimationEnd(animation: Animator) {
                                    itemView.visibility = View.GONE
                                    taskAdapterListener!!.onMove(task)
                                    removeItem(items.indexOf(task))
                                }

                                override fun onAnimationCancel(animation: Animator) {}
                                override fun onAnimationRepeat(animation: Animator) {}
                            })
                            val translationSet = AnimatorSet()
                            translationSet.play(translationX).before(translationXBack)
                            translationSet.start()
                        }
                    }

                    override fun onAnimationCancel(animation: Animator) {}
                    override fun onAnimationRepeat(animation: Animator) {}
                })
                flipIn.start()
            }
            taskViewHolder.cv.setOnClickListener { taskAdapterListener!!.onClick(task) }
            setHiddenTasks(task, taskViewHolder)
        } else {
            val separator = item as ModelSeparator
            val separatorViewHolder = holder as SeparatorViewHolder
            separatorViewHolder.type.text = resources.getString(separator.type)
        }
        if (!item.hasAnimated()) {
            fromRightToLeft(holder.itemView, holder.bindingAdapterPosition)
            item.setAnimated(true)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).isTask) {
            TYPE_TASK
        } else {
            TYPE_SEPARATOR
        }
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    companion object {
        private const val TYPE_TASK = 0
        private const val TYPE_SEPARATOR = 1
    }
}