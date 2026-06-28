package net.osmand.plus.plugins.nautical

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import net.osmand.plus.R

class NauticalSettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_nautical_settings)

        val ipInput = findViewById<EditText>(R.id.nautical_ip_input)
        val portInput = findViewById<EditText>(R.id.nautical_port_input)
        val saveButton = findViewById<Button>(R.id.nautical_save_button)

        val prefs = getSharedPreferences("nautical_prefs", Context.MODE_PRIVATE)
        ipInput.setText(prefs.getString("server_ip", ""))
        portInput.setText(prefs.getString("server_port", "3000"))

        saveButton.setOnClickListener {
            prefs.edit()
                .putString("server_ip", ipInput.text.toString())
                .putString("server_port", portInput.text.toString())
                .apply()

            Toast.makeText(this, "Settings Saved!", Toast.LENGTH_SHORT).show()
            finish() // Closes the screen and returns to the map
        }
    }
}