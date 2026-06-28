package com.amr3d.preview.pro

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView
    private var currentFileUri: Uri? = null

    // تفعيل الكائنات الكسولة بشكل صحيح مع تأمين استدعاءاتها
    private val viewerFragment by lazy { ViewerFragment() }
    private val fileBrowserFragment by lazy { FileBrowserFragment() }
    private val slicerFragment by lazy { SlicerFragment() }
    private val historyFragment by lazy { HistoryFragment() }
    private val settingsFragment by lazy { SettingsFragment() }
    
    private var activeFragment: Fragment = viewerFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bottomNav = findViewById(R.id.bottomNav)

        setupFragmentListeners()
        setupNavigation()
        initializeFragments(savedInstanceState)

        // معالجة الملفات القادمة من تطبيقات خارجية (واتساب، التليجرام، إلخ)
        val fileUri = intent?.data
        if (fileUri != null) {
            handleIncomingFileUri(fileUri)
        }
    }

    /**
     * إعداد المستمعين (Listeners) مرة واحدة فقط في الـ onCreate لمنع تسريب الذاكرة
     */
    private fun setupFragmentListeners() {
        fileBrowserFragment.fileSelectedListener = object : FileBrowserFragment.OnFileSelectedListener {
            override fun onFileSelected(file: File) {
                val uri = Uri.fromFile(file)
                HistoryFragment.addToHistory(this@MainActivity, file.absolutePath)
                handleIncomingFileUri(uri)
            }
        }

        historyFragment.fileSelectedListener = object : HistoryFragment.OnFileSelectedListener {
            override fun onFileSelected(file: File) {
                handleIncomingFileUri(Uri.fromFile(file))
            }
        }
    }

    /**
     * بناء أولي لجميع الـ Fragments وإخفائها بدلاً من تدميرها بـ replace للحفاظ على وضعية مجسم الـ 3D
     */
    private fun initializeFragments(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .add(R.id.fragmentContainer, settingsFragment, "settings").hide(settingsFragment)
                .add(R.id.fragmentContainer, historyFragment, "history").hide(historyFragment)
                .add(R.id.fragmentContainer, slicerFragment, "slicer").hide(slicerFragment)
                .add(R.id.fragmentContainer, fileBrowserFragment, "files").hide(fileBrowserFragment)
                .add(R.id.fragmentContainer, viewerFragment, "viewer")
                .commit()
            activeFragment = viewerFragment
        } else {
            // استعادة الحالات عند دوران الشاشة
            viewerFragment.let { supportFragmentManager.findFragmentByTag("viewer") ?: it }
            fileBrowserFragment.let { supportFragmentManager.findFragmentByTag("files") ?: it }
            slicerFragment.let { supportFragmentManager.findFragmentByTag("slicer") ?: it }
            historyFragment.let { supportFragmentManager.findFragmentByTag("history") ?: it }
            settingsFragment.let { supportFragmentManager.findFragmentByTag("settings") ?: it }
        }
    }

    private fun setupNavigation() {
        bottomNav.setOnItemSelectedListener { item ->
            val targetFragment = when (item.itemId) {
                R.id.nav_viewer -> viewerFragment
                R.id.nav_files -> fileBrowserFragment
                R.id.nav_slicer -> slicerFragment
                R.id.nav_history -> historyFragment
                R.id.nav_settings -> settingsFragment
                else -> viewerFragment
            }
            switchFragment(targetFragment)
            true
        }
    }

    /**
     * استخدام التبديل الآمن (show/hide) بدلاً من تدمير الـ Fragment بـ (replace)
     */
    private fun switchFragment(fragment: Fragment) {
        if (activeFragment == fragment) return

        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .hide(activeFragment)
            .show(fragment)
            .commit()
        
        activeFragment = fragment
    }

    /**
     * دالة آمنة لمعالجة وتحميل الملفات تضمن عدم حدوث Crash وتنتظر جاهزية الـ Fragment
     */
    private fun handleIncomingFileUri(uri: Uri) {
        currentFileUri = uri
        
        // الانتقال أولاً لشاشة العارض وتحديث الـ Bottom Nav
        switchFragment(viewerFragment)
        bottomNav.selectedItemId = R.id.nav_viewer

        // تأمين الـ Lifecycle: ننتظر حتى تصبح واجهة الـ Fragment جاهزة تماماً في الذاكرة قبل ضخ الملف
        viewerFragment.view?.post {
            try {
                viewerFragment.loadFile(uri)
            } catch (e: Exception) {
                Toast.makeText(this, "تعذر تحميل ملف الـ 3D الممرر", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            // حل بديل ذكي في حال لم تكن الواجهة قد بدأت إطلاقاً بعد
            viewerFragment.arguments = Bundle().apply { putParcelable("pending_file_uri", uri) }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent) // تحديث الـ intent داخل الاكتيفيتي
        intent?.data?.let { uri ->
            handleIncomingFileUri(uri)
        }
    }
}
