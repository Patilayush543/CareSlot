# Project outline

## Title

**Role-Based Appointment Scheduling and Waitlist Management Using Interval Trees**

## Problem

When appointments are stored as ordinary lists, checking whether a new appointment overlaps an existing one may require checking every booking. This project organizes bookings by provider and date and uses an interval tree to search time ranges for conflicts.

## Objectives

1. Find open appointment slots matching a requested provider, date, duration, and time window.
2. Reject bookings that overlap an existing appointment.
3. Allow authorized users to cancel appointments and remove intervals from the interval tree.
4. Demonstrate the use of an interval tree, hash tables, and priority queues in a working application.
5. Provide a simple React interface connected to a Java backend.

## Scope and assumptions

- Fictional providers and sample bookings are used.
- Clinic hours are 09:00 to 17:00.
- Appointment ranges use the half-open form `[start, end)`. Therefore, a 10:00–10:30 booking and a 10:30–11:00 booking do not conflict.
- Appointment data is held in memory and resets when the Java server restarts.
- The project is a classroom prototype, not a production medical system.

## Data structures

### Interval tree

Each node stores an appointment interval and the largest end time in its subtree (`maxEnd`). The tree is ordered by appointment start time. During overlap search, `maxEnd` helps skip a subtree that ends before the requested interval begins. Insertion uses treap rotations so the tree stays balanced on average.

Deletion merges the removed node's left and right treap subtrees, then refreshes `maxEnd` while returning through the recursion. This keeps future overlap searches correct after cancellation.

Overlap rule for intervals `[aStart, aEnd)` and `[bStart, bEnd)`:

```text
aStart < bEnd AND bStart < aEnd
```

### HashMap

- `providers`: provider ID → provider details.
- `calendars`: provider ID plus date → that provider/date's interval tree.
- `appointments`: appointment ID → appointment details.

Each appointment also stores the patient's name. The `GET /api/appointments` endpoint returns the booking schedule so the React screen can show who has each slot.
The `DELETE /api/appointments/{id}` endpoint removes the appointment from both the appointment map and its provider/date interval tree. Patients can cancel only their own records; administrators can cancel any record.

The demo has patient, doctor, and administrator roles. Patient accounts can search and book, and their schedule view is filtered to their own appointments. Each private doctor account is assigned to one provider and sees only that provider's appointments; the administrator can view the complete appointment register. Public registration creates patient accounts only; staff accounts remain demo-only. Credentials, registered accounts, and active sessions are stored in in-memory hash maps.

### PriorityQueue

Available candidate slots are placed in a min-priority queue ordered by start time. Polling the queue returns the earliest matching slot first.

## Main flow

1. User selects a provider, date, appointment length, and preferred time range.
2. Backend checks candidate starts in 30-minute increments during clinic hours.
3. For each candidate it searches the provider/date interval tree for an overlap.
4. Non-overlapping candidates enter the priority queue.
5. The UI displays the earliest candidates first.
6. When the user books a candidate, the backend checks for conflict again and inserts it into the interval tree.
7. The patient sees their own appointments; each doctor sees their provider schedule; the administrator sees the full register.
8. An authorized user can cancel an appointment, which deletes it from the appointment map and interval tree.
9. A new patient can register, receive a patient session, and use the same search and booking flow.

## Complexity discussion

With a balanced interval tree, insertion and typical overlap searches take `O(log n)` time, where `n` is the number of appointments for a provider on a date. A pathological, unbalanced tree can take `O(n)`. Candidate generation checks `k` possible start times, giving roughly `O(k log n)` overlap-search work. Ranking `k` candidates using a heap takes `O(k log k)` total time.

## Suggested demonstration

1. Search tomorrow for Dr. Anaya Rao in the morning.
2. Book the 09:30 slot.
3. Search again and show that the 09:30 option is no longer available.
4. Try to book 09:15–09:45 directly through the API and show that the overlapping booking is rejected.
5. Book 10:00–10:30 and show that the adjacent 09:30–10:00 booking is allowed.
6. Cancel a booking and search again to show that its interval becomes available.
7. Register a new patient, confirm that the new session opens the patient page, and verify that staff registration is not offered.

## Limitations and future improvements

- Current data is in memory; a database could persist bookings.
- The slot interval is fixed at 30 minutes; it could be configurable.
- A real service would need authentication, authorization, privacy protections, and transaction-safe storage.
- The current interval tree is a randomized treap, so its balancing guarantee is expected rather than strict worst-case balancing.
