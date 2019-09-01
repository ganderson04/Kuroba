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
package com.github.adamantcheese.chan.ui.controller;

import android.app.AlertDialog;
import android.content.Context;
import android.net.Uri;
import android.widget.Toast;

import androidx.documentfile.provider.DocumentFile;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.database.DatabaseManager;
import com.github.adamantcheese.chan.core.saf.FileManager;
import com.github.adamantcheese.chan.core.saf.callback.DirectoryChooserCallback;
import com.github.adamantcheese.chan.core.saf.file.AbstractFile;
import com.github.adamantcheese.chan.core.saf.file.ExternalFile;
import com.github.adamantcheese.chan.core.saf.file.FileDescriptorMode;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.settings.BooleanSettingView;
import com.github.adamantcheese.chan.ui.settings.LinkSettingView;
import com.github.adamantcheese.chan.ui.settings.ListSettingView;
import com.github.adamantcheese.chan.ui.settings.SettingView;
import com.github.adamantcheese.chan.ui.settings.SettingsController;
import com.github.adamantcheese.chan.ui.settings.SettingsGroup;
import com.github.adamantcheese.chan.utils.Logger;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import kotlin.Unit;

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.runOnUiThread;

public class MediaSettingsController extends SettingsController {
    private static final String TAG = "MediaSettingsController";

    // Special setting views
    private BooleanSettingView boardFolderSetting;
    private BooleanSettingView threadFolderSetting;
    private LinkSettingView saveLocation;
    private LinkSettingView localThreadsLocation;
    private ListSettingView<ChanSettings.MediaAutoLoadMode> imageAutoLoadView;
    private ListSettingView<ChanSettings.MediaAutoLoadMode> videoAutoLoadView;

    private Executor fileCopyingExecutor = Executors.newSingleThreadExecutor();

    @Inject
    FileManager fileManager;

    @Inject
    DatabaseManager databaseManager;

    public MediaSettingsController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        inject(this);

        EventBus.getDefault().register(this);

        navigation.setTitle(R.string.settings_screen_media);

        setupLayout();
        populatePreferences();
        buildPreferences();

        onPreferenceChange(imageAutoLoadView);

        threadFolderSetting.setEnabled(ChanSettings.saveBoardFolder.get());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        EventBus.getDefault().unregister(this);
    }

    @Override
    public void onPreferenceChange(SettingView item) {
        super.onPreferenceChange(item);

        if (item == imageAutoLoadView) {
            updateVideoLoadModes();
        } else if (item == boardFolderSetting) {
            updateThreadFolderSetting();
        }
    }

    @Subscribe
    public void onEvent(ChanSettings.SettingChanged<?> setting) {
        if (setting.setting == ChanSettings.saveLocationUri) {
            // Image save location (SAF) was chosen
            String defaultDir = ChanSettings.getDefaultSaveLocationDir();

            ChanSettings.saveLocation.setNoUpdate(defaultDir);
            saveLocation.setDescription(ChanSettings.saveLocationUri.get());
        } else if (setting.setting == ChanSettings.localThreadsLocationUri) {
            // Local threads location (SAF) was chosen
            String defaultDir = ChanSettings.getDefaultLocalThreadsLocation();

            ChanSettings.localThreadLocation.setNoUpdate(defaultDir);
            localThreadsLocation.setDescription(ChanSettings.localThreadsLocationUri.get());
        } else if (setting.setting == ChanSettings.saveLocation) {
            // Image save location (Java File API) was chosen
            ChanSettings.saveLocationUri.setNoUpdate("");
            saveLocation.setDescription(ChanSettings.saveLocation.get());
        } else if (setting.setting == ChanSettings.localThreadLocation) {
            // Local threads location (Java File API) was chosen
            ChanSettings.localThreadsLocationUri.setNoUpdate("");
            localThreadsLocation.setDescription(ChanSettings.localThreadLocation.get());
        }
    }

    private void populatePreferences() {
        // Media group
        {
            SettingsGroup media = new SettingsGroup(R.string.settings_group_media);

            setupSaveLocationSetting(media);
            setupLocalThreadLocationSetting(media);

            boardFolderSetting = (BooleanSettingView) media.add(new BooleanSettingView(this,
                    ChanSettings.saveBoardFolder,
                    R.string.setting_save_board_folder,
                    R.string.setting_save_board_folder_description));

            threadFolderSetting = (BooleanSettingView) media.add(new BooleanSettingView(this,
                    ChanSettings.saveThreadFolder,
                    R.string.setting_save_thread_folder,
                    R.string.setting_save_thread_folder_description));

            media.add(new BooleanSettingView(this,
                    ChanSettings.saveServerFilename,
                    R.string.setting_save_server_filename,
                    R.string.setting_save_server_filename_description));

            media.add(new BooleanSettingView(this, ChanSettings.videoDefaultMuted,
                    R.string.setting_video_default_muted,
                    R.string.setting_video_default_muted_description));

            media.add(new BooleanSettingView(this, ChanSettings.videoOpenExternal,
                    R.string.setting_video_open_external,
                    R.string.setting_video_open_external_description));

            media.add(new BooleanSettingView(this,
                    ChanSettings.shareUrl,
                    R.string.setting_share_url, R.string.setting_share_url_description));

            media.add(new BooleanSettingView(this,
                    ChanSettings.revealImageSpoilers,
                    R.string.settings_reveal_image_spoilers,
                    R.string.settings_reveal_image_spoilers_description));

            media.add(new BooleanSettingView(this,
                    ChanSettings.allowMediaScannerToScanLocalThreads,
                    R.string.settings_allow_media_scanner_scan_local_threads_title,
                    R.string.settings_allow_media_scanner_scan_local_threads_description));

            groups.add(media);
        }

        // Loading group
        {
            SettingsGroup loading = new SettingsGroup(R.string.settings_group_media_loading);

            setupMediaLoadTypesSetting(loading);

            loading.add(new BooleanSettingView(this,
                    ChanSettings.videoAutoLoop,
                    R.string.setting_video_auto_loop,
                    R.string.setting_video_auto_loop_description));

            requiresRestart.add(loading.add(new BooleanSettingView(this,
                    ChanSettings.autoLoadThreadImages,
                    R.string.setting_auto_load_thread_images,
                    R.string.setting_auto_load_thread_images_description)));

            groups.add(loading);
        }
    }

    private void setupLocalThreadLocationSetting(SettingsGroup media) {
        if (!ChanSettings.incrementalThreadDownloadingEnabled.get()) {
            Logger.d(TAG, "setupLocalThreadLocationSetting() incrementalThreadDownloadingEnabled is disabled");
            return;
        }

        LinkSettingView localThreadsLocationSetting = new LinkSettingView(this,
                R.string.media_settings_local_threads_location_title,
                0,
                v -> showUseSAFOrOldAPIForLocalThreadsLocationDialog());


        localThreadsLocation = (LinkSettingView) media.add(localThreadsLocationSetting);
        localThreadsLocation.setDescription(getLocalThreadsLocation());
    }

    private String getLocalThreadsLocation() {
        if (!ChanSettings.localThreadsLocationUri.get().isEmpty()) {
            return ChanSettings.localThreadsLocationUri.get();
        }

        return ChanSettings.localThreadLocation.get();
    }

    private void setupSaveLocationSetting(SettingsGroup media) {
        LinkSettingView chooseSaveLocationSetting = new LinkSettingView(this,
                R.string.save_location_screen,
                0,
                v -> showUseSAFOrOldAPIForSaveLocationDialog());

        saveLocation = (LinkSettingView) media.add(chooseSaveLocationSetting);
        saveLocation.setDescription(getSaveLocation());
    }

    private String getSaveLocation() {
        if (!ChanSettings.saveLocationUri.get().isEmpty()) {
            return ChanSettings.saveLocationUri.get();
        }

        return ChanSettings.saveLocation.get();
    }

    private void showUseSAFOrOldAPIForLocalThreadsLocationDialog() {
        AlertDialog alertDialog = new AlertDialog.Builder(context)
                .setTitle(R.string.use_saf_for_local_threads_location_dialog_title)
                .setMessage(R.string.use_saf_for_local_threads_location_dialog_message)
                .setPositiveButton(R.string.use_saf_dialog_positive_button_text, (dialog, which) -> {
                    onLocalThreadsLocationUseSAFClicked();
                })
                .setNegativeButton(R.string.use_saf_dialog_negative_button_text, (dialog, which) -> {
                    onLocalThreadsLocationUseOldApiClicked();
                })
                .create();

        alertDialog.show();
    }

    /**
     * Select a directory where local threads will be stored via the old Java File API
     */
    private void onLocalThreadsLocationUseOldApiClicked() {
        SaveLocationController saveLocationController = new SaveLocationController(
                context,
                SaveLocationController.SaveLocationControllerMode.LocalThreadsSaveLocation,
                dirPath -> {
                    AbstractFile oldLocalThreadsDirectory = fileManager.newLocalThreadFile();

                    Logger.d(TAG, "SaveLocationController with LocalThreadsSaveLocation mode returned dir "
                            + dirPath);

                    // Supa hack to get the callback called
                    ChanSettings.localThreadLocation.setSync("");
                    ChanSettings.localThreadLocation.setSync(dirPath);

                    AbstractFile newLocalThreadsDirectory = fileManager.newLocalThreadFile();
                    askUserIfTheyWantToMoveOldThreadsToTheNewDirectory(
                            oldLocalThreadsDirectory,
                            newLocalThreadsDirectory);
                });

        navigationController.pushController(saveLocationController);
    }

    /**
     * Select a directory where local threads will be stored via the SAF
     */
    private void onLocalThreadsLocationUseSAFClicked() {
        fileManager.openChooseDirectoryDialog(new DirectoryChooserCallback() {
            @Override
            public void onResult(@NotNull Uri uri) {
                AbstractFile oldLocalThreadsDirectory = fileManager.newLocalThreadFile();

                ChanSettings.localThreadsLocationUri.set(uri.toString());
                String defaultDir = ChanSettings.getDefaultLocalThreadsLocation();

                ChanSettings.localThreadLocation.setNoUpdate(defaultDir);
                localThreadsLocation.setDescription(uri.toString());

                AbstractFile newLocalThreadsDirectory = fileManager.newLocalThreadFile();
                askUserIfTheyWantToMoveOldThreadsToTheNewDirectory(
                        oldLocalThreadsDirectory,
                        newLocalThreadsDirectory);
            }

            @Override
            public void onCancel(@NotNull String reason) {
                Toast.makeText(context, reason, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void askUserIfTheyWantToMoveOldThreadsToTheNewDirectory(
            AbstractFile oldLocalThreadsDirectory,
            AbstractFile newLocalThreadsDirectory) {

        // TODO: strings
        AlertDialog alertDialog = new AlertDialog.Builder(context)
                .setTitle("Move old local threads to the new directory?")
                .setMessage("This operation may take quite some time. Once started this operation shouldn't be canceled, otherwise something may break")
                .setPositiveButton("Move", (dialog, which) -> {
                    moveOldFilesToTheNewDirectory(oldLocalThreadsDirectory, newLocalThreadsDirectory);
                })
                .setNegativeButton("Do not move", (dialog, which) -> {})
                .create();

        alertDialog.show();
    }

    private void moveOldFilesToTheNewDirectory(
            @Nullable AbstractFile oldLocalThreadsDirectory,
            @Nullable AbstractFile newLocalThreadsDirectory) {
        if (oldLocalThreadsDirectory == null || newLocalThreadsDirectory == null) {
            Logger.e(TAG, "One of the directories is null, cannot copy " +
                    "(oldLocalThreadsDirectory is null == " + (oldLocalThreadsDirectory == null) + ")" +
                    ", newLocalThreadsDirectory is null == " + (newLocalThreadsDirectory == null) + ")");
            return;
        }

        Logger.d(TAG, "oldLocalThreadsDirectory = " + oldLocalThreadsDirectory.getFullPath()
                + ", newLocalThreadsDirectory = " + newLocalThreadsDirectory.getFullPath());

        navigationController.pushController(new LoadingViewController(context, true));

        fileCopyingExecutor.execute(() -> {
            boolean result = fileManager.copyDirectoryWithContent(
                    oldLocalThreadsDirectory,
                    newLocalThreadsDirectory);

            runOnUiThread(() -> {
                navigationController.popController();

                if (!result) {
                    // TODO: strings
                    Toast.makeText(
                            context,
                            "Could not copy one directory's file into another one",
                            Toast.LENGTH_LONG
                    ).show();
                } else {
                    if (!fileManager.forgetSAFTree(oldLocalThreadsDirectory)) {
                        // TODO: strings
                        Toast.makeText(
                                context,
                                "Files were copied but couldn't remove SAF permissions from the old directory",
                                Toast.LENGTH_SHORT
                        ).show();
                    } else {
                        // TODO: strings
                        Toast.makeText(
                                context,
                                "Successfully copied files",
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
            });
        });
    }

    private void showUseSAFOrOldAPIForSaveLocationDialog() {
        AlertDialog alertDialog = new AlertDialog.Builder(context)
                .setTitle(R.string.use_saf_for_save_location_dialog_title)
                .setMessage(R.string.use_saf_for_save_location_dialog_message)
                .setPositiveButton(R.string.use_saf_dialog_positive_button_text, (dialog, which) -> {
                    onSaveLocationUseSAFClicked();
                })
                .setNegativeButton(R.string.use_saf_dialog_negative_button_text, (dialog, which) -> {
                    onSaveLocationUseOldApiClicked();
                })
                .create();

        alertDialog.show();
    }

    /**
     * Select a directory where saved images will be stored via the old Java File API
     */
    private void onSaveLocationUseOldApiClicked() {
        SaveLocationController saveLocationController = new SaveLocationController(
                context,
                SaveLocationController.SaveLocationControllerMode.ImageSaveLocation,
                dirPath -> {
                    Logger.d(TAG, "SaveLocationController with ImageSaveLocation mode returned dir "
                            + dirPath);

                    // Supa hack to get the callback called
                    ChanSettings.saveLocation.setSync("");
                    ChanSettings.saveLocation.setSync(dirPath);
                });

        navigationController.pushController(saveLocationController);
    }

    /**
     * Select a directory where saved images will be stored via the SAF
     */
    private void onSaveLocationUseSAFClicked() {
        fileManager.openChooseDirectoryDialog(new DirectoryChooserCallback() {
            @Override
            public void onResult(@NotNull Uri uri) {
                ChanSettings.saveLocationUri.set(uri.toString());

                String defaultDir = ChanSettings.getDefaultSaveLocationDir();
                ChanSettings.saveLocation.setNoUpdate(defaultDir);
                saveLocation.setDescription(uri.toString());

            }

            @Override
            public void onCancel(@NotNull String reason) {
                Toast.makeText(context, reason, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void testMethod(@NotNull Uri uri) {
        {
            ExternalFile externalFile = fileManager.fromUri(uri)
                    .appendSubDirSegment("123")
                    .appendSubDirSegment("456")
                    .appendSubDirSegment("789")
                    .appendFileNameSegment("test123.txt")
                    .createNew();

            if (!externalFile.isFile()) {
                throw new RuntimeException("test123.txt is not a file");
            }

            if (externalFile.isDirectory()) {
                throw new RuntimeException("test123.txt is a directory");
            }

            if (externalFile == null || !externalFile.exists()) {
                throw new RuntimeException("Couldn't create test123.txt");
            }

            if (!externalFile.getName().equals("test123.txt")) {
                throw new RuntimeException("externalFile name != test123.txt");
            }

            boolean externalFile2Exists = fileManager.fromUri(uri)
                    .appendSubDirSegment("123")
                    .appendSubDirSegment("456")
                    .appendSubDirSegment("789")
                    .exists();

            if (!externalFile2Exists) {
                throw new RuntimeException("789 directory does not exist");
            }

            if (!externalFile.delete() && externalFile.exists()) {
                throw new RuntimeException("Couldn't delete test123.txt");
            }
        }

        {
            AbstractFile externalFile = fileManager.newSaveLocationFile()
                    .appendSubDirSegment("1234")
                    .appendSubDirSegment("4566")
                    .appendFileNameSegment("filename.json")
                    .createNew();

            if (externalFile == null || !externalFile.exists()) {
                throw new RuntimeException("Couldn't create filename.json");
            }

            if (!externalFile.isFile()) {
                throw new RuntimeException("filename.json is not a file");
            }

            if (externalFile.isDirectory()) {
                throw new RuntimeException("filename.json is not a directory");
            }

            if (!externalFile.getName().equals("filename.json")) {
                throw new RuntimeException("externalFile1 name != filename.json");
            }

            AbstractFile dir = fileManager.newSaveLocationFile()
                    .appendSubDirSegment("1234")
                    .appendSubDirSegment("4566");

            if (!dir.getName().equals("4566")) {
                throw new RuntimeException("dir.name != 4566, name = " + dir.getName());
            }

            AbstractFile foundFile = dir.findFile("filename.json");
            if (foundFile == null || !foundFile.exists()) {
                throw new RuntimeException("Couldn't find filename.json");
            }

            // Write string to the file
            String testString = "Hello world";

            foundFile.withFileDescriptor(FileDescriptorMode.WriteTruncate, (fd) -> {
                try (OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(fd))) {
                    osw.write(testString);
                    osw.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                return Unit.INSTANCE;
            });

            if (foundFile.getLength() != testString.length()) {
                throw new RuntimeException("file length != testString.length(), file length = "
                        + foundFile.getLength());
            }

            foundFile.withFileDescriptor(FileDescriptorMode.Read, (fd) -> {
                try (InputStreamReader isr = new InputStreamReader(new FileInputStream(fd))) {
                    char[] stringBytes = new char[testString.length()];
                    int read = isr.read(stringBytes);

                    if (read != testString.length()) {
                        throw new RuntimeException("read bytes != testString.length(), read = " + read);
                    }

                    String resultString = new String(stringBytes);
                    if (!resultString.equals(testString)) {
                        throw new RuntimeException("resultString != testString, resultString = "
                                + resultString);
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }

                return Unit.INSTANCE;
            });

            // Write another string that is shorter than the previous string
            String testString2 = "Hello";

            foundFile.withFileDescriptor(FileDescriptorMode.WriteTruncate, (fd) -> {
                try (OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(fd))) {
                    osw.write(testString2);
                    osw.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                return Unit.INSTANCE;
            });

            if (foundFile.getLength() != testString2.length()) {
                throw new RuntimeException("file length != testString.length(), file length = "
                        + foundFile.getLength());
            }

            foundFile.withFileDescriptor(FileDescriptorMode.Read, (fd) -> {
                try (InputStreamReader isr = new InputStreamReader(new FileInputStream(fd))) {
                    char[] stringBytes = new char[testString2.length()];
                    int read = isr.read(stringBytes);

                    if (read != testString2.length()) {
                        throw new RuntimeException("read bytes != testString2.length(), read = " + read);
                    }

                    String resultString = new String(stringBytes);
                    if (!resultString.equals(testString2)) {
                        throw new RuntimeException("resultString != testString2, resultString = "
                                + resultString);
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }

                return Unit.INSTANCE;
            });
        }

        {
            ExternalFile externalFile = fileManager.fromUri(uri);
            if (!externalFile.getName().equals("Test")) {
                throw new RuntimeException("externalFile.name != Test, name = " + externalFile.getName());
            }
        }

        System.out.println("All tests passed!");
    }

    private void setupMediaLoadTypesSetting(SettingsGroup loading) {
        List<ListSettingView.Item> imageAutoLoadTypes = new ArrayList<>();
        List<ListSettingView.Item> videoAutoLoadTypes = new ArrayList<>();
        for (ChanSettings.MediaAutoLoadMode mode : ChanSettings.MediaAutoLoadMode.values()) {
            int name = 0;
            switch (mode) {
                case ALL:
                    name = R.string.setting_image_auto_load_all;
                    break;
                case WIFI:
                    name = R.string.setting_image_auto_load_wifi;
                    break;
                case NONE:
                    name = R.string.setting_image_auto_load_none;
                    break;
            }

            imageAutoLoadTypes.add(new ListSettingView.Item<>(getString(name), mode));
            videoAutoLoadTypes.add(new ListSettingView.Item<>(getString(name), mode));
        }

        imageAutoLoadView = new ListSettingView<>(this,
                ChanSettings.imageAutoLoadNetwork, R.string.setting_image_auto_load,
                imageAutoLoadTypes);
        loading.add(imageAutoLoadView);

        videoAutoLoadView = new ListSettingView<>(this,
                ChanSettings.videoAutoLoadNetwork, R.string.setting_video_auto_load,
                videoAutoLoadTypes);
        loading.add(videoAutoLoadView);

        updateVideoLoadModes();
    }

    private void updateVideoLoadModes() {
        ChanSettings.MediaAutoLoadMode currentImageLoadMode = ChanSettings.imageAutoLoadNetwork.get();
        ChanSettings.MediaAutoLoadMode[] modes = ChanSettings.MediaAutoLoadMode.values();
        boolean enabled = false;
        boolean resetVideoMode = false;
        for (int i = 0; i < modes.length; i++) {
            if (modes[i].getKey().equals(currentImageLoadMode.getKey())) {
                enabled = true;
                if (resetVideoMode) {
                    ChanSettings.videoAutoLoadNetwork.set(modes[i]);
                    videoAutoLoadView.updateSelection();
                    onPreferenceChange(videoAutoLoadView);
                }
            }
            videoAutoLoadView.items.get(i).enabled = enabled;
            if (!enabled && ChanSettings.videoAutoLoadNetwork.get().getKey()
                    .equals(modes[i].getKey())) {
                resetVideoMode = true;
            }
        }
    }

    private void updateThreadFolderSetting() {
        if (ChanSettings.saveBoardFolder.get()) {
            threadFolderSetting.setEnabled(true);
        } else if (!ChanSettings.saveBoardFolder.get()) {
            if (ChanSettings.saveThreadFolder.get()) {
                threadFolderSetting.onClick(threadFolderSetting.view);
            }
            threadFolderSetting.setEnabled(false);
            ChanSettings.saveThreadFolder.set(false);
        }
    }
}
