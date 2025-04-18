package de.infix.testBalloon.framework.internal

import de.infix.testBalloon.framework.ConcurrentList
import de.infix.testBalloon.framework.assertContainsInOrder
import de.infix.testBalloon.framework.internal.integration.IntellijLogTestReport
import de.infix.testBalloon.framework.testSuite
import de.infix.testBalloon.framework.withTestFramework
import de.infix.testBalloon.framework.withTestReport
import kotlin.getValue
import kotlin.test.Test
import kotlin.test.fail

class IntellijLogTestReportTests {
    @Test
    fun output() = withTestFramework {
        val suite by testSuite("topSuite") {
            test("test1") {}

            test("test2") { fail("intentionally") }

            testSuite("subSuite1") {
                test("test1") {}
            }

            test("test3") {}

            testSuite("subSuite2") {
                test("test1") { fail("intentionally") }
            }
        }

        withTestReport(suite) {
            val output = ConcurrentList<String>()
            val report = IntellijLogTestReport { output.add(it) }
            allEvents().forEach { report.add(it) }

            val timeRegex = Regex("""(?<= (start|end))Time='\d+'""")
            val stackTraceRegex = Regex("""<stackTrace><[^<]*</stackTrace>""")

            @Suppress("SpellCheckingInspection")
            output.elements()
                .drop(2) // beforeSuite for session, compartment
                .dropLast(2) // afterSuite for compartment, session
                .map { it.replace(stackTraceRegex, "<stackTrace>...</stackTrace>").replace(timeRegex, "Time='...'") }
                .assertContainsInOrder(
                    listOf(
                        "<ijLog><event type='beforeSuite'><test id='topSuite' parentId='@Default'><descriptor name='topSuite' displayName='topSuite' className='topSuite'/></test></event></ijLog>",
                        "<ijLog><event type='beforeTest'><test id='topSuite.test1' parentId='topSuite'><descriptor name='topSuite.test1' displayName='test1' className='topSuite.test1'/></test></event></ijLog>",
                        "<ijLog><event type='afterTest'><test id='topSuite.test1' parentId='topSuite'><descriptor name='topSuite.test1' displayName='test1' className='topSuite.test1'/><result resultType='SUCCESS' startTime='...' endTime='...'></result></test></event></ijLog>",
                        "<ijLog><event type='beforeTest'><test id='topSuite.test2' parentId='topSuite'><descriptor name='topSuite.test2' displayName='test2' className='topSuite.test2'/></test></event></ijLog>",
                        "<ijLog><event type='afterTest'><test id='topSuite.test2' parentId='topSuite'><descriptor name='topSuite.test2' displayName='test2' className='topSuite.test2'/><result resultType='FAILURE' startTime='...' endTime='...'><errorMsg><![CDATA[aW50ZW50aW9uYWxseQ==]]></errorMsg><stackTrace>...</stackTrace><failureType>assertionFailed</failureType></result></test></event></ijLog>",
                        "<ijLog><event type='beforeSuite'><test id='topSuite.subSuite1' parentId='topSuite'><descriptor name='topSuite.subSuite1' displayName='subSuite1' className='topSuite.subSuite1'/></test></event></ijLog>",
                        "<ijLog><event type='beforeTest'><test id='topSuite.subSuite1.test1' parentId='topSuite.subSuite1'><descriptor name='topSuite.subSuite1.test1' displayName='test1' className='topSuite.subSuite1.test1'/></test></event></ijLog>",
                        "<ijLog><event type='afterTest'><test id='topSuite.subSuite1.test1' parentId='topSuite.subSuite1'><descriptor name='topSuite.subSuite1.test1' displayName='test1' className='topSuite.subSuite1.test1'/><result resultType='SUCCESS' startTime='...' endTime='...'></result></test></event></ijLog>",
                        "<ijLog><event type='afterSuite'><test id='topSuite.subSuite1' parentId='topSuite'><descriptor name='topSuite.subSuite1' displayName='subSuite1' className='topSuite.subSuite1'/><result resultType='SUCCESS' startTime='...' endTime='...'></result></test></event></ijLog>",
                        "<ijLog><event type='beforeTest'><test id='topSuite.test3' parentId='topSuite'><descriptor name='topSuite.test3' displayName='test3' className='topSuite.test3'/></test></event></ijLog>",
                        "<ijLog><event type='afterTest'><test id='topSuite.test3' parentId='topSuite'><descriptor name='topSuite.test3' displayName='test3' className='topSuite.test3'/><result resultType='SUCCESS' startTime='...' endTime='...'></result></test></event></ijLog>",
                        "<ijLog><event type='beforeSuite'><test id='topSuite.subSuite2' parentId='topSuite'><descriptor name='topSuite.subSuite2' displayName='subSuite2' className='topSuite.subSuite2'/></test></event></ijLog>",
                        "<ijLog><event type='beforeTest'><test id='topSuite.subSuite2.test1' parentId='topSuite.subSuite2'><descriptor name='topSuite.subSuite2.test1' displayName='test1' className='topSuite.subSuite2.test1'/></test></event></ijLog>",
                        "<ijLog><event type='afterTest'><test id='topSuite.subSuite2.test1' parentId='topSuite.subSuite2'><descriptor name='topSuite.subSuite2.test1' displayName='test1' className='topSuite.subSuite2.test1'/><result resultType='FAILURE' startTime='...' endTime='...'><errorMsg><![CDATA[aW50ZW50aW9uYWxseQ==]]></errorMsg><stackTrace>...</stackTrace><failureType>assertionFailed</failureType></result></test></event></ijLog>",
                        "<ijLog><event type='afterSuite'><test id='topSuite.subSuite2' parentId='topSuite'><descriptor name='topSuite.subSuite2' displayName='subSuite2' className='topSuite.subSuite2'/><result resultType='SUCCESS' startTime='...' endTime='...'></result></test></event></ijLog>",
                        "<ijLog><event type='afterSuite'><test id='topSuite' parentId='@Default'><descriptor name='topSuite' displayName='topSuite' className='topSuite'/><result resultType='SUCCESS' startTime='...' endTime='...'></result></test></event></ijLog>"
                    ),
                    exhaustive = true
                )
        }
    }
}
