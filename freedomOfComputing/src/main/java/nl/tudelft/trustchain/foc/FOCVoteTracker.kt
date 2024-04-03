package nl.tudelft.trustchain.foc

import android.util.Log
import nl.tudelft.trustchain.common.freedomOfComputing.InstalledApps
import nl.tudelft.trustchain.foc.community.FOCSignedVote
import nl.tudelft.trustchain.foc.community.FOCVote
import nl.tudelft.trustchain.foc.util.ExtensionUtils
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

/**
 * FOCVoteTracker is a singleton which is responsible for keeping track of all the votes placed.
 */
object FOCVoteTracker {
    // Stores the votes for all apks
    private var voteMap: HashMap<String, HashSet<FOCSignedVote>> = HashMap()
    private val thresholdForInstall = 10

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
     * Gets called on start up to load the state from memory
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
     * Function to get the current votes that are placed
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
        this.checkThresholds()
    }

    /**
     * Checks whether any threshold for install is reached and if so installs the app.
     */
    private fun checkThresholds() {
        for (fileName in voteMap.keys) {
            val upVotes = getNumberOfVotes(fileName, true)
            if (upVotes >= thresholdForInstall && upVotes - getNumberOfVotes(fileName, false) >= thresholdForInstall) {
                InstalledApps.addApp(fileName.removeSuffix(ExtensionUtils.APK_DOT_EXTENSION))
            }
        }
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
        this.checkThresholds()
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

    /**
     * Used to be able to store the state in a file.
     */
    private fun serializeMap(map: HashMap<String, HashSet<FOCSignedVote>>): ByteArray {
        val byteArrayOutputStream = ByteArrayOutputStream()
        val objectOutputStream = ObjectOutputStream(byteArrayOutputStream)
        objectOutputStream.writeObject(map)
        objectOutputStream.flush()
        return byteArrayOutputStream.toByteArray()
    }

    /**
     * Used to recover the state from a file.
     */
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
