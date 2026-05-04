/* ============================================================
   api.js – Shared API helpers & auth utilities
   ============================================================ */

const API = '/api';
const AUTH_USER_KEY = 'cinema_user';
const AUTH_ACCESS_TOKEN_KEY = 'cinema_access_token';
const AUTH_EXPIRES_AT_KEY = 'cinema_access_expires_at';

// Migrate dữ liệu đăng nhập cũ (localStorage) sang sessionStorage theo tab.
// Sau lần đầu, xoá bản global để tránh tab khác ghi đè phiên hiện tại.
(() => {
  const legacy = localStorage.getItem(AUTH_USER_KEY);
  if (legacy && !sessionStorage.getItem(AUTH_USER_KEY)) {
    sessionStorage.setItem(AUTH_USER_KEY, legacy);
  }
  if (legacy) {
    localStorage.removeItem(AUTH_USER_KEY);
  }
  localStorage.removeItem(AUTH_ACCESS_TOKEN_KEY);
  localStorage.removeItem(AUTH_EXPIRES_AT_KEY);
})();

// ── Auth helpers ────────────────────────────────────────────
function getCurrentUser() {
  try {
    const raw = sessionStorage.getItem(AUTH_USER_KEY);
    if (!raw) return null;
    const user = JSON.parse(raw);
    const tokenUserId = getAuthUserId();
    const currentUserId = user?._key || user?.key || null;

    // Tránh lẫn phiên: user trong storage phải cùng subject với JWT.
    if (!tokenUserId || !currentUserId || tokenUserId !== currentUserId) {
      clearCurrentUser();
      return null;
    }
    return user;
  } catch {
    return null;
  }
}
function getAccessToken() {
  return sessionStorage.getItem(AUTH_ACCESS_TOKEN_KEY);
}
function getTokenExpiresAt() {
  return sessionStorage.getItem(AUTH_EXPIRES_AT_KEY);
}
function isAccessTokenExpired() {
  const expiresAt = getTokenExpiresAt();
  if (!expiresAt) return true;
  return Date.now() >= new Date(expiresAt).getTime();
}
function parseJwtPayload(token) {
  try {
    if (!token) return null;
    const parts = token.split('.');
    if (parts.length < 2) return null;
    const payload = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = payload + '='.repeat((4 - (payload.length % 4)) % 4);
    return JSON.parse(atob(padded));
  } catch {
    return null;
  }
}
function getAuthUserId() {
  const token = getAccessToken();
  const payload = parseJwtPayload(token);
  return payload?.sub || null;
}
function setCurrentUser(user) {
  sessionStorage.setItem(AUTH_USER_KEY, JSON.stringify(user));
}
function setAuthSession(auth) {
  if (!auth) return;
  if (auth.user) {
    setCurrentUser(auth.user);
  }
  if (auth.accessToken) {
    sessionStorage.setItem(AUTH_ACCESS_TOKEN_KEY, auth.accessToken);
  }
  if (auth.expiresAt) {
    sessionStorage.setItem(AUTH_EXPIRES_AT_KEY, auth.expiresAt);
  }
}
function clearCurrentUser() {
  sessionStorage.removeItem(AUTH_USER_KEY);
  sessionStorage.removeItem(AUTH_ACCESS_TOKEN_KEY);
  sessionStorage.removeItem(AUTH_EXPIRES_AT_KEY);
}
function requireAuth() {
  if (!getCurrentUser() || !getAccessToken() || isAccessTokenExpired() || !getAuthUserId()) {
    clearCurrentUser();
    window.location.href = '/login.html';
    return false;
  }
  return true;
}

// ── Format helpers ──────────────────────────────────────────
function formatVND(amount) {
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount);
}
function formatDateTime(iso) {
  if (!iso) return '';
  const d = new Date(iso);
  return d.toLocaleString('vi-VN', { dateStyle: 'short', timeStyle: 'short' });
}
function formatDuration(min) {
  const h = Math.floor(min / 60), m = min % 60;
  return h > 0 ? `${h}h${m > 0 ? m + 'm' : ''}` : `${m}m`;
}
function rankLabel(rank) {
  const map = { normal: '🎟 Normal', vip: '⭐ VIP', premium: '🏆 Premium' };
  return map[rank] || rank;
}
function rankClass(rank) {
  return { normal: 'rank-normal', vip: 'rank-vip', premium: 'rank-premium' }[rank] || 'rank-normal';
}

// ── HTTP helpers ────────────────────────────────────────────
async function apiFetch(path, options = {}) {
  const token = getAccessToken();
  const headers = { 'Content-Type': 'application/json', ...options.headers };
  if (token && !isAccessTokenExpired()) {
    headers.Authorization = `Bearer ${token}`;
  }

  const res = await fetch(API + path, {
    headers,
    ...options
  });
  const data = await res.json().catch(() => ({}));
  if (res.status === 401) {
    clearCurrentUser();
  }
  if (!res.ok) throw new Error(data.error || `HTTP ${res.status}`);
  return data;
}

const api = {
  // Movies
  getMovies:    (params = '') => apiFetch(`/movies${params}`),
  getMovie:     (id)          => apiFetch(`/movies/${id}`),

  // Screenings
  getScreenings:     (movieId)     => apiFetch(`/screenings?movieId=${movieId}`),
  getSeatMap:        (screeningId) => apiFetch(`/screenings/${screeningId}/seats`),

  // Bookings
  createBooking:     (body)   => apiFetch('/bookings', { method: 'POST', body: JSON.stringify(body) }),
  getUserBookings:   (userId) => apiFetch(`/bookings/user/${userId}`),

  // Users
  register: (body) => apiFetch('/users/register', { method: 'POST', body: JSON.stringify(body) }),
  login:    (body) => apiFetch('/users/login',    { method: 'POST', body: JSON.stringify(body) }),
  getUser:  (id)   => apiFetch(`/users/${id}`),

  // Reports
  getOverview:      ()        => apiFetch('/reports/overview'),
  getMovieReport:   (movieId) => apiFetch(`/reports/movies/${movieId}`),
  getMovieReports:  ()        => apiFetch('/reports/movies'),

  // AI Review
  getAiMovieReview: (movieId) => apiFetch(`/ai/review/${movieId}`),
};

// ── Render navbar user state ────────────────────────────────
function renderNavUser() {
  const user = getCurrentUser();
  const el = document.getElementById('nav-user');
  if (!el) return;
  if (user) {
    el.innerHTML = `
      <span class="rank-badge ${rankClass(user.memberRank)} me-2">${rankLabel(user.memberRank)}</span>
      <span class="text-dark me-3 fw-semibold">${user.name}</span>
      <button class="btn-outline-custom btn-sm" onclick="logout()">Đăng xuất</button>`;
  } else {
    el.innerHTML = `
      <a href="/login.html" class="nav-link-custom me-2">Đăng nhập</a>
      <a href="/login.html#register" class="btn-primary-custom">Đăng ký</a>`;
  }
}
function logout() {
  clearCurrentUser();
  window.location.href = '/';
}

// Shared navbar HTML
function getNavbarHTML(activePage = '') {
  return `
  <nav class="navbar-custom">
    <div class="container d-flex align-items-center justify-content-between flex-wrap gap-2">
      <a class="navbar-brand-custom" href="/">🎬 Cinema<span>Book</span></a>
      <div class="d-flex align-items-center gap-1 flex-wrap">
        <a href="/"            class="nav-link-custom ${activePage==='home'?'active':''}">Trang chủ</a>
        <a href="/movies.html"  class="nav-link-custom ${activePage==='movies'?'active':''}">Danh sách phim</a>
        <a href="/report.html"  class="nav-link-custom ${activePage==='report'?'active':''}">📊 Báo cáo</a>
      </div>
      <div class="d-flex align-items-center" id="nav-user"></div>
    </div>
  </nav>`;
}

function getFooterHTML() {
  return `
  <footer>
    <div class="container text-center">
      <div class="brand mb-2">🎬 CinemaBook</div>
      <p>© 2026 CinemaBook. Hệ thống đặt vé xem phim trực tuyến.</p>
      <p>Powered by Spring Boot + ArangoDB</p>
    </div>
  </footer>`;
}
