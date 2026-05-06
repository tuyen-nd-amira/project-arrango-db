document.addEventListener('DOMContentLoaded', () => {
  document.getElementById('navbar-placeholder').innerHTML = getNavbarHTML('history');
  document.getElementById('footer-placeholder').innerHTML = getFooterHTML();
  renderNavUser();

  if (!requireAuth()) return;
  
  loadHistory();
});

async function loadHistory() {
  const container = document.getElementById('history-container');
  try {
    const user = getCurrentUser();
    const userId = user._key || user.key;
    const bookings = await api.getUserBookings(userId);

    if (!bookings || bookings.length === 0) {
      container.innerHTML = `<div class="text-center py-5 text-muted">Bạn chưa có giao dịch nào.</div>`;
      return;
    }

    let html = `<div class="table-responsive"><table class="table table-dark table-hover align-middle">
      <thead>
        <tr>
          <th>Mã vé</th>
          <th>Phim</th>
          <th>Rạp & Suất chiếu</th>
          <th>Ghế</th>
          <th>Tổng tiền</th>
          <th>Ngày đặt</th>
          <th>Trạng thái</th>
          <th>Hành động</th>
        </tr>
      </thead>
      <tbody>`;

    for (const b of bookings) {
      let statusClass = 'status-holding';
      let statusText = 'Đang giữ chỗ';
      if (b.status === 'confirmed') { statusClass = 'status-confirmed'; statusText = 'Thành công'; }
      if (b.status === 'cancelled') { statusClass = 'status-cancelled'; statusText = 'Đã hủy'; }

      let actionHtml = '';
      if (b.status === 'holding') {
        actionHtml = `<a href="/payment.html?id=${b._key || b.key}" class="btn-primary-custom btn-sm text-decoration-none">Thanh toán</a>`;
      }

      html += `
        <tr>
          <td class="fw-bold">${b.bookingCode || '-'}</td>
          <td>${b.movieTitle || 'Không rõ'}</td>
          <td>${b.cinemaName || 'Không rõ'}<br><small class="text-muted">${formatDateTime(b.showTime)}</small></td>
          <td>${(b.seatLabels || []).join(', ')}</td>
          <td>${formatVND(b.totalAmount)}</td>
          <td>${formatDateTime(b.createdAt)}</td>
          <td class="${statusClass}">${statusText}</td>
          <td>${actionHtml}</td>
        </tr>
      `;
    }

    html += `</tbody></table></div>`;
    container.innerHTML = html;

  } catch (error) {
    container.innerHTML = `<div class="alert alert-danger">Lỗi tải lịch sử: ${error.message}</div>`;
  }
}
