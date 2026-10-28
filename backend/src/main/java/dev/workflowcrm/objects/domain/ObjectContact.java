package dev.workflowcrm.objects.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class ObjectContact {
    @Column(name = "contact_name", length = 200)
    private String name;

    @Column(name = "contact_role", length = 100)
    private String role;

    @Column(name = "contact_phone", length = 64)
    private String phone;

    @Column(name = "contact_email", length = 320)
    private String email;

    protected ObjectContact() {}

    public ObjectContact(String name, String role, String phone, String email) {
        this.name = ObjectText.required(name, 200, "Contact name");
        this.role = ObjectText.required(role, 100, "Contact role");
        this.phone = ObjectText.optional(phone, 64, "Contact phone");
        this.email = ObjectText.optional(email, 320, "Contact email");
        if (this.phone == null && this.email == null) {
            throw new IllegalArgumentException("Contact phone or email is required");
        }
    }

    public String getName() {
        return name;
    }

    public String getRole() {
        return role;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }
}
