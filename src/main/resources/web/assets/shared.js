export async function fetchJson(url, options = {}) {
    const response = await fetch(url, {
        ...options,
        headers: {
            "Content-Type": "application/json",
            ...(options.headers ?? {})
        }
    });

    let payload = null;
    try {
        payload = await response.json();
    } catch {
        payload = null;
    }

    if (!response.ok) {
        throw new Error(payload?.message ?? `Request failed with status ${response.status}`);
    }

    return payload;
}

export function showToast(message, tone = "info") {
    const stack = document.getElementById("toastStack");
    if (!stack) {
        return;
    }

    const toast = document.createElement("div");
    toast.className = "toast";
    toast.dataset.tone = tone;
    toast.textContent = message;
    stack.appendChild(toast);

    window.setTimeout(() => {
        toast.remove();
    }, 3200);
}

export function formatRelativeTime(epochMillis) {
    if (!epochMillis) {
        return "Not authenticated";
    }

    const date = new Date(epochMillis);
    return date.toLocaleString([], {
        hour: "2-digit",
        minute: "2-digit",
        month: "short",
        day: "numeric"
    });
}
