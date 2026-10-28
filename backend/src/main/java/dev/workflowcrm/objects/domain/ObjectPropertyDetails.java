package dev.workflowcrm.objects.domain;

import jakarta.persistence.Embedded;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
public abstract class ObjectPropertyDetails extends ObjectLifecycle {
    @jakarta.persistence.Column(name = "short_comment", length = 200)
    private String shortComment;

    public String getShortComment() {
        return shortComment;
    }

    public void comment(String text) {
        shortComment = ObjectText.optional(text, 200, "Short comment");
    }

    @Override
    protected Integer constructionTermDays() {
        return specifications == null || specifications.getCommercial() == null
                ? null
                : specifications.getCommercial().constructionTermDays();
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "data_source_id")
    private ObjectDataSource dataSource;

    public ObjectDataSource getDataSource() {
        return dataSource;
    }

    public void selectDataSource(ObjectDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void markEdited() {
        markUpdated();
    }

    @Embedded
    private ObjectSpecifications specifications = new ObjectSpecifications();

    public void describeCommercialTerms(String terms, String contractType) {
        if (specifications == null) specifications = new ObjectSpecifications();
        specifications.describeCommercialTerms(terms, contractType);
    }

    public void describeTechnicalSpecifications(String text) {
        if (specifications == null) specifications = new ObjectSpecifications();
        specifications.describeTechnicalSpecifications(text);
    }

    public void specify(TechnicalSpecifications technical, CommercialTerms commercial, ContractType contractType) {
        if (specifications == null) specifications = new ObjectSpecifications();
        specifications.specify(technical, commercial, contractType);
        if (getMilestones() != null) schedule(getMilestones());
    }

    public ObjectSpecifications getSpecifications() {
        return specifications;
    }

    public void updateSpecifications(TechnicalSpecifications technical, CommercialTerms commercial) {
        if (specifications == null) specifications = new ObjectSpecifications();
        specifications.updateDetails(technical, commercial);
        if (getMilestones() != null) schedule(getMilestones());
    }

    public void changeContractType(ContractType type) {
        if (specifications == null) specifications = new ObjectSpecifications();
        specifications.changeContractType(type);
    }
}
