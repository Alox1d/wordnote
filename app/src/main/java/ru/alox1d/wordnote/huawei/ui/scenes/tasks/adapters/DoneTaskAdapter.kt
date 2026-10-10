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
import ru.alox1d.wordnote.huawei.domain.model.ModelTask
import ru.alox1d.wordnote.huawei.helpers.util.Utils

class DoneTaskAdapter : TaskAdapter() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.model_task, parent, false)
        val title = v.findViewById<View>(R.id.tvTaskTitle) as TextView
        val description: Editor = v.findViewById(R.id.editor_fragDescription)
        val containerDescription = v.findViewById<FrameLayout>(R.id.cv_taskDescriptionContainer)
        val ivArrow = v.findViewById<ImageButton>(R.id.iv_taskArrow)
        val ivHidden = v.findViewById<ImageView>(R.id.iv_hidden)
        val date = v.findViewById<View>(R.id.tvTaskDate) as TextView
        val priority = v.findViewById<View>(R.id.cvTaskPriority) as CircleImageView
        val cv: MaterialCardView = v.findViewById(R.id.card_view_task)
        return TaskViewHolder(v, title, description, containerDescription, ivArrow, date, priority, cv, ivHidden)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        if (item.isTask) {
            holder.itemView.isEnabled = true
            val task = item as ModelTask
            val taskViewHolder = holder as TaskViewHolder
            val itemView = taskViewHolder.itemView
            val resources = itemView.resources
            taskViewHolder.title.text = task.title
            if (task.date != 0L) {
                taskViewHolder.date.text = Utils.getFullDate(task.date)
            } else {
                taskViewHolder.date.text = null
            }
            itemView.visibility = View.VISIBLE
            taskViewHolder.priority.isEnabled = true
            taskViewHolder.title.alpha = 0.2f
            taskViewHolder.date.alpha = 0.2f
            taskViewHolder.priority.setColorFilter(resources.getColor(task.priorityColor))
            taskViewHolder.priority.setImageResource(R.drawable.ic_check_circle_white_48dp)
            itemView.setOnLongClickListener {
                val handler = Handler()
                handler.postDelayed({ taskAdapterListener!!.onLongClick(position, taskViewHolder.description) }, 1000)
                true
            }

            // setUpExpand
            initExpand(position, task, taskViewHolder)
            taskViewHolder.priority.setOnClickListener {
                taskViewHolder.priority.isEnabled = false
                task.status = ModelTask.STATUS_CURRENT
                taskAdapterListener!!.onStatusChange(task, ModelTask.STATUS_CURRENT)
                taskViewHolder.title.alpha = 1.0f
                taskViewHolder.date.alpha = 0.6f
                taskViewHolder.priority.setColorFilter(resources.getColor(task.priorityColor))
                val flipIn = ObjectAnimator.ofFloat(taskViewHolder.priority, "rotationY", 180f, 0f)
                taskViewHolder.priority.setImageResource(R.drawable.ic_checkbox_blank_circle_white_48dp)
                flipIn.addListener(object : Animator.AnimatorListener {
                    override fun onAnimationStart(animation: Animator) {}
                    override fun onAnimationEnd(animation: Animator) {
                        if (task.status != ModelTask.STATUS_DONE) {
                            val translationX = ObjectAnimator.ofFloat(itemView,
                                    "translationX", 0f, -itemView.width.toFloat())
                            val translationXBack = ObjectAnimator.ofFloat(itemView,
                                    "translationX", -itemView.width.toFloat(), 0f)
                            translationX.addListener(object : Animator.AnimatorListener {
                                override fun onAnimationStart(animation: Animator) {}
                                override fun onAnimationEnd(animation: Animator) {
                                    itemView.visibility = View.GONE
                                    taskAdapterListener!!.onMove(task)
                                    removeItem(position)
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
            setHiddenTasks(task, taskViewHolder)
            if (!task.hasAnimated()) {
                fromRightToLeft(holder.itemView, position)
                task.setAnimated(true)
            }
        }
    }
}