package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.Tools.dialogOnUiThread;
import static net.kdt.pojavlaunch.Tools.hasNoOnlineProfileDialog;
import static net.kdt.pojavlaunch.Tools.hasOnlineProfile;
import static net.kdt.pojavlaunch.Tools.openPath;
import static net.kdt.pojavlaunch.Tools.runOnUiThread;
import static net.kdt.pojavlaunch.Tools.shareLog;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.mcVersionSpinner;

import net.kdt.pojavlaunch.CustomControlsActivity;
import net.kdt.pojavlaunch.NexusUpdateManager;
import net.kdt.pojavlaunch.PojavProfile;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.value.MinecraftAccount;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;

public class MainMenuFragment extends Fragment {
    public static final String TAG = "MainMenuFragment";

    private static final String DISCORD_URL = "https://discord.gg/stsstudio";
    private static final String STORE_URL = "https://stsstudio.org/store";
    private static final String WEBSITE_URL = "https://stsstudio.org";

    private mcVersionSpinner mVersionSpinner;

    public MainMenuFragment(){
        super(R.layout.fragment_launcher);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mVersionSpinner = view.findViewById(R.id.mc_version_spinner);

        // 1. Setup User and Profile Header Info
        setupUserAndProfileInfo(view);

        // 2. Setup Play Launch Triggers
        setupPlayTriggers(view);

        // 3. Setup Left Sidebar and Bottom Navigation
        setupNavigation(view);

        // 4. Setup Quick Access and Server Cards
        setupQuickAccessAndServers(view);

        // 5. Setup Edition Toggle
        setupEditionToggle(view);

        // 6. Automatic background update check
        view.postDelayed(() -> {
            if (isAdded() && getActivity() != null && !getActivity().isFinishing()) {
                NexusUpdateManager.checkForUpdate(requireActivity(), false);
            }
        }, 2500);

        // Handle physical/system back button safely
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                setEnabled(false);
                requireActivity().onBackPressed();
            }
        });

        // Setup preserved Pojav hooks so no legacy code or listeners throw NullPointerException
        setupPreservedHooks(view);
    }

    private void setupUserAndProfileInfo(@NonNull View view) {
        MinecraftAccount currentAccount = PojavProfile.getCurrentProfileContent(requireContext(), null);
        String username = (currentAccount != null && Tools.isValidString(currentAccount.username))
                ? currentAccount.username : "Player";

        TextView txtWelcome = view.findViewById(R.id.txt_welcome_title);
        if (txtWelcome != null) {
            txtWelcome.setText("Welcome back,\n" + username + "!");
        }

        TextView txtAccountName = view.findViewById(R.id.txt_account_name);
        if (txtAccountName != null) {
            txtAccountName.setText(username);
        }

        View chipAccount = view.findViewById(R.id.chip_active_account);
        if (chipAccount != null) {
            chipAccount.setOnClickListener(v -> Tools.swapFragment(requireActivity(), SelectAuthFragment.class, SelectAuthFragment.TAG, null));
        }

        // Active profile and version display
        String profileName = "Nexus 1.21.11";
        String versionDetails = "Fabric 1.21.11 • Active";
        try {
            LauncherProfiles.load();
            MinecraftProfile prof = LauncherProfiles.getCurrentProfile();
            if (prof != null) {
                if (Tools.isValidString(prof.name)) profileName = prof.name;
                if (Tools.isValidString(prof.lastVersionId)) versionDetails = prof.lastVersionId + " • Active";
            }
        } catch (Exception ignored) {}

        TextView txtHeroVer = view.findViewById(R.id.txt_hero_version_label);
        if (txtHeroVer != null) txtHeroVer.setText(profileName + " Active ›");

        TextView txtStripName = view.findViewById(R.id.txt_strip_version_name);
        if (txtStripName != null) txtStripName.setText(profileName);

        TextView txtStripSub = view.findViewById(R.id.txt_strip_version_sub);
        if (txtStripSub != null) txtStripSub.setText(versionDetails);

        View btnHeroVerPill = view.findViewById(R.id.btn_hero_version_pill);
        if (btnHeroVerPill != null && mVersionSpinner != null) {
            btnHeroVerPill.setOnClickListener(v -> mVersionSpinner.openProfileEditor(requireActivity()));
        }

        View stripSelector = view.findViewById(R.id.strip_version_selector);
        if (stripSelector != null && mVersionSpinner != null) {
            stripSelector.setOnClickListener(v -> mVersionSpinner.openProfileEditor(requireActivity()));
        }
    }

    private void setupPlayTriggers(@NonNull View view) {
        View.OnClickListener launchListener = v -> launchGame();

        Button btnHeroPlay = view.findViewById(R.id.btn_hero_play);
        if (btnHeroPlay != null) btnHeroPlay.setOnClickListener(launchListener);

        Button btnBmPlay = view.findViewById(R.id.btn_breezemc_play);
        if (btnBmPlay != null) btnBmPlay.setOnClickListener(launchListener);

        View navPlay = view.findViewById(R.id.nav_btn_play);
        if (navPlay != null) navPlay.setOnClickListener(launchListener);

        View bottomPlay = view.findViewById(R.id.bottom_nav_play);
        if (bottomPlay != null) bottomPlay.setOnClickListener(launchListener);

        Button legacyPlay = view.findViewById(R.id.play_button);
        if (legacyPlay != null) legacyPlay.setOnClickListener(launchListener);
    }

    private void launchGame() {
        if (Tools.hasMods("sodium") && !(LauncherPreferences.DEFAULT_PREF.getBoolean("sodium_override", false))) {
            AlertDialog sodiumWarningDialog = new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.sodium_warning_title)
                    .setMessage(R.string.sodium_warning_message)
                    .setNeutralButton(R.string.delete_sodium, (d, w) -> {
                        Tools.deleteSodiumMods();
                        ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
                    })
                    .create();
            sodiumWarningDialog.show();
        } else {
            ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
        }
    }

    private void setupNavigation(@NonNull View view) {
        NestedScrollView scrollContainer = view.findViewById(R.id.main_scroll_container);

        View navHome = view.findViewById(R.id.nav_btn_home);
        if (navHome != null) navHome.setOnClickListener(v -> {
            if (scrollContainer != null) scrollContainer.smoothScrollTo(0, 0);
        });

        View bottomHome = view.findViewById(R.id.bottom_nav_home);
        if (bottomHome != null) bottomHome.setOnClickListener(v -> {
            if (scrollContainer != null) scrollContainer.smoothScrollTo(0, 0);
        });

        View.OnClickListener modsListener = v -> Tools.swapFragment(requireActivity(), SearchModFragment.class, SearchModFragment.TAG, null);
        View navMods = view.findViewById(R.id.nav_btn_mods);
        if (navMods != null) navMods.setOnClickListener(modsListener);
        View bottomMods = view.findViewById(R.id.bottom_nav_mods);
        if (bottomMods != null) bottomMods.setOnClickListener(modsListener);

        View.OnClickListener libListener = v -> Tools.swapFragment(requireActivity(), ProfileTypeSelectFragment.class, ProfileTypeSelectFragment.TAG, null);
        View navLibrary = view.findViewById(R.id.nav_btn_library);
        if (navLibrary != null) navLibrary.setOnClickListener(libListener);
        View bottomLibrary = view.findViewById(R.id.bottom_nav_library);
        if (bottomLibrary != null) bottomLibrary.setOnClickListener(libListener);

        View.OnClickListener serverListener = v -> copyToClipboard("play.stsstudio.org", "STS Studio Network (play.stsstudio.org) copied!");
        View navServers = view.findViewById(R.id.nav_btn_servers);
        if (navServers != null) navServers.setOnClickListener(serverListener);

        View.OnClickListener storeListener = v -> Tools.openURL(requireActivity(), STORE_URL);
        View navStore = view.findViewById(R.id.nav_btn_store);
        if (navStore != null) navStore.setOnClickListener(storeListener);

        View navSettings = view.findViewById(R.id.nav_btn_settings);
        if (navSettings != null) {
            navSettings.setOnClickListener(v -> startActivity(new Intent(requireContext(), CustomControlsActivity.class)));
        }

        View bottomMore = view.findViewById(R.id.bottom_nav_more);
        if (bottomMore != null) {
            bottomMore.setOnClickListener(v -> showMoreMenuDialog());
        }
    }

    private void setupQuickAccessAndServers(@NonNull View view) {
        View qaMods = view.findViewById(R.id.btn_qa_mods);
        if (qaMods != null) {
            qaMods.setOnClickListener(v -> Tools.swapFragment(requireActivity(), SearchModFragment.class, SearchModFragment.TAG, null));
        }

        View qaWorlds = view.findViewById(R.id.btn_qa_worlds);
        if (qaWorlds != null) {
            qaWorlds.setOnClickListener(v -> openPath(v.getContext(), getCurrentProfileDirectory(), false));
        }

        View qaServers = view.findViewById(R.id.btn_qa_servers);
        if (qaServers != null) {
            qaServers.setOnClickListener(v -> copyToClipboard("play.stsstudio.org", "STS Studio Network (play.stsstudio.org) copied!"));
        }

        View qaStore = view.findViewById(R.id.btn_qa_store);
        if (qaStore != null) {
            qaStore.setOnClickListener(v -> Tools.openURL(requireActivity(), STORE_URL));
        }

        View lastPlayed = view.findViewById(R.id.btn_last_played);
        if (lastPlayed != null) {
            lastPlayed.setOnClickListener(v -> openPath(v.getContext(), getCurrentProfileDirectory(), false));
        }

        // Featured server cards
        View cardHyades = view.findViewById(R.id.card_server_hyades);
        if (cardHyades != null) {
            cardHyades.setOnClickListener(v -> copyToClipboard("play.stsstudio.org", "HyadesMC server IP (play.stsstudio.org) copied!"));
        }

        View cardCraftport = view.findViewById(R.id.card_server_craftport);
        if (cardCraftport != null) {
            cardCraftport.setOnClickListener(v -> copyToClipboard("craftport.net", "CraftPort server IP (craftport.net) copied!"));
        }

        View cardEnchanted = view.findViewById(R.id.card_server_enchanted);
        if (cardEnchanted != null) {
            cardEnchanted.setOnClickListener(v -> copyToClipboard("enchantedmc.net", "Enchanted server IP (enchantedmc.net) copied!"));
        }

        View cardBlockbuster = view.findViewById(R.id.card_server_blockbuster);
        if (cardBlockbuster != null) {
            cardBlockbuster.setOnClickListener(v -> copyToClipboard("blockbuster.gg", "BlockBuster server IP (blockbuster.gg) copied!"));
        }

        View btnViewAllServers = view.findViewById(R.id.btn_view_all_servers);
        if (btnViewAllServers != null) {
            btnViewAllServers.setOnClickListener(v -> showServersDialog());
        }

        // News cards
        View.OnClickListener newsListener = v -> Tools.openURL(requireActivity(), WEBSITE_URL);
        View news1 = view.findViewById(R.id.news_item_1);
        if (news1 != null) news1.setOnClickListener(newsListener);
        View news2 = view.findViewById(R.id.news_item_2);
        if (news2 != null) news2.setOnClickListener(newsListener);
        View btnViewNews = view.findViewById(R.id.btn_view_all_news);
        if (btnViewNews != null) btnViewNews.setOnClickListener(newsListener);
    }

    private void setupEditionToggle(@NonNull View view) {
        Button btnToggleJava = view.findViewById(R.id.btn_toggle_java);
        Button btnToggleBedrock = view.findViewById(R.id.btn_toggle_bedrock);

        if (btnToggleJava != null) {
            btnToggleJava.setOnClickListener(v -> {
                btnToggleJava.setBackgroundResource(R.drawable.bg_nav_pill_active);
                btnToggleJava.setTextColor(Color.WHITE);
                if (btnToggleBedrock != null) {
                    btnToggleBedrock.setBackgroundResource(R.drawable.bg_chip_tag);
                    btnToggleBedrock.setTextColor(Color.parseColor("#A09BB8"));
                }
                Toast.makeText(requireContext(), "Java Edition active", Toast.LENGTH_SHORT).show();
            });
        }

        if (btnToggleBedrock != null) {
            btnToggleBedrock.setOnClickListener(v -> showBedrockLaunchDialog());
        }
    }

    private void showBedrockLaunchDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Minecraft Bedrock Edition")
                .setMessage("Launch Bedrock on STS Studio Network:\n\n"
                        + "• Server Address: play.stsstudio.org\n"
                        + "• Bedrock Port: 19132 (Default Port)\n\n"
                        + "You can launch Bedrock on this device or copy server details to connect.")
                .setPositiveButton("Launch Bedrock", (d, w) -> {
                    try {
                        Intent mcIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("minecraft://?addExternalServer=STS%20Studio|play.stsstudio.org:19132"));
                        if (mcIntent.resolveActivity(requireContext().getPackageManager()) != null) {
                            startActivity(mcIntent);
                            return;
                        }
                    } catch (Exception ignored) {}

                    Intent launchIntent = requireContext().getPackageManager().getLaunchIntentForPackage("com.mojang.minecraftpe");
                    if (launchIntent != null) {
                        startActivity(launchIntent);
                        Toast.makeText(requireContext(), "Opening Minecraft Bedrock. Server: play.stsstudio.org (Port 19132)", Toast.LENGTH_LONG).show();
                    } else {
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.mojang.minecraftpe")));
                        } catch (Exception e) {
                            Tools.openURL(requireActivity(), "https://play.google.com/store/apps/details?id=com.mojang.minecraftpe");
                        }
                    }
                })
                .setNeutralButton("Copy Server IP", (d, w) -> copyToClipboard("play.stsstudio.org:19132", "Bedrock Server (play.stsstudio.org:19132) copied!"))
                .setNegativeButton("Close", null)
                .show();
    }

    private void showServersDialog() {
        CharSequence[] servers = new CharSequence[] {
                "HyadesMC (play.stsstudio.org - Season 0 & 2 Lifesteal)",
                "BreezeMC (play.breezemc.fun - Lifesteal & Survival)",
                "CraftPort (craftport.net)",
                "Enchanted (enchantedmc.net)",
                "BlockBuster (blockbuster.gg)"
        };
        new AlertDialog.Builder(requireContext())
                .setTitle("Partner & Featured Servers")
                .setItems(servers, (d, which) -> {
                    String ip = "play.stsstudio.org";
                    if (which == 1) ip = "play.breezemc.fun";
                    else if (which == 2) ip = "craftport.net";
                    else if (which == 3) ip = "enchantedmc.net";
                    else if (which == 4) ip = "blockbuster.gg";
                    copyToClipboard(ip, "Server IP (" + ip + ") copied!");
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void showMoreMenuDialog() {
        CharSequence[] options = new CharSequence[] {
                "Check for Updates",
                "Join Discord Server",
                "Custom Touch Controls",
                "Install .JAR Mod / Installer",
                "Share Crash Logs",
                "Open Game Directory",
                "Visit STS Studio Website"
        };
        new AlertDialog.Builder(requireContext())
                .setTitle("Nexus Options & Tools")
                .setItems(options, (d, which) -> {
                    switch (which) {
                        case 0:
                            NexusUpdateManager.checkForUpdate(requireActivity(), true);
                            break;
                        case 1:
                            Tools.openURL(requireActivity(), DISCORD_URL);
                            break;
                        case 2:
                            startActivity(new Intent(requireContext(), CustomControlsActivity.class));
                            break;
                        case 3:
                            runInstallerWithConfirmation(false);
                            break;
                        case 4:
                            shareLog(requireContext());
                            break;
                        case 5:
                            openPath(requireContext(), getCurrentProfileDirectory(), false);
                            break;
                        case 6:
                            Tools.openURL(requireActivity(), WEBSITE_URL);
                            break;
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void setupPreservedHooks(@NonNull View view) {
        // Preserves references for legacy Pojav click listeners and activities
        Button btnTopCheckUpdate = view.findViewById(R.id.btn_top_check_update);
        if (btnTopCheckUpdate != null) {
            btnTopCheckUpdate.setOnClickListener(v -> NexusUpdateManager.checkForUpdate(requireActivity(), true));
        }

        Button btnTopDiscord = view.findViewById(R.id.btn_top_discord);
        if (btnTopDiscord != null) {
            btnTopDiscord.setOnClickListener(v -> Tools.openURL(requireActivity(), DISCORD_URL));
        }

        Button btnTopSwitchAccount = view.findViewById(R.id.btn_top_switch_account);
        if (btnTopSwitchAccount != null) {
            btnTopSwitchAccount.setOnClickListener(v -> Tools.swapFragment(requireActivity(), SelectAuthFragment.class, SelectAuthFragment.TAG, null));
        }

        Button mInstallJarButton = view.findViewById(R.id.install_jar_button);
        if (mInstallJarButton != null) {
            mInstallJarButton.setOnClickListener(v -> runInstallerWithConfirmation(false));
        }

        Button mShareLogsButton = view.findViewById(R.id.share_logs_button);
        if (mShareLogsButton != null) {
            mShareLogsButton.setOnClickListener((v) -> shareLog(requireContext()));
        }

        Button mOpenDirectoryButton = view.findViewById(R.id.open_files_button);
        if (mOpenDirectoryButton != null) {
            mOpenDirectoryButton.setOnClickListener((v) -> openPath(v.getContext(), getCurrentProfileDirectory(), false));
        }

        setupRamPills(view);
    }

    private void setupRamPills(View root) {
        Button ram2gb = root.findViewById(R.id.btn_ram_2g);
        Button ram4gb = root.findViewById(R.id.btn_ram_4g);
        Button ram6gb = root.findViewById(R.id.btn_ram_6g);

        View.OnClickListener ramListener = v -> {
            int inactiveBg = Color.parseColor("#1E1B2E");
            int inactiveTxt = Color.parseColor("#A0A0B8");
            int activeBg = Color.parseColor("#6C5CE7");

            if (ram2gb != null) { ram2gb.setBackgroundColor(inactiveBg); ram2gb.setTextColor(inactiveTxt); }
            if (ram4gb != null) { ram4gb.setBackgroundColor(inactiveBg); ram4gb.setTextColor(inactiveTxt); }
            if (ram6gb != null) { ram6gb.setBackgroundColor(inactiveBg); ram6gb.setTextColor(inactiveTxt); }

            Button clicked = (Button) v;
            clicked.setBackgroundColor(activeBg);
            clicked.setTextColor(Color.WHITE);

            int ramValue = 4096;
            if (v.getId() == R.id.btn_ram_2g) ramValue = 2048;
            else if (v.getId() == R.id.btn_ram_6g) ramValue = 6144;

            LauncherPreferences.DEFAULT_PREF.edit().putInt("allocation", ramValue).apply();
            Toast.makeText(requireContext(), "Allocated RAM: " + clicked.getText(), Toast.LENGTH_SHORT).show();
        };

        if (ram2gb != null) ram2gb.setOnClickListener(ramListener);
        if (ram4gb != null) ram4gb.setOnClickListener(ramListener);
        if (ram6gb != null) ram6gb.setOnClickListener(ramListener);
    }

    private void copyToClipboard(String text, String message) {
        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Server IP", text);
        if (clipboard != null) clipboard.setPrimaryClip(clip);
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    private File getCurrentProfileDirectory() {
        String currentProfile = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
        if (!Tools.isValidString(currentProfile)) return new File(Tools.DIR_GAME_NEW);
        LauncherProfiles.load();
        MinecraftProfile profileObject = LauncherProfiles.mainProfileJson.profiles.get(currentProfile);
        if (profileObject == null) return new File(Tools.DIR_GAME_NEW);
        return Tools.isValidString(profileObject.gameDir) ? new File(profileObject.gameDir) : new File(Tools.DIR_GAME_NEW);
    }

    private void runInstallerWithConfirmation(boolean isCustom) {
        if (!hasOnlineProfile()) {
            hasNoOnlineProfileDialog(requireActivity());
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle(R.string.alerttitle_installmod);
        builder.setMessage("Select a .jar installer file to execute.");
        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
            Bundle bundle = new Bundle();
            bundle.putBoolean(FileSelectorFragment.BUNDLE_SELECT_FILE, true);
            Tools.swapFragment(requireActivity(), FileSelectorFragment.class, FileSelectorFragment.TAG, bundle);
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }
}
