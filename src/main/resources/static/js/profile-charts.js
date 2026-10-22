(() => {
  "use strict";
  const data = window.drssProfileCharts;
  if (!data) return;
  const palette = [
    "#dc2626",
    "#2563eb",
    "#f59e0b",
    "#16a34a",
    "#8b5cf6",
    "#64748b",
  ];

  function setup(id) {
    const canvas = document.getElementById(id);
    if (!canvas) return null;
    canvas.width = Math.max(
      260,
      Math.min(680, canvas.parentElement.clientWidth - 32),
    );
    canvas.height = 240;
    const ctx = canvas.getContext("2d");
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    ctx.font = "13px system-ui, sans-serif";
    return { ctx, width: canvas.width, height: canvas.height };
  }

  function empty(plot) {
    plot.ctx.fillStyle = "#64748b";
    plot.ctx.fillText("Chưa có dữ liệu cho khoảng thời gian này.", 16, 120);
  }

  function donut() {
    const plot = setup("profileDistribution");
    if (!plot) return;
    const values = Object.values(data.distribution).map(Number);
    const total = values.reduce((sum, value) => sum + value, 0);
    if (!total) return empty(plot);
    let angle = -Math.PI / 2;
    values.forEach((value, index) => {
      const next = angle + (2 * Math.PI * value) / total;
      plot.ctx.beginPath();
      plot.ctx.arc(plot.width / 2, 116, 84, angle, next);
      plot.ctx.arc(plot.width / 2, 116, 46, next, angle, true);
      plot.ctx.fillStyle = palette[index % palette.length];
      plot.ctx.fill();
      angle = next;
    });
    plot.ctx.textAlign = "center";
    plot.ctx.fillStyle = "#1f2937";
    plot.ctx.fillText(`${total} mục`, plot.width / 2, 120);
  }

  function trend() {
    const plot = setup("profileTrend");
    if (!plot) return;
    const labels = Array.isArray(data.labels) ? data.labels : [];
    const series = Object.entries(data.series || {});
    if (
      !labels.length ||
      series.every(([, values]) => values.every((v) => !Number(v)))
    ) {
      return empty(plot);
    }
    const ctx = plot.ctx;
    const left = 38,
      bottom = 200,
      top = 22,
      right = plot.width - 18;
    const max = Math.max(
      1,
      ...labels.map((_, i) =>
        series.reduce((total, [, values]) => total + Number(values[i] || 0), 0),
      ),
    );
    ctx.strokeStyle = "#cbd5e1";
    ctx.beginPath();
    ctx.moveTo(left, top);
    ctx.lineTo(left, bottom);
    ctx.lineTo(right, bottom);
    ctx.stroke();
    ctx.fillStyle = "#475569";
    ctx.fillText(String(max), 8, top + 5);
    labels.forEach((label, i) => {
      const x = left + ((right - left) * (i + 0.5)) / labels.length;
      ctx.fillText(String(label).slice(-5), x - 15, bottom + 17);
    });
    if (data.role === "ROLE_CITIZEN") {
      const values = series[0]?.[1] || [];
      ctx.beginPath();
      values.forEach((raw, i) => {
        const x = left + ((right - left) * (i + 0.5)) / labels.length;
        const y = bottom - (Number(raw) / max) * (bottom - top);
        if (i === 0) ctx.moveTo(x, y);
        else ctx.lineTo(x, y);
      });
      ctx.strokeStyle = palette[1];
      ctx.lineWidth = 3;
      ctx.stroke();
    } else {
      labels.forEach((_, i) => {
        let cumulative = 0;
        series.forEach(([, values], index) => {
          const value = Number(values[i] || 0);
          const height = (value / max) * (bottom - top);
          const x = left + ((right - left) * (i + 0.2)) / labels.length;
          const barWidth = ((right - left) * 0.6) / labels.length;
          ctx.fillStyle = palette[index % palette.length];
          ctx.fillRect(x, bottom - cumulative - height, barWidth, height);
          cumulative += height;
        });
      });
      series.forEach(([name], i) => {
        ctx.fillStyle = palette[i % palette.length];
        ctx.fillRect(44 + i * 110, 2, 8, 8);
        ctx.fillStyle = "#334155";
        ctx.fillText(name, 56 + i * 110, 10);
      });
    }
  }

  donut();
  trend();
})();
