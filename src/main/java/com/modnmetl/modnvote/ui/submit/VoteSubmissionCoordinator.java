package com.modnmetl.modnvote.ui.submit;

import com.modnmetl.modnvote.publication.WitnessPublicationService;
import com.modnmetl.modnvote.service.BallotService;
import com.modnmetl.modnvote.service.PollServiceException;
import com.modnmetl.modnvote.ui.session.VoteSession;
import com.modnmetl.modnvote.ui.session.YesNoVoteSession;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;

public final class VoteSubmissionCoordinator {

    private static final String DEFAULT_BYPASS_NODE = "modnvote.bypass";
    private static final String CLIENT_PLATFORM_RANKED = "JAVA_GUI";
    private static final String CLIENT_PLATFORM_YES_NO = "JAVA_GUI_YES_NO";

    private final JavaPlugin plugin;
    private final BallotService ballotService;
    private final WitnessPublicationService witnessPublicationService;

    public VoteSubmissionCoordinator(JavaPlugin plugin,
                                     BallotService ballotService,
                                     WitnessPublicationService witnessPublicationService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.ballotService = Objects.requireNonNull(ballotService, "ballotService");
        this.witnessPublicationService = Objects.requireNonNull(witnessPublicationService, "witnessPublicationService");
    }

    public SubmissionOutcome submitRankedVote(Player player,
                                              VoteSession session) throws PollServiceException {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(session, "session");

        String ipHash = requirePlayerIpHash(player);
        boolean bypassIpDuplicateCheck = hasBypassPermission(player);

        BallotService.SubmissionResult result = ballotService.submitRankedBallot(
                session.pollId(),
                player.getUniqueId().toString(),
                CLIENT_PLATFORM_RANKED,
                session.rankedOptionIds(),
                ipHash,
                null,
                bypassIpDuplicateCheck
        );

        if (result.success()) {
            witnessPublicationService.maybePublishCheckpoint(session.pollId());
        }

        return new SubmissionOutcome(result, bypassIpDuplicateCheck);
    }

    public SubmissionOutcome submitYesNoVote(Player player,
                                             YesNoVoteSession session) throws PollServiceException {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(session, "session");

        Long selectedOptionId = session.selectedOptionId();
        if (selectedOptionId == null) {
            throw new PollServiceException("A yes/no choice must be selected before submission.");
        }

        String ipHash = requirePlayerIpHash(player);
        boolean bypassIpDuplicateCheck = hasBypassPermission(player);

        BallotService.SubmissionResult result = ballotService.submitYesNoBallot(
                session.pollId(),
                player.getUniqueId().toString(),
                CLIENT_PLATFORM_YES_NO,
                selectedOptionId,
                ipHash,
                null,
                bypassIpDuplicateCheck
        );

        if (result.success()) {
            witnessPublicationService.maybePublishCheckpoint(session.pollId());
        }

        return new SubmissionOutcome(result, bypassIpDuplicateCheck);
    }

    private boolean hasBypassPermission(Player player) {
        String bypassNode = plugin.getConfig().getString("permissions.bypass_node", DEFAULT_BYPASS_NODE);
        return player.hasPermission(bypassNode);
    }

    private String requirePlayerIpHash(Player player) throws PollServiceException {
        String ipHash = hashPlayerIp(player);
        if (ipHash == null) {
            throw new PollServiceException("We could not confirm your network address for duplicate-protection checks.");
        }
        return ipHash;
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
