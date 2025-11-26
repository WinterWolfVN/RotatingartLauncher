package com.app.ralib.utils;

import android.util.Log;

import com.app.ralib.Shared;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Objects;

public class TemporaryFileAcquirer implements Closeable {
    private static final String TAG = "TemporaryFileAcquirer";

    private final Path preferredTempDir;
    private final ArrayList<Path> tmpFilePaths = new ArrayList<>();

    public TemporaryFileAcquirer() {
        this.preferredTempDir = Objects.requireNonNull(Shared.getContext().getExternalCacheDir())
                .toPath()
                .toAbsolutePath();
    }

    public TemporaryFileAcquirer(Path preferredTempDir) {
        this.preferredTempDir = preferredTempDir;
    }

    public Path acquireTempFilePath(String preferredSuffix) {
        Path tempFilePath = preferredTempDir.resolve(System.currentTimeMillis() + "_" + preferredSuffix);
        tmpFilePaths.add(tempFilePath);
        return tempFilePath;
    }

    public void cleanupTempFiles() {
        for (Path tmpFilePath : tmpFilePaths) {
            var isSuccessful = FileUtils.deleteDirectoryRecursively(tmpFilePath);
            if (!isSuccessful) {
                Log.w(TAG, "Failed to delete temporary file or directory: " + tmpFilePath);
            }
        }
        tmpFilePaths.clear();
    }

    @Override
    public void close() {
        cleanupTempFiles();
    }
}
