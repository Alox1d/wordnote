package ru.alox1d.wordnote.huawei.ui.scenes.tasks

import android.animation.LayoutTransition
import android.app.ActivityOptions
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.FragmentManager
import androidx.viewpager.widget.ViewPager
import com.example.toothpick.annotation.ApplicationScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.huawei.hms.mlplugin.asr.MLAsrCaptureConstants
import ru.alox1d.wordnote.huawei.R
import ru.alox1d.wordnote.huawei.WordNoteApp
import ru.alox1d.wordnote.huawei.WordNoteApp.Companion.activityPaused
import ru.alox1d.wordnote.huawei.WordNoteApp.Companion.activityResumed
import ru.alox1d.wordnote.huawei.data.storage.PreferenceStorage
import ru.alox1d.wordnote.huawei.device.alarm.AlarmHelper.Companion.instance
import ru.alox1d.wordnote.huawei.device.service.ad.IAdsService
import ru.alox1d.wordnote.huawei.di.module.ActivityModule
import ru.alox1d.wordnote.huawei.di.module.AppCompatActivityModule
import ru.alox1d.wordnote.huawei.di.module.MainModule
import ru.alox1d.wordnote.huawei.domain.model.ModelTask
import ru.alox1d.wordnote.huawei.domain.model.TaskModes
import ru.alox1d.wordnote.huawei.domain.service.fido.IFIDOService
import ru.alox1d.wordnote.huawei.ui.dialogs.DialogHelper
import ru.alox1d.wordnote.huawei.ui.scenes.backup.BackupActivity
import ru.alox1d.wordnote.huawei.ui.scenes.splash.SplashFragment
import ru.alox1d.wordnote.huawei.ui.scenes.tasks.CurrentTaskFragment.OnTaskDoneListener
import ru.alox1d.wordnote.huawei.ui.scenes.tasks.DoneTaskFragment.OnTaskRestoreListener
import ru.alox1d.wordnote.huawei.ui.scenes.tasks.adapters.TabAdapter
import ru.alox1d.wordnote.huawei.ui.scenes.tasks.dialogs.AddingTaskDialogFragment
import ru.alox1d.wordnote.huawei.ui.scenes.tasks.dialogs.AddingTaskDialogFragment.AddingTaskListener
import ru.alox1d.wordnote.huawei.ui.widgets.ButtonsWidget.Companion.WIDGET_REQUEST_CODE
import ru.alox1d.wordnote.huawei.ui.widgets.ButtonsWidget.Companion.WIDGET_REQUEST_CODE_ADD
import ru.alox1d.wordnote.huawei.ui.widgets.ButtonsWidget.Companion.WIDGET_REQUEST_CODE_RECOGNIZE_CAMERA
import ru.alox1d.wordnote.huawei.ui.widgets.ButtonsWidget.Companion.WIDGET_REQUEST_CODE_RECOGNIZE_VOICE
import toothpick.ktp.KTP
import toothpick.ktp.delegate.inject
import toothpick.smoothie.lifecycle.closeOnDestroy


class TasksActivity : AppCompatActivity() {
    private lateinit var fragmentManager: FragmentManager
    var preferenceStorage: PreferenceStorage? = null
    var tabAdapter: TabAdapter? = null
    var tabLayout: TabLayout? = null
    var viewPager: ViewPager? = null
    lateinit var currentTaskFragment: TaskFragment
    lateinit var doneTaskFragment: TaskFragment
    var taskFragments: MutableList<TaskFragment> = ArrayList()
    var searchView: SearchView? = null
//    private var taskDatabase: TaskDatabase? = null
    var addingTaskDialogFragment: AddingTaskDialogFragment? = null
    private var mASRListener: ((String?) -> Unit)? = null
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private val mFIDOService: IFIDOService by inject()
    private val adsService: IAdsService by inject()


    fun setASRListener(mASRListener: (String?) -> Unit) {
        this.mASRListener = mASRListener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        loadPreferences()
        setDI()
        setDeviceServices()
        setAlarm()
//        setDB()
        setUI()
        onNewIntent(intent)
    }

    private fun setDI() {
        KTP.openScopes(ApplicationScope::class.java, this)
            .installModules(
                MainModule(applicationContext),
                ActivityModule(this),
                AppCompatActivityModule(this)
            )
            .closeOnDestroy(this)
            .inject(this)
    }

    private fun loadPreferences() {
        PreferenceStorage.INSTANCE.init(applicationContext)
        preferenceStorage = PreferenceStorage.INSTANCE
    }

    private fun setDeviceServices() {
        adsService.showBanner(this, R.id.banner)
    }

    private fun setAlarm() {
        instance.init(applicationContext)
    }

//    private fun setDB() {
//        taskDatabase = TaskDatabase.getInstance(this)
//    }

    private fun processWidgetsRequest(widgetIntent: Intent): Boolean {
        if (widgetIntent.extras?.getInt(WIDGET_REQUEST_CODE) == WIDGET_REQUEST_CODE_ADD) {
            showAddTaskDialog()
            return true

        } else if (widgetIntent.extras?.getInt(WIDGET_REQUEST_CODE) == WIDGET_REQUEST_CODE_RECOGNIZE_VOICE) {
            showAddTaskDialog(TaskModes.VOICE_RECOG)
            return true

        } else if (widgetIntent.extras?.getInt(WIDGET_REQUEST_CODE) == WIDGET_REQUEST_CODE_RECOGNIZE_CAMERA) {
            showAddTaskDialog(TaskModes.IMAGE_RECOG)
            return true
        }
        return false

    }

    private fun runNightMode() {
        if (preferenceStorage!!.getBoolean(PreferenceStorage.DARK_IS_ENABLED)) {
            AppCompatDelegate.setDefaultNightMode(
                AppCompatDelegate.MODE_NIGHT_YES
            )
        } else {
            AppCompatDelegate.setDefaultNightMode(
                AppCompatDelegate.MODE_NIGHT_UNSPECIFIED
            )
        }
    }

    override fun onSaveInstanceState(outState: Bundle, outPersistentState: PersistableBundle) {}
    override fun onResume() {
        super.onResume()
        activityResumed()
        setSearchView()
        animateSearchView()
    }

    private fun setSearchView() {
        searchView = findViewById<View>(R.id.search_view) as SearchView
        searchView!!.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String): Boolean {
                for (frag in taskFragments) {
                    frag.findTasks(newText)
                }
                return false
            }
        })
        val searchBar = searchView!!.findViewById<View>(R.id.search_bar) as LinearLayout
        searchBar.layoutTransition = LayoutTransition()
    }

    override fun onPause() {
        super.onPause()
        activityPaused()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.extras != null) {
            val timeStamp = intent.getLongExtra("time_stamp", 0L)
            if (timeStamp != 0L)
                for (frag in taskFragments) {
                    // нет смысла в массиве TaskFragment'ов, т.к. всего 2 фрагмента
                    frag.expandTask(timeStamp)
                }
            processWidgetsRequest(intent)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        val splashItem = menu.findItem(R.id.action_splash)
        val snItem = menu.findItem(R.id.action_social_network)
        val darkItem = menu.findItem(R.id.action_dark_theme)
        splashItem.isChecked = preferenceStorage!!.getBoolean(PreferenceStorage.SPLASH_IS_INVISIBLE)
        snItem.isChecked = preferenceStorage!!.getBoolean(PreferenceStorage.SN_SYNC_IS_ACTIVE)
        darkItem.isChecked = preferenceStorage!!.getBoolean(PreferenceStorage.DARK_IS_ENABLED)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        val id = item.itemId
        when (id) {
            R.id.action_splash -> {
                item.isChecked = !item.isChecked
                preferenceStorage!!.putBoolean(
                    PreferenceStorage.SPLASH_IS_INVISIBLE,
                    item.isChecked
                )
                return true
            }
            R.id.action_about -> {
                val dialogHelper = DialogHelper(this)
                dialogHelper.showAbout()
                return true
            }
            R.id.action_how_to_use -> {
                val dialogHelper = DialogHelper(this)
                dialogHelper.showHowToUse()
                return true
            }
            R.id.action_sync -> {
                startBackupActivity()
                return true
            }
            R.id.action_dark_theme -> {
                item.isChecked = !item.isChecked
                preferenceStorage!!.putBoolean(PreferenceStorage.DARK_IS_ENABLED, item.isChecked)
                if (item.isChecked) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                } else {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                }
                dayNightRecreating()
                return true
            }
            R.id.action_hidden_notes -> {
                if (!WordNoteApp.isHiddenNotesUnlocked) {
                    mFIDOService.fingerAuthenticateWithoutCryptoObject { result ->
                        WordNoteApp.isHiddenNotesUnlocked = true
                        for (frag in taskFragments) {
                            frag.addHiddenTasks()
                        }
                    }
                } else {
                    WordNoteApp.isHiddenNotesUnlocked = false
                    for (frag in taskFragments) {
                        frag.removeHiddenTasks()
                    }
                }

            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun TasksActivity.startBackupActivity() {
        val i = Intent(this, BackupActivity::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            startActivityForResult(
                i,
                BackupActivity.REQUEST_CODE_OK,
                ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
            )
        } else {
            startActivityForResult(i, BackupActivity.REQUEST_CODE_OK)
        }
    }

    private fun runSplash() {
        if (!preferenceStorage!!.getBoolean(PreferenceStorage.SPLASH_IS_INVISIBLE)) {
            val splashFragment = SplashFragment()
            fragmentManager.beginTransaction()
                .replace(R.id.content_frame, splashFragment)
                .addToBackStack(null)
                .commit()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        for (fragment in supportFragmentManager.fragments) {
            fragment.onActivityResult(requestCode, resultCode, data)
            when (resultCode) {
                BackupActivity.REQUEST_CODE_OK -> {
                }
            }
        }
        if (requestCode == 100) {
            when (resultCode) {
                MLAsrCaptureConstants.ASR_SUCCESS -> if (data != null) {
                    val bundle = data.extras
                    if (bundle != null && bundle.containsKey(MLAsrCaptureConstants.ASR_RESULT)) {
                        val text = bundle.getString(MLAsrCaptureConstants.ASR_RESULT).toString()
                        mASRListener!!.invoke(text)
                    }
                }
                MLAsrCaptureConstants.ASR_FAILURE -> if (data != null) {
                    val bundle = data.extras
                    if (bundle != null && bundle.containsKey(MLAsrCaptureConstants.ASR_ERROR_CODE)) {
                        val errorCode = bundle.getInt(MLAsrCaptureConstants.ASR_ERROR_CODE)
                        Toast.makeText(
                            this,
                            getString(R.string.error_code) + errorCode,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    if (bundle != null && bundle.containsKey(MLAsrCaptureConstants.ASR_ERROR_MESSAGE)) {
                        val errorMsg = bundle.getString(MLAsrCaptureConstants.ASR_ERROR_MESSAGE)
                        Toast.makeText(
                            this,
                            getString(R.string.error_code) + errorMsg,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    Toast.makeText(this, R.string.failed_to_get_data, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setUI() {

        fragmentManager = supportFragmentManager

        runSplash()

        val toolbar = findViewById<View>(R.id.toolbar) as Toolbar
        toolbar.let { setSupportActionBar(it) }
        tabLayout = findViewById<View>(R.id.tab_layout) as TabLayout
        tabLayout!!.addTab(tabLayout!!.newTab().setText(R.string.current_task))
        tabLayout!!.addTab(tabLayout!!.newTab().setText(R.string.done_task))
        viewPager = findViewById<View>(R.id.pager) as ViewPager
        tabAdapter = TabAdapter(fragmentManager, 2)
        viewPager!!.adapter = tabAdapter
        viewPager!!.addOnPageChangeListener(TabLayout.TabLayoutOnPageChangeListener(tabLayout))
        tabLayout!!.setOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewPager!!.currentItem = tab.position
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        currentTaskFragment =
            tabAdapter!!.getItem(TabAdapter.CURRENT_TASK_FRAGMENT_POSITION) as CurrentTaskFragment
        currentTaskFragment.addListener(object : OnTaskDoneListener {
            override fun onTaskDone(task: ModelTask) {
                for (frag in taskFragments) {
                    if (frag is DoneTaskFragment) {
                        // нет смысла в массиве TaskFragment'ов, т.к. всего 2 фрагмента
                        doneTaskFragment.addTask(task, false)
                    }
                }
            }
        })
        taskFragments.add(currentTaskFragment)
        doneTaskFragment =
            tabAdapter!!.getItem(TabAdapter.DONE_TASK_FRAGMENT_POSITION) as DoneTaskFragment
        doneTaskFragment.addListener(object : OnTaskRestoreListener {
            override fun onTaskRestore(task: ModelTask) {
                for (frag in taskFragments) {
                    (frag as? CurrentTaskFragment)?.addTask(task, false)
                }
            }
        })
        taskFragments.add(doneTaskFragment)
        val fab = findViewById<View>(R.id.fab) as FloatingActionButton
        fab.setOnClickListener {
            showAddTaskDialog()
        }

    }

    private fun showAddTaskDialog(mode: TaskModes = TaskModes.NONE) {
        addingTaskDialogFragment = AddingTaskDialogFragment.newInstance(mode)
        addingTaskDialogFragment!!.addListener(object : AddingTaskListener {
            override fun onTaskAdd(newTask: ModelTask) {
                for (frag in taskFragments) {
                    (frag as? CurrentTaskFragment)?.addTask(newTask, true)
                }
            }

            override fun onTaskAddingCancel() {}
        })

        addingTaskDialogFragment!!.show(fragmentManager, "AddingTaskDialogFragment")
    }

    override fun onStart() {
        runNightMode()
        super.onStart()
    }

    private fun dayNightRecreating() {
        val fragmentTransaction = fragmentManager!!.beginTransaction()
        val fragments = fragmentManager!!.fragments
        if (!fragments.isEmpty()) for (frag in fragments) {
            fragmentTransaction.remove(frag)
        }

        // Find and delete other Fragments
        fragmentTransaction.commit() // or commitAllowingStateLoss()
        recreate()
    }

    private fun animateSearchView() {
        searchView!!.post {
            try {
                Thread.sleep(SEARCHVIEW_APPEAR_DURATION.toLong())
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
            searchView!!.setIconifiedByDefault(false)
        }
    }

    companion object {
        private const val SEARCHVIEW_APPEAR_DURATION = 200
        const val CURRENT_TASKS = "CURRENT_TASKS"
        const val DONE_TASKS = "DONE_TASKS"
    }
}