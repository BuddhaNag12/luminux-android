package com.buddhanag.luminux.monetization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

class AdUnitsTest {
    @Test fun debugBuildsUseGoogleTestUnits() {
        assertEquals("ca-app-pub-3940256099942544/5224354917", AdUnits.rewarded)
        assertEquals("ca-app-pub-3940256099942544/2247696110", AdUnits.native)
        assertEquals(AdUnits.TEST_REWARDED, AdUnits.pick("ca-app-pub-1/2", AdUnits.TEST_REWARDED))
    }

    @Test fun purchasesNeedPlaySignatureOnceAKeyIsSet() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.genKeyPair()
        val key = Base64.getEncoder().encodeToString(pair.public.encoded)
        val json = """{"productId":"com.buddhanag.luminux.pro"}"""
        val signature = Signature.getInstance("SHA1withRSA").run {
            initSign(pair.private)
            update(json.toByteArray())
            Base64.getEncoder().encodeToString(sign())
        }
        assertTrue(verifyPurchase(json, signature, key))
        assertFalse(verifyPurchase(json.replace("pro", "free"), signature, key))
        assertFalse(verifyPurchase(json, "bad", "not a key"))
        assertTrue(verifyPurchase(json, "bad", key = ""))
    }
}
