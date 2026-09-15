package com.pinbeatfinder.data.remote

import com.pinbeatfinder.domain.model.PostOffice

/** An on-device directory consulted before any network provider. */
interface LocalDirectorySource {
    val id: String
    val label: String

    /** False while the directory is still being prepared (first launch) or failed to load. */
    suspend fun isReady(): Boolean

    /** Empty list means "not in this snapshot"; the caller then falls back to the network. */
    suspend fun search(query: String, isPincode: Boolean): List<PostOffice>
}
