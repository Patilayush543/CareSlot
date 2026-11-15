package app.scheduler;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Collections;
import java.util.UUID;

public final class Scheduler {
    private final Map<String, Provider> providers = new HashMap<String, Provider>();
    private final Map<String, IntervalTree> calendars = new HashMap<String, IntervalTree>();
    private final Map<String, Appointment> appointments = new HashMap<String, Appointment>();
    private final Map<String, Account> accounts = new HashMap<String, Account>();
    private final Map<String, DemoSession> sessions = new HashMap<String, DemoSession>();

    public Scheduler() {
        addProvider(new Provider("p1", "Dr. Anaya Rao", "General Physician"));
        addProvider(new Provider("p2", "Dr. Kabir Mehta", "Dentist"));
        addProvider(new Provider("p3", "Dr. Mira Shah", "Dermatologist"));
        accounts.put("admin", new Account("admin123", "Schedule Administrator", "ADMIN", null));
        accounts.put("anaya", new Account("doctor123", "Dr. Anaya Rao", "DOCTOR", "p1"));
        accounts.put("kabir", new Account("doctor123", "Dr. Kabir Mehta", "DOCTOR", "p2"));
        accounts.put("mira", new Account("doctor123", "Dr. Mira Shah", "DOCTOR", "p3"));
        accounts.put("aarav", new Account("demo123", "Aarav Sharma", "PATIENT", null));
        accounts.put("diya", new Account("demo123", "Diya Patel", "PATIENT", null));
        accounts.put("ishaan", new Account("demo123", "Ishaan Verma", "PATIENT", null));
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        book("p1", "Aarav Sharma", tomorrow.toString(), 9 * 60, 9 * 60 + 30);
        book("p1", "Diya Patel", tomorrow.toString(), 11 * 60, 11 * 60 + 30);
        book("p2", "Ishaan Verma", tomorrow.toString(), 10 * 60, 10 * 60 + 45);
    }

    public synchronized List<Provider> getProviders() {
        return new ArrayList<Provider>(providers.values());
    }

    public synchronized DemoSession login(String username, String password) {
        Account account = accounts.get(username == null ? "" : username.trim().toLowerCase());
        if (account == null || password == null || !account.password.equals(password))
            throw new IllegalArgumentException("Username or password is incorrect.");
        String normalized = username.trim().toLowerCase();
        return createSession(normalized, account);
    }

    public synchronized DemoSession registerPatient(String username, String password, String displayName) {
        String normalized = username == null ? "" : username.trim().toLowerCase();
        if (!normalized.matches("[a-z0-9_]{3,32}"))
            throw new IllegalArgumentException("Username must be 3 to 32 letters, numbers, or underscores.");
        if (password == null || password.length() < 6 || password.length() > 80)
            throw new IllegalArgumentException("Password must be 6 to 80 characters.");
        String name = displayName == null ? "" : displayName.trim();
        if (name.isEmpty() || name.length() > 80)
            throw new IllegalArgumentException("Full name must be 1 to 80 characters.");
        if (accounts.containsKey(normalized)) throw new IllegalArgumentException("That username is already registered.");
        Account account = new Account(password, name, "PATIENT", null);
        accounts.put(normalized, account);
        return createSession(normalized, account);
    }

    public synchronized DemoSession authenticate(String token) {
        DemoSession session = sessions.get(token);
        if (session == null) throw new IllegalArgumentException("Please log in again.");
        return session;
    }

    public synchronized void logout(String token) {
        if (token != null) sessions.remove(token);
    }

    public synchronized Appointment bookForPatient(String token, String providerId, String date, int start, int end) {
        DemoSession session = authenticate(token);
        if (!"PATIENT".equals(session.role)) throw new IllegalArgumentException("Only patient accounts can book appointments.");
        return book(providerId, session.displayName, date, start, end);
    }

    public synchronized List<Appointment> findSlots(String providerId, String date, int duration,
                                                       int preferredStart, int preferredEnd) {
        Provider provider = providers.get(providerId);
        if (provider == null) throw new IllegalArgumentException("Choose a valid provider.");
        validateDate(date);
        if (duration < 15 || duration > 180) throw new IllegalArgumentException("Duration must be 15 to 180 minutes.");
        if (preferredStart < 0 || preferredEnd > 24 * 60 || preferredStart >= preferredEnd)
            throw new IllegalArgumentException("Choose a valid preferred time range.");

        IntervalTree calendar = calendar(providerId, date);
        PriorityQueue<Appointment> ranked = new PriorityQueue<Appointment>(11, new Comparator<Appointment>() {
            public int compare(Appointment a, Appointment b) {
                return Integer.compare(a.startMinute, b.startMinute);
            }
        });
        // Clinic hours are 09:00–17:00. Candidate times advance in 30-minute increments.
        for (int start = Math.max(9 * 60, preferredStart); start + duration <= Math.min(17 * 60, preferredEnd); start += 30) {
            if (calendar.findOverlap(start, start + duration) == null) {
                ranked.add(new Appointment("", provider.id, provider.name, provider.service, "", date, start, start + duration));
            }
        }
        List<Appointment> result = new ArrayList<Appointment>();
        while (!ranked.isEmpty()) result.add(ranked.poll());
        return result;
    }

    public synchronized Appointment book(String providerId, String patientName, String date, int start, int end) {
        Provider provider = providers.get(providerId);
        if (provider == null) throw new IllegalArgumentException("Choose a valid provider.");
        if (patientName == null || patientName.trim().isEmpty()) throw new IllegalArgumentException("Enter the patient's name.");
        patientName = patientName.trim();
        if (patientName.length() > 80) throw new IllegalArgumentException("Patient name must be 80 characters or fewer.");
        validateDate(date);
        if (start < 9 * 60 || end > 17 * 60 || start >= end)
            throw new IllegalArgumentException("Appointments must fit within clinic hours (09:00–17:00).");
        IntervalTree calendar = calendar(providerId, date);
        if (calendar.findOverlap(start, end) != null)
            throw new IllegalArgumentException("That time overlaps an existing appointment. Choose another slot.");
        Appointment appointment = new Appointment(UUID.randomUUID().toString(), provider.id, provider.name,
                provider.service, patientName, date, start, end);
        calendar.insert(appointment);
        appointments.put(appointment.id, appointment);
        return appointment;
    }

    public synchronized List<Appointment> getAppointments(String token) {
        DemoSession session = authenticate(token);
        List<Appointment> result = new ArrayList<Appointment>();
        for (Appointment appointment : appointments.values()) {
            if ("ADMIN".equals(session.role)
                    || ("DOCTOR".equals(session.role) && appointment.providerId.equals(session.providerId))
                    || ("PATIENT".equals(session.role) && appointment.patientName.equals(session.displayName))) {
                result.add(appointment);
            }
        }
        Collections.sort(result, new Comparator<Appointment>() {
            public int compare(Appointment a, Appointment b) {
                int byDate = a.date.compareTo(b.date);
                return byDate != 0 ? byDate : Integer.compare(a.startMinute, b.startMinute);
            }
        });
        return result;
    }

    public synchronized Appointment cancel(String token, String appointmentId) {
        DemoSession session = authenticate(token);
        if (appointmentId == null || appointmentId.trim().isEmpty())
            throw new IllegalArgumentException("Appointment ID is required.");
        Appointment appointment = appointments.get(appointmentId.trim());
        if (appointment == null) throw new IllegalArgumentException("Appointment was not found.");
        if ("PATIENT".equals(session.role) && !appointment.patientName.equals(session.displayName))
            throw new IllegalArgumentException("You can only cancel your own appointments.");
        if ("DOCTOR".equals(session.role) && !appointment.providerId.equals(session.providerId))
            throw new IllegalArgumentException("You can only cancel appointments assigned to your provider.");

        IntervalTree calendar = calendar(appointment.providerId, appointment.date);
        if (!calendar.remove(appointment))
            throw new IllegalStateException("Could not remove the appointment from its calendar.");
        if (appointments.remove(appointment.id) == null) {
            calendar.insert(appointment);
            throw new IllegalStateException("Could not update the appointment records.");
        }
        return appointment;
    }

    private void addProvider(Provider provider) { providers.put(provider.id, provider); }

    private DemoSession createSession(String username, Account account) {
        DemoSession session = new DemoSession(UUID.randomUUID().toString(), username, account.displayName,
                account.role, account.providerId);
        sessions.put(session.token, session);
        return session;
    }

    private IntervalTree calendar(String providerId, String date) {
        String key = providerId + "|" + date;
        IntervalTree tree = calendars.get(key);
        if (tree == null) {
            tree = new IntervalTree();
            calendars.put(key, tree);
        }
        return tree;
    }

    private void validateDate(String date) {
        try {
            LocalDate parsed = LocalDate.parse(date);
            if (parsed.isBefore(LocalDate.now())) throw new IllegalArgumentException("Choose today or a future date.");
        } catch (RuntimeException ex) {
            if (ex instanceof IllegalArgumentException && ex.getMessage() != null && ex.getMessage().startsWith("Choose")) throw ex;
            throw new IllegalArgumentException("Date must use YYYY-MM-DD format.");
        }
    }
}
