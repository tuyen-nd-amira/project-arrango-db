/* movies.js – Movie listing page */

document.getElementById('navbar-placeholder').innerHTML = getNavbarHTML('movies');
document.getElementById('footer-placeholder').innerHTML = getFooterHTML();
renderNavUser();

let currentGenre = '';
let searchTimeout = null;
let genresBuilt = false;

function moviePoster(m) {
  return m.poster_url || m.imageUrl || `https://picsum.photos/seed/${m._key}/400/600`;
}

function extractGenres(movies) {
  const set = new Set();
  movies.forEach(m => {
    const raw = (m.genre || '').trim();
    if (!raw) return;
    raw.split(',').map(x => x.trim()).filter(Boolean).forEach(g => set.add(g));
  });
  return Array.from(set).sort((a, b) => a.localeCompare(b, 'vi'));
}

function renderGenreFilters(genres) {
  const wrap = document.getElementById('genre-filters');
  if (!wrap) return;

  if (!genres.length) {
    wrap.style.display = 'none';
    return;
  }

  wrap.style.display = '';

  const buttons = ['<button class="genre-btn active" data-genre="">Tất cả</button>']
    .concat(genres.map(g => `<button class="genre-btn" data-genre="${g}">${g}</button>`));

  wrap.innerHTML = buttons.join('');
}

async function loadMovies(genre = '', search = '') {
  const grid = document.getElementById('movies-grid');
  grid.innerHTML = '<div class="col-12 text-center py-5"><div class="spinner"></div></div>';
  try {
    let params = '';
    if (search) params = `?search=${encodeURIComponent(search)}`;
    else if (genre) params = `?genre=${encodeURIComponent(genre)}`;
    const movies = await api.getMovies(params);

    if (!genresBuilt) {
      renderGenreFilters(extractGenres(await api.getMovies()));
      genresBuilt = true;
    }

    grid.innerHTML = movies.length === 0
      ? '<div class="col-12 text-center text-muted py-5">Không tìm thấy phim nào.</div>'
      : movies.map(movieCardHTML).join('');
  } catch (e) {
    grid.innerHTML = `<div class="col-12 text-center text-danger py-4">Lỗi: ${e.message}</div>`;
  }
}

function movieCardHTML(m) {
  return `
    <div class="col-6 col-md-4 col-lg-3">
      <div class="movie-card h-100">
        <div style="position:relative;overflow:hidden;height:300px">
          <img src="${moviePoster(m)}" alt="${m.title}"
               style="width:100%;height:100%;object-fit:cover;transition:transform 0.3s"
               onmouseover="this.style.transform='scale(1.06)'" onmouseout="this.style.transform='scale(1)'"
               onerror="this.src='https://picsum.photos/seed/${m._key}/400/600'">
        </div>
        <div class="movie-card-body">
          <div class="movie-title" title="${m.title}">${m.title}</div>
          <div class="d-flex justify-content-between mt-1 mb-2">
            <span class="duration">⏱ ${formatDuration(m.duration)}</span>
            <span class="text-muted" style="font-size:0.8rem">${m.status || ''}</span>
          </div>
          <p class="text-muted" style="font-size:0.8rem;height:48px;overflow:hidden;margin-bottom:10px">
            ${m.description}
          </p>
          <a href="/booking.html?movieId=${m._key}" class="btn-primary-custom w-100 text-center"
             style="font-size:0.9rem;padding:9px">🎟 Đặt vé ngay</a>
        </div>
      </div>
    </div>`;
}

// Genre filter
document.getElementById('genre-filters').addEventListener('click', e => {
  const btn = e.target.closest('.genre-btn');
  if (!btn) return;
  document.querySelectorAll('.genre-btn').forEach(b => b.classList.remove('active'));
  btn.classList.add('active');
  currentGenre = btn.dataset.genre;
  loadMovies(currentGenre);
});

// Search with debounce
document.getElementById('search-input').addEventListener('input', e => {
  clearTimeout(searchTimeout);
  searchTimeout = setTimeout(() => {
    const q = e.target.value.trim();
    if (q.length === 0) loadMovies(currentGenre);
    else loadMovies('', q);
  }, 400);
});

loadMovies();
