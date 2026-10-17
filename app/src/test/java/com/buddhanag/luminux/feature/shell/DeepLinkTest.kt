package com.buddhanag.luminux.feature.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkTest {
    @Test fun deepLinkRoutes() {
        assertEquals(listOf(Route.Pro), deepLinkStack("luminux", "pro", null, isProUnlocked = false))
        assertEquals(emptyList<Route>(), deepLinkStack("luminux", "pro", null, isProUnlocked = true))
        assertEquals(listOf(Route.Album("user:7")), deepLinkStack("luminux", "collection", "album:user:7", false))
        assertEquals(listOf(Route.PhotoSet("abc")), deepLinkStack("luminux", "collection", "set:abc", false))
        assertEquals(emptyList<Route>(), deepLinkStack("luminux", "collection", "cameraRoll", false))
        assertEquals(emptyList<Route>(), deepLinkStack("luminux", "collection", null, false))
        assertEquals(emptyList<Route>(), deepLinkStack("luminux", "hub", null, false))
        assertEquals(emptyList<Route>(), deepLinkStack("luminux", "elsewhere", null, false))
        assertNull(deepLinkStack("https", "pro", null, false))
    }
}
