import { fetchJson, showToast } from "/assets/shared.js";

const form = document.getElementById("setupForm");
const tokenInput = document.getElementById("tokenInput");
const passwordInput = document.getElementById("passwordInput");
const statusNode = document.getElementById("setupStatus");

const tokenFromQuery = new URL(window.location.href).searchParams.get("token");
if (tokenFromQuery) {
    tokenInput.value = tokenFromQuery;
    statusNode.textContent = "Setup token loaded from the URL printed in the Minecraft console.";
}

form.addEventListener("submit", async event => {
    event.preventDefault();
    statusNode.textContent = "Creating admin password…";

    try {
        await fetchJson("/api/setup", {
            method: "POST",
            body: JSON.stringify({
                token: tokenInput.value.trim(),
                password: passwordInput.value
            })
        });

        statusNode.textContent = "Setup complete. Opening console…";
        window.location.replace("/");
    } catch (error) {
        statusNode.textContent = error.message;
        showToast(error.message, "error");
    }
});
