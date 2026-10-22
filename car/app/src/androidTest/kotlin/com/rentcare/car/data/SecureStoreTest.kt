package com.rentcare.car.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rentcare.core.Place
import com.rentcare.core.Powertrain
import com.rentcare.core.Rental
import com.rentcare.core.ReturnRule
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class SecureStoreTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val box = SecureBox("rentcare_test_key")

    @Before fun clean() {
        RentalStore(ctx).clearAll()
    }

    @Test fun roundTrip() {
        assertEquals("12하3456 제주", box.decrypt(box.encrypt("12하3456 제주")))
    }

    @Test fun freshNoncePerEncryption() {
        assertNotEquals(box.encrypt("same"), box.encrypt("same"))
    }

    @Test fun tamperedCiphertextIsRejected() {
        val c = box.encrypt("secret").toCharArray()
        c[c.size - 3] = if (c[c.size - 3] == 'A') 'B' else 'A'
        assertNull(box.decrypt(String(c)))
    }

    @Test fun rentalIsNotStoredInPlaintext() {
        val store = RentalStore(ctx)
        val rental = Rental(
            rentalId = "R-PLAIN-CHECK", vendorId = "demo-jeju", vendorName = "데모", vendorPhone = "064-000-0000",
            vehiclePlate = "99하9999", vehicleModel = "IONIQ 5", powertrain = Powertrain.EV,
            rule = ReturnRule.EvChargeWaived, returnPlace = Place("제주공항", 33.5, 126.4), returnAtMillis = 0,
        )
        store.saveRental(rental)
        assertEquals(rental, store.loadRental())
        val xml = File(ctx.applicationInfo.dataDir, "shared_prefs/rentcare.xml").readText()
        assertFalse(xml.contains("99하9999") || xml.contains("R-PLAIN-CHECK"), "plaintext leaked: $xml")
    }

    @Test fun plaintextFromOlderVersionIsDroppedNotRead() {
        ctx.getSharedPreferences("rentcare", Context.MODE_PRIVATE).edit()
            .putString("rental", """{"rentalId":"OLD"}""").commit()
        assertNull(RentalStore(ctx).loadRental())
        assertFalse(ctx.getSharedPreferences("rentcare", Context.MODE_PRIVATE).contains("rental"))
    }
}
