package SWOP;

import com.wavesplatform.transactions.common.Amount;
import dapps.FarmingDApp;
import dapps.GovernanceDApp;
import im.mak.paddle.Account;
import com.wavesplatform.transactions.common.AssetId;
import com.wavesplatform.transactions.data.IntegerEntry;
import im.mak.paddle.exceptions.ApiError;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigInteger;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import static im.mak.paddle.token.Waves.WAVES;
import static im.mak.paddle.util.Async.async;
import static im.mak.paddle.Node.node;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.Alphanumeric.class)
class FarmingTest {
    final long initSWOPAmount = 100000000000000L;
    long firstReward = 5000000000L;
    long secondReward = 5000000000L;
    int previousReward = 0;
    long scaleValue = (long) Math.pow(10, shareAssetDecimals);
    final String keyShareTokensLocked = "%s_total_share_tokens_locked";
    final String keyRewardPoolFractionCurrent = "%s_current_pool_fraction_reward";
    final String keyRewardPoolFractionPrevious = "%s_previous_pool_fraction_reward";
    final String keyTotalRewardPerBlockCurrent = "total_reward_per_block_current";
    final String keyTotalRewardPerBlockPrevious = "total_reward_per_block_previous";
    final Long totalRewardPerBlockCurrent = 189751395L;
    final Long totalRewardPerBlockPrevious = 189751395L;
    final Long totalVoteShare = 10000000000L;
    final String keyRewardUpdateHeight = "reward_update_height";
    final String keyLastInterest = "%s_last_interest";
    final String keyLastInterestHeight = "%s_last_interest_height";
    final String keyUserShareTokensLocked = "%s_%s_share_tokens_locked";
    final String keyUserLastInterest = "%s_%s_last_interest";
    final String keyUserSWOPClaimedAmount = "_SWOP_claimed_amount";
    final String keyUserSWOPLastClaimedAmount = "_SWOP_last_claimed_amount";
    final String keyAvailableSWOP = "%s_%s_available_SWOP";
    final String keyFarmingStartHeight = "farming_start_height";
    final static String keyPoolRewardHeight = "_reward_update_height";

    static final int shareAssetDecimals = 6;
    static Account pool1, pool2, firstCaller, secondCaller, votingDApp, earlyLP;
    static GovernanceDApp governance;
    static FarmingDApp farming;
    static AssetId shareAssetId1, shareAssetId2;
    static AssetId swopId;

    @BeforeAll
    static void before() {
        async(
                () -> pool1 = new Account(WAVES.amount(1000)),
                () -> pool2 = new Account(WAVES.amount(1000)),
                () -> firstCaller = new Account(WAVES.amount(1000)),
                () -> secondCaller = new Account(WAVES.amount(1000)),
                () -> earlyLP = new Account(WAVES.amount(1000)),
                () -> votingDApp = new Account(WAVES.amount(1000))
        );
        async(
                () -> shareAssetId1 = firstCaller.issue(a -> a
                        .quantity(Long.MAX_VALUE).name("sBTC_WAVES").decimals(shareAssetDecimals)).tx().assetId(),
                () -> shareAssetId2 = firstCaller.issue(a -> a
                        .quantity(Long.MAX_VALUE).name("sUSDT_USDN").decimals(shareAssetDecimals)).tx().assetId()
        );
        async(
                () -> firstCaller.transfer(secondCaller, Long.MAX_VALUE / 2, shareAssetId1),
                () -> pool1.writeData(d -> d.string("share_asset_id", shareAssetId1.toString())),
                () -> firstCaller.transfer(secondCaller, Long.MAX_VALUE / 2, shareAssetId2),
                () -> pool2.writeData(d -> d.string("share_asset_id", shareAssetId2.toString())),
                () -> farming = new FarmingDApp(WAVES.amount(1000), votingDApp.address()),
                () -> votingDApp.writeData(d -> d
                        .integer(pool1.address().toString() + keyPoolRewardHeight, node().getHeight())
                        .integer(pool2.address().toString() + keyPoolRewardHeight, node().getHeight())
                )
        );
    }

    @Test
    void a_init() {
        firstCaller.invoke(farming.init(earlyLP.address().toString()), i -> i.additionalFee(WAVES.of(1)));
        swopId = AssetId.as(farming.getStringData("SWOP_id"));
        assertThat(farming.getAssetBalance(swopId)).isEqualTo(initSWOPAmount);
    }

    static Stream<Arguments> poolProvider() {
        return Stream.of(
                Arguments.of(pool1),
                Arguments.of(pool2));
    }

    @ParameterizedTest(name = "init pool share farming")
    @MethodSource("poolProvider")
    void b_initPoolShareFarming(Account pool) {
        int rewardUpdateHeight = node().getHeight();
        String poolAddress = pool.address().toString();

        votingDApp.writeData(d -> d
                .integer(String.format(keyRewardPoolFractionCurrent, poolAddress), firstReward)
                .integer(String.format(keyRewardPoolFractionPrevious, poolAddress), previousReward)
                .integer(keyRewardUpdateHeight, rewardUpdateHeight)
                .integer(keyTotalRewardPerBlockCurrent, totalRewardPerBlockCurrent)
                .integer(keyTotalRewardPerBlockPrevious, totalRewardPerBlockPrevious));

        firstCaller.invoke(farming.initPoolShareFarming(pool));
        assertThat(farming.getData()).contains(
                IntegerEntry.as(String.format(keyShareTokensLocked, poolAddress), 0),
                IntegerEntry.as(String.format(keyLastInterest, poolAddress), 0));
    }

    /*@Test
    void c_preLock() {
        String poolAddress = pool1.address().toString();
        startFarmingHeight = node().getHeight() + 10;
        farmingDapp.writeData(d -> d.integer("farming_start_height", startFarmingHeight));

        firstCaller.invoke(farming.lockShareTokens(pool1), Amount.of(preLockShareAmount, shareAssetId1));
        secondCaller.invoke(farming.lockShareTokens(pool1), Amount.of(preLockShareAmount, shareAssetId1));

        assertAll("state after first user lock before farming",
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_last_interest", poolAddress))).isEqualTo(0),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_%s_last_interest", poolAddress, firstCaller.address()))).isEqualTo(0),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_%s_last_interest", poolAddress, secondCaller.address()))).isEqualTo(0),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_%s_share_tokens_locked", poolAddress, firstCaller.address()))).isEqualTo(preLockShareAmount),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_%s_share_tokens_locked", poolAddress, secondCaller.address()))).isEqualTo(preLockShareAmount),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_total_share_tokens_locked", poolAddress))).isEqualTo(2 * preLockShareAmount),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_%s_available_SWOP", poolAddress, firstCaller.address()))).isEqualTo(0),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_%s_available_SWOP", poolAddress, secondCaller.address()))).isEqualTo(0));

    }
    @Test
    void d_preWithdraw() {
        String poolAddress = pool1.address().toString();
        firstCaller.invoke(farming.withdrawShareTokens(pool1, preWithdrawAmount));
        secondCaller.invoke(farming.withdrawShareTokens(pool1, preWithdrawAmount));

        assertAll("state after first user lock before farming",
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_last_interest", poolAddress))).isEqualTo(0),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_%s_last_interest", poolAddress, firstCaller.address()))).isEqualTo(0),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_%s_last_interest", poolAddress, secondCaller.address()))).isEqualTo(0),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_%s_share_tokens_locked", poolAddress, firstCaller.address()))).isEqualTo(preLockShareAmount - preWithdrawAmount),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_%s_share_tokens_locked", poolAddress, secondCaller.address()))).isEqualTo(preLockShareAmount - preWithdrawAmount),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_total_share_tokens_locked", poolAddress))).isEqualTo(2 * preLockShareAmount - 2 * preWithdrawAmount),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_%s_available_SWOP", poolAddress, firstCaller.address()))).isEqualTo(0),
                () -> assertThat(farmingDapp.getIntegerData(String.format("%s_%s_available_SWOP", poolAddress, secondCaller.address()))).isEqualTo(0));
    }*/

    static Stream<Arguments> lockShareProvider() {
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
                Arguments.of(pool2, ThreadLocalRandom.current().nextLong(100000000000L, 100000000000000L)));
    }

    @ParameterizedTest(name = "lock {1} share tokens")
    @MethodSource("lockShareProvider")
    void f_lockShareTokens(Account pool, long lockShareAmount) {
        String poolAddress = pool.address().toString();
        AssetId shareAssetId = pool.address().equals(pool1.address()) ? shareAssetId1 : shareAssetId2;

        firstCaller.invoke(farming.lockShareTokens(pool), Amount.of(1, shareAssetId));
        node().waitNBlocks(1);

        long sTokensLockedBeforeFirst = farming.getIntegerData(String.format(keyShareTokensLocked, poolAddress));
        long user1ShareTokensLocked = farming.getIntegerData(String.format(keyUserShareTokensLocked, poolAddress, firstCaller.address()));
        long user1AvailableSWOP = farming.getIntegerData(String.format(keyAvailableSWOP, poolAddress, firstCaller.address()));
        long lastInterest = farming.getIntegerData(String.format(keyLastInterest, poolAddress));
        long poolUpdateHeight = votingDApp.getIntegerData(String.format(pool.address().toString() + keyPoolRewardHeight));
        long user1NewInterest = calcInterest(
                farming.getIntegerData(String.format(keyLastInterestHeight, poolAddress)),
                node().getHeight(),
                votingDApp.getIntegerData(keyRewardUpdateHeight),
                lastInterest,
                votingDApp.getIntegerData(String.format(keyRewardPoolFractionCurrent, poolAddress)),
                farming.getIntegerData(String.format(keyShareTokensLocked, poolAddress)),
                votingDApp.getIntegerData(String.format(keyRewardPoolFractionPrevious, poolAddress)),
                scaleValue,
                poolUpdateHeight);
        long claimAmount1 = BigInteger.valueOf(user1ShareTokensLocked).multiply(BigInteger.valueOf(user1NewInterest - lastInterest)).divide(BigInteger.valueOf(scaleValue)).longValue();

        firstCaller.invoke(farming.lockShareTokens(pool), Amount.of(lockShareAmount, shareAssetId));

        assertThat(farming.getData()).contains(
                IntegerEntry.as(String.format(keyLastInterest, poolAddress), user1NewInterest),
                IntegerEntry.as(String.format(keyUserLastInterest, poolAddress, firstCaller.address()), user1NewInterest),
                IntegerEntry.as(String.format(keyUserShareTokensLocked, poolAddress, firstCaller.address()), user1ShareTokensLocked + lockShareAmount),
                IntegerEntry.as(String.format(keyShareTokensLocked, poolAddress), sTokensLockedBeforeFirst + lockShareAmount),
                IntegerEntry.as(String.format(keyAvailableSWOP, poolAddress, firstCaller.address()), user1AvailableSWOP + claimAmount1));

        long sTokensLockedBeforeSecond = farming.getIntegerData(String.format(keyShareTokensLocked, poolAddress));
        long user2NewInterest = calcInterest(
                farming.getIntegerData(String.format(keyLastInterestHeight, poolAddress)),
                node().getHeight(),
                votingDApp.getIntegerData(keyRewardUpdateHeight),
                farming.getIntegerData(String.format(keyLastInterest, poolAddress)),
                votingDApp.getIntegerData(String.format(keyRewardPoolFractionCurrent, poolAddress)),
                farming.getIntegerData(String.format(keyShareTokensLocked, poolAddress)),
                votingDApp.getIntegerData(String.format(keyRewardPoolFractionPrevious, poolAddress)),
                scaleValue,
                poolUpdateHeight);
        long user2ShareTokensLocked = lockShareAmount == 1
                ? 0 : farming.getIntegerData(String.format(keyUserShareTokensLocked, poolAddress, secondCaller.address()));

        secondCaller.invoke(farming.lockShareTokens(pool), Amount.of(lockShareAmount, shareAssetId));
        node().waitNBlocks(1);

        assertThat(farming.getData()).contains(
                IntegerEntry.as(String.format(keyLastInterest, poolAddress), user2NewInterest),
                IntegerEntry.as(String.format(keyUserLastInterest, poolAddress, secondCaller.address()), user2NewInterest),
                IntegerEntry.as(String.format(keyUserShareTokensLocked, poolAddress, secondCaller.address()), user2ShareTokensLocked + lockShareAmount),
                IntegerEntry.as(String.format(keyShareTokensLocked, poolAddress), sTokensLockedBeforeSecond + lockShareAmount));
    }

    static Stream<Arguments> withdrawShareProvider() {
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
                Arguments.of(pool2, ThreadLocalRandom.current().nextLong(100000000L, 100000000000L)));
    }

    @ParameterizedTest(name = "withdraw {1} share tokens")
    @MethodSource("withdrawShareProvider")
    void h_withdrawShareTokens(Account pool, long withdrawShareAmount) {
        String poolAddress = pool.address().toString();
        AssetId shareAssetId;
        if (pool == pool1) {
            shareAssetId = shareAssetId1;
            if (withdrawShareAmount == 1)
                node().waitNBlocks(2);
        } else {
            shareAssetId = shareAssetId2;
        }
        long sTokensLockedBeforeFirst = farming.getIntegerData(String.format(keyShareTokensLocked, poolAddress));
        long user1ShareTokensLocked = farming.getIntegerData(String.format(keyUserShareTokensLocked, poolAddress, firstCaller.address()));
        long poolUpdateHeight = votingDApp.getIntegerData(String.format(pool.address().toString() + keyPoolRewardHeight));
        long user1NewInterest = calcInterest(
                farming.getIntegerData(String.format(keyLastInterestHeight, poolAddress)),
                node().getHeight(),
                votingDApp.getIntegerData(keyRewardUpdateHeight),
                farming.getIntegerData(String.format(keyLastInterest, poolAddress)),
                votingDApp.getIntegerData(String.format(keyRewardPoolFractionCurrent, poolAddress)),
                farming.getIntegerData(String.format(keyShareTokensLocked, poolAddress)),
                votingDApp.getIntegerData(String.format(keyRewardPoolFractionPrevious, poolAddress)),
                scaleValue,
                poolUpdateHeight);

        firstCaller.invoke(farming.withdrawShareTokens(pool, withdrawShareAmount), Amount.of(withdrawShareAmount, shareAssetId));

        assertAll("state after first user lock check",
                () -> assertThat(farming.getIntegerData(String.format(keyLastInterest, poolAddress))).isCloseTo(user1NewInterest, within(10L)),
                () -> assertThat(farming.getIntegerData(String.format(keyUserLastInterest, poolAddress, firstCaller.address()))).isCloseTo(user1NewInterest, within(10L)),
                () -> assertThat(farming.getData()).contains(
                        IntegerEntry.as(String.format(keyUserShareTokensLocked, poolAddress, firstCaller.address()), user1ShareTokensLocked - withdrawShareAmount),
                        IntegerEntry.as(String.format(keyShareTokensLocked, poolAddress), sTokensLockedBeforeFirst - withdrawShareAmount)));

        long sTokensLockedBeforeSecond = farming.getIntegerData(String.format(keyShareTokensLocked, poolAddress));
        long user2NewInterest = calcInterest(
                farming.getIntegerData(String.format(keyLastInterestHeight, poolAddress)),
                node().getHeight(),
                votingDApp.getIntegerData(keyRewardUpdateHeight),
                farming.getIntegerData(String.format(keyLastInterest, poolAddress)),
                votingDApp.getIntegerData(String.format(keyRewardPoolFractionCurrent, poolAddress)),
                farming.getIntegerData(String.format(keyShareTokensLocked, poolAddress)),
                votingDApp.getIntegerData(String.format(keyRewardPoolFractionPrevious, poolAddress)),
                scaleValue,
                poolUpdateHeight);
        long user2ShareTokensLocked = farming.getIntegerData(String.format(keyUserShareTokensLocked, poolAddress, secondCaller.address()));

        secondCaller.invoke(farming.withdrawShareTokens(pool, withdrawShareAmount), Amount.of(withdrawShareAmount, shareAssetId));

        assertAll("state after second user lock check",
                () -> assertThat(farming.getIntegerData(String.format(keyLastInterest, poolAddress))).isCloseTo(user2NewInterest, within(10L)),
                () -> assertThat(farming.getIntegerData(String.format(keyUserLastInterest, poolAddress, secondCaller.address()))).isCloseTo(user2NewInterest, within(10L)),
                () -> assertThat(farming.getIntegerData(String.format(keyUserShareTokensLocked, poolAddress, secondCaller.address()))).isEqualTo(user2ShareTokensLocked - withdrawShareAmount),
                () -> assertThat(farming.getIntegerData(String.format(keyShareTokensLocked, poolAddress))).isEqualTo(sTokensLockedBeforeSecond - withdrawShareAmount));
    }

    @ParameterizedTest(name = "claim SWOP")
    @MethodSource("poolProvider")
    void i_claimSWOP(Account pool) {
        String poolAddress = pool.address().toString();
        long sTokensLockedBefore = farming.getIntegerData(String.format(keyShareTokensLocked, poolAddress));
        long user1ShareTokensLocked = farming.getIntegerData(String.format(keyUserShareTokensLocked, poolAddress, firstCaller.address()));
        long lastInterest1 = farming.getIntegerData(String.format(keyLastInterest, poolAddress));
        long lastInterestHeight1 = farming.getIntegerData(String.format(keyLastInterestHeight, poolAddress));
        long rewardUpdateHeight1 = votingDApp.getIntegerData(keyRewardUpdateHeight);
        long rewardPoolFractionCurrent1 = votingDApp.getIntegerData(String.format(keyRewardPoolFractionCurrent, poolAddress));
        long totalShareTokensLocked1 = farming.getIntegerData(String.format(keyShareTokensLocked, poolAddress));
        long rewardPoolFractionPrevious1 = votingDApp.getIntegerData(String.format(keyRewardPoolFractionPrevious, poolAddress));
        long poolUpdateHeight = votingDApp.getIntegerData(String.format(pool.address().toString() + keyPoolRewardHeight));
        node().waitNBlocks(1);
        int currentHeight1 = node().getHeight();
        long user1NewInterest = calcInterest(
                lastInterestHeight1,
                currentHeight1,
                rewardUpdateHeight1,
                lastInterest1,
                rewardPoolFractionCurrent1,
                totalShareTokensLocked1,
                rewardPoolFractionPrevious1,
                scaleValue,
                poolUpdateHeight);
        long claimAmount1 = BigInteger.valueOf(user1ShareTokensLocked).multiply(BigInteger.valueOf(user1NewInterest - lastInterest1)).divide(BigInteger.valueOf(scaleValue)).longValue();

        assertThat(assertThrows(ApiError.class, () ->
                firstCaller.invoke(farming.claim(pool)))
        ).hasMessageContaining("You have 0 available SWOP");
    }

    @Test
    void j_claimAfterRewardUpdate() {
        long lockShareAmount = 1000000L;
        long userSWOPBalanceBefore = firstCaller.getAssetBalance(swopId);
        long sTokensLockedBeforeFirst = farming.getIntegerData(String.format(keyShareTokensLocked, pool1.address()));
        long userShareTokensLocked = farming.getIntegerData(String.format(keyUserShareTokensLocked, pool1.address(), firstCaller.address()));
        long userAvailableSWOP = farming.getIntegerData(String.format(keyAvailableSWOP, pool1.address(), firstCaller.address()));
        long lastInterest = farming.getIntegerData(String.format(keyLastInterest, pool1.address()));
        long scaleValue = (long) Math.pow(10, shareAssetDecimals);
        long poolUpdateHeight = votingDApp.getIntegerData(String.format(pool1.address().toString() + keyPoolRewardHeight));
        long userNewInterest = calcInterest(
                farming.getIntegerData(String.format(keyLastInterestHeight, pool1.address())),
                node().getHeight(),
                votingDApp.getIntegerData(keyRewardUpdateHeight),
                lastInterest,
                votingDApp.getIntegerData(String.format(keyRewardPoolFractionCurrent, pool1.address())),
                farming.getIntegerData(String.format(keyShareTokensLocked, pool1.address())),
                votingDApp.getIntegerData(String.format(keyRewardPoolFractionPrevious, pool1.address())),
                scaleValue,
                poolUpdateHeight);
        long claimAmount1 = BigInteger.valueOf(userShareTokensLocked).multiply(BigInteger.valueOf(userNewInterest - lastInterest)).divide(BigInteger.valueOf(scaleValue)).longValue();

        firstCaller.invoke(farming.lockShareTokens(pool1), Amount.of(lockShareAmount, shareAssetId1));

        assertThat(farming.getData()).describedAs("state after first user lock check").contains(
                IntegerEntry.as(String.format(keyLastInterest, pool1.address()), userNewInterest),
                IntegerEntry.as(String.format(keyUserLastInterest, pool1.address(), firstCaller.address()), userNewInterest),
                IntegerEntry.as(String.format(keyUserShareTokensLocked, pool1.address(), firstCaller.address()), userShareTokensLocked + lockShareAmount),
                IntegerEntry.as(String.format(keyShareTokensLocked, pool1.address()), sTokensLockedBeforeFirst + lockShareAmount),
                IntegerEntry.as(String.format(keyAvailableSWOP, pool1.address(), firstCaller.address()), userAvailableSWOP + claimAmount1));

        node().waitNBlocks(2);
        votingDApp.writeData(d -> d
                .integer(String.format(keyRewardPoolFractionCurrent, pool1.address()), secondReward)
                .integer(String.format(keyRewardPoolFractionPrevious, pool1.address()), firstReward)
                .integer(keyRewardUpdateHeight, node().getHeight() + 3));
        node().waitNBlocks(2);

        long userAvailableSWOP2 = farming.getIntegerData(String.format(keyAvailableSWOP, pool1.address(), firstCaller.address()));
        long sTokensLockedBefore = farming.getIntegerData(String.format(keyShareTokensLocked, pool1.address()));
        long userShareTokensLocked2 = farming.getIntegerData(String.format(keyUserShareTokensLocked, pool1.address(), firstCaller.address()));
        long lastInterest2 = farming.getIntegerData(String.format(keyLastInterest, pool1.address()));
        int currentHeight = node().getHeight();
        long userNewInterest2 = calcInterest(
                farming.getIntegerData(String.format(keyLastInterestHeight, pool1.address())),
                currentHeight,
                votingDApp.getIntegerData(keyRewardUpdateHeight),
                farming.getIntegerData(String.format(keyLastInterest, pool1.address())),
                votingDApp.getIntegerData(String.format(keyRewardPoolFractionCurrent, pool1.address())),
                farming.getIntegerData(String.format(keyShareTokensLocked, pool1.address())),
                votingDApp.getIntegerData(String.format(keyRewardPoolFractionPrevious, pool1.address())),
                scaleValue,
                poolUpdateHeight);
        long claimAmount2 = BigInteger.valueOf(userShareTokensLocked2).multiply(BigInteger.valueOf(userNewInterest2 - lastInterest2)).divide(BigInteger.valueOf(scaleValue)).longValue();

        firstCaller.invoke(farming.claim(pool1));

        assertAll("state after first user claim after reward update check",
                () -> assertThat(farming.getData()).contains(
                        IntegerEntry.as(String.format(keyLastInterest, pool1.address()), userNewInterest2),
                        IntegerEntry.as(String.format(keyUserLastInterest, pool1.address(), firstCaller.address()), userNewInterest2),
                        IntegerEntry.as(String.format(keyUserShareTokensLocked, pool1.address(), firstCaller.address()), userShareTokensLocked2),
                        IntegerEntry.as(String.format(keyShareTokensLocked, pool1.address()), sTokensLockedBefore),
                        IntegerEntry.as(String.format(keyAvailableSWOP, pool1.address(), firstCaller.address()), 0),
                        IntegerEntry.as(String.format(keyLastInterestHeight, pool1.address()), currentHeight)),
                () -> assertThat(firstCaller.getAssetBalance(swopId)).isEqualTo(userSWOPBalanceBefore + userAvailableSWOP2 + claimAmount2));
    }

    private long calcInterest(long lastInterestHeight,
                              long currentHeight,
                              long rewardUpdateHeight,
                              long lastInterest,
                              long rewardPoolFractionCurrent,
                              long shareTokenLocked,
                              long rewardPoolFractionPrevious,
                              long scaleValue,
                              long poolRewardUpdateHeight) {
        long currentRewardPerBlock = BigInteger.valueOf(totalRewardPerBlockCurrent)
                .multiply(BigInteger.valueOf(rewardPoolFractionCurrent)).divide(BigInteger.valueOf(totalVoteShare)).longValue();
        long previousRewardPerBlock = BigInteger.valueOf(totalRewardPerBlockPrevious)
                .multiply(BigInteger.valueOf(rewardPoolFractionPrevious)).divide(BigInteger.valueOf(totalVoteShare)).longValue();
        long height = node().getHeight();
        if (height <= rewardUpdateHeight && rewardUpdateHeight == poolRewardUpdateHeight) {
            long reward = currentRewardPerBlock * (currentHeight - lastInterestHeight);
            BigInteger deltaInterest = BigInteger.valueOf(reward).multiply(BigInteger.valueOf(scaleValue)).divide(BigInteger.valueOf(shareTokenLocked));
            return lastInterest + deltaInterest.longValue();
        } else if (rewardUpdateHeight < height && rewardUpdateHeight == poolRewardUpdateHeight) {
            long reward = currentRewardPerBlock * (currentHeight - lastInterestHeight);
            BigInteger deltaInterest = BigInteger.valueOf(reward).multiply(BigInteger.valueOf(scaleValue)).divide(BigInteger.valueOf(shareTokenLocked));
            return lastInterest + deltaInterest.longValue();
        } else if (rewardUpdateHeight < height && rewardUpdateHeight != poolRewardUpdateHeight) {
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
