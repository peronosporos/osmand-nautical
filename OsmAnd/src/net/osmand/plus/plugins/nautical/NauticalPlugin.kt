package net.osmand.plus.plugins.nautical


import android.util.Log

import android.content.Context

import android.content.Intent

import net.osmand.plus.OsmandApplication

import net.osmand.plus.R

import net.osmand.plus.plugins.OsmandPlugin

import net.osmand.plus.plugins.nautical.engine.OkHttpSignalKConnection

import net.osmand.plus.plugins.nautical.engine.SignalKEngine


class NauticalPlugin(app: OsmandApplication) : OsmandPlugin(app) {


    companion object {

        const val NAUTICAL_ID = "osmand.nautical"

    }


// We MUST keep the engine and connection alive here

    private val connection = OkHttpSignalKConnection()

    val engine = SignalKEngine(connection)


    override fun getId(): String = NAUTICAL_ID


    override fun getName(): String = "Nautical Marine Controls"


    override fun getDescription(linksEnabled: Boolean): CharSequence {

        return "Connects directly to your SignalK server to stream live telemetry, map AIS targets, and provide direct autopilot tracking controls."

    }


    override fun getLogoResourceId(): Int {

        return R.drawable.ic_action_sail_boat_dark

    }


    override fun setEnabled(enabled: Boolean) {

        super.setEnabled(enabled)

        if (enabled) {

            Log.d("NauticalPlugin", "Plugin activated.")

        } else {

            connection.disconnect()

        }

    }


    override fun getSettingsScreenType(): net.osmand.plus.settings.fragments.SettingsScreenType? {

        return net.osmand.plus.settings.fragments.SettingsScreenType.NAUTICAL_SETTINGS

    }

}

