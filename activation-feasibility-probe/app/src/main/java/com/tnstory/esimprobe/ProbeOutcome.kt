package com.tnstory.esimprobe

/** Non-secret states exposed by the activation feasibility probe. */
sealed interface ProbeOutcome {
    val message: String

    data object Ready : ProbeOutcome {
        override val message = "Ready to submit an activation code."
    }

    data object AppDispatch : ProbeOutcome {
        override val message = "Activation request sent to the system."
    }

    data object SubmissionInProgress : ProbeOutcome {
        override val message = "An activation request is already awaiting the system."
    }

    data object InconclusiveNoCallback : ProbeOutcome {
        override val message = "No system callback arrived within five minutes."
    }

    data object InitialCallbackOk : ProbeOutcome {
        override val message = "Initial system callback reported success."
    }

    data object InitialCallbackError : ProbeOutcome {
        override val message = "Initial system callback reported an error."
    }

    data object InitialCallbackResolvableError : ProbeOutcome {
        override val message = "Initial system callback requires resolution."
    }

    data object ResolutionUi : ProbeOutcome {
        override val message = "System resolution UI was opened; awaiting its callback."
    }

    data object TerminalCallbackOk : ProbeOutcome {
        override val message = "Post-resolution system callback reported success."
    }

    data object TerminalCallbackResolvableError : ProbeOutcome {
        override val message = "Post-resolution callback still requires resolution."
    }

    data object TerminalCallbackError : ProbeOutcome {
        override val message = "Post-resolution system callback reported an error."
    }

    data object ResolutionCancelledOrUnknown : ProbeOutcome {
        override val message = "Resolution UI returned; awaiting any system callback."
    }

    data class LocalFailure(val reason: Reason) : ProbeOutcome {
        override val message = reason.message
    }

    enum class Reason(val message: String) {
        EMPTY_ACTIVATION_CODE("Enter an activation code before submitting."),
        ESIM_UNSUPPORTED("This device does not advertise eSIM support."),
        ESIM_DISABLED("eSIM management is not enabled on this device."),
        REQUEST_REJECTED("The system did not accept the activation request."),
        RESOLUTION_UNAVAILABLE("The system could not open its resolution UI."),
        RESOLUTION_NOTIFICATION_UNAVAILABLE("The app could not notify you to continue the system resolution."),
    }
}
