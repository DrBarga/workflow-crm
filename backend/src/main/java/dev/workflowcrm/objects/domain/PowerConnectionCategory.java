package dev.workflowcrm.objects.domain;

public enum PowerConnectionCategory {
    I("I"),
    II("II"),
    III("III");

    private final String label;

    PowerConnectionCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
