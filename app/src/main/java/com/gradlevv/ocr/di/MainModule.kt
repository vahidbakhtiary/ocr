package com.gradlevv.ocr.di

import android.app.Application
import android.content.Context
import dagger.Module
import dagger.Provides

@Module
object MainModule {

    @Provides
    fun provideContext(application: Application): Context = application
}