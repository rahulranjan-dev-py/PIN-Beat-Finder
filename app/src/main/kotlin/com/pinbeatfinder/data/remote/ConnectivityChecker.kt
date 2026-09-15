package com.pinbeatfinder.data.remote

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Answers "is there a usable network right now?" — used for cache policy and error mapping. */
fun interface ConnectivityChecker {
    fun isOnline(): Boolean

    /** Emits the current state and every change. Default: a single snapshot, for non-Android callers. */
    fun observe(): Flow<Boolean> = flowOf(isOnline())
}
