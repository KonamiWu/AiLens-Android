package com.konami.ailens.facedetection

import org.opencv.android.OpenCVLoader

object OpenCVLoader {
    fun initLocal(): Boolean {
        return OpenCVLoader.initLocal()
    }
}