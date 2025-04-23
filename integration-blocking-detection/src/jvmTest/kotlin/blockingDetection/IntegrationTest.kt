package blockingDetection

import de.infix.testBalloon.framework.TestConfig
import de.infix.testBalloon.framework.testSuite
import de.infix.testBalloon.integration.blockingDetection.BlockingDetection
import de.infix.testBalloon.integration.blockingDetection.blockingDetection
import de.infix.testBalloon.integration.blockingDetection.withBlockingDetection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import reactor.blockhound.BlockingOperationError
import kotlin.test.assertFailsWith

val IntegrationTest by testSuite {
    test("detecting", configuration = TestConfig.blockingDetection()) {
        assertFailsWith<BlockingOperationError> { blockInNonBlockingContext() }
    }

    test("disabling", configuration = TestConfig.blockingDetection()) {
        assertFailsWith<BlockingOperationError> { blockInNonBlockingContext() }
        withBlockingDetection(BlockingDetection.DISABLED) {
            blockInNonBlockingContext()
        }
    }

    test("detecting printing", configuration = TestConfig.blockingDetection(BlockingDetection.PRINT)) {
        blockInNonBlockingContext()
    }

    test("not enabled") {
        blockInNonBlockingContext()
    }

    test("does not complain on Dispatchers.IO") {
        withContext(Dispatchers.IO) {
            Thread.sleep(2)
        }
    }
}

private suspend fun blockInNonBlockingContext() {
    // Provokes a blocking situation which will be detected if
    // a) BlockHound has been successfully activated, and
    // b) the required 'kotlinx-coroutines-debug' dependency is present.
    withContext(Dispatchers.Default) {
        // Use a non-blocking dispatcher
        @Suppress("BlockingMethodInNonBlockingContext")
        Thread.sleep(2)
    }
}
