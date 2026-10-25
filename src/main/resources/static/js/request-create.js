(() => {
  "use strict";
  const form = document.getElementById("citizenRequestForm");
  if (!form || !window.fetch || !window.AbortController || !window.DrssRetry)
    return;

  const owner = form.dataset.requestOwnerId;
  const keyInput = form.querySelector('input[name="operationKey"]');
  const submit = form.querySelector('button[type="submit"]');
  if (!owner || !keyInput || !submit) return;

  // Persist only an operation UUID, not form content, media, credentials or tokens.
  const storageName = `drss:create:${owner}`;
  const validKey =
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
  let busy = false;
  let generation = 0;
  let payload = null; // FormData retains the exact File objects while this page is open.
  let recoveredCanSubmit = false;
  let savedKey = null;
  try {
    savedKey = sessionStorage.getItem(storageName);
  } catch (_) {
    /* Private mode. */
  }

  function remember() {
    try {
      sessionStorage.setItem(storageName, keyInput.value);
    } catch (_) {
      /* Private mode. */
    }
  }
  function forget() {
    savedKey = null;
    try {
      sessionStorage.removeItem(storageName);
    } catch (_) {
      /* Private mode. */
    }
  }
  function setBusy(value) {
    busy = value;
    submit.disabled = value;
    submit.setAttribute("aria-busy", String(value));
    submit.textContent = value ? "Đang gửi…" : "Gửi yêu cầu";
  }
  async function readJson(response) {
    if (!response.headers.get("content-type")?.includes("application/json")) {
      throw new Error("Invalid JSON response");
    }
    return response.json();
  }
  function pending(message) {
    setBusy(true);
    window.DrssRetry.show(message, () => inspect(true));
  }
  function applyResult(result, allowPost) {
    if (result.state === "SUCCEEDED" && validKey.test(result.requestId || "")) {
      forget();
      location.assign(`/requests/${encodeURIComponent(result.requestId)}`);
      return;
    }
    if (result.state === "PROCESSING" || result.state === "UNKNOWN") {
      pending("Yêu cầu có thể vẫn đang xử lý. Thử lại để kiểm tra kết quả.");
      return;
    }
    if (
      result.state === "NOT_FOUND" ||
      (result.state === "FAILED" && result.retryAllowed === true)
    ) {
      if (allowPost && payload) {
        // Explicit status check succeeded; replay exact key + payload only now.
        sendCaptured();
      } else if (payload) {
        pending(
          "Chưa có yêu cầu hoàn tất. Thử lại với cùng mã thao tác và nội dung.",
        );
      } else {
        // Reload loses selected files. Ask the user to re-enter the form;
        // retaining the key means the backend still prevents duplicate creation.
        recoveredCanSubmit = true;
        window.DrssRetry.hide();
        setBusy(false);
      }
      return;
    }
    window.DrssRetry.hide();
    setBusy(false);
    if (result.state === "FAILED") {
      forget();
      window.alert(
        "Lần gửi đã thất bại và không thể thử lại. Kiểm tra Yêu cầu của tôi trước khi tạo yêu cầu mới.",
      );
      location.assign("/requests");
    } else if (
      result.state === "LIMITED" ||
      result.state === "VALIDATION_ERROR"
    ) {
      if (result.state === "VALIDATION_ERROR") {
        payload = null;
        recoveredCanSubmit = true;
      }
      window.alert(
        result.message || "Vui lòng kiểm tra biểu mẫu trước khi gửi lại.",
      );
    } else {
      pending("Chưa xác định được kết quả. Thử lại để kiểm tra mã thao tác.");
    }
  }
  async function inspect(allowPost = false) {
    const current = ++generation;
    setBusy(true);
    try {
      const result = await window.DrssRetry.getJson(
        `/api/request-creations/${encodeURIComponent(keyInput.value)}`,
        {
          retry: () => inspect(true),
          shouldShow: () => current === generation,
          message:
            "Kiểm tra kết quả quá lâu. Thử lại để tra cứu cùng mã thao tác.",
        },
      );
      if (current === generation) applyResult(result, allowPost);
    } catch (_) {
      if (current === generation)
        pending(
          "Chưa kiểm tra được kết quả. Thử lại để tra cứu cùng mã thao tác.",
        );
    }
  }
  async function sendCaptured() {
    if (!payload) return;
    const current = ++generation;
    setBusy(true);
    try {
      const result = await window.DrssRetry.run({
        timeoutMs: 90000,
        retry: () => inspect(true),
        shouldShow: () => current === generation,
        message:
          "Gửi yêu cầu quá lâu. Thử lại để kiểm tra kết quả trước khi gửi lại.",
        request: async (signal) =>
          readJson(
            await fetch("/api/request-creations", {
              method: "POST",
              credentials: "same-origin",
              signal,
              headers: { Accept: "application/json" },
              body: payload,
            }),
          ),
      });
      if (current === generation) applyResult(result, false);
    } catch (_) {
      if (current === generation)
        pending(
          "Chưa xác định được kết quả gửi. Thử lại để kiểm tra mã thao tác.",
        );
    }
  }

  form.addEventListener("submit", (event) => {
    // Media validation is registered first and can cancel this submission.
    if (event.defaultPrevented || !form.checkValidity()) return;
    event.preventDefault();
    if (busy || !validKey.test(keyInput.value)) return;
    if (savedKey && !recoveredCanSubmit) {
      inspect(true);
      return;
    }
    payload = new FormData(form);
    if (!savedKey) {
      savedKey = keyInput.value;
      remember();
      sendCaptured();
    } else {
      // After reload, check again before posting the re-entered payload.
      recoveredCanSubmit = false;
      inspect(true);
    }
  });

  if (savedKey && validKey.test(savedKey)) {
    keyInput.value = savedKey;
    inspect(false);
  } else if (savedKey) {
    forget();
  }
})();
