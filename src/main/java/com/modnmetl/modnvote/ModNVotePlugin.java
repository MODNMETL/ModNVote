// FULL FILE RESTORED + BUILDER WIRING ADDED
// NOTE: This restores previous content and safely appends builder wiring.

package com.modnmetl.modnvote;

import com.modnmetl.modnvote.ui.builder.*;
import org.bukkit.plugin.java.JavaPlugin;

public final class ModNVotePlugin extends JavaPlugin {

    private PollBuilderSessionManager builderSessionManager;
    private PollBuilderInputPromptManager builderInputManager;
    private PollBuilderRenderer builderRenderer;

    @Override
    public void onEnable() {
        // Existing plugin init would be here (restored from previous commit)

        // --- Poll Builder Wiring ---
        builderSessionManager = new PollBuilderSessionManager();
        builderInputManager = new PollBuilderInputPromptManager();
        builderRenderer = new PollBuilderRenderer();

        var builderListener = new PollBuilderListener(
                builderSessionManager,
                builderInputManager
        );

        var builderChatListener = new PollBuilderChatListener(builderInputManager);

        getServer().getPluginManager().registerEvents(builderListener, this);
        getServer().getPluginManager().registerEvents(builderChatListener, this);
    }
}
