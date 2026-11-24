package net.osmand.plus.plugins.nautical.ui.cockpit

import android.content.Context
import android.view.View
import android.view.ViewGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the Cockpit screen registry contract: unique concern IDs, declaration-order
 * selector semantics, and lookup behaviour. The fake pane lives here so no test
 * scaffolding pollutes production sources.
 */
class CockpitScreenRegistryTest {

    private class TestPane(override val paneId: CockpitScreenId) : CockpitScreen {
        override fun onCreateView(context: Context, parent: ViewGroup): View {
            throw UnsupportedOperationException("not used in this test")
        }

        override fun onStart() {}
        override fun onStop() {}
    }

    @Test
    fun `registry keeps declaration order for the selector`() {
        val registry = CockpitScreenRegistry()
        registry.register(TestPane(CockpitScreenId.HELM))
        registry.register(TestPane(CockpitScreenId.INSTRUMENTS))

        assertEquals(listOf(CockpitScreenId.HELM, CockpitScreenId.INSTRUMENTS), registry.available())
    }

    @Test
    fun `duplicate concern registration is rejected`() {
        val registry = CockpitScreenRegistry()
        registry.register(TestPane(CockpitScreenId.VHF))

        val error = runCatching {
            registry.register(TestPane(CockpitScreenId.VHF))
        }.exceptionOrNull()

        assertTrue("expected IllegalArgumentException", error is IllegalArgumentException)
        // The original pane survives the rejected registration.
        assertEquals(CockpitScreenId.VHF, registry.pane(CockpitScreenId.VHF)?.paneId)
    }

    @Test
    fun `lookup of an unregistered concern returns null`() {
        val registry = CockpitScreenRegistry()
        assertNull(registry.pane(CockpitScreenId.WATCHKEEPER))
        assertTrue(registry.isEmpty())
        assertTrue(registry.available().isEmpty())
    }
}
