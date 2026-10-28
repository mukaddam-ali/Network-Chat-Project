import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;

// برنامج العميل (على الـ cmd). بيتصل بالسيرفر عن طريق TCP (Socket).
// تشغيله:  java Client
public class Client {

    static Socket socket;
    static BufferedReader in;                       // منقرا منه اللي بيبعته السيرفر
    static PrintWriter out;                         // منكتب فيه اللي بنبعته للسيرفر
    static String current = null;                   // المجموعة اللي بنكتب فيها هلق
    static ArrayList<String> joined = new ArrayList<>();            // المجموعات اللي أنا فيها
    static String groupList = "";                                   // آخر لائحة مجموعات من السيرفر
    static HashMap<String, String> members = new HashMap<>();       // أعضاء كل مجموعة

    public static void main(String[] args) throws Exception {
        BufferedReader keyboard = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Server address (Enter = localhost): ");
        String host = keyboard.readLine().trim();
        if (host.isEmpty()) host = "localhost";

        socket = new Socket(host, 5000);            // هون بنتصل بالسيرفر (TCP)
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

        // منطلب الاسم لحد ما السيرفر يقبله
        while (true) {
            System.out.print("Your name: ");
            out.println("LOGIN|" + keyboard.readLine());
            String answer = in.readLine();
            if (answer.startsWith("WELCOME")) break;
            System.out.println(answer.split("\\|")[1]);   // مثلاً: الاسم مستخدم
        }

        // خيط ثاني: بيضل يقرا من السيرفر ويطبع، وإحنا منكتب بنفس الوقت
        Thread reader = new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    show(line);
                }
            } catch (IOException e) {
            }
            System.out.println("Disconnected from server.");
            System.exit(0);
        });
        reader.start();

        printHelp();

        // هون منقرا اللي بيكتبه المستخدم
        String text;
        while ((text = keyboard.readLine()) != null) {
            text = text.trim();
            if (text.isEmpty()) continue;

            if (text.startsWith("/")) {
                command(text);
            } else if (current == null) {
                System.out.println("You are not in a group. Use /join <group>");
            } else {
                out.println("MSG|" + current + "|" + text);   // رسالة عادية للمجموعة الحالية
            }
        }
    }

    // الأوامر اللي بتبلش بـ /
    static void command(String text) {
        String[] p = text.split(" ", 3);      // مثلاً:  /join Study 123
        String cmd = p[0];
        String arg1 = p.length > 1 ? p[1] : "";
        String arg2 = p.length > 2 ? p[2] : "";

        if (cmd.equals("/groups")) {
            printGroups();
        } else if (cmd.equals("/create")) {
            out.println("CREATE|" + arg1 + "|" + arg2);
        } else if (cmd.equals("/join")) {
            out.println("JOIN|" + arg1 + "|" + arg2);
        } else if (cmd.equals("/go")) {
            if (joined.contains(arg1)) {
                current = arg1;
                System.out.println("Now writing in: " + current);
            } else {
                System.out.println("You are not in that group.");
            }
        } else if (cmd.equals("/leave")) {
            out.println("LEAVE|" + (arg1.isEmpty() ? current : arg1));
        } else if (cmd.equals("/who")) {
            System.out.println("Online in " + current + ": " + members.get(current));
        } else if (cmd.equals("/help")) {
            printHelp();
        } else if (cmd.equals("/quit")) {
            System.exit(0);
        } else {
            System.out.println("Unknown command. Type /help");
        }
    }

    // اللي بيجي من السيرفر: منفهمه وبنطبعه
    static void show(String line) {
        String[] p = line.split("\\|", 5);
        String type = p[0];

        if (type.equals("ERROR")) {
            System.out.println("! " + p[1]);
        } else if (type.equals("JOINED")) {
            if (!joined.contains(p[1])) joined.add(p[1]);
            current = p[1];
            System.out.println("--- You are now in group: " + p[1] + " ---");
        } else if (type.equals("LEFT")) {
            joined.remove(p[1]);
            System.out.println("--- You left group: " + p[1] + " ---");
            if (p[1].equals(current)) {
                current = joined.isEmpty() ? null : joined.get(0);
                System.out.println("Now writing in: " + current);
            }
        } else if (type.equals("GROUPS")) {
            groupList = line;                 // منخزنها وبنطبعها لما يطلبها المستخدم
        } else if (type.equals("MEMBERS")) {
            members.put(p[1], p[2]);
        } else if (type.equals("MSG")) {
            System.out.println("[" + p[1] + "] " + p[2] + " (" + p[3] + "): " + p[4]);
        } else if (type.equals("SYS")) {
            System.out.println("[" + p[1] + "] * " + p[2]);
        }
    }

    static void printGroups() {
        String[] all = groupList.split("\\|");
        System.out.println("Groups:");
        for (int i = 1; i < all.length; i++) {
            String[] g = all[i].split(",");       // اسم,عدد,مقفلة
            System.out.println("  " + g[0] + "  (" + g[1] + " online)" + (g[2].equals("1") ? "  [password]" : "")
                    + (joined.contains(g[0]) ? "  <- you are in" : ""));
        }
    }

    static void printHelp() {
        System.out.println("Commands:");
        System.out.println("  /groups                  show all groups");
        System.out.println("  /create <name> [pass]    make a new group (password is optional)");
        System.out.println("  /join <name> [pass]      join a group");
        System.out.println("  /go <name>               choose which of your groups you write in");
        System.out.println("  /who                     who is online in the current group");
        System.out.println("  /leave [name]            leave a group");
        System.out.println("  /quit                    close");
        System.out.println("Just type text to send a message to the current group.");
    }
}
