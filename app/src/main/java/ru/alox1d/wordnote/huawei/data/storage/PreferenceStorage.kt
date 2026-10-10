package ru.alox1d.wordnote.huawei.data.storage

import android.content.Context
import android.content.SharedPreferences

class PreferenceStorage private constructor() {

    companion object {
        const val SPLASH_IS_INVISIBLE = "splash_is_invisible"
        const val SN_SYNC_IS_ACTIVE = "SN_SYNC_IS_ACTIVE"
        const val DARK_IS_ENABLED = "DARK_IS_ENABLED"
        const val BACKUP_INITIAL_DATE = "BACKUP_DATE"
        const val BACKUP_INTERVAL = "BACKUP_INTERVAL"
        const val unionID = "unionID"
        const val at = "at"
        val INSTANCE: PreferenceStorage = PreferenceStorage()
    }

    private var context: Context? = null
    private var preferences: SharedPreferences? = null

    fun init(context: Context) {
        this.context = context
        preferences = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
    }

    fun putBoolean(key: String?, value: Boolean) {
        val editor = preferences!!.edit()
        editor.putBoolean(key, value)
        editor.apply()
    }

    fun getBoolean(key: String?): Boolean {
        return preferences!!.getBoolean(key, false)
    }

    fun getLong(key: String?): Long {
        return preferences!!.getLong(key, -1L)
    }

    fun putLong(key: String?, value: Long) {
        val editor = preferences!!.edit()
        editor.putLong(key, value)
        editor.apply()
    }

    fun putString(key: String?, value: String?) {
        val editor = preferences!!.edit()
        editor.putString(key, value)
        editor.apply()
    }

    fun getString(key: String?): String? {
        return preferences!!.getString(key, "")
    }

    val code: String?
        get() {
            val defaultValue = ""
            return preferences!!.getString("code", defaultValue)
        }

    fun saveCode(str: String?) {
        val editor = preferences!!.edit()
        editor.putString("code", str)
        editor.apply()
    }

}