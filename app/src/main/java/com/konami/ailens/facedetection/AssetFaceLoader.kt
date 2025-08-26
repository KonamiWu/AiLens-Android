package com.konami.ailens.facedetection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import org.json.JSONObject
import java.nio.charset.Charset

object AssetFaceLoader {
    var bitmap: Bitmap? = null

    /** 從 assets/faces 讀圖 → embed → 存 repo */
//    fun loadToRepository(context: Context, repo: FaceRepository, recognizer: OnnxFaceRecognizer) {
//        val am = context.assets
//        val dir = "faces"
//        val files = am.list(dir)?.filter { it.endsWith(".jpg", true) || it.endsWith(".png", true) }.orEmpty()
//        if (files.isEmpty()) {
//            Log.e("AssetFaceLoader", "No files under assets/$dir")
//            return
//        }
//        Log.e("AssetFaceLoader", "Loading ${files.size} faces from assets/$dir")
//
//        files.forEach { filename ->
//            try {
//                // 直接 decode bitmap，不做旋轉 / resize
//                val inputStream = am.open("$dir/$filename")
//                val src = BitmapFactory.decodeStream(inputStream)
//                inputStream.close()
//                if (src == null) {
//                    Log.e("AssetFaceLoader", "Failed to decode $filename")
//                    return@forEach
//                }
//
//                val name = filename.substringBefore('_').substringBeforeLast('.') // konami_1.jpg → konami
//
//                bitmap = src
//
//                val emb = recognizer.embed(src)
//                repo.add(Person(name, emb))
//
//                Log.e("AssetFaceLoader", "Enrolled $name (${emb.size}d) from $filename")
//            } catch (t: Throwable) {
//                Log.e("AssetFaceLoader", "Failed on $filename", t)
//            }
//        }
//    }

    fun loadEmbeddingsFromAssets(context: Context, repo: FaceRepository) {
        val am = context.assets
        am.open("emb/people_avg.json").use { input ->
            val json = input.readBytes().toString(Charset.forName("UTF-8"))
            val obj = JSONObject(json)
            val names = obj.keys()
            while (names.hasNext()) {
                val name = names.next()
                val info = obj.getJSONObject(name)
                val arr = info.getJSONArray("embedding")
                val emb = FloatArray(arr.length()) { i -> arr.getDouble(i).toFloat() }
                repo.add(Person(name, emb))
            }
        }
    }
}
