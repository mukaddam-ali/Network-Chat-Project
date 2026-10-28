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
3. **Multiple groups** – every group is listed for everyone (🔒 = password protected). Clicking a protected group asks for its password; only members can see its messages. A client can be in several groups at once and switch between them. The server broadcasts only to members of the message's group. Only one server runs (it also serves the web page). `General` is public and always exists.

## Protocol (JSON over WebSocket)

Client → server: `login {name}`, `create {group, password?}`, `join {group, password?}`, `leave {group}`, `message {group, text}`
Server → client: `welcome`, `joined {history}`, `left`, `message`, `system`, `groups`, `members`, `error`
