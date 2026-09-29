package dev.cluvex.zedsecure.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnManager as PlatformVpnManager
import android.net.VpnProfileState
import android.os.Build
import dev.cluvex.zedsecure.core.AppLog as Log
import dev.cluvex.zedsecure.R

class Ikev2EventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PlatformVpnManager.ACTION_VPN_MANAGER_EVENT) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val category = intent.categories?.firstOrNull()
        val errorClass = intent.getIntExtra(PlatformVpnManager.EXTRA_ERROR_CLASS, -1)
        val errorCode = intent.getIntExtra(PlatformVpnManager.EXTRA_ERROR_CODE, -1)
        val state = (intent.getParcelableExtra(
            PlatformVpnManager.EXTRA_VPN_PROFILE_STATE, VpnProfileState::class.java,
        ))?.state
        Log.i(TAG, "VPN event category=$category errorClass=$errorClass errorCode=$errorCode state=$state")
        LogBus.append("I/IKEv2 platform event: ${category ?: "state"} state=$state")

        val reason: String? = when (category) {
            PlatformVpnManager.CATEGORY_EVENT_IKE_ERROR ->
                context.getString(R.string.ikev2_ike_error)
            PlatformVpnManager.CATEGORY_EVENT_NETWORK_ERROR ->
                context.getString(networkErrorRes(errorCode))

            PlatformVpnManager.CATEGORY_EVENT_DEACTIVATED_BY_USER,
            PlatformVpnManager.CATEGORY_EVENT_ALWAYS_ON_STATE_CHANGED -> null
            else -> null
        }

        val recoverable = errorClass == PlatformVpnManager.ERROR_CLASS_RECOVERABLE
        if (reason != null && recoverable) {
            LogBus.append("W/IKEv2 recoverable: $reason (platform will retry)")
            return
        }
        Ikev2Controller.onPlatformEvent(context, state, reason)
    }

    private fun networkErrorRes(code: Int): Int = when (code) {
        PlatformVpnManager.ERROR_CODE_NETWORK_UNKNOWN_HOST -> R.string.ikev2_err_unknown_host
        PlatformVpnManager.ERROR_CODE_NETWORK_PROTOCOL_TIMEOUT -> R.string.ikev2_err_timeout
        PlatformVpnManager.ERROR_CODE_NETWORK_LOST -> R.string.ikev2_err_network_lost
        else -> R.string.ikev2_network_error
    }

    private companion object {
        const val TAG = "Ikev2EventReceiver"
    }
}
