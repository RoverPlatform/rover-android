//package io.rover.rover.core.data
//
//import io.rover.notifications.domain.Notification
//import io.rover.rover.core.data.state.StateManagerServiceInterface
//import io.rover.rover.core.events.EventQueueServiceInterface
//import io.rover.rover.core.logging.GlobalStaticLogHolder
//import io.rover.rover.core.logging.JvmLogger
//import io.rover.rover.core.streams.Publisher
//import io.rover.rover.core.streams.Scheduler
//import io.rover.rover.core.streams.subscribe
//import io.rover.notifications.NotificationsRepository
//import io.rover.notifications.graphql.encodeJson
//import io.rover.notifications.ui.concerns.NotificationsRepositoryInterface
//import io.rover.rover.platform.DateFormatting
//import io.rover.rover.platform.KeyValueStorage
//import io.rover.rover.platform.LocalStorage
//import org.amshove.kluent.mock
//import org.amshove.kluent.shouldEqual
//import org.jetbrains.spek.api.Spek
//import org.jetbrains.spek.api.dsl.given
//import org.jetbrains.spek.api.dsl.it
//import org.jetbrains.spek.api.dsl.on
//import org.json.JSONArray
//import org.json.JSONObject
//import java.net.URI
//import java.util.Date
//import java.util.concurrent.Executor
//
//
//// Important notes about spek.  SpekBodies are executed at setup time, so everything by default is
//// shared between all tests.  Or so it seems.
//
//class NotificationsRepositorySpec: Spek({
//    given("a notifications repository") {
//        // set up a minimal Rover instance just to get logging working.
//
//        GlobalStaticLogHolder.globalLogEmitter = JvmLogger()
//
//        fun repo(notificationsToEmit: List<Notification>): NotificationsRepository {
//            val eventQueue : EventQueueServiceInterface = mock()
//
//            val stateManagerService: StateManagerServiceInterface = object : StateManagerServiceInterface by mock() {
//                override fun updatesForQueryFragment(queryFragment: String): Publisher<NetworkResult<JSONObject>> {
//                    return Publisher.just(
//                        NetworkResult.Success(
//                            // crap they have be encoded. those concerns should be separated.
//                            // TODO: cannot use JSONObject here.
//                            JSONObject().apply {
//                                put(
//                                    "notifications",
//                                    JSONArray(
//                                        notificationsToEmit.map { it.encodeJson(DateFormatting()) }
//                                    )
//                                )
//                            }
//                        )
//                    )
//                }
//            }
//
//            val keyValueStorage : MutableMap<String, String?> = mutableMapOf()
//            val repo = NotificationsRepository(
//                DateFormatting(),
//                Executor { command -> command.run() },
//                object : Scheduler {
//                    override fun execute(runnable: () -> Unit) {
//                        runnable()
//                    }
//                },
//                eventQueue,
//                stateManagerService,
//                object : LocalStorage {
//                    override fun getKeyValueStorageFor(namedContext: String): KeyValueStorage {
//                        return object : KeyValueStorage {
//                            override fun get(key: String): String? {
//                                return keyValueStorage[key]
//                            }
//
//                            override fun set(key: String, value: String?) {
//                                keyValueStorage[key] = value
//                            }
//                        }
//                    }
//                }
//            )
//
//            return repo
//        }
//
//        given("the cloud API returns device state with one event") {
//            val repository by memoized { repo(listOf(
//                Notification(
//                    "41C7F235-7B47-4DC9-9ED8-E1C937F6C6D1",
//                    null, null, "body", false, false, true, Date(), Date(),
//                    URI("http://google.ca"),
//                    null
//                )
//            ))}
//
//            // how do I test the yielding of the current cached contents on disk?
//            // how do I test the merge code?
//            // how can I split up the repo into a repo and, say, two "stores"? will that work for our async sync model?
//
//            fun emittedEvents(): List<NotificationsRepositoryInterface.Emission.Update> {
//                val updates : MutableList<NotificationsRepositoryInterface.Emission.Update> = mutableListOf<NotificationsRepositoryInterface.Emission.Update>()
//                repository.updates().subscribe { emission ->
//                    updates.add(emission)
//                }
//                return updates
//            }
//
//            on("initial subscription") {
//                val updates = emittedEvents()
//
//                it("should have yielded no updates because no cache is yet available") {
//                    updates.size.shouldEqual(0)
//                }
//            }
//
//            on("after refresh called") {
//                val updates = emittedEvents()
//                repository.refresh()
//                it("should yield the first emission") {
//                    updates.first().notifications.first().id.shouldEqual("41C7F235-7B47-4DC9-9ED8-E1C937F6C6D1")
//                    updates.size.shouldEqual(1)
//                }
//
//                it("should yield only one emission") {
//                    updates.size.shouldEqual(1)
//                }
//            }
//
//        }
//    }
//})