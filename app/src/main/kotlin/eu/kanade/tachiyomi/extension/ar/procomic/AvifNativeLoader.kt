package eu.kanade.tachiyomi.extension.ar.procomic

import android.content.Context
import android.os.Build
import org.aomedia.avif.android.AvifDecoder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

/**
 * Resolves and loads the AOMedia AVIF native library (`libavif_android.so`) in Mihon/Tachiyomi.
 *
 * Mihon instantiates extension classloaders without passing a `librarySearchPath`
 * (`DelegateLastClassLoaderCompat(appInfo.sourceDir, null, context.classLoader)`).
 * Consequently, `System.loadLibrary("avif_android")` in `AvifDecoder`'s static initializer
 * fails silently with `UnsatisfiedLinkError`.
 *
 * This loader implements a multi-tier resolution strategy:
 * 1. Test if native symbols are already bound (`AvifDecoder.versionString()`).
 * 2. Attempt standard `System.loadLibrary("avif_android")`.
 * 3. Locate `libavif_android.so` in the extension package or host app `nativeLibraryDir`
 *    and invoke `System.load(...)` directly with the absolute path.
 * 4. Locate the extension APK (via PackageInfo, ClassLoader dex path, or host private files)
 *    and extract `lib/<supported_abi>/libavif_android.so` to the app's `codeCacheDir` or `cacheDir`,
 *    then load it via `System.load(...)`.
 */
object AvifNativeLoader {

    private const val LIB_NAME = "libavif_android.so"
    private const val EXTENSION_PACKAGE = "eu.kanade.tachiyomi.extension.ar.procomic"

    @Volatile
    private var isLoaded = false

    /**
     * Checks whether the AOMedia AVIF native library is loaded and operational.
     */
    fun isFunctional(): Boolean {
        return runCatching {
            val v = AvifDecoder.versionString()
            !v.isNullOrEmpty()
        }.getOrDefault(false)
    }

    /**
     * Ensures `libavif_android.so` is loaded into the ART runtime.
     * Returns true if the library is loaded and verified functional.
     */
    @Synchronized
    fun ensureLoaded(context: Context? = null): Boolean {
        if (isLoaded && isFunctional()) return true

        // Tier 1: Check if already functional
        if (isFunctional()) {
            isLoaded = true
            return true
        }

        // Tier 2: Try standard System.loadLibrary
        runCatching {
            System.loadLibrary("avif_android")
        }
        if (isFunctional()) {
            isLoaded = true
            return true
        }

        val ctx = resolveContext(context)

        // Tier 3: Search nativeLibraryDir from PackageInfo or ApplicationInfo
        if (ctx != null && loadFromNativeDirs(ctx)) {
            isLoaded = true
            return true
        }

        // Tier 4: Extract from APK / container files into codeCacheDir
        if (ctx != null && extractAndLoadFromApk(ctx)) {
            isLoaded = true
            return true
        }

        // Tier 5: Try finding any APK from ClassLoader or scanning filesystem
        if (extractAndLoadFromClassLoaderOrScan(ctx)) {
            isLoaded = true
            return true
        }

        ProComicDiag.logStage(
            "PAGES",
            96,
            "AvifNativeLoader failed to load libavif_android.so. ABIs=${Build.SUPPORTED_ABIS.joinToString()}",
        )
        return false
    }

    private fun resolveContext(explicitContext: Context?): Context? {
        if (explicitContext != null) return explicitContext
        ProComic.applicationContext?.let { return it }

        return runCatching {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val method = activityThreadClass.getMethod("currentApplication")
            method.invoke(null) as? Context
        }.getOrNull()
    }

    private fun loadFromNativeDirs(context: Context): Boolean {
        val candidateDirs = mutableListOf<File>()

        // 1. Extension package's native library dir
        runCatching {
            val packageInfo = context.packageManager.getPackageInfo(EXTENSION_PACKAGE, 0)
            packageInfo.applicationInfo?.nativeLibraryDir?.let { candidateDirs.add(File(it)) }
        }

        // 2. Context application's native library dir
        context.applicationInfo?.nativeLibraryDir?.let { candidateDirs.add(File(it)) }

        for (dir in candidateDirs) {
            val libFile = File(dir, LIB_NAME)
            if (libFile.isFile && libFile.canRead()) {
                val loaded = runCatching {
                    System.load(libFile.absolutePath)
                    isFunctional()
                }.getOrDefault(false)
                if (loaded) return true
            }
        }
        return false
    }

    private fun extractAndLoadFromApk(context: Context): Boolean {
        val apkPaths = mutableSetOf<String>()

        // 1. Extension PackageInfo sourceDir & publicSourceDir
        runCatching {
            val packageInfo = context.packageManager.getPackageInfo(EXTENSION_PACKAGE, 0)
            packageInfo.applicationInfo?.sourceDir?.let { apkPaths.add(it) }
            packageInfo.applicationInfo?.publicSourceDir?.let { apkPaths.add(it) }
            packageInfo.applicationInfo?.splitSourceDirs?.forEach { apkPaths.add(it) }
        }

        // 2. Context sourceDir & packageCodePath
        context.applicationInfo?.sourceDir?.let { apkPaths.add(it) }
        runCatching { context.packageCodePath?.let { apkPaths.add(it) } }

        // 3. Search in context.filesDir / exts
        runCatching {
            val extsDir = File(context.filesDir, "exts")
            if (extsDir.isDirectory) {
                extsDir.listFiles()?.forEach { file ->
                    if (file.name.contains("procomic", ignoreCase = true) &&
                        (file.extension == "apk" || file.extension == "ext" || file.extension == "zip")
                    ) {
                        apkPaths.add(file.absolutePath)
                    }
                }
            }
        }

        val targetDir = context.codeCacheDir ?: context.cacheDir ?: context.filesDir
        for (apkPath in apkPaths) {
            val apkFile = File(apkPath)
            if (apkFile.isFile && apkFile.canRead()) {
                if (extractAndLoadFromZip(apkFile, targetDir)) {
                    return true
                }
            }
        }
        return false
    }

    private fun extractAndLoadFromClassLoaderOrScan(context: Context?): Boolean {
        val targetDir = context?.codeCacheDir ?: context?.cacheDir ?: context?.filesDir
            ?: File("/data/local/tmp")

        // Parse dex paths from ClassLoader.toString()
        val classLoaderStr = AvifNativeLoader::class.java.classLoader?.toString() ?: ""
        val pathRegex = Regex("""(/[^:\s"'\(\)\]]+\.(?:apk|ext|zip))""")
        val matches = pathRegex.findAll(classLoaderStr).map { it.groupValues[1] }.toList()

        for (path in matches) {
            val file = File(path)
            if (file.isFile && file.canRead()) {
                if (extractAndLoadFromZip(file, targetDir)) {
                    return true
                }
            }
        }
        return false
    }

    private fun extractAndLoadFromZip(zipFileOnDisk: File, targetDir: File): Boolean {
        return runCatching {
            ZipFile(zipFileOnDisk).use { zip ->
                val supportedAbis = Build.SUPPORTED_ABIS ?: emptyArray()
                for (abi in supportedAbis) {
                    val entryName = "lib/$abi/$LIB_NAME"
                    val entry = zip.getEntry(entryName) ?: continue

                    if (!targetDir.exists()) {
                        targetDir.mkdirs()
                    }
                    val targetFile = File(targetDir, "libavif_android_${abi}.so")

                    // Extract if not present or size differs
                    if (!targetFile.exists() || targetFile.length() != entry.size) {
                        val tempFile = File(targetDir, "libavif_android_${abi}.tmp")
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(tempFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        if (targetFile.exists()) {
                            targetFile.delete()
                        }
                        tempFile.renameTo(targetFile)
                    }

                    targetFile.setReadable(true, false)
                    targetFile.setExecutable(true, false)

                    runCatching {
                        System.load(targetFile.absolutePath)
                    }
                    if (isFunctional()) {
                        return true
                    }
                }
                false
            }
        }.getOrDefault(false)
    }
}
