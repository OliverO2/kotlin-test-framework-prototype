package testFramework.internal.integration

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import testFramework.Test
import testFramework.internal.TestEvent
import testFramework.internal.TestReport
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

internal object IntellijTestLog : TestReport() {
    private val outputMutex = Mutex()

    override suspend fun add(event: TestEvent) {
        val parentElement = event.element.parentSuite

        // Apparently, `className` must be unique, even across platform targets. Otherwise, IntelliJ's "run test"
        // window will mix tests for different targets under common `className` hierarchy nodes.
        // Therefore, we cannot use `event.element::class.simpleName` here.
        // Unfortunately, if IntelliJ is not given a correct fully qualified class name, it does not offer to run a
        // single test class via its run window.
        val className = event.element.elementPath

        suspend fun addBeforeEvent() {
            ijLog {
                event(type = if (event.element is Test) "beforeTest" else "beforeSuite") {
                    test(id = event.element.elementPath, parentId = parentElement?.elementPath) {
                        descriptor(
                            name = event.element.elementPath,
                            displayName = event.element.displayName,
                            className = className
                        )
                    }
                }
            }
        }

        suspend fun addAfterEvent(
            resultType: String,
            startingEvent: TestEvent = event,
            content: IjLog.Event.Test.Result.() -> Unit = {}
        ) {
            ijLog {
                event(type = if (event.element is Test) "afterTest" else "afterSuite") {
                    test(id = event.element.elementPath, parentId = parentElement?.elementPath) {
                        descriptor(
                            name = event.element.elementPath,
                            displayName = event.element.displayName,
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
            is TestEvent.Starting -> {
                addBeforeEvent()
            }

            is TestEvent.Finished -> {
                val resultType = when {
                    !event.element.isEnabled -> "SKIPPED"
                    event.throwable == null -> "SUCCESS"
                    else -> "FAILURE"
                }
                addAfterEvent(resultType = resultType, startingEvent = event.startingEvent) {
                    event.throwable?.let { throwable ->
                        throwable.message?.let { errorMsg(it) }
                        stackTrace(throwable.stackTraceToString())
                        failureType(if (throwable is AssertionError) "assertionFailed" else "error")
                    }
                }
            }
        }
    }

    private suspend fun ijLog(content: IjLog.() -> Unit) {
        val entry = StringBuilder()
        IjLog(entry).content()
        outputMutex.withLock {
            println("<ijLog>$entry</ijLog>")
        }
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
                    content: Result.() -> Unit = {}
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

@OptIn(ExperimentalEncodingApi::class)
private fun String.asCdata(): String = "<![CDATA[${Base64.encode(encodeToByteArray())}]]>"
