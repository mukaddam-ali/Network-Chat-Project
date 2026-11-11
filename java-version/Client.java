import java.io.*;
import java.net.*;

// Client (runs in CMD). Connects to the server, sends its name and group,
// then everything you type is sent to everyone in your group.
public class Client {

    public static void main(String[] args) throws IOException {
        BufferedReader keyboard = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Server address (Enter = localhost): ");
        String host = keyboard.readLine().trim();
        if (host.isEmpty()) host = "localhost";
        System.out.print("Your name: ");
        String name = keyboard.readLine().trim();
        System.out.print("Group name: ");
        String group = keyboard.readLine().trim();

        Socket socket = new Socket(host, 5000);   // connect to the server (TCP)
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

        out.println(name);    // tell the server who we are
        out.println(group);   // and which group we want

        // thread that keeps reading messages from the server and prints them
        new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    System.out.println(line);
                }
            } catch (IOException e) {
            }
            System.out.println("Disconnected.");
            System.exit(0);
        }).start();

        // main thread: send what the user types (/quit to exit)
        String text;
        while ((text = keyboard.readLine()) != null) {
            if (text.equals("/quit")) break;
            if (!text.trim().isEmpty()) out.println(text);
        }
        socket.close();
    }
}
