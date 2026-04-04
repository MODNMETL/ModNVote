package com.modnmetl.modnvote;

import com.modnmetl.modnvote.commands.PollCommand;
import com.modnmetl.modnvote.config.MessageService;
import com.modnmetl.modnvote.platform.PaperPlatformAdapter;
import com.modnmetl.modnvote.platform.PlatformAdapter;
import com.modnmetl.modnvote.service.BallotService;
import com.modnmetl.modnvote.service.IntegrityVerificationService;
import com.modnmetl.modnvote.service.PollService;
import com.modnmetl.modnvote.storage.DatabaseManager;
import com.modnmetl.modnvote.storage.SchemaInitializer;
import com.modnmetl.modnvote.ui.format.BallotSummaryFormatter;
import com.modnmetl.modnvote.ui.render.JavaInventoryVoteRenderer;
import com.modnmetl.modnvote.ui.render.VoteGuiListener;
import com.modnmetl.modnvote.ui.session.VoteSessionCleanupListener;
import com.modnmetl.modnvote.ui.session.VoteSessionManager;
import com.modnmetl.modnvote.ui.submit.VoteSubmissionCoordinator;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.logging.Level;

/**
 * ModNVote 2.0 plugin bootstrap.
 *
 * This replaces the 1.x single-round yes/no runtime with a ballot-first
 * platform foundation for the 2.x architecture.
 *
 * At this stage the bootstrap is responsible for:
 * - configuration and messages resource setup
 * - database and schema initialization
 * - core service wiring
 * - integrity verification service wiring
 * - vote-session and renderer wiring
 * - command and listener registration
 *
 * Vote-session GUI flows are now active for ranked voting, with broader
 * vote-engine expansion layered on top in later development stages.
 */
public final class ModNVotePlugin extends JavaPlugin {

    private PlatformAdapter platformAdapter;
    private DatabaseManager databaseManager;
    private SchemaInitializer schemaInitializer;
    private PollService pollService;
    private BallotService ballotService;
    private MessageService messageService;
    private IntegrityVerificationService integrityVerificationService;

    private VoteSessionManager voteSessionManager;
    private BallotSummaryFormatter ballotSummaryFormatter;
    private JavaInventoryVoteRenderer javaInventoryVoteRenderer;
    private VoteSubmissionCoordinator voteSubmissionCoordinator;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false);

        try {
            this.platformAdapter = new PaperPlatformAdapter(this);

            String sqliteFileName = getConfig().getString("storage.sqlite_file", "modnvote.db");
            Path databasePath = getDataFolder().toPath().resolve(sqliteFileName);

            this.databaseManager = new DatabaseManager(databasePath);
            this.schemaInitializer = new SchemaInitializer(databaseManager);
            this.schemaInitializer.initialize();

            this.pollService = new PollService(databaseManager, platformAdapter, getLogger());
            this.ballotService = new BallotService(databaseManager, platformAdapter, getLogger());
            this.messageService = new MessageService(this);
            this.integrityVerificationService = new IntegrityVerificationService(
                    databaseManager,
                    platformAdapter,
                    getLogger()
            );

            this.voteSessionManager = new VoteSessionManager(Duration.ofMinutes(10));
            this.ballotSummaryFormatter = new BallotSummaryFormatter();
            this.javaInventoryVoteRenderer = new JavaInventoryVoteRenderer(ballotSummaryFormatter);
            this.voteSubmissionCoordinator = new VoteSubmissionCoordinator(this, ballotService);

            registerCommands();
            registerListeners();

            getLogger().info("ModNVote 2.0 bootstrap enabled successfully.");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to enable ModNVote 2.0", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (voteSessionManager != null) {
            voteSessionManager.clearAllSessions();
        }

        if (databaseManager != null) {
            databaseManager.close();
        }
    }

    private void registerCommands() {
        PluginCommand command = getCommand("modnvote");
        if (command == null) {
            throw new IllegalStateException("Command 'modnvote' is not defined in plugin.yml");
        }

        PollCommand executor = new PollCommand(
                this,
                pollService,
                ballotService,
                integrityVerificationService,
                messageService,
                voteSessionManager,
                javaInventoryVoteRenderer
        );
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(
                new VoteGuiListener(
                        voteSessionManager,
                        javaInventoryVoteRenderer,
                        voteSubmissionCoordinator,
                        messageService
                ),
                this
        );
        getServer().getPluginManager().registerEvents(
                new VoteSessionCleanupListener(voteSessionManager),
                this
        );
    }

    public void reloadPluginConfiguration() {
        reloadConfig();
        if (messageService != null) {
            messageService.reload();
        }
    }

    public PlatformAdapter getPlatformAdapter() {
        return Objects.requireNonNull(platformAdapter, "platformAdapter");
    }

    public DatabaseManager getDatabaseManager() {
        return Objects.requireNonNull(databaseManager, "databaseManager");
    }

    public PollService getPollService() {
        return Objects.requireNonNull(pollService, "pollService");
    }

    public BallotService getBallotService() {
        return Objects.requireNonNull(ballotService, "ballotService");
    }

    public MessageService getMessageService() {
        return Objects.requireNonNull(messageService, "messageService");
    }

    public IntegrityVerificationService getIntegrityVerificationService() {
        return Objects.requireNonNull(integrityVerificationService, "integrityVerificationService");
    }

    public VoteSessionManager getVoteSessionManager() {
        return Objects.requireNonNull(voteSessionManager, "voteSessionManager");
    }

    public JavaInventoryVoteRenderer getJavaInventoryVoteRenderer() {
        return Objects.requireNonNull(javaInventoryVoteRenderer, "javaInventoryVoteRenderer");
    }
}