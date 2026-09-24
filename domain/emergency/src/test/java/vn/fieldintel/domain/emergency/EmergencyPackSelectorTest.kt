package vn.fieldintel.domain.emergency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmergencyPackSelectorTest {
    private val selector = EmergencyPackSelector()
    private fun pack(slot: PackSlot, verified: Boolean = true, revoked: Boolean = false) =
        EmergencyPack(slot.name.lowercase(), "1", verified, revoked, slot)

    @Test fun verifiedActiveWins() {
        val r = selector.select(pack(PackSlot.ACTIVE), pack(PackSlot.LKG), pack(PackSlot.GOLDEN))
        assertEquals(PackSlot.ACTIVE, r.slot)
    }

    @Test fun unverifiedActiveFallsBackToLkg() {
        val r = selector.select(pack(PackSlot.ACTIVE, verified=false), pack(PackSlot.LKG), pack(PackSlot.GOLDEN))
        assertEquals(PackSlot.LKG, r.slot)
    }

    @Test fun revokedActiveIsNeverSelected() {
        val r = selector.select(pack(PackSlot.ACTIVE, revoked=true), pack(PackSlot.LKG), pack(PackSlot.GOLDEN))
        assertEquals(PackSlot.LKG, r.slot)
    }

    @Test fun revokedLkgFallsBackToGolden() {
        val r = selector.select(null, pack(PackSlot.LKG, revoked=true), pack(PackSlot.GOLDEN))
        assertEquals(PackSlot.GOLDEN, r.slot)
    }

    @Test fun noSafePackFallsBackToMinimalUi() {
        val r = selector.select(
            pack(PackSlot.ACTIVE, revoked=true),
            pack(PackSlot.LKG, verified=false),
            pack(PackSlot.GOLDEN, revoked=true)
        )
        assertEquals(PackSlot.MINIMAL_UI, r.slot)
        assertNull(r.pack)
    }
}
