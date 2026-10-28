import java.util.ArrayList;

// هاي الكلاس تبع المجموعة (Group)
// كل مجموعة إلها اسم وكلمة سر وأعضاء وآخر رسائل
public class Group {

    String name;
    String password;   // إذا فاضية معناها المجموعة بدون كلمة سر
    ArrayList<ClientHandler> members = new ArrayList<>();  // الأعضاء اللي جوا المجموعة
    ArrayList<String> history = new ArrayList<>();         // آخر الرسائل (بنخزنها كنص جاهز)

    Group(String name, String password) {
        this.name = name;
        this.password = password;
    }

    // هل المجموعة إلها كلمة سر؟
    boolean isLocked() {
        return !password.isEmpty();
    }
}
