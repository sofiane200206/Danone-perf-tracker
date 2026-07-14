package model;

public enum UserRole {
    ADMIN("Administrateur"),
    USER("Utilisateur");

    private final String displayName;

    UserRole(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}