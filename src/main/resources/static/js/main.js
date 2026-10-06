document.addEventListener("DOMContentLoaded", function () {
  // Khởi tạo tất cả Bootstrap Tooltips
  var tooltipTriggerList = [].slice.call(
    document.querySelectorAll('[data-bs-toggle="tooltip"]'),
  );
  var tooltipList = tooltipTriggerList.map(function (tooltipTriggerEl) {
    return new bootstrap.Tooltip(tooltipTriggerEl);
  });

  // Khởi tạo tự động đóng Flash Messages sau 5 giây
  const alerts = document.querySelectorAll(".alert-dismissible");
  alerts.forEach(function (alert) {
    setTimeout(function () {
      let bsAlert = new bootstrap.Alert(alert);
      bsAlert.close();
    }, 5000);
  });
});
