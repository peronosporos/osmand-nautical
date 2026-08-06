package net.osmand.plus.plugins.nautical.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import net.osmand.plus.R
import net.osmand.plus.base.BaseOsmAndFragment
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.network.SignalKRoute

class SignalKServerRoutesFragment : BaseOsmAndFragment() {

    private lateinit var adapter: RoutesAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = themedInflater.inflate(R.layout.recyclerview_fragment, container, false)
        val recyclerView: RecyclerView = view.findViewById(R.id.recycler_view)
        
        adapter = RoutesAdapter(
            onNavigate = { routeId, route -> navigateWithOsmAnd(routeId, route) },
            onPush = { routeId, route -> pushToAutopilot(routeId, route) }
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        refreshRoutes()

        return view
    }

    private fun refreshRoutes() {
        lifecycleScope.launch {
            val routes = NauticalPlugin.engine?.fetchRoutesFromServer()
            if (routes != null) {
                adapter.submitList(routes.entries.toList())
                view?.findViewById<View>(R.id.txt_empty_list)?.visibility = if (routes.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun navigateWithOsmAnd(id: String, route: SignalKRoute) {
        lifecycleScope.launch {
            val fullRoute = NauticalPlugin.engine?.getRestService()?.getRouteById(id)?.body() ?: route
            val points = fullRoute.feature.geometry.coordinates.map { Pair(it[1], it[0]) }
            if (points.isNotEmpty()) {
                // Bridge to OsmAnd navigation
                // For now, load into Engine which NauticalPlugin observes
                NauticalPlugin.engine?.loadRoute(points)
                app.showToastMessage(R.string.nautical_navigate_with_osmand)
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
        private val onPush: (String, SignalKRoute) -> Unit
    ) : ListAdapter<Map.Entry<String, SignalKRoute>, RouteViewHolder>(DiffCallback()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RouteViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_nautical_server_route, parent, false)
            return RouteViewHolder(view)
        }

        override fun onBindViewHolder(holder: RouteViewHolder, position: Int) {
            holder.bind(getItem(position), onNavigate, onPush)
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

        fun bind(entry: Map.Entry<String, SignalKRoute>, onNav: (String, SignalKRoute) -> Unit, onP: (String, SignalKRoute) -> Unit) {
            val route = entry.value
            txtName.text = route.name ?: "Unnamed Route"
            txtDesc.text = route.description ?: ""
            btnNavigate.setOnClickListener { onNav(entry.key, route) }
            btnPush.setOnClickListener { onP(entry.key, route) }
        }
    }
}
