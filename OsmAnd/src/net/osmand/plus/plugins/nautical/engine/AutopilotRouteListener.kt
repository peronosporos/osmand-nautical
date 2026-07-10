package net.osmand.plus.plugins.nautical.engine

import net.osmand.data.ValueHolder
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.routing.IRouteInformationListener
import net.osmand.plus.routing.RoutingHelper

class AutopilotRouteListener(
    private val routingHelper: RoutingHelper
) : IRouteInformationListener {

    override fun newRouteIsCalculated(newRoute: Boolean, showToast: ValueHolder<Boolean>) {
        updateAutopilot()
    }

    override fun routeWasCancelled() {}
    override fun routeWasFinished() {}

    private fun updateAutopilot() {
        // Accessing the TargetPointsHelper from the app instance
        val app = routingHelper.application
        val targetPoints = app.targetPointsHelper

        // This method retrieves the destination point
        val point = targetPoints.pointToNavigate

        if (point != null) {
            // Using the correct getter methods for your specific version
            val latitude = point.latitude
            val longitude = point.longitude

            NauticalPlugin.autopilot?.sendActiveWaypoint(latitude, longitude)
        }
    }
}