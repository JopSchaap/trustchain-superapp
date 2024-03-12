package nl.tudelft.trustchain.foc.util

import com.frostwire.jlibtorrent.Sha1Hash
import nl.tudelft.ipv8.util.sha1
import nl.tudelft.trustchain.foc.DEFAULT_APK
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.copyTo
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isReadable
import kotlin.io.path.name
import kotlin.io.path.readBytes

class ApkTrackerTest {
    private lateinit var apkTracker: ApkTracker

    @JvmField
    @Rule
    var folder: TemporaryFolder = TemporaryFolder.builder().assureDeletion().build()
    private lateinit var apkFolder: Path

    private val searchApkPath = Paths.get("src/main/res/raw/search.apk")

    @Before
    fun setUp() {
        apkFolder = folder.newFolder("apk_dir").toPath()
        apkTracker = ApkTracker(apkFolder)
    }

    @Test
    fun createApkDir() {
        val apkDirPath = apkTracker.createApkDir()
        Assert.assertTrue(apkDirPath.exists())
        Assert.assertTrue(apkDirPath.isDirectory())
        Assert.assertTrue(apkDirPath.name.startsWith(apkTracker.noHashPrefix))
    }

    @Test
    fun createApkDirWithKnownHash() {
        val apkDirPath = apkTracker.createApkDir(Sha1Hash.max())
        Assert.assertTrue(apkDirPath.exists())
        Assert.assertTrue(apkDirPath.isDirectory())
        Assert.assertTrue(apkDirPath.name == Sha1Hash.max().toHex())
    }

    fun setupDefaultApk(): Path {
        val apkDirPath = apkTracker.createApkDir()
        Assert.assertTrue(searchApkPath.exists())
        val newApkLoc = apkDirPath.resolve(searchApkPath.name)
        searchApkPath.copyTo(newApkLoc)
        Assert.assertTrue(newApkLoc.exists())
        return apkTracker.setHashKnown(apkDirPath)
    }

    @Test
    fun setHashKnown() {
        val apkDirPath = apkTracker.createApkDir()
        Assert.assertTrue(searchApkPath.exists())
        val newApkLoc = apkDirPath.resolve(searchApkPath.name)
        searchApkPath.copyTo(newApkLoc)

        Assert.assertTrue(newApkLoc.exists())
        val finalApkLoc = apkTracker.setHashKnown(apkDirPath)
        Assert.assertFalse(apkDirPath.exists())
        Assert.assertTrue((finalApkLoc.isReadable()))
    }

    @Test
    fun getApkByHash() {
        val apkLoc = setupDefaultApk()

        val hash = Sha1Hash(sha1(apkLoc.readBytes()))
        val actual = apkTracker.getApkByHash(hash)
        Assert.assertEquals(actual, apkLoc)
    }

    @Test
    fun getApkByName() {
        val apkLoc = setupDefaultApk()

        val actual = apkTracker.getApkByName(DEFAULT_APK)
        Assert.assertEquals(actual, apkLoc)
    }

    @Test
    fun exists() {
        val hash = Sha1Hash(sha1(searchApkPath.readBytes()))
        Assert.assertFalse(apkTracker.exists(hash))
        setupDefaultApk()
        Assert.assertTrue(apkTracker.exists(hash))
    }

    @Test
    fun deleteAPK() {
        val apkLoc = setupDefaultApk()
        val hash = Sha1Hash(sha1(apkLoc.readBytes()))

        Assert.assertTrue(apkTracker.exists(hash))
        apkTracker.deleteAPK(hash)
        Assert.assertFalse(apkTracker.exists(hash))
    }

    @Test
    fun listNames() {
        Assert.assertEquals(0, apkTracker.listNames().size)
        setupDefaultApk()
        Assert.assertEquals(1, apkTracker.listNames().size)
    }
}
