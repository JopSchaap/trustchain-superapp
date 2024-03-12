package nl.tudelft.trustchain.foc.util

import com.frostwire.jlibtorrent.Sha1Hash
import nl.tudelft.ipv8.util.sha1
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.createDirectory
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.forEachDirectoryEntry
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.jvm.optionals.getOrElse

/**
 * Used to get the correct path objects for apk files.
 *
 * This class handles storing files in the correct directory and allows obtaining the files.
 * The typical location of a file is as follows: "[baseDir]/apk_hash/apk_name.apk".
 */
class ApkTracker(private val baseDir: Path) {
    // The directory in which all the apks will be stored
    private val noHashDir = baseDir
    val noHashPrefix = "no_hash_known_"

    init {
        Files.createDirectories(baseDir)
        Files.createDirectories(noHashDir)
    }

    /**
     * Creates a new apk directory in which the new apk should be saved.
     *
     * @param apkHash: The hash of the newly to save apk.
     */
    fun createApkDir(apkHash: Sha1Hash): Path {
        val hashString = apkHash.toHex()
        val newDir = baseDir.resolve(hashString)
        newDir.createDirectory()
        return newDir
    }

    /**
     * Creates a new apk directory for an apk of which we do not yet know the has.
     */
    fun createApkDir(): Path {
        val newDir = Files.createTempDirectory(baseDir, noHashPrefix)
        return newDir
    }

    /**
     * Function to remove entire directory and all files contained within.
     *
     * @return True iff the directory existed and is now deleted.
     */
    private fun deleteRecursivelyIfExists(dir: Path): Boolean {
        return if (dir.isDirectory()) {
            for (subFile in Files.newDirectoryStream(dir)) {
                subFile.deleteIfExists()
            }
            return dir.deleteIfExists()
        } else if (dir.exists()) {
            return dir.deleteIfExists()
        } else {
            false
        }
    }

    /**
     * Sets the hash of the previously unknown hash apk.
     *
     * Should only be used after first creating a directory using [createApkDir].
     * @param noHashPath The old directory path.
     * @return True iff the hashed directory previously existed and thus was overridden.
     */
    fun setHashKnown(noHashPath: Path): Path {
        val apkFile =
            Files.find(
                noHashPath,
                1,
                { file: Path, _ -> file.name.endsWith(ExtensionUtils.APK_DOT_EXTENSION) }
            ).findFirst().getOrElse {
                throw FileNotFoundException("Could not find apk file in the downloaded directory ${noHashPath.absolute()}")
            }
        val hash = Sha1Hash(sha1(apkFile.readBytes()))
        val newDir = baseDir.resolve(hash.toHex())
        deleteRecursivelyIfExists(newDir)
        Files.move(noHashPath, newDir)
        return newDir.resolve(apkFile.name)
    }

    private fun findApkInDir(dir: Path): Path {
        if (!dir.exists()) throw FileNotFoundException()
        for (subFile in Files.newDirectoryStream(dir)) {
            if (subFile.name.endsWith(ExtensionUtils.APK_DOT_EXTENSION)) {
                return subFile
            }
        }
        throw FileNotFoundException()
    }

    /**
     * Returns the path to the apk file by its hash.
     *
     * @param apkHash The hash of the file we are trying to find.
     * @return A path object referencing the apk, with hash equal to [apkHash].
     */
    fun getApkByHash(apkHash: Sha1Hash): Path {
        val apkDir = baseDir.resolve(apkHash.toHex())
        return findApkInDir(apkDir)
    }

    /**
     * Returns the path to the apk file by its name.
     *
     * Please note that this will return the first found apk that matches its name and thus is better replaced with [getApkByHash], if multiple apks with the same name exists.
     * @param apkName The name of the apk, if no extension is given will search for "[apkName].apk".
     * @return A path object referencing the apk with name equal to [apkName].
     */
    fun getApkByName(apkName: String): Path {
        var apkNameWithSuffix = apkName
        if (!apkNameWithSuffix.endsWith(ExtensionUtils.APK_DOT_EXTENSION)) {
            apkNameWithSuffix += ExtensionUtils.APK_DOT_EXTENSION
        }
        val iter = Files.find(baseDir, 2, { subFile: Path, _ -> subFile.name == apkNameWithSuffix })
        return iter.findFirst().getOrElse { throw FileNotFoundException() }
    }

    fun exists(hash: Sha1Hash): Boolean {
        return try {
            getApkByHash(hash).exists()
        } catch (f: FileNotFoundException) {
            false
        }
    }

    fun deleteAPK(apkHash: Sha1Hash): Boolean {
        return deleteRecursivelyIfExists(baseDir.resolve(apkHash.toHex()))
    }

    fun listNames(): ArrayList<String> {
        val result = ArrayList<String>()
        baseDir.forEachDirectoryEntry {
            try {
                result.add(findApkInDir(it).name)
            } catch (f: FileNotFoundException) {
                f.printStackTrace()
            }
        }
        return result
    }
}
