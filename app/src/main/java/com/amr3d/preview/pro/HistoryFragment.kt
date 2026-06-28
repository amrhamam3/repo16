package com.amr3d.preview.pro

import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import java.io.File

/**
 * تاريخ الملفات المفتوحة - نسخة مطورة ومحمية من الانهيارات مع تنظيف تلقائي
 */
class HistoryFragment : Fragment() {

    interface OnFileSelectedListener {
        fun onFileSelected(file: File)
    }
    var fileSelectedListener: OnFileSelectedListener? = null

    private lateinit var listView: ListView
    private lateinit var emptyText: TextView
    private lateinit var btnClear: Button

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_history, container, false)
        listView = view.findViewById(R.id.historyList)
        emptyText = view.findViewById(R.id.emptyHistory)
        btnClear = view.findViewById(R.id.btnClearHistory)

        setupHistoryList()

        btnClear.setOnClickListener {
            clearHistory(requireContext())
            showEmptyState()
        }

        return view
    }

    private fun setupHistoryList() {
        val context = requireContext()
        val history = loadHistory(context).toMutableList()

        if (history.isEmpty()) {
            showEmptyState()
        } else {
            emptyText.visibility = View.GONE
            listView.visibility = View.VISIBLE
            
            // إصلاح الـ Adapter: استخدام SimpleAdapter لربط العنوان (اسم الملف) والعنوان الفرعي (المسار) بشكل سليم في simple_list_item_2
            val data = history.map { path ->
                val file = File(path)
                mapOf("title" to file.name, "subtitle" to file.parent.orEmpty())
            }

            val adapter = SimpleAdapter(
                context,
                data,
                android.R.layout.simple_list_item_2,
                arrayOf("title", "subtitle"),
                intArrayOf(android.R.id.text1, android.R.id.text2)
            )
            
            listView.adapter = adapter
            
            listView.setOnItemClickListener { _, _, position, _ ->
                val filePath = history[position]
                val file = File(filePath)
                
                if (file.exists()) {
                    fileSelectedListener?.onFileSelected(file)
                } else {
                    // التعديل: تنظيف تلقائي وذكي للملف المفقود من السجل والذاكرة لتجنب تكرار الخطأ للمستخدم
                    Toast.makeText(context, "الملف لم يعد موجوداً، تم حذفه من السجل", Toast.LENGTH_SHORT).show()
                    removeFromHistory(context, filePath)
                    setupHistoryList() // إعادة تحديث بناء القائمة فوراً
                }
            }
        }
    }

    private fun showEmptyState() {
        emptyText.visibility = View.VISIBLE
        listView.visibility = View.GONE
        listView.adapter = null
    }

    companion object {
        private const val PREF_KEY = "file_history"
        private const val MAX_HISTORY = 20

        fun addToHistory(context: Context, path: String) {
            val prefs = context.getSharedPreferences("amr3d_prefs", Context.MODE_PRIVATE)
            val history = loadHistory(context).toMutableList()
            history.remove(path)
            history.add(0, path)
            if (history.size > MAX_HISTORY) history.removeAt(history.lastIndex)
            prefs.edit().putString(PREF_KEY, history.joinToString("|")).apply()
        }

        /**
         * دالة مضافة لحذف ملف بعينه من السجل عند اكتشاف تلفه أو عدم وجوده
         */
        fun removeFromHistory(context: Context, path: String) {
            val prefs = context.getSharedPreferences("amr3d_prefs", Context.MODE_PRIVATE)
            val history = loadHistory(context).toMutableList()
            if (history.remove(path)) {
                prefs.edit().putString(PREF_KEY, history.joinToString("|")).apply()
            }
        }

        fun loadHistory(context: Context): List<String> {
            val prefs = context.getSharedPreferences("amr3d_prefs", Context.MODE_PRIVATE)
            val str = prefs.getString(PREF_KEY, "") ?: ""
            return if (str.isEmpty()) emptyList() else str.split("|").filter { it.isNotEmpty() }
        }

        fun clearHistory(context: Context) {
            context.getSharedPreferences("amr3d_prefs", Context.MODE_PRIVATE)
                .edit().remove(PREF_KEY).apply()
        }
    }
}
