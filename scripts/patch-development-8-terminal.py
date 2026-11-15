from pathlib import Path
import re
import sys

root = Path(sys.argv[1]).resolve()

def replace_once(path, old, new, label):
    file = root / path
    source = file.read_text()
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one exact match in {path}, found {count}")
    file.write_text(source.replace(old, new, 1))

def patch(path, old, new, label):
    replace_once(path, old, new, label)

p = root / "src/apps/Productivity.tsx"
s = p.read_text()
bt = chr(96)
dollar = chr(36)
old = '''    const raw = command.trim(),
      [cmd, ...args] = raw.split(/\\s+/),
      argument = args.join(" ");
    setHistory((h) => [raw, ...h]);
    setHistoryIndex(-1);
    setCommand("");
    print(''' + bt + dollar + "{prompt} $ " + dollar + "{raw}" + bt + ''', "command");'''
new = '''    const raw = command.trim();
    let [cmd, ...args] = raw.split(/\\s+/);
    let argument = args.join(" ");
    const workspaceMode = cmd.toLowerCase() === "workspace";
    setHistory((h) => [raw, ...h]);
    setHistoryIndex(-1);
    setCommand("");
    const commandPrompt = workspaceMode
      ? "kk@workspace:~" + (path.length ? "/" + path.join("/") : "")
      : prompt;
    print(commandPrompt + " $ " + raw, "command");
    if (workspaceMode) {
      cmd = args.shift() || "help";
      argument = args.join(" ");
    } else if (!["help", "clear", "theme", "open", "native", "shell"].includes(cmd.toLowerCase())) {
      if (!window.KKDexServices?.execute) {
        print("Android shell backend is not connected. The command " + raw + " was not executed. Install and open the KK DEX Android APK for real command execution. Use workspace " + cmd + " for the managed virtual workspace.", "error");
        return;
      }
      setBusy(true);
      try {
        const result = await window.KKDexServices.execute(raw);
        if (result.workingDirectory) setNativeCwd(result.workingDirectory);
        print(result.output, result.exitCode === 0 ? "normal" : "error");
      } catch (error) {
        print(error instanceof Error ? error.message : "Android could not execute this command.", "error");
      } finally {
        setBusy(false);
      }
      return;
    }'''
if s.count(old) != 1:
    raise SystemExit("Default native shell routing anchor was not unique.")
s = s.replace(old, new, 1)

old_cwd = '''  const [cwd, setCwd] = useState("documents");
  const [history, setHistory'''
new_cwd = '''  const [cwd, setCwd] = useState("documents");
  const [nativeCwd, setNativeCwd] = useState("");
  const [history, setHistory'''
if s.count(old_cwd) != 1:
    raise SystemExit("Could not locate native cwd state anchor.")
s = s.replace(old_cwd, new_cwd, 1)

prompt_pattern = re.compile(r'  const prompt = .*;\n')
prompt_new = '''  const prompt = nativeShell
    ? "kk@android:" + (nativeCwd || "~")
    : "kk@workspace:~" + (path.length ? "/" + path.join("/") : "");
'''
s, count = prompt_pattern.subn(prompt_new, s, count=1)
if count != 1:
    raise SystemExit("Could not update the terminal prompt.")

intro_start = s.index("      text: nativeShell")
intro_end = s.index(',\n      kind: "accent"', intro_start)
intro_block = '''      text: nativeShell
        ? 'KK DEX Terminal 1.2\\nAndroid application shell connected. Commands execute on Android as the KK DEX app, not as root.\\nThe current directory is remembered between commands. Python, Node, Git and package managers work only when genuinely installed.\\nUse "workspace <command>" only for KK DEX virtual workspace files.\\nType "help" to get started.'
        : 'KK DEX Terminal 1.2\\nAndroid shell is not connected in this browser preview. Commands are never simulated.\\nUse "workspace <command>" to explore the KK DEX virtual workspace, or install and open the Android APK for real shell execution.\\nType "help" to get started.' '''
s = s[:intro_start] + intro_block + s[intro_end:]

start = s.index('      case "help":')
end = s.index('      case "clear":', start)
help_case = '''      case "help":
        if (workspaceMode) {
          print("KK DEX workspace commands: pwd, ls, cd <folder|..|~>, cat <file>, mkdir <name>, rm <name>, open <app>, echo <text>, date, whoami. These operate on the managed virtual workspace, not Android files.");
          break;
        }
        if (nativeShell) {
          print("ANDROID SHELL CONNECTED\\nCommands run with /system/bin/sh as the KK DEX Android app UID. This is real execution, not root.\\nTry: pwd, ls, cd, mkdir, touch, cat, cp, mv, rm, grep, find, ps, env, df, uname -a, id, whoami, python3 --version, node --version, git --version.\\nThe current directory is remembered between commands. Android may restrict shared and protected storage.\\nUse workspace <command> for KK DEX virtual files.");
        } else {
          print("ANDROID SHELL NOT CONNECTED\\nThis browser preview cannot execute Android commands. No command output is simulated. Install and open the KK DEX APK to use the native shell.\\nUse workspace <command> for KK DEX virtual files.");
        }
        break;
'''
s = s[:start] + help_case + s[end:]
p.write_text(s)

replace_once("src/types.ts",
'''      execute?: (command: string) => Promise<string>;''',
'''      execute?: (command: string) => Promise<{
        output: string;
        workingDirectory?: string;
        exitCode: number;
        timedOut?: boolean;
      }>;''',
"native execute type")

replace_once("src/android/native.ts",
'''        backend: string;
      }>("runCommand", { command });''',
'''        backend: string;
        workingDirectory?: string;
        timedOut?: boolean;
      }>("runCommand", { command });''',
"native result type")

native = root / "src/android/native.ts"
ns = native.read_text()
native_pattern = re.compile(r'      const header = .*;\n      return .*;\n')
native_replacement = '''      const header = "[" + result.backend + (result.exitCode === 0 ? "" : " · exit " + result.exitCode) + "]";
      return {
        output: [header, output || "(no output)", result.timedOut ? "[Command timed out]" : ""].filter(Boolean).join(String.fromCharCode(10)),
        workingDirectory: result.workingDirectory,
        exitCode: result.exitCode,
        timedOut: result.timedOut,
      };
'''
ns, count = native_pattern.subn(native_replacement, ns, count=1)
if count != 1:
    raise SystemExit("Could not update native terminal result adapter.")
native.write_text(ns)

replace_once("src/apps/Productivity.tsx",
'''          print(await window.KKDexServices.execute(argument));''',
'''          const result = await window.KKDexServices.execute(argument);
          if (result.workingDirectory) setNativeCwd(result.workingDirectory);
          print(result.output, result.exitCode === 0 ? "normal" : "error");''',
"explicit native output")

replace_once("src/apps/Productivity.tsx",
'''            print(await window.KKDexServices.execute(raw));''',
'''            const result = await window.KKDexServices.execute(raw);
            if (result.workingDirectory) setNativeCwd(result.workingDirectory);
            print(result.output, result.exitCode === 0 ? "normal" : "error");''',
"default native output")

# Convert any remaining legacy direct shell print calls to the structured result's text.
legacy_shell_print = re.compile("print\\\\(\\\\s*await window\\\\.KKDexServices\\\\.execute\\\\(([^)]*)\\\\)\\\\s*\\\\);")
s, legacy_count = legacy_shell_print.subn(
    lambda match: "print((await window.KKDexServices.execute(" + match.group(1) + ")).output);",
    s,
)
print(f"Updated {legacy_count} remaining legacy shell-output call(s).")

controller = root / "android/app/src/main/java/com/kkdex/desktop/TerminalController.kt"
source = controller.read_text()
class_start = source.index("class TerminalController")
drain_start = source.index("    private fun drain", class_start)
new_class = r'''class TerminalController(private val context: Context) {
    private val runtimeNames = listOf("python3", "python", "node", "git", "toybox", "unzip")
    private val workspaceRoot = context.filesDir.canonicalFile
    @Volatile private var workingDirectory = workspaceRoot
    @Volatile private var previousDirectory = workspaceRoot

    @Synchronized
    fun run(commandValue: String): JSONObject {
        val command = commandValue.trim()
        if (command.isEmpty()) throw IllegalArgumentException("No command was provided.")
        if (command.length > MAX_COMMAND_LENGTH) {
            throw IllegalArgumentException("Command exceeds the limit of " + MAX_COMMAND_LENGTH + " characters.")
        }
        val started = System.currentTimeMillis()
        handleDirectoryChange(command)?.let {
            return it.put("durationMs", System.currentTimeMillis() - started)
        }
        val process = ProcessBuilder("/system/bin/sh", "-c", command)
            .directory(workingDirectory)
            .apply {
                environment()["HOME"] = workspaceRoot.absolutePath
                environment()["PWD"] = workingDirectory.absolutePath
                environment()["TMPDIR"] = context.cacheDir.absolutePath
                environment()["TERM"] = "xterm-256color"
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
            outThread.join(500)
            errThread.join(500)
        } else {
            outThread.join(2000)
            errThread.join(2000)
        }
        return JSONObject()
            .put("stdout", stdout.text())
            .put("stderr", stderr.text())
            .put("exitCode", if (finished) process.exitValue() else 124)
            .put("timedOut", !finished)
            .put("backend", "Android app UID shell · not root")
            .put("durationMs", System.currentTimeMillis() - started)
            .put("workingDirectory", workingDirectory.absolutePath)
    }

    private fun handleDirectoryChange(command: String): JSONObject? {
        val match = Regex("^cd(?:\\s+(.+))?$").matchEntire(command) ?: return null
        val rawTarget = match.groupValues.getOrNull(1)?.trim().orEmpty()
        val target = if (rawTarget.length >= 2 &&
            ((rawTarget.first() == '\'' && rawTarget.last() == '\'') ||
                (rawTarget.first() == '"' && rawTarget.last() == '"'))
        ) rawTarget.substring(1, rawTarget.length - 1) else rawTarget
        val candidate = when (target) {
            "", "~" -> workspaceRoot
            "-" -> previousDirectory
            else -> {
                val path = if (target.startsWith("~/")) {
                    File(workspaceRoot, target.removePrefix("~/"))
                } else {
                    File(target).let { if (it.isAbsolute) it else File(workingDirectory, target) }
                }
                path.canonicalFile
            }
        }
        val insideWorkspace = candidate == workspaceRoot ||
            candidate.path.startsWith(workspaceRoot.path + File.separator)
        if (!insideWorkspace) {
            return directoryResult("cd: access denied; the terminal working directory must remain inside the KK DEX private workspace.", 1)
        }
        if (!candidate.exists() || !candidate.isDirectory) {
            return directoryResult("cd: no such directory: " + rawTarget, 1)
        }
        previousDirectory = workingDirectory
        workingDirectory = candidate
        return directoryResult("", 0)
    }

    private fun directoryResult(error: String, exitCode: Int): JSONObject =
        JSONObject()
            .put("stdout", "")
            .put("stderr", error)
            .put("exitCode", exitCode)
            .put("backend", "Android app UID shell · not root")
            .put("workingDirectory", workingDirectory.absolutePath)

    fun detectRuntimes(): JSONObject {
        val result = JSONObject()
        runtimeNames.forEach { name ->
            val located = runCatching { run("command -v $name 2>/dev/null") }.getOrNull()
            val path = located?.getString("stdout")?.trim()?.lineSequence()?.firstOrNull { it.isNotBlank() }
            if (path.isNullOrBlank()) {
                result.put(name, JSONObject.NULL)
            } else {
                val version = runCatching {
                    val command = if (name == "toybox") "toybox --version" else "$name --version 2>/dev/null"
                    run(command).getString("stdout").trim().lineSequence().firstOrNull()
                }.getOrNull()
                result.put(name, if (version.isNullOrBlank()) path else "$path · $version")
            }
        }
        return result
    }

'''
controller.write_text(source[:class_start] + new_class + source[drain_start:])

patch("tests/desktop.spec.ts",
'''test("Terminal executes workspace operations, not fake Android commands", async ({
  page,
}) => {
  await launch(page, "terminal");
  const input = page.getByRole("textbox", { name: "Terminal command" });
  await input.fill("pwd");
  await input.press("Enter");
  await expect(page.locator(".terminal-output")).toContainText(
    "workspace:/Documents",
  );
  await input.fill("ls");
  await input.press("Enter");
  await expect(page.locator(".terminal-output")).toContainText(
    "Getting started.md",
  );
  await input.fill("uname -a");
  await input.press("Enter");
  await expect(page.locator(".terminal-output")).toContainText(
    "Unknown workspace command: uname",
  );
  await input.fill("native whoami");
  await input.press("Enter");
  await expect(page.locator(".terminal-output")).toContainText(
    "No Android shell backend is connected",
  );
});''',
'''test("Terminal keeps virtual workspace commands explicit and never fakes Android shell output", async ({
  page,
}) => {
  await launch(page, "terminal");
  const input = page.getByRole("textbox", { name: "Terminal command" });
  await input.fill("workspace pwd");
  await input.press("Enter");
  await expect(page.locator(".terminal-output")).toContainText("workspace:/Documents");
  await input.fill("workspace ls");
  await input.press("Enter");
  await expect(page.locator(".terminal-output")).toContainText("Getting started.md");
  await input.fill("uname -a");
  await input.press("Enter");
  await expect(page.locator(".terminal-output")).toContainText("Android shell backend is not connected");
  await input.fill("native whoami");
  await input.press("Enter");
  await expect(page.locator(".terminal-output")).toContainText("Android shell backend is not connected");
});''',
"desktop terminal test")

patch("tests/android-native.spec.ts",
'''  await input.fill("native printf genuine");''',
'''  await input.fill("printf genuine");''',
"native terminal default command test")


# Make the terminal usable on Android phones: explicit Run control and IME action.
label_pos = s.find('aria-label="Terminal command"')
if label_pos < 0:
    raise SystemExit("Could not locate the terminal command field label.")
input_start = s.rfind("<input", 0, label_pos)
if input_start < 0:
    raise SystemExit("Could not locate the terminal command input element.")
input_end = s.find("/>", label_pos)
if input_end < 0:
    raise SystemExit("Could not locate the end of the terminal command input element.")
input_end += 2
input_tag = s[input_start:input_end]
if "enterKeyHint=" not in input_tag:
    input_tag = input_tag[:-2] + ' enterKeyHint="go" inputMode="text" autoCapitalize="off" autoCorrect="off" />'
run_button = """
      <button
        type="button"
        className="terminal-run-button"
        aria-label="Run command"
        onClick={() => {
          const commandInput = document.querySelector<HTMLInputElement>('input[aria-label="Terminal command"]');
          if (!commandInput) return;
          commandInput.focus();
          commandInput.dispatchEvent(new KeyboardEvent("keydown", {
            key: "Enter",
            code: "Enter",
            bubbles: true,
          }));
        }}
      >
        Run
      </button>"""
s = s[:input_start] + input_tag + run_button + s[input_end:]
css = root / "src/index.css"
css.write_text(css.read_text().rstrip() + """

/* Development 8: reliable phone terminal controls and Android keyboard behavior. */
.terminal-run-button {
  min-width: 72px;
  min-height: 46px;
  flex: 0 0 auto;
  border: 1px solid var(--border, rgba(255,255,255,.18));
  border-radius: 10px;
  background: var(--accent, #fff);
  color: #111;
  font: inherit;
  font-weight: 700;
  padding: 0 16px;
  cursor: pointer;
  touch-action: manipulation;
}
.terminal-run-button:focus-visible {
  outline: 2px solid currentColor;
  outline-offset: 2px;
}
input[aria-label="Terminal command"] {
  min-width: 0;
  min-height: 46px;
  font-size: 16px !important;
  touch-action: manipulation;
}
@media (max-width: 700px) {
  .terminal-run-button {
    min-width: 84px;
    min-height: 52px;
    border-radius: 12px;
    font-size: 16px;
  }
  input[aria-label="Terminal command"] {
    min-height: 52px;
    padding-inline: 14px;
  }
}
""")
p.write_text(s)

print("Development 8 Terminal patch applied: real Android shell is the default, cd persists, and virtual workspace commands use an explicit prefix.")
