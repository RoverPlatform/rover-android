package io.rover.rover.services.network.requests.data

import io.rover.rover.core.domain.Device
import io.rover.rover.services.network.NetworkRequest
import io.rover.rover.services.network.WireEncoderInterface
import org.json.JSONObject

class FetchDeviceRequest(): NetworkRequest<Device> {
    override val query: String
        get() = """
            query {
                device {
                    profile {
                        identifier
                        attributes
                    }
                    regions {
                        __typename
                        ... on BeaconRegion {
                            uuid
                            major
                            minor
                        }
                        ... on GeofenceRegion {
                            latitude
                            longitude
                            radius
                        }
                    }
                }
            }
            """

    override val variables: JSONObject
        get() = JSONObject()

    override fun decodePayload(responseObject: JSONObject, wireEncoder: WireEncoderInterface): Device =
        Device.decodeJson(responseObject.getJSONObject("data").getJSONObject("device"))
}
