package io.github.warleysr.dechainer.security

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.security.MessageDigest
import java.security.SecureRandom
import androidx.core.content.edit

class SecurityManager {

    companion object {
        private const val CHAR_POOL = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        private val isRecoveryKeySet = mutableStateOf(false)
        
        var sessionEndTime by mutableLongStateOf(0L)
            private set
            
        fun isSessionActive(): Boolean = System.currentTimeMillis() < sessionEndTime

        fun startSession() {
            sessionEndTime = System.currentTimeMillis() + (10 * 60 * 1000) // 10 minutes
        }

        fun endSession() {
            sessionEndTime = 0L
        }

        fun generatePassphrase(length: Int = 16): String {
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

        fun isRecoveryPhraseSet(context: Context) : Boolean  {
            isRecoveryKeySet.value = getRecoveryHash(context) != null
            return isRecoveryKeySet.value
        }

        fun saveRecoveryHash(context: Context, phrase: String) {
            val hash = hashPhrase(phrase)
            val prefs = context.getSharedPreferences("recovery_prefs", Context.MODE_PRIVATE)
            prefs.edit { putString("recovery_hash", hash) }
            isRecoveryKeySet.value = true
        }

        fun validatePassphrase(userInput: String, storedHash: String): Boolean {
            if (isSessionActive()) return true

            val inputHash = hashPhrase(userInput)
            val success =  inputHash == storedHash

            if (success)
                startSession()

            return success
        }
    }
}
