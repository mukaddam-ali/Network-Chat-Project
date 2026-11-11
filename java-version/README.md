# Network Chat (Java, TCP, console)

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

Press Enter for `localhost` (or type the server PC's IP if you are on another PC), then type a name.

Commands inside the client: `/groups`, `/create <name> [password]`, `/join <name> [password]`, `/go <name>`, `/who`, `/leave [name]`, `/quit`, `/help`. Any other text is sent as a message to your current group.

## Files

- `Server.java` – the one server. Accepts clients, keeps the groups, sends each message only to the members of its group.
- `ClientHandler.java` – one thread per connected client, reads what that client sends.
- `Group.java` – a group: name, optional password, members, last messages.
- `Client.java` – the console program the user types in.

## How it covers the assignment (COSC 2327)

- Step 1 (two clients through one server): every client connects over TCP to the single `Server`, which forwards each message to the other clients.
- Step 2 (more than two clients): the server starts one thread per client (`ClientHandler`), so any number of clients chat at once; everyone is in the `General` group by default and receives every message sent there.
- Step 3 (multiple groups): `/create` and `/join` make and enter groups. `Server.sendMessage` sends a message only to the members of that group; clients in other groups never receive it. Still only one server running.
- Step 4: upload the `.java` files in this folder to Blackboard before the demo.
- Step 5: demo in the instructor's office on or before Dec 1, 2026 (start `java Server` once, then 3+ `java Client` windows).

Quick demo: open 3 clients (Ali, Sara, Omar). Ali: `/create Study`. Sara: `/join Study`, then type a message. Omar stays in General and does not see it.
