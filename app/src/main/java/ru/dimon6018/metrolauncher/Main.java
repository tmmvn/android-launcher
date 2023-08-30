package ru.dimon6018.metrolauncher;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.elevation.SurfaceColors;

import ru.dimon6018.metrolauncher.content.AllApps;
import ru.dimon6018.metrolauncher.content.Start;
import ru.dimon6018.metrolauncher.content.data.DataProviderFragment;
import ru.dimon6018.metrolauncher.helpers.AbstractDataProvider;

public class Main extends AppCompatActivity {
    private static final String FRAGMENT_TAG_DATA_PROVIDER = "data provider";
    private static final String FRAGMENT_LIST_VIEW = "list view";
    private static final String FRAGMENT_ALL_APPS = "all apps";

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_screen_laucnher);
        getWindow().setNavigationBarColor(SurfaceColors.SURFACE_2.getColor(this));
        BottomNavigationView navbar = findViewById(R.id.navigation);
        getSupportFragmentManager().beginTransaction()
                .add(new DataProviderFragment(), FRAGMENT_TAG_DATA_PROVIDER)
                .commit();
        getSupportFragmentManager().beginTransaction()
                .add(R.id.pager, new Start(), FRAGMENT_LIST_VIEW)
                .commit();
        navbar.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.start_win) {
                removeSearch();
                getSupportFragmentManager().beginTransaction()
                        .add(new DataProviderFragment(), FRAGMENT_TAG_DATA_PROVIDER)
                        .commit();
                getSupportFragmentManager().beginTransaction()
                        .add(R.id.pager, new Start(), FRAGMENT_LIST_VIEW)
                        .commit();
                return true;
            } else if (item.getItemId() == R.id.start_apps) {
                removeStart();
                getSupportFragmentManager().beginTransaction()
                        .add(R.id.pager, new AllApps(), FRAGMENT_ALL_APPS)
                        .commit();
                return true;
            } else {
                return false;
            }
        });
    }
    private void removeStart() {
        Fragment fragment1 = getSupportFragmentManager().findFragmentByTag(FRAGMENT_LIST_VIEW);
        Fragment fragment2 = getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG_DATA_PROVIDER);
        getSupportFragmentManager().beginTransaction().remove(fragment1).remove(fragment2).commitAllowingStateLoss();
    }
    private void removeSearch() {
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(FRAGMENT_ALL_APPS);
        getSupportFragmentManager().beginTransaction().remove(fragment).commitAllowingStateLoss();
    }
    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    public AbstractDataProvider getDataProvider() {
        final Fragment fragment = getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG_DATA_PROVIDER);
        return ((DataProviderFragment) fragment).getDataProvider();
    }

}

