package dev.workflowcrm.users.domain;

import dev.workflowcrm.shared.persistence.BaseEntity;
import jakarta.persistence.*;
import java.util.*;

@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "password_setup_required", nullable = false)
    private boolean passwordSetupRequired;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "telegram_chat_id", unique = true)
    private Long telegramChatId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @org.hibernate.annotations.BatchSize(size = 100)
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_regions",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "region_id"))
    private Set<Region> regions = new LinkedHashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserStatus status;

    protected User() {}

    public User(String email, String passwordHash, String fullName, Role role, Set<Region> regions, UserStatus status) {
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.passwordSetupRequired = true;
        this.fullName = fullName;
        this.role = role;
        this.regions.addAll(regions);
        this.status = status;
    }

    public void changeAccess(Role role, Set<Region> regions, UserStatus status) {
        this.role = java.util.Objects.requireNonNull(role);
        if (!this.regions.equals(regions)) {
            this.regions.clear();
            this.regions.addAll(regions);
        }
        this.status = java.util.Objects.requireNonNull(status);
    }

    public void activateWithPassword(String passwordHash) {
        this.passwordHash = java.util.Objects.requireNonNull(passwordHash);
        this.passwordSetupRequired = false;
        this.status = UserStatus.ACTIVE;
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = java.util.Objects.requireNonNull(passwordHash);
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isPasswordSetupRequired() {
        return passwordSetupRequired;
    }

    public String getFullName() {
        return fullName;
    }

    public Role getRole() {
        return role;
    }

    public Set<Region> getRegions() {
        return Set.copyOf(regions);
    }

    public List<UUID> getRegionIds() {
        return regions.stream().map(Region::getId).sorted().toList();
    }

    public UserStatus getStatus() {
        return status;
    }

    public Long getTelegramChatId() {
        return telegramChatId;
    }

    public void bindTelegram(long chatId) {
        this.telegramChatId = chatId;
    }

    public void unbindTelegram() {
        this.telegramChatId = null;
    }

    private static String normalizeEmail(String email) {
        return email == null ? null : email.strip().toLowerCase(java.util.Locale.ROOT);
    }
}
