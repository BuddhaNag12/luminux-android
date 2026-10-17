package com.buddhanag.luminux.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class AboutTest {
    @Test fun reflowLicense() {
        val text = "Copyright 2015\nMicrosoft\n\n-----------------------\nPREAMBLE\nThe goals\nof the licence\n\n\n\nEnd\n"
        assertEquals("Copyright 2015 Microsoft\n\nPREAMBLE The goals of the licence\n\nEnd", reflowLicense(text))
    }

    @Test fun reflowLicenseHandlesWindowsLineEnds() {
        assertEquals("a b\n\nc", reflowLicense("a\r\nb\r\n\r\nc"))
    }
}
