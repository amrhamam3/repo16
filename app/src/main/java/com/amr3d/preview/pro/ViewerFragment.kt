package com.amr3d.preview.pro

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment

class ViewerFragment : Fragment() {

    private lateinit var glView: GLViewerView
    private lateinit var tvReport: TextView
    private lateinit var btnClearPoints: ImageButton
    private lateinit var btnMaterial: ImageButton
    private var loadedModel: STLModel? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_viewer, container, false)
        glView = view.findViewById(R.id.glViewer)
        tvReport = view.findViewById(R.id.tvModelReport)
        btnClearPoints = view.findViewById(R.id.btnClearPoints)
        btnMaterial = view.findViewById(R.id.btnMaterial)

        btnClearPoints.setOnClickListener {
            glView.stlRenderer.clearMeasurementPoints()
            Toast.makeText(context, "تم مسح نقاط القياس", Toast.LENGTH_SHORT).show()
        }

        btnMaterial.setOnClickListener {
            showMaterialDialog()
        }

        glView.onSingleTap = { x, y ->
            handleTapToMeasure(x, y)
        }

        arguments?.getParcelable<Uri>("pending_file_uri")?.let {
            loadFile(it)
        }

        return view
    }

    fun loadFile(uri: Uri) {
        val ctx = context ?: return
        tvReport.text = "جاري تحليل بنية ملف الـ 3D..."
        
        Thread {
            try {
                val model = STLParser.parse(ctx, uri)
                loadedModel = model
                activity?.runOnUiThread {
                    glView.stlRenderer.setModel(model)
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
        val model = loadedModel ?: return
        val prefs = requireContext().getSharedPreferences("amr3d_prefs", Context.MODE_PRIVATE)
        val savedUnit = prefs.getString("unit", "MM") ?: "MM"
        val unit = MeasurementUnit.valueOf(savedUnit)

        val report = MeasurementTools.inspect(model, unit)
        
        tvReport.text = """
            عدد المثلثات: ${report.triangleCount}
            الأبعاد: ${String.format("%.1f", report.width)} x ${String.format("%.1f", report.depth)} x ${String.format("%.1f", report.height)} ${report.unit.label}
            الحجم التقريبي: ${String.format("%.2f", report.approxVolume)} تكعيب
            المساحة السطحية: ${String.format("%.2f", report.approxSurfaceArea)} مربع
        """.trimIndent()
    }

    private fun showMaterialDialog() {
        val materials = arrayOf("بلاستيك", "معدن نيون", "خشب كلاسيك", "رخام فاخر", "برونز مأكسد", "ألياف كربون", "ريزن شفاف")
        
        AlertDialog.Builder(requireContext())
            .setTitle("اختر خامة عرض المجسم")
            .setItems(materials) { _, which ->
                glView.stlRenderer.currentMaterial = which
                Toast.makeText(context, "تم تطبيق خامة: ${materials[which]}", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun handleTapToMeasure(screenX: Float, screenY: Float) {
        val model = loadedModel ?: return
        val renderer = glView.stlRenderer

        val ray = RayPicker.screenPointToRay(
            screenX, screenY,
            renderer.surfaceWidth, renderer.surfaceHeight,
            renderer.getModelMatrix(), renderer.getViewMatrix(), renderer.getProjectionMatrix()
        )

        Thread {
            val hitPoint = RayPicker.findClosestIntersection(ray, model)
            activity?.runOnUiThread {
                if (hitPoint != null) {
                    renderer.addMeasurementPoint(hitPoint)
                    val pts = renderer.measurementPoints
                    if (pts.size >= 2) {
                        val prefs = requireContext().getSharedPreferences("amr3d_prefs", Context.MODE_PRIVATE)
                        val unit = MeasurementUnit.valueOf(prefs.getString("unit", "MM") ?: "MM")
                        val dist = MeasurementTools.distanceBetween(pts[pts.size - 2], pts[pts.size - 1], unit)
                        Toast.makeText(context, "المسافة المقاسة: ${String.format("%.2f", dist)} ${unit.label}", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "تم وضع النقطة الأولى، حدد النقطة الثانية لقياس البعد", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "نقرت خارج نطاق مجسم الـ 3D", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
}
