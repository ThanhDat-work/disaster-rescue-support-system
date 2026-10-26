(() => {
  "use strict";

  let overlay;
  let messageNode;
  let retryButton;
  let closeButton;
  let retryAction;
  let dismissAction;
  let previousFocus;

  function ensureOverlay() {
    if (overlay) return;
    overlay = document.createElement("div");
    overlay.className = "drss-retry-overlay";
    overlay.hidden = true;
    overlay.setAttribute("role", "alertdialog");
    overlay.setAttribute("aria-modal", "true");
    overlay.setAttribute("aria-labelledby", "drss-retry-title");
    overlay.setAttribute("aria-describedby", "drss-retry-message");
    overlay.innerHTML = `
      <div class="drss-retry-card">
        <button type="button" class="drss-retry-close" aria-label="Đóng thông báo">
          <svg viewBox="0 0 20 20" width="18" height="18" aria-hidden="true" focusable="false">
            <path d="m5 5 10 10M15 5 5 15" />
          </svg>
        </button>
        <h2 id="drss-retry-title">Kết nối bị gián đoạn</h2>
        <p id="drss-retry-message"></p>
        <div class="drss-retry-actions">
          <button type="button" class="btn btn-primary" data-retry-action>Thử lại</button>
        </div>
      </div>`;
    messageNode = overlay.querySelector("#drss-retry-message");
    retryButton = overlay.querySelector("[data-retry-action]");
    closeButton = overlay.querySelector(".drss-retry-close");
    retryButton.addEventListener("click", () => {
      const action = retryAction;
      hide();
      if (action)
        Promise.resolve()
          .then(action)
          .catch(() => {});
    });
    closeButton.addEventListener("click", dismiss);
    overlay.addEventListener("click", (event) => {
      if (event.target === overlay) dismiss();
    });
    overlay.addEventListener("keydown", (event) => {
      if (event.key === "Escape") {
        event.preventDefault();
        dismiss();
        return;
      }
      if (event.key === "Tab") {
        const focusable = [closeButton, retryButton];
        const currentIndex = focusable.indexOf(document.activeElement);
        if (event.shiftKey && currentIndex <= 0) {
          event.preventDefault();
          retryButton.focus();
        } else if (!event.shiftKey && currentIndex === focusable.length - 1) {
          event.preventDefault();
          closeButton.focus();
        }
      }
    });
    document.body.append(overlay);
  }

  function dismiss() {
    const action = dismissAction;
    hide();
    if (action) action();
  }

  function show(message, onRetry, onDismiss) {
    if (typeof onRetry !== "function") return;
    window.DrssPageSkeleton?.hide();
    ensureOverlay();
    if (overlay.hidden) previousFocus = document.activeElement;
    messageNode.textContent = message;
    retryAction = onRetry;
    dismissAction = typeof onDismiss === "function" ? onDismiss : null;
    overlay.hidden = false;
    retryButton.focus();
  }

  function hide() {
    if (!overlay || overlay.hidden) return;
    overlay.hidden = true;
    retryAction = null;
    dismissAction = null;
    if (previousFocus?.isConnected) previousFocus.focus();
    previousFocus = null;
  }

  async function run({
    request,
    retry,
    message,
    timeoutMs = 10000,
    shouldShow = () => true,
    onDismiss,
    controller = new AbortController(),
  }) {
    if (
      typeof request !== "function" ||
      !Number.isFinite(timeoutMs) ||
      timeoutMs <= 0
    ) {
      throw new TypeError("Invalid asynchronous request configuration");
    }
    let expired = false;
    const timeout = window.setTimeout(() => {
      expired = true;
      controller.abort();
    }, timeoutMs);
    try {
      return await request(controller.signal);
    } catch (error) {
      if (
        typeof retry === "function" &&
        shouldShow() &&
        (expired || !controller.signal.aborted)
      ) {
        show(
          message || "Không thể tải dữ liệu. Kiểm tra kết nối rồi thử lại.",
          retry,
          onDismiss,
        );
      }
      throw error;
    } finally {
      window.clearTimeout(timeout);
    }
  }

  function getJson(url, { retry, message, shouldShow, controller } = {}) {
    return run({
      retry,
      message,
      shouldShow,
      controller,
      request: async (signal) => {
        const response = await fetch(url, {
          credentials: "same-origin",
          headers: { Accept: "application/json" },
          signal,
        });
        if (
          !response.ok ||
          !response.headers.get("content-type")?.includes("application/json")
        ) {
          throw new Error(`Unexpected response: ${response.status}`);
        }
        return response.json();
      },
    });
  }

  // A top-level HTML navigation is not a fetch: prepare same-origin GET pages
  // while the current document remains visible, then navigate normally.
  // This intentionally does not intercept POST forms or direct URL/F5 loads.
  let navigationSequence = 0;
  let navigationController = null;

  async function navigateGet(url) {
    const sequence = ++navigationSequence;
    navigationController?.abort();
    const controller = new AbortController();
    navigationController = controller;
    window.DrssPageSkeleton?.show();
    try {
      const destination = await run({
        controller,
        timeoutMs: 10000,
        retry: () => navigateGet(url),
        onDismiss: () => {
          if (sequence !== navigationSequence) return;
          navigationSequence++;
          controller.abort();
          navigationController = null;
        },
        shouldShow: () => sequence === navigationSequence,
        message: "Trang tải quá lâu. Thử lại để tải trang này.",
        request: async (signal) => {
          const response = await fetch(url, {
            credentials: "same-origin",
            headers: { Accept: "text/html" },
            cache: "no-store",
            signal,
          });
          if (response.status >= 500) {
            throw new Error(`Page unavailable: ${response.status}`);
          }
          // Wait for the complete HTML, not just response headers.
          await response.text();
          return response.url || url;
        },
      });
      if (sequence === navigationSequence) window.location.assign(destination);
    } catch (_) {
      // run() owns the shared retry overlay. Never show a browser error page
      // or silently submit a second request after an uncertain outcome.
    } finally {
      if (sequence === navigationSequence) navigationController = null;
    }
  }

  function internalPage(url) {
    return (
      url.origin === window.location.origin &&
      url.protocol === window.location.protocol &&
      !url.pathname.startsWith("/api/") &&
      !/\.(?:pdf|zip|png|jpe?g|webp|svg|mp4|csv|xlsx?)$/i.test(url.pathname)
    );
  }

  document.addEventListener("click", (event) => {
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
    const link = event.target.closest("a[href]");
    if (
      !link ||
      link.hasAttribute("download") ||
      (link.target && link.target !== "_self")
    )
      return;
    const url = new URL(link.href, window.location.href);
    if (!internalPage(url)) return;
    if (
      url.pathname === window.location.pathname &&
      url.search === window.location.search &&
      url.hash
    )
      return;
    event.preventDefault();
    navigateGet(url.href);
  });

  document.addEventListener("submit", (event) => {
    if (event.defaultPrevented || !(event.target instanceof HTMLFormElement))
      return;
    const form = event.target;
    if (
      form.method.toUpperCase() !== "GET" ||
      (form.target && form.target !== "_self")
    )
      return;
    const url = new URL(form.action, window.location.href);
    if (!internalPage(url)) return;
    const fields = new URLSearchParams(new FormData(form));
    if (event.submitter?.name)
      fields.append(event.submitter.name, event.submitter.value);
    url.search = fields.toString();
    event.preventDefault();
    navigateGet(url.href);
  });

  window.DrssRetry = Object.freeze({ run, getJson, show, hide });
})();
