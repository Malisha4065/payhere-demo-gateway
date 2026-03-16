const form = document.getElementById("checkout-form");
const eventsBox = document.getElementById("events");
const refreshBtn = document.getElementById("refresh");

async function loadEvents() {
  try {
    const res = await fetch("/api/payhere/events", { cache: "no-store" });
    if (!res.ok) {
      eventsBox.textContent = `Events endpoint error: ${res.status}`;
      return;
    }
    const data = await res.json();
    eventsBox.textContent = JSON.stringify(data, null, 2);
  } catch (err) {
    eventsBox.textContent = "Unable to fetch events. Is the server running?";
  }
}

form.addEventListener("submit", (e) => {
  e.preventDefault();
  const params = new URLSearchParams();
  const orderId = form.orderId.value.trim();
  const amount = form.amount.value.trim();
  const currency = form.currency.value.trim();

  if (orderId) params.set("orderId", orderId);
  if (amount) params.set("amount", amount);
  if (currency) params.set("currency", currency);

  const url = `/api/payhere/checkout${params.toString() ? `?${params}` : ""}`;
  window.location.href = url;
});

refreshBtn.addEventListener("click", loadEvents);
loadEvents();
