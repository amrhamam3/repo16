package com.amr3d.preview.pro

import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import java.io.File

/**
 * تاريخ الملفات المفتوحة
 */
class HistoryFragment : Fragment() {

    interface OnFileSelectedListener {
        fun onFileSelected(file: File)
    }
    var fileSelectedListener: OnFileSelectedListener? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_history, container, false)
        val listView = view.findViewById<ListView>(R.id.historyList)
        val emptyText = view.findViewById<TextView>(R.id.emptyHistory)
        val btnClear = view.findViewById<Button>(R.id.btnClearHistory)

        val history = loadHistory(requireContext())

        if (history.isEmpty()) {
            emptyText.visibility = View.VISIBLE
            listView.visibility = View.GONE
        } else {
            emptyText.visibility = View.GONE
            listView.visibility = View.VISIBLE
            val names = history.map {
                val f = File(it)
                "📄 ${f.name}\n${f.parent}"
            }
            listView.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_2, android.R.id.text1, names)
            listView.setOnItemClickListener { _, _, position, _ ->
                val file = File(history[position])
                if (file.exists()) fileSelectedListener?.onFileSelected(file)
                else Toast.makeText(context, "الملف غير موجود", Toast.LENGTH_SHORT).show()
            }
        }

        btnClear.setOnClickListener {
            clearHistory(requireContext())
            emptyText.visibility = View.VISIBLE
            listView.visibility = View.GONE
        }

        return view
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
