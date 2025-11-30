package com.app.ralib.patch;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;

public class Patch {
    public static final String TAG = "Patch";

    @NonNull
    public Path patchPath;
    @NonNull
    public PatchManifest manifest;

    @NonNull Path getEntryAssemblyAbsolutePath() {
        return patchPath.resolve(manifest.getEntryAssemblyFile()).toAbsolutePath().normalize();
    }

    @Nullable
    public static Patch fromPatchPath(@NonNull Path patchPath) {
        patchPath = patchPath.normalize();
        if (!Files.exists(patchPath) || !Files.isDirectory(patchPath)) {
            Log.w(TAG, "fromPatchPath: Patch path does not exist or is not a directory: " + patchPath);
            return null;
        }

        var manifest = PatchManifest.fromJson(patchPath.resolve(PatchManifest.MANIFEST_FILE_NAME));
        if (manifest == null) {
            Log.w(TAG, "fromPatchPath: Failed to load manifest from path: " + patchPath);
            return null;
        }

        return new Patch(patchPath, manifest);
    }

    public Patch(@NonNull Path patchPath, @NonNull PatchManifest manifest) {
        this.patchPath = patchPath;
        this.manifest = manifest;
    }
}
