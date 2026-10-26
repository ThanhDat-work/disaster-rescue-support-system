document.addEventListener("DOMContentLoaded", function () {
  // Native browser validation blocks invalid submits; reflect its state in Bootstrap styling.
  document.querySelectorAll("form.needs-validation").forEach(function (form) {
    form.addEventListener(
      "invalid",
      function (event) {
        event.target.classList.add("is-invalid");
        event.target.setAttribute("aria-invalid", "true");
      },
      true,
    );

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
  document
    .querySelectorAll(".alert-dismissible [data-bs-dismiss='alert']")
    .forEach(function (closeButton) {
      closeButton.addEventListener("click", function (event) {
        event.preventDefault();
        event.stopPropagation();
        const alert = closeButton.closest(".alert");
        if (!alert) return;
        alert.classList.remove("show");
        window.setTimeout(function () {
          alert.remove();
        }, 220);
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
        window.setTimeout(function () {
          alert.remove();
        }, 220);
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
    function stop() {
      window.clearInterval(timer);
      timer = 0;
    }
    function schedule() {
      stop();
      if (
        !paused &&
        carouselVisible &&
        !document.hidden &&
        !carousel.matches(":focus-within")
      )
        timer = window.setInterval(() => show(active + 1), 5200);
      if (toggle) {
        toggle.textContent = paused ? "Phát" : "Tạm dừng";
        toggle.setAttribute(
          "aria-label",
          paused ? "Phát tự chuyển slide" : "Tạm dừng tự chuyển slide",
        );
      }
    }
    carousel
      .querySelector("[data-carousel-prev]")
      ?.addEventListener("click", () => {
        show(active - 1);
        schedule();
      });
    carousel
      .querySelector("[data-carousel-next]")
      ?.addEventListener("click", () => {
        show(active + 1);
        schedule();
      });
    dots.forEach((dot) =>
      dot.addEventListener("click", () => {
        show(Number(dot.dataset.carouselTo));
        schedule();
      }),
    );
    toggle?.addEventListener("click", () => {
      paused = !paused;
      schedule();
    });
    carousel.addEventListener("focusin", stop);
    carousel.addEventListener("focusout", (event) => {
      if (!carousel.contains(event.relatedTarget)) schedule();
    });
    carousel.addEventListener("keydown", (event) => {
      if (event.key === "ArrowLeft") {
        event.preventDefault();
        show(active - 1);
        schedule();
      }
      if (event.key === "ArrowRight") {
        event.preventDefault();
        show(active + 1);
        schedule();
      }
    });
    carousel.addEventListener(
      "touchstart",
      (event) => {
        touchStartX = event.changedTouches[0]?.clientX ?? null;
      },
      { passive: true },
    );
    carousel.addEventListener(
      "touchend",
      (event) => {
        const endX = event.changedTouches[0]?.clientX;
        if (
          touchStartX === null ||
          endX === undefined ||
          Math.abs(endX - touchStartX) < 45
        )
          return;
        show(active + (endX < touchStartX ? 1 : -1));
        schedule();
        touchStartX = null;
      },
      { passive: true },
    );
    document.addEventListener("visibilitychange", schedule);
    show(0);
    schedule();
    if ("IntersectionObserver" in window) {
      const carouselObserver = new IntersectionObserver(
        (entries) => {
          carouselVisible = entries[0]?.isIntersecting === true;
          schedule();
        },
        { threshold: 0.2 },
      );
      carouselObserver.observe(carousel);
    }
  });

  const notificationPanel = document.querySelector("[data-notification-panel]");
  if (notificationPanel) {
    const toggle = document.querySelector("[data-notification-toggle]");
    const list = notificationPanel.querySelector("[data-notification-list]");
    const unread = document.querySelector("[data-unread-count]");
    const markAllButton = notificationPanel.querySelector(
      "[data-notification-mark-all]",
    );
    const markSelectedButton = notificationPanel.querySelector(
      "[data-notification-mark-selected]",
    );
    const selectionToggle = notificationPanel.querySelector(
      "[data-notification-selection-toggle]",
    );
    const realtimeState = notificationPanel.querySelector(
      "[data-realtime-state]",
    );
    const role = notificationPanel.dataset.activeRole || "";
    let client = null;
    let stompConnected = false;
    const subscriptions = new Map();
    let manualClose = false;
    let inboxGeneration = 0;
    let inboxController = null;
    let unreadAmount = 0;
    let selectionMode = false;
    let lastInboxSync = 0;
    let teamIds = [];
    let teamLookupGeneration = 0;
    const seenNotifications = new Set();
    const validUuid = /^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i;

    function csrfHeaders() {
      const token = document.querySelector('meta[name="_csrf"]')?.content;
      const header = document.querySelector(
        'meta[name="_csrf_header"]',
      )?.content;
      return token && header ? { [header]: token } : {};
    }
    function setUnread(value) {
      const amount = Math.max(0, Number(value) || 0);
      unreadAmount = amount;
      unread.hidden = amount === 0;
      unread.textContent = amount > 99 ? "99+" : String(amount);
      if (markAllButton) markAllButton.disabled = amount === 0;
      toggle.setAttribute(
        "aria-label",
        amount ? `Thông báo, ${amount} chưa đọc` : "Thông báo",
      );
    }
    function showState(message) {
      list.replaceChildren(
        Object.assign(document.createElement("p"), {
          className: "notification-state",
          textContent: message,
        }),
      );
    }
    function syncSelectedAction() {
      const selectedCount = list.querySelectorAll(
        "[data-notification-select]:checked",
      ).length;
      if (markSelectedButton) {
        markSelectedButton.hidden = !selectionMode;
        markSelectedButton.disabled = selectedCount === 0;
        markSelectedButton.textContent = selectedCount
          ? `Đánh dấu đã chọn (${selectedCount})`
          : "Đánh dấu đã chọn";
      }
    }
    function setSelectionMode(enabled) {
      selectionMode = enabled;
      list.classList.toggle("is-selecting", selectionMode);
      if (selectionToggle) {
        selectionToggle.textContent = selectionMode ? "Hủy chọn" : "Chọn";
        selectionToggle.setAttribute("aria-pressed", String(selectionMode));
      }
      list.querySelectorAll(".notification-item").forEach((row) => {
        row.querySelector("[data-notification-select], .notification-select-spacer")?.remove();
        if (!selectionMode) return;

        let selector = document.createElement("span");
        selector.className = "notification-select-spacer";
        if (row.classList.contains("is-unread") && row.dataset.notificationId) {
          selector = document.createElement("input");
          selector.type = "checkbox";
          selector.className = "notification-select";
          selector.dataset.notificationSelect = row.dataset.notificationId;
          selector.setAttribute("aria-label", "Chọn thông báo");
          selector.addEventListener("change", syncSelectedAction);
        }
        row.insertBefore(selector, row.lastElementChild);
      });
      if (!selectionMode) {
        list.querySelectorAll("[data-notification-select]").forEach((input) => {
          input.checked = false;
        });
      }
      syncSelectedAction();
    }
    async function submitReadAction(path, notificationIds) {
      const hasBody = Array.isArray(notificationIds);
      const response = await window.DrssRetry.run({
        retry: () => submitReadAction(path, notificationIds),
        message:
          "Không thể cập nhật trạng thái thông báo. Bấm Thử lại để tiếp tục.",
        request: async (signal) => {
          const result = await fetch(path, {
            method: "POST",
            credentials: "same-origin",
            headers: {
              Accept: "application/json",
              ...(hasBody ? { "Content-Type": "application/json" } : {}),
              ...csrfHeaders(),
            },
            ...(hasBody ? { body: JSON.stringify(notificationIds) } : {}),
            signal,
          });
          if (!result.ok) throw new Error(`HTTP ${result.status}`);
          return result;
        },
      });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      await loadInbox();
    }
    function notificationDestination(item) {
      const referenceId = String(item.referenceId || "");
      if (!validUuid.test(referenceId)) return null;
      if (item.referenceType === "RESCUE_REQUEST") {
        const targetRole = item.eventType === "REQUEST_CREATED"
          ? "ROLE_COORDINATOR"
          : "ROLE_CITIZEN";
        return {
          role: targetRole,
          url: targetRole === "ROLE_COORDINATOR"
            ? `/coordinator/requests/${referenceId}`
            : `/requests/${referenceId}`,
        };
      }
      if (item.referenceType === "RESCUE_ASSIGNMENT") {
        return {
          role: "ROLE_RESCUER",
          url: `/team/tasks/${referenceId}`,
        };
      }
      return null;
    }
    function formatDate(value) {
      if (!value) return "";
      const date = new Date(value);
      return Number.isNaN(date.getTime())
        ? ""
        : new Intl.DateTimeFormat("vi-VN", {
            dateStyle: "medium",
            timeStyle: "short",
          }).format(date);
    }
    function showToast(payload) {
      let host = document.querySelector("[data-realtime-toast-host]");
      if (!host) {
        host = document.createElement("div");
        host.className = "drss-toast-host";
        host.dataset.realtimeToastHost = "";
        host.setAttribute("aria-live", "polite");
        document.body.append(host);
      }
      const toast = document.createElement("div");
      toast.className = "drss-notification-toast";
      toast.setAttribute("role", "status");
      const title = document.createElement("strong");
      title.textContent =
        typeof payload.title === "string"
          ? payload.title.slice(0, 120)
          : "Có thông báo mới";
      const action = document.createElement("button");
      action.type = "button";
      action.textContent = "Xem thông báo";
      action.addEventListener("click", () => {
        toast.remove();
        if (notificationPanel.hidden) toggle?.click();
        else loadInbox();
      });
      toast.append(title, action);
      host.append(toast);
      while (host.childElementCount > 3) host.firstElementChild.remove();
      window.setTimeout(() => toast.remove(), 7000);
    }
    async function loadInbox(options) {
      const silent = options === true;
      const generation = ++inboxGeneration;
      inboxController?.abort();
      const controller = new AbortController();
      inboxController = controller;
      if (!silent) {
        showState("Đang tải thông báo…");
        if (realtimeState) realtimeState.textContent = "Đang tải thông báo…";
      }
      try {
        const inbox = await window.DrssRetry.getJson("/api/notifications", {
          controller,
          retry: loadInbox,
          shouldShow: () =>
            generation === inboxGeneration && !notificationPanel.hidden,
          message:
            "Hộp thư tải quá lâu hoặc kết nối bị gián đoạn. Bấm Thử lại để tải lại thông báo.",
        });
        if (generation !== inboxGeneration) return false;
        setUnread(inbox.unreadCount);
        lastInboxSync = Date.now();
        const fragment = document.createDocumentFragment();
        (inbox.items || []).forEach((item) => {
          const row = document.createElement("article");
          row.className = `notification-item${item.read ? "" : " is-unread"}`;
          row.dataset.notificationId = item.id || "";
          const marker = document.createElement("span");
          marker.setAttribute("aria-hidden", "true");
          const rowContent = [marker];
          if (selectionMode) {
            let selector = document.createElement("span");
            selector.className = "notification-select-spacer";
            if (!item.read && item.id) {
              selector = document.createElement("input");
              selector.type = "checkbox";
              selector.className = "notification-select";
              selector.dataset.notificationSelect = item.id;
              selector.setAttribute(
                "aria-label",
                `Chọn thông báo: ${item.title || "Thông báo"}`,
              );
              selector.addEventListener("change", syncSelectedAction);
            }
            rowContent.push(selector);
          }
          const copy = document.createElement("div");
          const destination = notificationDestination(item);
          const content = document.createElement(destination ? "a" : "div");
          if (destination) {
            content.href = destination.role === role
              ? destination.url
              : `/account/role-switch?targetRole=${encodeURIComponent(destination.role)}&returnTo=${encodeURIComponent(destination.url)}`;
            content.className = "notification-content-link";
          }
          const title = document.createElement("h3");
          title.textContent = item.title || "Thông báo";
          const message = document.createElement("p");
          message.textContent = item.message || "";
          content.append(title, message);
          if (item.createdAt) {
            const time = document.createElement("time");
            time.dateTime = item.createdAt;
            time.textContent = formatDate(item.createdAt);
            content.append(time);
          }
          copy.append(content);
          if (!item.read && item.id) {
            const button = document.createElement("button");
            button.type = "button";
            button.textContent = "Đánh dấu đã đọc";
            button.addEventListener("click", async () => {
              button.disabled = true;
              try {
                await submitReadAction(
                  `/api/notifications/${encodeURIComponent(item.id)}/read`,
                );
              } catch (_) {
                button.disabled = false;
                if (realtimeState)
                  realtimeState.textContent =
                    "Không thể cập nhật trạng thái đã đọc.";
              }
            });
            copy.append(button);
          }
          row.append(...rowContent, copy);
          fragment.append(row);
        });
        if (inbox.items?.length) list.replaceChildren(fragment);
        else showState("Chưa có thông báo nào.");
        syncSelectedAction();
        if (realtimeState)
          realtimeState.textContent = stompConnected
            ? "Đang nhận thông báo trực tiếp"
            : "Cập nhật khi mở hộp thư";
        return true;
      } catch (error) {
        if (generation !== inboxGeneration) return false;
        if (!silent) showState("Chưa tải được thông báo.");
        if (realtimeState && !silent)
          realtimeState.textContent = "Không kết nối được với hộp thư.";
        return false;
      } finally {
        if (generation === inboxGeneration) inboxController = null;
      }
    }
    function subscribe(destination) {
      if (!client?.connected || subscriptions.has(destination)) return;
      subscriptions.set(
        destination,
        client.subscribe(destination, (message) => {
          let payload;
          try {
            payload = JSON.parse(message.body);
          } catch (_) {
            return;
          }
          if (
            !validUuid.test(payload?.notificationId || "") ||
            seenNotifications.has(payload.notificationId)
          )
            return;
          seenNotifications.add(payload.notificationId);
          if (seenNotifications.size > 100)
            seenNotifications.delete(seenNotifications.values().next().value);
          setUnread(unreadAmount + 1);
          showToast(payload);
          loadInbox(true);
        }),
      );
    }
    function disconnectRealtime() {
      const oldClient = client;
      client = null;
      stompConnected = false;
      subscriptions.clear();
      if (oldClient) void oldClient.deactivate({ force: true });
    }
    async function refreshTeamSubscriptions() {
      if (role !== "ROLE_RESCUER" || document.hidden) return;
      const generation = ++teamLookupGeneration;
      try {
        const result = await window.DrssRetry.getJson(
          "/api/realtime/subscriptions",
        );
        if (generation !== teamLookupGeneration) return;
        const nextIds = [
          ...new Set(
            (result.teamIds || []).filter(
              (id) => typeof id === "string" && validUuid.test(id),
            ),
          ),
        ];
        teamIds = nextIds;
        for (const [destination, subscription] of subscriptions) {
          if (
            destination.startsWith("/user/queue/teams/") &&
            !teamIds.some(
              (id) => destination === `/user/queue/teams/${id}/notifications`,
            )
          ) {
            subscription.unsubscribe();
            subscriptions.delete(destination);
          }
        }
        teamIds.forEach((id) =>
          subscribe(`/user/queue/teams/${id}/notifications`),
        );
        if (!client) connectRealtime();
      } catch (_) {
        if (generation !== teamLookupGeneration) return;
        // A failed authorization lookup cannot preserve old team subscriptions.
        teamIds = [];
        disconnectRealtime();
      }
    }
    function connectRealtime() {
      if (
        manualClose ||
        document.hidden ||
        !window.StompJs?.Client ||
        client?.active
      )
        return;
      const csrf = csrfHeaders();
      if (Object.keys(csrf).length !== 1) {
        if (realtimeState)
          realtimeState.textContent = "Không thể xác thực kết nối trực tiếp.";
        return;
      }
      try {
        const scheme = location.protocol === "https:" ? "wss:" : "ws:";
        const currentClient = new window.StompJs.Client({
          brokerURL: `${scheme}//${location.host}/ws`,
          connectHeaders: csrf,
          connectionTimeout: 8000,
          heartbeatIncoming: 10000,
          heartbeatOutgoing: 10000,
          discardWebsocketOnCommFailure: true,
          reconnectDelay: 1500,
          reconnectTimeMode: window.StompJs.ReconnectionTimeMode.EXPONENTIAL,
          maxReconnectDelay: 30000,
          debug: () => {}, // Never log frames or authorization headers.
        });
        client = currentClient;
        currentClient.onConnect = () => {
          if (client !== currentClient) return;
          stompConnected = true;
          subscriptions.clear();
          subscribe("/user/queue/notifications");
          if (role === "ROLE_COORDINATOR")
            subscribe("/user/queue/coordinator/notifications");
          teamIds.forEach((id) =>
            subscribe(`/user/queue/teams/${id}/notifications`),
          );
          refreshTeamSubscriptions();
          loadInbox(true); // Reconcile notifications missed during downtime.
          if (realtimeState)
            realtimeState.textContent = "Đang nhận thông báo trực tiếp";
        };
        currentClient.onStompError = () => {
          if (client === currentClient && realtimeState)
            realtimeState.textContent = "Kết nối thông báo bị từ chối.";
        };
        currentClient.onWebSocketClose = () => {
          if (client !== currentClient) return;
          stompConnected = false;
          subscriptions.clear();
          if (!manualClose && !document.hidden && realtimeState)
            realtimeState.textContent = "Mất kết nối, đang thử kết nối lại…";
        };
        currentClient.activate();
      } catch (_) {
        disconnectRealtime();
        if (realtimeState)
          realtimeState.textContent = "Không thể mở kết nối trực tiếp.";
      }
    }
    toggle?.addEventListener("click", () => {
      const open = toggle.getAttribute("aria-expanded") !== "true";
      toggle.setAttribute("aria-expanded", String(open));
      notificationPanel.hidden = !open;
      if (open) loadInbox();
    });
    document.addEventListener("click", (event) => {
      if (
        !notificationPanel.contains(event.target) &&
        !toggle.contains(event.target)
      ) {
        notificationPanel.hidden = true;
        toggle.setAttribute("aria-expanded", "false");
      }
    });
    document.addEventListener("keydown", (event) => {
      if (event.key === "Escape" && !notificationPanel.hidden) {
        notificationPanel.hidden = true;
        toggle.setAttribute("aria-expanded", "false");
        toggle.focus();
      }
    });
    notificationPanel
      .querySelector("[data-notification-refresh]")
      ?.addEventListener("click", loadInbox);
    markAllButton?.addEventListener("click", async () => {
      markAllButton.disabled = true;
      try {
        await submitReadAction("/api/notifications/read-all");
      } catch (_) {
        markAllButton.disabled = false;
        if (realtimeState)
          realtimeState.textContent = "Không thể đánh dấu tất cả đã đọc.";
      }
    });
    selectionToggle?.addEventListener("click", () => {
      setSelectionMode(!selectionMode);
    });
    markSelectedButton?.addEventListener("click", async () => {
      const notificationIds = [
        ...list.querySelectorAll("[data-notification-select]:checked"),
      ].map((checkbox) => checkbox.dataset.notificationSelect);
      if (!notificationIds.length) return;
      markSelectedButton.disabled = true;
      try {
        await submitReadAction(
          "/api/notifications/read-selected",
          notificationIds,
        );
        setSelectionMode(false);
      } catch (_) {
        syncSelectedAction();
        if (realtimeState)
          realtimeState.textContent = "Không thể đánh dấu thông báo đã chọn.";
      }
    });
    loadInbox();
    refreshTeamSubscriptions();
    connectRealtime();
    window.setInterval(() => {
      if (document.hidden) return;
      if (!stompConnected && Date.now() - lastInboxSync > 15000)
        loadInbox(true);
      if (role === "ROLE_RESCUER") refreshTeamSubscriptions();
    }, 15000);
    document.addEventListener("visibilitychange", () => {
      if (!document.hidden) {
        manualClose = false;
        loadInbox();
        refreshTeamSubscriptions();
        connectRealtime();
      } else {
        manualClose = true;
        disconnectRealtime();
      }
    });
    window.addEventListener("beforeunload", () => {
      manualClose = true;
      disconnectRealtime();
    });
  }

  // Disable only after native validation accepts the submission.
  document.querySelectorAll("form[data-disable-on-submit]").forEach((form) => {
    const submitter = form.querySelector(
      "button[type='submit']:not([formnovalidate]),input[type='submit']",
    );
    const status = form.querySelector("[data-submit-status]");
    form.addEventListener("submit", (event) => {
      if (form.dataset.submitting === "true") {
        event.preventDefault();
        return;
      }
      form.dataset.submitting = "true";
      queueMicrotask(() => {
        if (event.defaultPrevented || !form.checkValidity()) {
          delete form.dataset.submitting;
          return;
        }
        if (!submitter) return;
        submitter.dataset.originalText = submitter.textContent;
        submitter.disabled = true;
        submitter.setAttribute("aria-busy", "true");
        submitter.textContent = "Đang gửi…";
        if (status)
          status.textContent = "Đang gửi yêu cầu. Vui lòng không bấm lại.";
      });
    });
    window.addEventListener("pageshow", () => {
      delete form.dataset.submitting;
      if (submitter) {
        submitter.disabled = false;
        submitter.removeAttribute("aria-busy");
        if (submitter.dataset.originalText)
          submitter.textContent = submitter.dataset.originalText;
      }
      if (status) status.textContent = "";
    });
  });

  document.querySelectorAll("img[data-avatar-fallback]").forEach((image) => {
    image.addEventListener(
      "error",
      () => {
        const fallback = image.parentElement?.querySelector(
          "[data-avatar-initial]",
        );
        image.hidden = true;
        if (fallback) {
          fallback.hidden = false;
          fallback.classList.remove("d-none");
        }
      },
      { once: true },
    );
  });

  if (
    "IntersectionObserver" in window &&
    !window.matchMedia("(prefers-reduced-motion: reduce)").matches
  ) {
    const revealTargets = document.querySelectorAll(
      ".section-heading-row,.carousel-visuals,.carousel-controls,.process-intro,.process-list li,.roles-title,.role-lines article,.home-cta > div,.footer-brand,.footer-links > div,.footer-bottom",
    );
    const revealObserver = new IntersectionObserver(
      (entries, observer) => {
        entries.forEach((entry) => {
          if (!entry.isIntersecting) return;
          entry.target.classList.add("reveal-target", "is-revealed");
          observer.unobserve(entry.target);
        });
      },
      { rootMargin: "0px 0px -12% 0px", threshold: 0.08 },
    );
    revealTargets.forEach((target) => revealObserver.observe(target));
  }
});
