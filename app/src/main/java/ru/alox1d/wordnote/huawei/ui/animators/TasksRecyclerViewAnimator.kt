package ru.alox1d.wordnote.huawei.ui.animators

import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView

class TasksRecyclerViewAnimator : DefaultItemAnimator() {
    override fun animateAdd(holder: RecyclerView.ViewHolder?): Boolean {
        dispatchAddFinished(holder) // this is what bypasses the animation
        return true
    } /* this is the default implementation of animateAdd() in DefaultItemAnimator
@Override
    public boolean animateAdd(final RecyclerView.ViewHolder holder) {
        resetAnimation(holder);
        holder.itemView.setAlpha(0); // this is what caused the flashing/blinking
        mPendingAdditions.add(holder);
        return true;
    }
*/
}