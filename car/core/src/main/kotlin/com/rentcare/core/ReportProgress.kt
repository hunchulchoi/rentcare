package com.rentcare.core

/** Steps of the report status stepper on the car (attachments are shown apart: they are not a step). */
object ReportProgress {
    enum class Step(val status: IncidentStatus) {
        RECEIVED(IncidentStatus.RECEIVED),
        ACKNOWLEDGED(IncidentStatus.ACKNOWLEDGED),
        IN_PROGRESS(IncidentStatus.IN_PROGRESS),
        RESOLVED(IncidentStatus.RESOLVED),
    }

    enum class State { DONE, CURRENT, TODO }

    data class Item(val step: Step, val state: State, val atMillis: Long?)

    fun of(status: IncidentStatus, acknowledgedAt: String?, resolvedAt: String?): List<Item> = Step.entries.map { s ->
        val state = when {
            !status.reached(s.status) -> State.TODO
            status == s.status && !status.isFinal -> State.CURRENT
            else -> State.DONE
        }
        val at = when (s) {
            Step.ACKNOWLEDGED -> IsoTime.millis(acknowledgedAt)
            Step.RESOLVED -> IsoTime.millis(resolvedAt)
            else -> null
        }
        Item(s, state, if (state == State.TODO) null else at)
    }
}
