package com.michelle.raouf.fetch_jet.core.telemetry

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmulatorCheckTest {

    @Test
    fun theOwnersEmulatorIsAnEmulator() {
        assertTrue(
            looksLikeEmulator(
                "google/sdk_gphone64_x86_64/emu64xa:16/BE4B.251210.005/14574095:user/release-keys",
                "ranchu", "sdk_gphone64_x86_64", "sdk_gphone64_x86_64"
            )
        )
    }

    @Test
    fun olderEmulatorsAreEmulators() {
        assertTrue(looksLikeEmulator("generic/sdk/generic:9/PSR1/1:userdebug/test-keys", "goldfish", "sdk", "Android SDK built for x86"))
        assertTrue(looksLikeEmulator("google/sdk_gphone_arm64/emulator64_arm64:12/S2B2/1:userdebug/dev-keys", "ranchu", "sdk_gphone_arm64", "sdk_gphone_arm64"))
    }

    @Test
    fun realPhonesAreNot() {
        assertFalse(looksLikeEmulator("samsung/a56xnsxx/a56x:16/BP2A.250605.031.A3/A566BXXU3BYK1:user/release-keys", "s5e8855", "a56xnsxx", "SM-A566B"))
        assertFalse(looksLikeEmulator("google/husky/husky:16/BP3A.251005.004/14179870:user/release-keys", "husky", "husky", "Pixel 8 Pro"))
        assertFalse(looksLikeEmulator("Xiaomi/garnet_global/garnet:15/AQ3A.240912.001/OS2.0.5.0:user/release-keys", "qcom", "garnet_global", "23117RA68G"))
    }
}
