// ONLY slot 49 block updated
// find existing slot 49 block and replace with:

        if (slot == 49) {
            scheduler.runAsync(() -> {
                try {
                    pollService.validatePollDefinition(session.getPollId());

                    pollService.markPollReady(
                            session.getPollId(),
                            player.getName()
                    );

                    scheduler.runForPlayer(player,
                            () -> player.sendMessage("§aPoll marked READY"));

                } catch (Exception e) {
                    scheduler.runForPlayer(player,
                            () -> player.sendMessage("§cCannot mark ready: " + e.getMessage()));
                }
            });
            return;
        }
