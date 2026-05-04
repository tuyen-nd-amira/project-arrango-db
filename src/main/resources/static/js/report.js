/* report.js – Movie revenue report page */

document.getElementById('navbar-placeholder').innerHTML = getNavbarHTML('report');
document.getElementById('footer-placeholder').innerHTML = getFooterHTML();
renderNavUser();

let allReports = [];
let sortField = 'totalRevenue';
let sortAsc = false;
let maxRevenue = 1;

async function loadReport() {
  document.getElementById('report-table-wrap').innerHTML =
    '<div class="text-center py-5"><div class="spinner"></div></div>';

  try {
    const [reports, overview] = await Promise.all([
      api.getMovieReports(),
      api.getOverview()
    ]);

    // Fill overview stats
    document.getElementById('ov-revenue').textContent  = formatVND(overview.totalRevenue || 0);
    document.getElementById('ov-tickets').textContent  = (overview.totalTickets || 0).toLocaleString('vi-VN');
    document.getElementById('ov-bookings').textContent = (overview.totalBookings || 0).toLocaleString('vi-VN');
    document.getElementById('ov-users').textContent    = (overview.totalUsers || 0).toLocaleString('vi-VN');

    allReports = reports;
    maxRevenue = Math.max(1, ...reports.map(r => r.totalRevenue || 0));

    renderTable(allReports);
  } catch (e) {
    document.getElementById('report-table-wrap').innerHTML =
      `<div class="text-center text-danger py-4">Lỗi tải báo cáo: ${e.message}</div>`;
  }
}

function renderTable(reports) {
  const filtered = applySearch(reports);
  document.getElementById('movie-count').textContent = `${filtered.length} phim`;

  if (filtered.length === 0) {
    document.getElementById('report-table-wrap').innerHTML =
      '<div class="text-center text-muted py-4">Không có dữ liệu doanh thu.</div>';
    return;
  }

  const sorted = [...filtered].sort((a, b) => {
    const va = a[sortField] ?? 0;
    const vb = b[sortField] ?? 0;
    if (typeof va === 'string') return sortAsc ? va.localeCompare(vb, 'vi') : vb.localeCompare(va, 'vi');
    return sortAsc ? va - vb : vb - va;
  });

  const rows = sorted.map((r, idx) => {
    const rankNum = idx + 1;
    const medal = rankNum === 1 ? '🥇' : rankNum === 2 ? '🥈' : rankNum === 3 ? '🥉' : `${rankNum}.`;
    const pct = maxRevenue > 0 ? Math.round((r.totalRevenue || 0) / maxRevenue * 100) : 0;
    const hasData = (r.totalRevenue || 0) > 0;

    return `
      <tr>
        <td class="fw-bold" style="min-width:36px">${medal}</td>
        <td>
          <div class="fw-semibold" style="max-width:260px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis"
               title="${escHtml(r.movieTitle)}">${escHtml(r.movieTitle || '–')}</div>
          <div class="text-muted" style="font-size:0.78rem">${escHtml(r.genre || '')}</div>
        </td>
        <td>
          <div class="fw-bold" style="color:${hasData ? 'var(--primary)' : '#aaa'}">${formatVND(r.totalRevenue || 0)}</div>
          <div class="revenue-bar-wrap mt-1">
            <div class="revenue-bar" style="width:${pct}%"></div>
          </div>
        </td>
        <td class="text-center fw-semibold">${(r.totalTickets || 0).toLocaleString('vi-VN')}</td>
        <td class="text-center">${(r.totalBookings || 0).toLocaleString('vi-VN')}</td>
        <td class="text-center">
          <a href="/booking.html?movieId=${r.movieId}" class="btn-primary-custom"
             style="font-size:0.78rem;padding:6px 14px;display:inline-block">🎟 Đặt vé</a>
        </td>
      </tr>`;
  }).join('');

  document.getElementById('report-table-wrap').innerHTML = `
    <div class="table-responsive">
      <table class="table revenue-table mb-0">
        <thead>
          <tr>
            <th style="width:44px">#</th>
            <th>
              Tên phim
              ${sortHeader('movieTitle')}
            </th>
            <th>
              Doanh thu
              ${sortHeader('totalRevenue')}
            </th>
            <th class="text-center">
              Vé bán
              ${sortHeader('totalTickets')}
            </th>
            <th class="text-center">
              Lượt đặt
              ${sortHeader('totalBookings')}
            </th>
            <th class="text-center">Hành động</th>
          </tr>
        </thead>
        <tbody>${rows}</tbody>
      </table>
    </div>`;
}

function sortHeader(field) {
  const isActive = sortField === field;
  const icon = isActive ? (sortAsc ? '▲' : '▼') : '⇅';
  return `<button class="sort-btn ${isActive ? 'active' : ''}"
                  onclick="changeSort('${field}')">${icon}</button>`;
}

function changeSort(field) {
  if (sortField === field) {
    sortAsc = !sortAsc;
  } else {
    sortField = field;
    sortAsc = field === 'movieTitle';
  }
  renderTable(allReports);
}

function applySearch(reports) {
  const q = document.getElementById('search-report')?.value?.toLowerCase().trim() || '';
  if (!q) return reports;
  return reports.filter(r =>
    (r.movieTitle || '').toLowerCase().includes(q) ||
    (r.genre || '').toLowerCase().includes(q)
  );
}

function escHtml(str) {
  return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

// Search với debounce
let searchTimeout = null;
document.getElementById('search-report')?.addEventListener('input', () => {
  clearTimeout(searchTimeout);
  searchTimeout = setTimeout(() => renderTable(allReports), 300);
});

loadReport();
