package app.fontflip.install

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

enum class FontStatus { NONE, ORIGINAL, CUSTOM }

/** Tudo que fala com o sistema: pacotes, instalador, lojas e ajustes. */
object SystemActions {

    @Suppress("DEPRECATION")
    fun status(context: Context): FontStatus = try {
        val info = context.packageManager.getPackageInfo(FontApkBuilder.PACKAGE, 0)
        if (info.versionName?.startsWith(FontApkBuilder.VERSION_TAG) == true) FontStatus.CUSTOM else FontStatus.ORIGINAL
    } catch (e: PackageManager.NameNotFoundException) {
        FontStatus.NONE
    }

    /** @return false se ainda falta a permissão "instalar apps desconhecidos" (a tela de ajuste é aberta). */
    fun install(context: Context, apk: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }

    fun uninstallIntent(): Intent =
        Intent(Intent.ACTION_DELETE, Uri.parse("package:${FontApkBuilder.PACKAGE}"))
            .putExtra(Intent.EXTRA_RETURN_RESULT, true)

    fun openGalaxyStore(context: Context): Boolean = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("samsungapps://ProductDetail/${FontApkBuilder.PACKAGE}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess

    fun openDisplaySettings(context: Context) {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
