/**
 * Copyright 2016 Bartosz Schiller
 *
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.barteksc.sample

import androidx.appcompat.app.AppCompatActivity
import com.github.barteksc.pdfviewer.listener.OnPageErrorListener
import android.os.Bundle
import com.github.barteksc.sample.PDFViewActivity
import com.github.barteksc.sample.R
import android.content.pm.PackageManager
import android.content.Intent
import android.app.Activity
import android.content.ActivityNotFoundException
import android.graphics.Color
import android.net.Uri
import android.widget.Toast
import com.github.barteksc.sample.PDFViewActivity.DownloadAsyncTask
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle
import com.github.barteksc.pdfviewer.util.FitPolicy
import android.provider.OpenableColumns
import com.shockwave.pdfium.PdfDocument.Meta
import com.shockwave.pdfium.PdfDocument.Bookmark
import android.os.AsyncTask
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.github.barteksc.pdfviewer.listener.OnLoadCompleteListener
import com.github.barteksc.pdfviewer.listener.OnPageChangeListener
import com.github.barteksc.sample.databinding.ActivityMainBinding
import java.io.IOException
import java.io.InputStream
import java.lang.ref.WeakReference
import java.net.URL

class PDFViewActivity : AppCompatActivity(), OnPageChangeListener, OnLoadCompleteListener,
    OnPageErrorListener {
    var uri: Uri? = null
    var pageNumber = 0
    var pdfFileName: String? = null
    var activityMainBinding: ActivityMainBinding? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityMainBinding = ActivityMainBinding.inflate(
            layoutInflater
        )
        setContentView(activityMainBinding!!.root)
        activityMainBinding!!.pdfView.setBackgroundColor(Color.LTGRAY)
        if (uri != null) {
            displayFromUri(uri)
        } else {
            displayFromAsset(SAMPLE_FILE)
        }
        val left = resources.getDimensionPixelSize(R.dimen.default_padding_left_right)
        val top = resources.getDimensionPixelSize(R.dimen.default_padding_top_bottom)
        activityMainBinding?.tvPageCounter?.setPadding(left, top, left, top)
        title = pdfFileName
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.options, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.pickFile) {
            val permissionCheck = ContextCompat.checkSelfPermission(
                this,
                READ_EXTERNAL_STORAGE
            )
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(READ_EXTERNAL_STORAGE),
                    PERMISSION_CODE
                )
                return true
            }
            launchPicker()
        } else if (item.itemId == R.id.loadUrl) {
            displayFromUrl()
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            uri = data!!.data
            displayFromUri(uri)
        }
    }

    fun launchPicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "application/pdf"
        try {
            startActivityForResult(intent, REQUEST_CODE)
        } catch (e: ActivityNotFoundException) {
            //alert user that file manager not working
            Toast.makeText(this, R.string.toast_pick_file_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun displayFromUrl() {
        //    String url = "http://192.168.0.101:5500/docs/test1.pdf";
        val url = "http://192.168.0.101:5500/docs/test3.pdf"
        DownloadAsyncTask(this, url).execute()
    }

    private fun displayFromAsset(assetFileName: String) {
        pdfFileName = assetFileName
        activityMainBinding!!.pdfView.fromAsset(SAMPLE_FILE)
            .defaultPage(pageNumber)
            .onPageChange(this)
            .enableAnnotationRendering(true)
            .onLoad(this)
            .scrollHandle(DefaultScrollHandle(this))
            .spacing(10) // in dp
            .onPageError(this)
            .pageFitPolicy(FitPolicy.BOTH)
            .load()
    }

    private fun displayFromUri(uri: Uri?) {
        pdfFileName = getFileName(uri)
        activityMainBinding!!.pdfView.fromUri(uri)
            .defaultPage(pageNumber)
            .onPageChange(this)
            .enableAnnotationRendering(true)
            .onLoad(this)
            .scrollHandle(DefaultScrollHandle(this))
            .spacing(10) // in dp
            .onPageError(this)
            .load()
    }

    override fun onPageChanged(page: Int, pageCount: Int) {
        pageNumber = page
        title = String.format("%s %s / %s", pdfFileName, page + 1, pageCount)

        activityMainBinding?.tvPageCounter?.text = "${page + 1} / $pageCount"
    }

    fun getFileName(uri: Uri?): String? {
        var result: String? = null
        if (uri!!.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.lastPathSegment
        }
        return result
    }

    override fun loadComplete(nbPages: Int) {
        val meta = activityMainBinding!!.pdfView.documentMeta
        Log.e(TAG, "title = " + meta.title)
        Log.e(TAG, "author = " + meta.author)
        Log.e(TAG, "subject = " + meta.subject)
        Log.e(TAG, "keywords = " + meta.keywords)
        Log.e(TAG, "creator = " + meta.creator)
        Log.e(TAG, "producer = " + meta.producer)
        Log.e(TAG, "creationDate = " + meta.creationDate)
        Log.e(TAG, "modDate = " + meta.modDate)
        printBookmarksTree(activityMainBinding!!.pdfView.tableOfContents, "-")
    }

    fun printBookmarksTree(tree: List<Bookmark>, sep: String) {
        for (b in tree) {
            Log.e(TAG, String.format("%s %s, p %d", sep, b.title, b.pageIdx))
            if (b.hasChildren()) {
                printBookmarksTree(b.children, "$sep-")
            }
        }
    }

    /**
     * Listener for response to user permission request
     *
     * @param requestCode  Check that permission request code matches
     * @param permissions  Permissions that requested
     * @param grantResults Whether permissions granted
     */
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == PERMISSION_CODE) {
            if (grantResults.size > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                launchPicker()
            }
        }
    }

    override fun onPageError(page: Int, t: Throwable) {
        Log.e(TAG, "Cannot load page $page")
    }

    internal inner class DownloadAsyncTask(pdfViewActivity: PDFViewActivity?, url: String) :
        AsyncTask<Void?, Void?, InputStream?>() {
        var pdfViewActivityWeakReference: WeakReference<PDFViewActivity>
        var url: String

        private fun checkValid(): Boolean {
            val activity: Activity? = pdfViewActivityWeakReference.get()
            return !(activity == null || activity.isFinishing || activity.isDestroyed)
        }

        override fun doInBackground(vararg params: Void?): InputStream? {
            return try {
                if (!checkValid()) {
                    null
                } else URL(url).openStream()
            } catch (e: IOException) {
                e.printStackTrace()
                null
            }
        }

        override fun onPostExecute(inputStream: InputStream?) {
            super.onPostExecute(inputStream)
            if (inputStream != null) {
                activityMainBinding!!.pdfView.fromStream(inputStream)
                    .defaultPage(pageNumber)
                    .onPageChange(pdfViewActivityWeakReference.get())
                    .enableAnnotationRendering(true)
                    .onLoad(pdfViewActivityWeakReference.get())
                    .scrollHandle(DefaultScrollHandle(pdfViewActivityWeakReference.get()))
                    .spacing(10) // in dp
                    .onPageError(pdfViewActivityWeakReference.get())
                    .load()
            }
        }

        init {
            pdfViewActivityWeakReference = WeakReference<PDFViewActivity>(pdfViewActivity)
            this.url = url
        }
    }

    companion object {
        private val TAG = PDFViewActivity::class.java.simpleName
        private const val REQUEST_CODE = 42
        const val PERMISSION_CODE = 42042
        const val SAMPLE_FILE = "sample.pdf"
        const val READ_EXTERNAL_STORAGE = "android.permission.READ_EXTERNAL_STORAGE"
    }
}