package com.tnstory.esimprobe

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProbeAndroidBoundaryTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @After
    fun clearState() {
        context.getSharedPreferences("probe_state", Context.MODE_PRIVATE).edit().clear().commit()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java)
                .deleteNotificationChannel("esim_probe_callbacks")
        }
    }

    @Test
    fun matchingReceiverCallbackPersistsOutcomeAndEmitsPackageStateSignal() {
        val store = ProbeStateStore(context)
        val operationId = store.beginOperation(System.currentTimeMillis())
        var stateChanged: Intent? = null
        val observer = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                stateChanged = intent
            }
        }
        context.registerReceiver(observer, IntentFilter(ProbeCallbackReceiver.ACTION_STATE_CHANGED))
        try {
            dispatchCallback(
                operationId,
                android.telephony.euicc.EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_OK,
            )
        } finally {
            context.unregisterReceiver(observer)
        }

        assertEquals(ProbeOutcome.InitialCallbackOk, store.outcome())
        assertNotNull(stateChanged)
        assertEquals(context.packageName, stateChanged?.`package`)
        assertFalse(store.hasActiveOperation())
    }

    @Test
    fun disabledResolutionChannelClearsResolvableOperation() {
        val store = ProbeStateStore(context)
        val operationId = store.beginOperation(System.currentTimeMillis())
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.deleteNotificationChannel("esim_probe_callbacks")
            manager.createNotificationChannel(
                NotificationChannel(
                    "esim_probe_callbacks",
                    "eSIM probe callbacks",
                    NotificationManager.IMPORTANCE_NONE,
                ),
            )
        }

        dispatchCallback(
            operationId,
            android.telephony.euicc.EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_RESOLVABLE_ERROR,
        )

        assertFalse(store.hasActiveOperation())
        assertEquals(
            ProbeOutcome.LocalFailure(ProbeOutcome.Reason.RESOLUTION_NOTIFICATION_UNAVAILABLE),
            store.outcome(),
        )
    }

    @Test
    fun recreationRefreshesExpiredPersistedOperation() {
        val store = ProbeStateStore(context)
        store.beginOperation(0)

        val activity = Robolectric.buildActivity(ProbeActivity::class.java).setup().get()
        val outcome = findOutcomeView(activity.findViewById(android.R.id.content))
            ?: throw AssertionError("Expected rendered timeout outcome")

        assertTrue(outcome.text.contains(ProbeOutcome.InconclusiveNoCallback.message))
        assertFalse(store.hasActiveOperation())
    }

    @Test
    fun resumedActivityRefreshesVisibleOutcomeAfterReceiverCallback() {
        val store = ProbeStateStore(context)
        val operationId = store.beginOperation(System.currentTimeMillis())
        val activity = Robolectric.buildActivity(ProbeActivity::class.java).setup().get()

        dispatchCallback(
            operationId,
            android.telephony.euicc.EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_OK,
        )

        val outcome = findTextView(
            activity.findViewById(android.R.id.content),
            ProbeOutcome.InitialCallbackOk.message,
        ) ?: throw AssertionError("Expected visible callback outcome without recreation")
        assertTrue(outcome.text.contains(ProbeOutcome.InitialCallbackOk.message))
        assertFalse(store.hasActiveOperation())
    }

    @Test
    fun resumedActivityTimeoutRunnableClearsOperationAtPersistedDeadline() {
        val store = ProbeStateStore(context)
        store.beginOperation(
            System.currentTimeMillis() - ProbeStateModel.TIMEOUT_MILLIS + TIMEOUT_TEST_DELAY_MILLIS,
        )
        val activity = Robolectric.buildActivity(ProbeActivity::class.java).setup().get()
        Thread.sleep(TIMEOUT_TEST_WALL_WAIT_MILLIS)

        Shadows.shadowOf(Looper.getMainLooper()).idleFor(
            TIMEOUT_TEST_DELAY_MILLIS + 1,
            TimeUnit.MILLISECONDS,
        )

        val outcome = findTextView(
            activity.findViewById(android.R.id.content),
            ProbeOutcome.InconclusiveNoCallback.message,
        ) ?: throw AssertionError("Expected visible timeout outcome at persisted deadline")
        assertTrue(outcome.text.contains(ProbeOutcome.InconclusiveNoCallback.message))
        assertFalse(store.hasActiveOperation())
    }

    private fun dispatchCallback(operationId: String, initialResultCode: Int) {
        context.sendOrderedBroadcast(
            Intent(context, ProbeCallbackReceiver::class.java)
                .setAction(ProbeCallbackReceiver.ACTION_DOWNLOAD_CALLBACK)
                .putExtra(ProbeCallbackReceiver.EXTRA_OPERATION_ID, operationId),
            null,
            null,
            null,
            initialResultCode,
            null,
            null,
        )
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun findOutcomeView(view: View): TextView? = when (view) {
        is TextView -> view.takeIf { it.text.contains(ProbeOutcome.InconclusiveNoCallback.message) }
        is ViewGroup -> (0 until view.childCount)
            .firstNotNullOfOrNull { findOutcomeView(view.getChildAt(it)) }
        else -> null
    }

    private fun findTextView(view: View, text: String): TextView? = when (view) {
        is TextView -> view.takeIf { it.text.contains(text) }
        is ViewGroup -> (0 until view.childCount)
            .firstNotNullOfOrNull { findTextView(view.getChildAt(it), text) }
        else -> null
    }

    private companion object {
        const val TIMEOUT_TEST_DELAY_MILLIS = 100L
        const val TIMEOUT_TEST_WALL_WAIT_MILLIS = 200L
    }
}
