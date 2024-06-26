package nl.tudelft.trustchain.foc.community

import android.content.Context
import android.util.Log
import com.frostwire.jlibtorrent.TorrentInfo
import mu.KotlinLogging
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.Overlay
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
import nl.tudelft.ipv8.keyvault.PrivateKey
import nl.tudelft.ipv8.messaging.Packet
import nl.tudelft.ipv8.messaging.eva.TransferException
import nl.tudelft.ipv8.messaging.eva.TransferProgress
import nl.tudelft.trustchain.common.freedomOfComputing.AppPayload
import nl.tudelft.trustchain.common.freedomOfComputing.AppRequestPayload
import nl.tudelft.trustchain.foc.FOCVoteTracker
import nl.tudelft.trustchain.foc.MainActivityFOC
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception
import java.util.*
import kotlin.collections.HashSet
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

private val logger = KotlinLogging.logger {}

@OptIn(ExperimentalUnsignedTypes::class)
class FOCCommunity(
    context: Context
) : FOCCommunityBase() {
    override val serviceId = "12313685c1912a141279f8248fc8db5899c5df5b"

    val discoveredAddressesContacted: MutableMap<IPv4Address, Date> = mutableMapOf()

    private val appDirectory = context.cacheDir
    var activity: MainActivityFOC? = null

    private lateinit var evaSendCompleteCallback: (
        peer: Peer,
        info: String,
        nonce: ULong
    ) -> Unit
    private lateinit var evaReceiveProgressCallback: (
        peer: Peer,
        info: String,
        progress: TransferProgress
    ) -> Unit
    private lateinit var evaReceiveCompleteCallback: (
        peer: Peer,
        info: String,
        id: String,
        data: ByteArray?
    ) -> Unit
    private lateinit var evaErrorCallback: (
        peer: Peer,
        exception: TransferException
    ) -> Unit

    override fun setEVAOnReceiveProgressCallback(f: (peer: Peer, info: String, progress: TransferProgress) -> Unit) {
        this.evaReceiveProgressCallback = f
    }

    override fun setEVAOnReceiveCompleteCallback(f: (peer: Peer, info: String, id: String, data: ByteArray?) -> Unit) {
        this.evaReceiveCompleteCallback = f
    }

    override fun setEVAOnErrorCallback(f: (peer: Peer, exception: TransferException) -> Unit) {
        this.evaErrorCallback = f
    }

    // Retrieve the trustchain community
    private fun getTrustChainCommunity(): TrustChainCommunity {
        return IPv8Android.getInstance().getOverlay()
            ?: throw IllegalStateException("TrustChainCommunity is not configured")
    }

    override fun walkTo(address: IPv4Address) {
        super.walkTo(address)

        discoveredAddressesContacted[address] = Date()
    }

    override var torrentMessagesList = ArrayList<Pair<Peer, FOCMessage>>()

    private val focVoteTracker: FOCVoteTracker = FOCVoteTracker

    object MessageId {
        const val FOC_THALIS_MESSAGE = 220
        const val TORRENT_MESSAGE = 230
        const val APP_REQUEST = 231
        const val APP = 232
        const val VOTE_MESSAGE = 233
        const val PULL_VOTE_MESSAGE = 234
        const val PULL_REQUEST = 235
    }

    /**
     * When a user adds a torrent, it is broadcasted to all peers
     * @param torrentName name of torrent
     */
    override fun informAboutTorrent(torrentName: String) {
        if (torrentName != "") {
            for (peer in getPeers()) {
                val packet =
                    serializePacket(
                        MessageId.TORRENT_MESSAGE,
                        FOCMessage("foc:" + torrentName),
                        true
                    )
                send(peer.address, packet)
            }
        }
    }

    /**
     * When a user votes on an apk, the result of the vote is hot potatoed to a random subset of peers
     * @param fileName name of APK
     * @param vote signed vote of the user
     * @param ttl The Time-To-Live (TTL) value indicating the maximum number of hops the vote information can traverse in the gossip network.
     */
    override fun informAboutVote(
        fileName: String,
        vote: FOCSignedVote,
        ttl: Int
    ) {
        Log.i(
            "vote-gossip",
            "Informing about ${vote.vote.isUpVote} vote on $fileName from ${vote.vote.memberId}"
        )
        val peers = getPeers().shuffled()
        val n = peers.size
        // Gossip to log(n) peers
        for (peer in peers.take(max(floor(ln(n.toDouble())).toInt(), min(n, 3)))) {
            Log.i("vote-gossip", "Sending vote to ${peer.mid}")
            val packet =
                serializePacket(
                    MessageId.VOTE_MESSAGE,
                    FOCVoteMessage(fileName, vote, ttl),
                    true
                )
            Log.i("vote-gossip", "Address: ${peer.address}, packet: $packet")
            send(peer.address, packet)
        }
    }

    override fun sendPullRequest(ids: HashSet<UUID>) {
        Log.i("pull-based", "Sending pull request")
        val message = FOCPullRequestMessage(ids)
        Log.i("pull-based", "I already have ${ids.size} votes")
        for (peer in getPeers()) {
            Log.i("pull-based", "sending pull vote request to ${peer.mid}")
            val m =
                serializePacket(
                    MessageId.PULL_REQUEST,
                    message,
                    encrypt = true,
                    recipient = peer
                )
            evaSendBinary(peer, PULL_REQUEST_ATTACHMENT, UUID.randomUUID().toString(), m)
        }
    }

    /**
     * Sends an application request to peer. It  constructs an application
     * request payload with the given torrent info hash and UUID, then sends it to the specified peer.
     * @param torrentInfoHash the hash of the given torrent
     * @param peer  the peer
     * @uuid the uuid (Universally Unique Identifier) uniquely identifies the request in the application layer
     */
    override fun sendAppRequest(
        torrentInfoHash: String,
        peer: Peer,
        uuid: String
    ) {
        AppRequestPayload(torrentInfoHash, uuid).let { payload ->
            logger.debug { "-> $payload" }
            send(peer, serializePacket(MessageId.APP_REQUEST, payload))
        }
    }

    init {
        messageHandlers[MessageId.FOC_THALIS_MESSAGE] = ::onMessage
        messageHandlers[MessageId.TORRENT_MESSAGE] = ::onTorrentMessage
        messageHandlers[MessageId.VOTE_MESSAGE] = ::onVoteMessage
        messageHandlers[MessageId.PULL_VOTE_MESSAGE] = ::onPullVoteMessage
        messageHandlers[MessageId.PULL_REQUEST] = ::onPullRequest
        messageHandlers[MessageId.APP_REQUEST] = ::onAppRequestPacket
        messageHandlers[MessageId.APP] = ::onAppPacket
        evaProtocolEnabled = true
    }

    override fun load() {
        super.load()

        setOnEVASendCompleteCallback(::onEVASendCompleteCallback)
        setOnEVAReceiveProgressCallback(::onEVAReceiveProgressCallback)
        setOnEVAReceiveCompleteCallback(::onEVAReceiveCompleteCallback)
        setOnEVAErrorCallback(::onEVAErrorCallback)
    }

    private fun onMessage(packet: Packet) {
        val (peer, payload) = packet.getAuthPayload(FOCMessage)
        Log.i("personal", peer.mid + ": " + payload.message)
    }

    /**
     * Function to process and incoming torrent magnet link in the form of a packet.
     * checks if the torrent message is already present in the list of received messages,
     * and adds it if not already present.
     * @param packet received packet
     */
    private fun onTorrentMessage(packet: Packet) {
        val (peer, payload) = packet.getAuthPayload(FOCMessage)
        val torrentHash =
            payload.message.substringAfter("magnet:?xt=urn:btih:")
                .substringBefore("&dn=")
        if (torrentMessagesList.none {
                it.second
                val existingHash =
                    it.second.message.substringAfter("magnet:?xt=urn:btih:").substringBefore("&dn=")
                torrentHash == existingHash
            }
        ) {
            torrentMessagesList.add(Pair(peer, payload))
            Log.i("personal", peer.mid + ": " + payload.message)
        }
    }

    /**
     * Invoked when a peer receives an VOTE_MESSAGE. Once received, it updates
     * the current voteMap and updates the changes in hte UI
     * @param packet received packet
     */
    private fun onVoteMessage(packet: Packet) {
        Log.i("vote-gossip", "OnVoteMessage Called")
        val (peer, payload) = packet.getAuthPayload(FOCVoteMessage)
        Log.i(
            "vote-gossip",
            "Received vote message from ${peer.mid} for file ${payload.fileName} and direction ${payload.focSignedVote.vote.isUpVote}"
        )
        focVoteTracker.vote(payload.fileName, payload.focSignedVote)

        activity?.runOnUiThread {
            activity?.updateVoteCounts(payload.fileName)
        }
        // If TTL is > 0 then forward the message further
        if (payload.TTL > 0) {
            informAboutVote(payload.fileName, payload.focSignedVote, payload.TTL - 1)
        }
    }

    private fun onPullVoteMessage(packet: Packet) {
        Log.i("pull-based", "onPullVoteMessage called")
        val (peer, payload) =
            packet.getDecryptedAuthPayload(
                FOCPullVoteMessage.Deserializer,
                myPeer.key as PrivateKey
            )
        Log.i("pull-based", "Received votemap from ${peer.address}")
        focVoteTracker.mergeVoteMaps(payload.voteMap)
        activity?.runOnUiThread {
            for (key in focVoteTracker.getCurrentState().keys) {
                activity?.updateVoteCounts(key)
            }
        }
    }

    private fun onPullRequest(packet: Packet) {
        Log.i("pull-based", "Received Pull Request")
        val (peer, payload) =
            packet.getDecryptedAuthPayload(
                FOCPullRequestMessage.Deserializer,
                myPeer.key as PrivateKey
            )
        Log.i("pull-based", "Pull Request has ${payload.ids.size} votes")
        val voteMap = focVoteTracker.getVotesToSend(payload.ids)
        val m =
            serializePacket(
                MessageId.PULL_VOTE_MESSAGE,
                FOCPullVoteMessage(voteMap),
                encrypt = true,
                recipient = peer
            )
        evaSendBinary(peer, PULL_RESPONSE_ATTACHMENT, UUID.randomUUID().toString(), m)
    }

    private fun onAppRequestPacket(packet: Packet) {
        val (peer, payload) = packet.getAuthPayload(AppRequestPayload.Deserializer)
        logger.debug { "-> DemoCommunity: Received request $payload from ${peer.mid}" }
        onAppRequest(peer, payload)
    }

    private fun onAppRequest(
        peer: Peer,
        appRequestPayload: AppRequestPayload
    ) {
        try {
            locateApp(appRequestPayload.appTorrentInfoHash)?.let { file ->
                logger.debug { "-> sending app ${file.name} to ${peer.mid}" }
                sendApp(peer, appRequestPayload.appTorrentInfoHash, file, appRequestPayload.uuid)
                return
            }
            logger.debug { "Received Request for an app that doesn't exist" }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sendApp(
        peer: Peer,
        appTorrentInfoHash: String,
        file: File,
        uuid: String
    ) {
        val appPayload = AppPayload(appTorrentInfoHash, file.name, file.readBytes())
        val packet =
            serializePacket(MessageId.APP, appPayload, encrypt = true, recipient = peer)
        if (evaProtocolEnabled) {
            evaSendBinary(
                peer,
                EVA_FOC_COMMUNITY_ATTACHMENT,
                uuid,
                packet
            )
        } else {
            send(peer, packet)
        }
    }

    private fun locateApp(appTorrentInfoHash: String): File? {
        appDirectory.listFiles()?.forEachIndexed { _, file ->
            if (file.name.endsWith(".torrent")) {
                TorrentInfo(file).let { torrentInfo ->
                    if (torrentInfo.infoHash().toString() == appTorrentInfoHash) {
                        if (torrentInfo.isValid) {
                            if (isTorrentOkay(torrentInfo, appDirectory)) {
                                return File(appDirectory.path + "/" + torrentInfo.name())
                            }
                        }
                    }
                }
            }
        }
        return null
    }

    /***
     *This function evaluates whether the torrent file
     * is suitable for downloading based on the directory.
     * it checks if the file extension is either "jar" or "apk" and if the file size
     * does not exceed the total size specified.
     * @param torrentInfo torrent file information
     * @param saveDirectory the directory where the torrent file will be saved
     * @return `true` if the torrent file meets the criteria for downloading; `false` otherwise.
     */
    private fun isTorrentOkay(
        torrentInfo: TorrentInfo,
        saveDirectory: File
    ): Boolean {
        File(saveDirectory.path + "/" + torrentInfo.name()).run {
            if (!arrayListOf("jar", "apk").contains(extension)) return false
            if (length() >= torrentInfo.totalSize()) return true
        }
        return false
    }

    /**
     *This function decrypts and processes the incoming packet containing an application payload.
     * @param packet the incoming packet
     */
    private fun onAppPacket(packet: Packet) {
        val (peer, payload) =
            packet.getDecryptedAuthPayload(
                AppPayload.Deserializer,
                myPeer.key as PrivateKey
            )
        logger.debug { "<- Received app from ${peer.mid}" }
        val file = appDirectory.toString() + "/" + payload.appName
        val existingFile = File(file)
        Log.i("send-app", "Received app packet of size: ${payload.data.size} Bytes")
        if (!existingFile.exists()) {
            try {
                val os = FileOutputStream(file)
                os.write(payload.data)
            } catch (e: Exception) {
                logger.debug { "Could not write file from $peer with hash ${payload.appTorrentInfoHash}" }
            }
        } else {
            logger.error { "File $file already exists, will not overwrite after EVA download" }
        }
    }

    /**
     * This function is invoked when the EVA send operation to a peer is completed. It logs the
     *  completion of the send operation with the provided information.
     *  @param peer peer involved in callback
     *  @param info additional information associated with send operation
     *  @param nonce nonce value associated with operation
     */
    private fun onEVASendCompleteCallback(
        peer: Peer,
        info: String,
        nonce: ULong
    ) {
        Log.d("DemoCommunity", "ON EVA send complete callback for '$info'")

        if (info != EVA_FOC_COMMUNITY_ATTACHMENT) return

        if (this::evaSendCompleteCallback.isInitialized) {
            this.evaSendCompleteCallback(peer, info, nonce)
        }
    }

    /**
     *This function is invoked when the EVA receive operation progresses with the provided information
     * It logs the progress of the receive operation with the provided information.
     * @param peer peer involved in callback
     * @param info additional information associated with send operation
     * @param progress the progress details of the receive operation
     */
    private fun onEVAReceiveProgressCallback(
        peer: Peer,
        info: String,
        progress: TransferProgress
    ) {
        Log.d("DemoCommunity", "ON EVA receive progress callback for '$info'")

        if (info != EVA_FOC_COMMUNITY_ATTACHMENT) return

        if (this::evaReceiveProgressCallback.isInitialized) {
            this.evaReceiveProgressCallback(peer, info, progress)
        }
    }

    /**
     *This function is invoked when the EVA receive operation to a peer is completed. It logs the
     *completion of the receive operation with the provided information.
     * @param peer peer involved in callback
     * @param info additional information associated with send operation
     * @param id The ID associated with the receive operation.
     * @param data The data received in the EVA operation, if available.
     *
     */
    private fun onEVAReceiveCompleteCallback(
        peer: Peer,
        info: String,
        id: String,
        data: ByteArray?
    ) {
        Log.d("DemoCommunity", "ON EVA receive complete callback for '$info'")

        if (info == PULL_RESPONSE_ATTACHMENT) {
            data?.let {
                val packet = Packet(peer.address, it)
                onPullVoteMessage(packet)
            }
        }
        if (info == PULL_REQUEST_ATTACHMENT) {
            data?.let {
                val packet = Packet(peer.address, it)
                onPullRequest(packet)
            }
        }
        if (info != EVA_FOC_COMMUNITY_ATTACHMENT) return

        data?.let {
            val packet = Packet(peer.address, it)
            onAppPacket(packet)
        }

        if (this::evaReceiveCompleteCallback.isInitialized) {
            this.evaReceiveCompleteCallback(peer, info, id, data)
        }
    }

    /**
     *This function is invoked when an error occurs during an EVA operation with the specified
     *peer and exception details. It logs the error information and invokes the registered callback
     * @param peer peer involved in callback
     * @param exception the exception that occurred
     */
    private fun onEVAErrorCallback(
        peer: Peer,
        exception: TransferException
    ) {
        Log.d("DemoCommunity", "ON EVA error callback for '${exception.info} from ${peer.mid}'")

        if (this::evaErrorCallback.isInitialized) {
            this.evaErrorCallback(peer, exception)
        }
    }

    class Factory(
        private val context: Context
    ) : Overlay.Factory<FOCCommunity>(FOCCommunity::class.java) {
        override fun create(): FOCCommunity {
            return FOCCommunity(context)
        }
    }

    companion object {
        // Use this until we can commit an id to kotlin ipv8
        const val EVA_FOC_COMMUNITY_ATTACHMENT = "eva_foc_community_attachment"
        const val PULL_RESPONSE_ATTACHMENT = "pull_response_attachment"
        const val PULL_REQUEST_ATTACHMENT = "pull_request_attachment"
    }
}
