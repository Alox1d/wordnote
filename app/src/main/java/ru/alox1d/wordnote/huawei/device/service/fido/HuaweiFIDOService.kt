package ru.alox1d.wordnote.huawei.domain.services.fido

import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.huawei.hms.support.api.fido.bioauthn.BioAuthnCallback
import com.huawei.hms.support.api.fido.bioauthn.BioAuthnManager
import com.huawei.hms.support.api.fido.bioauthn.BioAuthnPrompt
import com.huawei.hms.support.api.fido.bioauthn.BioAuthnResult
import ru.alox1d.wordnote.huawei.R
import ru.alox1d.wordnote.huawei.domain.service.fido.IFIDOService
import ru.alox1d.wordnote.huawei.ui.dialogs.DialogHelper
import toothpick.InjectConstructor

@InjectConstructor
class HuaweiFIDOService(val context: AppCompatActivity) : IFIDOService {
    private var bioAuthnPrompt: BioAuthnPrompt
    private var callback: ((Boolean) -> Unit)? = null

    init {
        bioAuthnPrompt = createBioAuthnPrompt()
    }

    override fun createBioAuthnPrompt(): BioAuthnPrompt {
        // call back
        val callback: BioAuthnCallback = object : BioAuthnCallback() {
            override fun onAuthError(errMsgId: Int, errString: CharSequence) {
                if (this@HuaweiFIDOService.callback == null) return
                finishAuthentication(false)
                if (errMsgId != BioAuthnPrompt.ERROR_USER_CANCELED &&
                    errMsgId != BioAuthnPrompt.ERROR_CANCELED &&
                    errMsgId != BioAuthnPrompt.ERROR_NEGATIVE_BUTTON) {
                    showResult(context.getString(R.string.auth_failed) + errMsgId)
                }
            }

            override fun onAuthSucceeded(result: BioAuthnResult) {
                finishAuthentication(true)
            }

            override fun onAuthFailed() {
                showResult(context.getString(R.string.auth_failed))
            }
        }
        return BioAuthnPrompt(context, ContextCompat.getMainExecutor(context), callback)
    }

    /**
     * Shows the fingerprint prompt without CryptoObject and allows the user to use the device PIN and password for
     * authentication.
     */
    override fun fingerAuthenticateWithoutCryptoObject(callback: (Boolean) -> Unit) {
        if (this.callback != null) {
            callback(false)
            return
        }
        this.callback = callback
        try {
            val errorCode = BioAuthnManager(context).canAuth()
            if (errorCode != 0) {
                finishAuthentication(false)
                showResult(context.getString(R.string.cant_auth) + errorCode)
                return
            }
            val info = BioAuthnPrompt.PromptInfo.Builder()
                .setTitle(context.getString(R.string.show_hidden_notes))
                .setDeviceCredentialAllowed(true)
                .build()
            bioAuthnPrompt.auth(info)
        } catch (error: RuntimeException) {
            finishAuthentication(false)
            showResult(context.getString(R.string.auth_failed))
        }
    }

    private fun finishAuthentication(success: Boolean) {
        val pending = callback ?: return
        callback = null
        pending(success)
    }

    override fun showResult(msg: String) {
        DialogHelper(context).showResult(context.getString(R.string.auth_result), msg)
    }
}