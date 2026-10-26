package com.example.receivemoney.speech

import com.example.receivemoney.domain.Gender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceGenderMatcherTest {

    @Test
    fun `detects explicit gender labels`() {
        assertEquals(Gender.FEMALE, VoiceGenderMatcher.detect("en-us-x-sfg#female_1-local", emptySet()))
        assertEquals(Gender.MALE, VoiceGenderMatcher.detect("en-us-x-sfg#male_2-local", emptySet()))
        assertEquals(Gender.MALE, VoiceGenderMatcher.detect("voice-a", setOf("gender=male")))
        assertEquals(Gender.FEMALE, VoiceGenderMatcher.detect("voice-b", setOf("Gender=Female")))
    }

    @Test
    fun `does not guess gender from opaque voice codes`() {
        assertNull(VoiceGenderMatcher.detect("en-us-x-zzz-local", setOf("networkTimeoutMs")))
        assertNull(VoiceGenderMatcher.detect("en-US-SMTd01", emptySet()))
        assertNull(VoiceGenderMatcher.detect("en-US-default", emptySet()))
    }

    @Test
    fun `female is not mistaken for male`() {
        assertEquals(Gender.FEMALE, VoiceGenderMatcher.detect("x#female", emptySet()))
    }

    @Test
    fun `samsung names encode gender`() {
        assertEquals(Gender.FEMALE, VoiceGenderMatcher.detect("en-US-SMTf00", emptySet()))
        assertEquals(Gender.MALE, VoiceGenderMatcher.detect("en-US-SMTm00", emptySet()))
    }

    @Test
    fun `curated google en-us voices`() {
        assertEquals(Gender.MALE, VoiceGenderMatcher.detect("en-us-x-iom-local", setOf("networkTimeoutMs")))
        assertEquals(Gender.MALE, VoiceGenderMatcher.detect("en-us-x-tpd-network", emptySet()))
        assertEquals(Gender.FEMALE, VoiceGenderMatcher.detect("en-us-x-sfg-local", emptySet()))
        assertEquals(Gender.FEMALE, VoiceGenderMatcher.detect("km-kh-x-khm-local", emptySet()))
        assertEquals(Gender.FEMALE, VoiceGenderMatcher.detect("km-kh-x-khm-network", emptySet()))
        assertNull("Other locales are not curated", VoiceGenderMatcher.detect("en-gb-x-rjs-local", emptySet()))
    }
}
