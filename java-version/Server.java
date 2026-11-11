import java.io.*;
import java.net.*;
import java.util.*;

// السيرفر: بيشتغل مرة وحدة، وكل العملاء بيتصلوا فيه عن طريق TCP.
// لما عميل بيبعت رسالة، السيرفر بيبعتها لكل العملاء اللي بنفس المجموعة.
public class Server {

    static List<Handler> clients = new ArrayList<>();   // كل العملاء المتصلين

    public static void main(String[] args) throws IOException {
        ServerSocket server = new ServerSocket(5000);
        System.out.println("Server running on port 5000");
        while (true) {
            Socket socket = server.accept();            // استنى عميل جديد
            new Handler(socket).start();                // خيط (thread) لكل عميل
        }
    }

    // ابعت رسالة لكل اللي بنفس المجموعة
    static synchronized void broadcast(String group, String message) {
        for (Handler c : clients) {
            if (c.group.equals(group)) {
                c.out.println(message);
            }
        }
    }

    static synchronized void add(Handler c) { clients.add(c); }
    static synchronized void remove(Handler c) { clients.remove(c); }

    // كل عميل بيشتغل بخيط لحاله
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
                name = in.readLine();      // أول سطر: الاسم
                group = in.readLine();     // تاني سطر: المجموعة
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
