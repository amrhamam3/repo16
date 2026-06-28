package com.amr3d.preview.pro

import android.os.Bundle
import android.os.Environment
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import java.io.File

/**
 * مستعرض الملفات - يعرض STL و DXF فقط
 */
class FileBrowserFragment : Fragment() {

    private lateinit var listView: ListView
    private lateinit var currentPathText: TextView
    private lateinit var btnBack: ImageButton
    private var currentPath = Environment.getExternalStorageDirectory()
    private val supportedExtensions = setOf("stl", "dxf")

    interface OnFileSelectedListener {
        fun onFileSelected(file: File)
    }

    var fileSelectedListener: OnFileSelectedListener? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_file_browser, container, false)
        listView = view.findViewById(R.id.fileList)
        currentPathText = view.findViewById(R.id.currentPath)
        btnBack = view.findViewById(R.id.btnBackDir)

        btnBack.setOnClickListener {
            currentPath.parentFile?.let {
                currentPath = it
                loadDirectory(currentPath)
            }
        }

        loadDirectory(currentPath)
        return view
    }

    private fun loadDirectory(dir: File) {
        currentPathText.text = dir.absolutePath

        val entries = mutableListOf<File>()

        // المجلدات أولاً
        dir.listFiles()
            ?.filter { it.isDirectory && !it.isHidden }
            ?.sortedBy { it.name }
            ?.let { entries.addAll(it) }

        // ثم ملفات STL/DXF فقط
        dir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in supportedExtensions }
            ?.sortedBy { it.name }
            ?.let { entries.addAll(it) }

        val names = entries.map {
            if (it.isDirectory) "📁 ${it.name}"
            else {
                val ext = it.extension.uppercase()
                val size = it.length() / 1024
                "📄 [$ext] ${it.name} (${size}KB)"
            }
        }

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, names)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val file = entries[position]
            if (file.isDirectory) {
                currentPath = file
                loadDirectory(file)
            } else {
                fileSelectedListener?.onFileSelected(file)
            }
        }
    }
}
