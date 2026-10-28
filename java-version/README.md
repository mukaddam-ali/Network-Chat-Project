# Network Chat (Java, TCP)

Chat with groups. One server, many clients. TCP sockets (`ServerSocket` / `Socket`).

## Run (Windows cmd)

Open a cmd window inside this folder.

```
javac -encoding UTF-8 *.java
java Server
```

Leave that window open. Then open more cmd windows (one per user) in the same folder:

```
java Client
```

Type `localhost` as the server address (or the server PC's IP if you are on another PC), then a name.

## Files

- `Server.java` – the one server. Accepts clients, keeps the groups, sends each message only to the members of its group.
- `ClientHandler.java` – one thread per connected client, reads what that client sends.
- `Group.java` – a group: name, optional password, members, last messages.
- `Client.java` – the window (Swing) the user sees.
