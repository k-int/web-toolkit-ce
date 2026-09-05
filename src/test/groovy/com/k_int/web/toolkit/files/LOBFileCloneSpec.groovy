package com.k_int.web.toolkit.files

import spock.lang.Specification
import javax.sql.rowset.serial.SerialBlob

class LOBFileCloneSpec extends Specification {
    def 'LOB clone callback accepts the target and reads the source bytes'() {
        given:
        byte[] bytes = 'Original document content'.getBytes('UTF-8')
        def source = [fileContents: new SerialBlob(bytes)]
        def target = [fileContents: null]
        // This is the invocation contract used by Clonable.cloneSingleProperty.
        def callback = LOBFileObject.cloneStaticValues.fileContents.rehydrate(target, source, source)

        when:
        def copy = callback.call(target)

        then:
        !copy.is(source.fileContents)
        copy.length() == bytes.length
        copy.binaryStream.bytes == bytes
        source.fileContents.binaryStream.bytes == bytes
    }
}
