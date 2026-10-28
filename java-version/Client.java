import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

// برنامج العميل (الواجهة). بيتصل بالسيرفر عن طريق TCP (Socket).
// تشغيله:  java Client
public class Client {

    // ---------- الاتصال ----------
    Socket socket;
    BufferedReader in;
    PrintWriter out;
    String myName;

    // ---------- البيانات ----------
    ArrayList<String[]> allGroups = new ArrayList<>();            // كل المجموعات [اسم, عدد, مقفلة؟]
    ArrayList<String> joined = new ArrayList<>();                 // المجموعات اللي أنا فيها
    HashMap<String, StringBuilder> chats = new HashMap<>();       // نص الدردشة لكل مجموعة
    HashMap<String, String> members = new HashMap<>();            // أعضاء كل مجموعة
    HashSet<String> unread = new HashSet<>();                     // مجموعات فيها رسائل جديدة
    String active = null;                                         // المجموعة المفتوحة هلق

    // ---------- الواجهة ----------
    static final Color BG = new Color(0x14, 0x1A, 0x30);
    static final Color PANEL = new Color(0x1C, 0x23, 0x40);
    static final Color TEXT = new Color(0xE8, 0xEC, 0xF8);
    static final Color MUTED = new Color(0x8D, 0x97, 0xB5);
    static final Color ACCENT = new Color(0x7C, 0x5C, 0xFF);

    JFrame frame = new JFrame("Network Chat");
    DefaultListModel<String[]> groupModel = new DefaultListModel<>();
    JList<String[]> groupList = new JList<>(groupModel);
    DefaultListModel<String> memberModel = new DefaultListModel<>();
    JList<String> memberList = new JList<>(memberModel);
    JEditorPane chatPane = new JEditorPane();
    JLabel title = new JLabel("Join a group");
    JTextField input = new JTextField();

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        new Client().start(args);
    }

    void start(String[] args) throws Exception {
        // عنوان السيرفر والاسم (إذا ما انكتبوا بالأمر منسألك)
        String host = args.length > 0 ? args[0] : JOptionPane.showInputDialog(null, "Server address:", "localhost");
        if (host == null) return;
        socket = new Socket(host, 5000);   // هون بنتصل بالسيرفر (TCP)
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

        // منطلب الاسم لحد ما السيرفر يقبله
        String name = args.length > 1 ? args[1] : null;
        while (true) {
            if (name == null) name = JOptionPane.showInputDialog(null, "Your name:");
            if (name == null) return;   // ضغط Cancel
            out.println("LOGIN|" + name);
            String answer = in.readLine();
            if (answer.startsWith("WELCOME")) {
                myName = answer.split("\\|")[1];
                break;
            }
            JOptionPane.showMessageDialog(null, answer.split("\\|")[1]);   // مثلاً الاسم مستخدم
            name = null;
        }

        buildWindow();

        // خيط ثاني بيضل يقرا اللي بيبعته السيرفر
        Thread reader = new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    String l = line;
                    SwingUtilities.invokeLater(() -> handle(l));   // تحديث الشاشة لازم يصير بخيط الواجهة
                }
            } catch (IOException e) {
            }
            SwingUtilities.invokeLater(() -> title.setText("Disconnected from server"));
        });
        reader.start();
    }

    // ---------- اللي بيجي من السيرفر ----------

    void handle(String line) {
        String[] p = line.split("\\|", 5);
        String type = p[0];

        if (type.equals("ERROR")) {
            JOptionPane.showMessageDialog(frame, p[1]);

        } else if (type.equals("JOINED")) {
            // دخلت مجموعة، بعدها السيرفر بيبعت رسائلها القديمة
            if (!joined.contains(p[1])) joined.add(p[1]);
            chats.put(p[1], new StringBuilder());
            openGroup(p[1]);

        } else if (type.equals("LEFT")) {
            joined.remove(p[1]);
            chats.remove(p[1]);
            if (p[1].equals(active)) {
                openGroup(joined.isEmpty() ? null : joined.get(0));
            } else {
                refreshGroups();
            }

        } else if (type.equals("GROUPS")) {
            allGroups.clear();
            String[] all = line.split("\\|");
            for (int i = 1; i < all.length; i++) {
                allGroups.add(all[i].split(","));   // اسم,عدد,مقفلة
            }
            refreshGroups();

        } else if (type.equals("MEMBERS")) {
            members.put(p[1], p[2]);
            if (p[1].equals(active)) showMembers();

        } else if (type.equals("MSG")) {
            addMessage(p[1], p[2], p[3], p[4]);

        } else if (type.equals("SYS")) {
            addToChat(p[1], "<div align='center'><font color='#8D97B5' size='2'>" + safe(p[2]) + "</font></div>", false);
        }
    }

    // ---------- رسم الدردشة ----------

    // رسالة عادية: رسائلي عاليمين بلون، رسائل غيري عاليسار
    void addMessage(String group, String sender, String time, String text) {
        String html;
        if (sender.equals(myName)) {
            html = "<div align='right'><table cellpadding='6'><tr><td bgcolor='#6C5CE7'>"
                 + "<font color='white'>" + safe(text) + "</font> <font color='#D0C8FF' size='2'>" + time + "</font>"
                 + "</td></tr></table></div>";
        } else {
            html = "<div align='left'><table cellpadding='6'><tr><td bgcolor='#2A3150'>"
                 + "<font color='" + colorFor(sender) + "'><b>" + safe(sender) + "</b></font> "
                 + "<font color='#8D97B5' size='2'>" + time + "</font><br>"
                 + "<font color='#E8ECF8'>" + safe(text) + "</font>"
                 + "</td></tr></table></div>";
        }
        addToChat(group, html, true);
    }

    // منحط السطر بدردشة المجموعة، وإذا هي المفتوحة منعرضه
    void addToChat(String group, String html, boolean isRealMessage) {
        StringBuilder chat = chats.get(group);
        if (chat == null) return;
        chat.append(html);
        if (group.equals(active)) {
            showChat();
        } else if (isRealMessage) {
            unread.add(group);   // مجموعة ثانية: منعلّم إنو فيها جديد
            refreshGroups();
        }
    }

    void showChat() {
        String body = active == null ? "" : chats.get(active).toString();
        chatPane.setText("<html><body style='font-family:SansSerif; font-size:13px; background:#141A30'>" + body + "</body></html>");
        // ننزل لآخر الدردشة
        SwingUtilities.invokeLater(() -> chatPane.setCaretPosition(chatPane.getDocument().getLength()));
    }

    // افتح مجموعة (أو null يعني ولا وحدة)
    void openGroup(String group) {
        active = group;
        unread.remove(group);
        title.setText(group == null ? "Join a group" : "# " + group);
        input.setEnabled(group != null);
        showChat();
        showMembers();
        refreshGroups();
    }

    void showMembers() {
        memberModel.clear();
        if (active == null || members.get(active) == null) return;
        for (String name : members.get(active).split(",")) {
            memberModel.addElement(name);
        }
        title.setText("# " + active + "   (" + memberModel.size() + " online)");
    }

    void refreshGroups() {
        groupModel.clear();
        for (String[] g : allGroups) {
            groupModel.addElement(g);
        }
    }

    // ---------- اللي بنعمله لما المستخدم يضغط ----------

    void clickOnGroup(String[] g) {
        String name = g[0];
        boolean locked = g[2].equals("1");
        if (joined.contains(name)) {
            openGroup(name);                          // أنا فيها: بس افتحها
        } else if (!locked) {
            out.println("JOIN|" + name + "|");        // مفتوحة: ادخل
        } else {
            JPasswordField pass = new JPasswordField();   // مقفلة: اسأل عن الباسورد
            int ok = JOptionPane.showConfirmDialog(frame, pass, "Password for " + name, JOptionPane.OK_CANCEL_OPTION);
            if (ok == JOptionPane.OK_OPTION) {
                out.println("JOIN|" + name + "|" + new String(pass.getPassword()));
            }
        }
    }

    void createGroup() {
        JTextField nameField = new JTextField();
        JPasswordField passField = new JPasswordField();
        Object[] fields = { "Group name:", nameField, "Password (optional):", passField };
        int ok = JOptionPane.showConfirmDialog(frame, fields, "Create group", JOptionPane.OK_CANCEL_OPTION);
        if (ok == JOptionPane.OK_OPTION && !nameField.getText().trim().isEmpty()) {
            out.println("CREATE|" + nameField.getText().trim() + "|" + new String(passField.getPassword()));
        }
    }

    void sendMessage() {
        String text = input.getText().trim();
        if (text.isEmpty() || active == null) return;
        out.println("MSG|" + active + "|" + text);
        input.setText("");
    }

    // ---------- صنع النافذة ----------

    void buildWindow() {
        // لائحة المجموعات: كل مجموعة بلون حسب حالتها
        groupList.setCellRenderer(new DefaultListCellRenderer() {
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean sel, boolean focus) {
                String[] g = (String[]) value;
                String text = "# " + g[0] + "  (" + g[1] + ")" + (g[2].equals("1") ? "  [password]" : "");
                if (unread.contains(g[0])) text = "* " + text;
                JLabel label = (JLabel) super.getListCellRendererComponent(list, text, index, false, false);
                label.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
                label.setOpaque(true);
                if (g[0].equals(active)) {
                    label.setBackground(ACCENT);
                    label.setForeground(Color.WHITE);
                } else {
                    label.setBackground(PANEL);
                    label.setForeground(joined.contains(g[0]) ? TEXT : MUTED);
                }
                return label;
            }
        });
        groupList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                int i = groupList.locationToIndex(e.getPoint());
                if (i >= 0 && groupList.getCellBounds(i, i).contains(e.getPoint())) {
                    clickOnGroup(groupModel.get(i));
                }
            }
        });

        groupList.setBackground(PANEL);
        memberList.setBackground(PANEL);
        memberList.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 0));
        memberList.setForeground(TEXT);
        memberList.setFixedCellHeight(28);

        chatPane.setContentType("text/html");
        chatPane.setEditable(false);
        chatPane.setBackground(BG);

        input.setBackground(PANEL);
        input.setForeground(TEXT);
        input.setCaretColor(TEXT);
        input.setFont(new Font("SansSerif", Font.PLAIN, 14));
        input.setEnabled(false);
        input.addActionListener(e -> sendMessage());      // زر Enter

        JButton sendButton = makeButton("Send");
        sendButton.addActionListener(e -> sendMessage());
        JButton createButton = makeButton("Create group");
        createButton.addActionListener(e -> createGroup());
        JButton leaveButton = makeButton("Leave group");
        leaveButton.addActionListener(e -> {
            if (active != null) out.println("LEAVE|" + active);
        });

        // الجهة اليسار: اسمي + المجموعات + الأزرار
        JPanel left = new JPanel(new BorderLayout());
        left.setBackground(PANEL);
        left.setPreferredSize(new Dimension(230, 0));
        left.add(makeHeader("Groups  (you: " + myName + ")"), BorderLayout.NORTH);
        left.add(plainScroll(groupList), BorderLayout.CENTER);
        JPanel buttons = new JPanel(new GridLayout(2, 1, 0, 6));
        buttons.setBackground(PANEL);
        buttons.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        buttons.add(createButton);
        buttons.add(leaveButton);
        left.add(buttons, BorderLayout.SOUTH);

        // الجهة اليمين: مين أونلاين
        JPanel right = new JPanel(new BorderLayout());
        right.setBackground(PANEL);
        right.setPreferredSize(new Dimension(170, 0));
        right.add(makeHeader("Online in group"), BorderLayout.NORTH);
        right.add(plainScroll(memberList), BorderLayout.CENTER);

        // النص: عنوان المجموعة + الدردشة + خانة الكتابة
        title.setForeground(TEXT);
        title.setFont(new Font("SansSerif", Font.BOLD, 18));
        title.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        JPanel bottom = new JPanel(new BorderLayout(8, 0));
        bottom.setBackground(BG);
        bottom.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        bottom.add(input, BorderLayout.CENTER);
        bottom.add(sendButton, BorderLayout.EAST);
        JPanel center = new JPanel(new BorderLayout());
        center.setBackground(BG);
        center.add(title, BorderLayout.NORTH);
        center.add(plainScroll(chatPane), BorderLayout.CENTER);
        center.add(bottom, BorderLayout.SOUTH);

        frame.setLayout(new BorderLayout());
        frame.add(left, BorderLayout.WEST);
        frame.add(center, BorderLayout.CENTER);
        frame.add(right, BorderLayout.EAST);
        frame.setSize(1050, 680);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }

    // ---------- أشياء صغيرة للشكل ----------

    JButton makeButton(String text) {
        JButton b = new JButton(text);
        b.setBackground(ACCENT);
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setOpaque(true);
        return b;
    }

    JLabel makeHeader(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(MUTED);
        l.setBorder(BorderFactory.createEmptyBorder(14, 12, 10, 12));
        return l;
    }

    JScrollPane plainScroll(Component c) {
        JScrollPane s = new JScrollPane(c);
        s.setBorder(null);
        return s;
    }

    // كل اسم بلون خاص فيه
    String colorFor(String name) {
        Color c = Color.getHSBColor((Math.abs(name.hashCode()) % 360) / 360f, 0.5f, 1f);
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    // منحمي النص من رموز HTML
    String safe(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
