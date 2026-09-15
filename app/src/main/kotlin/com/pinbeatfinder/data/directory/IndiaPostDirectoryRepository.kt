package com.pinbeatfinder.data.directory

import com.pinbeatfinder.core.phonetic.IndianPhoneticNormalizer
import com.pinbeatfinder.core.phonetic.PhoneticSearchEngine
import com.pinbeatfinder.data.remote.LocalDirectorySource
import com.pinbeatfinder.data.repository.BeatDirectoryRepository
import com.pinbeatfinder.domain.model.PostOffice
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Searches the bundled All-India directory; ranks name hits with the same engine as the beat tab. */
class IndiaPostDirectoryRepository(
    private val dao: DirectoryDao,
    private val seeder: DirectorySeeder,
    private val phonetic: PhoneticSearchEngine,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LocalDirectorySource {
    override val id: String = ID
    override val label: String get() = (seeder.state.value as? SeedState.Ready)?.let { "$LABEL (${it.version})" } ?: LABEL

    override suspend fun isReady(): Boolean = seeder.isReady

    override suspend fun search(query: String, isPincode: Boolean): List<PostOffice> = withContext(ioDispatcher) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        val rows = if (isPincode) {
            dao.byPincode(q)
        } else {
            val norm = IndianPhoneticNormalizer.normalize(q)
            val keys = phonetic.encode(q)
            dao.searchByName(
                normPattern = if (norm.isEmpty()) "" else "$norm%",
                primaryPattern = if (keys.isEmpty) "" else "${keys.primary}%",
                alternatePattern = if (keys.isEmpty || keys.alternate == keys.primary) "" else "${keys.alternate}%",
                textPattern = "%${BeatDirectoryRepository.escapeLike(q)}%",
                limit = CANDIDATE_LIMIT,
            ).sortedByDescending { phonetic.score(q, it.name) }.take(RESULT_LIMIT)
        }
        val source = label
        rows.map { it.toPostOffice(source) }
    }

    private fun IndiaPostOfficeEntity.toPostOffice(source: String) = PostOffice(
        name = name,
        branchType = DirectoryAsset.officeTypeLabel(officeType),
        deliveryStatus = DirectoryAsset.deliveryLabel(delivery),
        circle = circle,
        division = division,
        region = region,
        block = "",
        district = district,
        state = state,
        pincode = pincode,
        source = source,
    )

    companion object {
        const val ID = "bundled"
        const val LABEL = "Built-in India Post directory"
        const val CANDIDATE_LIMIT = 400
        const val RESULT_LIMIT = 200
    }
}
