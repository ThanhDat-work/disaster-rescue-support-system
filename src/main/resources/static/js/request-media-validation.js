(() => {
  "use strict";

  const form = document.getElementById("citizenRequestForm");
  const input = document.getElementById("mediaFiles");
  const feedback = document.getElementById("mediaFilesFeedback");

  if (!form || !input || !feedback) {
    return;
  }

  const maxFileSizeBytes = Number(input.dataset.maxFileSizeBytes);
  const maxFiles = Number(input.dataset.maxFiles);
  const maxVideos = Number(input.dataset.maxVideos);

  if (
    !Number.isSafeInteger(maxFileSizeBytes) ||
    maxFileSizeBytes <= 0 ||
    !Number.isSafeInteger(maxFiles) ||
    maxFiles <= 0 ||
    !Number.isSafeInteger(maxVideos) ||
    maxVideos < 0
  ) {
    return;
  }

  const maximumMiB = Math.floor(maxFileSizeBytes / (1024 * 1024));

  function isVideo(file) {
    return (
      file.type.toLowerCase() === "video/mp4" ||
      file.name.toLowerCase().endsWith(".mp4")
    );
  }

  function showError(message) {
    input.setCustomValidity(message);
    input.classList.add("is-invalid");
    feedback.textContent = message;
    feedback.hidden = false;
  }

  function clearError() {
    input.setCustomValidity("");
    input.classList.remove("is-invalid");
    feedback.textContent = "";
    feedback.hidden = true;
  }

  function validateSelection() {
    clearError();

    const files = Array.from(input.files ?? []);

    if (files.length > maxFiles) {
      showError(`Chỉ được chọn tối đa ${maxFiles} tệp.`);
      return false;
    }

    const oversized = files.find((file) => file.size > maxFileSizeBytes);

    if (oversized) {
      showError(
        `Tệp "${oversized.name}" vượt quá ${maximumMiB} MiB. ` +
          "Vui lòng chọn tệp nhỏ hơn.",
      );
      return false;
    }

    const videoCount = files.filter(isVideo).length;

    if (videoCount > maxVideos) {
      showError(`Chỉ được chọn tối đa ${maxVideos} video MP4.`);
      return false;
    }

    return true;
  }

  input.addEventListener("change", validateSelection);

  form.addEventListener("submit", (event) => {
    if (validateSelection()) {
      return;
    }

    event.preventDefault();
    event.stopPropagation();
    input.focus();
    input.reportValidity();
  });
})();
