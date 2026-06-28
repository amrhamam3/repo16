package com.amr3d.preview.pro

import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment

/**
 * صفحة الإعدادات
 */
class SettingsFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        val prefs = requireContext().getSharedPreferences("amr3d_prefs", Context.MODE_PRIVATE)

        // وحدة القياس
        val unitGroup = view.findViewById<RadioGroup>(R.id.unitGroup)
        val savedUnit = prefs.getString("unit", "MM") ?: "MM"
        when (savedUnit) {
            "MM" -> unitGroup.check(R.id.radioMM)
            "CM" -> unitGroup.check(R.id.radioCM)
            "INCH" -> unitGroup.check(R.id.radioInch)
        }
        unitGroup.setOnCheckedChangeListener { _, checkedId ->
            val unit = when (checkedId) {
                R.id.radioMM -> "MM"
                R.id.radioCM -> "CM"
                R.id.radioInch -> "INCH"
                else -> "MM"
            }
            prefs.edit().putString("unit", unit).apply()
        }

        // رابط واتساب
        view.findViewById<TextView>(R.id.tvVersion).text = "الإصدار 3.0\nAmr Hamam 3D © 2026"

        return view
    }
}
