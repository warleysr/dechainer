package io.github.warleysr.dechainer.security

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom
import androidx.core.content.edit

class SecurityManager {

    companion object {
        private const val CHAR_POOL = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"

        fun generatePassphrase(length: Int = 8): String {
            val random = SecureRandom()
            return (1..length)
                .map { CHAR_POOL[random.nextInt(CHAR_POOL.length)] }
                .joinToString("")
        }

        fun hashPhrase(phrase: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(phrase.toByteArray())
            return bytes.joinToString("") { "%02x".format(it) }
        }

        fun getRecoveryHash(context: Context) : String? {
            return context.getSharedPreferences("recovery_prefs", Context.MODE_PRIVATE).getString("recovery_hash", null)
        }

        fun isRecoveryPhraseSet(context: Context) : Boolean = getRecoveryHash(context) != null

        fun saveRecoveryHash(context: Context, phrase: String) {
            val hash = hashPhrase(phrase)
            val prefs = context.getSharedPreferences("recovery_prefs", Context.MODE_PRIVATE)
            prefs.edit { putString("recovery_hash", hash) }
        }

        fun validatePassphrase(userInput: String, storedHash: String): Boolean {
            val inputHash = hashPhrase(userInput)
            return inputHash == storedHash
        }
    }
}