package dev.workflowcrm.objects.domain;

public enum SewageType {
    CENTRALIZED("Централізована"),
    LOCAL_TREATMENT("ЛОС"),
    CESSPOOL("Вигрібна яма");

    private final String label;

    SewageType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
