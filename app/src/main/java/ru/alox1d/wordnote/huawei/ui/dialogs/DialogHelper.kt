package ru.alox1d.wordnote.huawei.ui.dialogs

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ru.alox1d.wordnote.huawei.R

class DialogHelper(private val context: Context) {

    private val factory: LayoutInflater? = null
    private var builder: MaterialAlertDialogBuilder = MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_Crane_MaterialComponents_Dialog_Alert)
    private val view: View? = null
    private val dialog: Dialog? = null

    fun showAbout() {
        val factory = LayoutInflater.from(context)
        val view = factory.inflate(R.layout.dialog_about_program, null)
        builder.setView(view)
        builder.setNegativeButton(R.string.close) { dlg, sumthin -> }
        builder.show()
    }

    fun showHowToUse() {
        val factory = LayoutInflater.from(context)
        val view = factory.inflate(R.layout.dialog_how_to_use_program, null)
        builder.setView(view)
        builder.setNegativeButton(R.string.close) { dlg, sumthin -> }
        builder.show()
    }

    fun showResult(title: String, msg: String) {
        builder.setMessage(R.string.dialog_removing_message)
        builder.setTitle(title)
        builder.setMessage(msg)
        builder.setPositiveButton(R.string.close, null)
        builder.show()
    }

}
