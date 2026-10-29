package app.fontflip.install

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import java.io.File
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Date
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.security.auth.x500.X500Principal

/**
 * Monta e assina, no próprio aparelho, o APK de fonte (formato FlipFont da Samsung):
 * AndroidManifest.xml + assets/xml/<nome>.xml + assets/fonts/<nome>.ttf.
 * Não precisa de template nem de chaves embutidas.
 */
object FontApkBuilder {
    /** Mesmo pacote da Samsung Sans: é ela que o sistema deixa selecionar. */
    const val PACKAGE = "com.monotype.android.font.samsungsans"

    /** Marca no versionName para distinguir a nossa fonte da original. */
    const val VERSION_TAG = "fontflip"

    private const val ASSET = "Samsungsans"
    private const val KEY_ALIAS = "fontflip_signing"
    private const val MIN_SDK = 26

    fun build(context: Context, ttf: File): File {
        val dir = File(context.cacheDir, "apks").apply {
            mkdirs()
            listFiles()?.forEach { it.delete() }
        }
        val unsigned = File(dir, "font_unsigned.apk")
        val signed = File(dir, "font.apk")

        ZipOutputStream(unsigned.outputStream().buffered()).use { zip ->
            zip.put("AndroidManifest.xml", AxmlWriter.manifest(PACKAGE, "$VERSION_TAG-1", "Samsung Sans", 1, MIN_SDK, 30))
            zip.put("assets/xml/$ASSET.xml", fontXml().toByteArray(Charsets.UTF_8))
            zip.putNextEntry(ZipEntry("assets/fonts/$ASSET.ttf"))
            ttf.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }

        ApkSigner.Builder(listOf(signerConfig()))
            .setInputApk(unsigned)
            .setOutputApk(signed)
            .setMinSdkVersion(MIN_SDK)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(false)
            .build()
            .sign()
        unsigned.delete()

        val result = ApkVerifier.Builder(signed).setMinCheckedPlatformVersion(MIN_SDK).build().verify()
        check(result.isVerified) { "assinatura inválida: ${result.errors.joinToString()}" }
        return signed
    }

    private fun ZipOutputStream.put(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }

    private fun fontXml() = """<?xml version="1.0" encoding="utf-8"?>
<font displayname="Samsung sans">
    <sans>
        <file><filename>$ASSET.ttf</filename><droidname>DroidSans.ttf</droidname></file>
        <file><filename>$ASSET.ttf</filename><droidname>DroidSans-Bold.ttf</droidname></file>
    </sans>
</font>
"""

    /** Chave RSA gerada no Android Keystore (nunca sai do aparelho) com certificado autoassinado. */
    private fun signerConfig(): ApkSigner.SignerConfig {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!ks.containsAlias(KEY_ALIAS)) {
            val spec = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setKeySize(2048)
                .setCertificateSubject(X500Principal("CN=FontFlip"))
                .setCertificateNotBefore(Date(0))
                .setCertificateNotAfter(Date(4102444800000L)) // 2100-01-01
                .build()
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
                .apply { initialize(spec) }
                .generateKeyPair()
        }
        val key = ks.getKey(KEY_ALIAS, null) as PrivateKey
        val cert = ks.getCertificate(KEY_ALIAS) as X509Certificate
        return ApkSigner.SignerConfig.Builder("fontflip", key, listOf(cert)).build()
    }
}
