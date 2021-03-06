package ambious.htccarxposed;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import com.stericson.RootTools.RootTools;

public class CarModeInterface extends PreferenceActivity  implements SharedPreferences.OnSharedPreferenceChangeListener {

    private SharedPreferences sharedFile;
    private SharedPreferences mainFile;
    private static final String LOG_TAG = "HTCCarModeXposed";
    ComponentName devAdminReceiver;
    DevicePolicyManager mDPM;
    boolean mAdminActive;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.prefs);
        sharedFile = getSharedPreferences("xPreferences", MODE_WORLD_READABLE); //Enable the 'world-readable' preference file. This is neccesary because the module can't access the default one.
        mainFile = PreferenceManager.getDefaultSharedPreferences(this);
        int carVersion;
        String carVersionString;
        try {
            PackageManager manager = this.getPackageManager();
            PackageInfo info = manager.getPackageInfo("com.htc.AutoMotive", 0);
            carVersion = info.versionCode;
            carVersionString = info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(LOG_TAG,"Car app not installed!?");
            carVersion = -1;
            carVersionString = getString(R.string.not_detected);
        }
        Preference carVersionPref = findPreference("carVersion");
        if (carVersion != -1) {
            sharedFile.edit()
                    .putInt("carVersion", carVersion)
                    .commit();
            if (carVersionPref != null)
                carVersionPref.setSummary(carVersionString);
        } else {
            carVersionPref.setSummary(R.string.not_detected);
        }
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        syncListPrefSummary((ListPreference) findPreference("wifi_mode"));
        syncListPrefSummary((ListPreference) findPreference("wifi_exit"));
        syncListPrefSummary((ListPreference) findPreference("gps_mode"));
        syncListPrefSummary((ListPreference) findPreference("gps_exit"));
        if (mainFile.getString("gesture_override", "1").equals("3")) //If the 3 fingers tap setting is on 'custom'
            findPreference("gesture_override").setSummary(mainFile.getString("gesture_name","Custom..."));
        else
        {
            int newValueInt = Integer.parseInt(mainFile.getString("gesture_override", "1"));
            CharSequence newSummary = ((ListPreference)findPreference("gesture_override")).getEntries()[newValueInt];
            (findPreference("gesture_override")).setSummary(newSummary);
        }
        CheckBoxPreference logging = (CheckBoxPreference)findPreference("enable_logging");
        String summary = getResources().getString(R.string.enable_logging_long);
        if (logging != null || summary != null)
            logging.setSummary(summary + " \"" +  Environment.getExternalStorageDirectory().getAbsolutePath() + "/carx.log\"");
        mDPM = (DevicePolicyManager)getSystemService(Context.DEVICE_POLICY_SERVICE);
        devAdminReceiver = new ComponentName(this, ModuleDeviceAdminReceiver.class);
        CheckBoxPreference device_sleep = (CheckBoxPreference) findPreference("lock_screen");
        if (!isActiveAdmin()) {
            mAdminActive = false;
            device_sleep.setChecked(false);
        }
        device_sleep.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object o) {
                boolean value = (Boolean) o;
                {
                    if (value) {
                        // Launch the activity to have the user enable our admin.
                        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, devAdminReceiver);
                        startActivityForResult(intent, 1);
                    } else {
                        //Remove from admin
                        mDPM.removeActiveAdmin(devAdminReceiver);
                        return true;
                    }
                    //Set false anyway, only the request response can set true.
                    return false;
                }
            }
        });
        killBackground(); //Kill any open instances of the car-app so settings are applied next time it's accessed.
    }

    /**
     * Sets the summary of a ListPreference to its value
     */
    private void syncListPrefSummary(ListPreference preference) {
        try {
            int newValueInt = Integer.parseInt(preference.getValue());
            CharSequence newSummary = preference.getEntries()[newValueInt];
            preference.setSummary(newSummary);
        } catch (NumberFormatException ex) {
            Log.e(LOG_TAG, "Value of \"" + preference.getKey() + "\" is not numerical!");
        }
    }

    /**
     * The main preference change handler.
     * Automatically saves preferences to the 'sharedFile' file which is word-readable.
     * Also handles interface-tasks such as unchecking disabled checkboxes and validating root for root-methods.
     */
    @Override
    public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, String s) {
        Preference preference = findPreference(s);
        if (preference == null)
            return;
        if (preference instanceof ListPreference || preference instanceof GesturePreference) {
            int newValueInt = Integer.parseInt(sharedPreferences.getString(s, null));
            preference.setSummary(((ListPreference) preference).getEntries()[newValueInt]);
            sharedFile.edit()
                    .putInt(s, newValueInt)
                    .commit();
        } else if (preference instanceof CheckBoxPreference)
            sharedFile.edit()
                    .putBoolean(s, sharedPreferences.getBoolean(s, false))
                    .commit();
        if (s.equals("allow_pulldown") && !sharedPreferences.getBoolean(s, true)) {
            ((CheckBoxPreference) findPreference("allow_multitasking")).setChecked(false);
        }
        if (s.equals("kill_apps") && !sharedPreferences.getBoolean(s, false))
            ((CheckBoxPreference) findPreference("kill_root")).setChecked(false);
        if (s.equals("kill_root") && sharedPreferences.getBoolean(s, false)) {
            //Check for root privilages
            if (!RootTools.isAccessGiven()) {
                Log.e(LOG_TAG, "Couldn't get root privileges!");
                ((CheckBoxPreference) preference).setChecked(false);
                Toast.makeText(getApplicationContext(), getText(R.string.root_failed), Toast.LENGTH_LONG).show();
                return;
            }
        }

        if (s.equals("enable_app_icon")) {
            PackageManager packageManager = getPackageManager();
            ComponentName aliasName = new ComponentName(this, "ambious.htccarxposed.CarModeInterface_Alias");
            if (sharedPreferences.getBoolean(s, true)) {
                //Re-adds the icon to the launcher
                packageManager.setComponentEnabledSetting(aliasName, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);

            } else {
                //Removes the app Icon from the launcher - it's still accessibly from the Xposed Module Installer.
                PackageManager p = getPackageManager();
                p.setComponentEnabledSetting(aliasName, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            }
        }

        if (s.equals("enable_toggler")) {
            ComponentName cn = new ComponentName(this, "ambious.htccarxposed.CarToggler");
            if (cn == null) {
                Log.w(LOG_TAG, "ComponentName was null!");
                return;
            }
            if (sharedPreferences.getBoolean(s, true)) {
                //Re-adds the toggler icon to the launcher
                PackageManager p = getPackageManager();
                p.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP);
            } else {
                //Removes the toggler Icon from the launcher - it's still accessibly from the Xposed Module Installer.
                PackageManager p = getPackageManager();
                p.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            }
        }
        killBackground(); //If changes were made, they'll only apply if the car app is restarted.
    }

    /**
     * Kills the car app in the background
     */
    private void killBackground() {
        ActivityManager mActivityManager = (ActivityManager) getApplicationContext().getSystemService(Context.ACTIVITY_SERVICE);
        mActivityManager.killBackgroundProcesses("com.htc.AutoMotive");
    }

    public static void showSelector(Activity _this) {
        // Pick an application
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        Intent pickIntent = new Intent(Intent.ACTION_PICK_ACTIVITY);
        pickIntent.putExtra(Intent.EXTRA_INTENT,mainIntent);
        _this.startActivityForResult(pickIntent, _this.getTaskId());
    }

    // The result is obtained in onActivityResult:
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (data != null && requestCode == this.getTaskId() && resultCode == RESULT_OK) {
            //First, resolve the selected intent name and package
            String packageName = data.getComponent().getPackageName();
            final PackageManager pm = getApplicationContext().getPackageManager();
            ApplicationInfo ai;
            try {
                ai = pm.getApplicationInfo(packageName,0);
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(LOG_TAG,"Package not resolved!");
                Log.e(LOG_TAG,"Package address: " + data.getComponent().getPackageName());
                return;
            }
            String applicationName = (String) pm.getApplicationLabel(ai);
            String intentAction = data.getAction();
            //Now we just apply it to the settings
            sharedFile.edit()
                    .putInt("gesture_override",3)
                    .putString("gesture_package",packageName)
                    .putString("gesture_name",applicationName)
                    .putString("gesture_action",intentAction)
                    .commit();
            mainFile.edit()
                    .putString("gesture_package",packageName)
                    .putString("gesture_name",applicationName)
                    .putString("gesture_action",intentAction)
                    .commit();
            ListPreference listPreference = (ListPreference) findPreference("gesture_override");
            listPreference.setSummary(applicationName);
        }
        else if (requestCode == 1 && resultCode == RESULT_OK)
        {
            mAdminActive = isActiveAdmin();
            Log.i(LOG_TAG,"Admin permissions granted!");
            CheckBoxPreference lockscreen = (CheckBoxPreference)findPreference("lock_screen");
            lockscreen.setChecked(true);
        } else if (requestCode == 1 && resultCode == RESULT_CANCELED)
        {
            mAdminActive = isActiveAdmin();
            Log.i(LOG_TAG,"User cancelled admin request!");
            CheckBoxPreference lockscreen = (CheckBoxPreference)findPreference("lock_screen");
            lockscreen.setChecked(false);
            Toast.makeText(this, R.string.no_privilages,Toast.LENGTH_LONG).show();
        }
    }
    private boolean isActiveAdmin() {
        Log.i(LOG_TAG,"Admin state: " + mDPM.isAdminActive(devAdminReceiver));
        return mDPM.isAdminActive(devAdminReceiver);
    }
}
