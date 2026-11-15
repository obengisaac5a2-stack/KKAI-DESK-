package com.kkdesk

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** Real Android-shell execution inside KK DEX's private app workspace. Not root. */
class TerminalRuntime(context: Context) {
    private val root = File(context.filesDir, "terminal-home").apply { mkdirs() }.canonicalFile

    @Synchronized fun homePath(): String = root.absolutePath

    @Synchronized
    fun changeDirectory(currentPath: String, requestedPath: String): String = try {
        val current = safeDirectory(currentPath)
        val candidate = (if (requestedPath.startsWith("/")) File(requestedPath) else File(current, requestedPath)).canonicalFile
        if (candidate != root && !candidate.path.startsWith(root.path + File.separator)) {
            JSONObject().put("ok", false).put("error", "Access denied: terminal stays inside its private workspace.").toString()
        } else if (!candidate.exists() || !candidate.isDirectory) {
            JSONObject().put("ok", false).put("error", "cd: no such directory: $requestedPath").toString()
        } else {
            JSONObject().put("ok", true).put("cwd", candidate.absolutePath).toString()
        }
    } catch (e: Exception) {
        JSONObject().put("ok", false).put("error", e.message ?: "Could not change directory").toString()
    }

    @Synchronized
    fun execute(command: String, currentPath: String): String {
        if (command.isBlank()) return JSONObject().put("ok", true).put("stdout", "").put("stderr", "").put("exitCode", 0).toString()
        if (command.length > 8192) return failure("Command is too long.", 2)
        var outputFile: File? = null
        return try {
            val working = safeDirectory(currentPath)
            outputFile = File(root, ".terminal-output-${System.nanoTime()}.txt")
            val process = ProcessBuilder("/system/bin/sh", "-c", command)
                .directory(working)
                .redirectErrorStream(true)
                .redirectOutput(outputFile)
                .apply {
                    environment()["HOME"] = root.absolutePath
                    environment()["PWD"] = working.absolutePath
                    environment()["TMPDIR"] = File(root, "tmp").apply { mkdirs() }.absolutePath
                    environment()["TERM"] = "xterm-256color"
                }
                .start()
            val finished = process.waitFor(15, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                process.waitFor(2, TimeUnit.SECONDS)
            }
            val bytes = outputFile.readBytes().let { if (it.size > 131072) it.copyOf(131072) else it }
            val output = String(bytes, Charsets.UTF_8)
            JSONObject()
                .put("ok", true)
                .put("stdout", if (finished) output else output + "\n[KK DEX] Command stopped after 15 seconds.")
                .put("stderr", "")
                .put("exitCode", if (finished) process.exitValue() else 124)
                .put("timedOut", !finished)
                .put("cwd", working.absolutePath)
                .toString()
        } catch (e: Exception) {
            failure(e.message ?: "Command execution failed.", 126)
        } finally {
            outputFile?.delete()
        }
    }

    private fun safeDirectory(path: String): File {
        val candidate = File(path).canonicalFile
        if (candidate != root && !candidate.path.startsWith(root.path + File.separator)) {
            throw SecurityException("Access denied: terminal stays inside its private workspace.")
        }
        if (!candidate.exists() || !candidate.isDirectory) throw IllegalArgumentException("Working directory does not exist.")
        return candidate
    }

    private fun failure(message: String, code: Int): String =
        JSONObject().put("ok", false).put("stderr", message).put("stdout", "").put("exitCode", code).toString()
}
