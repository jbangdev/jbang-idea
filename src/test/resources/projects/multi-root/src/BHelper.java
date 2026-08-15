import com.google.common.base.Joiner;

class BHelper {
    static String message() {
        return Joiner.on("-").join("root", "b", "works");
    }
}
