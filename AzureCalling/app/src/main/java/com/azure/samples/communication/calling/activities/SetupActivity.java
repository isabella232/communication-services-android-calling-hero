// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.samples.communication.calling.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;

import com.azure.android.communication.calling.VideoStreamRenderer;
import com.azure.android.communication.calling.VideoStreamRendererView;
import com.azure.android.communication.calling.RenderingOptions;
import com.azure.android.communication.calling.ScalingMode;
import com.azure.samples.communication.calling.AzureCalling;
import com.azure.samples.communication.calling.external.calling.CallingContext;
import com.azure.samples.communication.calling.helpers.Constants;
import com.azure.samples.communication.calling.external.calling.JoinCallConfig;
import com.azure.samples.communication.calling.R;
import com.azure.samples.communication.calling.helpers.PermissionHelper;
import com.azure.samples.communication.calling.helpers.PermissionState;

import java9.util.concurrent.CompletableFuture;

public class SetupActivity extends AppCompatActivity {
    private static final String LOG_TAG = SetupActivity.class.getSimpleName();

    private String groupId;
    private EditText setupName;
    private LinearLayout setupMissingLayout;
    private ProgressBar setupProgressBar;
    private ImageView defaultAvatar;
    private ImageView setupMissingImage;
    private TextView setupMissingText;
    private LinearLayout setupVideoButtons;
    private ImageView setupGradient;
    private ConstraintLayout setupVideoLayout;
    private CallingContext callingContext;
    private PermissionHelper permissionHelper;
    private ToggleButton videoToggleButton;
    private ToggleButton audioToggleButton;
    private Button joinButton;
    private TextView joinButtonText;
    private VideoStreamRenderer rendererView;
    private VideoStreamRendererView previewVideo;
    private Button setupMissingButton;
    private Runnable initialAudioPermissionRequest;
    private Runnable initialVideoToggleRequest;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        // Get a support ActionBar corresponding to this toolbar
        final ActionBar ab = getSupportActionBar();
        // Disable the Up button
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
            ab.setTitle("Lobby");
        }

        initializeUI();

        handleAllPermissions();

        callingContext = ((AzureCalling) getApplication()).getCallingContext();
        final CompletableFuture<Void> setupCompletableFuture = callingContext.setupAsync();

        final Intent intent = getIntent();
        groupId = intent.getStringExtra(Constants.GROUP_ID);

        setupCompletableFuture.whenComplete((aVoid, throwable) -> {
            runOnUiThread(() -> {
                videoToggleButton.setEnabled(true);
                setupProgressBar.setVisibility(View.GONE);
                defaultAvatar.setVisibility(View.VISIBLE);
            });
        });
    }

    private void initializeUI() {
        setupMissingLayout = findViewById(R.id.setup_missing_layout);
        setupMissingImage = findViewById(R.id.setup_missing_image);
        setupMissingText = findViewById(R.id.setup_missing_text);
        setupMissingButton = findViewById(R.id.setup_missing_button);

        setupProgressBar = findViewById(R.id.setup_progress_bar);
        defaultAvatar = findViewById(R.id.default_avatar);

        setupMissingButton.setOnClickListener(l -> openSettings());

        setupGradient = findViewById(R.id.setup_gradient);
        setupVideoLayout = findViewById(R.id.setup_video_layout);
        setupVideoButtons = findViewById(R.id.setup_video_buttons);

        videoToggleButton = findViewById(R.id.setup_video);
        videoToggleButton.setOnClickListener(l -> toggleVideo(videoToggleButton.isChecked()));
        videoToggleButton.setEnabled(false);

        audioToggleButton = findViewById(R.id.setup_audio);
        audioToggleButton.setChecked(true);

        setupName = findViewById(R.id.setup_name);
        setupName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(final CharSequence charSequence, final int i, final int i1, final int i2) {
                // Do nothing
            }

            @Override
            public void onTextChanged(final CharSequence s, final int start, final int before, final int count) {
                // Do nothing
            }

            @Override
            public void afterTextChanged(final Editable editable) {
                setJoinButtonState();
            }
        });

        joinButton = findViewById(R.id.setup_button);
        joinButtonText = findViewById(R.id.setup_button_text);
        hidePermissionsWarning();
    }

    private void setJoinButtonState() {
        if (setupName.getText().toString().length() == 0
                || permissionHelper.getAudioPermissionState(this) != PermissionState.GRANTED) {
            //TODO: Error: "SPAN_EXCLUSIVE_EXCLUSIVE spans cannot have a zero length" occurs after clearing text
            joinButton.setEnabled(false);
            joinButtonText.setEnabled(false);
            joinButton.setClickable(false);
        } else {
            joinButton.setClickable(true);
            joinButton.setEnabled(true);
            joinButtonText.setEnabled(true);
            if (!joinButton.hasOnClickListeners()) {
                joinButton.setOnClickListener(l -> startCall());
            }
        }
    }

    @Override
    protected void onDestroy() {
        Log.d(LOG_TAG, "SetupActivity - onDestroy");
        super.onDestroy();
    }

    @Override
    protected void onStop() {
        Log.d(LOG_TAG, "SetupActivity - onStop");
        if (rendererView != null) {
            rendererView.dispose();
        }
        super.onStop();
    }

    private void openSettings() {
        final Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final Uri uri = Uri.fromParts("package", getPackageName(), null);
        intent.setData(uri);
        finishAffinity();
        startActivity(intent);
    }

    private void startCall() {
        Log.d(LOG_TAG, "Start call button clicked!");
        callingContext.getSetupCompletableFuture().whenComplete((aVoid, throwable) -> {
            runOnUiThread(() -> {
                if (rendererView != null) {
                    rendererView.dispose();
                }
                final JoinCallConfig joinCallConfig = new JoinCallConfig(
                        groupId, !audioToggleButton.isChecked(), videoToggleButton.isChecked(),
                        setupName.getText().toString());
                finishAffinity();
                final Intent intent = new Intent(this, CallActivity.class);
                intent.putExtra(Constants.JOIN_CALL_CONFIG, joinCallConfig);
                startActivity(intent);
            });
        });
    }

    private void toggleVideo(final boolean toggleOn) {
        if (toggleOn) {
            if (initialVideoToggleRequest != null) {
                initialVideoToggleRequest.run();
                // Video will turn on, or the button will be disabled
            } else {
                toggleVideoOn();
            }
        } else {
            toggleVideoOff();
        }
    }

    private void onInitialVideoToggleRequest() {
        final PermissionState videoAccess = permissionHelper.getVideoPermissionState(this);
        if (videoAccess == PermissionState.GRANTED) {
            toggleVideoOn();
        } else {
            runOnUiThread(() -> {
                handleVideoPermissionsDenied();
                videoToggleButton.setChecked(false);
            });
        }
    }

    private void toggleVideoOn() {
        callingContext.getLocalVideoStreamCompletableFuture().thenAccept(localVideoStream -> {
            runOnUiThread(() -> {
                defaultAvatar.setVisibility(View.GONE);
                rendererView = new VideoStreamRenderer(localVideoStream, getApplicationContext());
                previewVideo = rendererView.createView(new RenderingOptions(ScalingMode.CROP));
                setupVideoLayout.addView(previewVideo, 0);
                videoToggleButton.setChecked(true);
            });
        });
    }

    private void toggleVideoOff() {
        if (rendererView != null) {
            rendererView.dispose();
        }
        setupVideoLayout.removeView(previewVideo);
        rendererView = null;
        previewVideo = null;
        videoToggleButton.setChecked(false);
        defaultAvatar.setVisibility(View.VISIBLE);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            this.finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void handleButtonStates() {
        final PermissionState audioAccess = permissionHelper.getAudioPermissionState(this);
        final PermissionState videoAccess = permissionHelper.getVideoPermissionState(this);

        runOnUiThread(() -> {
            if ((audioAccess == PermissionState.DENIED)
                    && (videoAccess == PermissionState.NOT_ASKED)) {
                handleAudioPermissionsDenied();
            } else if ((audioAccess == PermissionState.GRANTED)
                    && (videoAccess == PermissionState.NOT_ASKED)) {
                hidePermissionsWarning();
            } else if ((audioAccess == PermissionState.DENIED)
                    && (videoAccess == PermissionState.DENIED)) {
                handleAudioAndVideoPermissionsDenied();
            } else if ((audioAccess == PermissionState.GRANTED)
                    && (videoAccess == PermissionState.DENIED)) {
                handleVideoPermissionsDenied();
            }  else {
                hidePermissionsWarning();
            }
        });
    }

    /**
     * Request each required permission if the app doesn't already have it.
     */
    private void handleAllPermissions() {
        permissionHelper = ((AzureCalling) getApplication()).getPermissionHelper();

        final PermissionState audioAccess = permissionHelper.getAudioPermissionState(this);

        if (audioAccess == PermissionState.NOT_ASKED) {
            initialAudioPermissionRequest =
                    permissionHelper.createAudioPermissionRequest(this, this::handleButtonStates);
            initialAudioPermissionRequest.run();
        } else {
            handleButtonStates();
        }

        if (permissionHelper.getVideoPermissionState(this) == PermissionState.NOT_ASKED) {
            initialVideoToggleRequest =
                    permissionHelper.createVideoPermissionRequest(this,
                            this::onInitialVideoToggleRequest);
        }
    }

    private void handleAudioAndVideoPermissionsDenied() {
        // Both video and audio not allowed
        setupMissingLayout.setVisibility(View.VISIBLE);
        setupMissingText.setText(R.string.setup_missing_video_mic);
        setupVideoButtons.setVisibility(View.GONE);
        setupGradient.setVisibility(View.GONE);
    }

    private void handleVideoPermissionsDenied() {
        // Audio allowed, video previously denied
        setupMissingLayout.setVisibility(View.VISIBLE);
        setupMissingImage.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(),
                R.drawable.ic_fluent_video_off_24_filled));
        setupMissingText.setText(R.string.setup_missing_video);
        videoToggleButton.setVisibility(View.GONE);
        setupGradient.setVisibility(View.GONE);
    }

    private void handleAudioPermissionsDenied() {
        // Audio not allowed, video allowed or unknown
        setupMissingLayout.setVisibility(View.VISIBLE);
        setupMissingImage.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(),
                R.drawable.ic_fluent_mic_off_24_filled));
        setupMissingText.setText(R.string.setup_missing_mic);
        setupVideoButtons.setVisibility(View.GONE);
        setupGradient.setVisibility(View.GONE);
    }

    private void hidePermissionsWarning() {
        setupMissingLayout.setVisibility(View.GONE);

    }
}
