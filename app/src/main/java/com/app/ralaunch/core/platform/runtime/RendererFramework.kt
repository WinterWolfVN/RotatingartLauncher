package com.app.ralaunch.core.platform.runtime

import android.content.Context
import android.system.Os
import android.util.Log
import java.io.File

/**
 * Custom Renderer Framework Injector
 * 
 * Bridges the gap between old Android OS (like 7.1) and modern renderers (Zink, VirGL).
 * It forces the OS to use our custom bundled Mesa drivers instead of the system's outdated drivers.
 */
object RendererFramework {

    private const val TAG = "RendererFramework"

    // ===================================================================
    // ... INJECT CUSTOM RENDERER PATHS ...
    // ===================================================================
    fun injectCustomDriverPaths(context: Context, selectedRenderer: String) {
        Log.i(TAG, "🏗️ Preparing Custom Renderer Framework for: $selectedRenderer")

        try {
            // ... 1. Find the directory where our custom .so libraries are stored ...
            // Usually, they are extracted into the app's internal "runtime_libs" folder
            val libsDir = File(context.filesDir, "runtime_libs").absolutePath
            val nativeLibsDir = context.applicationInfo.nativeLibraryDir

            // ... 2. The Magic Trick: LD_LIBRARY_PATH ...
            // This environment variable tells Linux/Android WHERE to look for C++ libraries FIRST.
            // By putting our folder first, we OVERRIDE the system's default graphics drivers!
            val currentLdPath = Os.getenv("LD_LIBRARY_PATH") ?: ""
            val newLdPath = "$libsDir:$nativeLibsDir:$currentLdPath"
            
            Os.setenv("LD_LIBRARY_PATH", newLdPath, true)
            Log.d(TAG, "LD_LIBRARY_PATH injected: $newLdPath")

            // ... 3. Configure Mesa / EGL to use our custom driver ...
            when (selectedRenderer.lowercase()) {
                "zink" -> {
                    Log.i(TAG, "Applying Zink (Vulkan-based OpenGL) framework hacks...")
                    // Force EGL to use the Mesa software driver loaded with Zink
                    Os.setenv("EGL_PLATFORM", "surfaceless", true)
                    Os.setenv("GALLIUM_DRIVER", "zink", true)
                    Os.setenv("MESA_LOADER_DRIVER_OVERRIDE", "zink", true)
                    // Zink often requires bypassing strict GL checks on mobile
                    Os.setenv("MESA_GL_VERSION_OVERRIDE", "3.0", true)
                    Os.setenv("MESA_GLES_VERSION_OVERRIDE", "3.1", true)
                }
                
                "virgl" -> {
                    Log.i(TAG, "Applying VirGL framework hacks...")
                    Os.setenv("GALLIUM_DRIVER", "virpipe", true)
                    Os.setenv("MESA_GL_VERSION_OVERRIDE", "3.0", true)
                }

                "gl4es" -> {
                    Log.i(TAG, "Applying GL4ES framework hacks...")
                    // Force GL4ES library path specifically
                    Os.setenv("LIBGL_DRIVERS_PATH", libsDir, true)
                    Os.setenv("LIBGL_FB", "1", true) // Force Framebuffer object
                }
                
                else -> {
                    Log.i(TAG, "Using Native OS Drivers. No Mesa hacks applied.")
                }
            }

            Log.i(TAG, "✅ Custom Renderer Framework injected successfully!")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to setup Renderer Framework: ${e.message}")
        }
    }
}
