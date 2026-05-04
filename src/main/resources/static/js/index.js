/* index.js – Home page logic */

document.getElementById('navbar-placeholder').innerHTML = getNavbarHTML('home');
document.getElementById('footer-placeholder').innerHTML = getFooterHTML();
renderNavUser();

// Update hero auth button if logged in
const user = getCurrentUser();
if (user) {
  const btn = document.getElementById('hero-auth-btn');
  if (btn) { btn.textContent = 'Lịch sử đặt vé'; btn.href = '/login.html'; }
}

// Load featured movies (first 6)
async function loadFeaturedMovies() {
  const container = document.getElementById('featured-movies');
  try {
    const movies = await api.getMovies();
    const featured = movies.slice(0, 6);
    container.innerHTML = featured.length === 0
      ? '<div class="col text-center text-muted py-4">Chưa có phim nào.</div>'
      : featured.map(movieCardHTML).join('');
  } catch (e) {
    container.innerHTML = `<div class="col text-center text-danger py-4">Lỗi tải phim: ${e.message}</div>`;
  }
}

function movieCardHTML(m) {
  return `
    <div class="col-6 col-md-4 col-lg-2">
      <div class="movie-card h-100">
        <img src="${m.imageUrl || 'https://picsum.photos/300/450'}" alt="${m.title}" loading="lazy"
             onerror="this.src='https://picsum.photos/seed/${m._key}/300/450'">
        <div class="movie-card-body">
          <div class="movie-title" title="${m.title}">${m.title}</div>
          <div class="d-flex justify-content-between align-items-center mt-2 mb-3">
            <span class="badge-genre">${m.genre}</span>
            <span class="rating">★ ${m.rating}</span>
          </div>
          <a href="/booking.html?movieId=${m._key}" class="btn-primary-custom w-100 text-center" style="font-size:0.85rem;padding:8px">Đặt vé</a>
        </div>
      </div>
    </div>`;
}

// Load system stats
async function loadStats() {
  try {
    const [overview, movies] = await Promise.all([api.getOverview(), api.getMovies()]);
    document.getElementById('stat-movies').textContent  = movies.length;
    document.getElementById('stat-tickets').textContent = (overview.totalTickets || 0).toLocaleString('vi-VN');
    document.getElementById('stat-revenue').textContent = formatVND(overview.totalRevenue || 0);
    document.getElementById('stat-users').textContent   = (overview.totalUsers || 0).toLocaleString('vi-VN');
  } catch { /* ignore stats errors */ }
}

loadFeaturedMovies();
loadStats();
