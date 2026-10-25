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
    const realtimeState = notificationPanel.querySelector(
      "[data-realtime-state]",
    );
    const role = notificationPanel.dataset.activeRole || "";
    let reconnectTimer = 0;
    let handshakeTimer = 0;
    let reconnectDelay = 1500;
    let socket = null;
    let stompConnected = false;
    let subscriptionSequence = 0;
    let subscribedDestinations = new Set();
    let manualClose = false;
    let inboxGeneration = 0;
    let inboxController = null;
    let unreadAmount = 0;
    let lastInboxSync = 0;
    let teamIds = [];
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
          const marker = document.createElement("span");
          marker.setAttribute("aria-hidden", "true");
          const copy = document.createElement("div");
          const title = document.createElement("h3");
          title.textContent = item.title || "Thông báo";
          const message = document.createElement("p");
          message.textContent = item.message || "";
          copy.append(title, message);
          if (item.createdAt) {
            const time = document.createElement("time");
            time.dateTime = item.createdAt;
            time.textContent = formatDate(item.createdAt);
            copy.append(time);
          }
          if (!item.read && item.id) {
            const button = document.createElement("button");
            button.type = "button";
            button.textContent = "Đánh dấu đã đọc";
            button.addEventListener("click", async () => {
              button.disabled = true;
              try {
                const result = await window.DrssRetry.run({
                  retry: loadInbox,
                  message:
                    "Chưa xác định được trạng thái đã đọc. Bấm Thử lại để tải lại hộp thư.",
                  request: (signal) =>
                    fetch(
                      `/api/notifications/${encodeURIComponent(item.id)}/read`,
                      {
                        method: "POST",
                        credentials: "same-origin",
                        headers: {
                          Accept: "application/json",
                          ...csrfHeaders(),
                        },
                        signal,
                      },
                    ),
                });
                if (!result.ok) throw new Error(`HTTP ${result.status}`);
                await loadInbox();
              } catch (_) {
                button.disabled = false;
                if (realtimeState)
                  realtimeState.textContent =
                    "Không thể cập nhật trạng thái đã đọc.";
              }
            });
            copy.append(button);
          }
          row.append(marker, copy);
          fragment.append(row);
        });
        if (inbox.items?.length) list.replaceChildren(fragment);
        else showState("Chưa có thông báo nào.");
        if (realtimeState && !stompConnected && !silent)
          realtimeState.textContent = "Cập nhật khi mở hộp thư";
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
      if (
        !stompConnected ||
        socket?.readyState !== WebSocket.OPEN ||
        subscribedDestinations.has(destination)
      )
        return;
      socket.send(
        window.DrssStomp.frame("SUBSCRIBE", {
          id: `drss-${++subscriptionSequence}`,
          destination,
          ack: "auto",
        }),
      );
      subscribedDestinations.add(destination);
    }
    async function refreshTeamSubscriptions() {
      if (role !== "ROLE_RESCUER" || document.hidden) return;
      try {
        const result = await window.DrssRetry.getJson(
          "/api/realtime/subscriptions",
        );
        const nextIds = [
          ...new Set(
            (result.teamIds || []).filter(
              (id) => typeof id === "string" && validUuid.test(id),
            ),
          ),
        ];
        const removed = teamIds.some((id) => !nextIds.includes(id));
        teamIds = nextIds;
        if (removed && socket?.readyState === WebSocket.OPEN) {
          // A removed membership must not keep an old team subscription.
          socket.close();
        } else {
          teamIds.forEach((id) =>
            subscribe(`/user/queue/teams/${id}/notifications`),
          );
        }
      } catch (_) {
        // Membership cannot be established after a failed lookup.
        teamIds = [];
        if (
          subscribedDestinations.size > 1 &&
          socket?.readyState === WebSocket.OPEN
        )
          socket.close();
      }
    }
    function connectRealtime() {
      if (
        manualClose ||
        document.hidden ||
        !window.DrssStomp ||
        socket?.readyState === WebSocket.OPEN ||
        socket?.readyState === WebSocket.CONNECTING
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
        const currentSocket = new WebSocket(`${scheme}//${location.host}/ws`);
        socket = currentSocket;
        stompConnected = false;
        subscribedDestinations = new Set();
        subscriptionSequence = 0;
        const parser = window.DrssStomp.createParser();
        currentSocket.onopen = () => {
          if (socket !== currentSocket) return;
          currentSocket.send(
            window.DrssStomp.frame("CONNECT", {
              "accept-version": "1.2",
              host: location.host,
              "heart-beat": "0,0",
              ...csrf,
            }),
          );
          handshakeTimer = window.setTimeout(() => {
            if (socket === currentSocket && !stompConnected)
              currentSocket.close();
          }, 8000);
          if (realtimeState)
            realtimeState.textContent = "Đang kết nối thông báo trực tiếp…";
        };
        currentSocket.onmessage = (event) => {
          if (socket !== currentSocket) return;
          let frames;
          try {
            frames = parser.push(event.data);
          } catch (_) {
            currentSocket.close();
            return;
          }
          frames.forEach((frame) => {
            if (frame.command === "CONNECTED") {
              window.clearTimeout(handshakeTimer);
              stompConnected = true;
              reconnectDelay = 1500;
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
            } else if (frame.command === "MESSAGE") {
              let payload;
              try {
                payload = JSON.parse(frame.body);
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
                seenNotifications.delete(
                  seenNotifications.values().next().value,
                );
              setUnread(unreadAmount + 1);
              showToast(payload);
              loadInbox(true);
            } else if (frame.command === "ERROR") {
              if (realtimeState)
                realtimeState.textContent =
                  "Kết nối thông báo bị từ chối; đang thử lại…";
              currentSocket.close();
            }
          });
        };
        currentSocket.onerror = () => currentSocket.close();
        currentSocket.onclose = () => {
          if (socket !== currentSocket) return;
          window.clearTimeout(handshakeTimer);
          socket = null;
          stompConnected = false;
          subscribedDestinations.clear();
          if (manualClose || document.hidden) return;
          if (realtimeState)
            realtimeState.textContent = "Mất kết nối, đang thử kết nối lại…";
          window.clearTimeout(reconnectTimer);
          reconnectTimer = window.setTimeout(connectRealtime, reconnectDelay);
          reconnectDelay = Math.min(30000, reconnectDelay * 2);
        };
      } catch (_) {
        if (realtimeState)
          realtimeState.textContent = "Không thể mở kết nối trực tiếp.";
        window.clearTimeout(reconnectTimer);
        reconnectTimer = window.setTimeout(connectRealtime, reconnectDelay);
        reconnectDelay = Math.min(30000, reconnectDelay * 2);
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
        window.clearTimeout(reconnectTimer);
        socket?.close();
      }
    });
    window.addEventListener("beforeunload", () => {
      manualClose = true;
      socket?.close();
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
