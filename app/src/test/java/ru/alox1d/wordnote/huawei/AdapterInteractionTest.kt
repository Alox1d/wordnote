package ru.alox1d.wordnote.huawei

import android.app.Application
import android.os.Looper
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.appcompat.app.AppCompatActivity
import com.github.irshulx.Editor
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import ru.alox1d.wordnote.huawei.domain.model.ModelTask
import ru.alox1d.wordnote.huawei.ui.scenes.tasks.adapters.CurrentTaskAdapter
import ru.alox1d.wordnote.huawei.ui.scenes.tasks.adapters.DoneTaskAdapter
import ru.alox1d.wordnote.huawei.ui.scenes.tasks.adapters.TaskAdapter
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AdapterInteractionTest {
    @Test fun deletingEarlierCurrentNoteDoesNotChangeLongPressedNote() {
        checkLongPressedNote(CurrentTaskAdapter(), ModelTask.STATUS_CURRENT)
    }

    @Test fun deletingEarlierDoneNoteDoesNotChangeLongPressedNote() {
        checkLongPressedNote(DoneTaskAdapter(), ModelTask.STATUS_DONE)
    }

    @Test fun expandedNoteStaysExpandedWhenEarlierNoteIsDeletedAndCanCollapse() {
        val adapter = DoneTaskAdapter()
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        controller.get().setTheme(R.style.Theme_WordNote)
        controller.setup().use {
            val body = "{\"nodes\":[{\"type\":\"INPUT\",\"content\":[\"Body\"],\"contentStyles\":[\"NORMAL\"],\"textSettings\":{\"textColor\":\"#000000\"}}]}"
            adapter.items = mutableListOf(
                ModelTask("first", body, "", 0, 1, ModelTask.STATUS_DONE, 1, 0),
                ModelTask("target", body, "", 0, 1, ModelTask.STATUS_DONE, 2, 0))
            val list = RecyclerView(controller.get())
            list.layoutManager = LinearLayoutManager(controller.get())
            list.adapter = adapter
            controller.get().setContentView(list)
            fun layout() {
                list.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1800, View.MeasureSpec.EXACTLY))
                list.layout(0, 0, 1000, 1800)
            }
            layout()
            var holder = list.findViewHolderForAdapterPosition(1) as TaskAdapter.TaskViewHolder
            holder.ivArrow.performClick()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            layout()
            adapter.removeItem(0)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            layout()
            holder = list.findViewHolderForAdapterPosition(0) as TaskAdapter.TaskViewHolder
            assertEquals(View.VISIBLE, holder.containerDescription.visibility)
            holder.ivArrow.performClick()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            layout()
            holder = list.findViewHolderForAdapterPosition(0) as TaskAdapter.TaskViewHolder
            assertEquals(View.GONE, holder.containerDescription.visibility)
        }
    }

    private fun checkLongPressedNote(adapter: TaskAdapter, status: Int) {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        controller.get().setTheme(R.style.Theme_WordNote)
        controller.setup().use {
            val first = ModelTask("first", "{\"nodes\":[]}", "", 0, 1, status, 1, 0)
            val target = ModelTask("target", "{\"nodes\":[]}", "", 0, 1, status, 2, 0)
            adapter.items = mutableListOf(first, target)
            var selectedPosition = -1
            adapter.taskAdapterListener = object : TaskAdapter.TaskAdapterListener {
                override fun onAdd(newTask: ModelTask?, saveToDB: Boolean) {}
                override fun onStatusChange(task: ModelTask?, status: Int) {}
                override fun onMove(task: ModelTask?) {}
                override fun onClick(task: ModelTask?) {}
                override fun onExpand(position: Int) {}
                override fun onLongClick(layoutPosition: Int, editor: Editor) { selectedPosition = layoutPosition }
            }
            val list = RecyclerView(controller.get())
            list.layoutManager = LinearLayoutManager(controller.get())
            list.adapter = adapter
            controller.get().setContentView(list)
            list.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1800, View.MeasureSpec.EXACTLY))
            list.layout(0, 0, 1000, 1800)
            val holder = list.findViewHolderForAdapterPosition(1)!!
            adapter.removeItem(0)
            val view = if (adapter is CurrentTaskAdapter) holder.itemView.findViewById<android.view.View>(R.id.card_view_task) else holder.itemView
            view.performLongClick()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            assertEquals("The pressed note moved to position 0", 0, selectedPosition)
            assertEquals(target, adapter.getItem(selectedPosition))
        }
    }
}
