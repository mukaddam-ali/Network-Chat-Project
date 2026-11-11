import java.io.*;
import java.net.*;

// Client (CMD). Connects to the server; what you type goes to everyone in your group.
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

        Socket socket = new Socket(host, 5000);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

        out.println(name);
        out.println(group);

        // thread that keeps reading from the server and printing
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

        // what you type is sent to the server
        String text;
        while ((text = keyboard.readLine()) != null) {
            if (text.equals("/quit")) break;
            if (!text.trim().isEmpty()) out.println(text);
        }
        socket.close();
    }
}
