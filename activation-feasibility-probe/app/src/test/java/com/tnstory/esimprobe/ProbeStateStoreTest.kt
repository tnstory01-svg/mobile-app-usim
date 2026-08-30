package com.tnstory.esimprobe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProbeStateStoreTest {
    @Test
    fun matchingCallbackCompletesButStaleCallbackCannotMutateActiveOperation() {
        val model = ProbeStateModel.ready().beginOperation("current", 0)
        val stale = model.onCallback("old", ProbeStateModel.CallbackKind.OK, 1)
        assertEquals(ProbeStateModel.CallbackDisposition.IGNORE, stale.disposition)
        assertEquals("current", stale.model.operationId)

        val matching = model.onCallback("current", ProbeStateModel.CallbackKind.OK, 1)
        assertEquals(ProbeStateModel.CallbackDisposition.ACCEPTED, matching.disposition)
        assertFalse(matching.model.hasActiveOperation)
        assertEquals(ProbeOutcome.InitialCallbackOk, matching.model.outcome)
    }

    @Test
    fun singleFlightRejectsSecondOperationUntilFirstIsTerminal() {
        val model = ProbeStateModel.ready().beginOperation("first", 0)
        assertTrue(model.hasActiveOperation)
        try {
            model.beginOperation("second", 1)
            throw AssertionError("Expected active operation to reject a duplicate submission")
        } catch (_: IllegalStateException) {
        }
        val completed = model.onCallback("first", ProbeStateModel.CallbackKind.ERROR, 1).model
        assertFalse(completed.hasActiveOperation)
        assertEquals("second", completed.beginOperation("second", 2).operationId)
    }

    @Test
    fun initialResolvableCallbackThenTerminalCallbackProducesTerminalOutcome() {
        val resolvable = ProbeStateModel.ready().beginOperation("operation", 0)
            .onCallback("operation", ProbeStateModel.CallbackKind.RESOLVABLE_ERROR, 1)
        assertEquals(ProbeStateModel.CallbackDisposition.OPEN_RESOLUTION, resolvable.disposition)
        val resolution = resolvable.model.beginResolution("operation")
        assertTrue(resolution.accepted)
        val terminal = resolution.model.onCallback("operation", ProbeStateModel.CallbackKind.OK, 2)
        assertEquals(ProbeStateModel.CallbackDisposition.ACCEPTED, terminal.disposition)
        assertEquals(ProbeOutcome.TerminalCallbackOk, terminal.model.outcome)
        assertEquals(
            listOf(
                ProbeOutcome.Ready,
                ProbeOutcome.AppDispatch,
                ProbeOutcome.InitialCallbackResolvableError,
                ProbeOutcome.ResolutionUi,
                ProbeOutcome.TerminalCallbackOk,
            ),
            terminal.model.outcomes,
        )
    }

    @Test
    fun restoredPersistedModelRetainsActiveOperationStageAndSequence() {
        val model = ProbeStateModel.ready().beginOperation("operation", 10)
            .onCallback("operation", ProbeStateModel.CallbackKind.RESOLVABLE_ERROR, 11).model
        val restored = ProbeStateModel.restore(
            model.operationId, model.stage.name, model.outcomeToken, model.startedAtMillis, model.sequenceToken,
        )
        assertEquals("operation", restored.operationId)
        assertEquals(ProbeStateModel.Stage.NEEDS_RESOLUTION, restored.stage)
        assertEquals(
            listOf(ProbeOutcome.Ready, ProbeOutcome.AppDispatch, ProbeOutcome.InitialCallbackResolvableError),
            restored.outcomes,
        )
    }

    @Test
    fun timeoutClearsOperationAndLateCallbackCannotChangeInconclusiveOutcome() {
        val dispatched = ProbeStateModel.ready().beginOperation("operation", 100)
        val timedOut = dispatched.expireTimedOut(100 + ProbeStateModel.TIMEOUT_MILLIS)
        assertFalse(timedOut.hasActiveOperation)
        assertEquals(ProbeOutcome.InconclusiveNoCallback, timedOut.outcome)

        val late = timedOut.onCallback(
            "operation", ProbeStateModel.CallbackKind.OK, 101 + ProbeStateModel.TIMEOUT_MILLIS,
        )
        assertEquals(ProbeStateModel.CallbackDisposition.IGNORE, late.disposition)
        assertEquals(ProbeOutcome.InconclusiveNoCallback, late.model.outcome)
    }

    @Test
    fun notificationUnavailabilityFailsAndClearsResolvableOperation() {
        val resolvable = ProbeStateModel.ready().beginOperation("operation", 0)
            .onCallback("operation", ProbeStateModel.CallbackKind.RESOLVABLE_ERROR, 1).model

        val failed = resolvable.failActiveOperation(
            ProbeOutcome.Reason.RESOLUTION_NOTIFICATION_UNAVAILABLE,
        )

        assertFalse(failed.hasActiveOperation)
        assertEquals(
            ProbeOutcome.LocalFailure(ProbeOutcome.Reason.RESOLUTION_NOTIFICATION_UNAVAILABLE),
            failed.outcome,
        )
    }

    @Test
    fun resolutionUiIsRecordedOnceAndPersistedDeadlineRejectsCallbackAtDeadline() {
        val resolution = ProbeStateModel.ready().beginOperation("operation", 100)
            .onCallback("operation", ProbeStateModel.CallbackKind.RESOLVABLE_ERROR, 101).model
            .beginResolution("operation").model
            .publishResolutionUi("operation")
        assertEquals(
            listOf(
                ProbeOutcome.Ready,
                ProbeOutcome.AppDispatch,
                ProbeOutcome.InitialCallbackResolvableError,
                ProbeOutcome.ResolutionUi,
            ),
            resolution.outcomes,
        )

        val restored = ProbeStateModel.restore(
            resolution.operationId,
            resolution.stage.name,
            resolution.outcomeToken,
            resolution.startedAtMillis,
            resolution.sequenceToken,
            resolution.deadlineAtMillis,
        )
        val callbackAtDeadline = restored.onCallback(
            "operation",
            ProbeStateModel.CallbackKind.OK,
            restored.deadlineAtMillis,
        )
        assertEquals(ProbeStateModel.CallbackDisposition.IGNORE, callbackAtDeadline.disposition)
        assertEquals(ProbeOutcome.InconclusiveNoCallback, callbackAtDeadline.model.outcome)
    }
}
