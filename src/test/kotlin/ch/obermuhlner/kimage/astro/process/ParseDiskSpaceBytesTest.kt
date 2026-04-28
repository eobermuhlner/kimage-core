package ch.obermuhlner.kimage.astro.process

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ParseDiskSpaceBytesTest {

    @Test fun `raw integer string`()    { assertEquals(123456789L, parseDiskSpaceBytes("123456789")) }
    @Test fun `max keyword`()           { assertEquals(Long.MAX_VALUE, parseDiskSpaceBytes("max")) }
    @Test fun `empty string means max`(){ assertEquals(Long.MAX_VALUE, parseDiskSpaceBytes("")) }

    @Test fun `kB decimal`()  { assertEquals(5_000L,             parseDiskSpaceBytes("5kB")) }
    @Test fun `KB uppercase`(){ assertEquals(3_000L,             parseDiskSpaceBytes("3KB")) }
    @Test fun `KiB binary`()  { assertEquals(4 * 1024L,          parseDiskSpaceBytes("4KiB")) }

    @Test fun `MB decimal`()  { assertEquals(2_000_000L,         parseDiskSpaceBytes("2MB")) }
    @Test fun `MiB binary`()  { assertEquals(3 * 1024L * 1024,   parseDiskSpaceBytes("3MiB")) }

    @Test fun `GB decimal`()  { assertEquals(1_000_000_000L,     parseDiskSpaceBytes("1GB")) }
    @Test fun `GiB binary`()  { assertEquals(2L * 1024 * 1024 * 1024, parseDiskSpaceBytes("2GiB")) }

    @Test fun `TB decimal`()  { assertEquals(1_000_000_000_000L, parseDiskSpaceBytes("1TB")) }
    @Test fun `TiB binary`()  { assertEquals(1L * 1024 * 1024 * 1024 * 1024, parseDiskSpaceBytes("1TiB")) }

    @Test fun `whitespace trimmed`() { assertEquals(500_000_000L, parseDiskSpaceBytes(" 500 MB ")) }

    @Test fun `decimal fraction`() { assertEquals(1_500_000_000L, parseDiskSpaceBytes("1.5GB")) }

    @Test fun `unknown suffix throws`() {
        assertThrows<IllegalArgumentException> { parseDiskSpaceBytes("10XB") }
    }
}
