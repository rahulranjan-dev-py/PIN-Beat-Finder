package com.pinbeatfinder.domain.model

/** A post office as returned by the All-India online lookup. */
data class PostOffice(
    val name: String,
    val branchType: String,
    val deliveryStatus: String,
    val circle: String,
    val division: String,
    val region: String,
    val block: String,
    val district: String,
    val state: String,
    val pincode: String,
    /** Human-readable name of the provider that answered, shown in the UI. */
    val source: String = "",
    /** True when the answer came from the on-device HTTP cache rather than the network. */
    val fromCache: Boolean = false,
)
