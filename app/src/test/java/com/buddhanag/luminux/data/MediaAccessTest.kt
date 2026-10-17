package com.buddhanag.luminux.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaAccessTest {
    @Test fun imagesOrVideoIsFull() {
        assertEquals(MediaAccess.FULL, mediaAccess(images = true, video = false, userSelected = false, askedBefore = true, canAskAgain = false))
        assertEquals(MediaAccess.FULL, mediaAccess(images = false, video = true, userSelected = true, askedBefore = true, canAskAgain = false))
    }

    @Test fun userSelectedOnlyIsLimited() {
        assertEquals(MediaAccess.LIMITED, mediaAccess(images = false, video = false, userSelected = true, askedBefore = true, canAskAgain = false))
    }

    @Test fun neverAskedIsNotDetermined() {
        assertEquals(MediaAccess.NOT_DETERMINED, mediaAccess(images = false, video = false, userSelected = false, askedBefore = false, canAskAgain = false))
    }

    @Test fun deniedOnceCanStillAsk() {
        assertEquals(MediaAccess.NOT_DETERMINED, mediaAccess(images = false, video = false, userSelected = false, askedBefore = true, canAskAgain = true))
    }

    @Test fun deniedForGoodIsDenied() {
        assertEquals(MediaAccess.DENIED, mediaAccess(images = false, video = false, userSelected = false, askedBefore = true, canAskAgain = false))
    }

    @Test fun onlyFullAndLimitedCanRead() {
        assertTrue(MediaAccess.FULL.canRead)
        assertTrue(MediaAccess.LIMITED.canRead)
        assertFalse(MediaAccess.NOT_DETERMINED.canRead)
        assertFalse(MediaAccess.DENIED.canRead)
    }
}
