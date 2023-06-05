package org.usvm.samples.stdlib;

import java.io.FileInputStream;
import java.io.IOException;

public class JavaIOFileInputStreamCheck {
    public int read(String s) throws IOException {
        FileInputStream fis = new FileInputStream(s);
        byte[] b = new byte[1000];
        return fis.read(b);
    }
}
