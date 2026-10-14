package com.srtstudio

import java.io.File
import java.util.Locale

object SrtWriter {
    private val sb = StringBuilder()
    private var idx = 1

    fun reset() {
        sb.setLength(0)
        idx = 1
    }

    fun add(startUs: Long, endUs: Long, text: String) {
        if (text.isBlank()) return
        sb.append(idx).append('\n')
        sb.append(fmt(startUs)).append(" --> ").append(fmt(endUs)).append('\n')
        sb.append(text.trim()).append("\n\n")
        idx++
    }

    fun save(file: File): Int {
        file.parentFile?.mkdirs()
        file.writeText(sb.toString(), Charsets.UTF_8)
        return idx - 1
    }

    private fun fmt(us: Long): String {
        val ms = if (us < 0) 0L else us / 1000
        val h = ms / 3600000
        val m = (ms % 3600000) / 60000
        val s = (ms % 60000) / 1000
        val mm = ms % 1000
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", h, m, s, mm)
    }
}
