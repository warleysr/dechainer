package io.github.warleysr.dechainer.security

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.security.SecureRandom
import androidx.core.content.edit

class SecurityManager {

    companion object {
        private const val CHAR_POOL = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        private val isRecoveryKeySet = mutableStateOf(false)
        
        var sessionEndTime by mutableLongStateOf(0L)
            private set

        fun isSessionActive(): Boolean = System.currentTimeMillis() < sessionEndTime

        private fun startSession() {
            sessionEndTime = System.currentTimeMillis() + (10 * 60 * 1000) // 10 minutes
        }

        fun endSession() {
            sessionEndTime = 0L
        }

        fun generateRecoveryCode(length: Int = 16): String {
            val random = SecureRandom()
            return (1..length)
                .map { CHAR_POOL[random.nextInt(CHAR_POOL.length)] }
                .joinToString("")
        }

        fun getRecoveryCode(context: Context) : String? {
            return context.getSharedPreferences("recovery_prefs", Context.MODE_PRIVATE).getString("recovery_code", null)
        }

        fun isRecoveryCodeSet(context: Context) : Boolean  {
            isRecoveryKeySet.value = getRecoveryCode(context) != null
            return isRecoveryKeySet.value
        }

        fun saveRecoveryCode(context: Context, code: String) {
            val prefs = context.getSharedPreferences("recovery_prefs", Context.MODE_PRIVATE)
            prefs.edit { putString("recovery_code", code) }
            isRecoveryKeySet.value = true
        }

        fun validateRecoveryCode(userInput: String, storedKey: String): Boolean {
            if (isSessionActive()) return true

            val success =  userInput == storedKey

            if (success)
                startSession()

            return success
        }
    }
}
