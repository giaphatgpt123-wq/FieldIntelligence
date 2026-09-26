package vn.fieldintel.feature.emergency

import org.junit.Assert.assertEquals
import org.junit.Test

class RegionQualityLabelTest {
    @Test
    fun labelsEachCaptureQualityState() {
        assertEquals("ĐANG ĐO CHẤT LƯỢNG", regionQualityLabel(null))
        assertEquals(
            "QUÁ TỐI",
            regionQualityLabel(
                RegionFrameQuality(20f, 5f, 7f, tooDark = true, tooBright = false, tooBlurred = false)
            )
        )
        assertEquals(
            "QUÁ SÁNG",
            regionQualityLabel(
                RegionFrameQuality(240f, 5f, 7f, tooDark = false, tooBright = true, tooBlurred = false)
            )
        )
        assertEquals(
            "CHƯA NÉT",
            regionQualityLabel(
                RegionFrameQuality(100f, 2f, 2f, tooDark = false, tooBright = false, tooBlurred = true)
            )
        )
        assertEquals(
            "ĐỦ ĐIỀU KIỆN CHỤP",
            regionQualityLabel(
                RegionFrameQuality(110f, 20f, 15f, tooDark = false, tooBright = false, tooBlurred = false)
            )
        )
    }
}
