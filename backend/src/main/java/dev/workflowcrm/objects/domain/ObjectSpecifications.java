package dev.workflowcrm.objects.domain;

import jakarta.persistence.*;

@Embeddable
public class ObjectSpecifications {
    @Column(name = "commercial_terms", columnDefinition = "text")
    private String commercialTerms;

    @Column(name = "technical_specifications", columnDefinition = "text")
    private String technicalSpecifications;

    @Column(name = "contract_type", length = 160)
    private String contractType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_type_id")
    private ContractType contractTypeReference;

    @Embedded
    private TechnicalSpecifications technical;

    @Embedded
    private CommercialTerms commercial;

    public void specify(TechnicalSpecifications technical, CommercialTerms commercial, ContractType contractType) {
        if (contractType != null && !contractType.isActive())
            throw new IllegalArgumentException("Contract type is inactive");
        this.technical = technical;
        this.commercial = commercial;
        this.contractTypeReference = contractType;
        this.contractType = null;
    }

    public TechnicalSpecifications getTechnical() {
        return technical;
    }

    public void updateDetails(TechnicalSpecifications technical, CommercialTerms commercial) {
        this.technical = technical;
        this.commercial = commercial;
    }

    public CommercialTerms getCommercial() {
        return commercial;
    }

    public ContractType getContractTypeReference() {
        return contractTypeReference;
    }

    public void changeContractType(ContractType contractType) {
        if (contractType == null || !contractType.isActive())
            throw new IllegalArgumentException("Invalid contract type");
        contractTypeReference = contractType;
        this.contractType = null;
    }

    public String getLegacyContractType() {
        return contractType;
    }

    public void describeCommercialTerms(String terms, String contractType) {
        this.commercialTerms = ObjectText.optional(terms, Integer.MAX_VALUE, "Commercial terms");
        this.contractType = ObjectText.optional(contractType, 160, "Contract type");
    }

    public void describeTechnicalSpecifications(String specifications) {
        technicalSpecifications = ObjectText.optional(specifications, Integer.MAX_VALUE, "Technical specifications");
    }

    public String getCommercialTerms() {
        return commercialTerms;
    }

    public String getTechnicalSpecifications() {
        return technicalSpecifications;
    }

    public String getContractType() {
        return contractTypeReference == null ? contractType : contractTypeReference.getCode();
    }
}
