package com.example

actual suspend fun log(message: String) {
    // println("${Thread.currentThread()} $message")
    // println(message)
}
