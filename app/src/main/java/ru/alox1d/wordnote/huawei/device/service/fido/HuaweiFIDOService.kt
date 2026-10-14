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
    private lateinit var callback: (Boolean) -> Unit

    init {
        bioAuthnPrompt = createBioAuthnPrompt()
    }

    override fun createBioAuthnPrompt(): BioAuthnPrompt {
        // call back
        val callback: BioAuthnCallback = object : BioAuthnCallback() {
            override fun onAuthError(errMsgId: Int, errString: CharSequence) {
                if (errMsgId != 10)
                    showResult(context.getString(R.string.auth_failed) + errMsgId + ", \n"+context.getString(R.string.error_msg) + errString)
            }

            override fun onAuthSucceeded(result: BioAuthnResult) {
                callback(true)
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
        this.callback = callback
        // Checks whether fingerprint authentication is available.
        val bioAuthnManager = BioAuthnManager(context)
        val errorCode = bioAuthnManager.canAuth()
        if (errorCode != 0) {
            showResult(context.getString(R.string.cant_auth) + errorCode)
            callback(false)
            return
        }

        // build prompt info
        val builder = BioAuthnPrompt.PromptInfo.Builder().setTitle(context.getString(R.string.show_hidden_notes))

        // The user will first be prompted to authenticate with biometrics, but also given the option to
        // authenticate with their device PIN, pattern, or password. setNegativeButtonText(CharSequence) should
        // not be set if this is set to true.
        builder.setDeviceCredentialAllowed(true)

        // Set the text for the negative button. setDeviceCredentialAllowed(true) should not be set if this button text
        // is set.
        // builder.setNegativeButtonText("This is the 'Cancel' button.");
        val info = builder.build()
        bioAuthnPrompt.auth(info)
    }

    override fun showResult(msg: String) {
        DialogHelper(context).showResult(context.getString(R.string.auth_result), msg)
    }
}