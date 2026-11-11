import java.io.*;
import java.net.*;

// العميل (على CMD). بيتصل بالسيرفر، وبتكتب رسالة بتوصل لكل اللي بمجموعتك.
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

        // خيط بيضل يقرا اللي بيجي من السيرفر ويطبعه
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

        // اللي بتكتبه بيروح للسيرفر
        String text;
        while ((text = keyboard.readLine()) != null) {
            if (text.equals("/quit")) break;
            if (!text.trim().isEmpty()) out.println(text);
        }
        socket.close();
    }
}
