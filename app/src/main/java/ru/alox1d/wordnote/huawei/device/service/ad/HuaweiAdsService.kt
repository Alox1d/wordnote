package ru.alox1d.wordnote.huawei.device.service.ad

import android.app.Activity
import android.view.View
import com.huawei.hms.ads.AdParam
import com.huawei.hms.ads.HwAds
import com.huawei.hms.ads.banner.BannerView
import ru.alox1d.wordnote.huawei.R
import toothpick.InjectConstructor

@InjectConstructor
class HuaweiAdsService : IAdsService {
    override fun showBanner(activity: Activity, bannerId: Int) {
        val banner: BannerView = activity.findViewById(bannerId)
        val unitId = activity.getString(R.string.banner_ad_unit_id)
        if (unitId.isBlank()) {
            banner.visibility = View.GONE
            return
        }
        HwAds.init(activity)
        banner.adId = unitId
        banner.loadAd(AdParam.Builder().build())
    }
}
