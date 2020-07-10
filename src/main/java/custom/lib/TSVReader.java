package custom.lib;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

public class TSVReader implements Closeable {
    final Scanner in;
    String peekLine = null;

    public TSVReader(InputStream stream) throws FileNotFoundException {
        in = new Scanner(stream);
    }

    public TSVReader(File f) throws FileNotFoundException {
        in = new Scanner(f);
    }

    public boolean hasNextTokens() {
        if (peekLine != null) return true;
        if (!in.hasNextLine()) {
            return false;
        }
        String line = in.nextLine().trim();
        if (line.isEmpty()) {
            return hasNextTokens();
        }
        this.peekLine = line;
        return true;
    }

    public String[] nextTokens() {
        if (!hasNextTokens()) return null;
        String[] tokens = peekLine.split("[\\s\t]+");
        peekLine = null;
        return tokens;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }
}