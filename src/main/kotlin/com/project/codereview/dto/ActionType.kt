package com.project.codereview.dto

enum class ActionType(val value: String) {
    ASSIGNED("assigned"),
    AUTO_MERGE_DISABLED("auto merge disabled"),
    AUTO_MERGE_ENABLED("auto merge enabled"),
    CLOSED("closed"),
    CONVERTED_TO_DRAFT("converted to draft"),
    DEMILESTONED("demilestoned"),
    DEQUEUED("dequeued"),
    EDITED("edited"),
    ENQUEUED("enqueued"),
    LABELED("labeled"),
    LOCKED("locked"),
    MILESTONED("milestoned"),
    OPENED("opened"),
    READY_FOR_REVIEW("ready for review"),
    REOPENED("reopened"),
    REVIEW_REQUEST_REMOVED("review request removed"),
    REVIEW_REQUESTED("review requested"),
    SYNCHRONIZED("synchronized"),
    UNASSIGNED("unassigned"),
    UNLABELED("unlabeled"),
    UNLOCKED("unlocked");

    companion object {
        operator fun invoke(value: String) = entries.find { it.value == value }
    }
}