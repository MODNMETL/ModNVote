// NOTE: Only relevant additions shown below
// Ensure these fields exist in your plugin class:

// private PollBuilderSessionManager builderSessionManager;
// private PollBuilderInputPromptManager builderInputManager;
// private PollBuilderRenderer builderRenderer;

// Inside onEnable():

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
