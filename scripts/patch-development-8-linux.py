from pathlib import Path
import re
import shutil
import sys

if len(sys.argv) != 3:
    raise SystemExit("usage: patch-development-8-linux.py PROJECT_ROOT LINUX_CONTROLLER_SOURCE")

root = Path(sys.argv[1]).resolve()
controller_source = Path(sys.argv[2]).resolve()
if not controller_source.is_file():
    raise SystemExit(f"Missing Linux runtime controller source: {controller_source}")

def replace_once(path, old, new, label):
    file = root / path
    source = file.read_text()
    if source.count(old) != 1:
        raise SystemExit(f"{label}: expected exactly one match in {path}, found {source.count(old)}")
    file.write_text(source.replace(old, new, 1))

# Add the Debian/PRoot controller as a native Android source file.
native_dir = root / "android/app/src/main/java/com/kkdex/desktop"
native_dir.mkdir(parents=True, exist_ok=True)
shutil.copyfile(controller_source, native_dir / "LinuxRuntimeController.kt")

terminal = root / "android/app/src/main/java/com/kkdex/desktop/TerminalController.kt"
source = terminal.read_text()
anchor = "class TerminalController(private val context: Context) {"
if source.count(anchor) != 1:
    raise SystemExit("Could not find the Development 8 TerminalController class.")
source = source.replace(anchor, anchor + "\n    private val linux = LinuxRuntimeController(context)", 1)
method_anchor = "    @Synchronized\n    fun run(commandValue: String): JSONObject {"
if source.count(method_anchor) != 1:
    raise SystemExit("Could not find the exact Development 8 Android shell method.")
dispatcher = """    fun run(commandValue: String): JSONObject {
        val command = commandValue.trim()
        if (command.isEmpty()) throw IllegalArgumentException("No command was provided.")
        when (command.lowercase()) {
            "linux install" -> return linux.install()
            "linux status" -> return linux.status()
            "linux reset" -> return linux.reset()
            "linux help", "linux" -> return linux.execute("linux help")
        }
        val explicitAndroid = listOf("android ", "shell ", "native ")
            .firstOrNull { command.startsWith(it, ignoreCase = true) }
        if (explicitAndroid != null) {
            return runAndroidShell(command.substring(explicitAndroid.length))
        }
        return if (linux.isInstalled()) linux.execute(command) else runAndroidShell(command)
    }

    @Synchronized
    private fun runAndroidShell(commandValue: String): JSONObject {"""
source = source.replace(method_anchor, dispatcher, 1)
terminal.write_text(source)

# The terminal's ordinary commands become Debian commands after install.
# The explicit android prefix continues to use the genuine Android app UID shell.
native_ts = root / "src/android/native.ts"
source = native_ts.read_text()
timeout_old = "        pickerMethod ? 300_000 : 60_000,"
timeout_new = """        method === "runCommand"
          ? String(args.command || "").trim().toLowerCase() === "linux install"
            ? 600_000
            : 300_000
          : pickerMethod
            ? 300_000
            : 60_000,"""
if source.count(timeout_old) != 1:
    raise SystemExit("Could not find the native bridge timeout expression.")
source = source.replace(timeout_old, timeout_new, 1)
native_ts.write_text(source)

# Route the shell/native aliases to Android even when Debian becomes the default.
productivity = root / "src/apps/Productivity.tsx"
source = productivity.read_text()
source = source.replace(
    "window.KKDexServices.execute(String(argument))",
    'window.KKDexServices.execute("android " + String(argument))',
)
source = source.replace(
    'Type "help" to get started.',
    'Linux controls: linux install, linux status, linux reset. After installation, ordinary commands run in Debian; use android <command> for Android.\\nType "help" to get started.',
)
source = source.replace(
    "Use workspace <command> for KK DEX virtual files.",
    "Use workspace <command> for KK DEX virtual files.\\nLinux controls: linux install, linux status, linux reset. After installation, ordinary commands run in Debian; use android <command> for Android.",
)
source = source.replace("ANDROID APP SANDBOX", "ANDROID + DEBIAN TERMINAL")
source = source.replace("Android app sandbox", "Android shell + Debian PRoot")
source = source.replace('"kk@android:"', '"kk@terminal:"')
source = source.replace(
    "Waiting for Android app-sandbox command...",
    "Waiting for native terminal command...",
)
if "linux install" not in source:
    raise SystemExit("Terminal help/intro did not receive the Debian commands.")
productivity.write_text(source)

# Extract native libraries from the APK so Android places them in nativeLibraryDir,
# where the PRoot executable and its loader can be launched by the app.
gradle = root / "android/app/build.gradle.kts"
source = gradle.read_text()
packaging_anchor = "    packaging {\n        resources.excludes += setOf("
if source.count(packaging_anchor) != 1:
    raise SystemExit("Could not find the Android packaging block in Development 8 Gradle.")
source = source.replace(
    packaging_anchor,
    "    packaging {\n        jniLibs {\n            useLegacyPackaging = true\n        }\n        resources.excludes += setOf(",
    1,
)
gradle.write_text(source)

assets = root / "android/app/src/main/assets/third-party-licenses"
assets.mkdir(parents=True, exist_ok=True)
(assets / "THIRD_PARTY_NOTICES.txt").write_text(
    "KK DEX Development 8 includes an Android ARM64 PRoot runtime.\n"
    "PRoot: GPL-2.0; source: https://github.com/termux/proot\n"
    "talloc: LGPL-3.0-or-later; source: https://www.samba.org/ftp/talloc/\n"
    "libandroid-shmem: BSD-3-Clause; source: https://github.com/termux/libandroid-shmem\n"
    "The corresponding license texts are in this directory. The Debian userland is downloaded separately from the pinned Debian artifact URL and verified by SHA-256 before extraction.\n"
)
print("Development 8 now has a Debian 13.7 PRoot terminal path, Android-shell escape prefix, extended install timeout, and native-library packaging.")
