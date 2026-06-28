package com.amr3d.preview.pro

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView
    private var currentFileUri: Uri? = null

    // Fragments
    private val viewerFragment by lazy { ViewerFragment() }
    private val fileBrowserFragment by lazy { FileBrowserFragment() }
    private val slicerFragment by lazy { SlicerFragment() }
    private val historyFragment by lazy { HistoryFragment() }
    private val settingsFragment by lazy { SettingsFragment() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bottomNav = findViewById(R.id.bottomNav)

        setupNavigation()

        // لو اتفتح من واتساب/مدير ملفات
        val fileUri = intent?.data
        if (fileUri != null) {
            currentFileUri = fileUri
            showFragment(viewerFragment)
            bottomNav.selectedItemId = R.id.nav_viewer
            viewerFragment.loadFile(fileUri)
        } else {
            showFragment(viewerFragment)
        }
    }

    private fun setupNavigation() {
        bottomNav.setOnItemSelectedListener { item ->
            val fragment = when (item.itemId) {
                R.id.nav_viewer -> viewerFragment
                R.id.nav_files -> fileBrowserFragment.also { f ->
                    f.fileSelectedListener = object : FileBrowserFragment.OnFileSelectedListener {
                        override fun onFileSelected(file: File) {
                            val uri = Uri.fromFile(file)
                            HistoryFragment.addToHistory(this@MainActivity, file.absolutePath)
                            viewerFragment.loadFile(uri)
                            bottomNav.selectedItemId = R.id.nav_viewer
                        }
                    }
                }
                R.id.nav_slicer -> slicerFragment
                R.id.nav_history -> historyFragment.also { f ->
                    f.fileSelectedListener = object : HistoryFragment.OnFileSelectedListener {
                        override fun onFileSelected(file: File) {
                            viewerFragment.loadFile(Uri.fromFile(file))
                            bottomNav.selectedItemId = R.id.nav_viewer
                        }
                    }
                }
                R.id.nav_settings -> settingsFragment
                else -> viewerFragment
            }
            showFragment(fragment)
            true
        }
    }

    private fun showFragment(fragment: Fragment) {
        val transaction = supportFragmentManager.beginTransaction()
        transaction.setCustomAnimations(
            android.R.anim.fade_in,
            android.R.anim.fade_out
        )
        transaction.replace(R.id.fragmentContainer, fragment)
        transaction.commit()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.data?.let { uri ->
            currentFileUri = uri
            viewerFragment.loadFile(uri)
            bottomNav.selectedItemId = R.id.nav_viewer
            showFragment(viewerFragment)
        }
    }
}
