package dev.workflowcrm.objects.domain;

import jakarta.persistence.*;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "objects")
public class RealEstateObject extends ObjectPropertyDetails {
    @Column(length = 500)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "object_type_id")
    private ObjectType type;

    @Column(length = 500)
    private String address;

    @Column(name = "region_id", nullable = false)
    private UUID regionId;

    @Column(name = "manager_id", nullable = false)
    private UUID managerId;

    @Column(name = "author_id", nullable = false, updatable = false)
    private UUID authorId;

    @Embedded
    private EntranceCoordinates entrance;

    @Embedded
    private PropertyAreas areas;

    @Embedded
    private ObjectContact contact;

    protected RealEstateObject() {}

    public RealEstateObject(ObjectRegistration registration, ObjectStatus initialStatus) {
        name = ObjectText.optional(registration.name(), 500, "Object name");
        type = registration.type();
        address = ObjectText.optional(registration.address(), 500, "Address");
        regionId = Objects.requireNonNull(registration.regionId(), "Region is required");
        managerId = Objects.requireNonNull(registration.managerId(), "Manager is required");
        authorId = Objects.requireNonNull(registration.authorId(), "Author is required");
        entrance = Objects.requireNonNull(registration.entrance(), "Entrance coordinates are required");
        areas = Objects.requireNonNull(registration.areas(), "Area is required");
        contact = registration.contact();
        specify(registration.technicalSpecifications(), registration.commercialTerms(), null);
        initializeStatus(initialStatus);
        selectDataSource(registration.dataSource());
        comment(registration.shortComment());
    }

    public void rename(String name) {
        this.name = ObjectText.required(name, 500, "Object name");
    }

    public void editLocation(String address, ObjectType type) {
        this.address = ObjectText.optional(address, 500, "Address");
        this.type = type;
    }

    public void resize(PropertyAreas areas) {
        this.areas = Objects.requireNonNull(areas);
    }

    public void moveEntrance(EntranceCoordinates entrance) {
        this.entrance = Objects.requireNonNull(entrance);
    }

    public void selectContact(ObjectContact contact) {
        this.contact = Objects.requireNonNull(contact);
    }

    public void reassign(UUID manager) {
        managerId = Objects.requireNonNull(manager);
    }

    public String getGoogleMapsUrl() {
        return entrance.googleMapsUrl();
    }

    public String getName() {
        return name;
    }

    public ObjectType getType() {
        return type;
    }

    public String getAddress() {
        return address;
    }

    public UUID getRegionId() {
        return regionId;
    }

    public UUID getManagerId() {
        return managerId;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public EntranceCoordinates getEntrance() {
        return entrance;
    }

    public PropertyAreas getAreas() {
        return areas;
    }

    public ObjectContact getContact() {
        return contact;
    }
}
