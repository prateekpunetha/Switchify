package dev.prateekpunetha.switchify;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.widget.Toolbar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "RelayControlActivity";
    private static final String ESP8266_IP = "192.168.51.33";
    private static final int ANIMATION_DURATION = 300;

    private MaterialSwitch relay1Switch, relay2Switch;
    private TextView relay1Text, relay2Text;
    private MaterialCardView relay1Container, relay2Container;
    private RequestQueue queue;
    private SharedPreferences sharedPreferences;
    private MaterialCardView selectedRelayContainer = null;
    private int selectedRelayNumber = -1;

    private MenuItem renameMenuItem, timerMenuItem, timerDisplayMenuItem;
    private Map<Integer, Timer> relayTimers = new HashMap<>();
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar appToolbar = findViewById(R.id.main_toolbar);
        setSupportActionBar(appToolbar);


        // Handle overlaps using insets
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
            if (!buttonView.isPressed()) {
                animateSwitch(relay1Switch, isChecked);
            }
            toggleRelay("/relay1", isChecked);
            saveSwitchState("relay1", isChecked);
            cancelTimer(1);
            updateRelayVisuals(relay1Container, isChecked);
        });

        relay2Switch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!buttonView.isPressed()) {
                animateSwitch(relay2Switch, isChecked);
            }
            toggleRelay("/relay2", isChecked);
            saveSwitchState("relay2", isChecked);
            cancelTimer(2);
            updateRelayVisuals(relay2Container, isChecked);
        });
    }

    private void animateSwitch(MaterialSwitch switchView, boolean newState) {
        ObjectAnimator thumbAnimator = ObjectAnimator.ofFloat(
                switchView,
                "thumbPosition",
                newState ? 1.0f : 0.0f
        );
        thumbAnimator.setDuration(ANIMATION_DURATION);
        thumbAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        thumbAnimator.start();
    }

    private void updateRelayVisuals(MaterialCardView container, boolean isOn) {
        container.setStrokeColor(ContextCompat.getColor(this, isOn ? R.color.colorAccent : android.R.color.transparent));
    }

    private void setupLongPressListeners() {
        relay1Container.setOnLongClickListener(v -> {
            handleRelaySelection(relay1Container, 1);
            return true;
        });

        relay2Container.setOnLongClickListener(v -> {
            handleRelaySelection(relay2Container, 2);
            return true;
        });

        View mainContainer = findViewById(android.R.id.content);
        mainContainer.setOnClickListener(v -> clearSelection());
    }

    private void handleRelaySelection(MaterialCardView relayContainer, int relayNumber) {
        if (selectedRelayContainer != null) {
            selectedRelayContainer.setChecked(false);
        }
        selectedRelayContainer = relayContainer;
        selectedRelayNumber = relayNumber;
        relayContainer.setChecked(true);
        if (renameMenuItem != null) renameMenuItem.setVisible(true);
        if (timerMenuItem != null) timerMenuItem.setVisible(true);
    }

    private void clearSelection() {
        if (selectedRelayContainer != null) {
            selectedRelayContainer.setChecked(false);
            selectedRelayContainer = null;
            selectedRelayNumber = -1;
        }
        if (renameMenuItem != null) renameMenuItem.setVisible(false);
        if (timerMenuItem != null) timerMenuItem.setVisible(false);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        renameMenuItem = menu.findItem(R.id.menu_action_edit);
        timerMenuItem = menu.findItem(R.id.menu_action_timer);
        timerDisplayMenuItem = menu.findItem(R.id.menu_timer_display);

        if (renameMenuItem != null) renameMenuItem.setVisible(false);
        if (timerMenuItem != null) timerMenuItem.setVisible(false);
        if (timerDisplayMenuItem != null) timerDisplayMenuItem.setVisible(false);
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
        } else if (item.getItemId() == R.id.menu_action_timer) {
            if (selectedRelayNumber != -1) {
                showTimerDialog(selectedRelayNumber);
                clearSelection();
            }
            return true;
        }

        Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
        startActivity(intent);

        return super.onOptionsItemSelected(item);
    }

    private void showTimerDialog(int relayNumber) {
        TextInputLayout textInputLayout = new TextInputLayout(this, null,
                com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox);
        TextInputEditText editText = new TextInputEditText(textInputLayout.getContext());
        textInputLayout.addView(editText);

        MaterialSwitch targetSwitch = (relayNumber == 1) ? relay1Switch : relay2Switch;
        boolean currentState = targetSwitch.isChecked();

        editText.setHint("Enter timer duration (seconds)");

        new MaterialAlertDialogBuilder(this,
                com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setTitle("Set Timer")
                .setMessage("Switch will turn " + (currentState ? "OFF" : "ON") + " after timer expires")
                .setView(textInputLayout)
                .setPositiveButton("Set", (dialog, which) -> {
                    String input = editText.getText().toString().trim();
                    if (!input.isEmpty()) {
                        try {
                            int seconds = Integer.parseInt(input);
                            setRelayTimer(relayNumber, seconds);
                        } catch (NumberFormatException e) {
                            Toast.makeText(this, "Invalid duration", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setRelayTimer(int relayNumber, int seconds) {
        cancelTimer(relayNumber);

        Timer timer = new Timer();
        relayTimers.put(relayNumber, timer);

        final MaterialSwitch targetSwitch = (relayNumber == 1) ? relay1Switch : relay2Switch;
        final MaterialCardView targetContainer = (relayNumber == 1) ? relay1Container : relay2Container;
        final TextView targetText = (relayNumber == 1) ? relay1Text : relay2Text;
        final String relayEndpoint = "/relay" + relayNumber;
        final boolean initialState = targetSwitch.isChecked();
        final long startTime = System.currentTimeMillis();
        final long endTime = startTime + (seconds * 1000);

        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                long remainingTime = (endTime - currentTime) / 1000;

                mainHandler.post(() -> {
                    if (remainingTime > 0) {
                        updateTimerDisplay(relayNumber, targetText.getText().toString(), remainingTime);
                    } else {
                        // Toggle to opposite of initial state
                        boolean newState = !initialState;
                        targetSwitch.setChecked(newState);
                        animateSwitch(targetSwitch, newState);
                        updateRelayVisuals(targetContainer, newState);
                        toggleRelay(relayEndpoint, newState);
                        saveSwitchState("relay" + relayNumber, newState);
                        hideTimerDisplay();
                        cancel();
                        relayTimers.remove(relayNumber);

                        Toast.makeText(MainActivity.this,
                                "Timer completed for " + targetText.getText() +
                                        " - Switched " + (newState ? "ON" : "OFF"),
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }, 0, 1000);

        // Keep the initial state
        targetSwitch.setChecked(initialState);
        updateRelayVisuals(targetContainer, initialState);
        Toast.makeText(this, String.format("Timer set for %d seconds - Will turn %s",
                seconds, initialState ? "OFF" : "ON"), Toast.LENGTH_SHORT).show();
    }

    private void cancelTimer(int relayNumber) {
        Timer existingTimer = relayTimers.get(relayNumber);
        if (existingTimer != null) {
            existingTimer.cancel();
            relayTimers.remove(relayNumber);
            hideTimerDisplay();
        }
    }

    private void updateTimerDisplay(int relayNumber, String relayName, long remainingSeconds) {
        if (timerDisplayMenuItem != null) {
            timerDisplayMenuItem.setVisible(true);
            timerDisplayMenuItem.setTitle(String.format("%s: %ds", relayName, remainingSeconds));
        }
    }

    private void hideTimerDisplay() {
        if (timerDisplayMenuItem != null) {
            timerDisplayMenuItem.setVisible(false);
        }
    }

    private void toggleRelay(String endpoint, boolean state) {
        String url = "http://" + ESP8266_IP + endpoint + (state ? "/on" : "/off");
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                response -> Log.d(TAG, "Relay toggled successfully"),
                error -> Log.e(TAG, "Error toggling relay", error));
        queue.add(stringRequest);
    }

    private void saveSwitchState(String key, boolean state) {
        sharedPreferences.edit().putBoolean(key, state).apply();
    }

    private void loadSwitchStates() {
        relay1Switch.setChecked(sharedPreferences.getBoolean("relay1", false));
        relay2Switch.setChecked(sharedPreferences.getBoolean("relay2", false));
    }

    private void fetchRelayState() {
        toggleRelay("/state", false);
    }

    private void loadRelayNames() {
        relay1Text.setText(sharedPreferences.getString("relay1_name", "Relay 1"));
        relay2Text.setText(sharedPreferences.getString("relay2_name", "Relay 2"));
    }

    private void showRenameDialog(int relayNumber) {
        TextInputLayout textInputLayout = new TextInputLayout(this, null,
                com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox);
        TextInputEditText editText = new TextInputEditText(textInputLayout.getContext());
        textInputLayout.addView(editText);

        editText.setHint("Enter new name");

        new MaterialAlertDialogBuilder(this,
                com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setTitle("Rename Relay")
                .setView(textInputLayout)
                .setPositiveButton("OK", (dialog, which) -> {
                    String newName = editText.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        if (relayNumber == 1) relay1Text.setText(newName);
                        else relay2Text.setText(newName);
                        saveRelayName(relayNumber, newName);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveRelayName(int relayNumber, String name) {
        String key = "relay" + relayNumber + "_name";
        sharedPreferences.edit().putString(key, name).apply();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        for (Timer timer : relayTimers.values()) {
            timer.cancel();
        }
        relayTimers.clear();
    }
}
