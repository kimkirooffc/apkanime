package com.aniflow.app;

import android.os.Bundle;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.appcompat.app.AppCompatActivity;

import com.aniflow.service.Repository;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class ProfileActivity extends AppCompatActivity {

    private Repository repository;
    private TextView watchlistCount;
    private TextView historyCount;
    private TextView downloadedCount;
    private BottomNavigationView bottomNav;

    private SwitchMaterial themeSystemSwitch;
    private RadioGroup themeModeGroup;
    private RadioButton themeModeLight;
    private RadioButton themeModeDark;
    private RadioButton themeModeAmoled;

    private ChipGroup accentChipGroup;
    private Chip accentBlueChip;
    private Chip accentPurpleChip;
    private Chip accentGreenChip;
    private Chip accentRedChip;
    private Chip accentOrangeChip;

    private boolean syncingThemeControls = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_AniFlow);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        repository = new Repository(this);
        watchlistCount = findViewById(R.id.watchlistCount);
        historyCount = findViewById(R.id.historyCount);
        downloadedCount = findViewById(R.id.downloadedCount);
        bottomNav = findViewById(R.id.bottomNav);

        themeSystemSwitch = findViewById(R.id.themeSystemSwitch);
        themeModeGroup = findViewById(R.id.themeModeGroup);
        themeModeLight = findViewById(R.id.themeModeLight);
        themeModeDark = findViewById(R.id.themeModeDark);
        themeModeAmoled = findViewById(R.id.themeModeAmoled);

        accentChipGroup = findViewById(R.id.accentChipGroup);
        accentBlueChip = findViewById(R.id.accentBlueChip);
        accentPurpleChip = findViewById(R.id.accentPurpleChip);
        accentGreenChip = findViewById(R.id.accentGreenChip);
        accentRedChip = findViewById(R.id.accentRedChip);
        accentOrangeChip = findViewById(R.id.accentOrangeChip);

        setupThemeControls();
        syncThemeControls();
        ThemeManager.applyAmoledSurfaceIfNeeded(this);
        NavigationHelper.bind(bottomNav, this, R.id.nav_profile);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_profile);
        }
        watchlistCount.setText(String.valueOf(repository.getWatchlist().size()));
        historyCount.setText(String.valueOf(repository.getContinueWatchingItems(999).size()));
        downloadedCount.setText(String.valueOf(repository.getDownloadCount()));
    }

    private void setupThemeControls() {
        themeSystemSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (syncingThemeControls) {
                return;
            }
            setManualThemeEnabled(!isChecked);
            if (isChecked) {
                applyThemeMode(ThemeManager.MODE_SYSTEM);
                return;
            }
            applyThemeMode(resolveModeFromRadioId(themeModeGroup.getCheckedRadioButtonId()));
        });

        themeModeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (syncingThemeControls || themeSystemSwitch.isChecked()) {
                return;
            }
            applyThemeMode(resolveModeFromRadioId(checkedId));
        });

        accentChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (syncingThemeControls) {
                return;
            }
            repository.setAccentColor(resolveAccentFromChipId(group.getCheckedChipId()));
            ThemeManager.setAccentColor(this, repository.getAccentColor());
        });
    }

    private void syncThemeControls() {
        syncingThemeControls = true;

        String mode = repository.getThemeMode();
        boolean followSystem = ThemeManager.MODE_SYSTEM.equals(mode);
        themeSystemSwitch.setChecked(followSystem);
        setManualThemeEnabled(!followSystem);

        int modeRadioId = switch (mode) {
            case ThemeManager.MODE_DARK -> R.id.themeModeDark;
            case ThemeManager.MODE_AMOLED -> R.id.themeModeAmoled;
            default -> R.id.themeModeLight;
        };
        themeModeGroup.check(modeRadioId);

        String accent = repository.getAccentColor();
        int accentChipId = switch (accent) {
            case ThemeManager.ACCENT_BLUE -> R.id.accentBlueChip;
            case ThemeManager.ACCENT_GREEN -> R.id.accentGreenChip;
            case ThemeManager.ACCENT_RED -> R.id.accentRedChip;
            case ThemeManager.ACCENT_ORANGE -> R.id.accentOrangeChip;
            default -> R.id.accentPurpleChip;
        };
        accentChipGroup.check(accentChipId);

        syncingThemeControls = false;
    }

    private void setManualThemeEnabled(boolean enabled) {
        themeModeGroup.setEnabled(enabled);
        themeModeLight.setEnabled(enabled);
        themeModeDark.setEnabled(enabled);
        themeModeAmoled.setEnabled(enabled);
        themeModeGroup.setAlpha(enabled ? 1f : 0.55f);
    }

    private void applyThemeMode(String mode) {
        String current = repository.getThemeMode();
        if (current.equals(mode)) {
            return;
        }
        repository.setThemeMode(mode);
        ThemeManager.setThemeMode(this, mode);
        ThemeManager.applyThemeAndRecreate(this);
    }

    private String resolveModeFromRadioId(@IdRes int checkedId) {
        if (checkedId == R.id.themeModeDark) {
            return ThemeManager.MODE_DARK;
        }
        if (checkedId == R.id.themeModeAmoled) {
            return ThemeManager.MODE_AMOLED;
        }
        return ThemeManager.MODE_LIGHT;
    }

    private String resolveAccentFromChipId(@IdRes int checkedId) {
        if (checkedId == R.id.accentBlueChip) {
            return ThemeManager.ACCENT_BLUE;
        }
        if (checkedId == R.id.accentGreenChip) {
            return ThemeManager.ACCENT_GREEN;
        }
        if (checkedId == R.id.accentRedChip) {
            return ThemeManager.ACCENT_RED;
        }
        if (checkedId == R.id.accentOrangeChip) {
            return ThemeManager.ACCENT_ORANGE;
        }
        return ThemeManager.ACCENT_PURPLE;
    }
}
