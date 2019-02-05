/*
 * Copyright (c) 2019 Naman Dwivedi.
 *
 * Licensed under the GNU General Public License v3
 *
 * This is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 */
@file:Suppress("unused")

package com.naman14.timberx

import android.app.Application
import com.naman14.timberx.BuildConfig.DEBUG
import com.naman14.timberx.network.DataHandler
import com.naman14.timberx.notifications.notificationModule
import com.naman14.timberx.util.logging.FabricTree
import org.koin.android.ext.android.startKoin
import timber.log.Timber

class TimberXApp : Application() {

    override fun onCreate() {
        super.onCreate()
        DataHandler.initCache(this)

        if (DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        Timber.plant(FabricTree())

        val modules = listOf(
                notificationModule
        )
        startKoin(
                androidContext = this,
                modules = modules
        )
    }
}
