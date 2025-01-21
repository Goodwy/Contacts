package com.goodwy.contacts

import com.goodwy.commons.RightApp
import com.goodwy.commons.extensions.isRuStoreInstalled
import com.goodwy.commons.helpers.rustore.RuStoreModule

class App : RightApp() {

    override fun onCreate() {
        super.onCreate()
        if (isRuStoreInstalled()) RuStoreModule.install(this, "685363647") //TODO rustore
    }
}
