package app.scheduler;

public final class Appointment {
    public final String id;
    public final String providerId;
    public final String providerName;
    public final String service;
    public final String patientName;
    public final String date;
    public final int startMinute;
    public final int endMinute;

    public Appointment(String id, String providerId, String providerName, String service,
                       String patientName, String date, int startMinute, int endMinute) {
        this.id = id;
        this.providerId = providerId;
        this.providerName = providerName;
        this.service = service;
        this.patientName = patientName;
        this.date = date;
        this.startMinute = startMinute;
        this.endMinute = endMinute;
    }

    public String getStartTime() { return formatTime(startMinute); }
    public String getEndTime() { return formatTime(endMinute); }

    public static String formatTime(int minute) {
        return String.format("%02d:%02d", minute / 60, minute % 60);
    }
}
