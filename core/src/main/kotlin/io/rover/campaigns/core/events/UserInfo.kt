package io.rover.campaigns.core.events

import io.rover.campaigns.core.data.domain.Attributes
import io.rover.campaigns.core.data.graphql.operations.data.encodeJson
import io.rover.campaigns.core.data.graphql.operations.data.toAttributesHash
import io.rover.campaigns.core.logging.log
import io.rover.campaigns.core.platform.LocalStorage
import org.json.JSONObject

class UserInfo(
    localStorage: LocalStorage
) : UserInfoInterface {
    private val store = localStorage.getKeyValueStorageFor(STORAGE_CONTEXT_IDENTIFIER)
    override fun update(builder: (attributes: HashMap<String, Any>) -> Unit) {
        val mutableDraft = HashMap(currentUserInfo)
        builder(mutableDraft)
        currentUserInfo = mutableDraft
    }

    override fun clear() {
        currentUserInfo = hashMapOf()
    }

    override var currentUserInfo: Attributes = try {
        val currentAttributesJson = store[USER_INFO_KEY]
        when (currentAttributesJson) {
            null -> hashMapOf()
            else -> JSONObject(store[USER_INFO_KEY]).toAttributesHash()
        }
    } catch (throwable: Throwable) {
        log.w("Corrupted local user info, ignoring and starting fresh.  Cause: ${throwable.message}")
        hashMapOf()
    }
        private set(value) {
            field = value
            store[USER_INFO_KEY] = value.encodeJson().toString()
            log.v("Stored new user info.")
        }

    companion object {
        private const val STORAGE_CONTEXT_IDENTIFIER = "user-info"
        private const val USER_INFO_KEY = "user-info"
    }
}