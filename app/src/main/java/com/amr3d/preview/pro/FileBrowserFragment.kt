package com.amr3d.preview.pro

import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import java.io.File

/**
 * مستعرض الملفات - يعرض STL و DXF فقط مع دعم الأنظمة الحديثة وطرق العرض المحسنة
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
            // منع الرجوع إلى ما وراء الجذر لتجنب المشاكل الأمنية
            val parent = currentPath.parentFile
            if (parent != null && currentPath.absolutePath != Environment.getExternalStorageDirectory().absolutePath) {
                currentPath = parent
                loadDirectory(currentPath)
            } else {
                Toast.makeText(requireContext(), "أنت في المجلد الرئيسي بالفعل", Toast.LENGTH_SHORT).show()
            }
        }

        // التحقق من الصلاحيات على الأنظمة الحديثة وتنبيه المستخدم
        checkStoragePermissions()

        loadDirectory(currentPath)
        return view
    }

    private fun checkStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Toast.makeText(
                    requireContext(), 
                    "يرجى تفعيل صلاحية الوصول لجميع الملفات من إعدادات النظام ليعمل المستعرض", 
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun loadDirectory(dir: File) {
        currentPathText.text = dir.absolutePath

        val entries = mutableListOf<File>()

        // معالجة الأخطاء في حال عدم القدرة على قراءة المجلد (بسبب الصلاحيات)
        val files = try {
            dir.listFiles()
        } catch (e: SecurityException) {
            Toast.makeText(requireContext(), "لا توجد صلاحية لقراءة هذا المجلد", Toast.LENGTH_SHORT).show()
            null
        }

        files?.let { allFiles ->
            // 1. المجلدات أولاً
            allFiles.filter { it.isDirectory && !it.isHidden }
                .sortedBy { it.name.lowercase() }
                .forEach { entries.add(it) }

            // 2. ثم ملفات STL/DXF فقط
            allFiles.filter { it.isFile && it.extension.lowercase() in supportedExtensions }
                .sortedBy { it.name.lowercase() }
                .forEach { entries.add(it) }
        }

        val names = entries.map { file ->
            if (file.isDirectory) {
                "📁 ${file.name}"
            } else {
                val ext = file.extension.uppercase()
                val formattedSize = formatFileSize(file.length())
                "📄 [$ext] ${file.name} ($formattedSize)"
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

    /**
     * تحسين حساب حجم الملف ديناميكياً ليظهر بـ KB أو MB حسب حجم ملف الـ 3D
     */
    private fun formatFileSize(sizeInBytes: Long): String {
        if (sizeInBytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(sizeInBytes.toDouble()) / Math.log10(1024.0)).toInt()
        val index = digitGroups.coerceAtMost(units.size - 1)
        val size = sizeInBytes / Math.pow(1024.0, index.toDouble())
        return String.format(java.util.Locale.US, "%.1f %s", size, units[index])
    }
}
