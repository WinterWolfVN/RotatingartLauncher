package com.app.ralaunch.core.platform.runtime.renderer

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File
import kotlin.io.path.Path
import org.koin.core.context.GlobalContext

actual object RendererRegistry {
    private const val RUNTIME_LIBS_DIR = "runtime_libs"

    const val ID_NATIVE = "native"
    const val ID_GL4ES = "gl4es"
    const val ID_GL4ES_ANGLE = "gl4es+angle"
    const val ID_MOBILEGLUES = "mobileglues"
    const val ID_ANGLE = "angle"
    const val ID_ZINK = "zink"

    private val rendererStore = LinkedHashMap<String, RendererInfo>()

    init {
        register(
            RendererInfo(
                id = ID_NATIVE,
                displayName = "Native OpenGL ES 3",
                description = "最快，有GPU加速，但可能有渲染错误",
                eglLibrary = null,
                glesLibrary = null,
                needsPreload = false,
                minAndroidVersion = 0
            )
        )

        register(
            RendererInfo(
                id = ID_GL4ES,
                displayName = "GL4ES",
                description = "最完美，游戏兼容性最强，但帧率稍慢",
                eglLibrary = "libEGL_gl4es.so",
                glesLibrary = "libGL_gl4es.so",
                needsPreload = true,
                minAndroidVersion = 0
            ) { _, env ->
                env["RALCORE_RENDERER"] = "gl4es"
                env["LIBGL_ES"] = "3"
                env["LIBGL_MIPMAP"] = "3"
                env["LIBGL_NORMALIZE"] = "1"
                env["LIBGL_NOINTOVLHACK"] = "1"
                env["LIBGL_NOERROR"] = "1"
            }
        )

        register(
            RendererInfo(
                id = ID_GL4ES_ANGLE,
                displayName = "GL4ES + ANGLE",
                description = "翻译成Vulkan，速度和兼容性最佳，推荐高通骁龙使用",
                eglLibrary = "libEGL_gl4es.so",
                glesLibrary = "libGL_gl4es.so",
                needsPreload = true,
                minAndroidVersion = 0
            ) { _, env ->
                env["RALCORE_RENDERER"] = "gl4es"
                env["LIBGL_ES"] = "3"
                env["LIBGL_MIPMAP"] = "3"
                env["LIBGL_NORMALIZE"] = "1"
                env["LIBGL_NOINTOVLHACK"] = "1"
                env["LIBGL_NOERROR"] = "1"
            }
        )

        register(
            RendererInfo(
                id = ID_MOBILEGLUES,
                displayName = "MobileGlues 1.3.3",
                description = "OpenGL 4.6 翻译至 OpenGL ES 3.2（现代化翻译层）",
                eglLibrary = "libmobileglues.so",
                glesLibrary = "libmobileglues.so",
                needsPreload = true,
                minAndroidVersion = 0
            ) { _, env ->
                env["RALCORE_RENDERER"] = "mobileglues"
                env["FNA3D_OPENGL_DRIVER"] = "mobileglues"
                env["MOBILEGLUES_GLES_VERSION"] = "3.2"
                env["FNA3D_MOJOSHADER_PROFILE"] = "glsles3"
                env["MG_DIR_PATH"] =
                    Path(Environment.getExternalStorageDirectory().absolutePath).resolve("MG").toString()
            }
        )

        register(
            RendererInfo(
                id = ID_ANGLE,
                displayName = "ANGLE (Vulkan Backend)",
                description = "OpenGL ES over Vulkan (Google官方)",
                eglLibrary = "libEGL_angle.so",
                glesLibrary = "libGLESv2_angle.so",
                needsPreload = true,
                minAndroidVersion = Build.VERSION_CODES.N
            ) { _, env ->
                env["RALCORE_EGL"] = "libEGL_angle.so"
                env["LIBGL_GLES"] = "libGLESv2_angle.so"
            }
        )

        register(
            RendererInfo(
                id = ID_ZINK,
                displayName = "Zink (Mesa Vulkan)",
                description = "桌面 OpenGL over Vulkan (Mesa Zink + Turnip)",
                eglLibrary = "libOSMesa.so",
                glesLibrary = "libOSMesa.so",
                needsPreload = true,
                minAndroidVersion = Build.VERSION_CODES.N
            ) { _, env ->
                env["RALCORE_RENDERER"] = "vulkan_zink"
                env["GALLIUM_DRIVER"] = "zink"
                env["MESA_LOADER_DRIVER_OVERRIDE"] = "zink"
                env["MESA_GL_VERSION_OVERRIDE"] = "4.3"
                env["MESA_GLSL_VERSION_OVERRIDE"] = "430"
                env["MESA_NO_ERROR"] = "1"
                env["ZINK_DESCRIPTORS"] = "auto"
                env["TU_DEBUG"] = "log"
                env["MESA_LOG"] = "debug"
                env["MESA_DEBUG"] = "1"
                env["LIBGL_DEBUG"] = "verbose"
            }
        )
    }

    @JvmStatic
    fun register(info: RendererInfo) {
        synchronized(rendererStore) {
            if (rendererStore.containsKey(info.id)) {
                throw IllegalStateException("Renderer already registered: ${info.id}")
            }
            rendererStore[info.id] = info
        }
    }

    @JvmStatic
    fun getRendererInfo(rendererId: String): RendererInfo? {
        return synchronized(rendererStore) { rendererStore[rendererId] }
    }

    @JvmStatic
    fun getRendererDisplayName(rendererId: String): String {
        val normalized = normalizeRendererId(rendererId)
        return getRendererInfo(normalized)?.displayName ?: normalized
    }

    @JvmStatic
    fun getRendererDescription(rendererId: String): String {
        val normalized = normalizeRendererId(rendererId)
        return getRendererInfo(normalized)?.description.orEmpty()
    }

    @JvmStatic
    fun getCompatibleRenderers(): MutableList<RendererInfo> {
        val context = getGlobalContext()
        val compatible = mutableListOf<RendererInfo>()
        val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)
        val runtimeLibsDir = File(context.filesDir, RUNTIME_LIBS_DIR)

        val renderers = synchronized(rendererStore) { rendererStore.values.toList() }
        for (renderer in renderers) {
            if (Build.VERSION.SDK_INT < renderer.minAndroidVersion) {
                continue
            }

            var hasLibraries = true
            if (renderer.eglLibrary != null) {
                val eglLibNative = File(nativeLibDir, renderer.eglLibrary)
                val eglLibRuntime = File(runtimeLibsDir, renderer.eglLibrary)
                if (!eglLibNative.exists() && !eglLibRuntime.exists()) {
                    hasLibraries = false
                }
            }

            if (hasLibraries && renderer.glesLibrary != null && renderer.glesLibrary != renderer.eglLibrary) {
                val glesLibNative = File(nativeLibDir, renderer.glesLibrary)
                val glesLibRuntime = File(runtimeLibsDir, renderer.glesLibrary)
                if (!glesLibNative.exists() && !glesLibRuntime.exists()) {
                    hasLibraries = false
                }
            }

            if (hasLibraries) {
                compatible.add(renderer)
            }
        }

        return compatible
    }

    @JvmStatic
    fun isKnownRendererId(id: String): Boolean {
        return synchronized(rendererStore) { rendererStore.containsKey(id) }
    }

    @JvmStatic
    actual fun normalizeRendererId(raw: String?): String {
        val candidate = raw?.trim().orEmpty()
        return if (candidate.isNotEmpty() && isKnownRendererId(candidate)) {
            candidate
        } else {
            synchronized(rendererStore) { rendererStore.keys.firstOrNull() } ?: ID_NATIVE
        }
    }

    @JvmStatic
    fun isRendererCompatible(rendererId: String): Boolean {
        val normalized = normalizeRendererId(rendererId)
        return getCompatibleRenderers().any { it.id == normalized }
    }

    @JvmStatic
    fun getRendererLibraryPath(libraryName: String?): String? {
        if (libraryName == null) return null
        val context = getGlobalContext()
        val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)
        return File(nativeLibDir, libraryName).absolutePath
    }

    @JvmStatic
    fun buildRendererEnv(rendererId: String): Map<String, String?> {
        val rendererInfo = getRendererInfo(normalizeRendererId(rendererId)) ?: return emptyMap()
        val context = getGlobalContext()
        val envMap = mutableMapOf<String, String?>()
        rendererInfo.configureEnv(context, envMap)
        return envMap
    }

    private fun getGlobalContext(): Context {
        val context: Context = GlobalContext.get().get(Context::class, null, null)
        return context.applicationContext
    }
}
