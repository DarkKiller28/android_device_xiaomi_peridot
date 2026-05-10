/*
 * Copyright (C) 2015 The CyanogenMod Project
 *               2017-2019 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.settings.touchsampling;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

import org.lineageos.settings.R;

public class TouchSamplingPerAppActivity extends CollapsingToolbarBaseActivity {

    private static final String TAG_PER_APP_HTSR = "per_app_htsr";
    private TouchSamplingPerAppFragment mFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mFragment = new TouchSamplingPerAppFragment();
        getFragmentManager().beginTransaction()
                .replace(com.android.settingslib.collapsingtoolbar.R.id.content_frame, mFragment, TAG_PER_APP_HTSR)
                .commit();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.per_app_htsr_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.show_system_apps) {
            if (mFragment != null) {
                mFragment.toggleSystemApps();
                // Update menu item title
                boolean showingSystem = mFragment.isShowingSystemApps();
                item.setTitle(showingSystem ? R.string.hide_system_apps : R.string.show_system_apps);
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
