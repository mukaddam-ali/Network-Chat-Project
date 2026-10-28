import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;

// السيرفر. بيشتغل مرة وحدة بس، وكل العملاء بيتصلوا فيه.
// بيستخدم TCP (ServerSocket و Socket).
//
// الفكرة: السيرفر عنده لائحة مجموعات. لما حدا يبعت رسالة،
// السيرفر بيبعتها بس لأعضاء نفس المجموعة.
//
// الرسائل بين السيرفر والعميل سطر نص، والكلمات بينها |
// من العميل:  LOGIN|اسم   CREATE|مجموعة|باسورد   JOIN|مجموعة|باسورد   LEAVE|مجموعة   MSG|مجموعة|نص
public class Server {

    static final int PORT = 5000;
    static final int MAX_HISTORY = 100;   // كم رسالة قديمة بيشوفها العضو الجديد

    // كل المجموعات (المفتاح هو اسم المجموعة)
    static LinkedHashMap<String, Group> groups = new LinkedHashMap<>();
    // كل العملاء اللي سجلوا دخول
    static ArrayList<ClientHandler> clients = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        // مجموعة General موجودة دايماً
        groups.put("General", new Group("General", ""));

        ServerSocket serverSocket = new ServerSocket(PORT);   // بنفتح البورت
        System.out.println("Server is running on port " + PORT);

        // منضل نستنى عملاء جداد
        while (true) {
            Socket socket = serverSocket.accept();            // بيستنى لحد ما حدا يتصل
            ClientHandler handler = new ClientHandler(socket);
            handler.start();                                  // كل عميل بخيط لحاله
        }
    }

    // ---------- دوال صغيرة بتساعدنا ----------

    // منشيل الرموز اللي بتخرب الرسالة، وبنقص الطول
    static String clean(String text, int maxLength) {
        text = text.replace("|", "").replace(",", "").replaceAll("\\s+", " ").trim();
        if (text.length() > maxLength) {
            text = text.substring(0, maxLength);
        }
        return text;
    }

    // دور على مجموعة باسمها (بدون ما يفرق كبير وصغير)
    static Group findGroup(String name) {
        for (Group g : groups.values()) {
            if (g.name.equalsIgnoreCase(name)) {
                return g;
            }
        }
        return null;
    }

    // ابعت رسالة لكل أعضاء مجموعة
    static void sendToGroup(Group g, String line) {
        for (ClientHandler member : g.members) {
            member.send(line);
        }
    }

    // ابعت لكل الناس لائحة المجموعات (اسم,عدد الأعضاء,هل عليها باسورد)
    static void sendGroupList() {
        String line = "GROUPS";
        for (Group g : groups.values()) {
            line += "|" + g.name + "," + g.members.size() + "," + (g.isLocked() ? "1" : "0");
        }
        for (ClientHandler c : clients) {
            c.send(line);
        }
    }

    // ابعت لأعضاء المجموعة أسماء المتواجدين فيها
    static void sendMembers(Group g) {
        String names = "";
        for (ClientHandler m : g.members) {
            names += (names.isEmpty() ? "" : ",") + m.name;
        }
        sendToGroup(g, "MEMBERS|" + g.name + "|" + names);
    }

    // رسالة نظام مثل "Ali joined the group"
    static void addSystemMessage(Group g, String text) {
        String line = "SYS|" + g.name + "|" + text;
        g.history.add(line);
        sendToGroup(g, line);
    }

    // ---------- الأوامر اللي بتجي من العملاء ----------
    // كلها synchronized: يعني خيط واحد بس بيدخل بنفس الوقت، عشان ما تخرب البيانات

    static synchronized void login(ClientHandler c, String name) {
        name = clean(name, 20);
        if (name.isEmpty()) {
            c.send("ERROR|Name required");
            return;
        }
        for (ClientHandler other : clients) {
            if (other.name.equalsIgnoreCase(name)) {
                c.send("ERROR|That name is already in use");
                return;
            }
        }
        c.name = name;
        clients.add(c);
        c.send("WELCOME|" + name);
        enterGroup(c, groups.get("General"));   // أول شي بيدخل General
        sendGroupList();
    }

    static synchronized void createGroup(ClientHandler c, String name, String password) {
        name = clean(name, 24);
        if (name.isEmpty()) {
            return;
        }
        if (findGroup(name) != null) {
            c.send("ERROR|A group with that name already exists");
            return;
        }
        Group g = new Group(name, password);
        groups.put(name, g);
        enterGroup(c, g);
        sendGroupList();
    }

    static synchronized void joinGroup(ClientHandler c, String name, String password) {
        Group g = findGroup(name);
        if (g == null) {
            c.send("ERROR|Group not found");
            return;
        }
        // إذا عليها باسورد وهو مش عضو، لازم الباسورد يكون صح
        if (g.isLocked() && !g.members.contains(c) && !g.password.equals(password)) {
            c.send("ERROR|Wrong password");
            return;
        }
        enterGroup(c, g);
        sendGroupList();
    }

    static synchronized void leaveGroup(ClientHandler c, String name) {
        Group g = findGroup(name);
        if (g == null || !g.members.contains(c)) {
            return;
        }
        exitGroup(c, g);
        c.send("LEFT|" + g.name);
        sendGroupList();
    }

    // هاي أهم وحدة: الرسالة بتروح بس لأعضاء نفس المجموعة
    static synchronized void sendMessage(ClientHandler c, String groupName, String text) {
        Group g = findGroup(groupName);
        text = text.replace("|", "").replaceAll("\\s+", " ").trim();
        if (g == null || text.isEmpty() || !g.members.contains(c)) {
            return;   // المجموعة مش موجودة، أو الرسالة فاضية، أو هو مش عضو
        }
        if (text.length() > 1000) {
            text = text.substring(0, 1000);
        }
        String time = LocalTime.now().toString().substring(0, 5);   // مثل 14:35
        String line = "MSG|" + g.name + "|" + c.name + "|" + time + "|" + text;

        g.history.add(line);
        if (g.history.size() > MAX_HISTORY) {
            g.history.remove(0);   // منشيل أقدم رسالة
        }
        sendToGroup(g, line);
    }

    // العميل سكّر البرنامج
    static synchronized void removeClient(ClientHandler c) {
        if (!clients.remove(c)) {
            return;   // ما كان مسجل دخول
        }
        // نسخة من اللائحة لأن exitGroup بتغير الأصلية
        for (Group g : new ArrayList<>(c.groups)) {
            exitGroup(c, g);
        }
        sendGroupList();
    }

    // ---------- دخول وخروج من المجموعة ----------

    static void enterGroup(ClientHandler c, Group g) {
        c.send("JOINED|" + g.name);
        if (g.members.contains(c)) {
            // هو أصلاً عضو، بس منرجع منبعتله الرسائل
            for (String old : g.history) {
                c.send(old);
            }
            return;
        }
        g.members.add(c);
        c.groups.add(g);
        for (String old : g.history) {     // بيشوف الرسائل القديمة
            c.send(old);
        }
        addSystemMessage(g, c.name + " joined the group");
        sendMembers(g);
    }

    static void exitGroup(ClientHandler c, Group g) {
        g.members.remove(c);
        c.groups.remove(g);
        // المجموعة صارت فاضية: منحذفها (إلا General)
        if (g.members.isEmpty() && !g.name.equals("General")) {
            groups.remove(g.name);
            return;
        }
        addSystemMessage(g, c.name + " left the group");
        sendMembers(g);
    }
}
