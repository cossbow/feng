package org.cossbow.feng.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class BufferOutputStream extends ByteArrayOutputStream {
    public BufferOutputStream() {
        super(1024);
    }

    public BufferOutputStream(int size) {
        super(size);
    }

    public InputStream read() {
        return new ByteArrayInputStream(buf, 0, count);
    }
}
