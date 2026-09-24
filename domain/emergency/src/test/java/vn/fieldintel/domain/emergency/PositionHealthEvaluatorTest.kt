package vn.fieldintel.domain.emergency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PositionHealthEvaluatorTest {
    private val evaluator = PositionHealthEvaluator()

    @Test fun accurateFreshFixIsGood() {
        val fix = PositionFix(10.0,105.0,8.0,1000,1000)
        val a = evaluator.assess(fix,null,2000)
        assertEquals(PositionHealth.GOOD,a.health)
        assertSame(fix,evaluator.reliableCandidate(a))
    }

    @Test fun missingCurrentFixIsLostAndKeepsLastReliable() {
        val last = PositionFix(10.0,105.0,10.0,1000,1000)
        val a = evaluator.assess(null,last,2000)
        assertEquals(PositionHealth.LOST,a.health)
        assertSame(last,evaluator.reliableCandidate(a))
    }

    @Test fun implausibleJumpIsSuspectAndDoesNotReplaceReliableFix() {
        val last = PositionFix(10.0,105.0,8.0,1000,1000)
        val current = PositionFix(11.0,106.0,8.0,2000,2000)
        val a = evaluator.assess(current,last,3000)
        assertEquals(PositionHealth.SUSPECT,a.health)
        assertSame(last,evaluator.reliableCandidate(a))
    }

    @Test fun veryOldFixIsLost() {
        val fix = PositionFix(10.0,105.0,8.0,1000,1000)
        val a = evaluator.assess(fix,null,130000)
        assertEquals(PositionHealth.LOST,a.health)
    }
}
