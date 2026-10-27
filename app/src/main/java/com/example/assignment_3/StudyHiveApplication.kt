package com.example.assignment_3

import android.app.Application
import com.example.assignment_3.di.AppContainer
import com.example.assignment_3.di.DefaultAppContainer

class StudyHiveApplication : Application() {

    /** Instrumented tests replace this with a container backed by an in-memory database. */
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
        container.notificationHelper.createChannels()
    }
}
