package SWOP;

import im.mak.paddle.Account;
import com.wavesplatform.transactions.common.AssetId;
import com.wavesplatform.transactions.data.IntegerEntry;
import com.wavesplatform.transactions.invocation.IntegerArg;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigInteger;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import com.wavesplatform.crypto.base.Base58;

import static im.mak.paddle.Async.async;
import static im.mak.paddle.Node.node;
import static im.mak.paddle.util.Script.fromFile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@TestMethodOrder(MethodOrderer.Alphanumeric.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class GovernanceTest {
    private int startFarmingHeight;
    private int shareAssetDecimals = 6;
    private long scaleValue = (long) Math.pow(10, 8);
    String keyLastInterest = "last_interest";
    String keyUserLastInterest = "_last_interest";
    String keyUserSWOPAmount = "_SWOP_amount";
    String keyTotalSWOPAmount = "total_SWOP_amount";
    private final String kBasePeriod = "base_period";
    private final String kStartHeight = "start_height";
    private final String kPeriodLength = "period_length";
    private Account pool = new Account(1000_00000000L);
    private Account firstCaller = new Account(1000_00000000L);
    private Account secondCaller = new Account(1000_00000000L);
    private Account airdropCaller = new Account(1000_00000000L);
    private Account governanceDapp = new Account(1000_00000000L);
    private Account farmingDapp = new Account(1000_00000000L);
    private Account votingDapp = new Account(1000_00000000L);
    private AssetId swopId;
    private final String votingScript = StringUtils.substringBefore(
            fromFile("dApps/SWOP/voting.ride")
                    .replace("3PLHVWCqA9DJPDbadUofTohnCULLauiDWhS", governanceDapp.address().toString()),
            "@Verifier");
    private String dAppScript = fromFile("dApps/SWOP/governance.ride")
            .replace("3P73HDkPqG15nLXevjCbmXtazHYTZbpPoPw", farmingDapp.address().toString())
            .replace("3PQZWxShKGRgBN1qoJw6B4s9YWS9FneZTPg", votingDapp.address().toString())
            .replace("DXDY2itiEcYBtGkVLnkpHtDFyWQUkoLJz79uJ7ECbMrA", Base58.encode(farmingDapp.publicKey().bytes()))
            .replace("E6Wa1SGoktYcjHjsKrvjMiqJY3SWmGKcD8Q5L8kxSPS7", Base58.encode(farmingDapp.publicKey().bytes()))
            .replace("AZmWJtuy4GeVrMmJH4hfFBRApe1StvhJSk4jcbT6bArQ", Base58.encode(farmingDapp.publicKey().bytes()));

    @BeforeAll
    void before() {
        async(
                () -> governanceDapp.setScript(s -> s.script(dAppScript)),
                () -> votingDapp.setScript(s -> s.script(votingScript)),
                () -> {
                    swopId = firstCaller.issue(a -> a.quantity(Long.MAX_VALUE).name("SWOP").decimals(shareAssetDecimals)).tx().assetId();
                    firstCaller.massTransfer(t -> t.assetId(swopId)
                            .to(secondCaller, Long.MAX_VALUE / 3)
                            .to(airdropCaller, Long.MAX_VALUE / 3));
                    farmingDapp.writeData(d -> d.string("SWOP_id", swopId.toString()));
                },
                () -> governanceDapp.writeData(d -> d.data(
                        IntegerEntry.as(kBasePeriod, 0_000000000L),
                        IntegerEntry.as(kPeriodLength, 10102_000000000L),
                        IntegerEntry.as(kStartHeight, 0_000000000L)
                )),
                () -> votingDapp.writeData(d -> d.data(
                        IntegerEntry.as(kBasePeriod, 0_000000000L),
                        IntegerEntry.as(kPeriodLength, 10102_000000000L),
                        IntegerEntry.as(kStartHeight, 0_000000000L)
                )));
    }

    Stream<Arguments> lockSWOPProvider() {
        long firstRange = ThreadLocalRandom.current().nextLong(100L, 1000L);
        long secondRange = ThreadLocalRandom.current().nextLong(1000L, 100000L);
        long thirdRange = ThreadLocalRandom.current().nextLong(100000L, 100000000L);

        return Stream.of(
                Arguments.of(1, 1),
                Arguments.of(firstRange, thirdRange),
                Arguments.of(secondRange, thirdRange),
                Arguments.of(thirdRange, firstRange));
    }

    @ParameterizedTest(name = "user1 lock {0} SWOP, user2 lock {1} SWOP")
    @MethodSource("lockSWOPProvider")
    void a_lockSWOP(long user1LockAmount, long user2LockAmount) {
        long user1SWOPBalance = firstCaller.getAssetBalance(swopId);
        long user2SWOPBalance = secondCaller.getAssetBalance(swopId);
        long user1SWOPAmount;
        long user2SWOPAmount;
        long totalSWOPAmount;
        if (user1LockAmount == 1) {
            user1SWOPAmount = 0;
            user2SWOPAmount = 0;
            totalSWOPAmount = 0;
        } else {
            user1SWOPAmount = governanceDapp.getIntegerData(firstCaller.address() + keyUserSWOPAmount);
            user2SWOPAmount = governanceDapp.getIntegerData(secondCaller.address() + keyUserSWOPAmount);
            totalSWOPAmount = governanceDapp.getIntegerData(keyTotalSWOPAmount);
        }

        long lastInterest = 0;
        firstCaller.invoke(i -> i.dApp(governanceDapp).function("lockSWOP").payment(user1LockAmount, swopId));

        assertAll("state after first user lock check",
                () -> assertThat(governanceDapp.getData()).contains(
                        IntegerEntry.as(firstCaller.address() + keyUserLastInterest, lastInterest),
                        IntegerEntry.as(firstCaller.address() + keyUserSWOPAmount, user1SWOPAmount + user1LockAmount),
                        IntegerEntry.as(keyTotalSWOPAmount, totalSWOPAmount + user1LockAmount)),
                () -> assertThat(governanceDapp.getAssetBalance(swopId)).isEqualTo(totalSWOPAmount + user1LockAmount),
                () -> assertThat(firstCaller.getAssetBalance(swopId)).isEqualTo(user1SWOPBalance - user1LockAmount));

        secondCaller.invoke(i -> i.dApp(governanceDapp).function("lockSWOP")
                .payment(user2LockAmount, swopId));

        assertAll("state after second user lock check",
                () -> assertThat(governanceDapp.getData()).contains(
                        IntegerEntry.as(secondCaller.address() + keyUserLastInterest, lastInterest),
                        IntegerEntry.as(secondCaller.address() + keyUserSWOPAmount, user2SWOPAmount + user2LockAmount),
                        IntegerEntry.as(keyTotalSWOPAmount, totalSWOPAmount + user1LockAmount + user2LockAmount)),
                () -> assertThat(governanceDapp.getAssetBalance(swopId)).isEqualTo(totalSWOPAmount + user1LockAmount + user2LockAmount),
                () -> assertThat(secondCaller.getAssetBalance(swopId)).isEqualTo(user2SWOPBalance - user2LockAmount));
    }

    Stream<Arguments> lockAndAirdropProvider() {

        return Stream.of(
                Arguments.of(100, 100, 100000),
                Arguments.of(1000, 100000000, 100),
                Arguments.of(50000, 20000000, 3000),
                Arguments.of(60000000, 500, 100000000),
                Arguments.of(10000000000L, 2000000000000L, 10000000000L),
                Arguments.of(300000000000L, 10000000000L, 10000000000000L));
    }

    @ParameterizedTest(name = "user1 lock {0} SWOP -> user2 lock {0} SWOP -> airdrop {2} SWOP -> user1 claimAndWithdraw -> user2 claimAndWithdraw")
    @MethodSource("lockAndAirdropProvider")
    void b_lockAirdropClaimWithdraw(long user1LockAmount, long user2LockAmount, long airdropAmount) {
        long user1SWOPBalance = firstCaller.getAssetBalance(swopId);
        long user2SWOPBalance = secondCaller.getAssetBalance(swopId);
        long user1SWOPAmount = governanceDapp.getIntegerData(firstCaller.address() + keyUserSWOPAmount);
        long user2SWOPAmount = governanceDapp.getIntegerData(secondCaller.address() + keyUserSWOPAmount);
        long totalSWOPAmount = governanceDapp.getIntegerData(keyTotalSWOPAmount);
        long governanceSWOPBalance = governanceDapp.getAssetBalance(swopId);

        firstCaller.invoke(i -> i.dApp(governanceDapp).function("lockSWOP").payment(user1LockAmount, swopId));
        secondCaller.invoke(i -> i.dApp(governanceDapp).function("lockSWOP").payment(user2LockAmount, swopId));

        long lastInterest = user1LockAmount == 100L ? 0 : governanceDapp.getIntegerData(keyLastInterest);
        long newInterest = calcNewInterest(lastInterest, airdropAmount, totalSWOPAmount + user1LockAmount + user2LockAmount);
        airdropCaller.invoke(i -> i.dApp(governanceDapp).function("airDrop").payment(airdropAmount, swopId));

        assertAll("state after airdrop check",
                () -> assertThat(governanceDapp.getIntegerData(keyLastInterest)).isEqualTo(newInterest),
                () -> assertThat(governanceDapp.getIntegerData(keyTotalSWOPAmount)).isEqualTo(totalSWOPAmount + user1LockAmount + user2LockAmount),
                () -> assertThat(governanceDapp.getAssetBalance(swopId)).isEqualTo(governanceSWOPBalance + user1LockAmount + user2LockAmount + airdropAmount));

        long user1SWOPLocked = governanceDapp.getIntegerData(firstCaller.address() + keyUserSWOPAmount);
        long user1lastInterest = governanceDapp.getIntegerData(firstCaller.address() + keyUserLastInterest);
        long user1ClaimAmount = BigInteger.valueOf(user1SWOPLocked)
                .multiply(BigInteger.valueOf(newInterest - user1lastInterest))
                .divide(BigInteger.valueOf(100000000L)).longValue();
        long governanceSWOPBalance1 = governanceDapp.getAssetBalance(swopId);

        firstCaller.invoke(i -> i.dApp(governanceDapp).function("claimAndWithdrawSWOP"));

        assertAll("state after first user claimAndWithdraw check",
                () -> assertThat(governanceDapp.getIntegerData(firstCaller.address() + keyUserLastInterest)).isEqualTo(newInterest),
                () -> assertThat(governanceDapp.getAssetBalance(swopId)).isEqualTo(governanceSWOPBalance1 - user1ClaimAmount),
                () -> assertThat(firstCaller.getAssetBalance(swopId)).isEqualTo(user1SWOPBalance - user1LockAmount + user1ClaimAmount));

        long user2SWOPLocked = governanceDapp.getIntegerData(secondCaller.address() + keyUserSWOPAmount);
        long user2LastInterest = governanceDapp.getIntegerData(secondCaller.address() + keyUserLastInterest);
        long user2ClaimAmount = BigInteger.valueOf(user2SWOPLocked)
                .multiply(BigInteger.valueOf(newInterest - user2LastInterest))
                .divide(BigInteger.valueOf(100000000L)).longValue();
        long governanceSWOPBalance2 = governanceDapp.getAssetBalance(swopId);

        secondCaller.invoke(i -> i.dApp(governanceDapp).function("claimAndWithdrawSWOP"));

        assertAll("state after second claimAndWithdraw lock check",
                () -> assertThat(governanceDapp.getIntegerData(secondCaller.address() + keyUserLastInterest)).isEqualTo(newInterest),
                () -> assertThat(governanceDapp.getAssetBalance(swopId)).isEqualTo(governanceSWOPBalance2 - user2ClaimAmount),
                () -> assertThat(secondCaller.getAssetBalance(swopId)).isEqualTo(user2SWOPBalance - user2LockAmount + user2ClaimAmount));
    }

    Stream<Arguments> claimStakeProvider() {

        return Stream.of(
                Arguments.of(100, 100),
                Arguments.of(1000, 100000000),
                Arguments.of(50000, 20000000),
                Arguments.of(60000000, 500),
                Arguments.of(10000000000L, 2000000000000L),
                Arguments.of(300000000000L, 10000000000L));
    }
    @ParameterizedTest(name = "user1 lock {0} SWOP -> user2 lock {0} SWOP -> airdrop {2} SWOP -> user1 claimAndStake -> user2 claimAndStake")
    @MethodSource("lockAndAirdropProvider")
    void c_lockAirdropClaimStake(long user1LockAmount, long user2LockAmount) {
        long airdropAmount = 1000000L;
        long user1SWOPBalance = firstCaller.getAssetBalance(swopId);
        long user2SWOPBalance = secondCaller.getAssetBalance(swopId);
        long user1SWOPAmount = governanceDapp.getIntegerData(firstCaller.address() + keyUserSWOPAmount);
        long user2SWOPAmount = governanceDapp.getIntegerData(secondCaller.address() + keyUserSWOPAmount);
        long totalSWOPAmount = governanceDapp.getIntegerData(keyTotalSWOPAmount);
        long governanceSWOPBalance = governanceDapp.getAssetBalance(swopId);

        firstCaller.invoke(i -> i.dApp(governanceDapp).function("lockSWOP").payment(user1LockAmount, swopId));
        secondCaller.invoke(i -> i.dApp(governanceDapp).function("lockSWOP").payment(user2LockAmount, swopId));

        long lastInterest =governanceDapp.getIntegerData(keyLastInterest);

        long newInterest = calcNewInterest(lastInterest, airdropAmount, totalSWOPAmount + user1LockAmount + user2LockAmount);
        airdropCaller.invoke(i -> i.dApp(governanceDapp).function("airDrop").payment(airdropAmount, swopId));

        long user1SWOPLocked = governanceDapp.getIntegerData(firstCaller.address() + keyUserSWOPAmount);
        long user1lastInterest = governanceDapp.getIntegerData(firstCaller.address() + keyUserLastInterest);
        long user1ClaimAmount = BigInteger.valueOf(user1SWOPLocked)
                .multiply(BigInteger.valueOf(newInterest - user1lastInterest))
                .divide(BigInteger.valueOf(100000000L)).longValue();
        long governanceSWOPBalance1 = governanceDapp.getAssetBalance(swopId);
        long totalSWOPAmount1 = governanceDapp.getIntegerData(keyTotalSWOPAmount);

        firstCaller.invoke(i -> i.dApp(governanceDapp).function("claimAndStakeSWOP"));
        assertAll("state after first user claimAndStake check",
                () -> assertThat(governanceDapp.getData()).contains(
                        IntegerEntry.as(firstCaller.address() + keyUserLastInterest, newInterest),
                        IntegerEntry.as(firstCaller.address() + keyUserSWOPAmount, user1SWOPLocked + user1ClaimAmount),
                        IntegerEntry.as(keyTotalSWOPAmount, totalSWOPAmount1 + user1ClaimAmount)),
                () -> assertThat(firstCaller.getAssetBalance(swopId)).isEqualTo(user1SWOPBalance - user1LockAmount));

        long user2SWOPLocked = governanceDapp.getIntegerData(secondCaller.address() + keyUserSWOPAmount);
        long user2LastInterest = governanceDapp.getIntegerData(secondCaller.address() + keyUserLastInterest);
        long user2ClaimAmount = BigInteger.valueOf(user2SWOPLocked)
                .multiply(BigInteger.valueOf(newInterest - user2LastInterest))
                .divide(BigInteger.valueOf(100000000L)).longValue();
        long governanceSWOPBalance2 = governanceDapp.getAssetBalance(swopId);
        long totalSWOPAmount2 = governanceDapp.getIntegerData(keyTotalSWOPAmount);

        secondCaller.invoke(i -> i.dApp(governanceDapp).function("claimAndStakeSWOP"));

        assertAll("state after second user claimAndStake check",
                () -> assertThat(governanceDapp.getData()).contains(
                        IntegerEntry.as(secondCaller.address() + keyUserLastInterest, newInterest),
                        IntegerEntry.as(secondCaller.address() + keyUserSWOPAmount, user2SWOPLocked + user2ClaimAmount),
                        IntegerEntry.as(keyTotalSWOPAmount, totalSWOPAmount2 + user2ClaimAmount)),
                () -> assertThat(secondCaller.getAssetBalance(swopId)).isEqualTo(user2SWOPBalance - user2LockAmount));
    }

    Stream<Arguments> withdrawSWOPProvider() {
        return Stream.of(
                Arguments.of(1, 1),
                Arguments.of(1000, 50000),
                Arguments.of(70000, 1000000),
                Arguments.of(100000000, 100000));
    }

    @ParameterizedTest(name = "user1 withdraw {0} SWOP, user2 withdraw {1} SWOP")
    @MethodSource("withdrawSWOPProvider")
    void d_withdrawSWOP(long user1WithdrawAmount, long user2WithdrawAmount) {
        node().waitNBlocks(1);
        long user1SWOPBalance = firstCaller.getAssetBalance(swopId);
        long user2SWOPBalance = secondCaller.getAssetBalance(swopId);
        long user1SWOPAmount = governanceDapp.getIntegerData(firstCaller.address() + keyUserSWOPAmount);
        long user1Interest = governanceDapp.getIntegerData(firstCaller.address() + keyUserLastInterest);
        long user2SWOPAmount = governanceDapp.getIntegerData(secondCaller.address() + keyUserSWOPAmount);
        long user2Interest = governanceDapp.getIntegerData(secondCaller.address() + keyUserLastInterest);
        long governanceSWOPBalance = governanceDapp.getAssetBalance(swopId);
        long totalSWOPAmount = governanceDapp.getIntegerData(keyTotalSWOPAmount);
        long lastInterest = governanceDapp.getIntegerData(keyLastInterest);


        firstCaller.invoke(i -> i.dApp(governanceDapp).function("withdrawSWOP", IntegerArg.as(user1WithdrawAmount)));

        assertAll("state after first user withdraw check",
                () -> assertThat(governanceDapp.getData()).contains(
                        IntegerEntry.as(firstCaller.address() + keyUserLastInterest, user1Interest),
                        IntegerEntry.as(firstCaller.address() + keyUserSWOPAmount, user1SWOPAmount - user1WithdrawAmount),
                        IntegerEntry.as(keyTotalSWOPAmount, totalSWOPAmount - user1WithdrawAmount)),
                () -> assertThat(governanceDapp.getAssetBalance(swopId)).isEqualTo(governanceSWOPBalance - user1WithdrawAmount),
                () -> assertThat(firstCaller.getAssetBalance(swopId)).isEqualTo(user1SWOPBalance + user1WithdrawAmount));

        secondCaller.invoke(i -> i.dApp(governanceDapp).function("withdrawSWOP", IntegerArg.as(user2WithdrawAmount)));

        assertAll("state after second user withdraw check",
                () -> assertThat(governanceDapp.getData()).contains(
                        IntegerEntry.as(secondCaller.address() + keyUserLastInterest, user2Interest),
                        IntegerEntry.as(secondCaller.address() + keyUserSWOPAmount, user2SWOPAmount - user2WithdrawAmount),
                        IntegerEntry.as(keyTotalSWOPAmount, totalSWOPAmount - user1WithdrawAmount - user2WithdrawAmount)),
                () -> assertThat(governanceDapp.getAssetBalance(swopId)).isEqualTo(governanceSWOPBalance - user1WithdrawAmount - user2WithdrawAmount),
                () -> assertThat(secondCaller.getAssetBalance(swopId)).isEqualTo(user2SWOPBalance + user2WithdrawAmount));
    }


    private long calcNewInterest(long lastInterest, long airdropAmount, long totalSWOPAmount) {
        return lastInterest + BigInteger.valueOf(airdropAmount)
                .multiply(BigInteger.valueOf(scaleValue))
                .divide(BigInteger.valueOf(totalSWOPAmount)).longValue();
    }
}