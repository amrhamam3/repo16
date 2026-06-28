package com.amr3d.preview.pro

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment

class ViewerFragment : Fragment() {

    private lateinit var glView: GLViewerView
    private lateinit var tvReport: TextView
    private lateinit var btnClearPoints: Button
    private var loadedModel: STLModel? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_viewer, container, false)
        glView = view.findViewById(R.id.glViewer)
        tvReport = view.findViewById(R.id.tvModelReport)
        btnClearPoints = view.findViewById(R.id.btnClearPoints)

        btnClearPoints.setOnClickListener {
            glView.clearMeasurementPoints()
            Toast.makeText(context, "تم مسح نقاط القياس", Toast.LENGTH_SHORT).show()
        }

        arguments?.getParcelable<Uri>("pending_file_uri")?.let {
            loadFile(it)
        }

        return view
    }

    fun loadFile(uri: Uri) {
        val ctx = context?: return
        tvReport.text = "جاري تحليل بنية ملف الـ 3D..."

        Thread {
            try {
                val model = STLParser.parse(ctx, uri)
                loadedModel = model
                activity?.runOnUiThread {
                    glView.setModel(model)
                    refreshInspectionReport()
                }
            } catch (e: Exception) {
                activity?.runOnUiThread {
                    tvReport.text = "خطأ: ${e.message}"
                }
            }
        }.start()
    }

    fun refreshInspectionReport() {
        val model = loadedModel?: return
        val prefs = requireContext().getSharedPreferences("amr3d_prefs", Context.MODE_PRIVATE)
        val savedUnit = prefs.getString("unit", "MM")?: "MM"
        val unit = MeasurementUnit.valueOf(savedUnit)

        val report = MeasurementTools.inspect(model, unit)

        tvReport.text = """
            عدد المثلثات: ${report.triangleCount}
            الأبعاد: ${String.format("%.1f", report.width)} x ${String.format("%.1f", report.depth)} x ${String.format("%.1f", report.height)} ${report.unit.label}
            الحجم التقريبي: ${String.format("%.2f", report.approxVolume)} تكعيب
            المساحة السطحية: ${String.format("%.2f", report.approxSurfaceArea)} مربع
        """.trimIndent()
    }

    fun showMaterialDialog() {
        val materials = arrayOf("بلاستيك", "معدن نيون", "خشب كلاسيك", "رخام فاخر", "برونز مأكسد", "ألياف كربون", "ريزن شفاف")

        AlertDialog.Builder(requireContext())
           .setTitle("اختر خامة عرض المجسم")
           .setItems(materials) { _, which ->
                val material = Material.values()[which]
                glView.setCurrentMaterial(material)
                Toast.makeText(context, "تم تطبيق خامة: ${materials[which]}", Toast.LENGTH_SHORT).show()
            }
           .show()
    }
}