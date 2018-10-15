package io.rover.app.engineering.services

import com.google.gson.annotations.SerializedName
import io.reactivex.Single
import io.rover.account.AuthService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Access the legacy Rover V1 REST Web API.
 */
class V1ApiNetworkClient(
    private val authService: AuthService,
    baseUrl: String = "https://api.rover.io"
) {

    private val token
        get() = authService.bearerToken ?: throw RuntimeException("Attempt to use V1ApiNetworkClient before authentication")

    // build our own httpclient with authenticator

    /**
     * A Retrofit network client that is set up for use with Rx, has an interceptor to include the
     * auth header(s), and has its base URL set to that of the Rover REST Web API.
     */
    private val restApiNetworkClient = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(
            OkHttpClient.Builder()
                .addInterceptor {
                    it.proceed(
                        it.request().newBuilder()
                            .header("Authorization", "Bearer $token")
                            .build()
                    )
                }
                .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            .build()
        )
        .addConverterFactory(GsonConverterFactory.create())
        .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
        .build()

    private val experienceListClient = restApiNetworkClient.create(ExperienceListClient::class.java)

    private fun <T> mapRetrofitExceptionToAppError(throwable: Throwable): NetworkClientResult.Error<T> =
        when(throwable) {
            is HttpException -> {
                NetworkClientResult.Error<T>(
                    setOf(401, 402, 403).contains(throwable.response().code()),
                    throwable
                )
            }
            else -> {
                NetworkClientResult.Error<T>(
                    false,
                    throwable
                )
            }
        }

    fun allExperienceItems(collectionTypeFilter: String): Single<NetworkClientResult<List<ExperienceListItem>>> {
        return experienceListClient.experienceListItemsWithCollectionFilter(collectionTypeFilter)
            .map { response ->
                NetworkClientResult.Success<List<ExperienceListItem>>(
                    response.data.map { listItem ->
                        ExperienceListItem(
                            listItem.attributes.name,
                            listItem.attributes.viewToken,
                            listItem.id
                        )
                    }
                ) as NetworkClientResult<List<ExperienceListItem>>
            }
            .onErrorReturn { error ->
                mapRetrofitExceptionToAppError<List<ExperienceListItem>>(error)
            }

    }

    enum class Filter(val wireFormat: String) {
        Draft("drafts"), Published("published"), Archived("archived")
    }

    /**
     * While not an experience itself, this tells you where a real one may be found.
     */
    data class ExperienceListItem(
        val name: String,
        val viewToken: String,
        val id: String
    )

    interface ExperienceListClient {
        @GET("v1/experience-list-items")
        fun experienceListItemsWithCollectionFilter(
            @Query("filter[collectionType]") collectionTypeFilter: String
        ): Single<ExperienceListResponse>

        @GET("v1/experience-list-items")
        fun allExperienceListItems(): Single<ExperienceListResponse>

        data class ExperienceListResponse(
            @field:SerializedName("data")
            val data: List<ExperienceListItem>
        ) {

            data class ExperienceListItem(
                @field:SerializedName("attributes")
                val attributes: Attributes,

                @field:SerializedName("id")
                val id: String,

                @field:SerializedName("type")
                val type: String
            ) {
                data class Attributes(
                    @field:SerializedName("name")
                    val name: String,

                    @field:SerializedName("view-token")
                    val viewToken: String
                    // TODO: there are more but I'm going to ignore them for now.
                )
            }
        }
    }
}

sealed class NetworkClientResult<T> {
    class Error<T>(
        /**
         * Is (re-) login needed?
         */
        val loginNeeded: Boolean,

        /**
         * For clarity, the original cause.
         *
         * However, because this effectively leaks implementation details, most consuming code
         * should probably avoid checking this directly.
         */
        val reason: Throwable
    ): NetworkClientResult<T>()

    class Success<T>(
        val item: T
    ): NetworkClientResult<T>()
}
