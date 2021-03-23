package SWOP;

import im.mak.paddle.Account;
import im.mak.paddle.exceptions.ApiError;
import im.mak.paddle.exceptions.NodeError;
import im.mak.waves.transactions.common.AssetId;
import im.mak.waves.transactions.common.Id;
import im.mak.waves.transactions.data.DataEntry;
import im.mak.waves.transactions.data.IntegerEntry;
import im.mak.waves.transactions.invocation.Arg;
import im.mak.waves.transactions.invocation.IntegerArg;
import im.mak.waves.transactions.invocation.ListArg;
import im.mak.waves.transactions.invocation.StringArg;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private Account firstCaller = new Account(1000_00000000L);
    private Account secondCaller = new Account(1000_00000000L);
    private long firstCallerInitAmount = 1000_00000000L;
    private long secondCallerInitAmount = 1000_00000000L;
    private String keyRewardPoolFractionCurrent = "_current_pool_fraction_reward";
    private String keyRewardPoolFractionPrevious = "_previous_pool_fraction_reward";
    private String keyRewardUpdateHeight = "reward_update_height";
    private String kUserPoolVoteSWOP = "_vote";
    private String kUserTotalVoteSWOP = "_user_total_SWOP_vote";
    private String kPoolVoteSWOP = "_vote_SWOP";
    private String kTotalVoteSWOP = "total_vote_SWOP";
    private String firstPool = "3P5N94Qdb8SqJuy56p1btfzz1zACpPbqs6x";
    private String secondPool = "3PA26XNQfUzwNQHhSEbtKzRfYFvAcgj2Nfw";
    private String thirdPool = "3PLZSEaGDLht8GGK8rDfbY8zraHcXYHeiwP";
    private String fourthPool = "3P4D2zZJubRPbFTurHpCNS9HbFaNiw6mf7D";
    private String fifthPool = "3PPRh8DHaVTPqiv1Mes5amXq3Dujg7wSjZm";
    private Account farming = new Account(1000_00000000L);
    private AssetId swopId;
    private Account voting = new Account(100_00000000L);
    private Account governance = new Account(1000_00000000L);
    private String votingScript = StringUtils.substringBefore(
            fromFile("dApps/SWOP/voting.ride")
                    .replace("3PLHVWCqA9DJPDbadUofTohnCULLauiDWhS", governance.address().toString()),
            "@Verifier");

    private String governanceScript = StringUtils.substringBefore(
            fromFile("dApps/SWOP/governance.ride")
                    .replace("3PQZWxShKGRgBN1qoJw6B4s9YWS9FneZTPg", voting.address().toString())
                    .replace("3P73HDkPqG15nLXevjCbmXtazHYTZbpPoPw", farming.address().toString())
                    .replace("DXDY2itiEcYBtGkVLnkpHtDFyWQUkoLJz79uJ7ECbMrA", firstCaller.publicKey().toString()),
            "@Verifier");


    @BeforeAll
    void before() {
        async(
                () -> {
                    voting.setScript(s -> s.script(votingScript));
                },
                () -> {
                    governance.setScript(s -> s.script(governanceScript));
                },
                () -> {
                    governance.writeData(d -> d.data(IntegerEntry.as(firstPool + keyRewardPoolFractionCurrent, 10_000000000L)));
                    governance.writeData(d -> d.data(IntegerEntry.as(firstPool + keyRewardPoolFractionPrevious, 10_000000000L)));
                },
                () -> {
                    governance.writeData(d -> d.data(IntegerEntry.as(secondPool + keyRewardPoolFractionCurrent, 20_000000000L)));
                    governance.writeData(d -> d.data(IntegerEntry.as(secondPool + keyRewardPoolFractionPrevious, 20_000000000L)));
                },
                () -> {
                    governance.writeData(d -> d.data(IntegerEntry.as(thirdPool + keyRewardPoolFractionCurrent, 30_000000000L)));
                    governance.writeData(d -> d.data(IntegerEntry.as(thirdPool + keyRewardPoolFractionPrevious, 30_000000000L)));
                },
                () -> {
                    governance.writeData(d -> d.data(IntegerEntry.as(fourthPool + keyRewardPoolFractionCurrent, 15_000000000L)));
                    governance.writeData(d -> d.data(IntegerEntry.as(fourthPool + keyRewardPoolFractionPrevious, 15_000000000L)));
                },
                () -> {
                    governance.writeData(d -> d.data(IntegerEntry.as(fifthPool + keyRewardPoolFractionCurrent, 25_000000000L)));
                    governance.writeData(d -> d.data(IntegerEntry.as(fifthPool + keyRewardPoolFractionPrevious, 25_000000000L)));
                },
                () -> {
                    governance.writeData(d -> d.data(IntegerEntry.as("reward_update_height", node().getHeight())));
                },
                () -> {
                    governance.writeData(d -> d.data(IntegerEntry.as(firstCaller.address().toString() + "_SWOP_amount", firstCallerInitAmount + 1)));
                },
                () -> {
                    governance.writeData(d -> d.data(IntegerEntry.as(secondCaller.address().toString() + "_SWOP_amount", secondCallerInitAmount + 1)));
                },
                () -> {
                    swopId = firstCaller.issue(a -> a.quantity(Long.MAX_VALUE).name("SWOP").decimals(8)).tx().assetId();
                    node().waitForTransaction(swopId);
                    node().waitForTransaction(firstCaller.transfer(t -> t.amount(Long.MAX_VALUE, swopId).to(governance)).tx().id());
                    node().waitForTransaction(farming.writeData(d -> d.string("SWOP_id", swopId.toString())).tx().id());
                }
        );
    }

    Stream<Arguments> voteProvider() {
        return Stream.of(
                Arguments.of(poolAddresses(firstPool), poolsVoteSWOPNew(1_00000000L), 1_00000000L),
                Arguments.of(poolAddresses(secondPool), poolsVoteSWOPNew(10_00000000L), 11_00000000L),
                Arguments.of(
                        poolAddresses(thirdPool, fourthPool, fifthPool),
                        poolsVoteSWOPNew(9_00000000L, 580_00000000L, 400_00000000L), 1000_00000000L)
        );
    }

    @ParameterizedTest(name = "first caller vote")
    @MethodSource("voteProvider")
    void a_firstVote(List<StringArg> poolAddresses, List<IntegerArg> poolsVoteSWOPNew, long expectedTotal) {
        Id invokeId = firstCaller.invoke(i -> i.dApp(voting).function("votePoolWeight",
                ListArg.as(poolAddresses.toArray(new StringArg[poolAddresses.size()])),
                ListArg.as(poolsVoteSWOPNew.toArray(new IntegerArg[poolsVoteSWOPNew.size()]))).fee(1_00500000L)).tx().id();
        node().waitForTransaction(invokeId);

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
                () -> assertThat(voting.getIntegerData(kTotalVoteSWOP)).isEqualTo(expectedTotal)
        );
    }

    @ParameterizedTest(name = "second caller vote")
    @MethodSource("voteProvider")
    void b_secondVote(List<StringArg> poolAddresses, List<IntegerArg> poolsVoteSWOPNew, long expectedTotal) {
        long totalVoteBefore = voting.getIntegerData(kTotalVoteSWOP);
        Id invokeId = secondCaller.invoke(i -> i.dApp(voting).function("votePoolWeight",
                ListArg.as(poolAddresses.toArray(new StringArg[poolAddresses.size()])),
                ListArg.as(poolsVoteSWOPNew.toArray(new IntegerArg[poolsVoteSWOPNew.size()]))).fee(1_00500000L)).tx().id();
        node().waitForTransaction(invokeId);

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
        Long sumVote = expectedUserVotes.stream().collect(Collectors.summingLong(Long::longValue));

        assertAll("vote pool weight",
                () -> assertThat(resultUserVotes).isEqualTo(expectedUserVotes),
                () -> assertThat(resultPoolVotes).isEqualTo(expectedPoolVotes),
                () -> assertThat(voting.getIntegerData(secondCaller.address().toString() + kUserTotalVoteSWOP)).isEqualTo(expectedTotal),
                () -> assertThat(voting.getIntegerData(kTotalVoteSWOP)).isEqualTo(totalVoteBefore + sumVote)
        );
    }

    @Test
    void c_withdrawSWOP() {
        ApiError error = assertThrows(ApiError.class, () ->
                firstCaller.invoke(i -> i.dApp(governance)
                        .function("withdrawSWOP", IntegerArg.as(2))
                        .fee(500000L)));
        assertTrue(error.getMessage().contains("withdrawAmount > availableFund"));

        long userSWOPBalanceBefore = firstCaller.getAssetBalance(swopId);
        node().waitForTransaction(
                firstCaller.invoke(i -> i.dApp(governance)
                        .function("withdrawSWOP", IntegerArg.as(1))
                        .fee(500000L)).tx().id()
        );

        assertAll("state after first user lock check",
                () -> assertThat(governance.getIntegerData(firstCaller.address() + "_SWOP_amount")).isEqualTo(firstCallerInitAmount),
                () -> assertThat(firstCaller.getAssetBalance(swopId)).isEqualTo(userSWOPBalanceBefore + 1)
        );
    }

    @Test
    void d_updateWeights() {
        long scaleValue8 = (long) Math.pow(10,8);
        List<StringArg> previousPools = poolAddresses(firstPool, secondPool, thirdPool, fourthPool);
        List<IntegerArg> previousRewards = poolsVoteSWOPNew(10 * scaleValue8, 20 * scaleValue8, 30 * scaleValue8, 40 * scaleValue8);
        List<StringArg> currentPools = poolAddresses(firstPool, secondPool, thirdPool, fourthPool, fifthPool);
        List<IntegerArg> currentRewards = poolsVoteSWOPNew(10 * scaleValue8, 20 * scaleValue8, 30 * scaleValue8, 15 * scaleValue8, 25 * scaleValue8);

        long rewardUpdateHeight = node().getHeight() + 3;
        Id invokeId = firstCaller.invoke(i -> i.dApp(governance).function("updateWeights",
                ListArg.as(previousPools.toArray(new StringArg[previousPools.size()])),
                ListArg.as(previousRewards.toArray(new IntegerArg[previousRewards.size()])),
                ListArg.as(currentPools.toArray(new StringArg[currentPools.size()])),
                ListArg.as(currentRewards.toArray(new IntegerArg[currentRewards.size()])),
                IntegerArg.as(rewardUpdateHeight)).fee(1_00500000L)).tx().id();
        node().waitForTransaction(invokeId);

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
                () -> assertThat(governance.getIntegerData(keyRewardUpdateHeight)).isEqualTo(rewardUpdateHeight)
        );
    }

    private List<StringArg> poolAddresses(String... pools) {
        return Arrays.stream(pools).map(StringArg::new).collect(Collectors.toList());
    }

    private List<IntegerArg> poolsVoteSWOPNew(Long... votes) {
        return Arrays.stream(votes).map(IntegerArg::new).collect(Collectors.toList());
    }
}
