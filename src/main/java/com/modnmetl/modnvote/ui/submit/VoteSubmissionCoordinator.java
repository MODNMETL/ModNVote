package com.modnmetl.modnvote.ui.submit;

import com.modnmetl.modnvote.service.BallotService;
import com.modnmetl.modnvote.service.PollServiceException;
import com.modnmetl.modnvote.ui.session.VoteSession;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Bridges in-memory vote sessions to the authoritative ballot submission service.
 *
 * Responsibilities:
 * - extract ranked selections from a VoteSession
 * - derive submission context from the player
 * - apply configured bypass permission checks
 * - submit through BallotService
 *
 * Non-responsibilities:
 * - no GUI rendering
 * - no inventory event handling
 * - no direct session storage/cleanup
 */
public final class VoteSubmissionCoordinator {

    private static final String DEFAULT_BYPASS_NODE = "modnvote.bypass";
    private static final String CLIENT_PLATFORM = "JAVA_GUI";

    private final JavaPlugin plugin;
    private final BallotService ballotService;

    public VoteSubmissionCoordinator(JavaPlugin plugin,
                                     BallotService ballotService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.ballotService = Objects.requireNonNull(ballotService, "ballotService");
    }

    public SubmissionOutcome submitRankedVote(Player player,
                                              VoteSession session) throws PollServiceException {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(session, "session");

        String ipHash = hashPlayerIp(player);
        if (ipHash == null) {
            throw new PollServiceException("We could not confirm your network address for duplicate-protection checks.");
        }

        String bypassNode = plugin.getConfig().getString("permissions.bypass_node", DEFAULT_BYPASS_NODE);
        boolean bypassIpDuplicateCheck = player.hasPermission(bypassNode);

        BallotService.SubmissionResult result = ballotService.submitRankedBallot(
                session.pollId(),
                player.getUniqueId().toString(),
                CLIENT_PLATFORM,
                session.rankedOptionIds(),
                ipHash,
                null,
                bypassIpDuplicateCheck
        );

        return new SubmissionOutcome(result, bypassIpDuplicateCheck);
    }

    private String hashPlayerIp(Player player) {
        try {
            InetSocketAddress address = player.getAddress();
            if (address == null || address.getAddress() == null) {
                return null;
            }

            String hostAddress = address.getAddress().getHostAddress();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(hostAddress.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            return null;
        }
    }

    public record SubmissionOutcome(
            BallotService.SubmissionResult submissionResult,
            boolean bypassIpDuplicateCheck
    ) {
        public SubmissionOutcome {
            Objects.requireNonNull(submissionResult, "submissionResult");
        }
    }
}