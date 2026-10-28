package dev.workflowcrm.objects.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record TransitionEffects(
        @Column(name = "is_pause_entry", nullable = false) boolean pauseEntry,
        @Column(name = "is_pause_exit", nullable = false) boolean pauseExit,
        @Column(name = "is_refusal_entry", nullable = false) boolean refusalEntry,
        @Column(name = "is_refusal_exit", nullable = false) boolean refusalExit,
        @Column(name = "is_ic_reset", nullable = false) boolean icReset,
        @Column(name = "is_signing_date_reset", nullable = false) boolean signingReset,
        @Column(name = "is_opening_date_reset", nullable = false) boolean openingReset,
        @Column(name = "milestone_trigger_field", length = 32) String milestoneField) {}
