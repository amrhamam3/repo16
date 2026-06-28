package com.amr3d.preview.pro

import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment

/**
 * صفحة الإعدادات - نسخة مطورة تدعم بث التحديثات الفورية لشاشة العرض ثلاثي الأبعاد
 */
class SettingsFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        val prefs = requireContext().getSharedPreferences("amr3d_prefs", Context.MODE_PRIVATE)

        // ===== 1. إعدادات وحدة القياس =====
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
            
            // بث التحديث: إخطار واجهة الـ Viewer لإعادة حساب الأبعاد والقياسات فوراً دون إعادة تشغيل التطبيق
            triggerViewerUpdate()
        }

        // ===== 2. إعدادات خامة المادة الافتراضية (Material) =====
        // إذا كان لديك Spinner في الـ XML للخامات (بلاستيك، معدن، إلخ)، يفضل ربطه هنا
        val materialSpinner = view.findViewById<Spinner>(R.id.materialSpinner) ?: null
        materialSpinner?.let { spinner ->
            val savedMat = prefs.getInt("default_material", 0)
            spinner.setSelection(savedMat)
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    prefs.edit().putInt("default_material", position).apply()
                    triggerViewerUpdate()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }

        // ===== 3. معلومات الإصدار =====
        val tvVersion = view.findViewById<TextView>(R.id.tvVersion)
        tvVersion.text = "الإصدار 3.0\nAmr Hamam 3D © 2026"

        return view
    }

    /**
     * دالة مساعدة للوصول إلى ViewerFragment وتحديث إعداداته بشكل حي ومباشر
     */
    private fun triggerViewerUpdate() {
        activity?.let { act ->
            // البحث عن الـ ViewerFragment المستضاف داخل الـ ViewPager أو الـ FragmentManager
            val fragments = act.supportFragmentManager.fragments
            for (fragment in fragments) {
                if (fragment is ViewerFragment) {
                    // استدعاء دالة تحديث الإعدادات داخل الفراغمنت (تأكد من كتابة دالة لتحديث العرض هناك)
                    fragment.refreshInspectionReport()
                    break
                }
            }
        }
    }
}
