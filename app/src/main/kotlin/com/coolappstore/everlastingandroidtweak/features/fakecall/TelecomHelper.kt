package com.coolappstore.everlastingandroidtweak.features.fakecall

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager

class TelecomHelper(private val context: Context) {

    private val telecomManager: TelecomManager =
        context.getSystemService(TelecomManager::class.java)

    fun accountHandle(): PhoneAccountHandle {
        return PhoneAccountHandle(
            ComponentName(context, FakeCallConnectionService::class.java),
            ACCOUNT_ID
        )
    }

    fun registerOrUpdatePhoneAccount(label: String): Boolean {
        return runCatching {
            val phoneAccount = PhoneAccount.Builder(accountHandle(), label)
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                .build()

            telecomManager.registerPhoneAccount(phoneAccount)
            true
        }.getOrDefault(false)
    }

    fun isAccountEnabled(): Boolean {
        return runCatching {
            telecomManager.getPhoneAccount(accountHandle())?.isEnabled == true
        }.getOrDefault(false)
    }

    fun triggerIncomingCall(callerName: String, callerNumber: String): Boolean {
        return runCatching {
            val normalizedNumber = callerNumber.trim()
            val incomingExtras = Bundle().apply {
                putString(EXTRA_FAKE_CALLER_NAME, callerName.trim())
                putString(EXTRA_FAKE_CALLER_NUMBER, normalizedNumber)
            }

            val extras = Bundle().apply {
                putParcelable(
                    TelecomManager.EXTRA_INCOMING_CALL_ADDRESS,
                    Uri.fromParts(PhoneAccount.SCHEME_TEL, normalizedNumber, null)
                )
                putBundle(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS, incomingExtras)
            }

            telecomManager.addNewIncomingCall(accountHandle(), extras)
            true
        }.getOrDefault(false)
    }

    companion object {
        const val ACCOUNT_ID = "fake_call_provider_account"
        const val EXTRA_FAKE_CALLER_NAME = "extra_fake_caller_name"
        const val EXTRA_FAKE_CALLER_NUMBER = "extra_fake_caller_number"
    }
}
