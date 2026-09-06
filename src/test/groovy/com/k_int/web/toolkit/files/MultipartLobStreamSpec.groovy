package com.k_int.web.toolkit.files

import org.hibernate.engine.jdbc.BlobProxy
import org.springframework.web.multipart.MultipartFile
import spock.lang.Specification

class MultipartLobStreamSpec extends Specification {
    def 'JDBC can bind a non-resettable multipart source twice without materializing it'() {
        given:
        byte[] content = 'streamed document'.bytes
        int opens = 0
        int closes = 0
        MultipartFile file = Stub() {
            getBytes() >> { throw new AssertionError('Whole-file materialization') }
            getInputStream() >> {
                opens++
                new ByteArrayInputStream(content) {
                    @Override boolean markSupported() { false }
                    @Override synchronized void reset() { throw new IOException('One-shot source') }
                    @Override void close() { closes++ }
                }
            }
        }
        def stream = new ReopenableMultipartInputStream(file)
        def blob = BlobProxy.generateProxy(stream, content.length)

        expect:
        blob.binaryStream.bytes == content
        blob.binaryStream.bytes == content
        opens == 2
        closes >= 1

        cleanup:
        blob.free()
        stream.close()
    }

    def 'a later mark restores its exact position from the source'() {
        given:
        MultipartFile file = Stub() { getInputStream() >> { new ByteArrayInputStream('abcdef'.bytes) } }
        def stream = new ReopenableMultipartInputStream(file)
        assert stream.skip(2) == 2
        stream.mark(1)
        assert stream.read() == (int)'c'

        when:
        stream.reset()

        then:
        stream.bytes == 'cdef'.bytes

        cleanup:
        stream.close()
    }
}
