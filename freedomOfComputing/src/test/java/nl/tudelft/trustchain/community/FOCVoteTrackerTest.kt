package nl.tudelft.trustchain.community

import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.verify
import nl.tudelft.ipv8.keyvault.JavaCryptoProvider
import nl.tudelft.ipv8.keyvault.PrivateKey
import nl.tudelft.trustchain.foc.FOCVoteTracker
import nl.tudelft.trustchain.foc.community.FOCSignedVote
import nl.tudelft.trustchain.foc.community.FOCVote
import org.apache.commons.lang3.SerializationUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Collections
import java.util.UUID

class FOCVoteTrackerTest {
    private val cryptoProvider = JavaCryptoProvider
    private var privateKey1: PrivateKey = cryptoProvider.generateKey()
    private var privateKey2: PrivateKey = cryptoProvider.generateKey()
    private val baseVote1 = FOCVote("0000", true)
    private val baseVote2 = FOCVote("0001", false)
    private var voteMap2: HashMap<String, HashSet<FOCSignedVote>> = HashMap()
    private val signKey1 = privateKey1.sign(SerializationUtils.serialize(baseVote1))
    private val signedVote1 =
        FOCSignedVote(UUID.randomUUID(), baseVote1, signKey1, privateKey1.pub().keyToBin())
    private val signKey2 = privateKey2.sign(SerializationUtils.serialize(baseVote2))
    private val id2 = UUID.randomUUID()
    private val signedVote2 =
        FOCSignedVote(id2, baseVote2, signKey2, privateKey2.pub().keyToBin())
    private val voteTracker: FOCVoteTracker = FOCVoteTracker

    @Before
    fun setup() {
        voteMap2["test.apk"] = HashSet()
        voteMap2["test.apk"]?.add(signedVote1)
        voteTracker.reset()
    }

    @After
    fun teardown() {
        // Delete the file after each test if it exists
        val file = File("test")
        if (file.exists()) {
            file.delete()
        }
    }

    @Test
    fun checkVoteAndCurrentStateCorrect() {
        voteTracker.vote("test.apk", signedVote1)
        assertEquals(voteMap2, voteTracker.getCurrentState())
    }

    @Test
    fun checkStoreStateCorrect() {
        voteTracker.vote("test.apk", signedVote1)
        voteTracker.storeState("test")
        val file = File("test")
        assertTrue(file.exists())
    }

    @Test
    fun voteWrongSignature() {
        mockkStatic(android.util.Log::class)
        every { Log.w(any(), any(String::class)) } returns 1
        val signedVote =
            FOCSignedVote(UUID.randomUUID(), baseVote1, signKey1, privateKey2.pub().keyToBin())
        voteTracker.vote("test", signedVote)
        verify { Log.w("vote-gossip", "received vote with invalid pub-key signature combination!") }
    }

    @Test
    fun checkVoteDuplicate() {
        voteTracker.vote("test.apk", signedVote1)
        voteTracker.vote("test.apk", signedVote1)
        assertEquals(1, voteTracker.getNumberOfVotes("test.apk", true))
    }

    @Test
    fun checkLoadStateCorrect() {
        voteTracker.vote("test.apk", signedVote1)
        voteTracker.storeState("test")
        voteTracker.loadState("test")
        assertEquals(voteMap2, voteTracker.getCurrentState())
    }

    @Test
    fun checkMergeVoteMapsCorrect() {
        mockkStatic(android.util.Log::class)
        every { Log.i(any(), any()) } returns 1
        val voteMap3: HashMap<String, HashSet<FOCSignedVote>> = HashMap()
        voteMap3["test.apk"] = HashSet()
        voteMap3["test2.apk"] = HashSet()
        voteMap3["test.apk"]?.add(signedVote1)
        voteMap3["test2.apk"]?.add(signedVote2)
        voteTracker.vote("test2.apk", signedVote2)
        voteTracker.mergeVoteMaps(voteMap2)
        verify { Log.i("pull-based", "Merged maps") }
        assertEquals(voteMap3, voteTracker.getCurrentState())
    }

    @Test
    fun checkMergeVoteMapsCorrect2() {
        mockkStatic(android.util.Log::class)
        every { Log.i(any(), any()) } returns 1
        val voteMap3: HashMap<String, HashSet<FOCSignedVote>> = HashMap()
        voteMap3["test.apk"] = HashSet()
        voteMap3["test2.apk"] = HashSet()
        voteMap3["test.apk"]?.add(signedVote1)
        voteMap3["test2.apk"]?.add(signedVote2)
        voteTracker.vote("test2.apk", signedVote2)
        voteTracker.vote("test.apk", signedVote1)
        voteTracker.mergeVoteMaps(voteMap2)
        verify { Log.i("pull-based", "Merged maps") }
        assertEquals(voteMap3, voteTracker.getCurrentState())
    }

    @Test
    fun checkMergeVoteMapsWrongSignature() {
        mockkStatic(android.util.Log::class)
        every { Log.i(any(), any()) } returns 1
        val voteMap: HashMap<String, HashSet<FOCSignedVote>> = HashMap()
        voteMap["test.apk"] = HashSet()

        // Create incorrectly signed votes
        val signedVote1 = FOCSignedVote(UUID.randomUUID(), baseVote1, signKey1, privateKey2.pub().keyToBin())
        val signedVote2 = FOCSignedVote(UUID.randomUUID(), baseVote2, signKey2, privateKey1.pub().keyToBin())

        voteMap["test.apk"]?.add(signedVote1)
        voteMap["test.apk"]?.add(signedVote2)
        voteTracker.mergeVoteMaps(voteMap)
        verify { Log.i("pull-based", "Merged maps") }
        assertEquals(Collections.emptySet<FOCSignedVote>(), voteTracker.getCurrentState()["test.apk"])
    }

    @Test
    fun getNumberOfVotesCorrect() {
        voteTracker.vote("test.apk", signedVote1)
        assertEquals(0, voteTracker.getNumberOfVotes("test2.apk", true))
        assertEquals(1, voteTracker.getNumberOfVotes("test.apk", true))
    }

    @Test
    fun getVotesToSendTest() {
        voteTracker.vote("test.apk", signedVote1)
        voteTracker.vote("test.apk", signedVote2)
        val ids = HashSet<UUID>()
        ids.add(id2)
        val res = HashMap<String, HashSet<FOCSignedVote>>()
        res["test.apk"] = hashSetOf(signedVote1)
        assertEquals(res, voteTracker.getVotesToSend(ids))
    }

    @Test
    fun getVotesToSendTest2() {
        val privateKey3: PrivateKey = cryptoProvider.generateKey()
        val baseVote3 = FOCVote("0003", false)
        val signKey3 = privateKey3.sign(SerializationUtils.serialize(baseVote3))
        val signedVote3 =
            FOCSignedVote(UUID.randomUUID(), baseVote3, signKey3, privateKey3.pub().keyToBin())
        voteTracker.vote("test.apk", signedVote1)
        voteTracker.vote("test.apk", signedVote2)
        voteTracker.vote("test.apk", signedVote3)
        val ids = HashSet<UUID>()
        ids.add(id2)
        val res = HashMap<String, HashSet<FOCSignedVote>>()
        res["test.apk"] = hashSetOf(signedVote1, signedVote3)
        assertEquals(res, voteTracker.getVotesToSend(ids))
    }
}
