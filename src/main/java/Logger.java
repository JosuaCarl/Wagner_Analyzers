import ij.IJ;

public class Logger {
    protected static void log(String message) {
        System.out.println(message);
        IJ.log(message);
    }
}
