package dev.andes.authentication

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-GCM authenticated storage. Private files excluded from backup; keys never exported. */
class TokenVault(context: Context, name: String) {
    private val alias = "dev.andes.$name.v1"
    private val file = AtomicFile(File(context.noBackupFilesDir, "$name.enc"))
    private val aad = alias.toByteArray(Charsets.UTF_8)

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }

    @Synchronized fun write(value: String) {
        require(value.length <= 512 * 1024)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(aad)
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val stream = file.startWrite()
        try {
            stream.write(byteArrayOf(1, cipher.iv.size.toByte()))
            stream.write(cipher.iv)
            stream.write(encrypted)
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }

    @Synchronized fun read(): String? {
        if (!file.baseFile.exists()) return null
        require(file.baseFile.length() in 30..(1024 * 1024)) { "Invalid credential storage" }
        val bytes = file.readFully()
        require(bytes[0] == 1.toByte() && bytes[1] == 12.toByte()) { "Invalid credential format" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(2, 14)))
        cipher.updateAAD(aad)
        return String(cipher.doFinal(bytes.copyOfRange(14, bytes.size)), Charsets.UTF_8)
    }

    @Synchronized fun clear() {
        // Attempt both removals even if one store fails.
        val keyResult = runCatching {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
        }
        file.delete()
        check(!file.baseFile.exists()) { "Credential file removal failed" }
        keyResult.getOrThrow()
    }
}
