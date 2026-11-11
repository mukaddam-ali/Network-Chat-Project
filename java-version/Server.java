import java.io.*;
import java.net.*;
import java.util.*;

// Server: runs once, all clients connect to it over TCP.
// When a client sends a message, the server forwards it to all clients in the same group.
public class Server {

    static List<Handler> clients = new ArrayList<>();   // all connected clients

    public static void main(String[] args) throws IOException {
        ServerSocket server = new ServerSocket(5000);
        System.out.println("Server running on port 5000");
        while (true) {
            Socket socket = server.accept();            // wait for a new client
            new Handler(socket).start();                // one thread per client
        }
    }

    // send a message to everyone in the group
    static synchronized void broadcast(String group, String message) {
        for (Handler c : clients) {
            if (c.group.equals(group)) {
                c.out.println(message);
            }
        }
    }

    static synchronized void add(Handler c) { clients.add(c); }
    static synchronized void remove(Handler c) { clients.remove(c); }

    // each client runs in its own thread
    static class Handler extends Thread {
        Socket socket;
        BufferedReader in;
        PrintWriter out;
        String name, group;

        Handler(Socket socket) throws IOException {
            this.socket = socket;
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
        }

        public void run() {
            try {
                name = in.readLine();      // first line: name
                group = in.readLine();     // second line: group
                add(this);
                broadcast(group, "*** " + name + " joined " + group);
                String line;
                while ((line = in.readLine()) != null) {
                    broadcast(group, name + ": " + line);
                }
            } catch (IOException e) {
            }
            if (group != null) {
                remove(this);
                broadcast(group, "*** " + name + " left");
            }
        }
    }
}
