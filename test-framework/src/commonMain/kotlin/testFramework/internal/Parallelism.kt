package testFramework.internal

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal suspend fun <Target> Collection<Target>.forEachWithParallelism(
    parallelism: Int,
    action: suspend (Target) -> Unit
) {
    if (parallelism == 1) {
        forEach { target ->
            action(target)
        }
    } else {
        chunked(parallelism).forEach { parallelTargets ->
            withContext(dispatcherWithParallelism(parallelTargets.size)) {
                coroutineScope {
                    parallelTargets.forEach { target ->
                        launch {
                            action(target)
                        }
                    }
                }
            }
        }
    }
}

internal expect fun dispatcherWithParallelism(parallelism: Int): CoroutineDispatcher

internal expect val platformParallelism: Int
