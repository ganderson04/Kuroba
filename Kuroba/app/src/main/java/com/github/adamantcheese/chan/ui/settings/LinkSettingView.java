/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.adamantcheese.chan.ui.settings;

import android.content.res.ColorStateList;
import android.view.View;
import android.widget.ImageView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.manager.SettingsNotificationManager.SettingNotification;
import com.github.adamantcheese.chan.ui.controller.settings.SettingsController;

import org.greenrobot.eventbus.Subscribe;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getRes;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;

public class LinkSettingView
        extends SettingView {
    public SettingNotification settingNotificationType = SettingNotification.Default;
    private final View.OnClickListener clickListener;
    private String description;

    public LinkSettingView(
            SettingsController settingsController, int name, int description, View.OnClickListener clickListener
    ) {
        this(settingsController, getString(name), getString(description), clickListener);
    }

    public LinkSettingView(
            SettingsController settingsController, String name, String description, View.OnClickListener clickListener
    ) {
        super(settingsController, name);
        this.description = description;
        this.clickListener = clickListener;
    }

    @Override
    public void setView(View view) {
        view.setOnClickListener(clickListener);
        super.setView(view);
    }

    @Subscribe(sticky = true)
    public void onNotificationsChanged(SettingNotification newType) {
        updateSettingNotificationIcon(newType);
    }

    @Override
    public String getBottomDescription() {
        return description;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (built) {
            view.setEnabled(enabled);
            view.findViewById(R.id.top).setEnabled(enabled);
            View bottom = view.findViewById(R.id.bottom);
            if (bottom != null) {
                bottom.setEnabled(enabled);
            }
        }
    }

    public void setDescription(int description) {
        setDescription(getString(description));
    }

    public void setDescription(String description) {
        this.description = description;
        if (built) {
            settingsController.onPreferenceChange(this);
        }
    }

    protected void updateSettingNotificationIcon(SettingNotification settingNotification) {
        if (!built) return;
        ImageView notificationIcon = view.findViewById(R.id.setting_notification_icon);
        if (notificationIcon == null) return; // no notification icon for this view

        notificationIcon.setVisibility(VISIBLE);
        switch (settingNotification) {
            case Default:
                notificationIcon.setVisibility(GONE);
                break;
            case ApkUpdate:
            case CrashLog:
                if (settingNotification == settingNotificationType) {
                    notificationIcon.setImageTintList(ColorStateList.valueOf(getRes().getColor(settingNotification.getNotificationIconTintColor())));
                } else {
                    notificationIcon.setVisibility(GONE);
                }
                break;
            case Both:
                notificationIcon.setImageTintList(ColorStateList.valueOf(getRes().getColor(settingNotificationType.getNotificationIconTintColor())));
                break;
        }
    }
}
