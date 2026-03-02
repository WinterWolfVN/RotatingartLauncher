package com.app.ralaunch.feature.patch.data

import android.util.Log
import java.io.File

data class Patch(
    val patchDir: File,
    val manifest: PatchManifest
) {
    fun getEntryAssemblyAbsolutePath(): File {
        // CHANGED: Use absoluteFile instead of canonicalFile
        return File(patchDir, manifest.entryAssemblyFile).absoluteFile
    }

    companion object {
        private const val TAG = "Patch"

        @JvmStatic
        fun fromPatchPath(patchDir: File): Patch? {
            // CHANGED: Use absoluteFile instead of canonicalFile
            val normalizedDir = patchDir.absoluteFile
            
            if (!normalizedDir.exists() || !normalizedDir.isDirectory) {
                Log.w(TAG, "fromPatchPath: Path does not exist or is not a directory: $normalizedDir")
                return null
            }

            val manifest = PatchManifest.fromJson(
                File(normalizedDir, PatchManifest.MANIFEST_FILE_NAME)
            )
            if (manifest == null) {
                Log.w(TAG, "fromPatchPath: Failed to load manifest from: $normalizedDir")
                return null
            }

            return Patch(normalizedDir, manifest)
        }
    }
}
