package com.k_int.web.toolkit.files

import spock.lang.Specification
import spock.lang.Unroll

class StorageSchemaValidationModeSpec extends Specification {
    def 'deployment default is warning and strict is explicit'() {
        given:
        def validator = new StorageSchemaValidator()
        expect:
        validator.mode == 'warn'
        when:
        validator.mode = 'strict'
        then:
        validator.mode == 'strict'
    }

    @Unroll
    def 'invalid validation mode #value cannot silently disable strict qualification'() {
        when:
        new StorageSchemaValidator(mode: value)
        then:
        thrown(IllegalArgumentException)
        where:
        value << [null, '', 'STRICT', 'off', 'warning']
    }
}
