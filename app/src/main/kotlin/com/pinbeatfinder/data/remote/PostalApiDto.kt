package com.pinbeatfinder.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire format of api.postalpincode.in. Both endpoints return a JSON array with a single
 * envelope; `PostOffice` is `null` when nothing matched (`Status == "Error"`).
 */
@Serializable
data class PostalApiEnvelope(
    @SerialName("Message") val message: String? = null,
    @SerialName("Status") val status: String? = null,
    @SerialName("PostOffice") val postOffice: List<PostOfficeDto>? = null,
) {
    val isSuccess: Boolean get() = status.equals("Success", ignoreCase = true)
}

@Serializable
data class PostOfficeDto(
    @SerialName("Name") val name: String? = null,
    @SerialName("Description") val description: String? = null,
    @SerialName("BranchType") val branchType: String? = null,
    @SerialName("DeliveryStatus") val deliveryStatus: String? = null,
    @SerialName("Circle") val circle: String? = null,
    @SerialName("District") val district: String? = null,
    @SerialName("Division") val division: String? = null,
    @SerialName("Region") val region: String? = null,
    @SerialName("Block") val block: String? = null,
    @SerialName("State") val state: String? = null,
    @SerialName("Country") val country: String? = null,
    @SerialName("Pincode") val pincode: String? = null,
)
