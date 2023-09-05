package ru.dimon6018.metrolauncher.content;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textview.MaterialTextView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import ru.dimon6018.metrolauncher.R;
import ru.dimon6018.metrolauncher.content.data.App;
import ru.dimon6018.metrolauncher.content.data.Prefs;

public class AllApps extends Fragment {

    private static List<App> appsList;
    private List<App> mApps;
    DialogFragment pinAppDialog;

    public static int positionCurrent;

    public AllApps(){
        super(R.layout.all_apps_screen);
    }
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setUpApps();
        pinAppDialog = new PinAppDialog();
        RecyclerView recyclerView = view.findViewById(R.id.app_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        mApps = new ArrayList<>();
        getHeaderListLatter(appsList);
        recyclerView.setAdapter(new AppAdapter(getContext(), mApps));
        MaterialCardView back = view.findViewById(R.id.return_back);
        back.setOnClickListener(v -> getParentFragmentManager().beginTransaction().remove(this).commitAllowingStateLoss());
    }
    public void setUpApps() {
        PackageManager pManager = getContext().getPackageManager();
        appsList = new ArrayList<>();
        Intent i = new Intent(Intent.ACTION_MAIN, null);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> allApps = pManager.queryIntentActivities(i, 0);

        for (ResolveInfo ri : allApps) {
            App app = new App();
            app.app_label = (String) ri.loadLabel(pManager);
            app.app_package = ri.activityInfo.packageName;
            app.isSection = false;
            app.app_icon = ri.activityInfo.loadIcon(pManager);
            appsList.add(app);
        }
    }
    private void getHeaderListLatter(List<App> newApps) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Collections.sort(newApps, Comparator.comparing(app -> String.valueOf(app.app_label.charAt(0)).toUpperCase()));
        } else {
            Collections.sort(newApps, (app1, app2) -> String.valueOf(app1.app_label.charAt(0)).toUpperCase().compareTo(String.valueOf(app2.app_label.charAt(0)).toUpperCase()));
        }

        String lastHeader = "";

        int size = newApps.size();

        for (int i = 0; i < size; i++) {

            App app = newApps.get(i);
            String header = String.valueOf(app.getLabel().charAt(0)).toUpperCase();

            if (!TextUtils.equals(lastHeader, header)) {
                lastHeader = header;
                App head = new App();
                head.app_label = header;
                head.isSection = true;
                mApps.add(head);
            }
            mApps.add(app);
        }
    }
    public static class PinAppDialog extends DialogFragment {

        @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Context context = new ContextThemeWrapper(getContext(), R.style.PinAppStyle);
            View view = LayoutInflater.from(context).inflate(R.layout.pin_app_dialog, null);
            MaterialTextView pin = view.findViewById(R.id.pin_app);
            pin.setOnClickListener(view1 -> {
                App app = appsList.get(positionCurrent);
                List<App> mPrevData = new Prefs(getContext()).getAppsPackage();
                int iCount = mPrevData.size();
                new Prefs(getContext()).addApp(app.getPackagel(), app.getLabel());
                new Prefs(getContext()).setPos(app.getPackagel(), iCount);
                new Prefs(getContext()).setTileSize(app.getPackagel(), 0);
                Log.i("AllApps", "Add new app. Pos: " + iCount);
                Log.i("AllApps", "Add new app. Label: " + app.getLabel());
                dismiss();
            });
            MaterialAlertDialogBuilder builder=new MaterialAlertDialogBuilder(getActivity());
            return builder
                    .setView(view)
                    .create();
        }
    }
    public static void CurrentPos(int pos) {
        positionCurrent = pos;
    }
    public class AppAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static List<App> appsList;
        public static final int SECTION_VIEW = 0;
        public static final int CONTENT_VIEW = 1;
        WeakReference<Context> mContextWeakReference;

        AppAdapter(Context context, List<App> apps) {
            appsList = apps;
            this.mContextWeakReference = new WeakReference<>(context);
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            if (viewType == SECTION_VIEW) {
                return new SectionHeaderViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.abc, parent, false));
            }
            return new AppHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.app, parent, false));
        }
        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (SECTION_VIEW == getItemViewType(position)) {
                SectionHeaderViewHolder sectionHeaderViewHolder = (SectionHeaderViewHolder) holder;
                App sectionItem = appsList.get(position);
                sectionHeaderViewHolder.headerTitleTextview.setText(sectionItem.app_label);
                return;
            }
            AppHolder holder1 = (AppHolder) holder;
            App apps = appsList.get(position);
            Drawable icon = apps.getDrawable();
            icon.setBounds(0, 0, getResources().getDimensionPixelSize(R.dimen.app_icon_size), getResources().getDimensionPixelSize(R.dimen.app_icon_size));
            holder1.icon.setImageDrawable(icon);
            holder1.label.setText(apps.getLabel());
            holder1.itemView.setOnClickListener(view -> {
                Intent intent = getContext().getPackageManager().getLaunchIntentForPackage((String) apps.getPackagel());
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            });
            holder1.itemView.setOnLongClickListener(view -> {
                View v = LayoutInflater.from(getContext()).inflate(R.layout.pin_app_dialog, null);
                MaterialTextView pin = v.findViewById(R.id.pin_app);
                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity(), R.style.CustomDialog);
                pin.setOnClickListener(view1 -> {
                            List<App> mPrevData = new Prefs(getContext()).getAppsPackage();
                            int iCount = mPrevData.size();
                            new Prefs(getContext()).addApp(apps.getPackagel(), apps.getLabel());
                            new Prefs(getContext()).setPos(apps.getPackagel(), iCount);
                            new Prefs(getContext()).setTileSize(apps.getPackagel(), 0);
                            Log.i("AllApps", "Add new app. Pos: " + iCount);
                            Log.i("AllApps", "Add new app. Label: " + apps.getLabel());
                        });
                builder.setView(v).show();
                return true;
            });
        }
        @Override
        public int getItemViewType(int position) {
            if (appsList.get(position).isSection) {
                return SECTION_VIEW;
            } else {
                return CONTENT_VIEW;
            }
        }
        @Override
        public int getItemCount() {
            return appsList.size();
        }
        public static class AppHolder extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView label;
            public AppHolder(@NonNull View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.app_icon);
                label = itemView.findViewById(R.id.app_label);
            }
        }
            public static class SectionHeaderViewHolder extends RecyclerView.ViewHolder {
                MaterialTextView headerTitleTextview;
                public SectionHeaderViewHolder(@NonNull View itemView) {
                    super(itemView);
                    headerTitleTextview = itemView.findViewById(R.id.abc_label);
                }
            }
        }
    }