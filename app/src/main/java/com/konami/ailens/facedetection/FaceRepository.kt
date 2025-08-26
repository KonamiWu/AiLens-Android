package com.konami.ailens.facedetection

import android.util.Log

data class Person(val name: String, val emb: FloatArray)

class FaceRepository {
    private val people = mutableListOf<Person>()

    fun add(person: Person) { people.add(person) }

    /** 回傳最佳人名與分數；若列表空或分數太低，回傳 null */
    fun findBest(query: FloatArray, minScore: Float = 0.6f): String? {
        var bestName = ""
        var best = -1f
        for (p in people) {
            val score = OnnxFaceRecognizer.Companion.cosine(query, p.emb)
            if (score > best) {
                best = score
                bestName = p.name
            }
        }
        return if (best >= minScore) bestName else null
    }
}
