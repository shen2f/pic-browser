package com.example.picbrowser.util

import kotlin.random.Random

fun <T> List<T>.shuffledRandom(): List<T> {
    val list = toMutableList()
    val random = Random.Default
    for (i in list.size downTo 2) {
        val j = random.nextInt(i)
        val temp = list[i - 1]
        list[i - 1] = list[j]
        list[j] = temp
    }
    return list
}