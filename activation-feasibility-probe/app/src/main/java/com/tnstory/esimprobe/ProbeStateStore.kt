package com.tnstory.esimprobe

import android.content.Context
import android.telephony.euicc.EuiccManager
import java.util.UUID

/** Android persistence adapter for the pure, non-secret [ProbeStateModel]. */
class ProbeStateStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun hasActiveOperation(): Boolean = synchronized(LOCK) { load().hasActiveOperation }
    fun beginOperation(nowMillis: Long): String = synchronized(LOCK) {
        val id = UUID.randomUUID().toString()
        save(load().beginOperation(id, nowMillis))
        id
    }
    fun outcome(): ProbeOutcome = synchronized(LOCK) { load().outcome }
    fun outcomeSequence(): List<ProbeOutcome> = synchronized(LOCK) { load().outcomes }
    fun timeoutDelayMillis(nowMillis: Long): Long? = synchronized(LOCK) {
        load().timeoutDelayMillis(nowMillis)
    }
    fun failActiveOperation(reason: ProbeOutcome.Reason) = synchronized(LOCK) { save(load().failActiveOperation(reason)) }
    fun recordLocalFailure(reason: ProbeOutcome.Reason) = synchronized(LOCK) { save(load().recordLocalFailure(reason)) }
    fun expireTimedOut(nowMillis: Long): Boolean = synchronized(LOCK) {
        val model = load().expireTimedOut(nowMillis)
        save(model)
        !model.hasActiveOperation && model.outcome == ProbeOutcome.InconclusiveNoCallback
    }
    fun onCallback(operationId: String, resultCode: Int, nowMillis: Long): CallbackResult = synchronized(LOCK) {
        val transition = load().onCallback(operationId, resultCode.asKind(), nowMillis)
        save(transition.model)
        when (transition.disposition) {
            ProbeStateModel.CallbackDisposition.IGNORE -> CallbackResult.Ignore
            ProbeStateModel.CallbackDisposition.ACCEPTED -> CallbackResult.Accepted
            ProbeStateModel.CallbackDisposition.OPEN_RESOLUTION -> CallbackResult.OpenResolution
        }
    }
    fun beginResolution(operationId: String): Boolean = synchronized(LOCK) {
        val transition = load().beginResolution(operationId)
        save(transition.model)
        transition.accepted
    }
    fun publishResolutionUi(operationId: String) = synchronized(LOCK) { save(load().publishResolutionUi(operationId)) }
    fun recordResolutionReturn() = synchronized(LOCK) { save(load().recordResolutionReturn()) }

    private fun load() = ProbeStateModel.restore(
        preferences.getString(KEY_OPERATION_ID, null),
        preferences.getString(KEY_STAGE, null),
        preferences.getString(KEY_OUTCOME, null),
        preferences.getLong(KEY_STARTED_AT, NO_STARTED_AT),
        preferences.getString(KEY_SEQUENCE, null),
        preferences.getLong(KEY_DEADLINE_AT, NO_STARTED_AT),
    )
    private fun save(model: ProbeStateModel) {
        preferences.edit().putString(KEY_STAGE, model.stage.name).putString(KEY_OUTCOME, model.outcomeToken)
            .putLong(KEY_STARTED_AT, model.startedAtMillis).putLong(KEY_DEADLINE_AT, model.deadlineAtMillis)
            .putString(KEY_SEQUENCE, model.sequenceToken).apply {
                if (model.operationId == null) remove(KEY_OPERATION_ID) else putString(KEY_OPERATION_ID, model.operationId)
            }.commit()
    }
    private fun Int.asKind() = when (this) {
        EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_OK -> ProbeStateModel.CallbackKind.OK
        EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_RESOLVABLE_ERROR -> ProbeStateModel.CallbackKind.RESOLVABLE_ERROR
        else -> ProbeStateModel.CallbackKind.ERROR
    }
    enum class CallbackResult { Ignore, Accepted, OpenResolution }
    private companion object {
        val LOCK = Any()
        const val PREFERENCES_NAME = "probe_state"
        const val KEY_OPERATION_ID = "operation_id"; const val KEY_STAGE = "stage"
        const val KEY_OUTCOME = "outcome"; const val KEY_STARTED_AT = "started_at"
        const val KEY_SEQUENCE = "sequence"; const val KEY_DEADLINE_AT = "deadline_at"
        const val NO_STARTED_AT = -1L
    }
}

/** Pure persisted state reducer. Tokens contain no activation material. */
class ProbeStateModel private constructor(
    val operationId: String?, val stage: Stage, private val current: StoredOutcome,
    val startedAtMillis: Long, private val history: List<StoredOutcome>,
    val deadlineAtMillis: Long,
) {
    val hasActiveOperation get() = operationId != null
    val outcome get() = current.toProbeOutcome()
    val outcomes get() = history.map { it.toProbeOutcome() }
    val outcomeToken get() = current.name
    val sequenceToken get() = history.joinToString(",") { it.name }

    fun beginOperation(id: String, now: Long): ProbeStateModel {
        check(!hasActiveOperation)
        return state(id, Stage.DISPATCHED, StoredOutcome.APP_DISPATCH, now, now + TIMEOUT_MILLIS)
    }
    fun failActiveOperation(reason: ProbeOutcome.Reason) = complete(StoredOutcome.local(reason))
    fun recordLocalFailure(reason: ProbeOutcome.Reason) = state(null, Stage.TERMINAL, StoredOutcome.local(reason), NO_STARTED_AT)
    fun expireTimedOut(now: Long): ProbeStateModel =
        if (hasActiveOperation && now >= deadlineAtMillis) complete(StoredOutcome.INCONCLUSIVE_NO_CALLBACK) else this
    fun timeoutDelayMillis(now: Long): Long? =
        if (hasActiveOperation) (deadlineAtMillis - now).coerceAtLeast(0) else null

    fun onCallback(id: String, kind: CallbackKind, now: Long): CallbackTransition {
        val timely = expireTimedOut(now)
        if (timely !== this) return CallbackTransition(timely, CallbackDisposition.IGNORE)
        if (id != operationId) return CallbackTransition(this, CallbackDisposition.IGNORE)
        return when (stage) {
            Stage.DISPATCHED -> when (kind) {
                CallbackKind.OK -> accepted(complete(StoredOutcome.INITIAL_CALLBACK_OK))
                CallbackKind.RESOLVABLE_ERROR -> CallbackTransition(
                    state(id, Stage.NEEDS_RESOLUTION, StoredOutcome.INITIAL_CALLBACK_RESOLVABLE_ERROR, startedAtMillis, deadlineAtMillis),
                    CallbackDisposition.OPEN_RESOLUTION,
                )
                CallbackKind.ERROR -> accepted(complete(StoredOutcome.INITIAL_CALLBACK_ERROR))
            }
            Stage.AWAITING_TERMINAL -> when (kind) {
                CallbackKind.OK -> accepted(complete(StoredOutcome.TERMINAL_CALLBACK_OK))
                CallbackKind.RESOLVABLE_ERROR -> accepted(complete(StoredOutcome.TERMINAL_CALLBACK_RESOLVABLE_ERROR))
                CallbackKind.ERROR -> accepted(complete(StoredOutcome.TERMINAL_CALLBACK_ERROR))
            }
            Stage.NEEDS_RESOLUTION, Stage.TERMINAL -> CallbackTransition(this, CallbackDisposition.IGNORE)
        }
    }
    fun beginResolution(id: String) =
        if (id == operationId && stage == Stage.NEEDS_RESOLUTION) ResolutionTransition(
            state(id, Stage.AWAITING_TERMINAL, StoredOutcome.RESOLUTION_UI, startedAtMillis, deadlineAtMillis), true
        ) else ResolutionTransition(this, false)
    fun publishResolutionUi(id: String) =
        this
    fun recordResolutionReturn() =
        if (stage == Stage.AWAITING_TERMINAL) state(operationId, stage, StoredOutcome.RESOLUTION_CANCELLED_OR_UNKNOWN, startedAtMillis, deadlineAtMillis) else this

    private fun accepted(model: ProbeStateModel) = CallbackTransition(model, CallbackDisposition.ACCEPTED)
    private fun complete(outcome: StoredOutcome) = state(null, Stage.TERMINAL, outcome, NO_STARTED_AT, NO_STARTED_AT)
    private fun state(id: String?, stage: Stage, outcome: StoredOutcome, started: Long, deadline: Long = NO_STARTED_AT) =
        ProbeStateModel(id, stage, outcome, started, history + outcome, deadline)
    data class CallbackTransition(val model: ProbeStateModel, val disposition: CallbackDisposition)
    data class ResolutionTransition(val model: ProbeStateModel, val accepted: Boolean)
    enum class CallbackKind { OK, RESOLVABLE_ERROR, ERROR }
    enum class CallbackDisposition { IGNORE, ACCEPTED, OPEN_RESOLUTION }
    enum class Stage { DISPATCHED, NEEDS_RESOLUTION, AWAITING_TERMINAL, TERMINAL }
    private enum class StoredOutcome {
        READY, APP_DISPATCH, INITIAL_CALLBACK_OK, INITIAL_CALLBACK_ERROR, INITIAL_CALLBACK_RESOLVABLE_ERROR,
        RESOLUTION_UI, TERMINAL_CALLBACK_OK, TERMINAL_CALLBACK_RESOLVABLE_ERROR, TERMINAL_CALLBACK_ERROR,
        RESOLUTION_CANCELLED_OR_UNKNOWN, INCONCLUSIVE_NO_CALLBACK, LOCAL_EMPTY_ACTIVATION_CODE,
        LOCAL_ESIM_UNSUPPORTED, LOCAL_ESIM_DISABLED, LOCAL_REQUEST_REJECTED, LOCAL_RESOLUTION_UNAVAILABLE,
        LOCAL_RESOLUTION_NOTIFICATION_UNAVAILABLE;
        fun toProbeOutcome() = when (this) {
            READY -> ProbeOutcome.Ready; APP_DISPATCH -> ProbeOutcome.AppDispatch; INITIAL_CALLBACK_OK -> ProbeOutcome.InitialCallbackOk
            INITIAL_CALLBACK_ERROR -> ProbeOutcome.InitialCallbackError; INITIAL_CALLBACK_RESOLVABLE_ERROR -> ProbeOutcome.InitialCallbackResolvableError
            RESOLUTION_UI -> ProbeOutcome.ResolutionUi; TERMINAL_CALLBACK_OK -> ProbeOutcome.TerminalCallbackOk
            TERMINAL_CALLBACK_RESOLVABLE_ERROR -> ProbeOutcome.TerminalCallbackResolvableError; TERMINAL_CALLBACK_ERROR -> ProbeOutcome.TerminalCallbackError
            RESOLUTION_CANCELLED_OR_UNKNOWN -> ProbeOutcome.ResolutionCancelledOrUnknown; INCONCLUSIVE_NO_CALLBACK -> ProbeOutcome.InconclusiveNoCallback
            LOCAL_EMPTY_ACTIVATION_CODE -> ProbeOutcome.LocalFailure(ProbeOutcome.Reason.EMPTY_ACTIVATION_CODE)
            LOCAL_ESIM_UNSUPPORTED -> ProbeOutcome.LocalFailure(ProbeOutcome.Reason.ESIM_UNSUPPORTED)
            LOCAL_ESIM_DISABLED -> ProbeOutcome.LocalFailure(ProbeOutcome.Reason.ESIM_DISABLED)
            LOCAL_REQUEST_REJECTED -> ProbeOutcome.LocalFailure(ProbeOutcome.Reason.REQUEST_REJECTED)
            LOCAL_RESOLUTION_UNAVAILABLE -> ProbeOutcome.LocalFailure(ProbeOutcome.Reason.RESOLUTION_UNAVAILABLE)
            LOCAL_RESOLUTION_NOTIFICATION_UNAVAILABLE ->
                ProbeOutcome.LocalFailure(ProbeOutcome.Reason.RESOLUTION_NOTIFICATION_UNAVAILABLE)
        }
        companion object { fun local(reason: ProbeOutcome.Reason) = when (reason) {
            ProbeOutcome.Reason.EMPTY_ACTIVATION_CODE -> LOCAL_EMPTY_ACTIVATION_CODE; ProbeOutcome.Reason.ESIM_UNSUPPORTED -> LOCAL_ESIM_UNSUPPORTED
            ProbeOutcome.Reason.ESIM_DISABLED -> LOCAL_ESIM_DISABLED; ProbeOutcome.Reason.REQUEST_REJECTED -> LOCAL_REQUEST_REJECTED
            ProbeOutcome.Reason.RESOLUTION_UNAVAILABLE -> LOCAL_RESOLUTION_UNAVAILABLE
            ProbeOutcome.Reason.RESOLUTION_NOTIFICATION_UNAVAILABLE -> LOCAL_RESOLUTION_NOTIFICATION_UNAVAILABLE
        }}
    }
    companion object {
        const val TIMEOUT_MILLIS = 5 * 60 * 1000L
        private const val NO_STARTED_AT = -1L
        fun ready() = ProbeStateModel(null, Stage.TERMINAL, StoredOutcome.READY, NO_STARTED_AT, listOf(StoredOutcome.READY), NO_STARTED_AT)
        fun restore(id: String?, stage: String?, outcome: String?, started: Long, sequence: String?, deadline: Long = started + TIMEOUT_MILLIS): ProbeStateModel {
            val parsedStage = stage?.let { runCatching { Stage.valueOf(it) }.getOrNull() } ?: Stage.TERMINAL
            val parsedOutcome = outcome?.let { runCatching { StoredOutcome.valueOf(it) }.getOrNull() } ?: StoredOutcome.READY
            val parsedHistory = sequence.orEmpty().split(',').mapNotNull { runCatching { StoredOutcome.valueOf(it) }.getOrNull() }
                .ifEmpty { listOf(parsedOutcome) }
            return ProbeStateModel(id, if (id == null) Stage.TERMINAL else parsedStage, parsedOutcome, if (id == null) NO_STARTED_AT else started, parsedHistory, if (id == null) NO_STARTED_AT else deadline)
        }
    }
}
