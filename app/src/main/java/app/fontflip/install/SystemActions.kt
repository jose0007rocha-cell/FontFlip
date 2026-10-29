package app.fontflip.install

import android.content.ComponentName
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

    private fun tryStart(context: Context, vararg intents: Intent): Boolean {
        for (intent in intents) {
            if (runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return true
        }
        return false
    }

    /** Tela "Estilo da fonte" da One UI (mesma usada pelo app original). */
    fun openFontStyle(context: Context): Boolean = tryStart(
        context,
        Intent().setClassName("com.android.settings", "com.android.settings.Settings\$SecFontStyleActivity"),
        Intent("com.samsung.settings.FontStyleActivity"),
        Intent().setClassName("com.android.settings", "com.samsung.settings.FontStyleActivity"),
        Intent(Settings.ACTION_DISPLAY_SETTINGS),
    )

    /** Abre o app Samsung Cloud (backup/restauração); se não achar, abre contas e sincronização. */
    fun openSamsungCloud(context: Context): Boolean = tryStart(
        context,
        context.packageManager.getLaunchIntentForPackage("com.samsung.android.scloud") ?: Intent(Settings.ACTION_SYNC_SETTINGS),
        Intent(Settings.ACTION_SYNC_SETTINGS),
    )

    private val GOOD = Regex("backup|restore|privacy", RegexOption.IGNORE_CASE)
    private val BAD = Regex("reset|erase|wipe|factory|format", RegexOption.IGNORE_CASE)

    /** Telas exportadas do Ajustes / Samsung Cloud cujo nome lembra backup, restauração ou privacidade. */
    @Suppress("DEPRECATION")
    fun restoreCandidates(context: Context): List<ComponentName> {
        val found = linkedSetOf<ComponentName>()
        for (pkg in listOf("com.android.settings", "com.samsung.android.scloud")) {
            val info = runCatching {
                context.packageManager.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES)
            }.getOrNull() ?: continue
            info.activities?.forEach {
                if (it.exported && it.enabled && GOOD.containsMatchIn(it.name) && !BAD.containsMatchIn(it.name)) {
                    found += ComponentName(it.packageName, it.name)
                }
            }
        }
        return found.toList()
    }

    /** Abre a tela "Fazer backup e restaurar": a escolhida antes, ou Privacidade, ou Contas. */
    fun openBackupRestore(context: Context, saved: String?): Boolean {
        if (saved != null && saved != PRIVACY) {
            val cn = ComponentName.unflattenFromString(saved)
            if (cn != null && tryStart(context, Intent().setComponent(cn))) return true
        }
        return tryStart(context, Intent(Settings.ACTION_PRIVACY_SETTINGS), Intent(Settings.ACTION_SYNC_SETTINGS))
    }

    /** Abre uma tela exportada pelo nome simples da classe (descoberta no aparelho). */
    private fun startByName(context: Context, simpleName: String): Boolean {
        val cn = restoreCandidates(context).firstOrNull { it.className.substringAfterLast('.') == simpleName }
            ?: return false
        return tryStart(context, Intent().setComponent(cn))
    }

    /** Backup: tela "SamsungBackup"; se falhar, "Fazer backup e restaurar". */
    fun openBackup(context: Context, saved: String?): Boolean =
        startByName(context, "SamsungBackup") || openBackupRestore(context, saved)

    /** Restauração: tela "SetupWizardRestoreActivity"; se falhar, "Fazer backup e restaurar". */
    fun openRestore(context: Context, saved: String?): Boolean =
        startByName(context, "SetupWizardRestoreActivity") || openBackupRestore(context, saved)

    /** Teste manual de uma candidata (null = Privacidade padrão do Android). */
    fun openComponent(context: Context, cn: ComponentName?): Boolean =
        if (cn == null) tryStart(context, Intent(Settings.ACTION_PRIVACY_SETTINGS))
        else tryStart(context, Intent().setComponent(cn))

    const val PRIVACY = "privacy"
}
