import java.io.*;
import java.net.Socket;
import java.util.ArrayList;

// كل عميل بيتصل بالسيرفر بنعمله ClientHandler
// هو thread (خيط) خاص فيه، يعني كل عميل بيشتغل لحاله وما بيوقف غيره
public class ClientHandler extends Thread {

    Socket socket;
    BufferedReader in;   // منقرا منه اللي بيبعته العميل
    PrintWriter out;     // منكتب فيه اللي بدنا نبعته للعميل

    String name;                                  // اسم العميل (فاضي لحد ما يسجل دخول)
    ArrayList<Group> groups = new ArrayList<>();  // المجموعات اللي هو فيها

    ClientHandler(Socket socket) throws IOException {
        this.socket = socket;
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
    }

    // بعت سطر للعميل
    synchronized void send(String line) {
        out.println(line);
    }

    // هاي بتشتغل أول ما نعمل start()
    public void run() {
        try {
            String line;
            // منضل نقرا سطر سطر لحد ما العميل يسكّر
            while ((line = in.readLine()) != null) {
                handle(line);
            }
        } catch (IOException e) {
            // العميل قطع الاتصال، عادي
        }
        // العميل راح: منشيله من كل المجموعات
        Server.removeClient(this);
        try {
            socket.close();
        } catch (IOException e) {
        }
    }

    // منفهم شو بده العميل وبننادي الدالة المناسبة بالسيرفر
    // شكل الرسالة: النوع|كلمة|كلمة
    void handle(String line) {
        String[] p = line.split("\\|", 3);
        String type = p[0];
        String second = p.length > 1 ? p[1] : "";
        String third = p.length > 2 ? p[2] : "";

        if (type.equals("LOGIN")) {
            Server.login(this, second);
        } else if (name == null) {
            return;   // لسا ما سجل دخول، منتجاهل
        } else if (type.equals("CREATE")) {
            Server.createGroup(this, second, third);
        } else if (type.equals("JOIN")) {
            Server.joinGroup(this, second, third);
        } else if (type.equals("LEAVE")) {
            Server.leaveGroup(this, second);
        } else if (type.equals("MSG")) {
            Server.sendMessage(this, second, third);
        }
    }
}
