package com.srtstudio

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private var videoUri: Uri? = null
    private var tracks: List<SubtitleTrack> = emptyList()
    private lateinit var spinner: Spinner
    private lateinit var tvInfo: TextView
    private lateinit var tvLog: TextView
    private lateinit var btnExtract: Button
    private val pool = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())
    private val activity get() = this

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        spinner = findViewById(R.id.spinner)
        tvInfo = findViewById(R.id.tvInfo)
        tvLog = findViewById(R.id.tvLog)
        btnExtract = findViewById(R.id.btnExtract)

        findViewById<Button>(R.id.btnPick).setOnClickListener { pickVideo() }
        btnExtract.setOnClickListener { extract() }
    }

    private fun pickVideo() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "video/*"
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        i.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        startActivityForResult(i, REQ)
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == REQ && res == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (ignored: Exception) {
            }
            videoUri = uri
            tvInfo.text = "Video: " + uri.lastPathSegment
            scan(uri)
        }
    }

    private fun scan(uri: Uri) {
        tvLog.text = "Đang quét track phụ đề..."
        btnExtract.isEnabled = false
        pool.execute {
            var err: String? = null
            var found: List<SubtitleTrack> = emptyList()
            try {
                found = SubtitleReader(activity).listTracks(uri)
            } catch (e: Exception) {
                err = e.message
            }
            val f = found
            val er = err
            ui.post {
                if (er != null) {
                    tvLog.text = "Lỗi quét: $er"
                    return@post
                }
                tracks = f
                if (f.isEmpty()) {
                    tvLog.text = "Video không có track phụ đề nhúng."
                    spinner.adapter = null
                } else {
                    val names = ArrayList<String>()
                    for (t in f) names.add(t.toString())
                    spinner.adapter = ArrayAdapter(
                        activity,
                        android.R.layout.simple_spinner_dropdown_item,
                        names
                    )
                    btnExtract.isEnabled = true
                    tvLog.text = "Tìm thấy ${f.size} track phụ đề."
                }
            }
        }
    }

    private fun extract() {
        val uri = videoUri ?: return
        val pos = spinner.selectedItemPosition
        if (pos < 0 || pos >= tracks.size) return
        val track = tracks[pos]

        btnExtract.isEnabled = false
        tvLog.text = "Đang trích xuất track #${track.index}..."

        pool.execute {
            var result: String
            var success = false
            try {
                val seg = uri.lastPathSegment ?: "output"
                var name = seg
                val slash = name.lastIndexOf('/')
                if (slash >= 0) name = name.substring(slash + 1)
                val dot = name.lastIndexOf('.')
                if (dot > 0) name = name.substring(0, dot)
                if (name.isBlank()) name = "output"

                val outDir = File(
                    activity.getExternalFilesDir(null),
                    "subtitles"
                )
                if (!outDir.exists()) outDir.mkdirs()

                val outFile = File(outDir, name + "_t" + track.index + ".srt")
                val count = SubtitleReader(activity).extract(uri, track.index, outFile)

                if (count > 0) {
                    val head = outFile.readLines().take(50).joinToString("\n")
                    result = "Trích xuất $count dòng\n" +
                             "File: ${outFile.absolutePath}\n\n" +
                             "---- 50 dòng đầu ----\n$head"
                    success = true
                } else {
                    result = "Track này rỗng hoặc không đọc được."
                }
            } catch (e: Exception) {
                result = "Lỗi: ${e.message}"
            }

            val r = result
            val s = success
            val of = if (s) {
                val seg = uri.lastPathSegment ?: "output"
                var nm = seg
                val slash = nm.lastIndexOf('/')
                if (slash >= 0) nm = nm.substring(slash + 1)
                val dot = nm.lastIndexOf('.')
                if (dot > 0) nm = nm.substring(0, dot)
                File(File(activity.getExternalFilesDir(null), "subtitles"),
                     nm + "_t" + track.index + ".srt").absolutePath
            } else ""

            ui.post {
                btnExtract.isEnabled = true
                tvLog.text = r
                if (s) {
                    Toast.makeText(activity, "Đã lưu:\n$of", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    companion object {
        const val REQ = 101
    }
}
