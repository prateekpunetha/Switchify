package dev.prateekpunetha.switchify;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.widget.Toolbar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.materialswitch.MaterialSwitch;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "RelayControlActivity";
    private static final String ESP8266_IP = "192.168.51.114";

    private MaterialSwitch relay1Switch, relay2Switch;
    private TextView relay1Text, relay2Text;
    private MaterialCardView relay1Container, relay2Container;
    private RequestQueue queue;
    private SharedPreferences sharedPreferences;
    private MaterialCardView selectedRelayContainer = null;
    private int selectedRelayNumber = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar appToolbar = ( Toolbar) findViewById(R.id.main_toolbar);
        setSupportActionBar(appToolbar);

        // handle overlaps using insets
        ViewCompat.setOnApplyWindowInsetsListener(appToolbar, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            mlp.leftMargin = insets.left;
            mlp.topMargin = insets.top;
            mlp.rightMargin = insets.right;
            v.setLayoutParams(mlp);
            return WindowInsetsCompat.CONSUMED;
        });

        relay1Switch = findViewById(R.id.relay1Switch);
        relay2Switch = findViewById(R.id.relay2Switch);
        relay1Text = findViewById(R.id.relay1Text);
        relay2Text = findViewById(R.id.relay2Text);
        relay1Container = findViewById(R.id.relay1Container);
        relay2Container = findViewById(R.id.relay2Container);

        queue = Volley.newRequestQueue(this);
        sharedPreferences = getSharedPreferences("RelayPreferences", MODE_PRIVATE);

        // Load saved switch states and names
        loadSwitchStates();
        loadRelayNames();

        // Setup long press listeners
        setupLongPressListeners();

        // Fetch initial state from ESP8266
        fetchRelayState();

        relay1Switch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            toggleRelay("/relay1", isChecked);
            saveSwitchState("relay1", isChecked);
        });

        relay2Switch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            toggleRelay("/relay2", isChecked);
            saveSwitchState("relay2", isChecked);
        });
    }

    private void setupLongPressListeners() {
        relay1Container.setOnLongClickListener(v -> {
            if (selectedRelayContainer != null) {
                selectedRelayContainer.setChecked(false);
            }
            selectedRelayContainer = relay1Container;
            selectedRelayNumber = 1;
            relay1Container.setChecked(true);
            if(renameMenuItem != null){
                renameMenuItem.setVisible(true);
            }
            return true;
        });

        relay2Container.setOnLongClickListener(v -> {
            if (selectedRelayContainer != null) {
                selectedRelayContainer.setChecked(false);
            }
            selectedRelayContainer = relay2Container;
            selectedRelayNumber = 2;
            relay2Container.setChecked(true);
            if(renameMenuItem != null){
                renameMenuItem.setVisible(true);
            }
            return true;
        });

        // Add click listener to clear selection
        View mainContainer = findViewById(android.R.id.content);
        mainContainer.setOnClickListener(v -> clearSelection());
    }

    private void clearSelection() {
        if (selectedRelayContainer != null) {
            selectedRelayContainer.setChecked(false);
            selectedRelayContainer = null;
            selectedRelayNumber = -1;
        }
        if(renameMenuItem != null){
            renameMenuItem.setVisible(false);
        }
    }

    private MenuItem renameMenuItem;
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        renameMenuItem = menu.findItem(R.id.menu_action_edit);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_action_edit) {
            if (selectedRelayNumber != -1) {
                showRenameDialog(selectedRelayNumber);
                clearSelection();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showRenameDialog(int relayNumber) {
        TextInputLayout textInputLayout = new TextInputLayout(this, null, com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox);

        TextInputEditText editText = new TextInputEditText(textInputLayout.getContext());
        textInputLayout.addView(editText);

        String currentName = relayNumber == 1 ?
                relay1Text.getText().toString() :
                relay2Text.getText().toString();

        editText.setText(currentName);
        editText.setSelection(currentName.length());

        // Add padding to the TextInputLayout
        int padding = getResources().getDimensionPixelSize(android.R.dimen.app_icon_size) / 2;
        textInputLayout.setPadding(padding, 0, padding, 0);

        new MaterialAlertDialogBuilder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setTitle("Rename")
                .setView(textInputLayout)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newName = editText.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        saveRelayName(relayNumber, newName);
                        updateRelayName(relayNumber, newName);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveRelayName(int relayNumber, String name) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("relay" + relayNumber + "_name", name);
        editor.apply();
    }

    private void updateRelayName(int relayNumber, String name) {
        if (relayNumber == 1) {
            relay1Text.setText(name);
        } else {
            relay2Text.setText(name);
        }
    }

    private void loadRelayNames() {
        String relay1Name = sharedPreferences.getString("relay1_name", "Relay 1");
        String relay2Name = sharedPreferences.getString("relay2_name", "Relay 2");

        relay1Text.setText(relay1Name);
        relay2Text.setText(relay2Name);
    }

    private void fetchRelayState() {
        fetchStateForSwitch("/relay1/state", relay1Switch);
        fetchStateForSwitch("/relay2/state", relay2Switch);
    }

    private void fetchStateForSwitch(String endpoint, MaterialSwitch switchMaterial) {
        String url = "http://" + ESP8266_IP + endpoint;

        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    switchMaterial.setOnCheckedChangeListener(null);
                    switchMaterial.setChecked("on".equals(response.trim()));
                    switchMaterial.setOnCheckedChangeListener((buttonView, isChecked) ->
                            toggleRelay(endpoint.replace("/state", ""), isChecked));
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
        boolean relay1State = sharedPreferences.getBoolean("relay1", false);
        boolean relay2State = sharedPreferences.getBoolean("relay2", false);

        relay1Switch.setChecked(relay1State);
        relay2Switch.setChecked(relay2State);
    }
}
