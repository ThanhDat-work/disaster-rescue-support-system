(() => {
  "use strict";
  const data = window.drssProfileCharts;
  if (!data) return;

  const colors = ["#1e5d99", "#2589b8", "#16795a", "#d97825", "#64748b", "#72869a"];
  const number = new Intl.NumberFormat("vi-VN");
  const localized = { PENDING: "Chờ xác minh", VERIFIED: "Đã xác minh", ASSIGNED: "Đã phân công", IN_PROGRESS: "Đang cứu hộ", RESCUED: "Đã cứu hộ", CLOSED: "Đã đóng", REJECTED: "Từ chối", CANCELLED: "Đã hủy", LOW: "Thấp", MEDIUM: "Trung bình", HIGH: "Cao", CRITICAL: "Nguy cấp" };
  const labelFor = (label) => localized[label] || label.replaceAll("_", " ");
  const colorFor = (label, index) => /critical|reject|từ chối|khẩn|nguy cấp/i.test(label) ? "#c82436" : /pending|chờ|wait/i.test(label) ? "#bd7516" : /assigned|phân công/i.test(label) ? "#d97825" : /rescu|cứu hộ|closed|đã đóng|hoàn thành/i.test(label) ? "#16795a" : colors[index % colors.length];
  const fillFor = (label, index) => /critical|reject|từ chối|khẩn|nguy cấp/i.test(label) ? "linear-gradient(100deg,#f0444f,#bd1f3a)" : colorFor(label, index);

  function node(tag, attrs = {}, text) {
    const element = document.createElement(tag);
    Object.entries(attrs).forEach(([key, value]) => element.setAttribute(key, String(value)));
    if (text !== undefined) element.textContent = text;
    return element;
  }
  function svgNode(tag, attrs = {}, text) {
    const element = document.createElementNS("http://www.w3.org/2000/svg", tag);
    Object.entries(attrs).forEach(([key, value]) => element.setAttribute(key, String(value)));
    if (text !== undefined) element.textContent = text;
    return element;
  }

  function renderDistribution() {
    const host = document.getElementById("profileDistribution");
    if (!host) return;
    const entries = Object.entries(data.distribution || {})
      .map(([label, raw], index) => [labelFor(label), Math.max(0, Number(raw) || 0), index])
      .sort((a, b) => b[1] - a[1]);
    const total = entries.reduce((sum, [, value]) => sum + value, 0);
    if (!entries.length || !total) {
      host.append(node("p", { class: "chart-empty" }, "Chưa có yêu cầu trong khoảng thời gian này."));
      return;
    }

    const list = node("div", { class: "chart-rows", role: "list" });
    entries.forEach(([label, value, index]) => {
      const row = node("div", { class: "chart-row", role: "listitem" });
      const top = node("div", { class: "chart-row-top" });
      top.append(node("span", { class: "chart-row-label" }, label));
      top.append(node("strong", { class: "chart-row-value" }, `${number.format(value)} · ${Math.round((value / total) * 100)}%`));
      const track = node("div", { class: "chart-track", "aria-hidden": "true" });
      track.append(node("span", { class: "chart-fill", style: `--bar-color:${fillFor(label, index)};--bar-width:${(value / total) * 100}%` }));
      row.append(top, track);
      list.append(row);
    });
    host.setAttribute("aria-label", `Phân bố ${number.format(total)} yêu cầu theo trạng thái`);
    host.append(list, node("p", { class: "chart-summary" }, `Tổng cộng ${number.format(total)} yêu cầu · sắp xếp theo số lượng`));
  }

  function renderTrend() {
    const host = document.getElementById("profileTrend");
    if (!host) return;
    const labels = Array.isArray(data.labels) ? data.labels : [];
    const series = Object.entries(data.series || {}).map(([name, values], index) => ({
      name: labelFor(name),
      values: labels.map((_, i) => Math.max(0, Number(values?.[i]) || 0)),
      color: colorFor(labelFor(name), index),
    }));
    if (!labels.length || !series.length || series.every(({ values }) => values.every((value) => value === 0))) {
      host.append(node("p", { class: "chart-empty" }, "Chưa có hoạt động để so sánh theo thời gian."));
      return;
    }

    const width = 640;
    const height = 264;
    const margin = { top: 18, right: 18, bottom: 42, left: 42 };
    const plotW = width - margin.left - margin.right;
    const plotH = height - margin.top - margin.bottom;
    const maxValue = Math.max(1, ...series.flatMap(({ values }) => values));
    const tickStep = Math.max(1, Math.ceil(maxValue / 4));
    const yMax = tickStep * 4;
    const x = (index) => margin.left + (labels.length < 2 ? plotW / 2 : (plotW * index) / (labels.length - 1));
    const y = (value) => margin.top + plotH - (value / yMax) * plotH;
    const svg = svgNode("svg", { class: "trend-chart", viewBox: `0 0 ${width} ${height}`, role: "img", "aria-label": "Đường xu hướng các yêu cầu theo thời gian" });

    for (let tick = 0; tick <= 4; tick++) {
      const value = tick * tickStep;
      const yy = y(value);
      svg.append(svgNode("line", { x1: margin.left, y1: yy, x2: width - margin.right, y2: yy, class: "chart-gridline" }));
      const tickLabel = svgNode("text", { x: margin.left - 9, y: yy + 4, "text-anchor": "end", class: "chart-axis-label" }, number.format(value));
      svg.append(tickLabel);
    }

    const labelStride = Math.max(1, Math.ceil(labels.length / 6));
    labels.forEach((label, index) => {
      if (index % labelStride !== 0 && index !== labels.length - 1) return;
      svg.append(svgNode("text", { x: x(index), y: height - 12, "text-anchor": "middle", class: "chart-axis-label" }, String(label)));
    });

    series.forEach(({ values, color, name }) => {
      const topPoints = values.map((value, index) => `${x(index)} ${y(value)}`);
      const baselinePoints = values
        .map((_, index) => `${x(index)} ${y(0)}`)
        .reverse();
      const areaPath = `M ${topPoints.join(" L ")} L ${baselinePoints.join(" L ")} Z`;
      svg.append(svgNode("path", { d: areaPath, class: "chart-area", fill: color }));
      const points = values.map((value, index) => `${x(index)},${y(value)}`).join(" ");
      svg.append(svgNode("polyline", { points, class: "chart-line", stroke: color }));
      values.forEach((value, index) => {
        const circle = svgNode("circle", { cx: x(index), cy: y(value), r: 3.5, class: "chart-point", fill: color });
        circle.append(svgNode("title", {}, `${name}, ${labels[index]}: ${number.format(value)}`));
        svg.append(circle);
      });
    });

    const legend = node("ul", { class: "chart-legend", "aria-label": "Chú giải" });
    series.forEach(({ name, color }) => {
      const item = node("li");
      item.append(node("span", { class: "chart-legend-mark", style: `--legend-color:${color}` }), node("span", {}, name));
      legend.append(item);
    });
    host.setAttribute("aria-label", `Xu hướng ${series.map(({ name }) => name).join(", ")} qua ${labels.length} mốc thời gian`);
    const scroller = node("div", { class: "trend-chart-scroller" });
    scroller.append(svg);
    host.append(scroller, legend);
  }

  renderDistribution();
  renderTrend();
})();
