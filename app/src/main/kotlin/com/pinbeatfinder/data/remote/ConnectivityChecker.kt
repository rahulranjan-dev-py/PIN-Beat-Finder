package com.pinbeatfinder.data.remote

/** Answers "is there a usable network right now?" — used for cache policy and error mapping. */
fun interface ConnectivityChecker {
    fun isOnline(): Boolean
}
