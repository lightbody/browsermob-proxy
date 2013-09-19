package net.lightbody.bmp.proxy.http;

import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class RepeatableInputStreamRequestEntity extends InputStreamEntity {
    private final static int BUFFER_SIZE = 2048;

    private BufferedInputStream content;

    public RepeatableInputStreamRequestEntity(InputStream instream, long length, ContentType contentType) {
        super(instream, length, contentType);
        content = new BufferedInputStream(instream,BUFFER_SIZE);
    }

    public RepeatableInputStreamRequestEntity(InputStream instream, long length) {
        super(instream, length);
        content = new BufferedInputStream(instream,BUFFER_SIZE);
    }

    @Override
    public boolean isChunked() {
        return false;
    }

    @Override
    public boolean isRepeatable() {
        return true;
    }

    @Override
    public void writeTo(OutputStream outstream) throws IOException {
        if (outstream == null) {
            throw new IllegalArgumentException("Output stream may not be null");
        }

        content.mark((int) this.getContentLength());
        try {
            byte[] buffer = new byte[BUFFER_SIZE];
            int l;
            if (this.getContentLength() < 0) {
                // consume until EOF
                while ((l = content.read(buffer)) != -1) {
                    outstream.write(buffer, 0, l);
                }
            } else {
                // consume no more than length
                long remaining = this.getContentLength();
                while (remaining > 0) {
                    l = content.read(buffer, 0, (int)Math.min(BUFFER_SIZE, remaining));
                    if (l == -1) {
                        break;
                    }
                    outstream.write(buffer, 0, l);
                    remaining -= l;
                }

            }
        } finally {
            content.reset();
        }



    }
}
