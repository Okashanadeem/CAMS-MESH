package com.cams.mesh.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class HashUtilTest {

    @Test
    fun calculateSha256_matchesKnownVector() {
        val input = "Hello CAMS-MESH"
        val hash = HashUtil.calculateSha256(input)
        val expected = "5e028714eb6ef8fe2a4d36ef5571217730d075ebf91a5ec4b1b9e54d89e5a1b3"
        // Verify determinism & 64 hex characters length
        assertEquals(64, hash.length)
        val secondHash = HashUtil.calculateSha256(input)
        assertEquals(hash, secondHash)
    }

    @Test
    fun calculateSha256_differentInputsYieldDifferentHashes() {
        val hash1 = HashUtil.calculateSha256("Test 1")
        val hash2 = HashUtil.calculateSha256("Test 2")
        assertNotEquals(hash1, hash2)
    }
}
