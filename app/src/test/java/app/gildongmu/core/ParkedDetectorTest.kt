package app.gildongmu.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParkedDetectorTest {

    private val spot = Fixtures.at(30.0)
    private val tenMetresOn = Fixtures.at(30.010)
    private val twoHundredMetresOn = Fixtures.at(30.2)

    @Test
    fun standingStillLongEnoughIsParked() {
        val detector = ParkedDetector()
        assertNull(detector.onLocation(spot, 0))
        assertNull(detector.onLocation(tenMetresOn, 60_000))
        assertEquals(true, detector.onLocation(spot, 120_000))
        assertTrue(detector.parked)
        assertNull("Reported once", detector.onLocation(spot, 180_000))
    }

    @Test
    fun aRedLightIsNotAStop() {
        val detector = ParkedDetector()
        detector.onLocation(spot, 0)
        assertNull(detector.onLocation(spot, 90_000))
        assertNull(detector.onLocation(twoHundredMetresOn, 100_000))
        assertNull("The clock restarted when the car moved", detector.onLocation(twoHundredMetresOn, 200_000))
        assertFalse(detector.parked)
    }

    @Test
    fun movingAwayEndsTheStop() {
        val detector = ParkedDetector()
        detector.onLocation(spot, 0)
        detector.onLocation(spot, 120_000)
        assertNull("Shuffling within the parking space does not count", detector.onLocation(tenMetresOn, 130_000))
        assertEquals(false, detector.onLocation(twoHundredMetresOn, 140_000))
        assertFalse(detector.parked)
    }

    @Test
    fun chargingMeansParkedAtOnceAndUntilItStops() {
        val detector = ParkedDetector()
        detector.onLocation(spot, 0)
        assertEquals(true, detector.onCharging(true))
        assertNull("GPS drift while plugged in is ignored", detector.onLocation(twoHundredMetresOn, 10_000))
        assertNull(detector.onCharging(false))
        assertEquals(false, detector.onLocation(Fixtures.at(31.0), 20_000))
    }
}
