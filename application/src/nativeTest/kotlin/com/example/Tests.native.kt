package com.example

actual suspend fun log(message: String) {
    // println("Thread[${pthread_self()}] $message")
    // println(message)
}
