package com.example.camera

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class TestData(val pair: Pair<Int, Int>)

fun main() {
    println(Json.encodeToString(TestData.serializer(), TestData(Pair(1, 2))))
}
