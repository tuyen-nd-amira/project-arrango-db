document.getElementById('navbar-placeholder').innerHTML = getNavbarHTML('movies');
document.getElementById('footer-placeholder').innerHTML = getFooterHTML();
renderNavUser();

if (!requireAuth()) {
  throw new Error('Unauthorized');
}

const params = new URLSearchParams(window.location.search);
const bookingId = params.get('bookingId');
const paymentBox = document.getElementById('payment-box');

let countdownTimer = null;
let holdExpiresAt = null;

function secondsLeft() {
  if (!holdExpiresAt) return 0;
  const diffMs = new Date(holdExpiresAt).getTime() - Date.now();
  return Math.max(0, Math.floor(diffMs / 1000));
}

function fmtCountdown(totalSeconds) {
  const min = Math.floor(totalSeconds / 60);
  const sec = totalSeconds % 60;
  return `${String(min).padStart(2, '0')}:${String(sec).padStart(2, '0')}`;
}

function showError(message) {
  paymentBox.innerHTML = `
    <h4 class="fw-bold mb-3 text-danger">Không thể thanh toán</h4>
    <p class="text-muted mb-4">${escapeHtml(message)}</p>
    <a class="btn-primary-custom" href="/movies.html">Quay về danh sách phim</a>
  `;
}

function escapeHtml(input) {
  return String(input)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function renderHolding(booking) {
  holdExpiresAt = booking.holdExpiresAt || null;

  paymentBox.innerHTML = `
    <h4 class="fw-bold mb-4">💳 Thanh toán đặt vé</h4>

    <div class="mb-3">
      <small class="text-muted">Mã booking</small>
      <div class="fw-bold">${escapeHtml(booking.bookingCode || booking._key || '')}</div>
    </div>

    <div class="mb-3">
      <small class="text-muted">Phim</small>
      <div class="fw-bold">${escapeHtml(booking.movieTitle || '')}</div>
    </div>

    <div class="mb-3">
      <small class="text-muted">Suất chiếu</small>
      <div class="fw-bold">${escapeHtml(formatDateTime(booking.showTime || booking.createdAt || ''))}</div>
    </div>

    <div class="mb-3">
      <small class="text-muted">Ghế</small>
      <div class="fw-bold">${(booking.seatLabels || []).map(escapeHtml).join(', ')}</div>
    </div>

    <div class="d-flex justify-content-between align-items-center p-3 mb-3" style="border:1px solid #e50914;border-radius:10px;background:#fff5f5">
      <span class="fw-bold" style="color:#222">Tổng tiền</span>
      <span class="fw-bold" style="color:#e50914;font-size:1.2rem">${formatVND(booking.totalAmount || 0)}</span>
    </div>

    <div class="p-3 mb-4" style="border:1px solid #9a3412;border-radius:10px;background:#2b140f">
      <div class="small text-uppercase fw-bold mb-1" style="color:#fb923c">Thời gian giữ vé</div>
      <div id="countdown-text" class="fw-bold" style="font-size:1.6rem;color:#fdba74">--:--</div>
      <small class="text-muted">Sau khi hết giờ, booking sẽ tự huỷ và ghế được mở lại.</small>
    </div>

    <button id="pay-btn" class="btn-primary-custom w-100">Hoàn thành thanh toán</button>
    <button id="cancel-hold-btn" class="btn-outline-custom w-100 mt-2" style="border-color:#e50914;color:#e50914">
      Hủy giữ vé
    </button>
  `;

  document.getElementById('pay-btn').addEventListener('click', completePayment);
  document.getElementById('cancel-hold-btn').addEventListener('click', cancelHolding);
  startCountdown();
}

async function cancelHolding() {
  const cancelBtn = document.getElementById('cancel-hold-btn');
  if (!cancelBtn) return;
  if (!confirm('Bạn có chắc muốn hủy giữ vé? Ghế sẽ được mở lại cho người khác.')) return;

  cancelBtn.disabled = true;
  cancelBtn.textContent = 'Đang hủy...';
  const payBtn = document.getElementById('pay-btn');
  if (payBtn) payBtn.disabled = true;

  try {
    await api.cancelHolding(bookingId);
    stopCountdown();
    paymentBox.innerHTML = `
      <div class="text-center py-3">
        <div style="font-size:3rem">❌</div>
        <h4 class="fw-bold mt-2 text-danger">Đã hủy giữ vé</h4>
        <p class="text-muted mt-2 mb-4">Ghế của bạn đã được mở lại. Vui lòng đặt lại nếu muốn.</p>
        <a class="btn-primary-custom" href="/movies.html">Đặt vé lại</a>
      </div>`;
  } catch (error) {
    cancelBtn.disabled = false;
    cancelBtn.textContent = '❌ Hủy giữ vé';
    if (payBtn) payBtn.disabled = false;
    alert((error && error.message) ? error.message : 'Hủy thất bại');
  }
}

function renderConfirmed(booking) {
  stopCountdown();
  paymentBox.innerHTML = `
    <div class="text-center py-2">
      <div style="font-size:3rem">🎉</div>
      <h4 class="fw-bold mt-2" style="color:#27ae60">Thanh toán thành công</h4>
      <p class="text-muted mb-3">Vé của bạn đã được xác nhận.</p>
      <div class="mb-4">
        <div><small class="text-muted">Mã booking</small></div>
        <div class="fw-bold">${escapeHtml(booking.bookingCode || booking._key || '')}</div>
      </div>
      <a class="btn-primary-custom" href="/movies.html">Đặt vé tiếp</a>
    </div>
  `;
}

function renderCancelled() {
  stopCountdown();
  paymentBox.innerHTML = `
    <h4 class="fw-bold mb-3 text-danger">Giữ vé đã hết hạn</h4>
    <p class="text-muted mb-4">Booking đã bị huỷ do quá 5 phút chưa thanh toán. Vui lòng đặt lại.</p>
    <a class="btn-primary-custom" href="/movies.html">Đặt vé lại</a>
  `;
}

function startCountdown() {
  stopCountdown();

  const tick = () => {
    const remain = secondsLeft();
    const countdownEl = document.getElementById('countdown-text');
    const payBtn = document.getElementById('pay-btn');

    if (countdownEl) {
      countdownEl.textContent = fmtCountdown(remain);
    }

    if (remain <= 0) {
      if (payBtn) {
        payBtn.disabled = true;
        payBtn.textContent = '⛔ Đã hết thời gian giữ vé';
      }
      stopCountdown();
    }
  };

  tick();
  countdownTimer = setInterval(tick, 1000);
}

function stopCountdown() {
  if (countdownTimer) {
    clearInterval(countdownTimer);
    countdownTimer = null;
  }
}

async function completePayment() {
  const payBtn = document.getElementById('pay-btn');
  if (!payBtn) return;

  if (secondsLeft() <= 0) {
    payBtn.disabled = true;
    payBtn.textContent = '⛔ Đã hết thời gian giữ vé';
    return;
  }

  payBtn.disabled = true;
  payBtn.textContent = 'Đang xử lý thanh toán...';

  try {
    const result = await api.completePayment(bookingId);
    if (result.user) {
      setCurrentUser(result.user);
      renderNavUser();
    }
    renderConfirmed(result.booking || {});
  } catch (error) {
    const msg = (error && error.message) ? error.message : 'Thanh toán thất bại';
    if (msg.toLowerCase().includes('het han')) {
      renderCancelled();
      return;
    }
    payBtn.disabled = false;
    payBtn.textContent = '✅ Hoàn thành thanh toán';
    alert(msg);
  }
}

async function initPaymentPage() {
  if (!bookingId) {
    showError('Thiếu bookingId trên URL');
    return;
  }

  try {
    const booking = await api.getBookingById(bookingId);

    if (booking.status === 'confirmed') {
      renderConfirmed(booking);
      return;
    }
    if (booking.status === 'cancelled') {
      renderCancelled();
      return;
    }

    renderHolding(booking);
  } catch (error) {
    showError(error.message || 'Không tải được dữ liệu booking');
  }
}

initPaymentPage();
