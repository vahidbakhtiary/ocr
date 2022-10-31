package com.gradlevv.ocr

import android.content.Context
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject


interface ResourceModelFactory {

    fun loadFindFourFile(): MappedByteBuffer
    fun loadRecognizeDigitsFile(): MappedByteBuffer

}

class ResourceModelFactoryImpl @Inject constructor(private val context: Context) :
    ResourceModelFactory {

    override fun loadFindFourFile(): MappedByteBuffer {
        return loadModelFromResource(R.raw.findfour)

    }

    override fun loadRecognizeDigitsFile(): MappedByteBuffer {
        return loadModelFromResource(R.raw.fourrecognize)
    }

    private fun loadModelFromResource(resource: Int): MappedByteBuffer {
        val fileDescriptor = context.resources.openRawResourceFd(resource)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        val result = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        inputStream.close()
        fileDescriptor.close()
        return result
    }
}
