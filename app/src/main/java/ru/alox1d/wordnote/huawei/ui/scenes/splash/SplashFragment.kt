package ru.alox1d.wordnote.huawei.ui.scenes.splash

import android.os.AsyncTask
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import ru.alox1d.wordnote.huawei.R
import java.util.concurrent.TimeUnit

/**
 * A simple [Fragment] subclass.
 */
class SplashFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        val splashTask = SplashTask()
        splashTask.execute()
        return inflater.inflate(R.layout.fragment_splash, container, false)
    }

    inner class SplashTask : AsyncTask<Void?, Void?, Void?>() {
        override fun doInBackground(vararg params: Void?): Void? {
            try {
                TimeUnit.SECONDS.sleep(2)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
            if (activity != null) {
                activity!!.supportFragmentManager.popBackStack()
            }
            return null
        }
    }
}