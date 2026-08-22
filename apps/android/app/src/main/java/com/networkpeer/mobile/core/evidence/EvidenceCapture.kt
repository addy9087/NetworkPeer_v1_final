package com.networkpeer.mobile.core.evidence

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object EvidenceCapture {
    fun createImageUri(context: Context): Uri {
        val directory = File(context.filesDir, "evidence").apply { mkdirs() }
        val image = File.createTempFile("capture-", ".jpg", directory)
        return FileProvider.getUriForFile(context, "${context.packageName}.evidence", image)
    }

    fun delete(context: Context, uri: Uri) {
        runCatching { context.contentResolver.delete(uri, null, null) }
    }
}
