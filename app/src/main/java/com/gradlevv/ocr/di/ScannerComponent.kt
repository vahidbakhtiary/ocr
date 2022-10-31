package com.gradlevv.ocr.di

import android.app.Application
import android.content.Context
import com.gradlevv.ocr.MainActivity
import dagger.BindsInstance
import dagger.Component

@Component(
    modules = [BinderModule::class,MainModule::class]
)

interface ScannerComponent {

    fun context(): Context

    fun inject(activity: MainActivity)

    @Component.Builder
    interface Builder {
        fun create(): ScannerComponent

        fun application(@BindsInstance application: Application): Builder

    }
}