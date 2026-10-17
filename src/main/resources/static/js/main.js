document.addEventListener("DOMContentLoaded", function () {
  // Khởi tạo tất cả Bootstrap Tooltips
  var tooltipTriggerList = [].slice.call(
    document.querySelectorAll('[data-bs-toggle="tooltip"]'),
  );
  if (window.bootstrap?.Tooltip) {
    tooltipTriggerList.map(function (tooltipTriggerEl) {
      return new bootstrap.Tooltip(tooltipTriggerEl);
    });
  }

  // Đảm bảo nút đóng vẫn hoạt động cả khi Bootstrap bundle chưa tải được.
  document.querySelectorAll(".alert-dismissible [data-bs-dismiss='alert']").forEach(function (closeButton) {
    closeButton.addEventListener("click", function (event) {
      event.preventDefault();
      event.stopPropagation();
      const alert = closeButton.closest(".alert");
      if (!alert) return;
      alert.classList.remove("show");
      window.setTimeout(function () { alert.remove(); }, 220);
    });
  });

  // Khởi tạo tự động đóng Flash Messages sau 5 giây
  const alerts = document.querySelectorAll(".alert-dismissible");
  alerts.forEach(function (alert) {
    setTimeout(function () {
      if (window.bootstrap?.Alert) {
        bootstrap.Alert.getOrCreateInstance(alert).close();
      } else {
        alert.classList.remove("show");
        window.setTimeout(function () { alert.remove(); }, 220);
      }
    }, 5000);
  });
});
