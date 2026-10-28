# Network Chat

A multi-group chat application with a single server (Node.js + WebSockets) and a web UI.

## Run

```bash
npm install
npm start
```

Open http://localhost:3000 in several browser tabs/windows (or from other devices on the same network using the server's IP).

## How it maps to the assignment

1. **Two clients through a server** – every client connects to the one server, which forwards each message.
2. **More than two clients** – any number of clients can connect concurrently; messages are broadcast to everyone in the sender's group.
3. **Multiple groups** – join a group at login, create/switch groups from the sidebar. The server broadcasts only to clients in the same group. Only one server runs (it also serves the web page).

## Protocol (JSON over WebSocket)

Client → server: `join {name, group}`, `switch {group}`, `message {text}`
Server → client: `joined`, `message`, `system`, `groups`, `members`, `error`
