package kz.finance.security.model;

public enum UserRole {
    USER("ROLE_USER"),
    PRO("ROLE_PRO");

    private final String role;

    UserRole(String role) {
        this.role = role;
    }

    public String getRole() {
        return role;
    }
}
