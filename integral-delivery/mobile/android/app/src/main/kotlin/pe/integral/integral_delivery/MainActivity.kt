package pe.integral.integral_delivery

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Integración Android pequeña, sin SDK de terceros ni permisos de almacenamiento global. */
class MainActivity : FlutterActivity() {
    private var pendingImage: MethodChannel.Result? = null
    private var pendingSave: MethodChannel.Result? = null
    private var saveContent = ""
    private val prefs by lazy { getSharedPreferences("integral", MODE_PRIVATE) }

    private fun encryptionKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "integral.session"
        if (store.containsAlias(alias)) return store.getKey(alias, null) as SecretKey
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }
    private fun decrypt(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "integral/platform").setMethodCallHandler { call, result ->
            try {
                when (call.method) {
                    "read" -> {
                        val key = call.argument<String>("key")!!
                        val value = prefs.getString(key, null)
                        result.success(if (key == "token" && !value.isNullOrEmpty()) try { decrypt(value) } catch (e: Exception) { "" } else value)
                    }
                    "write" -> {
                        val key = call.argument<String>("key")!!
                        val value = call.argument<String>("value") ?: ""
                        prefs.edit().putString(key, if (key == "token" && value.isNotEmpty()) encrypt(value) else value).apply()
                        result.success(true)
                    }
                    "pickImage" -> {
                        if (pendingImage != null) { result.error("busy", "Ya hay un selector abierto.", null) }
                        else {
                            pendingImage = result
                            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE) }, 41)
                        }
                    }
                    "saveFile" -> {
                        if (pendingSave != null) { result.error("busy", "Ya hay un archivo en proceso.", null) }
                        else {
                            pendingSave = result
                            saveContent = call.argument<String>("content") ?: ""
                            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = call.argument<String>("mime") ?: "text/plain"
                                putExtra(Intent.EXTRA_TITLE, call.argument<String>("name") ?: "reporte.txt")
                            }, 42)
                        }
                    }
                    "share" -> {
                        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, call.argument<String>("text")) }, "Compartir resumen"))
                        result.success(true)
                    }
                    "launch" -> {
                        val uri = Uri.parse(call.argument<String>("url"))
                        if (uri.scheme !in listOf("tel", "geo", "https")) throw IllegalArgumentException("Dirección no permitida.")
                        startActivity(Intent(if (uri.scheme == "tel") Intent.ACTION_DIAL else Intent.ACTION_VIEW, uri))
                        result.success(true)
                    }
                    else -> result.notImplemented()
                }
            } catch (e: Exception) {
                if (pendingImage === result) pendingImage = null
                if (pendingSave === result) pendingSave = null
                result.error("action_failed", "No se pudo abrir esta acción. Comprueba que haya una aplicación compatible.", null)
            }
        }
    }

    @Deprecated("API compatible con la versión mínima de Android del proyecto")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 41) {
            val result = pendingImage
            pendingImage = null
            if (resultCode != Activity.RESULT_OK || data?.data == null) { result?.success(null); return }
            try {
                val bytes = contentResolver.openInputStream(data.data!!)?.use { stream ->
                    val out = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var count: Int
                    while (stream.read(buffer).also { count = it } != -1) {
                        out.write(buffer, 0, count)
                        if (out.size() > 8 * 1024 * 1024) throw IllegalArgumentException("Selecciona una imagen de hasta 8 MB.")
                    }
                    out.toByteArray()
                }
                result?.success(bytes)
            } catch (e: Exception) { result?.error("image_failed", e.message ?: "No se pudo leer la imagen.", null) }
        } else if (requestCode == 42) {
            val result = pendingSave
            pendingSave = null
            try {
                if (resultCode == Activity.RESULT_OK && data?.data != null) {
                    contentResolver.openOutputStream(data.data!!)?.use { it.write(saveContent.toByteArray(Charsets.UTF_8)) }
                    result?.success(true)
                } else result?.success(false)
            } catch (e: Exception) { result?.error("save_failed", "No se pudo guardar el archivo.", null) }
            finally { saveContent = "" }
        }
    }
}
