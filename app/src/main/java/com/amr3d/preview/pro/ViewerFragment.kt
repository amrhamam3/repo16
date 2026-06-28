package com.amr3d.preview.pro

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.*
import android.view.animation.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class ViewerFragment : Fragment() {

    private lateinit var glViewerView: GLViewerView
    private lateinit var emptyStateText: TextView
    private lateinit var btnOpenFile: Button
    private lateinit var btnWhatsapp: ImageButton
    private lateinit var btnMeasureTool: ToggleButton
    private lateinit var btnInspect: Button
    private lateinit var btnResetView: Button
    private lateinit var btnWireframe: ToggleButton
    private lateinit var btnMaterial: Button
    private lateinit var btnUnit: Button
    private lateinit var btnExport: Button
    private lateinit var viewCube: ViewCubeView
    private lateinit var btnViewBack: Button
    private lateinit var btnViewLeft: Button
    private lateinit var btnViewBottom: Button
    private lateinit var measurementCard: CardView
    private lateinit var measurementText: TextView
    private lateinit var inspectionCard: CardView
    private lateinit var inspectionText: TextView

    private var currentModel: STLModel? = null
    private var measureModeOn = false
    private var currentUnit = MeasurementUnit.MM

    // ✅ إصلاح: استخدام OpenDocument بدلاً من GetContent للحصول على URI دائم
    private val openDocumentLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            // ✅ إصلاح Android 13+: أخذ إذن دائم للـ URI قبل القراءة
            try {
                requireContext().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: SecurityException) {
                // بعض الـ URIs لا تدعم الـ persistable permission — نكمل بدونها
            }
            loadFile(uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_viewer, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        wireUpListeners()
        animateToolbarEntrance()
    }

    private fun bindViews(view: View) {
        glViewerView    = view.findViewById(R.id.glViewerView)
        emptyStateText  = view.findViewById(R.id.emptyStateText)
        btnOpenFile     = view.findViewById(R.id.btnOpenFile)
        btnWhatsapp     = view.findViewById(R.id.btnWhatsapp)
        btnMeasureTool  = view.findViewById(R.id.btnMeasureTool)
        btnInspect      = view.findViewById(R.id.btnInspect)
        btnResetView    = view.findViewById(R.id.btnResetView)
        btnWireframe    = view.findViewById(R.id.btnWireframe)
        btnMaterial     = view.findViewById(R.id.btnMaterial)
        btnUnit         = view.findViewById(R.id.btnUnit)
        btnExport       = view.findViewById(R.id.btnExport)
        viewCube        = view.findViewById(R.id.viewCube)
        btnViewBack     = view.findViewById(R.id.btnViewBack)
        btnViewLeft     = view.findViewById(R.id.btnViewLeft)
        btnViewBottom   = view.findViewById(R.id.btnViewBottom)
        measurementCard = view.findViewById(R.id.measurementCard)
        measurementText = view.findViewById(R.id.measurementText)
        inspectionCard  = view.findViewById(R.id.inspectionCard)
        inspectionText  = view.findViewById(R.id.inspectionText)
    }

    private fun animateToolbarEntrance() {
        val topBar    = view?.findViewById<View>(R.id.topBar)         ?: return
        val bottomBar = view?.findViewById<View>(R.id.displayToolbar) ?: return
        val bottomBar2= view?.findViewById<View>(R.id.bottomToolbar)  ?: return

        topBar.translationY = -200f; topBar.alpha = 0f
        topBar.animate().translationY(0f).alpha(1f).setDuration(500)
            .setInterpolator(DecelerateInterpolator(2f)).start()

        bottomBar.translationY = 200f; bottomBar.alpha = 0f
        bottomBar.animate().translationY(0f).alpha(1f).setDuration(500)
            .setStartDelay(100).setInterpolator(DecelerateInterpolator(2f)).start()

        bottomBar2.translationY = 200f; bottomBar2.alpha = 0f
        bottomBar2.animate().translationY(0f).alpha(1f).setDuration(500)
            .setStartDelay(200).setInterpolator(DecelerateInterpolator(2f)).start()
    }

    private fun wireUpListeners() {
        btnOpenFile.setOnClickListener {
            animateButtonPress(it)
            openDocumentLauncher.launch(arrayOf("*/*"))
        }

        btnMeasureTool.setOnCheckedChangeListener { btn, isChecked ->
            animateButtonPress(btn)
            measureModeOn = isChecked
            if (!isChecked) {
                glViewerView.stlRenderer.clearMeasurementPoints()
                measurementCard.visibility = View.GONE
            } else {
                inspectionCard.visibility = View.GONE
                Toast.makeText(context, "اضغط على نقطتين على سطح الموديل", Toast.LENGTH_LONG).show()
            }
        }

        btnInspect.setOnClickListener {
            animateButtonPress(it)
            val model = currentModel ?: run {
                Toast.makeText(context, "افتح ملف STL أولاً", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showInspectionReport(model)
        }

        btnResetView.setOnClickListener { animateButtonPress(it); resetCamera() }
        btnWhatsapp.setOnClickListener  { animateButtonPress(it); openWhatsapp() }

        btnWireframe.setOnCheckedChangeListener { btn, isChecked ->
            animateButtonPress(btn)
            glViewerView.stlRenderer.wireframeMode = isChecked
        }

        btnMaterial.setOnClickListener { animateButtonPress(it); showMaterialPicker() }
        btnUnit.setOnClickListener     { animateButtonPress(it); cycleUnit() }
        btnExport.setOnClickListener   { animateButtonPress(it); exportCurrentView() }

        viewCube.onFaceSelected = { face -> jumpToView(face.rotX, face.rotY) }
        btnViewBack.setOnClickListener   { jumpToView(-10f, 180f) }
        btnViewLeft.setOnClickListener   { jumpToView(-10f, -90f) }
        btnViewBottom.setOnClickListener { jumpToView(89f, 0f)    }

        glViewerView.onSingleTap = { x, y ->
            if (measureModeOn) handleMeasurementTap(x, y)
        }

        inspectionCard.setOnClickListener  { inspectionCard.visibility = View.GONE }
        measurementCard.setOnClickListener {
            measurementCard.visibility = View.GONE
            glViewerView.stlRenderer.clearMeasurementPoints()
        }
    }

    private fun animateButtonPress(view: View) {
        view.animate().scaleX(0.88f).scaleY(0.88f).setDuration(80)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                view.animate().scaleX(1f).scaleY(1f).setDuration(150)
                    .setInterpolator(OvershootInterpolator(2f)).start()
            }.start()
    }

    // ✅ إصلاح رئيسي: استخدام viewLifecycleOwner.lifecycleScope بدلاً من CoroutineScope المنفصل
    fun loadFile(uri: Uri) {
        Toast.makeText(context, "جارٍ تحميل الملف...", Toast.LENGTH_SHORT).show()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val model = withContext(Dispatchers.IO) {
                    // ✅ إصلاح: استخدام Dispatchers.IO بدلاً من Default لعمليات الملفات
                    STLParser.parse(requireContext(), uri)
                }

                // ✅ التحقق من أن الـ Fragment لا يزال مرتبطاً قبل تحديث الـ UI
                if (!isAdded) return@launch

                currentModel = model
                glViewerView.stlRenderer.setModel(model)
                emptyStateText.visibility  = View.GONE
                inspectionCard.visibility  = View.GONE
                measurementCard.visibility = View.GONE
                btnMeasureTool.isChecked   = false
                btnWireframe.isChecked     = false

                // حفظ في التاريخ
                uri.path?.let { HistoryFragment.addToHistory(requireContext(), it) }

                Toast.makeText(context, "✅ ${model.triangleCount} مثلث", Toast.LENGTH_SHORT).show()

            } catch (e: SecurityException) {
                // ✅ إصلاح: معالجة خاصة لخطأ الأذونات على Android 13+
                if (!isAdded) return@launch
                Toast.makeText(
                    context,
                    "خطأ في الأذونات — حاول فتح الملف مرة أخرى",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                if (!isAdded) return@launch
                Toast.makeText(
                    context,
                    "تعذر قراءة الملف: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun loadSTLFile(uri: Uri) = loadFile(uri)

    private fun jumpToView(targetRotX: Float, targetRotY: Float) {
        val renderer = glViewerView.stlRenderer
        val startX = renderer.rotationX; val startY = renderer.rotationY
        android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 350; interpolator = DecelerateInterpolator(2f)
            addUpdateListener { anim ->
                val t = anim.animatedValue as Float
                renderer.rotationX = startX + (targetRotX - startX) * t
                renderer.rotationY = startY + (targetRotY - startY) * t
            }
            start()
        }
    }

    private fun resetCamera() {
        val renderer = glViewerView.stlRenderer
        renderer.rotationX = -25f; renderer.rotationY = 35f
        renderer.scaleFactor = 1f; renderer.panX = 0f; renderer.panY = 0f
        glViewerView.queueEvent { renderer.updateProjection() }
    }

    private fun showMaterialPicker() {
        val materials = STLRenderer.Material.values()
        val icons     = listOf("🔵","🔩","🪵","🪨","🟠","⬛","🟡")
        val matItems  = materials.map { "${icons[it.id]}  ${it.nameAr}" }
        val bgItems   = listOf("── خلفية ──","داكن","أسود","رمادي","أبيض","كحلي")
        val bgColors  = listOf(null,
            floatArrayOf(0.10f,0.11f,0.13f), floatArrayOf(0.02f,0.02f,0.02f),
            floatArrayOf(0.22f,0.24f,0.27f), floatArrayOf(0.92f,0.92f,0.92f),
            floatArrayOf(0.05f,0.08f,0.18f))
        val allItems  = (listOf("── المادة ──") + matItems + bgItems).toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle("المادة والمظهر")
            .setItems(allItems) { _, which ->
                when {
                    which == 0 -> {}
                    which <= materials.size -> {
                        glViewerView.stlRenderer.setMaterial(materials[which - 1])
                        Toast.makeText(context, materials[which-1].nameAr, Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        val bgIdx = which - materials.size - 1
                        bgColors.getOrNull(bgIdx)?.let { c ->
                            glViewerView.stlRenderer.setBackgroundColor(c[0], c[1], c[2])
                        }
                    }
                }
            }.show()
    }

    private fun cycleUnit() {
        currentUnit = when (currentUnit) {
            MeasurementUnit.MM   -> MeasurementUnit.CM
            MeasurementUnit.CM   -> MeasurementUnit.INCH
            MeasurementUnit.INCH -> MeasurementUnit.MM
        }
        btnUnit.text = currentUnit.label
        currentModel?.let { if (inspectionCard.visibility == View.VISIBLE) showInspectionReport(it) }
        val pts = glViewerView.stlRenderer.getMeasurementPoints()
        if (pts.size == 2) updateMeasurementText(pts[0], pts[1])
    }

    private fun exportCurrentView() {
        if (currentModel == null) {
            Toast.makeText(context, "افتح ملف أولاً", Toast.LENGTH_SHORT).show(); return
        }
        val renderer = glViewerView.stlRenderer
        val w = renderer.getSurfaceWidth(); val h = renderer.getSurfaceHeight()
        if (w <= 0 || h <= 0) return
        glViewerView.queueEvent {
            val bitmap = renderer.captureFrame(w, h)
            requireActivity().runOnUiThread { saveAndShareBitmap(bitmap) }
        }
    }

    private fun saveAndShareBitmap(bitmap: Bitmap) {
        try {
            val file = File(
                requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                "Amr3D_${System.currentTimeMillis()}.png"
            )
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val uri = FileProvider.getUriForFile(
                requireContext(), "${requireContext().packageName}.fileprovider", file
            )
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "تصدير الصورة"))
        } catch (e: Exception) {
            Toast.makeText(context, "تعذر الحفظ: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun openWhatsapp() {
        val phone = "201009172167"
        val msg   = Uri.encode("مرحبًا، عندي استفسار بخصوص تطبيق Amr3D Preview")
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("whatsapp://send?phone=$phone&text=$msg"))
                    .apply { setPackage("com.whatsapp") }
            )
        } catch (e: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$phone?text=$msg")))
        }
    }

    private fun handleMeasurementTap(screenX: Float, screenY: Float) {
        val model    = currentModel ?: return
        val renderer = glViewerView.stlRenderer
        val ray      = RayPicker.screenPointToRay(
            screenX, screenY,
            renderer.getSurfaceWidth(), renderer.getSurfaceHeight(),
            renderer.getCurrentModelMatrix(), renderer.getCurrentViewMatrix(),
            renderer.getCurrentProjectionMatrix()
        )
        val hit = RayPicker.findClosestIntersection(ray, model) ?: run {
            Toast.makeText(context, "لم يتم تحديد نقطة", Toast.LENGTH_SHORT).show(); return
        }
        renderer.addMeasurementPoint(hit)
        val pts = renderer.getMeasurementPoints()
        if (pts.size == 2) updateMeasurementText(pts[0], pts[1])
        else {
            measurementText.text = "نقطة أولى محددة — اضغط على نقطة ثانية"
            measurementCard.visibility = View.VISIBLE
        }
    }

    private fun updateMeasurementText(p1: FloatArray, p2: FloatArray) {
        val d = MeasurementTools.distanceBetween(p1, p2, currentUnit)
        measurementText.text = String.format(Locale.US, "المسافة: %.3f %s", d, currentUnit.label)
        measurementCard.visibility = View.VISIBLE
    }

    private fun showInspectionReport(model: STLModel) {
        if (inspectionCard.visibility == View.VISIBLE) {
            inspectionCard.visibility = View.GONE; return
        }
        val report = MeasurementTools.inspect(model, currentUnit)
        val u      = report.unit.label
        inspectionText.text = "📐 أبعاد الموديل\n─────────────────\n" +
            String.format(Locale.US, "الطول (X):    %.2f %s\n", report.width,  u) +
            String.format(Locale.US, "العرض (Y):   %.2f %s\n",  report.depth,  u) +
            String.format(Locale.US, "الارتفاع (Z): %.2f %s",   report.height, u)
        inspectionCard.visibility  = View.VISIBLE
        measurementCard.visibility = View.GONE
    }
}
