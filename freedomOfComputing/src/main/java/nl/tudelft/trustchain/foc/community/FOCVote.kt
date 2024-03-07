package nl.tudelft.trustchain.foc.community

import nl.tudelft.ipv8.messaging.Deserializable

data class FOCVote(val memberId: String) : nl.tudelft.ipv8.messaging.Serializable {
    override fun serialize(): ByteArray {
        return memberId.toByteArray()
    }

    companion object Deserializer : Deserializable<FOCVote> {
        override fun deserialize(
            buffer: ByteArray,
            offset: Int
        ): Pair<FOCVote, Int> {
            val toReturn = buffer.toString(Charsets.UTF_8)
            return Pair(FOCVote(toReturn), buffer.size)
        }
    }
}
