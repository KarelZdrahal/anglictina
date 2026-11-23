package cz.anglictina.kurz

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpeechTextTest {
    @Test fun abbreviationsAreSpokenInFull() {
        assertEquals("lend somebody money", Speech.spokenForm("lend sb money"))
        assertEquals("take after somebody", Speech.spokenForm("take after sb"))
        assertEquals("be a stickler for something", Speech.spokenForm("be a stickler for sth"))
        assertEquals("show somebody the ropes", Speech.spokenForm("show sb the ropes"))
        assertEquals("get on somebody's nerves", Speech.spokenForm("get on sb's nerves"))
        assertEquals("make up for something", Speech.spokenForm("make up for sth."))
        assertEquals("a subway", Speech.spokenForm("a subway"))
    }
}
