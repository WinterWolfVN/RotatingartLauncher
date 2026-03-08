package com.app.ralaunch.core.platform.runtime

import android.system.Os
import android.util.Log

/**
 * Game Performance Booster
 * 
 * Injects aggressive environment variables to optimize CPU/GPU usage,
 * unlock frame rates, and reduce stuttering for heavy games like tModLoader.
 */
object GameBoost {

    private const val TAG = "GameBoost"

    // ===================================================================
    // ... MAIN BOOST TRIGGER ...
    // ===================================================================
    fun applyMaxPerformance() {
        Log.i(TAG, "🚀 INITIATING MAX PERFORMANCE BOOST...")

        try {
            // ... 1. UNLOCK FPS (Disable V-Sync) ...
            // Forces the game to render as fast as the CPU/GPU can handle
            Os.setenv("vblank_mode", "0", true)
            
            // ... 2. GL4ES GRAPHICS OPTIMIZATION ...
            // Batch draw calls to heavily reduce CPU bottleneck
            Os.setenv("LIBGL_BATCH", "1", true)
            // Keep arrays in memory instead of recreating them (Reduces stutter)
            Os.setenv("LIBGL_KEEPARRAY", "1", true)
            // Disable heavy OpenGL error logging to save CPU cycles
            Os.setenv("LIBGL_NOLOG", "1", true)
            // Force hardware transform and lighting
            Os.setenv("LIBGL_HARDWARE_TL", "1", true)
            // Skip strict shader validation (Faster loading)
            Os.setenv("LIBGL_NOHASH", "1", true)

            // ... 3. FNA/TERRARIA SPECIFIC OPTIMIZATION ...
            // Prevent the game from rendering at ultra-high resolutions
            Os.setenv("FNA_GRAPHICS_ENABLE_HIGHDPI", "0", true)

            // ... 4. MONO & CPU THREADING ...
            // Allow Mono (.NET) to spawn more threads for heavy mod processing
            Os.setenv("MONO_THREADS_PER_CPU", "2000", true)
            // Use lighter Garbage Collection strategy for Mono
            Os.setenv("MONO_GC_PARAMS", "nursery-size=16m,major=marksweep", true)

            Log.i(TAG, "✅ GameBoost Applied! Device is running at MAX capacity.")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to apply GameBoost: ${e.message}")
        }
    }
}
