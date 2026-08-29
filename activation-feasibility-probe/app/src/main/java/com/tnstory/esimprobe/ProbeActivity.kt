package com.tnstory.esimprobe

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.euicc.DownloadableSubscription
import android.telephony.euicc.EuiccManager
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class ProbeActivity : AppCompatActivity() {
    private lateinit var activationCode: EditText
    private lateinit var outcomeView: TextView
    private var euiccManager: EuiccManager? = null

    private var receiverRegistered = false
    private var awaitingTerminalCallback = false
    private var resolutionStarted = false
    private var terminalCallbackReceived = false

    private val callbackReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (resultCode) {
                EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_OK -> {
                    publish(
                        if (awaitingTerminalCallback) {
                            ProbeOutcome.TerminalCallbackOk
                        } else {
                            ProbeOutcome.InitialCallbackOk
                        },
                    )
                    awaitingTerminalCallback = false
                    terminalCallbackReceived = true
                }

                EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_RESOLVABLE_ERROR -> {
                    if (awaitingTerminalCallback || resolutionStarted) {
                        publish(ProbeOutcome.TerminalCallbackResolvableError)
                        awaitingTerminalCallback = false
                        terminalCallbackReceived = true
                    } else {
                        publish(ProbeOutcome.InitialCallbackResolvableError)
                        openResolution(intent)
                    }
                }

                else -> {
                    publish(
                        if (awaitingTerminalCallback) {
                            ProbeOutcome.TerminalCallbackError
                        } else {
                            ProbeOutcome.InitialCallbackError
                        },
                    )
                    awaitingTerminalCallback = false
                    terminalCallbackReceived = true
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        euiccManager = getSystemService(EuiccManager::class.java)
        setContentView(createContentView())
        registerCallbackReceiver()
        publish(ProbeOutcome.Ready)
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            unregisterReceiver(callbackReceiver)
            receiverRegistered = false
        }
        super.onDestroy()
    }

    @Deprecated("Resolution uses the framework activity-result contract.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RESOLUTION_REQUEST_CODE) {
            // This return does not determine download success; the callback receiver remains authoritative.
            if (!terminalCallbackReceived) {
                publish(ProbeOutcome.ResolutionCancelledOrUnknown)
            }
        }
    }

    private fun createContentView(): View {
        val padding = (24 * resources.displayMetrics.density).toInt()
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)

            addView(TextView(context).apply {
                text = "eSIM activation feasibility probe"
                textSize = 20f
            })
            activationCode = EditText(context).apply {
                hint = "Operator activation-code body"
                isSaveEnabled = false
                setFreezesText(false)
                inputType = android.text.InputType.TYPE_CLASS_TEXT
            }
            addView(activationCode, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
            addView(Button(context).apply {
                text = "Submit to system"
                setOnClickListener { dispatchDownload() }
            })
            outcomeView = TextView(context)
            addView(outcomeView)
        }
    }

    private fun dispatchDownload() {
        val body = activationCode.text.toString()
        activationCode.text.clear()
        if (body.isBlank()) {
            publish(ProbeOutcome.LocalFailure(ProbeOutcome.Reason.EMPTY_ACTIVATION_CODE))
            return
        }
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_EUICC)) {
            publish(ProbeOutcome.LocalFailure(ProbeOutcome.Reason.ESIM_UNSUPPORTED))
            return
        }
        val manager = euiccManager
        if (manager == null || !manager.isEnabled) {
            publish(ProbeOutcome.LocalFailure(ProbeOutcome.Reason.ESIM_DISABLED))
            return
        }

        try {
            val subscription = DownloadableSubscription.forActivationCode(body)
            resolutionStarted = false
            awaitingTerminalCallback = false
            terminalCallbackReceived = false
            manager.downloadSubscription(subscription, false, callbackPendingIntent())
            publish(ProbeOutcome.AppDispatch)
        } catch (_: SecurityException) {
            publish(ProbeOutcome.LocalFailure(ProbeOutcome.Reason.REQUEST_REJECTED))
        } catch (_: IllegalArgumentException) {
            publish(ProbeOutcome.LocalFailure(ProbeOutcome.Reason.REQUEST_REJECTED))
        }
    }

    private fun openResolution(callbackIntent: Intent) {
        resolutionStarted = true
        awaitingTerminalCallback = true
        try {
            val manager = euiccManager
            if (manager == null) {
                awaitingTerminalCallback = false
                publish(ProbeOutcome.LocalFailure(ProbeOutcome.Reason.RESOLUTION_UNAVAILABLE))
                return
            }
            manager.startResolutionActivity(
                this,
                RESOLUTION_REQUEST_CODE,
                callbackIntent,
                callbackPendingIntent(),
            )
            publish(ProbeOutcome.ResolutionUi)
        } catch (_: IllegalArgumentException) {
            awaitingTerminalCallback = false
            publish(ProbeOutcome.LocalFailure(ProbeOutcome.Reason.RESOLUTION_UNAVAILABLE))
        } catch (_: IntentSender.SendIntentException) {
            awaitingTerminalCallback = false
            publish(ProbeOutcome.LocalFailure(ProbeOutcome.Reason.RESOLUTION_UNAVAILABLE))
        }
    }

    private fun callbackPendingIntent(): PendingIntent {
        val callback = Intent(ACTION_DOWNLOAD_CALLBACK).setPackage(packageName)
        return PendingIntent.getBroadcast(
            this,
            CALLBACK_REQUEST_CODE,
            callback,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    private fun registerCallbackReceiver() {
        val filter = IntentFilter(ACTION_DOWNLOAD_CALLBACK)
        ContextCompat.registerReceiver(
            this,
            callbackReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiverRegistered = true
    }

    private fun publish(outcome: ProbeOutcome) {
        outcomeView.text = outcome.message
    }

    private companion object {
        const val ACTION_DOWNLOAD_CALLBACK = "com.tnstory.esimprobe.DOWNLOAD_CALLBACK"
        const val CALLBACK_REQUEST_CODE = 100
        const val RESOLUTION_REQUEST_CODE = 101
    }
}
