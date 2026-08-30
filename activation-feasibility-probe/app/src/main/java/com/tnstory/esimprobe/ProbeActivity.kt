package com.tnstory.esimprobe

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.euicc.DownloadableSubscription
import android.telephony.euicc.EuiccManager
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class ProbeActivity : AppCompatActivity() {
    private lateinit var activationCode: EditText
    private lateinit var outcomeView: TextView
    private lateinit var stateStore: ProbeStateStore
    private var euiccManager: EuiccManager? = null
    private val timeoutHandler = Handler(Looper.getMainLooper())
    private val timeoutRunnable = Runnable {
        stateStore.expireTimedOut(System.currentTimeMillis())
        renderPersistedOutcome()
        scheduleTimeout()
    }
    private val stateChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            renderPersistedOutcome()
            scheduleTimeout()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        stateStore = ProbeStateStore(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST_CODE)
        }
        euiccManager = getSystemService(EuiccManager::class.java)
        setContentView(createContentView())
        renderPersistedOutcome()
        handleResolutionIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleResolutionIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        ContextCompat.registerReceiver(
            this,
            stateChangedReceiver,
            IntentFilter(ProbeCallbackReceiver.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        stateStore.expireTimedOut(System.currentTimeMillis())
        renderPersistedOutcome()
        scheduleTimeout()
    }

    override fun onPause() {
        timeoutHandler.removeCallbacks(timeoutRunnable)
        unregisterReceiver(stateChangedReceiver)
        super.onPause()
    }

    @Deprecated("Resolution uses the framework activity-result contract.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RESOLUTION_REQUEST_CODE) {
            stateStore.recordResolutionReturn()
            renderPersistedOutcome()
            scheduleTimeout()
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
                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                inputType = android.text.InputType.TYPE_CLASS_TEXT
                imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            }
            addView(
                activationCode,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
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
        if (stateStore.hasActiveOperation()) {
            publish(ProbeOutcome.SubmissionInProgress)
            return
        }
        if (body.isBlank()) {
            stateStore.recordLocalFailure(ProbeOutcome.Reason.EMPTY_ACTIVATION_CODE)
            renderPersistedOutcome()
            return
        }
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_EUICC)) {
            stateStore.recordLocalFailure(ProbeOutcome.Reason.ESIM_UNSUPPORTED)
            renderPersistedOutcome()
            return
        }
        val manager = euiccManager
        if (manager == null || !manager.isEnabled) {
            stateStore.recordLocalFailure(ProbeOutcome.Reason.ESIM_DISABLED)
            renderPersistedOutcome()
            return
        }

        val operationId = stateStore.beginOperation(System.currentTimeMillis())
        try {
            manager.downloadSubscription(
                DownloadableSubscription.forActivationCode(body),
                false,
                callbackPendingIntent(operationId),
            )
        } catch (_: SecurityException) {
            stateStore.failActiveOperation(ProbeOutcome.Reason.REQUEST_REJECTED)
        } catch (_: IllegalArgumentException) {
            stateStore.failActiveOperation(ProbeOutcome.Reason.REQUEST_REJECTED)
        }
        renderPersistedOutcome()
        scheduleTimeout()
    }

    private fun handleResolutionIntent(intent: Intent) {
        if (intent.action != ProbeCallbackReceiver.ACTION_OPEN_RESOLUTION) return
        val operationId = intent.getStringExtra(ProbeCallbackReceiver.EXTRA_OPERATION_ID) ?: return
        @Suppress("DEPRECATION")
        val callbackIntent = intent.getParcelableExtra<Intent>(
            ProbeCallbackReceiver.EXTRA_CALLBACK_INTENT,
        ) ?: return
        if (!stateStore.beginResolution(operationId)) return

        try {
            val manager = euiccManager
            if (manager == null) {
                stateStore.failActiveOperation(ProbeOutcome.Reason.RESOLUTION_UNAVAILABLE)
            } else {
                manager.startResolutionActivity(
                    this,
                    RESOLUTION_REQUEST_CODE,
                    callbackIntent,
                    callbackPendingIntent(operationId),
                )
                stateStore.publishResolutionUi(operationId)
            }
        } catch (_: IllegalArgumentException) {
            stateStore.failActiveOperation(ProbeOutcome.Reason.RESOLUTION_UNAVAILABLE)
        } catch (_: IntentSender.SendIntentException) {
            stateStore.failActiveOperation(ProbeOutcome.Reason.RESOLUTION_UNAVAILABLE)
        }
        renderPersistedOutcome()
        scheduleTimeout()
    }

    private fun callbackPendingIntent(operationId: String): PendingIntent {
        val callback = Intent(this, ProbeCallbackReceiver::class.java)
            .setAction(ProbeCallbackReceiver.ACTION_DOWNLOAD_CALLBACK)
            .setData(ProbeCallbackReceiver.callbackUri(operationId))
            .putExtra(ProbeCallbackReceiver.EXTRA_OPERATION_ID, operationId)
        return PendingIntent.getBroadcast(
            this,
            operationId.hashCode(),
            callback,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    private fun renderPersistedOutcome() {
        if (::outcomeView.isInitialized) {
            outcomeView.text = stateStore.outcomeSequence().joinToString("\n") { it.message }
        }
    }

    private fun scheduleTimeout() {
        timeoutHandler.removeCallbacks(timeoutRunnable)
        stateStore.timeoutDelayMillis(System.currentTimeMillis())?.let { delay ->
            timeoutHandler.postDelayed(timeoutRunnable, delay)
        }
    }

    private fun publish(outcome: ProbeOutcome) {
        outcomeView.text = outcome.message
    }

    private companion object {
        const val RESOLUTION_REQUEST_CODE = 101
        const val NOTIFICATION_PERMISSION_REQUEST_CODE = 102
    }
}
