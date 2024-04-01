package nl.tudelft.trustchain.foc.community

import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import nl.tudelft.ipv8.keyvault.PrivateKey
import nl.tudelft.trustchain.common.freedomOfComputing.InstalledApps
import nl.tudelft.trustchain.foc.MainActivityFOC
import nl.tudelft.trustchain.foc.util.ExtensionUtils
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class FOCVoteTracker(
    private val activity: MainActivityFOC,
    private val focCommunity: FOCCommunity
) {
    // Stores the votes for all apks
    private var voteMap: HashMap<String, HashSet<FOCSignedVote>> = HashMap()
    private val gossipDelay: Long = 1000
    private val scope = CoroutineScope(Dispatchers.IO)
    private val thresholdForInstall = 10

    fun start() {
        scope.launch {
            iterativelyCheckQueues()
        }
    }

    /**
     * Gets called on pause (or shutdown) of the app to persist state
     */
    fun storeState() {
        val votesFileName: String =
            activity.cacheDir.absolutePath + "/vote-tracker" + ExtensionUtils.DATA_DOT_EXTENSION
        try {
            File(votesFileName).writeBytes(serializeMap(voteMap))
        } catch (e: IOException) {
            printToast(e.toString())
            Log.e("vote-tracker-store", e.toString())
        }
    }

    /**
     * Gets called on start up to load the state from disk
     */
    fun loadState() {
        try {
            val voteFileName: String =
                activity.cacheDir.absolutePath + "/vote-tracker" + ExtensionUtils.DATA_DOT_EXTENSION
            val voteFile = File(voteFileName)

            if (voteFile.exists()) {
                voteMap = deserializeMap(voteFile.readBytes())
            }
        } catch (e: Exception) {
            printToast(e.toString())
            Log.e("vote-tracker load", e.toString())
        }
    }

    /**
     * Gets called when user places a vote
     * @param fileName APK on which vote is being placed
     * @param vote Vote that is being placed
     */
    fun vote(
        fileName: String,
        vote: FOCVote
    ) {
        // Sign the vote with the users private key such that other people can verify it
        val privateKey = focCommunity.myPeer.key as PrivateKey
        val signedVote = signVote(vote, privateKey)

        if (voteMap.containsKey(fileName)) {
            voteMap[fileName]!!.add(signedVote)
        } else {
            voteMap[fileName] = hashSetOf(signedVote)
        }
        // Initial TTL set to 2
        focCommunity.informAboutVote(fileName, signedVote, 2u)
    }

    /**
     * Gets called when user resumes UI settings
     */
    fun requestPullVotes() {
        focCommunity.informAboutPullSendVote()
    }

    /**
     * Gets called when a vote from another user has to be added to our state
     * @param fileName APK on which vote is being placed
     * @param vote Vote that is being placed
     */
    private fun insertVote(
        fileName: String,
        vote: FOCSignedVote
    ) {
        // Check the signature of the vote
        // TODO should somehow check if pub-key is associated to person that placed the vote
        if (checkAndGet(vote) == null) {
            Log.w("vote-gossip", "received vote with invalid pub-key signature combination!")
            return
        }

        if (voteMap.containsKey(fileName)) {
            voteMap[fileName]!!.add(vote)
        } else {
            voteMap[fileName] = hashSetOf(vote)
        }
        activity.runOnUiThread {
            activity.updateVoteCounts(fileName)
        }
    }

    /**
     * Gets called when a user receives incoming voting data
     * @param incomingMap incoming data
     */
    private fun mergeVoteMaps(incomingMap: HashMap<String, HashSet<FOCSignedVote>>) {
        for ((key, votes) in incomingMap) {
            if (voteMap.containsKey(key)) {
                voteMap[key]?.addAll(votes)
            } else { // this means votes from an apk can be received before the apk itself. Needs to be adjusted
                voteMap[key] = HashSet()
                voteMap[key]?.addAll(votes)
            }
        }
        Log.i("pull based", "incoming map: $incomingMap")
        Log.i("pull based", "new voteMap :$voteMap ")

        // check for vote counts above thresholds and add to home screen if reached
        checkThresholds()

        activity.runOnUiThread {
            for (key in voteMap.keys) {
                activity.updateVoteCounts(key)
            }
        }
    }

    /**
     * Get the number of votes for an APK
     * @param fileName APK for which we want to know the number of votes
     * @param voteType vote type that is for or against the APK
     */
    fun getNumberOfVotes(
        fileName: String,
        voteType: VoteType
    ): Int {
        if (!voteMap.containsKey(fileName)) {
            return 0
        }
        return voteMap[fileName]!!.count { v -> v.vote.voteType == voteType }
    }

    /**
     * Checks whether any threshold for install is reached and if so installs the app.
     */
    private fun checkThresholds() {
        for (fileName in voteMap.keys) {
            val upVotes = getNumberOfVotes(fileName, VoteType.UP)
            if (upVotes >= thresholdForInstall && upVotes - getNumberOfVotes(fileName, VoteType.DOWN) >= thresholdForInstall) {
                InstalledApps.addApp(fileName)
            }
        }
    }

    /**
     * Method to take votes from the queue and insert them into the tracker
     */
    private suspend fun iterativelyCheckQueues() {
        while (scope.isActive) {
            // Log.i("vote-gossip", "${focCommunity.voteMessagesQueue.size} in Queue")
            while (!focCommunity.voteMessagesQueue.isEmpty()) {
                val (_, payload) = focCommunity.voteMessagesQueue.remove()
                insertVote(payload.fileName, payload.focSignedVote)
            }
            // Log.i("pull based", "${focCommunity.pullVoteMessagesSendQueue.size} in  send Queue")
            while (!focCommunity.pullVoteMessagesSendQueue.isEmpty()) {
                val payload = focCommunity.pullVoteMessagesSendQueue.remove()
                focCommunity.informAboutPullReceiveVote(voteMap, payload)
            }
            // Log.i("pull based", "${focCommunity.pullVoteMessagesReceiveQueue.size} in receive Queue")
            while (!focCommunity.pullVoteMessagesReceiveQueue.isEmpty()) {
                val payload = focCommunity.pullVoteMessagesReceiveQueue.remove()
                Log.i("pull based", "merge votemaps called")
                mergeVoteMaps(payload.voteMap)
            }
            delay(gossipDelay)
        }
    }

    fun createFileKey(fileName: String) {
        if (!voteMap.containsKey(fileName)) {
            voteMap[fileName] = HashSet()
        }
    }

    /**
     * Display a short message on the screen
     */
    private fun printToast(s: String) {
        Toast.makeText(activity.applicationContext, s, Toast.LENGTH_LONG).show()
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
}
