package com.goodwy.contacts

import android.app.Application
import com.anjlab.android.iab.v3.BillingProcessor
import com.anjlab.android.iab.v3.PurchaseInfo
import com.goodwy.commons.extensions.checkUseEnglish
import com.goodwy.commons.extensions.isPackageInstalled
import com.goodwy.commons.extensions.toast

class App : Application() {

    lateinit var billingProcessor: BillingProcessor

    override fun onCreate() {
        super.onCreate()
        instance = this
        checkUseEnglish()

        // automatically restores purchases
        billingProcessor = BillingProcessor(
            this, BuildConfig.GOOGLE_PLAY_LICENSING_KEY,
            object : BillingProcessor.IBillingHandler {
                override fun onProductPurchased(productId: String, details: PurchaseInfo?) {}

                override fun onPurchaseHistoryRestored() {
                    toast(R.string.restored_previous_purchase_please_restart)
                }

                override fun onBillingError(errorCode: Int, error: Throwable?) {}

                override fun onBillingInitialized() {}
            })
    }

    override fun onTerminate() {
        super.onTerminate()
        billingProcessor.release()
    }

    companion object {
        private var instance: App? = null

        fun isPlayStoreInstalled(): Boolean {
            return instance!!.isPackageInstalled("com.android.vending")
                || instance!!.isPackageInstalled("com.google.market")
        }

        fun isProVersion(): Boolean {
            return if (isPlayStoreInstalled()) {
                (instance!!.billingProcessor.isPurchased(BuildConfig.PRODUCT_ID_X1)
                    || instance!!.billingProcessor.isPurchased(BuildConfig.PRODUCT_ID_X2)
                    || instance!!.billingProcessor.isPurchased(BuildConfig.PRODUCT_ID_X3))
            } else
                false
        }
    }
}
