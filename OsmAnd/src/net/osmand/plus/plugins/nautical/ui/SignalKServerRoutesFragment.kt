package net.osmand.plus.plugins.nautical.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import net.osmand.PlatformUtil
import net.osmand.plus.R
import net.osmand.plus.base.BaseOsmAndFragment
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.network.SignalKRoute
import org.apache.commons.logging.Log

class SignalKServerRoutesFragment : BaseOsmAndFragment() {

    private val log: Log = PlatformUtil.getLog(SignalKServerRoutesFragment::class.java)
    private lateinit var adapter: RoutesAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = themedInflater.inflate(R.layout.fragment_signalk_server_resources, container, false)
        val recyclerView: RecyclerView = view.findViewById(R.id.recycler_view)
        
        adapter = RoutesAdapter(
            onNavigate = { routeId, route -> navigateWithOsmAnd(routeId, route) },
            onPush = { routeId, route -> pushToAutopilot(routeId, route) },
            onDelete = { routeId, route -> confirmDeleteRoute(routeId, route) }
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        view.findViewById<MaterialButton?>(R.id.btn_empty_secondary)?.setOnClickListener {
            net.osmand.plus.settings.fragments.BaseSettingsFragment.showInstance(requireActivity(), net.osmand.plus.settings.fragments.SettingsScreenType.NAUTICAL_SETTINGS)
        }

        refreshRoutes()

        return view
    }

    private fun refreshRoutes() {
        lifecycleScope.launch {
            val root = view ?: return@launch
            val emptyLayout = root.findViewById<View>(R.id.layout_empty_state)
            val txtTitle = root.findViewById<TextView>(R.id.txt_empty_title)
            val txtDesc = root.findViewById<TextView>(R.id.txt_empty_desc)
            val btnPrimary = root.findViewById<MaterialButton>(R.id.btn_empty_primary)
            val btnSecondary = root.findViewById<MaterialButton>(R.id.btn_empty_secondary)
            val engine = NauticalPlugin.engine
            val serverIp = app.settings.NAUTICAL_SERVER_IP.get() ?: ""
            val serverPort = app.settings.NAUTICAL_SERVER_PORT.get() ?: "3000"

            if (engine == null) {
                adapter.submitList(emptyList())
                emptyLayout?.visibility = View.VISIBLE
                txtTitle?.text = "Signal K Server Disconnected"
                txtDesc?.text = "Unable to connect to Signal K server at $serverIp:$serverPort. Please verify connection settings."
                btnPrimary?.text = "Retry Connection"
                btnPrimary?.setOnClickListener { refreshRoutes() }
                btnSecondary?.visibility = View.VISIBLE
                return@launch
            }
            try {
                val routes = engine.fetchRoutesFromServer()
                if (routes != null && routes.isNotEmpty()) {
                    adapter.submitList(routes.entries.toList())
                    emptyLayout?.visibility = View.GONE
                } else {
                    adapter.submitList(emptyList())
                    emptyLayout?.visibility = View.VISIBLE
                    txtTitle?.text = getString(R.string.nautical_server_no_routes_title)
                    txtDesc?.text = "No routes found on the Signal K server. Create routes in OsmAnd or upload them to your server using the Resource Manager."
                    btnPrimary?.text = getString(R.string.nautical_server_refresh_btn)
                    btnPrimary?.setOnClickListener { refreshRoutes() }
                    btnSecondary?.visibility = View.VISIBLE
                }
            } catch (_: Exception) {
                adapter.submitList(emptyList())
                emptyLayout?.visibility = View.VISIBLE
                txtTitle?.text = "Connection Failed"
                txtDesc?.text = "Could not fetch routes from $serverIp:$serverPort. Check connection."
                btnPrimary?.text = "Retry"
                btnPrimary?.setOnClickListener { refreshRoutes() }
                btnSecondary?.visibility = View.VISIBLE
            }
        }
    }

    private fun confirmDeleteRoute(id: String, route: SignalKRoute) {
        AlertDialog.Builder(requireContext())
            .setMessage("Are you sure you want to delete route '${route.name ?: id}' from Signal K server?")
            .setPositiveButton(R.string.shared_string_delete) { _, _ ->
                lifecycleScope.launch {
                    try {
                        NauticalPlugin.engine?.deleteRouteFromServer(id)
                        refreshRoutes()
                    } catch (e: Exception) {
                        log.error("Failed to delete route $id from Signal K server: ${e.message}", e)
                        app.showToastMessage("Failed to delete route: ${e.message}")
                    }
                }
            }
            .setNegativeButton(R.string.shared_string_cancel, null)
            .show()
    }

    private fun navigateWithOsmAnd(id: String, route: SignalKRoute) {
        lifecycleScope.launch {
            try {
                val fullRoute = NauticalPlugin.engine?.getRestService()?.getRouteById(id)?.body() ?: route
                val points = fullRoute.feature.geometry.coordinates.map { Pair(it[1], it[0]) }
                if (points.isNotEmpty()) {
                    NauticalPlugin.engine?.loadRoute(points)
                    try {
                        val targetPointsHelper = app.targetPointsHelper
                        targetPointsHelper.removeAllWayPoints(false, true)
                        val lastPoint = points.last()
                        points.drop(1).dropLast(1).forEach { (lat, lon) ->
                            targetPointsHelper.navigateToPoint(net.osmand.data.LatLon(lat, lon), false, -1)
                        }
                        targetPointsHelper.navigateToPoint(net.osmand.data.LatLon(lastPoint.first, lastPoint.second), true, -1)
                        app.showToastMessage(R.string.nautical_navigate_with_osmand)
                    } catch (e: Exception) {
                        log.error("Failed to set navigation waypoints for route $id: ${e.message}", e)
                        app.showToastMessage("Failed to set route waypoints: ${e.message}")
                    }
                } else {
                    app.showToastMessage("Route contains no valid waypoints")
                }
            } catch (e: Exception) {
                log.error("Failed to load route $id for navigation: ${e.message}", e)
                app.showToastMessage("Failed to load route: ${e.message}")
            }
        }
    }

    private fun pushToAutopilot(id: String, route: SignalKRoute) {
        lifecycleScope.launch {
            val rest = NauticalPlugin.engine?.getRestService() ?: return@launch
            val fullRoute = rest.getRouteById(id).body() ?: route
            val points = fullRoute.feature.geometry.coordinates.map { Pair(it[1], it[0]) }
            
            if (points.isNotEmpty()) {
                val caps = NauticalPlugin.getInstance()?.capabilityManager?.capabilities?.value
                if (caps?.hasAutopilot == true) {
                    // Modern v2 Push
                    val course = net.osmand.plus.plugins.nautical.network.SignalKCourse(
                        activeRoute = net.osmand.plus.plugins.nautical.network.SignalKActiveRoute(
                            href = "/resources/routes/$id"
                        )
                    )
                    val response = rest.updateCourse(course)
                    if (response.isSuccessful) {
                        app.showToastMessage(R.string.nautical_push_to_autopilot)
                    } else {
                        // Fallback to mode toggle
                        NauticalPlugin.autopilot?.setAutopilotMode("track")
                        app.showToastMessage(R.string.nautical_push_to_autopilot)
                    }
                } else {
                    // Legacy v1 Push
                    NauticalPlugin.engine?.loadRoute(points)
                    NauticalPlugin.autopilot?.setAutopilotMode("track")
                    app.showToastMessage(R.string.nautical_push_to_autopilot)
                }
            }
        }
    }

    private class RoutesAdapter(
        private val onNavigate: (String, SignalKRoute) -> Unit,
        private val onPush: (String, SignalKRoute) -> Unit,
        private val onDelete: (String, SignalKRoute) -> Unit
    ) : ListAdapter<Map.Entry<String, SignalKRoute>, RouteViewHolder>(DiffCallback()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RouteViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_nautical_server_route, parent, false)
            return RouteViewHolder(view)
        }

        override fun onBindViewHolder(holder: RouteViewHolder, position: Int) {
            holder.bind(getItem(position), onNavigate, onPush, onDelete)
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<Map.Entry<String, SignalKRoute>>() {
        override fun areItemsTheSame(oldItem: Map.Entry<String, SignalKRoute>, newItem: Map.Entry<String, SignalKRoute>): Boolean = oldItem.key == newItem.key
        override fun areContentsTheSame(oldItem: Map.Entry<String, SignalKRoute>, newItem: Map.Entry<String, SignalKRoute>): Boolean = oldItem.value == newItem.value
    }

    private class RouteViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val txtName: TextView = view.findViewById(R.id.txt_route_name)
        private val txtDesc: TextView = view.findViewById(R.id.txt_route_desc)
        private val btnNavigate: MaterialButton = view.findViewById(R.id.btn_navigate)
        private val btnPush: MaterialButton = view.findViewById(R.id.btn_push)
        private val btnDelete: MaterialButton = view.findViewById(R.id.btn_delete_route)

        fun bind(entry: Map.Entry<String, SignalKRoute>, onNav: (String, SignalKRoute) -> Unit, onP: (String, SignalKRoute) -> Unit, onDel: (String, SignalKRoute) -> Unit) {
            val route = entry.value
            txtName.text = route.name ?: "Unnamed Route"
            txtDesc.text = route.description ?: ""
            btnNavigate.setOnClickListener { onNav(entry.key, route) }
            btnPush.setOnClickListener { onP(entry.key, route) }
            btnDelete.setOnClickListener { onDel(entry.key, route) }
        }
    }
}

