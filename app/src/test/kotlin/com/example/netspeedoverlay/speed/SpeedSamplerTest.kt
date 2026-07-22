package com.example.netspeedoverlay.speed

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

class SpeedSamplerTest {

    private lateinit var originalLocale: Locale

    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun `format zero bytes`() {
        assertEquals("0 KB/s", SpeedSampler.format(0L, showPerSecondSuffix = true))
    }

    @Test
    fun `format sub kilobyte value`() {
        assertEquals("0.5 KB/s", SpeedSampler.format(512L, showPerSecondSuffix = true))
    }

    @Test
    fun `format kilobyte value without suffix`() {
        assertEquals("2 KB", SpeedSampler.format(2048L, showPerSecondSuffix = false))
    }

    @Test
    fun `format megabyte value`() {
        assertEquals("1.0 MB/s", SpeedSampler.format(1024L * 1024L, showPerSecondSuffix = true))
    }

    @Test
    fun `format compactUnit removes space for 3+ digit numbers`() {
        val result = SpeedSampler.format(177L * 1024L, showPerSecondSuffix = false, compactUnit = true)
        assertEquals("177K", result)
    }

    @Test
    fun `format is locale independent (decimal point, not comma)`() {
        Locale.setDefault(Locale.ITALY)
        val bytesPerSec = (1.5 * 1024 * 1024).toLong()
        assertEquals("1.5 MB/s", SpeedSampler.format(bytesPerSec, showPerSecondSuffix = true))
    }

    @Test
    fun `formatCompact zero bytes`() {
        assertEquals("0K", SpeedSampler.formatCompact(0L))
    }

    @Test
    fun `formatCompact megabyte value is locale independent`() {
        Locale.setDefault(Locale.ITALY)
        assertEquals("2.0M", SpeedSampler.formatCompact(2L * 1024L * 1024L))
    }

    @Test
    fun `formatTotalBytes sub kilobyte`() {
        assertEquals("500 B", SpeedSampler.formatTotalBytes(500L))
    }

    @Test
    fun `formatTotalBytes kilobyte`() {
        assertEquals("1.5 KB", SpeedSampler.formatTotalBytes(1536L))
    }

    @Test
    fun `formatTotalBytes gigabyte`() {
        val twoGb = 2L * 1024 * 1024 * 1024
        assertEquals("2.00 GB", SpeedSampler.formatTotalBytes(twoGb))
    }
}
