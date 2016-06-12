package org.jolokia.util;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.*;

/**
 * Created by gnufied on 2/7/16.
 * Implement chunked writing of data. Part of chunking is actually already
 * done by OutputStream and doing so here again will result in double chunking.
 * We just ensure that, we are flushing and closing the stream properly.
 *
 * This code is very closely yanked from java.nio.StreamEncoder class.
 * The reason we couldn't simply extend StreamEncoder class is, that class marks certain
 * attributes private which are very important for overriding close and flush methods.
 */
public class ChunkedWriter extends Writer {

    private OutputStream out;
    private Charset cs;
    private CharsetEncoder encoder;
    private ByteBuffer bb;
    // Leftover first char in a surrogate pair
    private boolean haveLeftoverChar = false;
    private char leftoverChar;
    private CharBuffer lcb = null;

    private static final byte[] EMPTY = {};

    public ChunkedWriter(OutputStream stream, String charset) {
        super(stream);
        this.out = stream;
        if (Charset.isSupported(charset)) {
            this.cs = Charset.forName(charset);
            this.encoder = cs.newEncoder().onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE);
        } else {
            throw new UnsupportedCharsetException(charset);
        }
        bb = ByteBuffer.allocate(DEFAULT_BYTE_BUFFER_SIZE);
    }

    private static final int DEFAULT_BYTE_BUFFER_SIZE = 4096;

    private volatile boolean isOpen = true;

    private void ensureOpen() throws IOException {
        if (!isOpen)
            throw new IOException("Stream closed");
    }

    public boolean isOpen() { return isOpen; }

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
        synchronized (lock) {
            ensureOpen();
            if ((off < 0) || (off > cbuf.length) || (len < 0) ||
                    ((off + len) > cbuf.length) || ((off + len) < 0)) {
                throw new IndexOutOfBoundsException();
            } else if (len == 0) {
                return;
            }
            implWrite(cbuf, off, len);
        }
    }

    public void write(int c) throws IOException {
        char cbuf[] = new char[1];
        cbuf[0] = (char) c;
        write(cbuf, 0, 1);
    }

    public void write(String str, int off, int len) throws IOException {
        /* Check the len before creating a char buffer */
        if (len < 0)
            throw new IndexOutOfBoundsException();
        char cbuf[] = new char[len];
        str.getChars(off, off + len, cbuf, 0);
        write(cbuf, 0, len);
    }

    @Override
    public void flush() throws IOException {
        synchronized (lock) {
            ensureOpen();
            implFlush();
        }
    }

    void implFlushBuffer() throws IOException {
        if (bb.position() > 0)
            writeBytes();
        flushLeftOverChar(null, true);
        try {
            for (;;) {
                CoderResult cr = encoder.flush(bb);
                if (cr.isUnderflow())
                    break;
                if (cr.isOverflow()) {
                    assert bb.position() > 0;
                    writeBytes();
                    continue;
                }
                cr.throwException();
            }

            if (bb.position() > 0)
                writeBytes();
        } catch (IOException x) {
            encoder.reset();
            throw x;
        }
        out.write(EMPTY);
    }

    void implFlush() throws IOException {
        implFlushBuffer();
        if (out != null)
            out.flush();
    }

    @Override
    public void close() throws IOException {
        synchronized (lock) {
            if (!isOpen)
                return;
            out.close();
            isOpen = false;
        }
    }

    void implWrite(char cbuf[], int off, int len) throws IOException{
        CharBuffer cb = CharBuffer.wrap(cbuf,off, len);

        if(haveLeftoverChar)
            flushLeftOverChar(cb, false);

        while (cb.hasRemaining()) {
            CoderResult cr = encoder.encode(cb, bb, false);

            if (cr.isUnderflow()) {
                assert (cb.remaining() <= 1) : cb.remaining();

                if(cb.remaining() == 1) {
                    haveLeftoverChar = true;
                    leftoverChar = cb.get();
                }
                break;
            }

            if (cr.isOverflow()) {
                assert bb.position() > 0;
                writeBytes();
                continue;
            }
            cr.throwException();
        }

    }

    private void flushLeftOverChar(CharBuffer cb, boolean endOfInput) throws IOException{
        if (!haveLeftoverChar && !endOfInput)
            return;

        if (lcb == null)
            lcb = CharBuffer.allocate(2);
        else
            lcb.clear();

        if (haveLeftoverChar)
            lcb.put(leftoverChar);

        if ((cb != null) && cb.hasRemaining())
            lcb.put(cb.get());
        lcb.flip();

        while (lcb.hasRemaining() || endOfInput) {
            CoderResult cr = encoder.encode(lcb, bb, endOfInput);

            if(cr.isUnderflow()) {
                if (lcb.hasRemaining()) {
                    leftoverChar = lcb.get();
                    if (cb != null && cb.hasRemaining())
                        flushLeftOverChar(cb,endOfInput);
                    return;
                }
                break;
            }

            if(cr.isOverflow()) {
                assert bb.position() > 0;
                writeBytes();
                continue;
            }
            cr.throwException();
        }
        haveLeftoverChar = false;
    }

    private void writeBytes() throws IOException{
        bb.flip();
        int lim = bb.limit();
        int pos = bb.position();
        assert (pos <= lim);

        int rem = (pos <= lim ? lim - pos : 0);

        if (rem > 0) {
            out.write(bb.array(), bb.arrayOffset() + pos, rem);
        }
        bb.clear();
    }
}
