package SWOP;

import im.mak.paddle.Account;
import im.mak.paddle.exceptions.ApiError;
import im.mak.paddle.exceptions.NodeError;
import im.mak.waves.crypto.base.Base58;
import im.mak.waves.transactions.IssueTransaction;
import im.mak.waves.transactions.common.Amount;
import im.mak.waves.transactions.common.AssetId;
import im.mak.waves.transactions.common.Id;
import im.mak.waves.transactions.invocation.IntegerArg;
import im.mak.waves.transactions.invocation.StringArg;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.BufferedReader;
import java.io.FileReader;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import static im.mak.paddle.Async.async;
import static im.mak.paddle.Node.node;
import static im.mak.paddle.util.Script.fromFile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.shouldHaveThrown;
import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.Alphanumeric.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SWOPFarmingTest {
    private AssetId swopId;
    private AssetId shareAssetId1;
    private AssetId shareAssetId2;
    private int startFarmingHeight;
    private int shareAssetDecimals = 6;
    private int preLockShareAmount = 100000;
    private int preWithdrawAmount = 1000;
    private long initSWOPAmount = 100000000000000L;
    long firstReward = 5000000000L;
    long secondReward = 5000000000L;
    int previousReward = 0;
    long scaleValue = (long) Math.pow(10, shareAssetDecimals);
    private String keyShareTokensLocked = "_total_share_tokens_locked";
    private String keyRewardPoolFractionCurrent = "%s_current_pool_fraction_reward";
    private String keyRewardPoolFractionPrevious = "%s_previous_pool_fraction_reward";
    private String keyTotalRewardPerBlockCurrent = "total_reward_per_block_current";
    private String keyTotalRewardPerBlockPrevious = "total_reward_per_block_previous";
    private Long totalRewardPerBlockCurrent = 189751395L;
    private Long totalRewardPerBlockPrevious = 189751395L;
    private Long totalVoteShare = 10000000000L;
    private String keyRewardUpdateHeight = "reward_update_height";
    private String keyLastInterest = "_last_interest";
    private String keyLastInterestHeight = "_last_interest_height";
    private String keyUserShareTokensLocked = "_share_tokens_locked";
    private String keyUserLastInterest = "_last_interest";
    private String keyUserSWOPClaimedAmount = "_SWOP_claimed_amount";
    private String keyUserSWOPLastClaimedAmount = "_SWOP_last_claimed_amount";
    private String keyAvailableSWOP = "_available_SWOP";
    private String keyFarmingStartHeight = "farming_start_height";
    private Account pool1 = new Account(1000_00000000L);
    private Account pool2 = new Account(1000_00000000L);
    private Account firstCaller = new Account(1000_00000000L);
    private Account secondCaller = new Account(1000_00000000L);
    private Account votingDapp = new Account(1000_00000000L);
    private Account farmingDapp = new Account(1000_00000000L);
    private Account earlyLP = new Account(1_00000000L);
    private String dAppScript = StringUtils.substringBefore(
            fromFile("dApps/SWOP/farming.ride")
            .replace("3PLHVWCqA9DJPDbadUofTohnCULLauiDWhS", votingDapp.address().toString())
            .replace("oneWeekInBlock = 10106", "oneWeekInBlock = 10")
            .replace("DXDY2itiEcYBtGkVLnkpHtDFyWQUkoLJz79uJ7ECbMrA", Base58.encode(farmingDapp.publicKey().bytes()))
            .replace("E6Wa1SGoktYcjHjsKrvjMiqJY3SWmGKcD8Q5L8kxSPS7", Base58.encode(farmingDapp.publicKey().bytes()))
            .replace("AZmWJtuy4GeVrMmJH4hfFBRApe1StvhJSk4jcbT6bArQ", Base58.encode(farmingDapp.publicKey().bytes()))
            .replace("EtVkT6ed8GtbUiVVEqdmEqsp2J4qbb3rre2HFgxeVYdg", Base58.encode(farmingDapp.publicKey().bytes()))
            .replace("Czn4yoAuUZCVCLJDRfskn8URfkwpknwBTZDbs1wFrY7h", Base58.encode(farmingDapp.publicKey().bytes()))
            .replace("if i.caller != this then", "")
            .replace("throw(\"Only the DApp itself can call this function\") else", ""),
            "@Verifier");

    @BeforeAll
    void before() {
        async(
                () -> {
                    Id setScriptId = farmingDapp.setScript(s -> s.script(dAppScript)).tx().id();
                    node().waitForTransaction(setScriptId);
                },
                () -> {
                    shareAssetId1 = firstCaller.issue(a -> a.quantity(Long.MAX_VALUE).name("sBTC_WAVES").decimals(shareAssetDecimals)).tx().assetId();
                    node().waitForTransaction(shareAssetId1);
                    node().waitForTransaction(firstCaller.transfer(t -> t.amount(Amount.of(Long.MAX_VALUE / 2, shareAssetId1)).to(secondCaller)).tx().id());
                    node().waitForTransaction(pool1.writeData(d -> d.string("share_asset_id", shareAssetId1.toString())).tx().id());
                },
                () -> {
                    shareAssetId2 = firstCaller.issue(a -> a.quantity(Long.MAX_VALUE).name("sUSDT_USDN").decimals(shareAssetDecimals)).tx().assetId();
                    node().waitForTransaction(shareAssetId2);
                    node().waitForTransaction(firstCaller.transfer(t -> t.amount(Amount.of(Long.MAX_VALUE / 2, shareAssetId2)).to(secondCaller)).tx().id());
                    node().waitForTransaction(pool2.writeData(d -> d.string("share_asset_id", shareAssetId2.toString())).tx().id());
                }
        );
    }


    @Test
    void a_init() {
        node().waitForTransaction(
                firstCaller.invoke(i -> i.dApp(farmingDapp).function("init", StringArg.as(earlyLP.address().toString())).fee(1_00500000L)).tx().id()
        );
        swopId = AssetId.as(farmingDapp.getStringData("SWOP_id"));
        assertThat(farmingDapp.getAssetBalance(swopId)).isEqualTo(initSWOPAmount);
    }

    Stream<Arguments> poolProvider() {
        return Stream.of(
                Arguments.of(pool1),
                Arguments.of(pool2)
        );
    }
    @ParameterizedTest(name = "init pool share farming")
    @MethodSource("poolProvider")
    void b_initPoolShareFarming(Account pool) {
        int rewardUpdateHeight = node().getHeight();
        String poolAddress = pool.address().toString();
        System.out.println(poolAddress);

        node().waitForTransaction(votingDapp.writeData(d -> d.integer(String.format(keyRewardPoolFractionCurrent, poolAddress), firstReward)).tx().id());
        node().waitForTransaction(votingDapp.writeData(d -> d.integer(String.format(keyRewardPoolFractionPrevious, poolAddress), previousReward)).tx().id());
        node().waitForTransaction(votingDapp.writeData(d -> d.integer(keyRewardUpdateHeight, rewardUpdateHeight)).tx().id());
        node().waitForTransaction(votingDapp.writeData(d -> d.integer(keyTotalRewardPerBlockCurrent, totalRewardPerBlockCurrent)).tx().id());
        node().waitForTransaction(votingDapp.writeData(d -> d.integer(keyTotalRewardPerBlockPrevious, totalRewardPerBlockPrevious)).tx().id());

        node().waitForTransaction(
                firstCaller.invoke(i -> i.dApp(farmingDapp).function("initPoolShareFarming", StringArg.as(poolAddress)).fee(500000L)).tx().id()
        );
        assertAll("state after init check",
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_total_share_tokens_locked", poolAddress))).isEqualTo(0),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_last_interest", poolAddress))).isEqualTo(0)
        );
    }
    @Test
    void c_preLock() {
        String poolAddress = pool1.address().toString();
        startFarmingHeight = node().getHeight() + 10;
        farmingDapp.writeData(d -> d.integer("farming_start_height", startFarmingHeight));

        node().waitForTransaction(
                firstCaller.invoke(i -> i.dApp(farmingDapp).function("lockShareTokens", StringArg.as(poolAddress)).payment(preLockShareAmount, shareAssetId1).fee(500000L)).tx().id()
        );
        node().waitForTransaction(
                secondCaller.invoke(i -> i.dApp(farmingDapp).function("lockShareTokens", StringArg.as(poolAddress)).payment(preLockShareAmount, shareAssetId1).fee(500000L)).tx().id()
        );

        assertAll("state after first user lock before farming",
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_last_interest", poolAddress))).isEqualTo(0),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_%s_last_interest", poolAddress, firstCaller.address()))).isEqualTo(0),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_%s_last_interest", poolAddress, secondCaller.address()))).isEqualTo(0),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_%s_share_tokens_locked", poolAddress, firstCaller.address()))).isEqualTo(preLockShareAmount),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_%s_share_tokens_locked", poolAddress, secondCaller.address()))).isEqualTo(preLockShareAmount),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_total_share_tokens_locked", poolAddress))).isEqualTo(2 * preLockShareAmount),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_%s_available_SWOP", poolAddress, firstCaller.address()))).isEqualTo(0),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_%s_available_SWOP", poolAddress, secondCaller.address()))).isEqualTo(0)
        );

    }
    @Test
    void d_preWithdraw() {
        String poolAddress = pool1.address().toString();
        node().waitForTransaction(
                firstCaller.invoke(i -> i.dApp(farmingDapp).function("withdrawShareTokens", StringArg.as(poolAddress), IntegerArg.as(preWithdrawAmount)).fee(500000L)).tx().id()
        );
        node().waitForTransaction(
                secondCaller.invoke(i -> i.dApp(farmingDapp).function("withdrawShareTokens", StringArg.as(poolAddress), IntegerArg.as(preWithdrawAmount)).fee(500000L)).tx().id()
        );

//        node().waitNBlocks(1);
        assertAll("state after first user lock before farming",
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_last_interest", poolAddress))).isEqualTo(0),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_%s_last_interest", poolAddress, firstCaller.address()))).isEqualTo(0),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_%s_last_interest", poolAddress, secondCaller.address()))).isEqualTo(0),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_%s_share_tokens_locked", poolAddress, firstCaller.address()))).isEqualTo(preLockShareAmount - preWithdrawAmount),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_%s_share_tokens_locked", poolAddress, secondCaller.address()))).isEqualTo(preLockShareAmount - preWithdrawAmount),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_total_share_tokens_locked", poolAddress))).isEqualTo(2 * preLockShareAmount - 2 * preWithdrawAmount),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_%s_available_SWOP", poolAddress, firstCaller.address()))).isEqualTo(0),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_%s_available_SWOP", poolAddress, secondCaller.address()))).isEqualTo(0)
        );
    }
    @Test
    void e_methodsNotAllowedBeforeFarming() {
        NodeError error = assertThrows(NodeError.class, () ->
                firstCaller.invoke(i -> i.dApp(farmingDapp).function("claim", StringArg.as(pool1.address().toString())).payment(100, shareAssetId1).fee(500000L)));
        assertTrue(error.getMessage().contains("This method will be active after " + startFarmingHeight + " height"));
    }

    Stream<Arguments> lockShareProvider() {
        return Stream.of(
                Arguments.of(pool1, 1),
                Arguments.of(pool2, 1),
                Arguments.of(pool1, ThreadLocalRandom.current().nextLong(100L, 1000L)),
                Arguments.of(pool2, ThreadLocalRandom.current().nextLong(100L, 1000L)),
                Arguments.of(pool1, ThreadLocalRandom.current().nextLong(1000L, 100000L)),
                Arguments.of(pool2, ThreadLocalRandom.current().nextLong(1000L, 100000L)),
                Arguments.of(pool1, ThreadLocalRandom.current().nextLong(100000L, 100000000L)),
                Arguments.of(pool2, ThreadLocalRandom.current().nextLong(100000L, 100000000L)),
                Arguments.of(pool1, ThreadLocalRandom.current().nextLong(100000000L, 100000000000L)),
                Arguments.of(pool2, ThreadLocalRandom.current().nextLong(100000000L, 100000000000L)),
                Arguments.of(pool1, ThreadLocalRandom.current().nextLong(100000000000L, 100000000000000L)),
                Arguments.of(pool2, ThreadLocalRandom.current().nextLong(100000000000L, 100000000000000L))
        );
    }

    @ParameterizedTest(name = "lock {1} share tokens")
    @MethodSource("lockShareProvider")
    void f_lockShareTokens(Account pool, long lockShareAmount) {
        String poolAddress = pool.address().toString();
        AssetId shareAssetId;
        if (pool == pool1) {
            shareAssetId = shareAssetId1;
        } else {
            shareAssetId = shareAssetId2;
        }
        node().waitForHeight(startFarmingHeight);
        node().waitForTransaction(
                firstCaller.invoke(i -> i.dApp(farmingDapp).function("lockShareTokens", StringArg.as(poolAddress)).payment(1, shareAssetId).fee(500000L)).tx().id()
        );
        long sTokensLockedBeforeFirst = farmingDapp.getIntegerData(String.format("%s_total_share_tokens_locked", poolAddress));
        long user1ShareTokensLocked = farmingDapp.getIntegerData(String.format("%s_%s_share_tokens_locked", poolAddress, firstCaller.address()));
        long user1AvailableSWOP = farmingDapp.getIntegerData(String.format("%s_%s_available_SWOP", poolAddress, firstCaller.address()));
        long lastInterest = farmingDapp.getIntegerData(String.format("%s_last_interest", poolAddress));
        long user1NewInterest = calcInterest(
                farmingDapp.getIntegerData(String.format("%s_last_interest_height", poolAddress)),
                node().getHeight(),
                votingDapp.getIntegerData(keyRewardUpdateHeight),
                lastInterest,
                votingDapp.getIntegerData(String.format(keyRewardPoolFractionCurrent, poolAddress)),
                farmingDapp.getIntegerData(String.format("%s_total_share_tokens_locked", poolAddress)),
                votingDapp.getIntegerData(String.format(keyRewardPoolFractionPrevious, poolAddress)),
                scaleValue
        );
        long claimAmount1 = BigInteger.valueOf(user1ShareTokensLocked).multiply(BigInteger.valueOf(user1NewInterest - lastInterest)).divide(BigInteger.valueOf(scaleValue)).longValue();
        node().waitForTransaction(
                firstCaller.invoke(i -> i.dApp(farmingDapp).function("lockShareTokens", StringArg.as(poolAddress)).payment(lockShareAmount, shareAssetId).fee(500000L)).tx().id()
        );

        assertAll("state after first user lock check",
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_last_interest", poolAddress))).isEqualTo(user1NewInterest),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_%s_last_interest", poolAddress, firstCaller.address()))).isEqualTo(user1NewInterest),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_%s_share_tokens_locked", poolAddress, firstCaller.address()))).isEqualTo(user1ShareTokensLocked + lockShareAmount),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_total_share_tokens_locked", poolAddress))).isEqualTo(sTokensLockedBeforeFirst + lockShareAmount),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_%s_available_SWOP", poolAddress, firstCaller.address()))).isEqualTo(user1AvailableSWOP + claimAmount1)
        );

        long sTokensLockedBeforeSecond = farmingDapp.getIntegerData(String.format("%s_total_share_tokens_locked", poolAddress));
        long user2NewInterest = calcInterest(
                farmingDapp.getIntegerData(String.format("%s_last_interest_height", poolAddress)),
                node().getHeight(),
                votingDapp.getIntegerData(keyRewardUpdateHeight),
                farmingDapp.getIntegerData(String.format("%s_last_interest", poolAddress)),
                votingDapp.getIntegerData(String.format(keyRewardPoolFractionCurrent, poolAddress)),
                farmingDapp.getIntegerData(String.format("%s_total_share_tokens_locked", poolAddress)),
                votingDapp.getIntegerData(String.format(keyRewardPoolFractionPrevious, poolAddress)),
                scaleValue
        );
        long user2ShareTokensLocked;
        if (lockShareAmount == 1) {
            user2ShareTokensLocked = 0;
        } else {
            user2ShareTokensLocked = farmingDapp.getIntegerData(String.format("%s_%s_share_tokens_locked", poolAddress, secondCaller.address()));
        }
        node().waitForTransaction(
                secondCaller.invoke(i -> i.dApp(farmingDapp).function("lockShareTokens", StringArg.as(poolAddress)).payment(lockShareAmount, shareAssetId).fee(500000L)).tx().id()
        );
        node().waitNBlocks(1);

        assertAll("state after second user lock check",
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_last_interest", poolAddress))).isEqualTo(user2NewInterest),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_%s_last_interest", poolAddress, secondCaller.address()))).isEqualTo(user2NewInterest),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_%s_share_tokens_locked", poolAddress, secondCaller.address()))).isEqualTo(user2ShareTokensLocked + lockShareAmount),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_total_share_tokens_locked", poolAddress))).isEqualTo(sTokensLockedBeforeSecond + lockShareAmount)
        );

    }

    Stream<Arguments> withdrawShareProvider() {
        return Stream.of(
                Arguments.of(pool1, 1),
                Arguments.of(pool2, 1),
                Arguments.of(pool1, ThreadLocalRandom.current().nextLong(100L, 1000L)),
                Arguments.of(pool2, ThreadLocalRandom.current().nextLong(100L, 1000L)),
                Arguments.of(pool1, ThreadLocalRandom.current().nextLong(1000L, 100000L)),
                Arguments.of(pool2, ThreadLocalRandom.current().nextLong(1000L, 100000L)),
                Arguments.of(pool1, ThreadLocalRandom.current().nextLong(100000L, 100000000L)),
                Arguments.of(pool1, ThreadLocalRandom.current().nextLong(100000L, 100000000L)),
                Arguments.of(pool1, ThreadLocalRandom.current().nextLong(100000000L, 100000000000L)),
                Arguments.of(pool2, ThreadLocalRandom.current().nextLong(100000000L, 100000000000L))
        );
    }
    @ParameterizedTest(name = "withdraw {1} share tokens")
    @MethodSource("withdrawShareProvider")
    void h_withdrawShareTokens(Account pool, long withdrawShareAmount) {
        String poolAddress = pool.address().toString();
        AssetId shareAssetId;
        if (pool == pool1) {
            shareAssetId = shareAssetId1;
            if (withdrawShareAmount == 1) {
                int blockDuration = 2;
                node().waitNBlocks(blockDuration);
            }
        } else {
            shareAssetId = shareAssetId2;
        }
        long sTokensLockedBeforeFirst = farmingDapp.getIntegerData(String.format("%s_total_share_tokens_locked", poolAddress));
        long user1ShareTokensLocked = farmingDapp.getIntegerData(String.format("%s_%s_share_tokens_locked", poolAddress, firstCaller.address()));
        long user1NewInterest = calcInterest(
                farmingDapp.getIntegerData(String.format("%s_last_interest_height", poolAddress)),
                node().getHeight(),
                votingDapp.getIntegerData(keyRewardUpdateHeight),
                farmingDapp.getIntegerData(String.format("%s_last_interest", poolAddress)),
                votingDapp.getIntegerData(String.format(keyRewardPoolFractionCurrent, poolAddress)),
                farmingDapp.getIntegerData(String.format("%s_total_share_tokens_locked", poolAddress)),
                votingDapp.getIntegerData(String.format(keyRewardPoolFractionPrevious, poolAddress)),
                scaleValue
        );
        node().waitForTransaction(
                firstCaller.invoke(i -> i.dApp(farmingDapp).function("withdrawShareTokens", StringArg.as(poolAddress), IntegerArg.as(withdrawShareAmount)).payment(withdrawShareAmount, shareAssetId).fee(500000L)).tx().id()
        );

        assertAll("state after first user lock check",
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_last_interest", poolAddress))).isEqualTo(user1NewInterest),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_%s_last_interest", poolAddress, firstCaller.address()))).isEqualTo(user1NewInterest),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_%s_share_tokens_locked", poolAddress, firstCaller.address()))).isEqualTo(user1ShareTokensLocked - withdrawShareAmount),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_total_share_tokens_locked", poolAddress))).isEqualTo(sTokensLockedBeforeFirst - withdrawShareAmount)
        );

        long sTokensLockedBeforeSecond = farmingDapp.getIntegerData(String.format("%s_total_share_tokens_locked", poolAddress));
        long user2NewInterest = calcInterest(
                farmingDapp.getIntegerData(String.format("%s_last_interest_height", poolAddress)),
                node().getHeight(),
                votingDapp.getIntegerData(keyRewardUpdateHeight),
                farmingDapp.getIntegerData(String.format("%s_last_interest", poolAddress)),
                votingDapp.getIntegerData(String.format(keyRewardPoolFractionCurrent, poolAddress)),
                farmingDapp.getIntegerData(String.format("%s_total_share_tokens_locked", poolAddress)),
                votingDapp.getIntegerData(String.format(keyRewardPoolFractionPrevious, poolAddress)),
                scaleValue
        );
        long user2ShareTokensLocked = farmingDapp.getIntegerData(String.format("%s_%s_share_tokens_locked", poolAddress, secondCaller.address()));
        node().waitForTransaction(
                secondCaller.invoke(i -> i.dApp(farmingDapp).function("withdrawShareTokens", StringArg.as(poolAddress), IntegerArg.as(withdrawShareAmount)).payment(withdrawShareAmount, shareAssetId).fee(500000L)).tx().id()
        );
//        node().waitNBlocks(1);

        assertAll("state after second user lock check",
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_last_interest", poolAddress))).isEqualTo(user2NewInterest),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_%s_last_interest", poolAddress, secondCaller.address()))).isEqualTo(user2NewInterest),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_%s_share_tokens_locked", poolAddress, secondCaller.address()))).isEqualTo(user2ShareTokensLocked - withdrawShareAmount),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_total_share_tokens_locked", poolAddress))).isEqualTo(sTokensLockedBeforeSecond - withdrawShareAmount)
        );
    }

    @ParameterizedTest(name = "claim SWOP")
    @MethodSource("poolProvider")
    void i_claimSWOP(Account pool) {
        long firstSWOPBalanceBefore = firstCaller.getAssetBalance(swopId);
        long secondSWOPBalanceBefore = secondCaller.getAssetBalance(swopId);
        String poolAddress = pool.address().toString();
        long user1AvailableSWOP = farmingDapp.getIntegerData(poolAddress + "_" + firstCaller.address() + keyAvailableSWOP);
        long sTokensLockedBefore = farmingDapp.getIntegerData(poolAddress + keyShareTokensLocked);
        long user1ShareTokensLocked = farmingDapp.getIntegerData(poolAddress + "_" + firstCaller.address() + keyUserShareTokensLocked);
        long lastInterest1 = farmingDapp.getIntegerData(poolAddress + keyLastInterest);
        int currentHeight1 = node().getHeight();
        long user1NewInterest = calcInterest(
                farmingDapp.getIntegerData(String.format("%s_last_interest_height", poolAddress)),
                currentHeight1,
                votingDapp.getIntegerData(keyRewardUpdateHeight),
                farmingDapp.getIntegerData(String.format("%s_last_interest", poolAddress)),
                votingDapp.getIntegerData(String.format(keyRewardPoolFractionCurrent, poolAddress)),
                farmingDapp.getIntegerData(String.format("%s_total_share_tokens_locked", poolAddress)),
                votingDapp.getIntegerData(String.format(keyRewardPoolFractionPrevious, poolAddress)),
                scaleValue
        );
        long claimAmount1 = BigInteger.valueOf(user1ShareTokensLocked).multiply(BigInteger.valueOf(user1NewInterest - lastInterest1)).divide(BigInteger.valueOf(scaleValue)).longValue();

        node().waitForTransaction(
                firstCaller.invoke(i -> i.dApp(farmingDapp).function("claim", StringArg.as(poolAddress)).fee(500000L)).tx().id()
        );
        assertAll("state after first user claim check",
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_last_interest", poolAddress))).isEqualTo(user1NewInterest),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_%s_last_interest", poolAddress, firstCaller.address()))).isEqualTo(user1NewInterest),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_%s_share_tokens_locked", poolAddress, firstCaller.address()))).isEqualTo(user1ShareTokensLocked),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_total_share_tokens_locked", poolAddress))).isEqualTo(sTokensLockedBefore),
                () -> assertThat(farmingDapp.getIntegerData(poolAddress + "_" + firstCaller.address() + keyAvailableSWOP)).isEqualTo(0),
                () -> assertThat(farmingDapp.getIntegerData(poolAddress + keyLastInterestHeight)).isEqualTo(currentHeight1),
                () -> assertThat(firstCaller.getAssetBalance(swopId)).isEqualTo(firstSWOPBalanceBefore + user1AvailableSWOP + claimAmount1)
        );

        long user2AvailableSWOP = farmingDapp.getIntegerData(poolAddress + "_" + secondCaller.address() + keyAvailableSWOP);
        long user2ShareTokensLocked = farmingDapp.getIntegerData(poolAddress + "_" + secondCaller.address() + keyUserShareTokensLocked);
        long lastInterest2 = farmingDapp.getIntegerData(poolAddress + keyLastInterest);
        int currentHeight2 = node().getHeight();
        long user2NewInterest = calcInterest(
                farmingDapp.getIntegerData(String.format("%s_last_interest_height", poolAddress)),
                currentHeight1,
                votingDapp.getIntegerData(keyRewardUpdateHeight),
                farmingDapp.getIntegerData(String.format("%s_last_interest", poolAddress)),
                votingDapp.getIntegerData(String.format(keyRewardPoolFractionCurrent, poolAddress)),
                farmingDapp.getIntegerData(String.format("%s_total_share_tokens_locked", poolAddress)),
                votingDapp.getIntegerData(String.format(keyRewardPoolFractionPrevious, poolAddress)),
                scaleValue
        );
        long claimAmount2 = BigInteger.valueOf(user2ShareTokensLocked).multiply(BigInteger.valueOf(user2NewInterest - lastInterest2)).divide(BigInteger.valueOf(scaleValue)).longValue();

        node().waitForTransaction(
                secondCaller.invoke(i -> i.dApp(farmingDapp).function("claim", StringArg.as(poolAddress)).fee(500000L)).tx().id()
        );
        assertAll("state after first user claim check",
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_last_interest", poolAddress))).isEqualTo(user2NewInterest),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_%s_last_interest", poolAddress, secondCaller.address()))).isEqualTo(user2NewInterest),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_%s_share_tokens_locked", poolAddress, secondCaller.address()))).isEqualTo(user2ShareTokensLocked),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_total_share_tokens_locked", poolAddress))).isEqualTo(sTokensLockedBefore),
                () -> assertThat(farmingDapp.getIntegerData(poolAddress + "_" + secondCaller.address() + keyAvailableSWOP)).isEqualTo(0),
                () -> assertThat(farmingDapp.getIntegerData(poolAddress + keyLastInterestHeight)).isEqualTo(currentHeight2),
                () -> assertThat(secondCaller.getAssetBalance(swopId)).isEqualTo(secondSWOPBalanceBefore + user2AvailableSWOP + claimAmount2)
        );
    }

    @Test
    void j_claimAfterRewardUpdate() {
        long lockShareAmount = 1000000L;
        long userSWOPBalanceBefore = firstCaller.getAssetBalance(swopId);
        long sTokensLockedBeforeFirst = farmingDapp.getIntegerData(String.format("%s_total_share_tokens_locked", pool1.address()));
        long userShareTokensLocked = farmingDapp.getIntegerData(String.format("%s_%s_share_tokens_locked", pool1.address(), firstCaller.address()));
        long userAvailableSWOP = farmingDapp.getIntegerData(String.format("%s_%s_available_SWOP", pool1.address(), firstCaller.address()));
        long lastInterest = farmingDapp.getIntegerData(String.format("%s_last_interest", pool1.address()));
        long scaleValue = (long) Math.pow(10, shareAssetDecimals);
        long userNewInterest = calcInterest(
                farmingDapp.getIntegerData(String.format("%s_last_interest_height", pool1.address())),
                node().getHeight(),
                votingDapp.getIntegerData(keyRewardUpdateHeight),
                lastInterest,
                votingDapp.getIntegerData(String.format(keyRewardPoolFractionCurrent, pool1.address())),
                farmingDapp.getIntegerData(String.format("%s_total_share_tokens_locked", pool1.address())),
                votingDapp.getIntegerData(String.format(keyRewardPoolFractionPrevious, pool1.address())),
                scaleValue
        );
        long claimAmount1 = BigInteger.valueOf(userShareTokensLocked).multiply(BigInteger.valueOf(userNewInterest - lastInterest)).divide(BigInteger.valueOf(scaleValue)).longValue();
        node().waitForTransaction(
                firstCaller.invoke(i -> i.dApp(farmingDapp).function("lockShareTokens", StringArg.as(pool1.address().toString())).payment(lockShareAmount, shareAssetId1).fee(500000L)).tx().id()
        );

        assertAll("state after first user lock check",
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_last_interest", pool1.address()))).isEqualTo(userNewInterest),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_%s_last_interest", pool1.address(), firstCaller.address()))).isEqualTo(userNewInterest),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_%s_share_tokens_locked", pool1.address(), firstCaller.address()))).isEqualTo(userShareTokensLocked + lockShareAmount),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_total_share_tokens_locked", pool1.address()))).isEqualTo(sTokensLockedBeforeFirst + lockShareAmount),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_%s_available_SWOP", pool1.address(), firstCaller.address()))).isEqualTo(userAvailableSWOP + claimAmount1)
        );
        node().waitNBlocks(2);
        node().waitForTransaction(votingDapp.writeData(d -> d.integer(String.format(keyRewardPoolFractionCurrent, pool1.address()), secondReward)).tx().id());
        node().waitForTransaction(votingDapp.writeData(d -> d.integer(String.format(keyRewardPoolFractionPrevious, pool1.address()), firstReward)).tx().id());
        node().waitForTransaction(votingDapp.writeData(d -> d.integer(keyRewardUpdateHeight, node().getHeight() + 3)).tx().id());
        node().waitNBlocks(2);

        long userAvailableSWOP2 = farmingDapp.getIntegerData(pool1.address() + "_" + firstCaller.address() + keyAvailableSWOP);
        long sTokensLockedBefore = farmingDapp.getIntegerData(pool1.address() + keyShareTokensLocked);
        long userShareTokensLocked2 = farmingDapp.getIntegerData(pool1.address() + "_" + firstCaller.address() + keyUserShareTokensLocked);
        long lastInterest2 = farmingDapp.getIntegerData(pool1.address() + keyLastInterest);
        int currentHeight = node().getHeight();
        long userNewInterest2 = calcInterest(
                farmingDapp.getIntegerData(String.format("%s_last_interest_height", pool1.address())),
                currentHeight,
                votingDapp.getIntegerData(keyRewardUpdateHeight),
                farmingDapp.getIntegerData(String.format("%s_last_interest", pool1.address())),
                votingDapp.getIntegerData(String.format(keyRewardPoolFractionCurrent, pool1.address())),
                farmingDapp.getIntegerData(String.format("%s_total_share_tokens_locked", pool1.address())),
                votingDapp.getIntegerData(String.format(keyRewardPoolFractionPrevious, pool1.address())),
                scaleValue
        );
        long claimAmount2 = BigInteger.valueOf(userShareTokensLocked2).multiply(BigInteger.valueOf(userNewInterest2 - lastInterest2)).divide(BigInteger.valueOf(scaleValue)).longValue();

        node().waitForTransaction(
                firstCaller.invoke(i -> i.dApp(farmingDapp).function("claim", StringArg.as(pool1.address().toString())).fee(500000L)).tx().id()
        );
        assertAll("state after first user claim check",
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_last_interest", pool1.address()))).isEqualTo(userNewInterest2),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_%s_last_interest", pool1.address(), firstCaller.address()))).isEqualTo(userNewInterest2),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_%s_share_tokens_locked", pool1.address(), firstCaller.address()))).isEqualTo(userShareTokensLocked2),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_total_share_tokens_locked", pool1.address()))).isEqualTo(sTokensLockedBefore),
                () -> assertThat(farmingDapp.getIntegerData(pool1.address() + "_" + firstCaller.address() + keyAvailableSWOP)).isEqualTo(0),
                () -> assertThat(farmingDapp.getIntegerData(pool1.address() + keyLastInterestHeight)).isEqualTo(currentHeight),
                () -> assertThat(firstCaller.getAssetBalance(swopId)).isEqualTo(userSWOPBalanceBefore + userAvailableSWOP2 + claimAmount2)
        );

    }

    private long calcInterest(long lastInterestHeight,
                              long currentHeight,
                              long rewardUpdateHeight,
                              long lastInterest,
                              long rewardPoolFractionCurrent,
                              long shareTokenLocked,
                              long rewardPoolFractionPrevious,
                              long scaleValue) {
        long currentRewardPerBlock = BigInteger.valueOf(totalRewardPerBlockCurrent)
                .multiply(BigInteger.valueOf(rewardPoolFractionCurrent)).divide(BigInteger.valueOf(totalVoteShare)).longValue();
        long previousRewardPerBlock = BigInteger.valueOf(totalRewardPerBlockPrevious)
                .multiply(BigInteger.valueOf(rewardPoolFractionPrevious)).divide(BigInteger.valueOf(totalVoteShare)).longValue();
        if (currentHeight < rewardUpdateHeight) {
          long reward = previousRewardPerBlock * (currentHeight - lastInterestHeight);
          BigInteger deltaInterest = BigInteger.valueOf(reward).multiply(BigInteger.valueOf(scaleValue)).divide(BigInteger.valueOf(shareTokenLocked));
          return lastInterest + deltaInterest.longValue();
        } else {
            if (lastInterestHeight > rewardUpdateHeight) {
                if (shareTokenLocked == 0) {
                    return 0;
                } else {
                    long reward = currentRewardPerBlock * (currentHeight - lastInterestHeight);
                    BigInteger deltaInterest = BigInteger.valueOf(reward).multiply(BigInteger.valueOf(scaleValue)).divide(BigInteger.valueOf(shareTokenLocked));
                    return lastInterest + deltaInterest.longValue();
                }
            } else {
                long rewardAfterLastInterestBeforeRewardUpdate = previousRewardPerBlock * (rewardUpdateHeight - lastInterestHeight);
                long interestAfterUpdate = lastInterest +
                        BigInteger.valueOf(rewardAfterLastInterestBeforeRewardUpdate).multiply(BigInteger.valueOf(scaleValue)).divide(BigInteger.valueOf(shareTokenLocked)).longValue();
                long reward = currentRewardPerBlock * (currentHeight - rewardUpdateHeight);
                BigInteger deltaInterest = BigInteger.valueOf(reward).multiply(BigInteger.valueOf(scaleValue)).divide(BigInteger.valueOf(shareTokenLocked));
                return interestAfterUpdate + deltaInterest.longValue();
            }
        }
    }
}