package com.k_int.web.toolkit.databinding

import java.text.DecimalFormat
import java.text.NumberFormat

import groovy.transform.CompileStatic

@CompileStatic
class FixedLocaleBigDecimalConverter extends FixedLocaleNumberConverter {

    @Override
    protected NumberFormat getNumberFormatter() {
        def nf = super.getNumberFormatter()
        if (!(nf instanceof DecimalFormat)) {
          throw new IllegalStateException("Cannot support non-DecimalFormat: " + nf)
        }

        ((DecimalFormat)nf).setParseBigDecimal(true)
        
        nf
    }
}