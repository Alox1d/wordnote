package ru.alox1d.wordnote.huawei.ui.scenes.tasks

import ru.alox1d.wordnote.huawei.helpers.applySystemInsets
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
import android.view.WindowManager
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
import ru.alox1d.wordnote.huawei.ui.scenes.tasks.dialogs.TaskDialog
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
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private val mFIDOService: IFIDOService by inject()
    private val adsService: IAdsService by inject()
    private var hiddenNotesActivityResumed = false
    private var hiddenAuthenticationPending = false
    private var hiddenAuthenticationRequest = 0L
    private var unlockOnResume = false
    private var sensitiveWindowShown = false

    private val taskDoneListener = object : OnTaskDoneListener {
        override fun onTaskDone(task: ModelTask) { doneTaskFragment.addTask(task, false) }
    }
    private val taskRestoreListener = object : OnTaskRestoreListener {
        override fun onTaskRestore(task: ModelTask) { currentTaskFragment.addTask(task, false) }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        WordNoteApp.isHiddenNotesUnlocked = false
        sensitiveWindowShown = savedInstanceState?.getBoolean(SENSITIVE_WINDOW_SHOWN) == true
        if (sensitiveWindowShown) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applySystemInsets()

        loadPreferences()
        setDI()
        setDeviceServices()
        setAlarm()
//        setDB()
        setUI()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1003)
        }
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
        hiddenNotesActivityResumed = true
        activityResumed()
        bindTaskFragments()
        val shouldUnlock = unlockOnResume
        unlockOnResume = false
        setHiddenNotesUnlocked(shouldUnlock)
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
        hiddenNotesActivityResumed = false
        activityPaused()
        setHiddenNotesUnlocked(false)
        super.onPause()
    }

    override fun onDestroy() {
        invalidateHiddenAuthentication()
        WordNoteApp.isHiddenNotesUnlocked = false
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(SENSITIVE_WINDOW_SHOWN, sensitiveWindowShown)
        super.onSaveInstanceState(outState)
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
                if (WordNoteApp.isHiddenNotesUnlocked) {
                    invalidateHiddenAuthentication()
                    setHiddenNotesUnlocked(false)
                } else {
                    authenticateHiddenNotes()
                }
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun authenticateHiddenNotes() {
        if (hiddenAuthenticationPending) return
        hiddenAuthenticationPending = true
        val request = ++hiddenAuthenticationRequest
        try {
            mFIDOService.fingerAuthenticateWithoutCryptoObject { success ->
                if (request != hiddenAuthenticationRequest || !hiddenAuthenticationPending || isDestroyed || isFinishing) {
                    return@fingerAuthenticateWithoutCryptoObject
                }
                hiddenAuthenticationPending = false
                if (!success) {
                    unlockOnResume = false
                    setHiddenNotesUnlocked(false)
                } else if (hiddenNotesActivityResumed) {
                    setHiddenNotesUnlocked(true)
                } else {
                    // The system PIN screen may stop this activity. Keep content
                    // locked until it returns; a destroyed activity cannot unlock.
                    unlockOnResume = true
                }
            }
        } catch (error: RuntimeException) {
            hiddenAuthenticationPending = false
            unlockOnResume = false
            setHiddenNotesUnlocked(false)
            Toast.makeText(this, R.string.auth_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun invalidateHiddenAuthentication() {
        hiddenAuthenticationRequest++
        hiddenAuthenticationPending = false
        unlockOnResume = false
    }

    private fun setHiddenNotesUnlocked(unlocked: Boolean) {
        if (unlocked) {
            sensitiveWindowShown = true
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        WordNoteApp.isHiddenNotesUnlocked = unlocked
        taskFragments.forEach { it.refreshTasks() }
        supportFragmentManager.fragments.filterIsInstance<TaskDialog>().forEach {
            it.refreshHiddenNotesVisibility()
        }
    }

    private fun bindTaskFragments() {
        val liveFragments = supportFragmentManager.fragments
        currentTaskFragment = liveFragments.filterIsInstance<CurrentTaskFragment>().firstOrNull { it.isAdded }
            ?: currentTaskFragment
        doneTaskFragment = liveFragments.filterIsInstance<DoneTaskFragment>().firstOrNull { it.isAdded }
            ?: doneTaskFragment
        taskFragments.clear()
        taskFragments.add(currentTaskFragment)
        taskFragments.add(doneTaskFragment)
        currentTaskFragment.addListener(taskDoneListener)
        doneTaskFragment.addListener(taskRestoreListener)
    }

    fun onTaskAdded(task: ModelTask) {
        currentTaskFragment.addTask(task, true)
        taskFragments.forEach { it.refreshTasks() }
    }

    fun onTaskEdited(task: ModelTask) {
        currentTaskFragment.saveEditedTask(task)
        doneTaskFragment.refreshTasks()
    }

    fun showExistingTaskDialog(): Boolean {
        val existing = supportFragmentManager.fragments.filterIsInstance<TaskDialog>()
            .firstOrNull { it.isAdded && !it.isRemoving } ?: return false
        if (!existing.refreshHiddenNotesVisibility()) authenticateHiddenNotes()
        return true
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
        if (requestCode == BackupActivity.REQUEST_CODE_OK && resultCode == BackupActivity.REQUEST_CODE_OK) {
            invalidateHiddenAuthentication()
            bindTaskFragments()
            setHiddenNotesUnlocked(false)
        }
        for (fragment in supportFragmentManager.fragments) {
            fragment.onActivityResult(requestCode, resultCode, data)
            when (resultCode) {
                BackupActivity.REQUEST_CODE_OK -> {
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
        doneTaskFragment =
            tabAdapter!!.getItem(TabAdapter.DONE_TASK_FRAGMENT_POSITION) as DoneTaskFragment
        bindTaskFragments()
        val fab = findViewById<View>(R.id.fab) as FloatingActionButton
        fab.setOnClickListener {
            showAddTaskDialog()
        }

    }

    private fun showAddTaskDialog(mode: TaskModes = TaskModes.NONE) {
        if (showExistingTaskDialog()) return
        addingTaskDialogFragment = AddingTaskDialogFragment.newInstance(mode)
        addingTaskDialogFragment!!.addListener(object : AddingTaskListener {
            override fun onTaskAdd(newTask: ModelTask) {
                this@TasksActivity.onTaskAdded(newTask)
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
        private const val SENSITIVE_WINDOW_SHOWN = "sensitive_window_shown"
        private const val SEARCHVIEW_APPEAR_DURATION = 200
        const val CURRENT_TASKS = "CURRENT_TASKS"
        const val DONE_TASKS = "DONE_TASKS"
    }
}