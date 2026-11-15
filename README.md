# CareSlot: Role-Based Appointment Scheduling and Waitlist Management Using Interval Trees

A small MCA Advanced Data Structures project built with Java 8 and React. It searches appointment slots, ranks them by how soon they start, records the patient assigned to each booking, prevents overlapping bookings with a self-implemented interval tree, and exposes a simple HTTP API.

## Project structure

- `backend/` — Java 8 HTTP server; no external Java libraries required.
- `frontend/` — React + Vite user interface.

## Run the Java backend

From `backend`, compile and run (with a JDK installed):

```powershell
javac -d out src/main/java/app/scheduler/*.java
java -cp out app.scheduler.Main
```

The server listens at `http://localhost:8080`. It starts with fictional sample providers and bookings. A booking is assigned to the logged-in patient account, and the React screen shows who is booked with each provider. Data is kept in memory and resets when the server restarts.

## Run from VS Code

1. Open the `appointment-slot-finder` folder in VS Code.
2. If this is a fresh extraction, open a VS Code terminal, go to `frontend`, and run `npm install --cache .npm-cache` once.
3. Press **Ctrl+Shift+B** and choose **CareSlot: Start project** if prompted. VS Code starts the Java and React servers in separate terminals and opens the website.

The VS Code tasks launch Java and Node directly. Stop the servers from their VS Code terminal panels.

If registration shows “Cannot reach the Java server,” stop any old server terminals and run **CareSlot: Start project** again. The Java API must be running at `http://localhost:8080` before the React registration form can submit.

## One-click Windows launch

After installing the frontend dependencies once, double-click `start-careslot.bat` in the project folder. It compiles the Java backend, opens separate Java and React server windows, waits briefly, and opens `http://localhost:5173`. Keep both server windows open while using the app. If Java compilation fails, the launcher stops and shows the error instead of opening a page that cannot reach the API.

## Sign-in options

- **Patient Sign In** — patients see only their own appointments and can book or cancel their own bookings.
- **Create Account** — creates a patient account in memory and signs the new patient into the patient page.
- **Staff / Admin access** — a separate private entry for the administrator and the three provider accounts. Administrators see the complete schedule; doctors see only appointments assigned to their provider.

Demo credentials:

- Patient: `aarav` / `demo123`
- Administrator: `admin` / `admin123`
- Dr. Anaya Rao: `anaya` / `doctor123`
- Dr. Kabir Mehta: `kabir` / `doctor123`
- Dr. Mira Shah: `mira` / `doctor123`

Staff accounts are demo-only and cannot be created through the public registration form. Each doctor account is assigned to one provider; doctors can view and cancel only their own provider's appointments, while the administrator can view and cancel the full schedule. Credentials and accounts are stored in memory, not production authentication.

The interface uses the CareSlot brand with a subtle “Designed by Ayush” credit. The small sample-data note is informational: all providers and appointments are fictional.

To open the patient screen, start the project and visit `http://localhost:5173`; choose **Sign In** or **Create Account**. To open the staff screen, choose **Staff / Admin access** on the same entry page and use one of the staff credentials above.

## Run the React frontend

Install Node.js, then from `frontend` run `npm install` once and `npm run dev`. Open the local URL printed by Vite. The backend must be running separately. If npm's default cache folder is not writable, add `--cache .npm-cache` to the install command.

The Java API includes the browser CORS headers and `OPTIONS` preflight response needed by the React development server. Use the VS Code task to start both servers when possible.

## Deploy to Railway

Railway must run the frontend and Java backend as two services:

1. Create a Java backend service with the repository root directory set to `backend`.
   - Build command: `javac -d out src/main/java/app/scheduler/*.java`
   - Start command: `java -cp out app.scheduler.Main`
   - Railway supplies the `PORT` environment variable; the backend now uses it automatically. Locally it still defaults to port `8080`.
2. Create the frontend service with the repository root directory set to `frontend`.
   - Build command: `npm run build`
   - Start command: `npx vite preview --host 0.0.0.0 --port $PORT`
   - Set the frontend environment variable `VITE_API_URL` to the backend's public URL plus `/api`, for example `https://your-java-backend.up.railway.app/api`.
   - Redeploy the frontend after setting this variable because Vite embeds it during the build.
3. Open the frontend public URL from another device. Do not use `localhost` in `VITE_API_URL`; on another device, `localhost` points to that device itself.

The Java service remains an in-memory classroom backend, so its accounts and appointments reset whenever Railway restarts or redeploys it.

## Data structures demonstrated

- **Interval tree (implemented in this project):** stores appointment time ranges by provider and date and checks overlaps efficiently.
- **HashMap:** indexes providers and the interval tree for each provider/date.
- **PriorityQueue:** ranks available slots by start time.

## Cancellation demonstration

1. Sign in as `aarav` and view the sample appointment.
2. Select **Cancel** for Aarav's appointment.
3. Search the same provider and date again; the cancelled interval is available.
4. Sign in as `admin` to cancel any remaining appointment and view the complete schedule.

Patients can cancel only their own appointments. The backend removes a booking from both the appointment `HashMap` and the matching provider/date interval tree.

After a successful booking, the patient sees a confirmation page containing the patient, doctor, service, date, time range, and backend booking ID. Failed bookings remain on the booking page and show the error instead.

All sample data is fictional. This is a classroom prototype, not a production booking system.
