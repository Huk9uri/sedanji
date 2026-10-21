package kr.sedanji.admin;

import jakarta.persistence.*;

@Entity @Table(name = "admin_users")
public class AdminUserEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 100) private String username;
    @Column(name = "password_hash", nullable = false, length = 100) private String passwordHash;
    @Column(nullable = false) private boolean enabled = true;
    protected AdminUserEntity() {}
    public AdminUserEntity(String username, String passwordHash) { this.username = username; this.passwordHash = passwordHash; }
    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isEnabled() { return enabled; }
}
