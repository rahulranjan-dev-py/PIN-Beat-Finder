package com.pinbeatfinder.data.remote

import com.pinbeatfinder.domain.model.PostOffice
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Thrown by a mapper when the provider answered but the payload says "nothing found". */
class ProviderNoResultsException(message: String) : Exception(message)

/** Thrown by a mapper when the payload is not in the expected shape (HTML error page, schema change…). */
class ProviderFormatException(message: String) : Exception(message)

/**
 * One upstream source of post-office data. [fetch] returns the raw JSON; [map] turns it into
 * domain objects. Keeping the two apart lets the mappers be unit-tested on the JVM with canned
 * payloads while the fetchers stay thin Retrofit calls.
 */
class PostalProvider(
    val id: String,
    val label: String,
    val supportsNameSearch: Boolean,
    val fetch: suspend (query: String, isPincode: Boolean) -> JsonElement,
    /** Receives the raw payload and the original query (some providers omit the PIN in results). */
    val map: (payload: JsonElement, query: String) -> List<PostOffice>,
)

/**
 * Lenient JSON mappers for every provider. Upstream key casing and naming drift over time
 * (`officename`, `OfficeName`, `officeName`…), so every lookup is case-insensitive and tries a
 * list of aliases.
 */
object PostalProviders {
    const val ID_DATA_GOV_IN = "data.gov.in"
    const val ID_GITHUB_MIRROR = "github-mirror"
    const val ID_POSTALPINCODE_IN = "postalpincode.in"

    const val LABEL_DATA_GOV_IN = "data.gov.in (Department of Posts)"
    const val LABEL_GITHUB_MIRROR = "India Post directory mirror"
    const val LABEL_POSTALPINCODE_IN = "postalpincode.in"

    // ------------------------------------------------------------------ data.gov.in

    /**
     * `{ "status": "ok", "total": n, "count": n, "records": [ {circlename, regionname, divisionname,
     * officename, pincode, officetype, delivery, district, statename, ...} ] }`
     */
    fun mapDataGovIn(root: JsonElement): List<PostOffice> {
        val obj = root as? JsonObject ?: throw ProviderFormatException("data.gov.in: expected a JSON object")
        obj.str("message")?.takeIf { it.isNotBlank() && obj["records"] == null }?.let {
            throw ProviderFormatException("data.gov.in: $it")
        }
        val records = obj["records"] as? JsonArray ?: throw ProviderFormatException("data.gov.in: no records array")
        val offices = records.mapNotNull { it as? JsonObject }.map { r ->
            PostOffice(
                name = r.str("officename", "office_name", "name").orEmpty(),
                branchType = normaliseOfficeType(r.str("officetype", "office_type", "branchtype")),
                deliveryStatus = normaliseDelivery(r.str("delivery", "deliverystatus", "delivery_status")),
                circle = r.str("circlename", "circle_name", "circle").orEmpty(),
                division = r.str("divisionname", "division_name", "division").orEmpty(),
                region = r.str("regionname", "region_name", "region").orEmpty(),
                block = r.str("taluk", "block").orEmpty(),
                district = r.str("district", "districtname", "district_name").orEmpty(),
                state = r.str("statename", "state_name", "state").orEmpty(),
                pincode = r.str("pincode", "pin").orEmpty(),
                source = LABEL_DATA_GOV_IN,
            )
        }.filter { it.name.isNotBlank() }
        if (offices.isEmpty()) throw ProviderNoResultsException("No records found on data.gov.in")
        return offices
    }

    // ------------------------------------------------------------------ GitHub mirror

    /**
     * `{ "state": "…", "district": "…", "offices": [ {officeName, officeType, deliveryStatus,
     * circleName, regionName, divisionName, latitude, longitude, pincode?} ] }`
     */
    fun mapGithubMirror(root: JsonElement, requestedPincode: String): List<PostOffice> {
        val obj = root as? JsonObject ?: throw ProviderFormatException("mirror: expected a JSON object")
        val offices = obj["offices"] as? JsonArray ?: throw ProviderFormatException("mirror: no offices array")
        val state = obj.str("state", "stateName").orEmpty()
        val district = obj.str("district", "districtName").orEmpty()
        val mapped = offices.mapNotNull { it as? JsonObject }.map { o ->
            PostOffice(
                name = o.str("officeName", "officename", "name").orEmpty(),
                branchType = normaliseOfficeType(o.str("officeType", "officetype")),
                deliveryStatus = normaliseDelivery(o.str("deliveryStatus", "delivery")),
                circle = o.str("circleName", "circlename").orEmpty(),
                division = o.str("divisionName", "divisionname").orEmpty(),
                region = o.str("regionName", "regionname").orEmpty(),
                block = o.str("taluk", "block").orEmpty(),
                district = o.str("district", "districtName") ?: district,
                state = o.str("state", "stateName") ?: state,
                pincode = o.str("pincode") ?: requestedPincode,
                source = LABEL_GITHUB_MIRROR,
            )
        }.filter { it.name.isNotBlank() }
        if (mapped.isEmpty()) throw ProviderNoResultsException("No offices found in the directory mirror")
        return mapped
    }

    // ------------------------------------------------------------------ postalpincode.in

    /**
     * `[ { "Message": "…", "Status": "Success" | "Error", "PostOffice": [ {Name, BranchType,
     * DeliveryStatus, Circle, District, Division, Region, Block, State, Pincode} ] | null } ]`
     */
    fun mapPostalPincodeIn(root: JsonElement): List<PostOffice> {
        val envelope: JsonObject = when (root) {
            is JsonArray -> root.firstOrNull() as? JsonObject
            is JsonObject -> root
            else -> null
        } ?: throw ProviderFormatException("postalpincode.in: unexpected payload")

        val status = envelope.str("Status").orEmpty()
        val offices = envelope["PostOffice"] as? JsonArray
        if (!status.equals("Success", ignoreCase = true) || offices.isNullOrEmpty()) {
            throw ProviderNoResultsException(envelope.str("Message") ?: "No records found")
        }
        return offices.mapNotNull { it as? JsonObject }.map { o ->
            PostOffice(
                name = o.str("Name").orEmpty(),
                branchType = normaliseOfficeType(o.str("BranchType")),
                deliveryStatus = normaliseDelivery(o.str("DeliveryStatus")),
                circle = o.str("Circle").orEmpty(),
                division = o.str("Division").orEmpty(),
                region = o.str("Region").orEmpty(),
                block = o.str("Block").orEmpty(),
                district = o.str("District").orEmpty(),
                state = o.str("State").orEmpty(),
                pincode = o.str("Pincode").orEmpty(),
                source = LABEL_POSTALPINCODE_IN,
            )
        }.filter { it.name.isNotBlank() }
    }

    // ------------------------------------------------------------------ helpers

    /** Case-insensitive lookup trying each alias in order; numbers are stringified, nulls skipped. */
    internal fun JsonObject.str(vararg aliases: String): String? {
        for (alias in aliases) {
            val entry = entries.firstOrNull { it.key.equals(alias, ignoreCase = true) } ?: continue
            val v = entry.value
            val text = when (v) {
                is JsonNull -> null
                is JsonPrimitive -> v.content
                else -> null
            }?.trim()
            if (!text.isNullOrEmpty() && !text.equals("NA", ignoreCase = true)) return text
        }
        return null
    }

    private fun normaliseOfficeType(raw: String?): String = when (raw?.trim()?.uppercase()?.replace(".", "")) {
        null, "" -> ""
        "BO" -> "Branch Office"
        "SO" -> "Sub Post Office"
        "HO", "HPO" -> "Head Post Office"
        "GPO" -> "General Post Office"
        else -> raw.trim()
    }

    private fun normaliseDelivery(raw: String?): String = when (raw?.trim()?.lowercase()) {
        null, "" -> ""
        "delivery" -> "Delivery"
        "non-delivery", "non delivery", "nondelivery" -> "Non-Delivery"
        else -> raw.trim()
    }
}
