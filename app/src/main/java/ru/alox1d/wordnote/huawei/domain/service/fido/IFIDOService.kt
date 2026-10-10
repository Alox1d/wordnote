package ru.alox1d.wordnote.huawei.domain.service.fido

import com.huawei.hms.support.api.fido.bioauthn.BioAuthnPrompt

interface IFIDOService {

    fun createBioAuthnPrompt(): BioAuthnPrompt

    /**
     * Shows the fingerprint prompt without CryptoObject and allows the user to use the device PIN and password for
     * authentication.
     */
    fun fingerAuthenticateWithoutCryptoObject(callback: (Boolean) -> Unit)

    fun showResult(msg: String)
}