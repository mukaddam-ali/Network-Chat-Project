import java.io.*;
import java.net.*;
import java.util.*;

// Server: runs once. Every client connects to it over TCP.
// When a client sends a message, the server forwards it
// only to the clients that are in the same group.
public class Server {

    // all connected clients
    static List<Handler> clients = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        ServerSocket server = new ServerSocket(5000);
        System.out.println("Server running on port 5000");
        while (true) {
            Socket socket = server.accept();   // wait for a new client
            new Handler(socket).start();       // one thread per client
        }
    }

    // send a message to every client in the given group
    static synchronized void broadcast(String group, String message) {
        for (Handler c : clients) {
            if (c.group.equals(group)) {
                c.out.println(message);
            }
        }
    }

    static synchronized void add(Handler c) { clients.add(c); }
    static synchronized void remove(Handler c) { clients.remove(c); }

    // Handler: one per client, runs in its own thread
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
                name = in.readLine();    // first line from client: name
                group = in.readLine();   // second line from client: group
                if (name == null || group == null) {
                    socket.close();      // client left before sending its info
                    return;
                }
                add(this);
                broadcast(group, "*** " + name + " joined " + group);

                String line;
                while ((line = in.readLine()) != null) {
                    broadcast(group, name + ": " + line);
                }
            } catch (IOException e) {
                // client disconnected
            }
            if (group != null) {
                remove(this);
                broadcast(group, "*** " + name + " left");
            }
        }
    }
}
