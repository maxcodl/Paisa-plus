package com.paisaplus.compose.update

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.File

/**
 * Optional installs through Shizuku (Settings › About › App updates › Install with Shizuku): `pm install` runs with
 * shell rights, so Android doesn't ask for confirmation. Paisa+ is recorded as the installer, which also lets later
 * updates through the normal installer go through silently. Anything off (Shizuku not running, no permission,
 * pm failing) falls back to the normal installer.
 */
object ShizukuInstall {
    const val REQUEST_CODE = 4210

    fun running(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun granted(): Boolean = running() && runCatching { !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)

    /** Shows Shizuku's own permission prompt; the result arrives through [Shizuku.addRequestPermissionResultListener]. */
    fun request() = runCatching { Shizuku.requestPermission(REQUEST_CODE) }

    /** True when pm accepted the APK. On success this process is usually killed right away, as the app is replaced. */
    fun install(ctx: Context, apk: File): Boolean {
        if (!granted()) return false
        return runCatching {
            // Shizuku keeps newProcess out of its public API; it is the documented way to run a shell command though.
            val newProcess = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
                .apply { isAccessible = true }
            val cmd = arrayOf("pm", "install", "-r", "-i", ctx.packageName, "-S", apk.length().toString())
            val p = newProcess.invoke(null, cmd, null, null) as Process
            p.outputStream.use { out -> apk.inputStream().use { it.copyTo(out) } }
            p.waitFor() == 0
        }.getOrDefault(false)
    }
}
