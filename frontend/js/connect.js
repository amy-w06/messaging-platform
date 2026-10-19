// connects to the WebSocket server and drives the messenger-style chat UI.
// sent messages appear as bubbles on the right, received on the left — similar
// to iMessage or WhatsApp rather than a Discord-style room layout.
// each conversation (general, DMs, group chats) has its own entry in the left
// panel and stores its own message history independently.

const ws = new WebSocket("ws://localhost:5002");

let username = "";
let currentChannel = "general";
let lastAuthor = "";
let lastTimestamp = 0;
const GROUP_THRESHOLD_MS = 3 * 60 * 1000; // group bubbles within 3 minutes

const channelMessages = { general: [] };

const COMMANDS = [
    { cmd: "/dm",           args: "<username> <message>",           desc: "Send a direct message" },
    { cmd: "/createGC",     args: "<groupname> [user1] [user2]...", desc: "Create a group chat" },
    { cmd: "/gc",           args: "<groupname> <message>",          desc: "Send to a group chat" },
    { cmd: "/joinGC",       args: "<groupname>",                    desc: "Join an existing group" },
    { cmd: "/addFriend",    args: "<username>",                     desc: "Send a friend request" },
    { cmd: "/acceptFriend", args: "<username>",                     desc: "Accept a friend request" },
];

const USER_COLOURS = [
    "#3d5a80","#9333ea","#16a34a","#dc2626","#d97706","#0891b2","#be185d","#065f46"
];
const userColourMap = {};

function getUserColour(name) {
    if (!userColourMap[name]) {
        userColourMap[name] = USER_COLOURS[Object.keys(userColourMap).length % USER_COLOURS.length];
    }
    return userColourMap[name];
}

function getInitial(name) { return name ? name.charAt(0).toUpperCase() : "?"; }

function formatTime(date) {
    return date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}

function scrollToBottom() {
    const c = document.getElementById("messages-container");
    c.scrollTop = c.scrollHeight;
}

function escapeHtml(str) {
    return String(str)
        .replace(/&/g,"&amp;").replace(/</g,"&lt;")
        .replace(/>/g,"&gt;").replace(/"/g,"&quot;");
}

// re-renders all stored messages for the current channel
function renderChannel() {
    const list = document.getElementById("messages-list");
    list.innerHTML = "";
    lastAuthor = "";
    lastTimestamp = 0;
    (channelMessages[currentChannel] || []).forEach(m => {
        if (m.type === "chat")   _renderBubble(m.author, m.text, m.time, false);
        if (m.type === "dm")     _renderDmBubble(m.label, m.text, false);
        if (m.type === "gc")     _renderGcBubble(m.label, m.text, false);
        if (m.type === "system") _renderSystem(m.text, false);
    });
    scrollToBottom();
}

// renders a chat bubble — sent messages go right, received go left.
// consecutive messages from the same person within GROUP_THRESHOLD_MS are grouped
// so only the first one shows the author name above it.
function _renderBubble(author, text, time, store) {
    if (store) {
        if (!channelMessages[currentChannel]) channelMessages[currentChannel] = [];
        channelMessages[currentChannel].push({ type:"chat", author, text, time });
    }

    const list = document.getElementById("messages-list");
    const now = time || new Date();
    const isSent = author === username;
    const isContinued = author === lastAuthor && (now - lastTimestamp) < GROUP_THRESHOLD_MS;
    lastAuthor = author;
    lastTimestamp = now;

    const row = document.createElement("div");
    row.className = "bubble-row " + (isSent ? "sent" : "recv");

    // only show the author name on the first bubble of a group
    if (!isContinued && !isSent) {
        const authorEl = document.createElement("div");
        authorEl.className = "bubble-author";
        authorEl.style.color = getUserColour(author);
        authorEl.textContent = author;
        row.appendChild(authorEl);
    }

    const bubble = document.createElement("div");
    bubble.className = "bubble";
    bubble.textContent = text;
    row.appendChild(bubble);

    // only show the timestamp on the last bubble — we update this when new ones arrive
    const timeEl = document.createElement("div");
    timeEl.className = "bubble-time";
    timeEl.textContent = formatTime(now);
    row.appendChild(timeEl);

    list.appendChild(row);
    updateConvoPreview(currentChannel, (isSent ? "You: " : author + ": ") + text);
    scrollToBottom();
}

// purple left-border bubble for incoming/outgoing DMs
function _renderDmBubble(label, text, store) {
    if (store) {
        if (!channelMessages[currentChannel]) channelMessages[currentChannel] = [];
        channelMessages[currentChannel].push({ type:"dm", label, text });
    }
    lastAuthor = "";
    const list = document.getElementById("messages-list");
    const el = document.createElement("div");
    el.className = "bubble-dm";
    el.innerHTML = `<div class="bubble-label">${escapeHtml(label)}</div><div class="bubble-body">${escapeHtml(text)}</div>`;
    list.appendChild(el);
    scrollToBottom();
}

// green left-border bubble for group chat messages
function _renderGcBubble(label, text, store) {
    if (store) {
        if (!channelMessages[currentChannel]) channelMessages[currentChannel] = [];
        channelMessages[currentChannel].push({ type:"gc", label, text });
    }
    lastAuthor = "";
    const list = document.getElementById("messages-list");
    const el = document.createElement("div");
    el.className = "bubble-gc";
    el.innerHTML = `<div class="bubble-label">${escapeHtml(label)}</div><div class="bubble-body">${escapeHtml(text)}</div>`;
    list.appendChild(el);
    scrollToBottom();
}

function _renderSystem(text, store) {
    if (store) {
        if (!channelMessages[currentChannel]) channelMessages[currentChannel] = [];
        channelMessages[currentChannel].push({ type:"system", text });
    }
    lastAuthor = "";
    const list = document.getElementById("messages-list");
    const el = document.createElement("div");
    el.className = "message-system";
    el.textContent = text;
    list.appendChild(el);
    scrollToBottom();
}

function appendBubble(author, text, time)  { _renderBubble(author, text, time, true); }
function appendDmBubble(label, text)       { _renderDmBubble(label, text, true); }
function appendGcBubble(label, text)       { _renderGcBubble(label, text, true); }
function appendSystem(text)                { _renderSystem(text, true); }

// updates the small preview line under a conversation in the left panel
function updateConvoPreview(channelKey, text) {
    const preview = document.getElementById("preview-" + channelKey);
    if (preview) preview.textContent = text.length > 40 ? text.slice(0,40) + "…" : text;
}

// switches the right panel to show a different conversation
function switchChannel(name) {
    currentChannel = name;
    if (!channelMessages[name]) channelMessages[name] = [];

    document.querySelectorAll(".convo-item").forEach(el => el.classList.remove("active"));
    const target = document.querySelector(`[data-channel="${CSS.escape(name)}"]`);
    if (target) {
        target.classList.add("active");
        // remove unread badge when we open the conversation
        const badge = target.querySelector(".convo-unread");
        if (badge) badge.remove();
    }

    const isDm = name.startsWith("dm-");
    const isGc = name.startsWith("gc-");
    const displayName = isDm ? name.slice(3) : isGc ? name.slice(3) : name;

    document.getElementById("chat-header-name").textContent = displayName;
    document.getElementById("chat-header-sub").textContent  = isDm ? "Direct message" : isGc ? "Group chat" : "Group chat";
    document.getElementById("chat-header-avatar").textContent = getInitial(displayName);
    document.getElementById("chat-header-avatar").style.background =
        isDm ? "#9333ea" : isGc ? "#16a34a" : "#3d5a80";
    document.getElementById("message-input").placeholder = `Message ${isDm ? "@" : "#"}${displayName}...`;

    document.getElementById("command-helper").classList.add("hidden");
    renderChannel();
}

// adds a DM entry to the left panel if not already there
function ensureDmChannel(otherUser) {
    const key = "dm-" + otherUser;
    if (document.querySelector(`[data-channel="${CSS.escape(key)}"]`)) return;
    const list = document.getElementById("conversation-list");
    const item = document.createElement("div");
    item.className = "convo-item";
    item.dataset.channel = key;
    item.onclick = () => switchChannel(key);
    item.innerHTML = `
        <div class="convo-avatar convo-avatar-dm">${getInitial(otherUser)}</div>
        <div class="convo-info">
            <div class="convo-name">${escapeHtml(otherUser)}</div>
            <div class="convo-preview" id="preview-${CSS.escape(key)}">No messages yet</div>
        </div>
    `;
    list.appendChild(item);
    if (!channelMessages[key]) channelMessages[key] = [];
}

// adds a group chat entry to the left panel if not already there
function ensureGcChannel(groupName) {
    const key = "gc-" + groupName;
    if (document.querySelector(`[data-channel="${CSS.escape(key)}"]`)) return;
    const list = document.getElementById("conversation-list");
    const item = document.createElement("div");
    item.className = "convo-item";
    item.dataset.channel = key;
    item.onclick = () => switchChannel(key);
    item.innerHTML = `
        <div class="convo-avatar convo-avatar-gc">${getInitial(groupName)}</div>
        <div class="convo-info">
            <div class="convo-name">${escapeHtml(groupName)}</div>
            <div class="convo-preview" id="preview-${CSS.escape(key)}">Group chat</div>
        </div>
    `;
    list.appendChild(item);
    if (!channelMessages[key]) channelMessages[key] = [];
}

// bumps the unread count badge on a conversation when a message arrives
// while the user is looking at a different one
function bumpUnread(channelKey) {
    const item = document.querySelector(`[data-channel="${CSS.escape(channelKey)}"]`);
    if (!item) return;
    let badge = item.querySelector(".convo-unread");
    if (!badge) {
        badge = document.createElement("div");
        badge.className = "convo-unread";
        badge.textContent = "1";
        item.appendChild(badge);
    } else {
        badge.textContent = parseInt(badge.textContent || "0") + 1;
    }
}

function showToast(title, body, actions) {
    const container = document.getElementById("toast-container");
    const toast = document.createElement("div");
    toast.className = "toast";
    const actionsHtml = (actions || []).map(a =>
        `<button class="toast-btn ${a.cls}" onclick="${a.onclick}">${escapeHtml(a.label)}</button>`
    ).join("");
    toast.innerHTML = `
        <div class="toast-title">${escapeHtml(title)}</div>
        <div class="toast-body">${escapeHtml(body)}</div>
        ${actionsHtml ? `<div class="toast-actions">${actionsHtml}</div>` : ""}
    `;
    container.appendChild(toast);
    setTimeout(() => {
        toast.style.animation = "fadeOut 0.2s ease forwards";
        setTimeout(() => toast.remove(), 200);
    }, actions && actions.length ? 12000 : 4000);
}

// fills the input with a command stub when a chip button is clicked
function fillCommand(cmd) {
    const input = document.getElementById("message-input");
    input.value = cmd;
    input.focus();
    document.getElementById("command-helper").classList.add("hidden");
    updateCommandPicker();
}

function showCommandHelper() {
    document.getElementById("command-helper").classList.toggle("hidden");
}

// online count (just tracks members we've seen message, not a proper presence system)
const onlineMembers = new Set();

function addMember(name) {
    onlineMembers.add(name);
    document.getElementById("online-count").textContent = onlineMembers.size;
}

function removeMember(name) {
    onlineMembers.delete(name);
    document.getElementById("online-count").textContent = onlineMembers.size;
}

// slash command autocomplete picker
let selectedCommandIdx = 0;

function updateCommandPicker() {
    const picker = document.getElementById("command-picker");
    const list   = document.getElementById("command-list");
    const val    = document.getElementById("message-input").value;

    if (!val.startsWith("/")) { picker.classList.add("hidden"); return; }

    const fragment = val.split(" ")[0].toLowerCase();
    const matches  = COMMANDS.filter(c => c.cmd.toLowerCase().startsWith(fragment));

    if (matches.length === 0) { picker.classList.add("hidden"); return; }

    list.innerHTML = "";
    matches.forEach((c, i) => {
        const item = document.createElement("div");
        item.className = "command-item" + (i === selectedCommandIdx ? " selected" : "");
        item.innerHTML = `
            <span class="command-name">${escapeHtml(c.cmd)} <span class="command-args">${escapeHtml(c.args)}</span></span>
            <span class="command-desc">${escapeHtml(c.desc)}</span>
        `;
        item.onclick = () => {
            document.getElementById("message-input").value = c.cmd + " ";
            picker.classList.add("hidden");
            document.getElementById("message-input").focus();
        };
        list.appendChild(item);
    });

    picker.classList.remove("hidden");
}

function sendMessage() {
    const input = document.getElementById("message-input");
    const text  = input.value.trim();
    if (!text || !username) return;

    document.getElementById("command-picker").classList.add("hidden");
    ws.send(text);

    if (text.startsWith("/dm ")) {
        const parts = text.split(" ");
        const target = parts[1];
        const msg    = parts.slice(2).join(" ");
        if (target && msg) {
            ensureDmChannel(target);
            const prev = currentChannel;
            currentChannel = "dm-" + target;
            appendDmBubble("DM to " + target, msg);
            updateConvoPreview("dm-" + target, "You: " + msg);
            currentChannel = prev;
        }
    } else if (text.startsWith("/gc ")) {
        const parts = text.split(" ");
        const group = parts[1];
        const msg   = parts.slice(2).join(" ");
        if (group && msg) {
            ensureGcChannel(group);
            const prev = currentChannel;
            currentChannel = "gc-" + group;
            appendGcBubble(group + " — " + username, msg);
            updateConvoPreview("gc-" + group, "You: " + msg);
            currentChannel = prev;
        }
    } else if (text.startsWith("/createGC ")) {
        const group = text.split(" ")[1];
        if (group) ensureGcChannel(group);
        appendSystem("Creating group '" + (group || "") + "'...");
    } else if (text.startsWith("/addFriend ")) {
        appendSystem("Friend request sent to " + text.split(" ")[1] + ".");
    } else if (text.startsWith("/acceptFriend ")) {
        appendSystem("Accepted friend request from " + text.split(" ")[1] + ".");
    } else if (!text.startsWith("/")) {
        appendBubble(username, text, new Date());
    }

    input.value = "";
    input.style.height = "auto";
}

// username modal
document.getElementById("username-submit").addEventListener("click", () => {
    const input = document.getElementById("username-input");
    const name  = input.value.trim();
    if (!name) return;
    username = name;
    ws.send(username);
});

document.getElementById("username-input").addEventListener("keydown", e => {
    if (e.key === "Enter") document.getElementById("username-submit").click();
});

document.getElementById("message-input").addEventListener("keydown", e => {
    const picker = document.getElementById("command-picker");

    if (!picker.classList.contains("hidden")) {
        const items = picker.querySelectorAll(".command-item");
        if (e.key === "ArrowDown") { e.preventDefault(); selectedCommandIdx = Math.min(selectedCommandIdx + 1, items.length - 1); updateCommandPicker(); return; }
        if (e.key === "ArrowUp")   { e.preventDefault(); selectedCommandIdx = Math.max(selectedCommandIdx - 1, 0); updateCommandPicker(); return; }
        if (e.key === "Tab" || (e.key === "Enter" && !e.shiftKey)) { e.preventDefault(); picker.querySelector(".command-item.selected")?.click(); return; }
        if (e.key === "Escape") { picker.classList.add("hidden"); return; }
    }

    if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); sendMessage(); }
});

document.getElementById("message-input").addEventListener("input", function() {
    this.style.height = "auto";
    this.style.height = Math.min(this.scrollHeight, 120) + "px";
    selectedCommandIdx = 0;
    updateCommandPicker();
    if (!this.value) document.getElementById("command-picker").classList.add("hidden");
});

document.getElementById("send-btn").addEventListener("click", sendMessage);

// search box filters the conversation list by name
document.getElementById("search-input").addEventListener("input", function() {
    const q = this.value.toLowerCase();
    document.querySelectorAll(".convo-item").forEach(item => {
        const name = item.querySelector(".convo-name").textContent.toLowerCase();
        item.style.display = name.includes(q) ? "" : "none";
    });
});

// websocket event handlers

ws.onopen = () => console.log("Connected");

ws.onmessage = (event) => {
    const data = event.data;

    if (data === "What is your username?") return;

    if (data.startsWith("Welcome, ")) {
        document.getElementById("username-modal").classList.add("hidden");
        document.getElementById("app").classList.remove("hidden");
        document.getElementById("user-badge-name").textContent = username;
        const avatar = document.getElementById("user-avatar-badge");
        avatar.style.background = getUserColour(username);
        avatar.textContent = getInitial(username);
        addMember(username);
        document.getElementById("message-input").focus();
        return;
    }

    if (data.endsWith(" has left the chat")) {
        removeMember(data.replace(" has left the chat", ""));
        appendSystem(data);
        return;
    }

    // incoming DM — store in the DM conversation and show a toast if elsewhere
    if (data.startsWith("DM from ")) {
        const rest     = data.slice(8);
        const colonIdx = rest.indexOf(": ");
        if (colonIdx !== -1) {
            const sender = rest.slice(0, colonIdx);
            const msg    = rest.slice(colonIdx + 2);
            ensureDmChannel(sender);
            const key  = "dm-" + sender;
            const prev = currentChannel;
            currentChannel = key;
            appendDmBubble("DM from " + sender, msg);
            updateConvoPreview(key, sender + ": " + msg);
            currentChannel = prev;
            if (prev !== key) { bumpUnread(key); showToast("DM from " + sender, msg); }
        }
        return;
    }

    if (data.startsWith("DM to ")) return; // already rendered when sent

    // incoming group chat message
    if (data.startsWith("[Group: ")) {
        const close = data.indexOf("]");
        if (close !== -1) {
            const groupName = data.slice(8, close);
            const rest      = data.slice(close + 2);
            const parts     = rest.split(": ");
            const author    = parts[0];
            const msg       = parts.slice(1).join(": ");
            ensureGcChannel(groupName);
            const key  = "gc-" + groupName;
            const prev = currentChannel;
            currentChannel = key;
            appendGcBubble(groupName + " — " + author, msg);
            updateConvoPreview(key, author + ": " + msg);
            currentChannel = prev;
            if (prev !== key) { bumpUnread(key); showToast("#" + groupName, author + ": " + msg); }
        }
        return;
    }

    if (data.startsWith("You joined group '")) {
        const name = data.slice(18, data.indexOf("'", 18));
        ensureGcChannel(name);
        appendSystem(data);
        return;
    }

    // regular chat message from another user in "Author: text" format
    if (data.includes(": ")) {
        const colonIdx = data.indexOf(": ");
        const author   = data.slice(0, colonIdx);
        const text     = data.slice(colonIdx + 2);
        if (author !== username) {
            addMember(author);
            if (currentChannel === "general") {
                appendBubble(author, text, new Date());
            } else {
                const prev = currentChannel;
                currentChannel = "general";
                appendBubble(author, text, new Date());
                updateConvoPreview("general", author + ": " + text);
                currentChannel = prev;
                bumpUnread("general");
                showToast(author, text);
            }
        }
        return;
    }

    appendSystem(data);
};

ws.onclose = () => appendSystem("Disconnected from server.");
ws.onerror = () => appendSystem("Connection error.");