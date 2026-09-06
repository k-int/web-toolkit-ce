package com.k_int.web.toolkit.files

import groovy.transform.CompileStatic
import org.springframework.web.multipart.MultipartFile

/** Rewind the multipart source for repeated JDBC binding without buffering file bytes. */
@CompileStatic
final class ReopenableMultipartInputStream extends InputStream {
    private final MultipartFile source
    private InputStream current
    private long position
    private long marked

    ReopenableMultipartInputStream(MultipartFile source) {
        this.source = source
        current = source.inputStream
    }

    @Override int read() {
        int value = current.read()
        if (value >= 0) position++
        value
    }

    @Override int read(byte[] bytes, int offset, int length) {
        int count = current.read(bytes, offset, length)
        if (count > 0) position += count
        count
    }

    @Override long skip(long count) {
        long skipped = current.skip(count)
        position += skipped
        skipped
    }

    @Override boolean markSupported() { true }
    @Override synchronized void mark(int readLimit) { marked = position }
    @Override synchronized void reset() {
        current.close()
        current = source.inputStream
        current.skipNBytes(marked)
        position = marked
    }
    @Override void close() { current.close() }
}
