document.addEventListener("DOMContentLoaded", function () {
  // Native browser validation blocks invalid submits; reflect its state in Bootstrap styling.
  document.querySelectorAll("form.needs-validation").forEach(function (form) {
    form.addEventListener("invalid", function (event) {
      event.target.classList.add("is-invalid");
      event.target.setAttribute("aria-invalid", "true");
    }, true);

    form.addEventListener("input", function (event) {
      const field = event.target;
      if (field.willValidate && field.checkValidity()) {
        field.classList.remove("is-invalid");
        field.removeAttribute("aria-invalid");
      }
    });
    form.addEventListener("change", function (event) {
      const field = event.target;
      if (field.willValidate && field.checkValidity()) {
        field.classList.remove("is-invalid");
        field.removeAttribute("aria-invalid");
      }
    });
  });

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

  document.querySelectorAll("[data-carousel]").forEach(function (carousel) {
    const slides = [...carousel.querySelectorAll("[data-slide]")];
    const dots = [...carousel.querySelectorAll("[data-carousel-to]")];
    const count = carousel.querySelector(".carousel-count");
    const toggle = carousel.querySelector("[data-carousel-toggle]");
    if (slides.length < 2) return;
    let active = 0;
    let timer = 0;
    let paused = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    let carouselVisible = !("IntersectionObserver" in window);
    let touchStartX = null;

    function show(index) {
      active = (index + slides.length) % slides.length;
      slides.forEach((slide, i) => {
        slide.hidden = i !== active;
        slide.classList.toggle("is-active", i === active);
        slide.setAttribute("aria-label", `${i + 1} trên ${slides.length}`);
      });
      dots.forEach((dot, i) => {
        if (i === active) dot.setAttribute("aria-current", "true");
        else dot.removeAttribute("aria-current");
      });
      if (count) count.textContent = `${active + 1} / ${slides.length}`;
    }
    function stop() { window.clearInterval(timer); timer = 0; }
    function schedule() {
      stop();
      if (!paused && carouselVisible && !document.hidden && !carousel.matches(":focus-within")) timer = window.setInterval(() => show(active + 1), 5200);
      if (toggle) {
        toggle.textContent = paused ? "Phát" : "Tạm dừng";
        toggle.setAttribute("aria-label", paused ? "Phát tự chuyển slide" : "Tạm dừng tự chuyển slide");
      }
    }
    carousel.querySelector("[data-carousel-prev]")?.addEventListener("click", () => { show(active - 1); schedule(); });
    carousel.querySelector("[data-carousel-next]")?.addEventListener("click", () => { show(active + 1); schedule(); });
    dots.forEach((dot) => dot.addEventListener("click", () => { show(Number(dot.dataset.carouselTo)); schedule(); }));
    toggle?.addEventListener("click", () => { paused = !paused; schedule(); });
    carousel.addEventListener("focusin", stop);
    carousel.addEventListener("focusout", (event) => { if (!carousel.contains(event.relatedTarget)) schedule(); });
    carousel.addEventListener("keydown", (event) => {
      if (event.key === "ArrowLeft") { event.preventDefault(); show(active - 1); schedule(); }
      if (event.key === "ArrowRight") { event.preventDefault(); show(active + 1); schedule(); }
    });
    carousel.addEventListener("touchstart", (event) => { touchStartX = event.changedTouches[0]?.clientX ?? null; }, { passive: true });
    carousel.addEventListener("touchend", (event) => {
      const endX = event.changedTouches[0]?.clientX;
      if (touchStartX === null || endX === undefined || Math.abs(endX - touchStartX) < 45) return;
      show(active + (endX < touchStartX ? 1 : -1)); schedule(); touchStartX = null;
    }, { passive: true });
    document.addEventListener("visibilitychange", schedule);
    show(0);
    schedule();
    if ("IntersectionObserver" in window) {
      const carouselObserver = new IntersectionObserver((entries) => {
        carouselVisible = entries[0]?.isIntersecting === true;
        schedule();
      }, { threshold: 0.2 });
      carouselObserver.observe(carousel);
    }
  });

  const notificationPanel = document.querySelector("[data-notification-panel]");
  if (notificationPanel) {
    const toggle = document.querySelector("[data-notification-toggle]");
    const list = notificationPanel.querySelector("[data-notification-list]");
    const unread = document.querySelector("[data-unread-count]");
    const realtimeState = notificationPanel.querySelector("[data-realtime-state]");
    const retry = notificationPanel.querySelector("[data-notification-retry]");
    const role = notificationPanel.dataset.activeRole || "";
    let reconnectTimer = 0;
    let reconnectDelay = 1500;
    let socket = null;
    let frameBuffer = "";
    let manualClose = false;
    let loadFailed = false;
    const teamIds = [...new Set([...document.querySelectorAll("[data-realtime-team-id]")]
      .map((node) => node.dataset.realtimeTeamId)
      .filter((id) => /^[0-9a-f-]{36}$/i.test(id)))];

    function csrfHeaders() {
      const token = document.querySelector('meta[name="_csrf"]')?.content;
      const header = document.querySelector('meta[name="_csrf_header"]')?.content;
      return token && header ? { [header]: token } : {};
    }
    function setUnread(value) {
      const amount = Math.max(0, Number(value) || 0);
      unread.hidden = amount === 0;
      unread.textContent = amount > 99 ? "99+" : String(amount);
      toggle.setAttribute("aria-label", amount ? `Thông báo, ${amount} chưa đọc` : "Thông báo");
    }
    function showState(message, failed) {
      list.replaceChildren(Object.assign(document.createElement("p"), { className: "notification-state", textContent: message }));
      if (retry) retry.hidden = !failed;
    }
    function formatDate(value) {
      if (!value) return "";
      const date = new Date(value);
      return Number.isNaN(date.getTime()) ? "" : new Intl.DateTimeFormat("vi-VN", { dateStyle: "medium", timeStyle: "short" }).format(date);
    }
    async function loadInbox() {
      if (realtimeState) realtimeState.textContent = "Đang tải thông báo…";
      try {
        const response = await fetch("/api/notifications", { headers: { Accept: "application/json" }, credentials: "same-origin" });
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        const inbox = await response.json();
        setUnread(inbox.unreadCount);
        const fragment = document.createDocumentFragment();
        (inbox.items || []).forEach((item) => {
          const row = document.createElement("article");
          row.className = `notification-item${item.read ? "" : " is-unread"}`;
          const marker = document.createElement("span"); marker.setAttribute("aria-hidden", "true");
          const copy = document.createElement("div");
          const title = document.createElement("h3"); title.textContent = item.title || "Thông báo";
          const message = document.createElement("p"); message.textContent = item.message || "";
          copy.append(title, message);
          if (item.createdAt) { const time = document.createElement("time"); time.dateTime = item.createdAt; time.textContent = formatDate(item.createdAt); copy.append(time); }
          if (!item.read && item.id) {
            const button = document.createElement("button"); button.type = "button"; button.textContent = "Đánh dấu đã đọc";
            button.addEventListener("click", async () => {
              button.disabled = true;
              try {
                const result = await fetch(`/api/notifications/${encodeURIComponent(item.id)}/read`, { method: "POST", credentials: "same-origin", headers: { Accept: "application/json", ...csrfHeaders() } });
                if (!result.ok) throw new Error(`HTTP ${result.status}`);
                await loadInbox();
              } catch (_) { button.disabled = false; if (realtimeState) realtimeState.textContent = "Không thể cập nhật trạng thái đã đọc."; }
            });
            copy.append(button);
          }
          row.append(marker, copy); fragment.append(row);
        });
        if (inbox.items?.length) list.replaceChildren(fragment);
        else showState("Chưa có thông báo nào.", false);
        loadFailed = false;
        if (retry) retry.hidden = true;
        if (realtimeState && !socket?.OPEN) realtimeState.textContent = "Cập nhật khi mở hộp thư";
        return true;
      } catch (_) {
        loadFailed = true;
        showState("Không tải được thông báo. Kiểm tra kết nối rồi thử lại.", true);
        if (realtimeState) realtimeState.textContent = "Không kết nối được với hộp thư.";
        return false;
      }
    }
    async function connectRealtime() {
      if (manualClose || document.hidden || socket?.readyState === WebSocket.OPEN || socket?.readyState === WebSocket.CONNECTING) return;
      if (role === "ROLE_RESCUER" && teamIds.length === 0) {
        if (realtimeState) realtimeState.textContent = "Thông báo đội được làm mới khi mở hộp thư.";
        return;
      }
      try {
        const scheme = location.protocol === "https:" ? "wss:" : "ws:";
        socket = new WebSocket(`${scheme}//${location.host}/ws`);
        socket.onopen = () => {
          reconnectDelay = 1500;
          socket.send("CONNECT\naccept-version:1.2\nheart-beat:10000,10000\n\u0000");
          if (realtimeState) realtimeState.textContent = "Đang kết nối thông báo trực tiếp…";
        };
        socket.onmessage = (event) => {
          frameBuffer += String(event.data || "");
          const frames = frameBuffer.split("\u0000"); frameBuffer = frames.pop() || "";
          frames.forEach((frame) => {
            const separator = frame.indexOf("\n\n");
            if (separator < 0) return;
            if (frame.startsWith("CONNECTED")) {
              const destinations = role === "ROLE_COORDINATOR"
                ? ["/user/queue/coordinator/notifications"]
                : role === "ROLE_RESCUER"
                  ? teamIds.map((id) => `/user/queue/teams/${id}/notifications`)
                  : ["/user/queue/notifications"];
              destinations.forEach((destination, index) => socket?.send(`SUBSCRIBE\nid=drss-notifications-${index}\ndestination=${destination}\nack=auto\n\u0000`));
              if (realtimeState) realtimeState.textContent = "Đang nhận thông báo trực tiếp";
            }
            if (frame.startsWith("MESSAGE")) {
              try { JSON.parse(frame.slice(separator + 2)); loadInbox(); } catch (_) { /* Ignore malformed frame without rendering it. */ }
            }
          });
        };
        socket.onerror = () => socket?.close();
        socket.onclose = () => {
          if (manualClose || document.hidden) return;
          if (realtimeState) realtimeState.textContent = "Mất kết nối, đang thử kết nối lại…";
          window.clearTimeout(reconnectTimer);
          reconnectTimer = window.setTimeout(connectRealtime, reconnectDelay);
          reconnectDelay = Math.min(30000, reconnectDelay * 2);
        };
      } catch (_) {
        if (realtimeState) realtimeState.textContent = "Không thể mở kết nối trực tiếp.";
      }
    }
    toggle?.addEventListener("click", () => {
      const open = toggle.getAttribute("aria-expanded") !== "true";
      toggle.setAttribute("aria-expanded", String(open)); notificationPanel.hidden = !open;
      if (open) loadInbox();
    });
    document.addEventListener("click", (event) => {
      if (!notificationPanel.contains(event.target) && !toggle.contains(event.target)) { notificationPanel.hidden = true; toggle.setAttribute("aria-expanded", "false"); }
    });
    document.addEventListener("keydown", (event) => {
      if (event.key === "Escape" && !notificationPanel.hidden) { notificationPanel.hidden = true; toggle.setAttribute("aria-expanded", "false"); toggle.focus(); }
    });
    notificationPanel.querySelector("[data-notification-refresh]")?.addEventListener("click", loadInbox);
    retry?.addEventListener("click", loadInbox);
    loadInbox().then(connectRealtime);
    document.addEventListener("visibilitychange", () => {
      if (!document.hidden) { manualClose = false; loadInbox(); connectRealtime(); }
      else { manualClose = true; window.clearTimeout(reconnectTimer); socket?.close(); }
    });
    window.addEventListener("beforeunload", () => { manualClose = true; socket?.close(); });
  }

  // Disable only after native validation accepts the submission.
  document.querySelectorAll("form[data-disable-on-submit]").forEach((form) => {
    form.addEventListener("submit", () => {
      const submitter = form.querySelector("button[type='submit']:not([formnovalidate]),input[type='submit']");
      if (!submitter) return;
      submitter.dataset.originalText = submitter.textContent;
      submitter.disabled = true;
      submitter.setAttribute("aria-busy", "true");
      submitter.textContent = "Đang gửi…";
    });
  });

  document.querySelectorAll("img[data-avatar-fallback]").forEach((image) => {
    image.addEventListener("error", () => {
      const fallback = image.parentElement?.querySelector("[data-avatar-initial]");
      image.hidden = true;
      if (fallback) { fallback.hidden = false; fallback.classList.remove("d-none"); }
    }, { once: true });
  });

  if ("IntersectionObserver" in window && !window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
    const revealTargets = document.querySelectorAll(
      ".section-heading-row,.carousel-visuals,.carousel-controls,.process-intro,.process-list li,.roles-title,.role-lines article,.home-cta > div,.footer-brand,.footer-links > div,.footer-bottom"
    );
    const revealObserver = new IntersectionObserver((entries, observer) => {
      entries.forEach((entry) => {
        if (!entry.isIntersecting) return;
        entry.target.classList.add("reveal-target", "is-revealed");
        observer.unobserve(entry.target);
      });
    }, { rootMargin: "0px 0px -12% 0px", threshold: 0.08 });
    revealTargets.forEach((target) => revealObserver.observe(target));
  }
});
