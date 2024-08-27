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
            content: Event.Test.Result.() -> Unit = {
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
                            content = content
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

    private fun ijLog(content: IjLog.() -> Unit) {
        val entry = StringBuilder()
        IjLog(entry).content()
        println("<ijLog>$entry</ijLog>")
    }

    private class IjLog(private val entry: StringBuilder) {
        // For the IntelliJ test event logging format, see:
        // https://github.com/JetBrains/intellij-community/blob/41cf54c6d3037b65733ae6c233af192ac7b723d0/plugins/gradle/tooling-extension-impl/src/org/jetbrains/plugins/gradle/tooling/internal/init/TestEventLogger.gradle
        // https://github.com/Kotlin/kotlinx-benchmark/blob/3b88587ade401b2016d1835b12c6470654d0d2a7/runtime/commonMain/src/kotlinx/benchmark/IntelliJLog.kt

        fun event(type: String, content: Event.() -> Unit) {
            entry.append("<event type='$type'>")
            Event().content()
            entry.append("</event>")
        }

        inner class Event {
            fun test(id: String, parentId: String?, content: Test.() -> Unit) {
                entry.append("<test id='${id.asAttributeValue()}' parentId='${parentId?.asAttributeValue() ?: ""}'>")
                Test().content()
                entry.append("</test>")
            }

            inner class Test {
                fun descriptor(name: String, displayName: String, className: String?) {
                    entry.append(
                        "<descriptor" +
                            " name='${name.asAttributeValue()}'" +
                            " displayName='${displayName.asAttributeValue()}'" +
                            " className='${className?.asAttributeValue() ?: ""}'/>"
                    )
                }

                fun result(
                    resultType: String,
                    startTime: Long? = null,
                    endTime: Long? = null,
                    content: Result.() -> Unit = {
                    }
                ) {
                    entry.append(
                        "<result resultType='$resultType' startTime='${startTime?.toString() ?: ""}'" +
                            " endTime='${endTime?.toString() ?: ""}'>"
                    )
                    Result().content()
                    entry.append("</result>")
                }

                inner class Result {
                    fun errorMsg(content: String) {
                        entry.append("<errorMsg>${content.asCdata()}</errorMsg>")
                    }

                    fun stackTrace(content: String) {
                        entry.append("<stackTrace>${content.asCdata()}</stackTrace>")
                    }

                    fun failureType(type: String) {
                        entry.append("<failureType>$type</failureType>")
                    }
                }
            }
        }
    }
}

/**
 * Returns the XML attribute value encoding for this character sequence.
 *
 * The encoding does not contain quotes, and is safe for enclosing it in single and double quotes.
 */
@OptIn(ExperimentalStdlibApi::class)
private fun CharSequence.asAttributeValue() = buildString {
    // Reference: https://www.w3.org/TR/2006/REC-xml11-20060816/#NT-AttValue
    for (c in this@asAttributeValue) {
        when (c) {
            '<' -> append("&lt;")
            '&' -> append("&amp;")
            '>' -> append("&gt;")
            '\'' -> append("&apos;")
            '"' -> append("&quot;")
            in XML_ATTRIBUTE_VALUE_CHAR_SET -> append(c)
            else -> append("&#${c.code};")
        }
    }
}

private val XML_ATTRIBUTE_VALUE_CHAR_SET =
    (
        listOf('a'..'z', 'A'..'Z', '0'..'9')
            .map { it.toSet() }
            .reduce { accumulation, component -> accumulation + component }
        ) + " -()+,./:=?;!*#@${'$'}_%".toSet()

private fun String.asCdata(): String = "<![CDATA[${encodeToByteArray().asBase64()}]]>"

// region Copyrighted code used with permission
// Original: https://github.com/Kotlin/kotlinx-benchmark/blob/3b88587ade401b2016d1835b12c6470654d0d2a7/runtime/commonMain/src/kotlinx/benchmark/IntelliJLog.kt
// License: https://www.apache.org/licenses/LICENSE-2.0
// Modified: Yes
private fun ByteArray.asBase64(): String {
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

private const val BASE64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
private const val BASE64_MASK: Byte = 0x3f
private const val BASE64_PAD = '='
// endregion
