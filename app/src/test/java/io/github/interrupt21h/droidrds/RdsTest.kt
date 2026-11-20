package io.github.interrupt21h.droidrds
import org.junit.Test
import org.junit.Assert.*
class RdsTest {
 @Test fun groupHeader() { val g=RdsGroup.parse("1234 8400 0000 0000"); assertEquals(8,g.type); assertEquals("A",g.version); assertTrue(g.tp) }
 @Test(expected=IllegalArgumentException::class) fun malformed() { RdsGroup.parse("1234 FFFFF 0000 0000") }
}
