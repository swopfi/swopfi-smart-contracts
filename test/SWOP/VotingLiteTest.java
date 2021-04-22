package SWOP;

import com.wavesplatform.crypto.Crypto;
import com.wavesplatform.transactions.account.PrivateKey;
import dapps.GovernanceDApp;
import dapps.VotingDApp;
import im.mak.paddle.Account;
import im.mak.paddle.exceptions.ApiError;
import com.wavesplatform.transactions.common.AssetId;
import com.wavesplatform.transactions.data.IntegerEntry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static im.mak.paddle.token.Waves.WAVES;
import static im.mak.paddle.util.Async.async;
import static im.mak.paddle.Node.node;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.Alphanumeric.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class VotingLiteTest {
    private final long firstCallerInitAmount = 1000_00000000L;
    private final long secondCallerInitAmount = 1000_00000000L;
    private final String keyRewardPoolFractionCurrent = "_current_pool_fraction_reward";
    private final String keyRewardPoolFractionPrevious = "_previous_pool_fraction_reward";
    private final String keyRewardUpdateHeight = "reward_update_height";
    private final String kUserPoolVoteSWOP = "_vote";
    private final String kUserTotalVoteSWOP = "_user_total_SWOP_vote";
    private final String kPoolVoteSWOP = "_vote_SWOP";
    private final String kTotalVoteSWOP = "total_vote_SWOP";
    private final String firstPool = "3P5N94Qdb8SqJuy56p1btfzz1zACpPbqs6x";
    private final String secondPool = "3PA26XNQfUzwNQHhSEbtKzRfYFvAcgj2Nfw";
    private final String thirdPool = "3PLZSEaGDLht8GGK8rDfbY8zraHcXYHeiwP";
    private final String fourthPool = "3P4D2zZJubRPbFTurHpCNS9HbFaNiw6mf7D";
    private final String fifthPool = "3PPRh8DHaVTPqiv1Mes5amXq3Dujg7wSjZm";
    private AssetId swopId;

    private Account firstCaller, secondCaller, farming;
    private VotingDApp voting;
    private GovernanceDApp governance;

    @BeforeAll
    void before() {
        PrivateKey votingPK = PrivateKey.fromSeed(Crypto.getRandomSeedBytes());
        PrivateKey governancePK = PrivateKey.fromSeed(Crypto.getRandomSeedBytes());

        async(
                () -> {
                    firstCaller = new Account(WAVES.amount(1000));
                    swopId = firstCaller.issue(a -> a.quantity(Long.MAX_VALUE).name("SWOP").decimals(8)).tx().assetId();
                },
                () -> secondCaller = new Account(WAVES.amount(1000)),
                () -> farming = new Account(WAVES.amount(1000))
        );
        async(
                () -> voting = new VotingDApp(votingPK, WAVES.amount(100), governancePK.address()),
                () -> governance = new GovernanceDApp(governancePK, WAVES.amount(1000), farming.publicKey(), votingPK.address())
        );
        async(
                () -> firstCaller.transfer(governance, Long.MAX_VALUE, swopId),
                () -> farming.writeData(d -> d.string("SWOP_id", swopId.toString()))
        );
        governance.writeData(d -> d.data(
                IntegerEntry.as(firstPool + keyRewardPoolFractionCurrent, 10_000000000L),
                IntegerEntry.as(firstPool + keyRewardPoolFractionPrevious, 10_000000000L),
                IntegerEntry.as(secondPool + keyRewardPoolFractionCurrent, 20_000000000L),
                IntegerEntry.as(secondPool + keyRewardPoolFractionPrevious, 20_000000000L),
                IntegerEntry.as(thirdPool + keyRewardPoolFractionCurrent, 30_000000000L),
                IntegerEntry.as(thirdPool + keyRewardPoolFractionPrevious, 30_000000000L),
                IntegerEntry.as(fourthPool + keyRewardPoolFractionCurrent, 15_000000000L),
                IntegerEntry.as(fourthPool + keyRewardPoolFractionPrevious, 15_000000000L),
                IntegerEntry.as(fifthPool + keyRewardPoolFractionCurrent, 25_000000000L),
                IntegerEntry.as(fifthPool + keyRewardPoolFractionPrevious, 25_000000000L),
                IntegerEntry.as(keyRewardUpdateHeight, node().getHeight()),
                IntegerEntry.as(firstCaller.address().toString() + "_SWOP_amount", firstCallerInitAmount + 1),
                IntegerEntry.as(secondCaller.address().toString() + "_SWOP_amount", secondCallerInitAmount + 1)));
    }

    Stream<Arguments> voteProvider() {
        return Stream.of(
                Arguments.of(singletonList(firstPool), singletonList(1_00000000L), 1_00000000L),
                Arguments.of(singletonList(secondPool), singletonList(10_00000000L), 11_00000000L),
                Arguments.of(
                        asList(thirdPool, fourthPool, fifthPool),
                        asList(9_00000000L, 580_00000000L, 400_00000000L), 1000_00000000L));
    }

    @ParameterizedTest(name = "first caller vote")
    @MethodSource("voteProvider")
    void a_firstVote(List<String> poolAddresses, List<Long> poolsVoteSWOPNew, long expectedTotal) {
        firstCaller.invoke(voting.votePoolWeight(poolAddresses, poolsVoteSWOPNew));

        List<Long> resultUserVotes = poolAddresses.stream().map(pool ->
                voting.getIntegerData(firstCaller.address() + "_" + pool + kUserPoolVoteSWOP)).collect(toList());

        List<Long> resultPoolVotes = poolAddresses.stream().map(pool ->
                voting.getIntegerData(pool + kPoolVoteSWOP)).collect(toList());

        assertAll("vote pool weight",
                () -> assertThat(resultUserVotes).isEqualTo(poolsVoteSWOPNew),
                () -> assertThat(resultPoolVotes).isEqualTo(poolsVoteSWOPNew),
                () -> assertThat(voting.getIntegerData(firstCaller.address().toString() + kUserTotalVoteSWOP)).isEqualTo(expectedTotal),
                () -> assertThat(voting.getIntegerData(kTotalVoteSWOP)).isEqualTo(expectedTotal));
    }

    @ParameterizedTest(name = "second caller vote")
    @MethodSource("voteProvider")
    void b_secondVote(List<String> poolAddresses, List<Long> poolsVoteSWOPNew, long expectedTotal) {
        long totalVoteBefore = voting.getIntegerData(kTotalVoteSWOP);

        secondCaller.invoke(voting.votePoolWeight(poolAddresses, poolsVoteSWOPNew));

        List<Long> resultUserVotes = poolAddresses.stream().map(pool ->
                voting.getIntegerData(secondCaller.address() + "_" + pool + kUserPoolVoteSWOP)).collect(toList());

        List<Long> resultPoolVotes = poolAddresses.stream().map(pool ->
                voting.getIntegerData(pool + kPoolVoteSWOP)).collect(toList());

        List<Long> expectedPoolVotes = poolsVoteSWOPNew.stream().map(vote -> vote * 2).collect(toList());
        List<Long> expectedUserVotes = new ArrayList<>(poolsVoteSWOPNew);
        long sumVote = expectedUserVotes.stream().mapToLong(Long::longValue).sum();

        assertAll("vote pool weight",
                () -> assertThat(resultUserVotes).isEqualTo(expectedUserVotes),
                () -> assertThat(resultPoolVotes).isEqualTo(expectedPoolVotes),
                () -> assertThat(voting.getIntegerData(secondCaller.address().toString() + kUserTotalVoteSWOP)).isEqualTo(expectedTotal),
                () -> assertThat(voting.getIntegerData(kTotalVoteSWOP)).isEqualTo(totalVoteBefore + sumVote));
    }

    Stream<Arguments> unvoteProvider() {
        return Stream.of(
                Arguments.of(singletonList(firstPool), singletonList(0L), 999_00000000L),
                Arguments.of(singletonList(secondPool), singletonList(0L), 989_00000000L),
                Arguments.of(
                        asList(thirdPool, fourthPool, fifthPool),
                        asList(0L, 0L, 0L), 0L));
    }
    @ParameterizedTest(name = "second caller unvote")
    @MethodSource("unvoteProvider")
    void c_secondUnvote(List<String> poolAddresses, List<Long> poolsVoteSWOPNew, long expectedTotal) {
        secondCaller.invoke(voting.votePoolWeight(poolAddresses, poolsVoteSWOPNew));

        List<Long> resultUserVotes = poolAddresses.stream().map(pool ->
                voting.getIntegerData(secondCaller.address() + "_" + pool + kUserPoolVoteSWOP)).collect(toList());

        List<Long> expectedUserVotes = new ArrayList<>(poolsVoteSWOPNew);

        assertAll("vote pool weight",
                () -> assertThat(resultUserVotes).isEqualTo(expectedUserVotes),
                () -> assertThat(voting.getIntegerData(secondCaller.address().toString() + kUserTotalVoteSWOP)).isEqualTo(expectedTotal));
    }

    Stream<Arguments> changeVoteProvider() {
        return Stream.of(
                Arguments.of(singletonList(firstPool), singletonList(400_00000000L), 400_00000000L),
                Arguments.of(singletonList(secondPool), singletonList(580_00000000L), 980_00000000L),
                Arguments.of(
                        asList(thirdPool, fourthPool, fifthPool),
                        asList(9_00000000L, 10_00000000L, 1_00000000L), 1000_00000000L));
    }

    @ParameterizedTest(name = "second caller change vote")
    @MethodSource("changeVoteProvider")
    void d_secondChangeVote(List<String> poolAddresses, List<Long> poolsVoteSWOPNew, long expectedTotal) {
        secondCaller.invoke(voting.votePoolWeight(poolAddresses, poolsVoteSWOPNew));

        List<Long> resultUserVotes = poolAddresses.stream().map(pool ->
                voting.getIntegerData(secondCaller.address() + "_" + pool + kUserPoolVoteSWOP)).collect(toList());

        List<Long> expectedUserVotes = new ArrayList<>(poolsVoteSWOPNew);

        assertAll("vote pool weight",
                () -> assertThat(resultUserVotes).isEqualTo(expectedUserVotes),
                () -> assertThat(voting.getIntegerData(secondCaller.address().toString() + kUserTotalVoteSWOP)).isEqualTo(expectedTotal));
    }

    @Test
    void e_withdrawSWOP() {
        assertThat(assertThrows(ApiError.class, () ->
                firstCaller.invoke(governance.withdrawSWOP(2)))
        ).hasMessageContaining("withdrawAmount > availableFund");

        long userSWOPBalanceBefore = firstCaller.getAssetBalance(swopId);

        firstCaller.invoke(governance.withdrawSWOP(1));

        assertAll("state after first user lock check",
                () -> assertThat(governance.getIntegerData(firstCaller.address() + "_SWOP_amount")).isEqualTo(firstCallerInitAmount),
                () -> assertThat(firstCaller.getAssetBalance(swopId)).isEqualTo(userSWOPBalanceBefore + 1));
    }

    @Test
    void f_updateWeights() {
        long scaleValue8 = (long) Math.pow(10,8);
        List<String> previousPools = asList(firstPool, secondPool, thirdPool, fourthPool);
        List<Long> previousRewards = asList(10 * scaleValue8, 20 * scaleValue8, 30 * scaleValue8, 40 * scaleValue8);
        List<String> currentPools = asList(firstPool, secondPool, thirdPool, fourthPool, fifthPool);
        List<Long> currentRewards = asList(10 * scaleValue8, 20 * scaleValue8, 30 * scaleValue8, 15 * scaleValue8, 25 * scaleValue8);

        int rewardUpdateHeight = node().getHeight() + 3;
        farming.invoke(governance.updateWeights(previousPools, previousRewards, currentPools, currentRewards, rewardUpdateHeight));

        List<Long> resultPreviousRewards = previousPools.stream().map(pool ->
                governance.getIntegerData(pool + keyRewardPoolFractionPrevious)).collect(toList());

        List<Long> resultCurrentRewards = currentPools.stream().map(pool ->
                governance.getIntegerData(pool + keyRewardPoolFractionCurrent)).collect(toList());

        List<Long> expectedPreviousRewards = new ArrayList<>(previousRewards);
        List<Long> expectedCurrentRewards = new ArrayList<>(currentRewards);

        assertAll("update weights check",
                () -> assertThat(resultPreviousRewards).isEqualTo(expectedPreviousRewards),
                () -> assertThat(resultCurrentRewards).isEqualTo(expectedCurrentRewards),
                () -> assertThat(governance.getIntegerData(keyRewardUpdateHeight)).isEqualTo(rewardUpdateHeight));
    }

}
