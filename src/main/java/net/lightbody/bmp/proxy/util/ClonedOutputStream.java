package net.lightbody.bmp.proxy.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;

public class ClonedOutputStream extends OutputStream {
    private OutputStream os;
    private ByteArrayOutputStream copy = new ByteArrayOutputStream();

    public ClonedOutputStream(OutputStream os) {
        this.os = os;
    }

    @Override
    public void write(int b) throws IOException {
//        os.write(b);
        copy.write(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
//        os.write(b);
        copy.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
//        os.write(b, off, len);
        copy.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
//        os.flush();
        copy.flush();
    }

    @Override
    public void close() throws IOException {
//        os.close();
        copy.close();
    }

    public ByteArrayOutputStream getOutput() {
        return copy;
    }

    public void writeAndCloseAll( ) throws IOException {
        writeAndCloseAll(null);
    }
    public void writeAndCloseAll(String in) throws IOException {
        if ( in!= null ) {
            if (copy.toString().equals(in) ){
                copy.writeTo(os);
            }
            else {
                PrintWriter out = new PrintWriter(os) ;
                out.write(in);
            }
        }
        else if (!copy.toString().isEmpty()) {
            copy.writeTo(os);
        }
        os.flush();
        os.close();
    }
}
