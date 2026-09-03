let token = localStorage.getItem("lmcs_token") || null;
let currentInspection = null;

const $ = (id) => document.getElementById(id);

function setState(show) {
  $("authView").style.display = show ? "block" : "none";
  $("inspectView").style.display = show ? "none" : "block";
  $("userbox").style.display = show ? "none" : "flex";
}

function renderUser(user) {
  $("username").textContent = `${user.full_name} (${user.role})`;
}

$("loginForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  const payload = { email: $("email").value, password: $("password").value };
  const res = await fetch("/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (!res.ok) return alert("Login failed: " + (await res.text()));
  const data = await res.json();
  token = data.access_token;
  localStorage.setItem("lmcs_token", token);
  renderUser(data.user);
  setState(false);
});

$("logout").addEventListener("click", () => {
  token = null;
  localStorage.removeItem("lmcs_token");
  setState(true);
});

$("imageInput").addEventListener("change", (e) => {
  const file = e.target.files[0];
  if (!file) return;
  const reader = new FileReader();
  reader.onload = () => {
    $("previewImg").src = reader.result;
    $("previewImg").hidden = false;
  };
  reader.readAsDataURL(file);
});

$("inspectForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  const file = $("imageInput").files[0];
  if (!file) return;
  $("status").textContent = "Running OCR, field extraction and rule evaluation…";
  $("inspectBtn").disabled = true;
  const form = new FormData();
  form.append("file", file);
  if ($("productName").value) form.append("product_name", $("productName").value);
  try {
    const res = await fetch("/inspect", { method: "POST", headers: { Authorization: `Bearer ${token}` }, body: form });
    if (!res.ok) throw new Error((await res.text()).slice(0, 400));
    currentInspection = await res.json();
    renderInspection(currentInspection);
    $("status").textContent = "Inspection completed.";
  } catch (err) {
    $("status").textContent = "Inspection failed: " + err.message;
  } finally {
    $("inspectBtn").disabled = false;
  }
});

function badge(status) {
  const cls = (status || "").toLowerCase();
  return `<span class="badge ${cls}">${status}</span>`;
}

function renderInspection(data) {
  $("inspectionId").textContent = data.inspection_id;
  const compliance = (data.compliance || {}).status || "—";
  $("complianceBadge").outerHTML = badge(compliance);
  const riskLevel = (data.risk || {}).level || "—";
  $("riskBadge").outerHTML = badge(riskLevel);
  const product = data.product || {};
  $("rProduct").textContent = product.name || "—";
  $("rMrp").textContent = product.mrp || "—";
  $("rNetQty").textContent = product.net_quantity || "—";
  $("rMfr").textContent = product.manufacturer || "—";
  $("rPacker").textContent = product.packer || "—";
  $("rImporter").textContent = product.importer || "—";
  const confidence = data.confidence || {};
  $("rConfidence").textContent = `${((confidence.overall || 0) * 100).toFixed(1)}% (${confidence.verdict})`;
  $("rRisk").textContent = `${riskLevel} — ${data.risk.reason || ""}`;

  const declTable = $("declTable");
  declTable.innerHTML = `<tr><th>Declaration</th><th>Value</th><th>Present</th><th>Confidence</th></tr>` +
    (data.declarations || []).map((d) => `
      <tr>
        <td>${d.type}</td>
        <td>${d.value ? String(d.value).slice(0, 140) : "—"}</td>
        <td class="${d.present ? "ok" : "miss"}">${d.present ? "present" : "missing"}</td>
        <td>${(d.confidence || 0).toFixed(2)}</td>
      </tr>`).join("");

  const ruleTable = $("ruleTable");
  ruleTable.innerHTML = `<tr><th>Rule</th><th>Result</th><th>Reason</th></tr>` +
    (data.rule_results || []).map((r) => `
      <tr>
        <td>${r.rule_id}</td>
        <td class="${r.result === "PASS" ? "ok" : "miss"}">${r.result}</td>
        <td>${r.reason}</td>
      </tr>`).join("");

  $("violationList").innerHTML = (data.violations || []).map((v) =>
    `<li><strong>${v.rule_id}</strong> — ${v.description} <em>(${v.severity})</em></li>`
  ).join("") || "<li>No violations recorded.</li>";

  $("evidenceGrid").innerHTML = (data.evidence || []).map((ev) =>
    `<img src="${ev.image_path}" alt="evidence ${ev.violation}" title="${ev.rule_id}" />`
  ).join("");

  $("resultCard").hidden = false;
  $("historyPanel").hidden = true;
}

document.querySelectorAll(".decisionbar .btn").forEach((btn) => {
  btn.addEventListener("click", async () => {
    if (!currentInspection) return;
    const decision = btn.dataset.decision;
    const payload = { decision, reason: $("decisionReason").value };
    const res = await fetch(`/inspections/${currentInspection.inspection_id}/decision`, {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
      body: JSON.stringify(payload),
    });
    if (!res.ok) return alert("Decision failed: " + (await res.text()));
    currentInspection = await res.json();
    renderInspection(currentInspection);
  });
});

$("historyBtn").addEventListener("click", async () => {
  const productId = currentInspection.product && currentInspection.product.id;
  $("historyPanel").hidden = true;
  if (!productId) return;
  const res = await fetch(`/products/${productId}/history`, { headers: { Authorization: `Bearer ${token}` } });
  if (!res.ok) return alert("History not available");
  const data = await res.json();
  $("historyText").textContent = JSON.stringify(data, null, 2);
  $("historyPanel").hidden = false;
});

$("reportBtn").addEventListener("click", async () => {
  if (!currentInspection) return;
  const res = await fetch(`/reports/${currentInspection.inspection_id}`, { headers: { Authorization: `Bearer ${token}` } });
  if (!res.ok) return alert("Report generation failed");
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `report_${currentInspection.inspection_id}.pdf`;
  a.click();
  URL.revokeObjectURL(url);
});

(async () => {
  if (!token) return setState(true);
  const res = await fetch("/auth/me", { headers: { Authorization: `Bearer ${token}` } });
  if (!res.ok) { localStorage.removeItem("lmcs_token"); return setState(true); }
  renderUser(await res.json());
  setState(false);
})();