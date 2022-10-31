package com.gradlevv.ocr.di

import com.gradlevv.ocr.ResourceModelFactory
import com.gradlevv.ocr.ResourceModelFactoryImpl
import dagger.Binds
import dagger.Module


@Module
abstract class BinderModule {


    @Binds
    abstract fun bindResourceModelFactory(resourceModel: ResourceModelFactoryImpl): ResourceModelFactory

}