(() => {
  "use strict";

  const pending = new WeakSet();

  async function openNotification(link, notificationId) {
    if (pending.has(link)) return;
    pending.add(link);
    try {
      const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
      const csrfHeader = document.querySelector(
        'meta[name="_csrf_header"]',
      )?.content;
      if (!csrfToken || !csrfHeader) {
        window.DrssRetry.show(
          "Không thể xác nhận phiên đăng nhập. Tải lại trang rồi thử lại.",
          () => window.location.reload(),
        );
        return;
      }

      await window.DrssRetry.run({
        retry: () => openNotification(link, notificationId),
        message: "Không thể đánh dấu đã đọc. Thử lại để mở thông báo.",
        request: async (signal) => {
          const response = await fetch(
            `/api/notifications/${encodeURIComponent(notificationId)}/read`,
            {
              method: "POST",
              credentials: "same-origin",
              headers: { Accept: "application/json", [csrfHeader]: csrfToken },
              signal,
            },
          );
          if (
            !response.ok ||
            !response.headers.get("content-type")?.includes("application/json")
          ) {
            throw new Error(`Unexpected response: ${response.status}`);
          }
          const updated = await response.json();
          if (updated.id !== notificationId || updated.read !== true) {
            throw new Error("Unexpected notification acknowledgement");
          }
          return response;
        },
      });

      window.location.assign(link.href);
    } catch (_) {
      // The shared retry overlay owns the retry action. Stay on this page.
    } finally {
      pending.delete(link);
    }
  }

  document.addEventListener(
    "click",
    (event) => {
      if (
        event.defaultPrevented ||
        event.button !== 0 ||
        event.metaKey ||
        event.ctrlKey ||
        event.shiftKey ||
        event.altKey ||
        !(event.target instanceof Element)
      )
        return;

      const link = event.target.closest(".notification-content-link[href]");
      if (!link || !link.closest("[data-notification-panel]")) return;

      const row = link.closest("[data-notification-id]");
      if (!row?.classList.contains("is-unread")) return;
      const notificationId = row.dataset.notificationId;
      if (
        !/^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i.test(
          notificationId || "",
        )
      )
        return;

      event.preventDefault();
      event.stopPropagation();
      openNotification(link, notificationId);
    },
    true,
  );
})();
