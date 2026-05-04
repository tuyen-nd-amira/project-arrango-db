/* booking.js – Seat selection and booking logic */

document.getElementById('navbar-placeholder').innerHTML = getNavbarHTML('movies');
document.getElementById('footer-placeholder').innerHTML = getFooterHTML();
renderNavUser();

const params     = new URLSearchParams(location.search);
const movieId    = params.get('movieId');
const screeningIdParam = params.get('screeningId');

let currentScreeningId = screeningIdParam || null;
let selectedSeats = [];   // { key, seatLabel }
let screeningPrice = 0;
let currentMovie = null;

if (!movieId) {
  window.location.href = '/movies.html';
}

// ── Main loader ──────────────────────────────────────────────
async function init() {
  const container = document.getElementById('booking-content');
  try {
    const [movie, screenings] = await Promise.all([
      api.getMovie(movieId),
      api.getScreenings(movieId)
    ]);
    document.getElementById('breadcrumb-movie').textContent = movie.title;
    document.title = `Đặt vé – ${movie.title} | CinemaBook`;
    currentMovie = movie;

    if (screenings.length === 0) {
      container.innerHTML = `
        <div class="text-center py-5">
          <div style="font-size:3rem">😕</div>
          <h4 class="mt-3">Không có suất chiếu nào cho phim này</h4>
          <a href="/movies.html" class="btn-outline-custom mt-3">Xem phim khác</a>
        </div>`;
      return;
    }

    if (!currentScreeningId) currentScreeningId = screenings[0]._key;

    container.innerHTML = buildPageHTML(movie, screenings);
    attachScreeningListeners();
    await loadSeatMap(currentScreeningId, screenings);

  } catch (e) {
    container.innerHTML = `<div class="text-center text-danger py-5">Lỗi: ${e.message}</div>`;
  }
}

// ── Page HTML skeleton ───────────────────────────────────────
function buildPageHTML(movie, screenings) {
  return `
  <div class="row g-4">
    <!-- Left: movie info + screenings + seat map -->
    <div class="col-lg-8">

      <!-- Movie info -->
      <div class="movie-card mb-4" style="flex-direction:row;display:flex;overflow:hidden">
        <img src="${movie.imageUrl}" alt="${movie.title}"
             style="width:130px;object-fit:cover;flex-shrink:0"
             onerror="this.src='https://picsum.photos/seed/${movie._key}/130/195'">
        <div class="p-4 flex-grow-1">
          <h3 class="fw-bold mb-1">${movie.title}</h3>
          <div class="d-flex flex-wrap gap-2 mb-2">
            <span class="badge-genre">${movie.genre}</span>
            <span class="text-muted small">⏱ ${formatDuration(movie.duration)}</span>
            <span class="rating small">★ ${movie.rating}</span>
          </div>
          <p class="text-muted" style="font-size:0.88rem;margin-bottom:8px">${movie.description}</p>
          <small class="text-muted">Đạo diễn: ${movie.director}</small>
        </div>
      </div>

      <!-- Screening time selector -->
      <div class="mb-4">
        <h5 class="fw-bold mb-3">⏰ Chọn suất chiếu</h5>
        <div class="d-flex flex-wrap gap-3" id="screening-pills">
          ${screenings.map(s => `
            <div class="screening-pill ${s._key === currentScreeningId ? 'active' : ''}"
                 data-sid="${s._key}" data-price="${s.price}">
              <div class="time">${formatDateTime(s.showTime)}</div>
              <div class="price mt-1">${formatVND(s.price)}/vé</div>
            </div>`).join('')}
        </div>
      </div>

      <!-- Seat map -->
      <div id="seat-map-wrapper">
        <div class="text-center py-4"><div class="spinner"></div></div>
      </div>
    </div>

    <!-- Right: Booking summary -->
    <div class="col-lg-4">
      <div class="booking-summary" id="booking-summary">
        <h5 class="fw-bold mb-4">🎟 Thông tin đặt vé</h5>

        <div class="mb-3">
          <small class="text-muted">Phim</small>
          <div class="fw-bold">${movie.title}</div>
        </div>

        <div class="mb-3">
          <small class="text-muted">Suất chiếu</small>
          <div class="fw-bold" id="summary-time">–</div>
        </div>

        <div class="mb-3">
          <small class="text-muted">Ghế đã chọn</small>
          <div id="summary-seats" class="mt-1">
            <span class="text-muted" style="font-size:0.85rem">Chưa chọn ghế</span>
          </div>
        </div>

        <hr style="border-color:#2a2a2a">

        <div class="d-flex justify-content-between mb-1">
          <span class="text-muted">Số vé:</span>
          <span id="summary-count" class="fw-bold">0</span>
        </div>
        <div class="d-flex justify-content-between mb-3">
          <span class="text-muted">Đơn giá:</span>
          <span id="summary-unit-price" class="fw-bold">–</span>
        </div>
        <div class="d-flex justify-content-between align-items-center mb-4">
          <span class="fw-bold">Tổng tiền:</span>
          <span class="total-price" id="summary-total">0 đ</span>
        </div>

        <button class="btn-primary-custom w-100" id="confirm-btn" disabled
                onclick="confirmBooking()">
          ✅ Xác nhận đặt vé
        </button>
        <div class="mt-2 text-center">
          <small class="text-muted">Bạn chưa đăng nhập? <a href="/login.html" style="color:#e50914">Đăng nhập</a></small>
        </div>

        <!-- AI Review -->
        <div class="mt-4 pt-3" style="border-top:1px solid #2a2a2a">
          <div class="d-flex justify-content-between align-items-center mb-2">
            <small class="fw-bold">🤖 AI Review phim</small>
            <button id="ai-review-btn" class="btn-outline-custom" style="font-size:0.8rem;padding:6px 10px" onclick="loadAiReview()">
              Xem AI review
            </button>
          </div>
          <div id="ai-review-box" class="text-muted" style="font-size:0.88rem;line-height:1.5">
            Bấm "Xem AI review" để xem nhận xét nhanh về phim này.
          </div>
        </div>

        <!-- Legend -->
        <div class="mt-4 pt-3" style="border-top:1px solid #2a2a2a">
          <small class="text-muted fw-bold d-block mb-2">Chú thích:</small>
          <div class="d-flex gap-3 flex-wrap">
            <div class="d-flex align-items-center gap-1">
              <div class="legend-dot" style="background:#555"></div>
              <small>Trống</small>
            </div>
            <div class="d-flex align-items-center gap-1">
              <div class="legend-dot" style="background:#c0392b"></div>
              <small>Đã đặt</small>
            </div>
            <div class="d-flex align-items-center gap-1">
              <div class="legend-dot" style="background:#27ae60"></div>
              <small>Đang chọn</small>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>`;
}

// ── Attach screening pill click events ───────────────────────
function attachScreeningListeners() {
  document.getElementById('screening-pills').addEventListener('click', async e => {
    const pill = e.target.closest('.screening-pill');
    if (!pill) return;
    document.querySelectorAll('.screening-pill').forEach(p => p.classList.remove('active'));
    pill.classList.add('active');
    currentScreeningId = pill.dataset.sid;
    selectedSeats = [];
    updateSummary(parseFloat(pill.dataset.price));
    const seatWrapper = document.getElementById('seat-map-wrapper');
    seatWrapper.innerHTML = '<div class="text-center py-4"><div class="spinner"></div></div>';
    try {
      const data = await api.getSeatMap(currentScreeningId);
      renderSeatMap(data);
    } catch (ex) {
      seatWrapper.innerHTML = `<div class="text-danger">Lỗi tải ghế: ${ex.message}</div>`;
    }
  });
}

// ── Load and render seat map ─────────────────────────────────
async function loadSeatMap(screeningId, screenings) {
  const wrapper = document.getElementById('seat-map-wrapper');
  try {
    const data = await api.getSeatMap(screeningId);
    renderSeatMap(data);
    // Update summary time
    const s = screenings.find(x => x._key === screeningId);
    if (s) {
      document.getElementById('summary-time').textContent = formatDateTime(s.showTime);
      document.getElementById('summary-unit-price').textContent = formatVND(s.price);
      screeningPrice = s.price;
    }
  } catch (e) {
    wrapper.innerHTML = `<div class="text-danger">Lỗi tải ghế: ${e.message}</div>`;
  }
}

function renderSeatMap(data) {
  const { screening, cinema, seats } = data;
  screeningPrice = screening.price;
  document.getElementById('summary-time').textContent = formatDateTime(screening.showTime);
  document.getElementById('summary-unit-price').textContent = formatVND(screening.price);

  const totalRows = cinema ? cinema.totalRows : 8;
  const totalCols = cinema ? cinema.totalCols : 10;

  // Group seats by row
  const byRow = {};
  seats.forEach(s => {
    if (!byRow[s.row]) byRow[s.row] = [];
    byRow[s.row].push(s);
  });

  let html = `
    <div class="seat-map-container">
      <div class="screen-label">MÀN HÌNH</div>`;

  for (let row = 0; row < totalRows; row++) {
    const rowLabel = String.fromCharCode(65 + row);
    html += `<div class="seat-row"><span class="seat-row-label">${rowLabel}</span>`;
    const rowSeats = (byRow[row] || []).sort((a, b) => a.col - b.col);
    rowSeats.forEach(s => {
      const cls = s.status === 'booked' ? 'seat-booked' : 'seat-available';
      const title = s.status === 'booked' ? 'Đã đặt' : `Ghế ${s.seatLabel} – ${formatVND(screeningPrice)}`;
      html += `<button class="seat ${cls}" data-key="${s._key}" data-label="${s.seatLabel}"
                       title="${title}" ${s.status === 'booked' ? 'disabled' : ''}>${s.col + 1}</button>`;
    });
    html += '</div>';
  }
  html += '</div>';

  const wrapper = document.getElementById('seat-map-wrapper');
  wrapper.innerHTML = html;

  // Attach click events to available seats
  wrapper.querySelectorAll('.seat.seat-available').forEach(btn => {
    btn.addEventListener('click', () => toggleSeat(btn));
  });
}

// ── Seat selection ───────────────────────────────────────────
function toggleSeat(btn) {
  const key   = btn.dataset.key;
  const label = btn.dataset.label;
  const idx   = selectedSeats.findIndex(s => s.key === key);

  if (idx >= 0) {
    selectedSeats.splice(idx, 1);
    btn.classList.remove('seat-selected');
    btn.classList.add('seat-available');
  } else {
    selectedSeats.push({ key, label });
    btn.classList.remove('seat-available');
    btn.classList.add('seat-selected');
  }
  updateSummary(screeningPrice);
}

function updateSummary(price) {
  const count = selectedSeats.length;
  const total = count * price;
  document.getElementById('summary-count').textContent = count;
  document.getElementById('summary-total').textContent = formatVND(total);

  const seatsEl = document.getElementById('summary-seats');
  seatsEl.innerHTML = count === 0
    ? '<span class="text-muted" style="font-size:0.85rem">Chưa chọn ghế</span>'
    : selectedSeats.map(s => `<span class="summary-seat-tag">${s.label}</span>`).join('');

  document.getElementById('confirm-btn').disabled = count === 0;
}

// ── Confirm booking ──────────────────────────────────────────
async function confirmBooking() {
  const user = getCurrentUser();
  if (!user) {
    window.location.href = '/login.html';
    return;
  }
  if (selectedSeats.length === 0) return;

  const btn = document.getElementById('confirm-btn');
  btn.disabled = true;
  btn.textContent = 'Đang xử lý...';

  try {
    const result = await api.createBooking({
      userId:      user._key,
      screeningId: currentScreeningId,
      seatKeys:    selectedSeats.map(s => s.key)
    });

    // Update stored user (rank may have changed)
    if (result.user) setCurrentUser(result.user);

    // Show success modal
    document.getElementById('modal-message').textContent =
      `Đã đặt ${selectedSeats.length} ghế: ${selectedSeats.map(s => s.label).join(', ')}`;

    if (result.user && result.user.memberRank !== user.memberRank) {
      const rankEl = document.getElementById('modal-new-rank');
      rankEl.className = `rank-badge ${rankClass(result.user.memberRank)}`;
      rankEl.textContent = rankLabel(result.user.memberRank);
      document.getElementById('modal-rank-update').style.display = 'block';
    }

    const modal = document.getElementById('success-modal');
    modal.style.display = 'flex';
    renderNavUser();

  } catch (e) {
    btn.disabled = false;
    btn.textContent = '✅ Xác nhận đặt vé';
    alert('Đặt vé thất bại: ' + e.message);
  }
}

async function loadAiReview() {
  if (!currentMovie || !currentMovie._key) return;

  const btn = document.getElementById('ai-review-btn');
  const box = document.getElementById('ai-review-box');
  if (!btn || !box) return;

  btn.disabled = true;
  btn.textContent = 'Đang tạo...';
  box.innerHTML = '<div class="spinner"></div>';

  try {
    const data = await api.getAiMovieReview(currentMovie._key);
    const sourceLabel = data.source === 'gemini' ? 'Gemini' : 'Fallback';
    box.innerHTML = `
      <div style="color:#111827;white-space:pre-wrap">${escapeHtml(data.review || 'Không có nội dung review')}</div>
      <div class="text-muted mt-2" style="font-size:0.78rem">Nguồn: ${sourceLabel} (${data.model || 'N/A'})</div>
    `;
  } catch (e) {
    box.innerHTML = `<span class="text-danger">Không thể tải AI review: ${escapeHtml(e.message)}</span>`;
  } finally {
    btn.disabled = false;
    btn.textContent = 'Xem AI review';
  }
}

function escapeHtml(input) {
  return String(input)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

init();
