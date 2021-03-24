package SWOP;

import im.mak.paddle.Account;
import im.mak.paddle.exceptions.ApiError;
import com.wavesplatform.transactions.common.AssetId;
import com.wavesplatform.transactions.data.IntegerEntry;
import com.wavesplatform.transactions.invocation.IntegerArg;
import com.wavesplatform.transactions.invocation.ListArg;
import com.wavesplatform.transactions.invocation.StringArg;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static im.mak.paddle.Async.async;
import static im.mak.paddle.Node.node;
import static im.mak.paddle.util.Script.fromFile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.Alphanumeric.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class VotingLiteTest {
    private final Account firstCaller = new Account(1000_00000000L);
    private final Account secondCaller = new Account(1000_00000000L);
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
    private final Account farming = new Account(1000_00000000L);
    private AssetId swopId;
    private final Account voting = new Account(100_00000000L);
    private final Account governance = new Account(1000_00000000L);
    private final String votingScript = StringUtils.substringBefore(
            fromFile("dApps/SWOP/voting.ride")
                    .replace("3PLHVWCqA9DJPDbadUofTohnCULLauiDWhS", governance.address().toString()),
            "@Verifier");

    private final String governanceScript = StringUtils.substringBefore(
            fromFile("dApps/SWOP/governance.ride")
                    .replace("3PQZWxShKGRgBN1qoJw6B4s9YWS9FneZTPg", voting.address().toString())
                    .replace("3P73HDkPqG15nLXevjCbmXtazHYTZbpPoPw", farming.address().toString())
                    .replace("DXDY2itiEcYBtGkVLnkpHtDFyWQUkoLJz79uJ7ECbMrA", firstCaller.publicKey().toString()),
            "@Verifier");

    @BeforeAll
    void before() {
        async(
                () -> voting.setScript(s -> s.script(votingScript)),
                () -> governance.writeData(d -> d.data(
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
                        IntegerEntry.as(secondCaller.address().toString() + "_SWOP_amount", secondCallerInitAmount + 1))),
                () -> {
                    swopId = firstCaller.issue(a -> a.quantity(Long.MAX_VALUE).name("SWOP").decimals(8)).tx().assetId();
                    firstCaller.transfer(t -> t.amount(Long.MAX_VALUE, swopId).to(governance));
                    farming.writeData(d -> d.string("SWOP_id", swopId.toString()));
                });
        governance.setScript(s -> s.script(governanceScript));
    }

    Stream<Arguments> voteProvider() {
        return Stream.of(
                Arguments.of(poolAddresses(firstPool), poolsVoteSWOPNew(1_00000000L), 1_00000000L),
                Arguments.of(poolAddresses(secondPool), poolsVoteSWOPNew(10_00000000L), 11_00000000L),
                Arguments.of(
                        poolAddresses(thirdPool, fourthPool, fifthPool),
                        poolsVoteSWOPNew(9_00000000L, 580_00000000L, 400_00000000L), 1000_00000000L));
    }

    @ParameterizedTest(name = "first caller vote")
    @MethodSource("voteProvider")
    void a_firstVote(List<StringArg> poolAddresses, List<IntegerArg> poolsVoteSWOPNew, long expectedTotal) {
        firstCaller.invoke(i -> i.dApp(voting)
                .function("votePoolWeight",
                        ListArg.as(poolAddresses.toArray(new StringArg[0])),
                        ListArg.as(poolsVoteSWOPNew.toArray(new IntegerArg[0]))));

        List<Long> resultUserVotes = new ArrayList<>();
        for (StringArg pool : poolAddresses) {
            resultUserVotes.add(voting.getIntegerData(String.format("%s_%s%s", firstCaller.address().toString(), pool.value(), kUserPoolVoteSWOP)));
        }
        List<Long> resultPoolVotes = new ArrayList<>();
        for (StringArg pool : poolAddresses) {
            resultPoolVotes.add(voting.getIntegerData(pool.value() + kPoolVoteSWOP));
        }

        List<Long> expectedVotes = new ArrayList<>();
        for (IntegerArg vote : poolsVoteSWOPNew) {
            expectedVotes.add(vote.value());
        }
        assertAll("vote pool weight",
                () -> assertThat(resultUserVotes).isEqualTo(expectedVotes),
                () -> assertThat(resultPoolVotes).isEqualTo(expectedVotes),
                () -> assertThat(voting.getIntegerData(firstCaller.address().toString() + kUserTotalVoteSWOP)).isEqualTo(expectedTotal),
                () -> assertThat(voting.getIntegerData(kTotalVoteSWOP)).isEqualTo(expectedTotal));
    }

    @ParameterizedTest(name = "second caller vote")
    @MethodSource("voteProvider")
    void b_secondVote(List<StringArg> poolAddresses, List<IntegerArg> poolsVoteSWOPNew, long expectedTotal) {
        long totalVoteBefore = voting.getIntegerData(kTotalVoteSWOP);
        secondCaller.invoke(i -> i.dApp(voting)
                .function("votePoolWeight",
                        ListArg.as(poolAddresses.toArray(new StringArg[0])),
                        ListArg.as(poolsVoteSWOPNew.toArray(new IntegerArg[0]))));

        List<Long> resultUserVotes = new ArrayList<>();
        for (StringArg pool : poolAddresses) {
            resultUserVotes.add(voting.getIntegerData(String.format("%s_%s%s", secondCaller.address().toString(), pool.value(), kUserPoolVoteSWOP)));
        }
        List<Long> resultPoolVotes = new ArrayList<>();
        for (StringArg pool : poolAddresses) {
            resultPoolVotes.add(voting.getIntegerData(pool.value() + kPoolVoteSWOP));
        }

        List<Long> expectedPoolVotes = new ArrayList<>();
        for (IntegerArg vote : poolsVoteSWOPNew) {
            expectedPoolVotes.add(vote.value() * 2);
        }

        List<Long> expectedUserVotes = new ArrayList<>();
        for (IntegerArg vote : poolsVoteSWOPNew) {
            expectedUserVotes.add(vote.value());
        }
        long sumVote = expectedUserVotes.stream().mapToLong(Long::longValue).sum();

        assertAll("vote pool weight",
                () -> assertThat(resultUserVotes).isEqualTo(expectedUserVotes),
                () -> assertThat(resultPoolVotes).isEqualTo(expectedPoolVotes),
                () -> assertThat(voting.getIntegerData(secondCaller.address().toString() + kUserTotalVoteSWOP)).isEqualTo(expectedTotal),
                () -> assertThat(voting.getIntegerData(kTotalVoteSWOP)).isEqualTo(totalVoteBefore + sumVote));
    }

    Stream<Arguments> unvoteProvider() {
        return Stream.of(
                Arguments.of(poolAddresses(firstPool), poolsVoteSWOPNew(0L), 999_00000000L),
                Arguments.of(poolAddresses(secondPool), poolsVoteSWOPNew(0L), 989_00000000L),
                Arguments.of(
                        poolAddresses(thirdPool, fourthPool, fifthPool),
                        poolsVoteSWOPNew(0L, 0L, 0L), 0L));
    }
    @ParameterizedTest(name = "second caller unvote")
    @MethodSource("unvoteProvider")
    void c_secondUnvote(List<StringArg> poolAddresses, List<IntegerArg> poolsVoteSWOPNew, long expectedTotal) {
        secondCaller.invoke(i -> i.dApp(voting).function("votePoolWeight",
                ListArg.as(poolAddresses.toArray(new StringArg[poolAddresses.size()])),
                ListArg.as(poolsVoteSWOPNew.toArray(new IntegerArg[poolsVoteSWOPNew.size()]))));

        List<Long> resultUserVotes = new ArrayList<>();
        for (StringArg pool : poolAddresses) {
            resultUserVotes.add(voting.getIntegerData(String.format("%s_%s%s", secondCaller.address().toString(), pool.value(), kUserPoolVoteSWOP)));
        }

        List<Long> expectedUserVotes = new ArrayList<>();
        for (IntegerArg vote : poolsVoteSWOPNew) {
            expectedUserVotes.add(vote.value());
        }

        assertAll("vote pool weight",
                () -> assertThat(resultUserVotes).isEqualTo(expectedUserVotes),
                () -> assertThat(voting.getIntegerData(secondCaller.address().toString() + kUserTotalVoteSWOP)).isEqualTo(expectedTotal));
    }

    Stream<Arguments> changeVoteProvider() {
        return Stream.of(
                Arguments.of(poolAddresses(firstPool), poolsVoteSWOPNew(400_00000000L), 400_00000000L),
                Arguments.of(poolAddresses(secondPool), poolsVoteSWOPNew(580_00000000L), 980_00000000L),
                Arguments.of(
                        poolAddresses(thirdPool, fourthPool, fifthPool),
                        poolsVoteSWOPNew(9_00000000L, 10_00000000L, 1_00000000L), 1000_00000000L));
    }
    @ParameterizedTest(name = "second caller change vote")
    @MethodSource("changeVoteProvider")
    void d_secondChangeVote(List<StringArg> poolAddresses, List<IntegerArg> poolsVoteSWOPNew, long expectedTotal) {
        secondCaller.invoke(i -> i.dApp(voting).function("votePoolWeight",
                ListArg.as(poolAddresses.toArray(new StringArg[poolAddresses.size()])),
                ListArg.as(poolsVoteSWOPNew.toArray(new IntegerArg[poolsVoteSWOPNew.size()]))));

        List<Long> resultUserVotes = new ArrayList<>();
        for (StringArg pool : poolAddresses) {
            resultUserVotes.add(voting.getIntegerData(String.format("%s_%s%s", secondCaller.address().toString(), pool.value(), kUserPoolVoteSWOP)));
        }

        List<Long> expectedUserVotes = new ArrayList<>();
        for (IntegerArg vote : poolsVoteSWOPNew) {
            expectedUserVotes.add(vote.value());
        }

        assertAll("vote pool weight",
                () -> assertThat(resultUserVotes).isEqualTo(expectedUserVotes),
                () -> assertThat(voting.getIntegerData(secondCaller.address().toString() + kUserTotalVoteSWOP)).isEqualTo(expectedTotal));
    }

    @Test
    void e_withdrawSWOP() {
        assertThat(assertThrows(ApiError.class, () ->
                firstCaller.invoke(i -> i.dApp(governance).function("withdrawSWOP", IntegerArg.as(2))))
        ).hasMessageContaining("withdrawAmount > availableFund");

        long userSWOPBalanceBefore = firstCaller.getAssetBalance(swopId);
        firstCaller.invoke(i -> i.dApp(governance).function("withdrawSWOP", IntegerArg.as(1)));

        assertAll("state after first user lock check",
                () -> assertThat(governance.getIntegerData(firstCaller.address() + "_SWOP_amount")).isEqualTo(firstCallerInitAmount),
                () -> assertThat(firstCaller.getAssetBalance(swopId)).isEqualTo(userSWOPBalanceBefore + 1));
    }

    @Test
    void f_updateWeights() {
        long scaleValue8 = (long) Math.pow(10,8);
        List<StringArg> previousPools = poolAddresses(firstPool, secondPool, thirdPool, fourthPool);
        List<IntegerArg> previousRewards = poolsVoteSWOPNew(10 * scaleValue8, 20 * scaleValue8, 30 * scaleValue8, 40 * scaleValue8);
        List<StringArg> currentPools = poolAddresses(firstPool, secondPool, thirdPool, fourthPool, fifthPool);
        List<IntegerArg> currentRewards = poolsVoteSWOPNew(10 * scaleValue8, 20 * scaleValue8, 30 * scaleValue8, 15 * scaleValue8, 25 * scaleValue8);

        long rewardUpdateHeight = node().getHeight() + 3;
        firstCaller.invoke(i -> i.dApp(governance).function("updateWeights",
                ListArg.as(previousPools.toArray(new StringArg[0])),
                ListArg.as(previousRewards.toArray(new IntegerArg[0])),
                ListArg.as(currentPools.toArray(new StringArg[0])),
                ListArg.as(currentRewards.toArray(new IntegerArg[0])),
                IntegerArg.as(rewardUpdateHeight)));

        List<Long> resultPreviousRewards = new ArrayList<>();
        for (StringArg pool : previousPools) {
            resultPreviousRewards.add(governance.getIntegerData(pool.value() + keyRewardPoolFractionPrevious));
        }

        List<Long> resultCurrentRewards = new ArrayList<>();
        for (StringArg pool : currentPools) {
            resultCurrentRewards.add(governance.getIntegerData(pool.value() + keyRewardPoolFractionCurrent));
        }

        List<Long> expectedPreviousRewards = new ArrayList<>();
        for (IntegerArg reward : previousRewards) {
            expectedPreviousRewards.add(reward.value());
        }

        List<Long> expectedCurrentRewards = new ArrayList<>();
        for (IntegerArg reward : currentRewards) {
            expectedCurrentRewards.add(reward.value());
        }

        assertAll("update weights check",
                () -> assertThat(resultPreviousRewards).isEqualTo(expectedPreviousRewards),
                () -> assertThat(resultCurrentRewards).isEqualTo(expectedCurrentRewards),
                () -> assertThat(governance.getIntegerData(keyRewardUpdateHeight)).isEqualTo(rewardUpdateHeight));
    }

    private List<StringArg> poolAddresses(String... pools) {
        return Arrays.stream(pools).map(StringArg::new).collect(Collectors.toList());
    }

    private List<IntegerArg> poolsVoteSWOPNew(Long... votes) {
        return Arrays.stream(votes).map(IntegerArg::new).collect(Collectors.toList());
    }
}
