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
import android.content.pm.PackageManager
import com.huawei.hms.mlplugin.asr.MLAsrCaptureConstants
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
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
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
import ru.alox1d.wordnote.huawei.ui.scenes.textrecognition.BaseTextRecognitionActivity
import ru.alox1d.wordnote.huawei.ui.scenes.textrecognition.TextRecognitionActivity
import toothpick.ktp.KTP
import toothpick.ktp.delegate.inject
import toothpick.smoothie.lifecycle.closeOnDestroy
import java.io.IOException
import java.util.*

abstract class TaskDialog() : DialogFragment() {
    private var hasSensitiveContent = false
    private var restoredDraft: Bundle? = null
    private val auxiliaryDialogs = linkedSetOf<Dialog>()
    private val childDialogs = mutableMapOf<Fragment, Dialog>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        childFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentViewCreated(fm: FragmentManager, fragment: Fragment, view: View, state: Bundle?) {
                val child = (fragment as? DialogFragment)?.dialog ?: return
                childDialogs[fragment] = child
                auxiliaryDialogs += child
                rememberSensitivity()
            }

            override fun onFragmentStarted(fm: FragmentManager, fragment: Fragment) {
                val child = childDialogs[fragment] ?: return
                // A child DialogFragment shows itself during onStart; mask it before the next draw.
                if (hasSensitiveContent && (dialog?.isShowing != true || !WordNoteApp.isActivityVisible)) child.hide()
            }

            override fun onFragmentDestroyed(fm: FragmentManager, fragment: Fragment) {
                childDialogs.remove(fragment)?.let { auxiliaryDialogs.remove(it) }
            }
        }, false)
        restoredDraft = savedInstanceState?.getBundle(DRAFT_STATE)
        isTitleImageRecognition = savedInstanceState?.getBoolean("ocr_title") == true
        isDescriptionImageRecognition = savedInstanceState?.getBoolean("ocr_body") == true
        isTitleVoiceRecognition = savedInstanceState?.getBoolean("voice_title") == true
        isDescriptionVoiceRecognition = savedInstanceState?.getBoolean("voice_body") == true
        hasSensitiveContent = savedInstanceState?.getBoolean(SENSITIVE_DRAFT) == true ||
                (arguments?.getInt("is_hidden", ModelTask.HIDDEN_FALSE) ?: ModelTask.HIDDEN_FALSE) != ModelTask.HIDDEN_FALSE
    }

    private fun rememberSensitivity() {
        if ((arguments?.getInt("is_hidden", ModelTask.HIDDEN_FALSE) ?: ModelTask.HIDDEN_FALSE) != ModelTask.HIDDEN_FALSE ||
            (::switchHidden.isInitialized && switchHidden.isChecked)) {
            hasSensitiveContent = true
        }
        if (hasSensitiveContent) {
            dialog?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            auxiliaryDialogs.forEach { it.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE) }
        }
    }

    fun refreshHiddenNotesVisibility(): Boolean {
        rememberSensitivity()
        val currentDialog = dialog ?: return false
        if (hasSensitiveContent && (!WordNoteApp.isHiddenNotesUnlocked || !WordNoteApp.isActivityVisible)) {
            currentDialog.hide()
            auxiliaryDialogs.forEach { it.hide() }
            return false
        }
        currentDialog.show()
        auxiliaryDialogs.forEach { it.show() }
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
        if (hasSensitiveContent) {
            dialog?.hide()
            auxiliaryDialogs.forEach { it.hide() }
        }
        super.onPause()
    }

    protected fun showAuxiliaryDialog(child: Dialog) {
        rememberSensitivity()
        if (hasSensitiveContent) child.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        auxiliaryDialogs += child
        child.setOnDismissListener { auxiliaryDialogs.remove(child) }
        if (dialog?.isShowing == true) child.show()
    }

    override fun onDestroyView() {
        auxiliaryDialogs.toList().forEach { it.dismiss() }
        auxiliaryDialogs.clear()
        childDialogs.clear()
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        rememberSensitivity()
        outState.putBoolean(SENSITIVE_DRAFT, hasSensitiveContent)
        if (::editor.isInitialized && ::calendar.isInitialized) {
            outState.putBundle(DRAFT_STATE, Bundle().apply {
                putString("title", etTitle.text.toString())
                putString("content", editor.contentAsSerialized)
                putString("date_text", etDate.text.toString())
                putString("time_text", etTime.text.toString())
                putLong("calendar", calendar.timeInMillis)
                putInt("priority", container.findViewById<android.widget.Spinner>(R.id.spDialogTaskPriority).selectedItemPosition)
                putBoolean("hidden", switchHidden.isChecked)
            })
        }
        outState.putBoolean("ocr_title", isTitleImageRecognition)
        outState.putBoolean("ocr_body", isDescriptionImageRecognition)
        outState.putBoolean("voice_title", isTitleVoiceRecognition)
        outState.putBoolean("voice_body", isDescriptionVoiceRecognition)
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
            showAuxiliaryDialog(colorPickerDialogBuilder)
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
        container.findViewById<View>(R.id.action_insert_link).setOnClickListener { showAuxiliaryDialog(editor.createLinkDialog()) }
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
        if (huaweiTextRecognitionService.checkPermissions()) startImageRecognition()
        else requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
    }

    private fun startImageRecognition() {
        startActivityForResult(Intent(requireActivity(), TextRecognitionActivity::class.java), TextRecognitionActivity.REQUEST)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        when (requestCode) {
            CAMERA_PERMISSION_REQUEST -> if (granted) startImageRecognition() else {
                isTitleImageRecognition = false
                isDescriptionImageRecognition = false
            }
            MICROPHONE_PERMISSION_REQUEST -> if (granted) asrService.startASR() else {
                isTitleVoiceRecognition = false
                isDescriptionVoiceRecognition = false
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100) {
            if (isTitleVoiceRecognition || isDescriptionVoiceRecognition) {
                val text = data?.getStringExtra(MLAsrCaptureConstants.ASR_RESULT)
                if (resultCode == MLAsrCaptureConstants.ASR_SUCCESS && text != null) {
                    if (isTitleVoiceRecognition) processTitle(text) else setDescription(text)
                } else if (resultCode == MLAsrCaptureConstants.ASR_FAILURE) {
                    Toast.makeText(context, R.string.failed_to_get_data, Toast.LENGTH_SHORT).show()
                }
                isTitleVoiceRecognition = false
                isDescriptionVoiceRecognition = false
            }
            return
        }
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
        if (requestCode == TextRecognitionActivity.REQUEST) {
            isDescriptionImageRecognition = false
            isTitleImageRecognition = false
        }
    }

    private fun setDescription(text: String?) {
        if (text.isNullOrEmpty()) return
        // Recognition returns plain text, including characters that are meaningful in HTML.
        val escaped = android.text.TextUtils.htmlEncode(text).replace("\n", "<br>")
        editor.render("<p>$escaped</p>")
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
        if (asrService.checkPermissions()) asrService.startASR()
        else requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), MICROPHONE_PERMISSION_REQUEST)
    }

    private fun processTitle(text: String?) {
        etTitle.setText(text)
        parseTitleDate()
    }


    protected fun setupAlertDialog(builder: AlertDialog.Builder): AlertDialog {
        restoredDraft?.let { state ->
            etTitle.setText(state.getString("title"))
            editor.clearAllContents()
            editor.render(editor.getContentDeserialized(state.getString("content")))
            etDate.setText(state.getString("date_text"))
            etTime.setText(state.getString("time_text"))
            calendar.timeInMillis = state.getLong("calendar")
            container.findViewById<android.widget.Spinner>(R.id.spDialogTaskPriority).setSelection(state.getInt("priority"))
            switchHidden.isChecked = state.getBoolean("hidden")
        }
        restoredDraft = null
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
        const val DRAFT_STATE = "task_editor_draft"
        const val CAMERA_PERMISSION_REQUEST = 2101
        const val MICROPHONE_PERMISSION_REQUEST = 2102
    }
}
