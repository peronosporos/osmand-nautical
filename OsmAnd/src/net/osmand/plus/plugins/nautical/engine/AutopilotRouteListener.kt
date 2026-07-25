package net.osmand.plus.plugins.nautical.engine

import net.osmand.data.ValueHolder
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.routing.IRouteInformationListener
import net.osmand.plus.routing.RoutingHelper

class AutopilotRouteListener(
    private val routingHelper: RoutingHelper,
) : IRouteInformationListener {

    override fun newRouteIsCalculated(newRoute: Boolean, showToast: ValueHolder<Boolean>) {
        updateAutopilot()
    }

    override fun routeWasCancelled() {
        NauticalPlugin.autopilot?.stopNavigation()
    }

    override fun routeWasFinished() {
        NauticalPlugin.autopilot?.stopNavigation()
    }

    private fun updateAutopilot() {
        val app = routingHelper.application
        val targetPoints = app.targetPointsHelper
        val points = mutableListOf<Pair<Double, Double>>()

        // Collect all remaining waypoints including intermediates
        targetPoints.intermediatePointsNavigation.forEach { pt ->
            points.add(Pair(pt.latitude, pt.longitude))
        }
        targetPoints.pointToNavigate?.let { pt ->
            points.add(Pair(pt.latitude, pt.longitude))
        }

        if (points.isNotEmpty()) {
            NauticalPlugin.engine?.loadRoute(points)
            NauticalPlugin.autopilot?.engageSmart()
        }
    }
}