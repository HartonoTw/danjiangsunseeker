package studio.freestyle.labs.danjiangsunseeker.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi

object AppDispatchers {
    @OptIn(ExperimentalCoroutinesApi::class)
    val HeavyComputation = Dispatchers.Default.limitedParallelism(1)
}
