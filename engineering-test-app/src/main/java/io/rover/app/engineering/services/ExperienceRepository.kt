package io.rover.app.engineering.services

import io.reactivex.Scheduler
import io.reactivex.Single

/**
 * Uses the Rover legacy monolith REST API to acquire the experiences list in this account.
 *
 * Right now barely more than a façade around the network client.
 */
class ExperienceRepository(
    private val v1ApiNetworkClient: V1ApiNetworkClient,
    private val ioScheduler: Scheduler
) {
    fun allExperiences(filter: ExperienceFilter): Single<NetworkClientResult<List<ExperienceListItem>>> {
        return v1ApiNetworkClient.allExperienceItems(filter.collectionTypeApiValue).map { networkExperienceItems ->
            when(networkExperienceItems) {
                is NetworkClientResult.Success -> {
                    NetworkClientResult.Success<List<ExperienceListItem>>(
                        networkExperienceItems.item.map { networkExperienceItem ->
                            ExperienceListItem(
                                networkExperienceItem.name,
                                networkExperienceItem.id
                            )
                        }
                    )
                }
                is NetworkClientResult.Error -> NetworkClientResult.Error<List<ExperienceListItem>>(
                    networkExperienceItems.loginNeeded,
                    networkExperienceItems.reason
                )
            }

        }.subscribeOn(ioScheduler)
    }

    /**
     * While not an experience itself, this tells you where a real one may be found.
     */
    data class ExperienceListItem(
        val name: String,
        val id: String
    )

    enum class ExperienceFilter(
        val collectionTypeApiValue: String
    ) {
        Draft("drafts"), Published("published")
    }
}
