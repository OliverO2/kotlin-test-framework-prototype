package com.example

actual suspend fun log(message: String) {
    println(message)
}
