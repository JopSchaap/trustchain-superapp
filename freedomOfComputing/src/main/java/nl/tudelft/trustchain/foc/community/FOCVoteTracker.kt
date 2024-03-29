package nl.tudelft.trustchain.foc.community

import android.util.Log
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.UUID

object FOCVoteTracker {
    // Stores the votes for all apks
    private var voteMap: HashMap<String, HashSet<FOCSignedVote>> = HashMap()

    /**
     * Gets called on pause (or shutdown) of the app to persist state
     */
    fun storeState(fileName: String) {
        try {
            File(fileName).writeBytes(serializeMap(voteMap))
        } catch (e: IOException) {
            Log.e("vote-tracker-store", e.toString())
        }
    }

    /**
     * Gets called on start up to load the state from disk
     */
    fun loadState(fileName: String) {
        try {
            val voteFile = File(fileName)
            if (voteFile.exists()) {
                voteMap = deserializeMap(voteFile.readBytes())
            }
        } catch (e: Exception) {
            Log.e("vote-tracker load", e.toString())
        }
    }

    /**
     * Function to get the current state of votes
     */
    fun getCurrentState(): HashMap<String, HashSet<FOCSignedVote>> {
        return voteMap
    }

    /**
     * Gets called when user places a vote
     * @param fileName APK on which vote is being placed
     * @param signedVote Vote that is being placed
     */
    fun vote(
        fileName: String,
        signedVote: FOCSignedVote
    ) {
        // Check the signature of the vote
        // TODO should somehow check if pub-key is associated to person that placed the vote
        if (checkAndGet(signedVote) == null) {
            Log.w("vote-gossip", "received vote with invalid pub-key signature combination!")
            return
        }
        if (voteMap.containsKey(fileName)) {
            voteMap[fileName]!!.add(signedVote)
        } else {
            voteMap[fileName] = hashSetOf(signedVote)
        }
    }

    /**
     * Method to get the votes we want to respond with for a pull request.
     * @param ids Set of vote ids that they already have
     */
    fun getVotesToSend(ids: HashSet<UUID>): HashMap<String, HashSet<FOCSignedVote>> {
        val res = HashMap<String, HashSet<FOCSignedVote>>()
        voteMap.forEach { entry ->
            entry.value.forEach { vote ->
                if (!ids.contains(vote.id)) {
                    if (res.containsKey(entry.key)) {
                        res[entry.key]!!.add(vote)
                    } else {
                        res[entry.key] = hashSetOf(vote)
                    }
                }
            }
        }
        return res
    }

    /**
     * Gets called when a user receives incoming voting data
     * @param incomingMap incoming data
     */
    fun mergeVoteMaps(incomingMap: HashMap<String, HashSet<FOCSignedVote>>) {
        for ((key, votes) in incomingMap) {
            if (voteMap.containsKey(key)) {
                voteMap[key]?.addAll(votes)
            } else { // this means votes from an apk can be received before the apk itself.
                voteMap[key] = votes
            }
        }
        Log.i("pull-based", "Merged maps")
    }

    /**
     * Get the number of votes for an APK
     * @param fileName APK for which we want to know the number of votes
     * @param isUpVote defines whether the vote is an upvote or a downvote
     */
    fun getNumberOfVotes(
        fileName: String,
        isUpVote: Boolean
    ): Int {
        if (!voteMap.containsKey(fileName)) {
            return 0
        }
        return voteMap[fileName]!!.count { v -> v.vote.isUpVote == isUpVote }
    }

    private fun serializeMap(map: HashMap<String, HashSet<FOCSignedVote>>): ByteArray {
        val byteArrayOutputStream = ByteArrayOutputStream()
        val objectOutputStream = ObjectOutputStream(byteArrayOutputStream)
        objectOutputStream.writeObject(map)
        objectOutputStream.flush()
        return byteArrayOutputStream.toByteArray()
    }

    @Suppress("UNCHECKED_CAST")
    private fun deserializeMap(byteArray: ByteArray): HashMap<String, HashSet<FOCSignedVote>> {
        val byteArrayInputStream = ByteArrayInputStream(byteArray)
        val objectInputStream = ObjectInputStream(byteArrayInputStream)
        return objectInputStream.readObject() as HashMap<String, HashSet<FOCSignedVote>>
    }

    /**
     * Checks the signature of the signed vote and if the signature is correct and the signer is verified returns the vote object else returns null.
     */
    private fun checkAndGet(signedVote: FOCSignedVote): FOCVote? {
        // TODO Should somehow verify the pub-key is associated to a known user
        return signedVote.checkAndGet()
    }

    /**
     * Added method for testing purposes
     */
    fun reset() {
        voteMap.clear()
    }
}
