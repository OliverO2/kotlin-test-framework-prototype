package testFramework.internal.integration

import testFramework.Test
import testFramework.TestModule
import testFramework.TestScope
import testFramework.internal.integration.IntellijTestLog.IjLog.Event

internal object IntellijTestLog {
    fun add(event: TestScope.Invocation.Event) {
        if (event.scope is TestModule.Root) return // ignore the root node

        val parentScope = event.scope.parent?.let { if (it.parent == null) null else it } // null if root is parent
        val className = event.scope::class.simpleName

        fun addBeforeEvent() {
            ijLog {
                event(type = if (event.scope is Test) "beforeTest" else "beforeSuite") {
                    test(id = event.scope.scopeName, parentId = parentScope?.scopeName) {
                        descriptor(
                            name = event.scope.scopeName,
                            displayName = event.scope.simpleScopeName,
                            className = className
                        )
                    }
                }
            }
        }

        fun addAfterEvent(
            resultType: String,
            startingEvent: TestScope.Invocation.Event = event,
            block: Event.Test.Result.() -> Unit = {
            }
        ) {
            ijLog {
                event(type = if (event.scope is Test) "afterTest" else "afterSuite") {
                    test(id = event.scope.scopeName, parentId = parentScope?.scopeName) {
                        descriptor(
                            name = event.scope.scopeName,
                            displayName = event.scope.simpleScopeName,
                            className = className
                        )
                        result(
                            resultType = resultType,
                            startTime = startingEvent.instant.toEpochMilliseconds(),
                            endTime = event.instant.toEpochMilliseconds(),
                            block = block
                        )
                    }
                }
            }
        }

        when (event) {
            is TestScope.Invocation.Event.Starting -> {
                addBeforeEvent()
            }

            is TestScope.Invocation.Event.Finished -> {
                val resultType = if (event.throwable == null) "SUCCESS" else "FAILURE"
                addAfterEvent(resultType = resultType, startingEvent = event.startingEvent) {
                    event.throwable?.let { throwable ->
                        throwable.message?.let { errorMsg(it) }
                        stackTrace(throwable.stackTraceToString())
                        failureType(if (throwable is AssertionError) "assertionFailed" else "error")
                    }
                }
            }

            is TestScope.Invocation.Event.Skipped -> {
                addBeforeEvent()
                addAfterEvent(resultType = "SKIPPED")
            }
        }
    }

    private fun ijLog(block: IjLog.() -> Unit) {
        val entry = StringBuilder()
        IjLog(entry).block()
        println("<ijLog>$entry</ijLog>")
    }

    private class IjLog(private val entry: StringBuilder) {
        // For the IntelliJ test event logging format, see:
        // https://github.com/JetBrains/intellij-community/blob/41cf54c6d3037b65733ae6c233af192ac7b723d0/plugins/gradle/tooling-extension-impl/src/org/jetbrains/plugins/gradle/tooling/internal/init/TestEventLogger.gradle
        // https://github.com/Kotlin/kotlinx-benchmark/blob/3b88587ade401b2016d1835b12c6470654d0d2a7/runtime/commonMain/src/kotlinx/benchmark/IntelliJLog.kt

        fun event(type: String, block: Event.() -> Unit) {
            entry.append("<event type='$type'>")
            Event().block()
            entry.append("</event>")
        }

        inner class Event {
            fun test(id: String, parentId: String?, block: Test.() -> Unit) {
                entry.append("<test id='$id' parentId='${parentId ?: ""}'>")
                Test().block()
                entry.append("</test>")
            }

            inner class Test {
                fun descriptor(name: String, displayName: String, className: String?) {
                    entry.append("<descriptor name='$name' displayName='$displayName' className='${className ?: ""}'/>")
                }

                fun result(
                    resultType: String,
                    startTime: Long? = null,
                    endTime: Long? = null,
                    block: Result.() -> Unit = {
                    }
                ) {
                    entry.append(
                        "<result resultType='$resultType' startTime='${startTime?.toString() ?: ""}'" +
                            " endTime='${endTime?.toString() ?: ""}'>"
                    )
                    Result().block()
                    entry.append("</result>")
                }

                inner class Result {
                    fun errorMsg(content: String) {
                        entry.append("<errorMsg>${content.cData()}</errorMsg>")
                    }

                    fun stackTrace(content: String) {
                        entry.append("<stackTrace>${content.cData()}</stackTrace>")
                    }

                    fun failureType(type: String) {
                        entry.append("<failureType>$type</failureType>")
                    }
                }
            }
        }
    }
}

private fun String.cData(): String = "<![CDATA[${encodeToByteArray().encodeBase64()}]]>"

// region Code from kotlinx-benchmark/runtime/commonMain/src/kotlinx/benchmark/IntelliJLog.kt
private const val BASE64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
private const val BASE64_MASK: Byte = 0x3f
private const val BASE64_PAD = '='

private fun ByteArray.encodeBase64(): String {
    fun ByteArray.getOrZero(index: Int): Int = if (index >= size) 0 else get(index).toInt() and 0xFF
    fun Int.toBase64(): Char = BASE64_ALPHABET[this]

    val result = ArrayList<Char>(4 * size / 3)
    var index = 0
    while (index < size) {
        val symbolsLeft = size - index
        val padSize = if (symbolsLeft >= 3) 0 else (3 - symbolsLeft) * 8 / 6
        val chunk = (getOrZero(index) shl 16) or (getOrZero(index + 1) shl 8) or getOrZero(index + 2)
        index += 3

        for (i in 3 downTo padSize) {
            val char = (chunk shr (6 * i)) and BASE64_MASK.toInt()
            result.add(char.toBase64())
        }

        repeat(padSize) { result.add(BASE64_PAD) }
    }

    return result.toCharArray().concatToString()
}
// endregion
