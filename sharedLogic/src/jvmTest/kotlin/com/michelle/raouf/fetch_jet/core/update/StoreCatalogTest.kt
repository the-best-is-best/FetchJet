package com.michelle.raouf.fetch_jet.core.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StoreCatalogTest {

    @Test
    fun readsTheNewestPackageVersion() {
        val json = """{"Products":[{"DisplaySkuAvailabilities":[{"Sku":{"Properties":{"Packages":[
            {"PackageFullName":"MichelleRaouf.FetchJet_1.4.1.0_x64__7hajc9fhnzk5a"},
            {"PackageFullName":"MichelleRaouf.FetchJet_1.5.0.0_x64__7hajc9fhnzk5a"},
            {"PackageFullName":"MichelleRaouf.FetchJet_1.10.2.0_arm64__7hajc9fhnzk5a"}]}}}]}]}"""
        assertEquals("1.10.2", storeVersionFromCatalog(json))
    }

    @Test
    fun noPackageMeansNoVersion() {
        assertNull(storeVersionFromCatalog("""{"Products":[]}"""))
    }
}
