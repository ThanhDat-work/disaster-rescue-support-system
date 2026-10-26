(() => {
  const loadingClass = "drss-page-loading";
  const screen = document.querySelector("[data-page-skeleton-screen]");

  function show() {
    document.documentElement.classList.add(loadingClass);
    if (screen) screen.setAttribute("aria-busy", "true");
  }

  function hide() {
    document.documentElement.classList.remove(loadingClass);
    if (screen) screen.removeAttribute("aria-busy");
  }

  window.DrssPageSkeleton = Object.freeze({ show, hide });
})();
