package dev.workflowcrm.objects.domain;

public enum CounterpartyTaxStatus {
    SOLE_PROPRIETOR_GROUP_3_NON_VAT("ФОП 3 група не платник ПДВ"),
    SOLE_PROPRIETOR_GROUP_3_VAT("ФОП 3 група платник ПДВ"),
    COMPANY_NON_VAT("ТОВ не платник ПДВ"),
    COMPANY_VAT("ТОВ платник ПДВ"),
    INDIVIDUAL("Фізична особа");

    private final String label;

    CounterpartyTaxStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
