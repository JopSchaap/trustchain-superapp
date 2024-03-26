package nl.tudelft.trustchain.foc.community

import android.util.Log
import nl.tudelft.ipv8.messaging.Deserializable

data class FOCMessage(val message: String) : nl.tudelft.ipv8.messaging.Serializable {
    override fun serialize(): ByteArray {
        return message.toByteArray()
    }

    companion object Deserializer : Deserializable<FOCMessage> {
        override fun deserialize(
            buffer: ByteArray,
            offset: Int
        ): Pair<FOCMessage, Int> {
            var toReturn = buffer.toString(Charsets.UTF_8)
            Log.i("benchmarking", "Deserialize FOCMessage: ${buffer.size} Bytes")
            return Pair(FOCMessage(toReturn), buffer.size)
        }
    }
}
