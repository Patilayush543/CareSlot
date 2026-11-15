import React, { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import './style.css';

const API = (import.meta.env.VITE_API_URL || 'http://localhost:8080/api').replace(/\/$/, '');
const today = new Date();
today.setDate(today.getDate() + 1);
const tomorrow = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;

function App() {
  const [providers, setProviders] = useState([]);
  const [session, setSession] = useState(null);
  const [loginUsername, setLoginUsername] = useState('');
  const [loginPassword, setLoginPassword] = useState('');
  const [loginError, setLoginError] = useState('');
  const [authMode, setAuthMode] = useState('patient');
  const [registerName, setRegisterName] = useState('');
  const [registerUsername, setRegisterUsername] = useState('');
  const [registerPassword, setRegisterPassword] = useState('');
  const [registerConfirm, setRegisterConfirm] = useState('');
  const [providerId, setProviderId] = useState('');
  const [date, setDate] = useState(tomorrow);
  const [duration, setDuration] = useState(30);
  const [from, setFrom] = useState('09:00');
  const [to, setTo] = useState('17:00');
  const [slots, setSlots] = useState([]);
  const [appointments, setAppointments] = useState([]);
  const [confirmedAppointment, setConfirmedAppointment] = useState(null);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  async function refreshAppointments(token) {
    try {
      const response = await fetch(`${API}/appointments`, { headers: { Authorization: `Bearer ${token}` } });
      const data = await response.json();
      if (response.ok) setAppointments(data);
      else setError(data.error || 'Could not load appointments. Please log in again.');
    } catch (_) { /* The provider request shows the connection message. */ }
  }

  useEffect(() => {
    fetch(`${API}/providers`).then(r => r.json()).then(list => {
      setProviders(list);
      if (list.length) setProviderId(list[0].id);
    }).catch(() => setError('Could not connect to the Java server. Start the backend and refresh this page.'));
  }, []);

  useEffect(() => { if (session) refreshAppointments(session.token); }, [session]);

  async function submitLogin(event) {
    event.preventDefault(); setLoginError('');
    try {
      const response = await fetch(`${API}/login`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: loginUsername, password: loginPassword })
      });
      const data = await response.json();
      if (!response.ok) throw new Error(data.error || 'Login failed.');
      setSession(data); setError('');
    } catch (e) { setLoginError(connectionMessage(e, 'Login failed.')); }
  }

  async function submitRegister(event) {
    event.preventDefault(); setLoginError('');
    if (registerPassword !== registerConfirm) {
      setLoginError('Passwords do not match.');
      return;
    }
    try {
      const response = await fetch(`${API}/register`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ displayName: registerName, username: registerUsername, password: registerPassword })
      });
      const data = await response.json();
      if (!response.ok) throw new Error(data.error || 'Registration failed.');
      setSession(data); setError('');
    } catch (e) { setLoginError(connectionMessage(e, 'Registration failed.')); }
  }

  async function logout() {
    if (session) await fetch(`${API}/logout`, { method: 'POST', headers: { Authorization: `Bearer ${session.token}` } }).catch(() => {});
    setSession(null); setAppointments([]); setSlots([]); setConfirmedAppointment(null); setMessage(''); setError('');
  }

  async function search(event) {
    event.preventDefault();
    setError(''); setMessage(''); setLoading(true); setSlots([]);
    const params = new URLSearchParams({ providerId, date, duration, from: toMinutes(from), to: toMinutes(to) });
    try {
      const response = await fetch(`${API}/slots?${params}`, { headers: { Authorization: `Bearer ${session.token}` } });
      const data = await response.json();
      if (!response.ok) throw new Error(data.error || 'Search failed.');
      setSlots(data);
      if (!data.length) setMessage('No matching times. Try a wider time range or another date.');
    } catch (e) { setError(e.message); }
    finally { setLoading(false); }
  }

  async function book(slot) {
    setError(''); setMessage('');
    try {
      const response = await fetch(`${API}/appointments`, {
        method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${session.token}` },
        body: JSON.stringify({ providerId, date, startTime: slot.startTime, endTime: slot.endTime })
      });
      const data = await response.json();
      if (!response.ok) throw new Error(data.error || 'Booking failed.');
      setAppointments(current => [...current, data].sort((a, b) => a.date.localeCompare(b.date) || a.startTime.localeCompare(b.startTime)));
      setSlots(current => current.filter(s => s.startTime !== slot.startTime));
      setConfirmedAppointment(data);
    } catch (e) { setError(e.message); }
  }

  async function cancelAppointment(appointment) {
    if (!window.confirm(`Cancel the appointment with ${appointment.providerName} at ${appointment.startTime}?`)) return;
    setError(''); setMessage('');
    try {
      const response = await fetch(`${API}/appointments/${encodeURIComponent(appointment.id)}`, {
        method: 'DELETE', headers: { Authorization: `Bearer ${session.token}` }
      });
      const data = await response.json();
      if (!response.ok) throw new Error(data.error || 'Cancellation failed.');
      setAppointments(current => current.filter(item => item.id !== appointment.id));
      setMessage(`Cancelled ${data.startTime}–${data.endTime} with ${data.providerName}.`);
    } catch (e) { setError(e.message); }
  }

  if (!session) return <LoginPage mode={authMode} setMode={setAuthMode} setLoginError={setLoginError} username={loginUsername} setUsername={setLoginUsername} password={loginPassword} setPassword={setLoginPassword} onSubmit={submitLogin} name={registerName} setName={setRegisterName} registerUsername={registerUsername} setRegisterUsername={setRegisterUsername} registerPassword={registerPassword} setRegisterPassword={setRegisterPassword} registerConfirm={registerConfirm} setRegisterConfirm={setRegisterConfirm} onRegister={submitRegister} error={loginError} />;
  if (confirmedAppointment) return <ConfirmationPage appointment={confirmedAppointment} onReturn={() => setConfirmedAppointment(null)} onLogout={logout} />;
  const isAdmin = session.role === 'ADMIN';
  const isDoctor = session.role === 'DOCTOR';
  const isStaff = isAdmin || isDoctor;
  const provider = providers.find(p => p.id === providerId);
  return <main className="page-shell">
    <header className="topbar"><a className="brand" href="#top"><span className="brand-mark">+</span> CareSlot</a><div className="user-tools"><span className="user-greeting">{session.displayName}<small>{isAdmin ? 'ADMIN' : isDoctor ? 'DOCTOR' : 'PATIENT'}</small></span><button className="logout-button" onClick={logout}>Log out</button></div></header>
    <section className="hero" id="top">
      <div className="hero-copy"><p className="eyebrow">A LITTLE EASIER TO GET CARE</p><h1>Find a time<br /><em>that works.</em></h1><p className="intro">Choose a provider and a time window. We’ll find the earliest appointments that fit.</p><div className="hero-note"><span>✳</span><span>Simple scheduling, with no overlapping bookings.</span></div></div>
      <div className="hero-art" aria-hidden="true"><div className="sun" /><div className="art-card"><div className="art-card-top"><span>YOUR DAY</span><span>✦</span></div><div className="art-time">10:30</div><div className="art-line" /><div className="art-doctor"><div className="avatar">AR</div><div><b>Dr. Anaya Rao</b><small>General Physician</small></div><span className="art-check">✓</span></div></div><div className="floating-pill">A good time for you <span>↗</span></div><div className="sparkle">✳</div></div>
    </section>
    <section className="booking-layout">
      {isStaff ? <div className="search-card admin-summary"><p className="eyebrow">{isDoctor ? 'DOCTOR WORKSPACE' : 'ADMIN WORKSPACE'}</p><h2>{isDoctor ? 'Your provider schedule' : 'Clinic schedule'}</h2><p>{isDoctor ? 'Appointments assigned to your provider appear here with the patient and service details.' : 'All confirmed appointments appear on this page with the patient and provider assigned to each slot.'}</p><div className="admin-stat"><b>{appointments.length}</b><span>{isDoctor ? 'appointments assigned to you' : 'appointments in the schedule'}</span></div><div className="admin-tip">{isDoctor ? 'You can view and cancel appointments assigned to your provider.' : 'Patient accounts can search and reserve open appointment times. The admin account can view the complete schedule.'}</div></div> : <form className="search-card" onSubmit={search}>
        <div className="section-heading"><div><p className="eyebrow">YOUR APPOINTMENT</p><h2>Let’s find your slot</h2></div><span className="step">01 <i>/ 02</i></span></div>
        <div className="signed-in-note">Booking as <b>{session.displayName}</b></div>
        <label>Choose a service provider<select value={providerId} onChange={e => setProviderId(e.target.value)} required>{providers.map(p => <option key={p.id} value={p.id}>{p.name} · {p.service}</option>)}</select></label>
        <div className="field-row"><label>Date<input type="date" min={tomorrow} value={date} onChange={e => setDate(e.target.value)} required /></label><label>Appointment length<select value={duration} onChange={e => setDuration(Number(e.target.value))}><option value="30">30 minutes</option><option value="45">45 minutes</option><option value="60">1 hour</option></select></label></div>
        <div className="field-row"><label>From<input type="time" value={from} onChange={e => setFrom(e.target.value)} required /></label><label>Until<input type="time" value={to} onChange={e => setTo(e.target.value)} required /></label></div>
        <button className="primary-button" disabled={!providerId || loading}>{loading ? 'Finding times…' : 'Find available times'} <span>→</span></button>
        <p className="privacy-note"><span>✦</span> This demo uses fictional providers and sample bookings.</p>
      </form>}
      <div className="results-area">
        <div className="results-heading"><div><p className="eyebrow">{isAdmin ? 'ADMIN SCHEDULE' : isDoctor ? 'YOUR PROVIDER SCHEDULE' : 'AVAILABLE TIMES'}</p><h2>{isAdmin ? 'All appointments' : isDoctor ? 'Your appointments' : slots.length ? `${slots.length} good options` : 'Your matches will appear here'}</h2></div><span className="result-icon">◷</span></div>
        {error && <div className="alert error">{error}</div>}
        {message && <div className="alert success">✓ &nbsp;{message}</div>}
        {!isStaff && (slots.length ? <div className="slot-list">{slots.map((slot, i) => <article className="slot-card" key={slot.startTime}><div className="slot-date"><span>{new Date(`${date}T12:00:00`).toLocaleDateString(undefined, { weekday: 'short' }).toUpperCase()}</span><b>{slot.startTime}</b><small>to {slot.endTime}</small></div><div className="slot-info"><b>{provider?.name}</b><span>{provider?.service}</span><small>{new Date(`${date}T12:00:00`).toLocaleDateString(undefined, { month: 'long', day: 'numeric' })}</small></div><button className="book-button" onClick={() => book(slot)}>Book <span>↗</span></button>{i === 0 && <span className="best-badge">EARLIEST</span>}</article>)}</div> : <div className="empty-state"><div className="empty-illustration"><div className="calendar-icon"><span>JUL</span><b>24</b><i>✓</i></div><span className="empty-spark">✦</span></div><h3>Good things take a moment.</h3><p>Search to see appointment times that fit your day.</p></div>)}
        {!isStaff && <div className="results-foot"><span className="mini-dot" /> Times are ranked from earliest to latest</div>}
        <section className="booked-panel"><div className="booked-heading"><div><p className="eyebrow">{isAdmin ? 'FULL REGISTER' : isDoctor ? 'YOUR PROVIDER SCHEDULE' : 'YOUR SCHEDULE'}</p><h3>{isAdmin ? 'Who’s booked' : isDoctor ? 'Your appointments' : 'Your appointments'}</h3></div><span className="booking-count">{appointments.length}</span></div>
          {appointments.length ? <div className="booked-list">{appointments.map(a => <article className="booked-row" key={a.id}><div className="booked-time"><b>{a.startTime}</b><span>{a.endTime}</span></div><div className="booked-person"><b>{a.patientName}</b><span>{a.providerName} · {a.date}</span></div><button className="cancel-button" onClick={() => cancelAppointment(a)}>Cancel</button><span className="booking-status">BOOKED</span></article>)}</div> : <p className="no-bookings">No appointments have been booked yet.</p>}
        </section>
      </div>
    </section>
    <footer><span>CareSlot <i>·</i> Designed by Ayush</span><span>Fictional sample data for demonstration</span></footer>
  </main>;
}

function ConfirmationPage({ appointment, onReturn, onLogout }) {
  return <main className="page-shell confirmation-shell">
    <header className="topbar"><a className="brand" href="#top"><span className="brand-mark">+</span> CareSlot</a><button className="logout-button" onClick={onLogout}>Log out</button></header>
    <section className="confirmation-card">
      <div className="confirmation-icon">✓</div>
      <p className="eyebrow">APPOINTMENT CONFIRMED</p>
      <h1>Thank you, your appointment is booked</h1>
      <p className="confirmation-intro">Your appointment has been added to your schedule.</p>
      <div className="confirmation-details">
        <div><span>Patient</span><b>{appointment.patientName}</b></div>
        <div><span>Doctor</span><b>{appointment.providerName}</b></div>
        <div><span>Service</span><b>{appointment.service}</b></div>
        <div><span>Date</span><b>{appointment.date}</b></div>
        <div><span>Time</span><b>{appointment.startTime} – {appointment.endTime}</b></div>
        <div><span>Booking ID</span><b className="booking-id">{appointment.id}</b></div>
      </div>
      <button className="primary-button confirmation-button" onClick={onReturn}>Return to my appointments <span>→</span></button>
    </section>
  </main>;
}

function LoginPage({ mode, setMode, setLoginError, username, setUsername, password, setPassword, onSubmit, name, setName, registerUsername, setRegisterUsername, registerPassword, setRegisterPassword, registerConfirm, setRegisterConfirm, onRegister, error }) {
  const isRegister = mode === 'register';
  const isStaff = mode === 'staff';
  return <main className="login-shell">
    <header className="topbar login-topbar"><a className="brand" href="#login"><span className="brand-mark">+</span> CareSlot</a><span className="brand-credit">Designed by Ayush</span></header>
    <section className="login-layout">
      <div className="login-welcome"><p className="eyebrow">YOUR TIME, WELL SPENT</p><h1>Care that fits<br />your <em>day.</em></h1><p>Find a suitable appointment, keep your plans in view, and arrive prepared.</p><div className="login-benefits"><span>✦</span><div><b>One clear place for your schedule</b><small>Book and manage appointments from your patient account.</small></div></div></div>
      <div className={`login-card ${isStaff ? 'staff-login-card' : ''}`}><div className="login-icon">{isStaff ? '▣' : '↗'}</div><p className="eyebrow">{isStaff ? 'PRIVATE STAFF ACCESS' : 'WELCOME TO CARESLOT'}</p><h2>{isStaff ? 'Staff / admin sign in' : isRegister ? 'Create your patient account' : 'Sign in to your account'}</h2><p className="login-subtitle">{isStaff ? 'Use the private staff demo account to view the full schedule.' : isRegister ? 'Create a patient account to book and manage your appointments.' : 'Continue to your appointments and available times.'}</p>
        {!isStaff && <div className="patient-switch" role="tablist" aria-label="Patient account options"><button type="button" className={!isRegister ? 'active' : ''} onClick={() => { setMode('patient'); setLoginError(''); }}>Sign In</button><button type="button" className={isRegister ? 'active' : ''} onClick={() => { setMode('register'); setLoginError(''); }}>Create Account</button></div>}
        {isRegister ? <form onSubmit={onRegister} className="login-form"><label>Full name<input autoComplete="name" value={name} onChange={e => setName(e.target.value)} required /></label><label>Username<input autoComplete="username" value={registerUsername} onChange={e => setRegisterUsername(e.target.value)} required /></label><label>Password<input type="password" autoComplete="new-password" value={registerPassword} onChange={e => setRegisterPassword(e.target.value)} required /></label><label>Confirm password<input type="password" autoComplete="new-password" value={registerConfirm} onChange={e => setRegisterConfirm(e.target.value)} required /></label>{error && <div className="alert error">{error}</div>}<button className="primary-button">Create patient account <span>→</span></button></form> : <form onSubmit={onSubmit} className="login-form"><label>Username<input autoComplete="username" value={username} onChange={e => setUsername(e.target.value)} required /></label><label>Password<input type="password" autoComplete="current-password" value={password} onChange={e => setPassword(e.target.value)} required /></label>{error && <div className="alert error">{error}</div>}<button className="primary-button">{isStaff ? 'Open staff schedule' : 'Sign in'} <span>→</span></button></form>}
        {isStaff ? <><p className="staff-note">Staff access is private and demo-only. Patient registration cannot create staff accounts.</p><button className="text-button" type="button" onClick={() => { setMode('patient'); setLoginError(''); }}>← Back to patient sign in</button></> : <button className="staff-entry" type="button" onClick={() => { setMode('staff'); setLoginError(''); }}>Staff / Admin access <span>→</span></button>}
      </div>
    </section><footer className="login-footer"><span>CareSlot <i>·</i> Designed by Ayush</span><span>Fictional sample data for demonstration</span></footer>
  </main>;
}

function toMinutes(time) { const [h, m] = time.split(':').map(Number); return h * 60 + m; }

function connectionMessage(error, fallback) {
  if (error instanceof TypeError) return 'Cannot reach the Java server. Run “CareSlot: Start project” in VS Code, then try again.';
  return error.message || fallback;
}

createRoot(document.getElementById('root')).render(<React.StrictMode><App /></React.StrictMode>);
