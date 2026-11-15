package com.kkdex.desktop

import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Debian ARM64 userland hosted privately by KK DEX.
 * PRoot supplies a fake root inside Debian only; Android itself remains unrooted.
 */
class LinuxRuntimeController(private val context: Context) {
    private val linuxHome = File(context.filesDir, "linux")
    private val rootfs = File(linuxHome, "debian")
    private val nativeDir = File(context.applicationInfo.nativeLibraryDir)
    private val proot = File(nativeDir, "libproot.so")
    private val loader = File(nativeDir, "libproot-loader.so")
    private val talloc = File(nativeDir, "libtalloc.so")
    private val tempDir = File(context.cacheDir, "proot-tmp")
    @Volatile private var currentDirectory = "/root"
    @Volatile private var previousDirectory = "/root"

    fun isInstalled(): Boolean =
        File(rootfs, "usr/bin/apt-get").isFile &&
        File(rootfs, "bin/bash").exists() &&
        proot.isFile && loader.isFile && talloc.isFile

    fun status(): JSONObject {
        val ready = isInstalled()
        return result(
            if (ready) {
                "Debian 13.7 ARM64 is installed. PRoot is ready. The Linux prompt runs as fake root inside Debian; Android remains unrooted.\nCurrent Linux directory: $currentDirectory\nTry: apt-get update"
            } else {
                "Debian is not installed yet. Runtime binaries: " +
                    if (proot.isFile && loader.isFile && talloc.isFile) "present" else "missing" +
                    ". Run: linux install"
            },
            0,
            "KK DEX Linux status",
        )
    }

    fun install(): JSONObject {
        if (!Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }) {
            throw IllegalStateException("The Debian runtime currently requires a 64-bit ARM Android device (arm64-v8a).")
        }
        if (!proot.canExecute() || !loader.isFile || !talloc.isFile) {
            throw IllegalStateException("The Android ARM64 PRoot runtime is missing or cannot execute from the app's native library directory.")
        }
        if (isInstalled()) return status()

        linuxHome.mkdirs()
        val archive = File(context.cacheDir, "kkdex-debian-13.7-arm64-rootfs.tar.gz")
        val staging = File(linuxHome, "debian.installing")
        if (staging.exists()) staging.deleteRecursively()
        staging.mkdirs()

        try {
            downloadAndVerify(archive)
            val extract = ProcessBuilder(
                "/system/bin/toybox", "tar", "-xzf", archive.absolutePath, "-C", staging.absolutePath,
            ).redirectErrorStream(true).start()
            val extractOutput = ByteArrayOutputStream()
            val drain = Thread {
                extract.inputStream.use { input ->
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count <= 0) break
                        if (total < MAX_OUTPUT_BYTES) {
                            val accepted = minOf(count, MAX_OUTPUT_BYTES - total)
                            extractOutput.write(buffer, 0, accepted)
                            total += accepted
                        }
                    }
                }
            }.apply { isDaemon = true; start() }
            val finished = extract.waitFor(EXTRACT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                extract.destroyForcibly()
                throw IllegalStateException("Debian extraction exceeded its time limit.")
            }
            drain.join(3000)
            if (extract.exitValue() != 0) {
                throw IllegalStateException("Android could not unpack the Debian filesystem: " + extractOutput.toString("UTF-8"))
            }

            if (!File(staging, "usr/bin/apt-get").isFile ||
                !File(staging, "usr/bin/dpkg").isFile ||
                !File(staging, "bin/bash").exists()
            ) {
                throw IllegalStateException("The extracted archive did not contain the expected Debian ARM64 tools.")
            }
            listOf("dev", "proc", "sys", "tmp", "root", "run").forEach {
                File(staging, it).mkdirs()
            }
            File(staging, "tmp").setReadable(true, false)
            File(staging, "tmp").setWritable(true, false)
            File(staging, "tmp").setExecutable(true, false)
            val resolv = File(staging, "etc/resolv.conf")
            resolv.delete()
            resolv.writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\n")
            File(staging, "etc/hostname").writeText("kkdex\n")

            if (rootfs.exists()) rootfs.deleteRecursively()
            if (!staging.renameTo(rootfs)) {
                throw IllegalStateException("Could not move the verified Debian filesystem into persistent app storage.")
            }
            currentDirectory = "/root"
            previousDirectory = "/root"
            archive.delete()
            return result(
                "Debian 13.7 ARM64 is installed in KK DEX private storage.\nPRoot is ready. This is fake root inside Debian only, not Android root.\nNext: apt-get update",
                0,
                "Debian 13.7 · PRoot · fake root",
            )
        } catch (error: Exception) {
            staging.deleteRecursively()
            throw IllegalStateException("Debian installation failed: " + (error.message ?: error.javaClass.simpleName), error)
        } finally {
            archive.delete()
        }
    }

    fun reset(): JSONObject {
        if (rootfs.exists() && !rootfs.deleteRecursively()) {
            throw IllegalStateException("Could not remove the Debian filesystem from KK DEX private storage.")
        }
        currentDirectory = "/root"
        previousDirectory = "/root"
        return result("The Debian filesystem was removed from KK DEX private storage. The Android app and Android system were not changed.", 0, "KK DEX Linux reset")
    }

    fun execute(commandValue: String): JSONObject {
        val command = commandValue.trim()
        if (command.isEmpty()) throw IllegalArgumentException("No Linux command was provided.")
        if (command.length > MAX_COMMAND_LENGTH) throw IllegalArgumentException("Linux command is too long.")
        if (!isInstalled()) throw IllegalStateException("Debian is not installed. Run: linux install")

        if (command == "linux status") return status()
        if (command == "linux reset") return reset()
        if (command == "linux help" || command == "linux") {
            return result("Linux commands:\n  linux install  Download and install Debian 13.7 ARM64\n  linux status   Check the Debian runtime\n  linux reset    Remove the Debian filesystem\nAfter installation, ordinary terminal commands run inside Debian. Use android <command> for the Android app shell.", 0, "KK DEX Linux help")
        }

        val cd = Regex("^cd(?:\\s+(.+))?$").matchEntire(command)
        if (cd != null) {
            val target = cd.groupValues.getOrNull(1)?.trim().orEmpty()
            val cdCommand = when (target) {
                "", "~" -> "cd /root && pwd"
                "-" -> "cd " + quote(previousDirectory) + " && pwd"
                else -> "cd " + target + " && pwd"
            }
            val changed = runProot(cdCommand)
            if (changed.getInt("exitCode") == 0) {
                val next = changed.optString("stdout").trim().lineSequence().lastOrNull().orEmpty()
                if (next.startsWith("/")) {
                    previousDirectory = currentDirectory
                    currentDirectory = next
                }
            }
            return changed
        }
        return runProot(command)
    }

    private fun runProot(command: String): JSONObject {
        val temp = tempDir.apply { mkdirs() }
        val guestCommand = "cd " + quote(currentDirectory) + " && " + command
        val args = listOf(
            proot.absolutePath,
            "--kill-on-exit", "--link2symlink", "--sysvipc", "-0",
            "-r", rootfs.absolutePath,
            "-b", "/dev", "-b", "/proc", "-b", "/sys",
            "-w", currentDirectory,
            "/usr/bin/env", "-i",
            "HOME=/root",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "TERM=xterm-256color",
            "LANG=C.UTF-8",
            "LC_ALL=C.UTF-8",
            "/bin/bash", "-lc", guestCommand,
        )
        val process = ProcessBuilder(args)
            .directory(context.filesDir)
            .apply {
                environment()["PROOT_LOADER"] = loader.absolutePath
                environment()["PROOT_TMP_DIR"] = temp.absolutePath
                environment()["TMPDIR"] = temp.absolutePath
                environment()["LD_LIBRARY_PATH"] = nativeDir.absolutePath
            }
            .redirectErrorStream(false)
            .start()
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val outThread = Thread { drain(process.inputStream, stdout) }.apply { isDaemon = true; start() }
        val errThread = Thread { drain(process.errorStream, stderr) }.apply { isDaemon = true; start() }
        val finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!finished) {
            process.destroy()
            if (process.isAlive) process.destroyForcibly()
        }
        outThread.join(2000)
        errThread.join(2000)
        return JSONObject()
            .put("stdout", stdout.toString("UTF-8"))
            .put("stderr", stderr.toString("UTF-8"))
            .put("exitCode", if (finished) process.exitValue() else 124)
            .put("timedOut", !finished)
            .put("backend", "Debian 13.7 · PRoot · fake root (not Android root)")
            .put("workingDirectory", currentDirectory)
    }

    private fun downloadAndVerify(destination: File) {
        val url = URL(ROOTFS_URL)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 180_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "KK-DEX-Development-8")
        }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Debian download returned HTTP " + connection.responseCode)
            }
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            connection.inputStream.use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > ROOTFS_BYTES) throw IllegalStateException("Debian download exceeded the expected archive size.")
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            if (total != ROOTFS_BYTES || hash != ROOTFS_SHA256) {
                destination.delete()
                throw IllegalStateException("Debian archive checksum or size verification failed. The archive was rejected.")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun drain(input: java.io.InputStream, target: ByteArrayOutputStream) {
        input.use { stream ->
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = stream.read(buffer)
                if (count <= 0) break
                if (total < MAX_OUTPUT_BYTES) {
                    val accepted = minOf(count, MAX_OUTPUT_BYTES - total)
                    target.write(buffer, 0, accepted)
                    total += accepted
                }
            }
        }
    }

    private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private fun result(output: String, exitCode: Int, backend: String) = JSONObject()
        .put("stdout", output)
        .put("stderr", "")
        .put("exitCode", exitCode)
        .put("timedOut", false)
        .put("backend", backend)
        .put("workingDirectory", currentDirectory)

    companion object {
        private const val ROOTFS_URL = "https://raw.githubusercontent.com/debuerreotype/docker-debian-artifacts/3b26c3995c7fae47d95ba678632a3520e97054f7/stable/slim/oci/blobs/rootfs.tar.gz"
        private const val ROOTFS_SHA256 = "55da9c092deae2c7b4e2e44fe06074eef1783b000b69f2ed020d776db5358b32"
        private const val ROOTFS_BYTES = 30_214_040L
        private const val COMMAND_TIMEOUT_SECONDS = 300L
        private const val EXTRACT_TIMEOUT_SECONDS = 240L
        private const val MAX_OUTPUT_BYTES = 512 * 1024
        private const val MAX_COMMAND_LENGTH = 8192
    }
}
