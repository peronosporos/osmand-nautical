package net.osmand.plus.plugins.nautical.ui.sail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.osmand.plus.R
import net.osmand.plus.base.BaseOsmAndFragment
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.Sail
import net.osmand.plus.plugins.nautical.engine.activeSailEfficiency

class SailInventoryFragment : BaseOsmAndFragment() {

    private lateinit var adapter: SailInventoryAdapter
    private val pendingToggles = mutableMapOf<String, Boolean>()
    private var txtEfficiency: TextView? = null
    private var txtEmpty: TextView? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = themedInflater.inflate(R.layout.fragment_sail_inventory, container, false)

        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        view.findViewById<View>(R.id.close_button)?.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        view.findViewById<TextView>(R.id.toolbar_title)?.text = getString(R.string.nautical_sail_inventory)

        txtEfficiency = view.findViewById(R.id.txt_efficiency_value)
        txtEmpty = view.findViewById(R.id.txt_empty_list)

        val recyclerView: RecyclerView = view.findViewById(R.id.recycler_view)
        adapter = SailInventoryAdapter(
            onSailToggle = { sail -> toggleSail(sail) },
            onReefChange = { sailId, reefs -> updateReefs(sailId, reefs) }
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            NauticalPlugin.engine?.marineStateFlow?.collectLatest { state ->
                val efficiencyPct = (state.activeSailEfficiency * 100.0).toInt()
                txtEfficiency?.text = getString(R.string.nautical_sail_efficiency_label, efficiencyPct)

                val rawSails = if (state.sailInventory.isNotEmpty()) {
                    state.sailInventory
                } else {
                    getDefaultSailInventory(state.reefs ?: 0)
                }

                val sails = rawSails.map { sail ->
                    val pending = pendingToggles[sail.id]
                    if (pending != null) {
                        if (pending == sail.active) {
                            pendingToggles.remove(sail.id)
                            sail
                        } else {
                            sail.copy(active = pending)
                        }
                    } else sail
                }

                val listItems = buildCategorizedSailList(sails)
                adapter.submitList(listItems)
                txtEmpty?.visibility = if (listItems.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        return view
    }

    private fun getDefaultSailInventory(currentReefs: Int): List<Sail> {
        return listOf(
            // Mainsail
            Sail("main_full", "Full Mainsail", "Mainsail", active = true, area = 1.00, reefs = currentReefs, maxReefs = 3),
            // Headsails
            Sail("headsail_genoa", "Genoa (140%)", "Genoa", active = true, area = 1.40, reefs = 0, maxReefs = 0),
            Sail("headsail_jib", "Working Jib (100%)", "Working Jib", active = false, area = 1.00, reefs = 0, maxReefs = 0),
            Sail("headsail_staysail", "Solent / Staysail (75%)", "Staysail", active = false, area = 0.75, reefs = 0, maxReefs = 0),
            Sail("headsail_storm_jib", "Storm Jib (30%)", "Storm Jib", active = false, area = 0.30, reefs = 0, maxReefs = 0),
            // Downwind / Reaching
            Sail("downwind_code_zero", "Code Zero", "Code Zero", active = false, area = 1.50, reefs = 0, maxReefs = 0),
            Sail("downwind_asym_spin", "Asymmetric Spinnaker", "Asymmetric Spinnaker", active = false, area = 1.80, reefs = 0, maxReefs = 0),
            Sail("downwind_sym_spin", "Symmetrical Spinnaker", "Symmetrical Spinnaker", active = false, area = 1.80, reefs = 0, maxReefs = 0)
        )
    }

    private fun buildCategorizedSailList(sails: List<Sail>): List<SailListItem> {
        val items = mutableListOf<SailListItem>()

        val mainSails = mutableListOf<Sail>()
        val headsails = mutableListOf<Sail>()
        val downwindSails = mutableListOf<Sail>()

        sails.forEach { sail ->
            val type = sail.type.lowercase()
            val name = sail.name.lowercase()
            when {
                type.contains("main") || name.contains("main") -> mainSails.add(sail)
                type.contains("spin") || name.contains("spin") || type.contains("code") || name.contains("code") || type.contains("gennaker") -> downwindSails.add(sail)
                else -> headsails.add(sail)
            }
        }

        if (mainSails.isNotEmpty()) {
            items.add(SailListItem.CategoryHeader(R.string.nautical_sail_category_main, "Mainsail"))
            mainSails.forEach { sail ->
                items.add(SailListItem.SailItem(sail, SailCategory.MAIN, isMainsail = true))
            }
        }

        if (headsails.isNotEmpty()) {
            items.add(SailListItem.CategoryHeader(R.string.nautical_sail_category_headsail, "Headsails"))
            headsails.forEach { sail ->
                items.add(SailListItem.SailItem(sail, SailCategory.HEADSAIL, isMainsail = false))
            }
        }

        if (downwindSails.isNotEmpty()) {
            items.add(SailListItem.CategoryHeader(R.string.nautical_sail_category_downwind, "Downwind / Reaching"))
            downwindSails.forEach { sail ->
                items.add(SailListItem.SailItem(sail, SailCategory.DOWNWIND, isMainsail = false))
            }
        }

        return items
    }

    private fun updateReefs(sailId: String?, reefs: Int) {
        val engine = NauticalPlugin.engine
        val controlManager = engine?.controlManager
        controlManager?.setSailReefs(sailId, reefs)
        
        lifecycleScope.launch {
            val path = if (sailId != null) "sails.inventory.$sailId.reefs" else "sails.reefs"
            engine?.sendDelta(path, reefs)
        }
    }

    private fun toggleSail(sail: Sail) {
        val nextState = !sail.active
        pendingToggles[sail.id] = nextState

        val engine = NauticalPlugin.engine
        val controlManager = engine?.controlManager
        controlManager?.setSailActive(sail.id, nextState)

        engine?.getCurrentState()?.let { state ->
            val updatedInventory = state.sailInventory.map { s ->
                if (s.id == sail.id) s.copy(active = nextState) else s
            }
            val listItems = buildCategorizedSailList(updatedInventory)
            adapter.submitList(listItems)
        }

        lifecycleScope.launch {
            engine?.sendDelta("sails.inventory.${sail.id}.active", nextState)
            engine?.sendDelta("steering.sails.active.${sail.id}", nextState)
        }
    }
}
