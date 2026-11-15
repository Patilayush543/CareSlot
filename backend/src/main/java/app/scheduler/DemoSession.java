package app.scheduler;

public final class DemoSession {
    public final String token;
    public final String username;
    public final String displayName;
    public final String role;
    public final String providerId;

    public DemoSession(String token, String username, String displayName, String role, String providerId) {
        this.token = token;
        this.username = username;
        this.displayName = displayName;
        this.role = role;
        this.providerId = providerId;
    }
}
