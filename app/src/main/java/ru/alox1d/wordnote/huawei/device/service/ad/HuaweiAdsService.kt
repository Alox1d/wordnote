package ru.alox1d.wordnote.huawei.device.service.ad

import android.app.Activity
import com.huawei.hms.ads.AdParam
import com.huawei.hms.ads.HwAds
import com.huawei.hms.ads.banner.BannerView
import toothpick.InjectConstructor

@InjectConstructor
class HuaweiAdsService : IAdsService {

        override fun showBanner(activity: Activity, bannerId: Int) {
            HwAds.init(activity);

            // Obtain BannerView based on the configuration in layout/ad_fragment.xml.
            val bannerView: BannerView = activity.findViewById(bannerId)
            val adParam = AdParam.Builder().build()
            bannerView.loadAd(adParam)
        }
}