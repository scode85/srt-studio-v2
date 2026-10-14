package com.srtstudio

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer

data class SubtitleTrack(
    val index: Int,
    val mime: String,
    val language: String?
) {
    override fun toString(): String {
        val l = language?.takeIf { it.isNotBlank() && it != "und" } ?: "?"
        return "Track #$index  [$l]  $mime"
    }
}

class SubtitleReader(private val ctx: Context) {

    fun listTracks(uri: Uri): List<SubtitleTrack> {
        val ex = MediaExtractor()
        try {
            val pfd = ctx.contentResolver.openFileDescriptor(uri, "r") ?: return emptyList()
            pfd.use { ex.setDataSource(it.fileDescriptor) }

            val out = ArrayList<SubtitleTrack>()
            for (i in 0 until ex.trackCount) {
                val fmt = ex.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (isSub(mime)) {
                    val lang = fmt.getString(MediaFormat.KEY_LANGUAGE)
                    out.add(SubtitleTrack(i, mime, lang))
                }
            }
            return out
        } finally {
            ex.release()
        }
    }

    fun extract(uri: Uri, trackIndex: Int, outFile: File): Int {
        val ex = MediaExtractor()
        try {
            val pfd = ctx.contentResolver.openFileDescriptor(uri, "r") ?: return 0
            pfd.use { ex.setDataSource(it.fileDescriptor) }

            ex.selectTrack(trackIndex)
            val mime = ex.getTrackFormat(trackIndex).getString(MediaFormat.KEY_MIME) ?: ""

            SrtWriter.reset()
            val buf = ByteBuffer.allocate(1 shl 20)

            while (true) {
                buf.clear()
                val size = ex.readSampleData(buf, 0)
                if (size < 0) break

                val startUs = ex.sampleTime
                val durUs = ex.sampleDuration
                val endUs = if (durUs > 0) startUs + durUs else startUs + 2000000L

                buf.position(0)
                buf.limit(size)
                val bytes = ByteArray(size)
                buf.get(bytes)

                SrtWriter.add(startUs, endUs, decode(mime, bytes))

                if (!ex.advance()) break
            }

            return SrtWriter.save(outFile)
        } finally {
            ex.release()
        }
    }

    private fun isSub(mime: String): Boolean {
        val m = mime.lowercase()
        return m.startsWith("text/") ||
                m.contains("subrip") || m.contains("srt") ||
                m.contains("vtt") || m.contains("webvtt") ||
                m.contains("ttml") || m.contains("tx3g") ||
                m.contains("mov_text") || m.contains("subtitle") ||
                m.contains("ssa") || m.contains("ass")
    }

    private fun decode(mime: String, bytes: ByteArray): String {
        val m = mime.lowercase()
        return when {
            m.contains("tx3g") || m.contains("mov_text") -> parseTx3g(bytes)
            m.contains("vtt")  -> cleanVtt(String(bytes, Charsets.UTF_8))
            m.contains("ttml") -> cleanTtml(String(bytes, Charsets.UTF_8))
            else -> String(bytes, Charsets.UTF_8)
        }
    }

    private fun parseTx3g(b: ByteArray): String {
        if (b.size < 2) return ""
        val len = ((b[0].toInt() and 0xFF) shl 8) or (b[1].toInt() and 0xFF)
        return if (len in 1..(b.size - 2)) String(b, 2, len, Charsets.UTF_8) else ""
    }

    private fun cleanVtt(t: String): String {
        val lines = ArrayList<String>()
        for (line in t.lines()) {
            val s = line.trim()
            if (s.isEmpty()) continue
            if (s.startsWith("WEBVTT")) continue
            if (s.contains("-->")) continue
            if (s.matches(Regex("^\\d+$"))) continue
            lines.add(s)
        }
        return lines.joinToString("\n")
    }

    private fun cleanTtml(t: String): String =
        t.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
}
