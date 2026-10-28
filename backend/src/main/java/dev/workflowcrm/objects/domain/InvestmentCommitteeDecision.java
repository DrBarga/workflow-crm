package dev.workflowcrm.objects.domain;

public enum InvestmentCommitteeDecision {
    APPROVED("Затверджено"),
    APPROVED_WITH_CONDITIONS("Затверджено з умовою"),
    REFUSED("Відмова");

    private final String label;

    InvestmentCommitteeDecision(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
