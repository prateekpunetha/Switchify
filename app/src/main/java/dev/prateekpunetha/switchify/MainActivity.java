package dev.prateekpunetha.switchify;

import android.app.Application;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.materialswitch.MaterialSwitch;



public class MainActivity extends AppCompatActivity {
    private static final String TAG = "RelayControlActivity";
    private static final String ESP8266_IP = "192.168.51.114";

    private MaterialSwitch relay1Switch, relay2Switch;
    private RequestQueue queue;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        relay1Switch = findViewById(R.id.relay1Switch);
        relay2Switch = findViewById(R.id.relay2Switch);
        queue = Volley.newRequestQueue(this);
        sharedPreferences = getSharedPreferences("RelayPreferences", MODE_PRIVATE);

        // Load saved switch states
        loadSwitchStates();

        // Fetch initial state from ESP8266
        fetchRelayState();

        relay1Switch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            toggleRelay("/relay1", isChecked);
            saveSwitchState("relay1", isChecked); // Save state
        });

        relay2Switch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            toggleRelay("/relay2", isChecked);
            saveSwitchState("relay2", isChecked); // Save state
        });
    }

    private void fetchRelayState() {
        fetchStateForSwitch("/relay1/state", relay1Switch);
        fetchStateForSwitch("/relay2/state", relay2Switch);
    }

    private void fetchStateForSwitch(String endpoint, MaterialSwitch switchMaterial) {
        String url = "http://" + ESP8266_IP + endpoint;

        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    switchMaterial.setOnCheckedChangeListener(null); // Temporarily remove listener
                    switchMaterial.setChecked("on".equals(response.trim()));
                    switchMaterial.setOnCheckedChangeListener((buttonView, isChecked) -> toggleRelay(endpoint, isChecked));
                },
                error -> Log.e(TAG, "Error fetching state: " + error.getMessage()));

        queue.add(request);
    }

    private void toggleRelay(String endpoint, boolean isChecked) {
        String url = "http://" + ESP8266_IP + endpoint + "?state=" + (isChecked ? "on" : "off");

        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> Log.d(TAG, "Response: " + response),
                error -> Log.e(TAG, "Error: " + error.getMessage()));

        queue.add(request);
    }

    private void saveSwitchState(String relayKey, boolean isChecked) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(relayKey, isChecked);
        editor.apply();
    }

    private void loadSwitchStates() {
        boolean relay1State = sharedPreferences.getBoolean("relay1", false); // Default is false (off)
        boolean relay2State = sharedPreferences.getBoolean("relay2", false); // Default is false (off)

        relay1Switch.setChecked(relay1State);
        relay2Switch.setChecked(relay2State);
    }
}
