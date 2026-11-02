package io.github.warleysr.dechainer.utils

import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader

class ShizukuRunner {
    interface CommandResultListener {
        /*
        * Runs after the command executes, at least partially. Does not run with 'done' if the command throws an error.
        * output: The output of the command
        * done: If the command has finished executing
         */
        fun onCommandResult(output: String, done: Boolean) {}

        /*
        * Runs if the command throws an error.
        * error: The error message
         */
        fun onCommandError(error: String) {}
    }

    data class ProcessResult(
        val exitCode: Int,
        val output: String,
        val error: String
    )

    companion object {
        fun exec(vararg command: String): ProcessResult? {
            val binder = Shizuku.getBinder()
            if (binder == null || !binder.pingBinder()) return null
            val cmdArray = if (command.size == 1 && command[0].contains(" ")) {
                arrayOf("sh", "-c", command[0])
            } else {
                command
            }
            return try {
                if (Shizuku.isPreV11()) return null
                val service = IShizukuService.Stub.asInterface(binder) ?: return null
                val process = service.newProcess(cmdArray, null, null) ?: return null
                var error = ""
                val errorReader = Thread {
                    error = try {
                        BufferedReader(InputStreamReader(FileInputStream(process.errorStream.fileDescriptor))).use {
                            it.readText()
                        }
                    } catch (_: Exception) { "" }
                }
                errorReader.start()
                val output = try {
                    BufferedReader(InputStreamReader(FileInputStream(process.inputStream.fileDescriptor))).use {
                        it.readText()
                    }
                } catch (_: Exception) { "" }
                errorReader.join()
                val code = process.waitFor()
                ProcessResult(code, output.trim(), error.trim())
            } catch (_: Exception) {
                null
            }
        }

        fun command(command: String, listener: CommandResultListener, lineBundle: Int = Integer.MAX_VALUE) {
            println("Command: $command")

            try {
                val process = IShizukuService.Stub.asInterface(Shizuku.getBinder())
                    .newProcess(arrayOf("sh", "-c", command), null, null)

                val reader = BufferedReader(
                    InputStreamReader(FileInputStream(process.inputStream.fileDescriptor))
                )
                val err = BufferedReader(
                    InputStreamReader(FileInputStream(process.errorStream.fileDescriptor))
                )
                val output = StringBuilder()
                val errordata = StringBuilder()
                var line: String?
                var linecount = 0

                while (reader.readLine().also { line = it } != null) {
                    linecount++
                    output.append(line).append("\n")
                    if (linecount == lineBundle) {
                        linecount = 0
                        listener.onCommandResult(output.toString(), false)
                    }
                }
                reader.close()

                while (err.readLine().also { line = it } != null) {
                    errordata.append(line).append("\n")
                }
                err.close()

                if (errordata.isNotBlank()) {
                    listener.onCommandError(errordata.toString())
                } else
                    listener.onCommandResult(output.toString(), true)

                process.waitFor()

            } catch (e: Exception) {
                listener.onCommandError(e.message ?: "No Shizuku")
            }
        }
    }
}
