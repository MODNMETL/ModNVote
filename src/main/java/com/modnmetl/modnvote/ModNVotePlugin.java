package com.modnmetl.modnvote;

import com.modnmetl.modnvote.commands.PollCommand;
import com.modnmetl.modnvote.config.MessageService;
import com.modnmetl.modnvote.listener.ActivePollNotificationListener;
import com.modnmetl.modnvote.platform.ModNScheduler;
import com.modnmetl.modnvote.platform.PaperPlatformAdapter;
import com.modnmetl.modnvote.platform.PlatformAdapter;
import com.modnmetl.modnvote.publication.WitnessPublicationService;
import com.modnmetl.modnvote.service.BallotService;
import com.modnmetl.modnvote.service.IntegrityVerificationService;
import com.modnmetl.modnvote.service.PollService;
import com.modnmetl.modnvote.service.ResultService;
import com.modnmetl.modnvote.storage.DatabaseManager;
import com.modnmetl.modnvote.storage.PollOptionDao;
import com.modnmetl.modnvote.storage.SchemaInitializer;
import com.modnmetl.modnvote.ui.builder.PollBuilderChatListener;
import com.modnmetl.modnvote.ui.builder.PollBuilderInputPromptManager;
import com.modnmetl.modnvote.ui.builder.PollBuilderListener;
import com.modnmetl.modnvote.ui.builder.PollBuilderRenderer;
import com.modnmetl.modnvote.ui.builder.PollBuilderSessionManager;
import com.modnmetl.modnvote.ui.builder.election.LinkedOfficesBuilderChatListener;
import com.modnmetl.modnvote.ui.builder.election.LinkedOfficesBuilderListener;
import com.modnmetl.modnvote.ui.builder.election.LinkedOfficesBuilderRenderer;
import com.modnmetl.modnvote.ui.builder.election.LinkedOfficesBuilderService;
import com.modnmetl.modnvote.ui.builder.election.LinkedOfficesBuilderSessionManager;
import com.modnmetl.modnvote.ui.builder.election.LinkedOfficesInputPromptManager;
import com.modnmetl.modnvote.ui.feedback.VoteSoundService;
import com.modnmetl.modnvote.ui.format.BallotSummaryFormatter;
import com.modnmetl.modnvote.ui.render.JavaInventoryVoteRenderer;
import com.modnmetl.modnvote.ui.render.VoteGuiListener;
import com.modnmetl.modnvote.ui.render.YesNoInventoryVoteRenderer;
import com.modnmetl.modnvote.ui.render.YesNoVoteGuiListener;
import com.modnmetl.modnvote.ui.session.VoteSessionCleanupListener;
import com.modnmetl.modnvote.ui.session.VoteSessionCloseCleanupListener;
import com.modnmetl.modnvote.ui.session.VoteSessionManager;
import com.modnmetl.modnvote.ui.session.YesNoVoteSessionCleanupListener;
import com.modnmetl.modnvote.ui.session.YesNoVoteSessionCloseCleanupListener;
import com.modnmetl.modnvote.ui.session.YesNoVoteSessionManager;
import com.modnmetl.modnvote.ui.submit.VoteSubmissionCoordinator;
import com.modnmetl.modnvote.ui.text.VoteGuiText;
import com.modnmetl.modnvote.ui.text.YesNoGuiText;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.logging.Level;

public final class ModNVotePlugin extends JavaPlugin {

    private ModNScheduler scheduler;
    private PlatformAdapter platformAdapter;
    private DatabaseManager databaseManager;
    private SchemaInitializer schemaInitializer;
    private PollService pollService;
    private BallotService ballotService;
    private MessageService messageService;
    private IntegrityVerificationService integrityVerificationService;
    private ResultService resultService;
    private WitnessPublicationService witnessPublicationService;

    private VoteSessionManager voteSessionManager;
    private YesNoVoteSessionManager yesNoVoteSessionManager;
    private BallotSummaryFormatter ballotSummaryFormatter;
    private VoteGuiText voteGuiText;
    private YesNoGuiText yesNoGuiText;
    private VoteSoundService voteSoundService;
    private JavaInventoryVoteRenderer javaInventoryVoteRenderer;
    private YesNoInventoryVoteRenderer yesNoInventoryVoteRenderer;
    private VoteSubmissionCoordinator voteSubmissionCoordinator;

    private PollBuilderSessionManager pollBuilderSessionManager;
    private PollBuilderInputPromptManager pollBuilderInputPromptManager;
    private PollBuilderRenderer pollBuilderRenderer;

    private LinkedOfficesBuilderSessionManager linkedOfficesBuilderSessionManager;
    private LinkedOfficesInputPromptManager linkedOfficesInputPromptManager;
    private LinkedOfficesBuilderRenderer linkedOfficesBuilderRenderer;
    private LinkedOfficesBuilderService linkedOfficesBuilderService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveBundledResourceIfMissing("messages.yml");

        try {
            this.scheduler = new ModNScheduler(this, getLogger());
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
            this.resultService = new ResultService(
                    databaseManager,
                    getLogger()
            );

            this.witnessPublicationService = new WitnessPublicationService(
                    this,
                    pollService,
                    integrityVerificationService,
                    databaseManager,
                    getLogger()
            );

            this.voteSessionManager = new VoteSessionManager(Duration.ofMinutes(10));
            this.yesNoVoteSessionManager = new YesNoVoteSessionManager(Duration.ofMinutes(10));
            this.ballotSummaryFormatter = new BallotSummaryFormatter();
            this.voteGuiText = new VoteGuiText(messageService, ballotSummaryFormatter);
            this.yesNoGuiText = new YesNoGuiText(messageService, ballotSummaryFormatter);
            this.voteSoundService = new VoteSoundService(this);
            this.javaInventoryVoteRenderer = new JavaInventoryVoteRenderer(scheduler, voteGuiText);
            this.yesNoInventoryVoteRenderer = new YesNoInventoryVoteRenderer(scheduler, yesNoGuiText);
            this.voteSubmissionCoordinator = new VoteSubmissionCoordinator(this, ballotService, witnessPublicationService);
            this.pollBuilderSessionManager = new PollBuilderSessionManager();
            this.pollBuilderInputPromptManager = new PollBuilderInputPromptManager();
            this.pollBuilderRenderer = new PollBuilderRenderer(pollService);

            this.linkedOfficesBuilderSessionManager = new LinkedOfficesBuilderSessionManager();
            this.linkedOfficesInputPromptManager = new LinkedOfficesInputPromptManager();
            this.linkedOfficesBuilderRenderer = new LinkedOfficesBuilderRenderer();
            this.linkedOfficesBuilderService = new LinkedOfficesBuilderService(pollService);

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
        if (yesNoVoteSessionManager != null) {
            yesNoVoteSessionManager.clearAllSessions();
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
                resultService,
                witnessPublicationService,
                messageService,
                voteSessionManager,
                yesNoVoteSessionManager,
                javaInventoryVoteRenderer,
                yesNoInventoryVoteRenderer
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
                        voteSoundService,
                        messageService
                ),
                this
        );
        getServer().getPluginManager().registerEvents(
                new YesNoVoteGuiListener(
                        yesNoVoteSessionManager,
                        yesNoInventoryVoteRenderer,
                        voteSubmissionCoordinator,
                        voteSoundService,
                        messageService
                ),
                this
        );
        getServer().getPluginManager().registerEvents(
                new VoteSessionCleanupListener(voteSessionManager),
                this
        );
        getServer().getPluginManager().registerEvents(
                new YesNoVoteSessionCleanupListener(yesNoVoteSessionManager),
                this
        );
        getServer().getPluginManager().registerEvents(
                new VoteSessionCloseCleanupListener(
                        this,
                        voteSessionManager,
                        javaInventoryVoteRenderer
                ),
                this
        );
        getServer().getPluginManager().registerEvents(
                new YesNoVoteSessionCloseCleanupListener(
                        this,
                        yesNoVoteSessionManager,
                        yesNoInventoryVoteRenderer
                ),
                this
        );
        getServer().getPluginManager().registerEvents(
                new ActivePollNotificationListener(
                        scheduler,
                        pollService,
                        ballotService,
                        getLogger()
                ),
                this
        );
        getServer().getPluginManager().registerEvents(
                new PollBuilderListener(
                        pollBuilderSessionManager,
                        pollBuilderInputPromptManager,
                        pollService,
                        new PollOptionDao(databaseManager),
                        scheduler,
                        pollBuilderRenderer
                ),
                this
        );
        getServer().getPluginManager().registerEvents(
                new PollBuilderChatListener(pollBuilderInputPromptManager),
                this
        );
        getServer().getPluginManager().registerEvents(
                new LinkedOfficesBuilderListener(
                        linkedOfficesBuilderSessionManager,
                        linkedOfficesInputPromptManager,
                        linkedOfficesBuilderService,
                        scheduler,
                        linkedOfficesBuilderRenderer
                ),
                this
        );
        getServer().getPluginManager().registerEvents(
                new LinkedOfficesBuilderChatListener(linkedOfficesInputPromptManager),
                this
        );
    }

    private void saveBundledResourceIfMissing(String resourcePath) {
        File target = new File(getDataFolder(), resourcePath);
        if (!target.exists()) {
            saveResource(resourcePath, false);
        }
    }

    public void reloadPluginConfiguration() {
        reloadConfig();
        if (messageService != null) {
            messageService.reload();
        }
    }

    public ModNScheduler getSchedulerBridge() {
        return Objects.requireNonNull(scheduler, "scheduler");
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

    public YesNoVoteSessionManager getYesNoVoteSessionManager() {
        return Objects.requireNonNull(yesNoVoteSessionManager, "yesNoVoteSessionManager");
    }

    public JavaInventoryVoteRenderer getJavaInventoryVoteRenderer() {
        return Objects.requireNonNull(javaInventoryVoteRenderer, "javaInventoryVoteRenderer");
    }

    public PollBuilderSessionManager getPollBuilderSessionManager() {
        return Objects.requireNonNull(pollBuilderSessionManager, "pollBuilderSessionManager");
    }

    public PollBuilderRenderer getPollBuilderRenderer() {
        return Objects.requireNonNull(pollBuilderRenderer, "pollBuilderRenderer");
    }

    public LinkedOfficesBuilderSessionManager getLinkedOfficesBuilderSessionManager() {
        return Objects.requireNonNull(linkedOfficesBuilderSessionManager, "linkedOfficesBuilderSessionManager");
    }

    public LinkedOfficesBuilderRenderer getLinkedOfficesBuilderRenderer() {
        return Objects.requireNonNull(linkedOfficesBuilderRenderer, "linkedOfficesBuilderRenderer");
    }

    public LinkedOfficesBuilderService getLinkedOfficesBuilderService() {
        return Objects.requireNonNull(linkedOfficesBuilderService, "linkedOfficesBuilderService");
    }

    public YesNoInventoryVoteRenderer getYesNoInventoryVoteRenderer() {
        return Objects.requireNonNull(yesNoInventoryVoteRenderer, "yesNoInventoryVoteRenderer");
    }
}
