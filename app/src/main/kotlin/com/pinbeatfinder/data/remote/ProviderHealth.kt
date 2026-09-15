package com.pinbeatfinder.data.remote

/** Last observed outcome for one provider, shown in Settings → Data sources. */
data class ProviderHealth(
    val id: String,
    val label: String,
    val status: Status = Status.UNKNOWN,
    val latencyMs: Long? = null,
    val checkedAt: Long? = null,
    val detail: String? = null,
) {
    enum class Status { UNKNOWN, OK, FAILED }
}
