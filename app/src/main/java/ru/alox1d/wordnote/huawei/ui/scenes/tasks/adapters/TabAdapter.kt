package ru.alox1d.wordnote.huawei.ui.scenes.tasks.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import ru.alox1d.wordnote.huawei.ui.scenes.tasks.CurrentTaskFragment
import ru.alox1d.wordnote.huawei.ui.scenes.tasks.DoneTaskFragment

class TabAdapter(fm: FragmentManager?, private val numberOfTabs: Int) : FragmentStatePagerAdapter(fm!!, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

    private val currentTaskFragment: CurrentTaskFragment
    private val doneTaskFragment: DoneTaskFragment

    init {
        currentTaskFragment = CurrentTaskFragment()
        doneTaskFragment = DoneTaskFragment()
    }

    override fun getItem(i: Int): Fragment {
        return when (i) {
            CURRENT_TASK_FRAGMENT_POSITION -> currentTaskFragment
            DONE_TASK_FRAGMENT_POSITION -> doneTaskFragment
            else -> currentTaskFragment
        }
    }

    override fun getCount(): Int {
        return numberOfTabs
    }

    companion object {
        const val CURRENT_TASK_FRAGMENT_POSITION = 0
        const val DONE_TASK_FRAGMENT_POSITION = 1
    }

}