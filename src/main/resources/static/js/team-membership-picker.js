document.addEventListener("DOMContentLoaded", () => {
  const leaderSearch = document.querySelector("#leader-search");
  const leaderSelect = document.querySelector("#leaderUserId");
  const memberSearch = document.querySelector("#member-search");
  const memberOptions = [...document.querySelectorAll(".member-option")];
  const leaderSummary = document.querySelector("#leader-summary");
  const memberSummary = document.querySelector("#member-summary");

  if (!leaderSelect) return;

  const normalize = (value) => (value || "").toLocaleLowerCase("vi").trim();
  const selectedMembers = () => memberOptions
    .map((row) => row.querySelector("input"))
    .filter((input) => input.checked);

  const syncMutualExclusion = () => {
    const leaderId = leaderSelect.value;
    memberOptions.forEach((row) => {
      const input = row.querySelector("input");
      const isLeader = input.value === leaderId;
      if (isLeader) input.checked = false;
      row.classList.toggle("d-none", isLeader);
    });
    const memberIds = new Set(selectedMembers().map((input) => input.value));
    [...leaderSelect.options].forEach((option) => {
      if (!option.value) return;
      option.disabled = memberIds.has(option.value);
      option.hidden = memberIds.has(option.value);
    });
  };

  leaderSearch?.addEventListener("input", () => {
    const keyword = normalize(leaderSearch.value);
    [...leaderSelect.options].forEach((option) => {
      if (!option.value) return;
      const matches = normalize(option.dataset.search).includes(keyword);
      option.hidden = option.disabled || !matches;
    });
  });

  memberSearch?.addEventListener("input", () => {
    const keyword = normalize(memberSearch.value);
    memberOptions.forEach((row) => {
      const isLeader = row.dataset.userId === leaderSelect.value;
      row.classList.toggle(
        "d-none",
        isLeader || !normalize(row.dataset.search).includes(keyword)
      );
    });
  });

  document.querySelector("#confirm-leader")?.addEventListener("click", () => {
    syncMutualExclusion();
    const option = leaderSelect.selectedOptions[0];
    leaderSummary.textContent = option?.value
      ? `Đã chọn: ${option.textContent.trim()}`
      : "Cần chọn một người làm đội trưởng.";
  });

  document.querySelector("#confirm-members")?.addEventListener("click", () => {
    const members = selectedMembers();
    if (members.some((input) => input.value === leaderSelect.value)) {
      leaderSelect.value = "";
      leaderSummary.textContent = "Đội trưởng vừa chọn là thành viên; vui lòng chọn lại.";
    }
    syncMutualExclusion();
    memberSummary.textContent = members.length === 0
      ? "Đội hiện chỉ có đội trưởng."
      : `Đã chọn ${members.length} thành viên.`;
  });

  leaderSelect.addEventListener("change", syncMutualExclusion);
  memberOptions.forEach((row) => row.querySelector("input")
    .addEventListener("change", syncMutualExclusion));
  syncMutualExclusion();
});