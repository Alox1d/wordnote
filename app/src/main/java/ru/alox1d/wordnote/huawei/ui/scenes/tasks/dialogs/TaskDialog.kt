package ru.alox1d.wordnote.huawei.ui.scenes.tasks.dialogs

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.fragment.app.DialogFragment
import com.example.toothpick.annotation.ApplicationScope
import com.flask.colorpicker.ColorPickerView
import com.flask.colorpicker.builder.ColorPickerDialogBuilder
import com.github.irshulx.Editor
import com.github.irshulx.EditorListener
import com.github.irshulx.models.EditorContent
import com.github.irshulx.models.EditorTextStyle
import com.github.irshulx.models.Node
import com.google.android.material.color.MaterialColors
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputLayout
import com.huawei.wordnote.domain.services.dateparser.ParsedDate
import ru.alox1d.wordnote.huawei.R
import ru.alox1d.wordnote.huawei.WordNoteApp
import ru.alox1d.wordnote.huawei.di.module.ActivityModule
import ru.alox1d.wordnote.huawei.domain.model.ModelTask
import ru.alox1d.wordnote.huawei.domain.service.asr.IASRService
import ru.alox1d.wordnote.huawei.domain.services.tr.HuaweiTextRecognitionService
import ru.alox1d.wordnote.huawei.helpers.util.Utils
import ru.alox1d.wordnote.huawei.data.storage.AttachmentStore
import ru.alox1d.wordnote.huawei.ui.scenes.tasks.TasksActivity
import ru.alox1d.wordnote.huawei.ui.scenes.textrecognition.BaseTextRecognitionActivity
import ru.alox1d.wordnote.huawei.ui.scenes.textrecognition.TextRecognitionActivity
import toothpick.ktp.KTP
import toothpick.ktp.delegate.inject
import toothpick.smoothie.lifecycle.closeOnDestroy
import java.io.IOException
import java.util.*

abstract class TaskDialog() : DialogFragment() {
    private var hasSensitiveContent = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hasSensitiveContent = savedInstanceState?.getBoolean(SENSITIVE_DRAFT) == true ||
                (arguments?.getInt("is_hidden", ModelTask.HIDDEN_FALSE) ?: ModelTask.HIDDEN_FALSE) != ModelTask.HIDDEN_FALSE
    }

    private fun rememberSensitivity() {
        if ((arguments?.getInt("is_hidden", ModelTask.HIDDEN_FALSE) ?: ModelTask.HIDDEN_FALSE) != ModelTask.HIDDEN_FALSE ||
            (::switchHidden.isInitialized && switchHidden.isChecked)) {
            hasSensitiveContent = true
        }
        if (hasSensitiveContent) dialog?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    fun refreshHiddenNotesVisibility(): Boolean {
        rememberSensitivity()
        val currentDialog = dialog ?: return false
        if (hasSensitiveContent && (!WordNoteApp.isHiddenNotesUnlocked || !WordNoteApp.isActivityVisible)) {
            currentDialog.hide()
            return false
        }
        currentDialog.show()
        return true
    }

    override fun onStart() {
        // DialogFragment.onStart shows its window. Protect it before that call,
        // then hide a locked draft synchronously before the first frame.
        rememberSensitivity()
        super.onStart()
        refreshHiddenNotesVisibility()
    }

    override fun onResume() {
        super.onResume()
        refreshHiddenNotesVisibility()
    }

    override fun onPause() {
        rememberSensitivity()
        if (hasSensitiveContent) dialog?.hide()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        rememberSensitivity()
        outState.putBoolean(SENSITIVE_DRAFT, hasSensitiveContent)
        super.onSaveInstanceState(outState)
    }


    protected lateinit var editor: Editor
    protected lateinit var inflater: LayoutInflater
    protected lateinit var container: View
    protected lateinit var tilTitle: TextInputLayout
    protected lateinit var ibTitleRecognitionImage: ImageButton
    protected lateinit var ibTitleRecognitionVoice: ImageButton
    protected lateinit var etTitle: EditText
    protected lateinit var etDate: EditText
    protected lateinit var etTime: EditText
    protected lateinit var calendar: Calendar
    protected lateinit var switchHidden: SwitchMaterial

    protected val huaweiTextRecognitionService: HuaweiTextRecognitionService by inject()
    protected val asrService: IASRService by inject()

    protected var isTitleImageRecognition = false
    protected var isDescriptionImageRecognition = false
    protected var isTitleVoiceRecognition = false
    protected var isDescriptionVoiceRecognition = false

    private fun setupEditor(container: View) {
        container.findViewById<View>(R.id.action_h1).setOnClickListener { editor.updateTextStyle(EditorTextStyle.H1) }
        container.findViewById<View>(R.id.action_h2).setOnClickListener { editor.updateTextStyle(EditorTextStyle.H2) }
        container.findViewById<View>(R.id.action_h3).setOnClickListener { editor.updateTextStyle(EditorTextStyle.H3) }
        container.findViewById<View>(R.id.action_bold).setOnClickListener { editor.updateTextStyle(EditorTextStyle.BOLD) }
        container.findViewById<View>(R.id.action_Italic).setOnClickListener { editor.updateTextStyle(EditorTextStyle.ITALIC) }
        container.findViewById<View>(R.id.action_indent).setOnClickListener { editor.updateTextStyle(EditorTextStyle.INDENT) }
        container.findViewById<View>(R.id.action_blockquote).setOnClickListener { editor.updateTextStyle(EditorTextStyle.BLOCKQUOTE) }
        container.findViewById<View>(R.id.action_outdent).setOnClickListener { editor.updateTextStyle(EditorTextStyle.OUTDENT) }
        container.findViewById<View>(R.id.action_bulleted).setOnClickListener { editor.insertList(false) }
        container.findViewById<View>(R.id.action_unordered_numbered).setOnClickListener { editor.insertList(true) }
        container.findViewById<View>(R.id.action_hr).setOnClickListener { editor.insertDivider() }
        container.findViewById<View>(R.id.action_color).setOnClickListener {
            val colorPickerDialogBuilder = ColorPickerDialogBuilder
                    .with(container.context)
                    .setTitle(requireActivity().resources.getString(R.string.popUpColor_choose)) //.initialColor(Color.RED)
                    .wheelType(ColorPickerView.WHEEL_TYPE.FLOWER)
                    .density(12)
                    .setOnColorSelectedListener {
                    }
                    .setPositiveButton(requireActivity().resources.getString(R.string.dialog_ok)) { dialog, selectedColor, allColors ->
                        editor.updateTextColor(Utils.colorHex(selectedColor))
                    }
                    .setNegativeButton(requireActivity().resources.getString(R.string.dialog_cancel)) { dialog, which -> }
                    .build()
            colorPickerDialogBuilder.window!!.setBackgroundDrawableResource(R.drawable.dialog_background)
            colorPickerDialogBuilder.show()
        }
        container.findViewById<View>(R.id.action_insert_image).setOnClickListener {
            editor.openImagePicker()
        }
        container.findViewById<View>(R.id.action_recognize_image).setOnClickListener {
            isDescriptionImageRecognition = true
            recognizeImage()
        }
        container.findViewById<View>(R.id.action_recognize_voice).setOnClickListener {
            isDescriptionVoiceRecognition = true
            recognizeVoice()
        }
        container.findViewById<View>(R.id.action_insert_link).setOnClickListener { editor.insertLink() }
        container.findViewById<View>(R.id.action_erase).setOnClickListener { editor.clearAllContents() }
        val headingTypeface = Utils.getHeadingTypeface()
        val contentTypeface = Utils.getContentface()
        editor.headingTypeface = headingTypeface
        editor.contentTypeface = contentTypeface
        editor.setDividerLayout(R.layout.tmpl_divider_layout)
        editor.setEditorImageLayout(R.layout.editor_image_view)
        editor.setListItemLayout(R.layout.tmpl_list_item)
        editor.editorListener = object : EditorListener {
            override fun onTextChanged(editText: EditText, text: Editable) {
            }

            override fun onUpload(image: Bitmap, uuid: String) {
                try {
                    val imageFile = AttachmentStore(requireContext()).saveBitmap(image)
                    editor.onImageUploadComplete(imageFile.path, uuid)
                } catch (_: Exception) {
                    editor.onImageUploadFailed(uuid)
                    Toast.makeText(context, R.string.image_save_failed, Toast.LENGTH_LONG).show()
                }
            }

            override fun onRenderMacro(name: String, props: Map<String, Any>, index: Int): View? {
                return null
            }
        }
    }

    protected fun recognizeImage() {
        // Text recognition
        val permission = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA)
        ActivityCompat.requestPermissions(requireActivity(), permission, 1)

        if (huaweiTextRecognitionService.checkPermissions()) {
            startActivityForResult(Intent(requireActivity(), TextRecognitionActivity::class.java), TextRecognitionActivity.REQUEST)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == editor.PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null && data.data != null) {
            val uri = data.data
            try {
                val bitmap = MediaStore.Images.Media.getBitmap(requireActivity().contentResolver, uri)
                editor.insertImage(bitmap)
            } catch (e: IOException) {
                Toast.makeText(requireContext(), R.string.image_save_failed, Toast.LENGTH_SHORT).show()
            }
        } else if (requestCode == TextRecognitionActivity.REQUEST && resultCode == Activity.RESULT_OK) {
            val text = data?.extras?.getString(BaseTextRecognitionActivity.ML_TEXT)
            if (isDescriptionImageRecognition) {
                setDescription(text)
            }
            if (isTitleImageRecognition) {
                processTitle(text)
            }

        } else if (resultCode == Activity.RESULT_CANCELED) {
            //Write your code if there's no result
            //Toast.makeText(getActivity().getApplicationContext(), "Cancelled", Toast.LENGTH_SHORT).show();
            // editor.RestoreState();
        }
        isDescriptionImageRecognition = false
        isTitleImageRecognition = false
    }

    private fun setDescription(text: String?) {
        val editedDescription = "<p>" + text?.replace("\n", "</br>")
        val currentDescription = editor.content
        if (currentDescription.nodes[0].content[0] == "") {
            editor.clearAllContents()
            editor.render(editedDescription)
            editor.content.nodes[0].content.removeAt(0);
        } else {
            editor.render(editedDescription)
        }
    }

    private fun fixBottomButtons(alertDialog: AlertDialog) {
        alertDialog.window!!.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        inflater = requireActivity().layoutInflater
        container = inflater.inflate(R.layout.dialog_task, null)
        tilTitle = container.findViewById(R.id.til_DialogTaskTitle)
        ibTitleRecognitionImage = container.findViewById(R.id.ib_taskTitleRecognizeImage)
        ibTitleRecognitionVoice = container.findViewById(R.id.ib_taskTitleRecognizeVoice)
        etTitle = tilTitle.getEditText()!!

        setDI()
        initServices()

        tilTitle.setEndIconOnClickListener(View.OnClickListener { v: View? ->
            parseTitleDate()
        }
        )
        ibTitleRecognitionImage.setOnClickListener {
            isTitleImageRecognition = true
            recognizeImage()
        }
        ibTitleRecognitionVoice.setOnClickListener {
            isTitleVoiceRecognition = true
            recognizeVoice()
        }



        return super.onCreateDialog(savedInstanceState)
    }

    private fun setDI() {
            KTP.openScopes(ApplicationScope::class.java, this)
                .installModules(ActivityModule(requireActivity()))
                .closeOnDestroy(this)
                .inject(this)
    }

    private fun initServices() {
//        asrService = ASRService(activity!!)
//        tr = TextRecognitionService(activity!!)
    }

    protected fun recognizeVoice() {
        val permission = arrayOf(Manifest.permission.INTERNET, Manifest.permission.RECORD_AUDIO)
        ActivityCompat.requestPermissions(requireActivity(), permission, 1)

        if (asrService.checkPermissions()) {
            (activity as TasksActivity).setASRListener { text: String? ->
                if (isTitleVoiceRecognition) {
                    processTitle(text)
                } else if (isDescriptionVoiceRecognition) {
                    setDescription(text)
                }
                isDescriptionVoiceRecognition = false
                isTitleVoiceRecognition = false
            }
            asrService.startASR()

        }
    }

    private fun processTitle(text: String?) {
        etTitle.setText(text)
        parseTitleDate()
    }


    protected fun setupAlertDialog(builder: AlertDialog.Builder): AlertDialog {
        val alertDialog = builder.create()
        alertDialog.window!!.setBackgroundDrawableResource(R.drawable.dialog_background)
        rememberSensitivity()
        if (hasSensitiveContent) alertDialog.window!!.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        alertDialog.setOnShowListener { dialog ->
            val positiveButton = (dialog as AlertDialog).getButton(DialogInterface.BUTTON_POSITIVE)
            if (etTitle.length() == 0) {
                positiveButton.isEnabled = false
                tilTitle.error = requireActivity().resources.getString(R.string.dialog_error_empty_title)
                tilTitle.errorIconDrawable = null
            }
            setupEditor(container)
            etTitle.requestFocus()
            etTitle.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(text: CharSequence, start: Int, before: Int, count: Int) {
                    if (text.length == 0) {
                        positiveButton.isEnabled = false
                        tilTitle.error = activity!!.resources.getString(R.string.dialog_error_empty_title)
                    } else {
                        positiveButton.isEnabled = true
                        tilTitle.isErrorEnabled = false
                    }
                }

                override fun afterTextChanged(s: Editable) {}
            })
        }
        fixBottomButtons(alertDialog)
        return alertDialog
    }

    private fun parseTitleDate() {
        try {
            ParsedDate.prepareParse(requireContext())
            val parsedDates = ParsedDate.parseDate(etTitle.text.toString())
//            val parsedDates = ParsedDate.parseDate("buy at 5 pm")

            if (parsedDates.size != 0) {
                val parsedDate = parsedDates[0]
                calendar.setTimeInMillis(parsedDate.valueOf().target_date.timeInMillis)
                etDate.setText(Utils.getDate(calendar.timeInMillis))
                etTime.setText(Utils.getTime(calendar.timeInMillis))
                etTitle.setText(parsedDates[0].string as CharSequence)
                etTitle.setSelection(etTitle.text.length);
            }
        } catch (e: Exception) {
            //TODO Задача не найдена в тексте
        }
    }

    protected fun setHiddenState(it: Boolean, task: ModelTask) {
        if (it) {
            hasSensitiveContent = true
            dialog?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            if (WordNoteApp.isHiddenNotesUnlocked)
                task.isHidden = ModelTask.HIDDEN_TRUE_UNLOCKED else task.isHidden = ModelTask.HIDDEN_TRUE
        } else {
            task.isHidden = ModelTask.HIDDEN_FALSE
        }
    }
    protected fun initDescriptionTextColor(editorContent: EditorContent, context: Context) {
        for (node in editorContent.nodes) {
            if (node.childs != null){
                for (child in node.childs) {
                    updateNodeTextColor(child, context)

                }
            }
            updateNodeTextColor(node, context)
        }
    }

    private fun updateNodeTextColor(node: Node, context: Context) {
        node.textSettings?.let {
            when (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                Configuration.UI_MODE_NIGHT_YES -> {
                    val black = Utils.colorHex(MaterialColors.getColor(context, android.R.attr.textColor, Color.BLACK))
                    if (node.textSettings.textColor == black) {
                        val white = Utils.colorHex(MaterialColors.getColor(context, android.R.attr.textColor, Color.WHITE))
                        it.setTextColor(white)
                    }
                }
            }
        }
    }

    private companion object {
        const val SENSITIVE_DRAFT = "sensitive_private_draft"
    }
}
