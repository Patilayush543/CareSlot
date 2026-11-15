package app.scheduler;

public final class Account {
    public final String password;
    public final String displayName;
    public final String role;
    public final String providerId;

    public Account(String password, String displayName, String role, String providerId) {
        this.password = password;
        this.displayName = displayName;
        this.role = role;
        this.providerId = providerId;
    }
}
