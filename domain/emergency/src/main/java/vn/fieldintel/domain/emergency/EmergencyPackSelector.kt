package vn.fieldintel.domain.emergency

enum class PackSlot { ACTIVE, LKG, GOLDEN, MINIMAL_UI }

data class EmergencyPack(
    val packId: String,
    val version: String,
    val verified: Boolean,
    val revoked: Boolean,
    val slot: PackSlot
)

data class PackSelection(
    val pack: EmergencyPack?,
    val slot: PackSlot,
    val reason: String
)

class EmergencyPackSelector {
    fun select(
        active: EmergencyPack?,
        lkg: EmergencyPack?,
        golden: EmergencyPack?
    ): PackSelection {
        eligible(active, PackSlot.ACTIVE)?.let {
            return PackSelection(it, PackSlot.ACTIVE, "ACTIVE_VERIFIED")
        }
        eligible(lkg, PackSlot.LKG)?.let {
            return PackSelection(it, PackSlot.LKG, "ACTIVE_UNAVAILABLE_USE_LKG")
        }
        eligible(golden, PackSlot.GOLDEN)?.let {
            return PackSelection(it, PackSlot.GOLDEN, "LKG_UNAVAILABLE_USE_GOLDEN")
        }
        return PackSelection(null, PackSlot.MINIMAL_UI, "NO_SAFE_PROTOCOL_PACK")
    }

    private fun eligible(pack: EmergencyPack?, requiredSlot: PackSlot): EmergencyPack? {
        if (pack == null) return null
        if (pack.slot != requiredSlot) return null
        if (!pack.verified) return null
        if (pack.revoked) return null
        return pack
    }
}
