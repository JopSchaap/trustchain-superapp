package com.example.musicdao

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.musicdao.ui.TipArtistDialog
import com.example.musicdao.util.Util
import com.example.musicdao.wallet.CryptoCurrencyConfig
import com.frostwire.jlibtorrent.FileStorage
import com.frostwire.jlibtorrent.TorrentInfo
import com.github.se_bastiaan.torrentstream.StreamStatus
import com.github.se_bastiaan.torrentstream.Torrent
import com.github.se_bastiaan.torrentstream.listeners.TorrentListener
import kotlinx.android.synthetic.main.fragment_release.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.bitcoinj.core.Address
import java.io.File

/**
 * A release is an audio album, EP, single, etc.
 * It is related to 1 trustchain block, with the tag "publish_release" and its corresponding
 * structure (see TODO doc)
 */
class Release(
    private val magnet: String,
    private val artists: String,
    private val title: String,
    private val releaseDate: String,
    private val publisher: String,
    private val torrentInfoName: String?
) : Fragment(R.layout.fragment_release), TorrentListener {
    private var metadata: FileStorage? = null
    private var tracks: MutableMap<Int, Track> = hashMapOf()
    private var currentFileIndex = -1
    private var prevFileIndex = -1
    private var prevProgress = -1.0f
    private var localTorrent: Torrent? = null
    private var localTorrentFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)

        var torrentUrl = magnet
        if (torrentInfoName != null) {
            val torrentFileName =
                "${context?.cacheDir}/$torrentInfoName.torrent"
            val torrentFile = File(torrentFileName)
            if (torrentFile.isFile) {
                // This means we have the torrent file already locally and we can skip the step of
                // obtaining the TorrentInfo from magnet link
                torrentUrl = torrentFile.toURI().toURL().toString()
            }
        }
        if ((activity as MusicService).torrentStream.isStreaming) {
            (activity as MusicService).torrentStream.stopStream()
        }
        (activity as MusicService).torrentStream.addListener(this)
        (activity as MusicService).torrentStream.startStream(torrentUrl)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        blockMetadata.text =
            HtmlCompat.fromHtml(
                "<b>$artists - $title<br></b>" +
                    releaseDate, 0
            )

        AudioPlayer.getInstance().hideTrackInfo()

        enableTipButton()

        lifecycleScope.launchWhenCreated {
            while (isActive) {
                if (activity is MusicService && debugTextRelease != null) {
                    debugTextRelease.text = (activity as MusicService).getStatsOverview()
                }
                delay(1000)
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (release_table_layout.childCount == 0) {
            loadingTracks.visibility = View.VISIBLE
        } else {
            loadingTracks.visibility = View.GONE
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            // Debug button is a simple toggle for a connectivity stats display
            R.id.action_debug -> {
                if (debugTextRelease != null) {
                    if (debugTextRelease.visibility == View.VISIBLE) {
                        debugTextRelease.visibility = View.GONE
                    } else {
                        debugTextRelease.visibility = View.VISIBLE
                    }
                }
                true
            }
            R.id.action_wallet -> {
                findNavController().navigate(R.id.walletFragment)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        (activity as MusicService).torrentStream.removeListener(this)
        if ((activity as MusicService).torrentStream.isStreaming) {
            (activity as MusicService).torrentStream.stopStream()
        }
        super.onDestroy()
    }

    /**
     * If the Release has a publisher, then this should point to the public key of the wallet of
     * this publisher. This method checks whether the publisher field exists and whether it is
     * properly formatted so money can be sent to it. If it is, a tip button is shown
     */
    private fun enableTipButton() {
        if (publisher.isNotEmpty()) {
            try {
                Address.fromString(CryptoCurrencyConfig.networkParams, publisher)
            } catch (e: Exception) {
                return
            }
            tipButton.visibility = View.VISIBLE
            tipButton.isClickable = true
            tipButton.setOnClickListener {
                TipArtistDialog(publisher)
                    .show(childFragmentManager, "Tip the artist")
            }
        }
    }

    /**
     * This sets the metadata of the TorrentInfo in the UI, it renders the file list and other
     * metadata
     */
    private fun setMetadata(metadata: FileStorage) {
        loadingTracks.visibility = View.GONE
        this.metadata = metadata
        val num = metadata.numFiles()
        val filestorage = metadata

        val allowedExtensions =
            listOf(".flac", ".mp3", ".3gp", ".aac", ".mkv", ".wav", ".ogg", ".mp4", ".m4a")

        for (index in 0 until num) {
            var fileName = filestorage.fileName(index)
            var found = false
            for (s in allowedExtensions) {
                if (fileName.endsWith(s)) {
                    found = true
                    fileName = fileName.substringBefore(s)
                    fileName = fileName.replace("_", " ")
                }
            }
            if (found) {
                val track = Track(
                    fileName,
                    index,
                    this,
                    Util.readableBytes(filestorage.fileSize(index))
                )

                // Add a table row (Track) to the table (Release)
                val transaction = childFragmentManager.beginTransaction()
                transaction.add(R.id.release_table_layout, track, "track$index")
                transaction.commit()

                val transaction2 = childFragmentManager.beginTransaction()
                transaction2.add(
                    R.id.release_table_layout,
                    Fragment(R.layout.track_table_divider),
                    "track$index-divider"
                )
                transaction2.commitAllowingStateLoss()

                tracks[index] = track
            }
        }
    }

    /**
     * Select a track from the Release and start downloading and seeding it
     */
    fun selectTrackAndPlay(index: Int) {
        currentFileIndex = index

        val audioPlayer = AudioPlayer.getInstance()
        audioPlayer.prepareNextTrack()

        val tor = localTorrent
        if (tor != null) {
            (activity as MusicService).torrentStream.removeListener(this)
            tor.setSelectedFileIndex(currentFileIndex)
            Util.setSequentialPriorities(tor)
            (activity as MusicService).torrentStream.addListener(this)

            // TODO needs to have a solid check whether the file was already downloaded before
            if (tor.videoFile.isFile && tor.videoFile.length() > 1024 * 512) {
                startPlaying(tor.videoFile, currentFileIndex)
            } else {
                AudioPlayer.getInstance()
                    .setTrackInfo("Buffering track: " + tor.videoFile.nameWithoutExtension)
            }
        }
    }

    /**
     * For each track in the list, try to update its progress bar UI state which corresponds to the
     * percentage of downloaded pieces from that track
     */
    private fun updateFileProgress(fileProgressArray: LongArray) {
        if (this.isAdded) {
            fileProgressArray.forEachIndexed { index, fileProgress ->
                tracks[index]?.setDownloadProgress(
                    fileProgress,
                    metadata?.fileSize(index)
                )
            }
        }
    }

    private fun startPlaying(file: File, index: Int) {
        val audioPlayer = AudioPlayer.getInstance()
        audioPlayer.setAudioResource(file, index)
        AudioPlayer.getInstance().setTrackInfo(file.nameWithoutExtension)
    }

    override fun onStreamReady(torrent: Torrent?) {
        val fileProgress = torrent?.torrentHandle?.fileProgress()
        if (fileProgress != null) updateFileProgress(fileProgress)
        if (!AudioPlayer.getInstance().isPlaying() &&
            torrent != null &&
            currentFileIndex != -1
        ) {
            startPlaying(
                torrent.videoFile,
                currentFileIndex
            )
        }
    }

    override fun onStreamPrepared(torrent: Torrent?) {
        val fileProgress = torrent?.torrentHandle?.fileProgress()
        if (fileProgress != null) updateFileProgress(fileProgress)
        localTorrent = torrent
        if (torrent == null) return
        torrent.setSelectedFileIndex(0)
        torrent.startDownload()
        Util.setSequentialPriorities(torrent)
        val torrentFile = torrent.torrentHandle?.torrentFile()
            ?: throw Error("Unknown torrent file metadata")
        setMetadata(torrentFile.files())
        // Keep seeding the torrent, also after start-up or after browsing to a different Release
        val infoName = torrentInfoName ?: torrent.torrentHandle.name()
        (requireActivity() as MusicService).contentSeeder?.add(torrentFile, infoName)
    }

    override fun onStreamStopped() {
    }

    override fun onStreamStarted(torrent: Torrent?) {
        val fileProgress = torrent?.torrentHandle?.fileProgress()
        if (fileProgress != null) updateFileProgress(fileProgress)
    }

    override fun onStreamProgress(torrent: Torrent?, status: StreamStatus) {
        val torrentFile: TorrentInfo? = torrent?.torrentHandle?.torrentFile()
        if (metadata == null && torrentFile != null) setMetadata(torrentFile.files())
        val fileProgress = torrent?.torrentHandle?.fileProgress()
        if (fileProgress != null) updateFileProgress(fileProgress)
        val progress = status.progress
        if (progress > 30 && !AudioPlayer.getInstance()
                .isPlaying() && torrent != null && currentFileIndex != -1
        ) {
            startPlaying(
                torrent.videoFile,
                currentFileIndex
            )
        }
        if (progress != prevProgress) {
            AudioPlayer.getInstance().retry()
        }
        prevProgress = progress
        prevFileIndex = currentFileIndex
    }

    override fun onStreamError(torrent: Torrent?, e: Exception?) {
        Log.e("TorrentStream", "Torrent stream error")
        Toast.makeText(context, "Torrent stream error: ${e?.message}", Toast.LENGTH_LONG).show()
        e?.printStackTrace()
    }
}
