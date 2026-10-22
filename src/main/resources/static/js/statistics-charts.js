(() => {
  "use strict";

  const ns = "http://www.w3.org/2000/svg";
  const fmt = new Intl.NumberFormat("vi-VN");
  const colors = {
    PENDING: "#d48a18",
    VERIFIED: "#1e5d99",
    ASSIGNED: "#2589b8",
    IN_PROGRESS: "#087f9a",
    RESCUED: "#16795a",
    CLOSED: "#78899a",
    REJECTED: "#c82436",
    CANCELLED: "#aab6c2",
    LOW: "#16795a",
    MEDIUM: "#2589b8",
    HIGH: "#d48a18",
    CRITICAL: "#c82436",
  };

  function svgElement(tag, attrs = {}, text) {
    const element = document.createElementNS(ns, tag);
    Object.entries(attrs).forEach(([key, value]) => element.setAttribute(key, String(value)));
    if (text !== undefined) element.textContent = text;
    return element;
  }

  function arcPath(cx, cy, outer, inner, start, end, isFull) {
    if (isFull) return `M ${cx} ${cy - outer} A ${outer} ${outer} 0 1 1 ${cx - 0.01} ${cy - outer} Z`;
    const point = (radius, angle) => [cx + radius * Math.cos(angle), cy + radius * Math.sin(angle)];
    const [ox1, oy1] = point(outer, start);
    const [ox2, oy2] = point(outer, end);
    const [ix2, iy2] = point(inner, end);
    const [ix1, iy1] = point(inner, start);
    const large = end - start > Math.PI ? 1 : 0;
    return `M ${ox1} ${oy1} A ${outer} ${outer} 0 ${large} 1 ${ox2} ${oy2} L ${ix2} ${iy2} A ${inner} ${inner} 0 ${large} 0 ${ix1} ${iy1} Z`;
  }

  function render(host) {
    const items = [...host.querySelectorAll(".stats-donut-data li")].map((item) => ({
      label: item.dataset.label || item.dataset.code || "Khác",
      code: item.dataset.code || "",
      value: Math.max(0, Number(item.dataset.count) || 0),
    }));
    const total = items.reduce((sum, item) => sum + item.value, 0);
    if (!total) {
      const empty = document.createElement("p");
      empty.className = "stats-chart-empty";
      empty.textContent = "Chưa có yêu cầu trong khoảng thời gian này.";
      host.append(empty);
      return;
    }

    const svg = svgElement("svg", { class: "stats-donut-svg", viewBox: "0 0 220 220", role: "img", "aria-label": `${host.getAttribute("aria-label")}: ${fmt.format(total)} yêu cầu` });
    const defs = svgElement("defs");
    const gradientId = `stats-emergency-gradient-${[...document.querySelectorAll("[data-stats-donut]")].indexOf(host)}`;
    const gradient = svgElement("linearGradient", { id: gradientId, x1: "0%", y1: "0%", x2: "100%", y2: "100%" });
    gradient.append(svgElement("stop", { offset: "0%", "stop-color": "#f0444f" }), svgElement("stop", { offset: "100%", "stop-color": "#ad1838" }));
    defs.append(gradient);
    svg.append(defs);

    let angle = -Math.PI / 2;
    items.forEach((item) => {
      if (!item.value) return;
      const next = angle + (item.value / total) * Math.PI * 2;
      const baseColor = colors[item.code] || "#64748b";
      const fill = item.code === "CRITICAL" || item.code === "REJECTED" ? `url(#${gradientId})` : baseColor;
      const path = svgElement("path", { d: arcPath(110, 110, 88, 57, angle, next, item.value === total), fill, class: "stats-donut-slice" });
      path.append(svgElement("title", {}, `${item.label}: ${fmt.format(item.value)} (${Math.round((item.value / total) * 100)}%)`));
      svg.append(path);
      angle = next;
    });
    svg.append(svgElement("circle", { cx: 110, cy: 110, r: 48, fill: "#fff" }));
    svg.append(svgElement("text", { x: 110, y: 103, "text-anchor": "middle", class: "stats-donut-total" }, fmt.format(total)));
    svg.append(svgElement("text", { x: 110, y: 125, "text-anchor": "middle", class: "stats-donut-caption" }, "yêu cầu"));

    const legend = document.createElement("ul");
    legend.className = "stats-donut-legend";
    items.forEach((item) => {
      const li = document.createElement("li");
      const swatch = document.createElement("span");
      swatch.className = `stats-donut-swatch tone-${item.code.toLowerCase()}`;
      swatch.setAttribute("aria-hidden", "true");
      const label = document.createElement("span");
      label.className = "stats-donut-label";
      label.textContent = item.label;
      const value = document.createElement("strong");
      value.className = "stats-donut-value";
      value.textContent = `${fmt.format(item.value)} · ${Math.round((item.value / total) * 100)}%`;
      li.append(swatch, label, value);
      legend.append(li);
    });
    host.replaceChildren(svg, legend);
  }

  document.querySelectorAll("[data-stats-donut]").forEach(render);
})();
