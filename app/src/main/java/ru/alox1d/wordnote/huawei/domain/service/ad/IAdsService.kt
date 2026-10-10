package ru.alox1d.wordnote.huawei.device.service.ad

import android.app.Activity


interface IAdsService {
    fun showBanner(activity: Activity, bannerId: Int)
}