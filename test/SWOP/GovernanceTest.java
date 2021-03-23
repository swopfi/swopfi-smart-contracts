package SWOP;

import im.mak.paddle.Account;
import im.mak.waves.transactions.common.AssetId;
import im.mak.waves.transactions.common.Id;
import im.mak.waves.transactions.invocation.IntegerArg;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import im.mak.waves.crypto.base.Base58;

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
    private Account pool = new Account(1000_00000000L);
    private Account firstCaller = new Account(1000_00000000L);
    private Account secondCaller = new Account(1000_00000000L);
    private Account airdropCaller = new Account(1000_00000000L);
    private Account governanceDapp = new Account(1000_00000000L);
    private Account farmingDapp = new Account(1000_00000000L);
    private AssetId swopId;
    private String dAppScript = fromFile("dApps/SWOP/governance.ride")
            .replace("3P73HDkPqG15nLXevjCbmXtazHYTZbpPoPw", farmingDapp.address().toString())
            .replace("DXDY2itiEcYBtGkVLnkpHtDFyWQUkoLJz79uJ7ECbMrA", Base58.encode(farmingDapp.publicKey().bytes()))
            .replace("E6Wa1SGoktYcjHjsKrvjMiqJY3SWmGKcD8Q5L8kxSPS7", Base58.encode(farmingDapp.publicKey().bytes()))
            .replace("AZmWJtuy4GeVrMmJH4hfFBRApe1StvhJSk4jcbT6bArQ", Base58.encode(farmingDapp.publicKey().bytes()));

    @BeforeAll
    void before() {
        async(
                () -> {
                    Id setScriptId = governanceDapp.setScript(s -> s.script(dAppScript)).tx().id();
                    node().waitForTransaction(setScriptId);
                },
                () -> {
                    swopId = firstCaller.issue(a -> a.quantity(Long.MAX_VALUE).name("SWOP").decimals(shareAssetDecimals)).tx().assetId();
                    node().waitForTransaction(swopId);
                    node().waitForTransaction(firstCaller.transfer(t -> t.amount(Long.MAX_VALUE / 3, swopId).to(secondCaller)).tx().id());
                    node().waitForTransaction(firstCaller.transfer(t -> t.amount(Long.MAX_VALUE / 3, swopId).to(airdropCaller)).tx().id());
                    node().waitForTransaction(farmingDapp.writeData(d -> d.string("SWOP_id", swopId.toString())).tx().id());
                }
        );
    }

    Stream<Arguments> lockSWOPProvider() {
        long firstRange = ThreadLocalRandom.current().nextLong(100L, 1000L);
        long secondRange = ThreadLocalRandom.current().nextLong(1000L, 100000L);
        long thirdRange = ThreadLocalRandom.current().nextLong(100000L, 100000000L);

        return Stream.of(
                Arguments.of(1, 1),
                Arguments.of(firstRange, thirdRange),
                Arguments.of(secondRange, thirdRange),
                Arguments.of(thirdRange, firstRange)
        );
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
        node().waitForTransaction(
                firstCaller.invoke(i -> i.dApp(governanceDapp)
                        .function("lockSWOP")
                        .payment(user1LockAmount, swopId).fee(500000L)).tx().id()
        );

        assertAll("state after first user lock check",
                () -> assertThat(governanceDapp.getIntegerData(firstCaller.address() + keyUserLastInterest)).isEqualTo(lastInterest),
                () -> assertThat(governanceDapp.getIntegerData(firstCaller.address() + keyUserSWOPAmount)).isEqualTo(user1SWOPAmount + user1LockAmount),
                () -> assertThat(governanceDapp.getIntegerData(keyTotalSWOPAmount)).isEqualTo(totalSWOPAmount + user1LockAmount),
                () -> assertThat(governanceDapp.getAssetBalance(swopId)).isEqualTo(totalSWOPAmount + user1LockAmount),
                () -> assertThat(firstCaller.getAssetBalance(swopId)).isEqualTo(user1SWOPBalance - user1LockAmount)
        );

        node().waitForTransaction(
                secondCaller.invoke(i -> i.dApp(governanceDapp)
                        .function("lockSWOP")
                        .payment(user2LockAmount, swopId).fee(500000L)).tx().id()
        );

        assertAll("state after second user lock check",
                () -> assertThat(governanceDapp.getIntegerData(secondCaller.address() + keyUserLastInterest)).isEqualTo(lastInterest),
                () -> assertThat(governanceDapp.getIntegerData(secondCaller.address() + keyUserSWOPAmount)).isEqualTo(user2SWOPAmount + user2LockAmount),
                () -> assertThat(governanceDapp.getIntegerData(keyTotalSWOPAmount)).isEqualTo(totalSWOPAmount + user1LockAmount + user2LockAmount),
                () -> assertThat(governanceDapp.getAssetBalance(swopId)).isEqualTo(totalSWOPAmount + user1LockAmount + user2LockAmount),
                () -> assertThat(secondCaller.getAssetBalance(swopId)).isEqualTo(user2SWOPBalance - user2LockAmount)
        );
    }

    Stream<Arguments> lockAndAirdropProvider() {

        return Stream.of(
                Arguments.of(100, 100, 100000),
                Arguments.of(1000, 100000000, 100),
                Arguments.of(50000, 20000000, 3000),
                Arguments.of(60000000, 500, 100000000),
                Arguments.of(10000000000L, 2000000000000L, 10000000000L),
                Arguments.of(300000000000L, 10000000000L, 10000000000000L)
        );
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

        node().waitForTransaction(
                firstCaller.invoke(i -> i.dApp(governanceDapp)
                        .function("lockSWOP")
                        .payment(user1LockAmount, swopId).fee(500000L)).tx().id()
        );

        node().waitForTransaction(
                secondCaller.invoke(i -> i.dApp(governanceDapp)
                        .function("lockSWOP")
                        .payment(user2LockAmount, swopId).fee(500000L)).tx().id()
        );

        long lastInterest;
        if (user1LockAmount == 100L) {
            lastInterest = 0;
        } else {
            lastInterest = governanceDapp.getIntegerData(keyLastInterest);
        }
        long newInterest = calcNewInterest(lastInterest, airdropAmount, totalSWOPAmount + user1LockAmount + user2LockAmount);
        node().waitForTransaction(
                airdropCaller.invoke(i -> i.dApp(governanceDapp)
                        .function("airDrop")
                        .payment(airdropAmount, swopId).fee(500000L)).tx().id()
        );

        assertAll("state after airdrop check",
                () -> assertThat(governanceDapp.getIntegerData(keyLastInterest)).isEqualTo(newInterest),
                () -> assertThat(governanceDapp.getIntegerData(keyTotalSWOPAmount)).isEqualTo(totalSWOPAmount + user1LockAmount + user2LockAmount),
                () -> assertThat(governanceDapp.getAssetBalance(swopId)).isEqualTo(governanceSWOPBalance + user1LockAmount + user2LockAmount + airdropAmount)
        );

        long user1SWOPLocked = governanceDapp.getIntegerData(firstCaller.address() + keyUserSWOPAmount);
        long user1lastInterest = governanceDapp.getIntegerData(firstCaller.address() + keyUserLastInterest);
        long user1ClaimAmount = BigInteger.valueOf(user1SWOPLocked)
                .multiply(BigInteger.valueOf(newInterest - user1lastInterest))
                .divide(BigInteger.valueOf(100000000L)).longValue();
        long governanceSWOPBalance1 = governanceDapp.getAssetBalance(swopId);

        node().waitForTransaction(
                firstCaller.invoke(i -> i.dApp(governanceDapp)
                        .function("claimAndWithdrawSWOP")
                        .fee(500000L)).tx().id()
        );

        assertAll("state after first user claimAndWithdraw check",
                () -> assertThat(governanceDapp.getIntegerData(firstCaller.address() + keyUserLastInterest)).isEqualTo(newInterest),
                () -> assertThat(governanceDapp.getAssetBalance(swopId)).isEqualTo(governanceSWOPBalance1 - user1ClaimAmount),
                () -> assertThat(firstCaller.getAssetBalance(swopId)).isEqualTo(user1SWOPBalance - user1LockAmount + user1ClaimAmount)
        );

        long user2SWOPLocked = governanceDapp.getIntegerData(secondCaller.address() + keyUserSWOPAmount);
        long user2LastInterest = governanceDapp.getIntegerData(secondCaller.address() + keyUserLastInterest);
        long user2ClaimAmount = BigInteger.valueOf(user2SWOPLocked)
                .multiply(BigInteger.valueOf(newInterest - user2LastInterest))
                .divide(BigInteger.valueOf(100000000L)).longValue();
        long governanceSWOPBalance2 = governanceDapp.getAssetBalance(swopId);

        node().waitForTransaction(
                secondCaller.invoke(i -> i.dApp(governanceDapp)
                        .function("claimAndWithdrawSWOP")
                        .fee(500000L)).tx().id()
        );

        assertAll("state after second claimAndWithdraw lock check",
                () -> assertThat(governanceDapp.getIntegerData(secondCaller.address() + keyUserLastInterest)).isEqualTo(newInterest),
                () -> assertThat(governanceDapp.getAssetBalance(swopId)).isEqualTo(governanceSWOPBalance2 - user2ClaimAmount),
                () -> assertThat(secondCaller.getAssetBalance(swopId)).isEqualTo(user2SWOPBalance - user2LockAmount + user2ClaimAmount)
        );
    }

    Stream<Arguments> claimStakeProvider() {

        return Stream.of(
                Arguments.of(100, 100),
                Arguments.of(1000, 100000000),
                Arguments.of(50000, 20000000),
                Arguments.of(60000000, 500),
                Arguments.of(10000000000L, 2000000000000L),
                Arguments.of(300000000000L, 10000000000L)
        );
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

        node().waitForTransaction(
                firstCaller.invoke(i -> i.dApp(governanceDapp)
                        .function("lockSWOP")
                        .payment(user1LockAmount, swopId).fee(500000L)).tx().id()
        );

        node().waitForTransaction(
                secondCaller.invoke(i -> i.dApp(governanceDapp)
                        .function("lockSWOP")
                        .payment(user2LockAmount, swopId).fee(500000L)).tx().id()
        );

        long lastInterest =governanceDapp.getIntegerData(keyLastInterest);

        long newInterest = calcNewInterest(lastInterest, airdropAmount, totalSWOPAmount + user1LockAmount + user2LockAmount);
        node().waitForTransaction(
                airdropCaller.invoke(i -> i.dApp(governanceDapp)
                        .function("airDrop")
                        .payment(airdropAmount, swopId).fee(500000L)).tx().id()
        );

        long user1SWOPLocked = governanceDapp.getIntegerData(firstCaller.address() + keyUserSWOPAmount);
        long user1lastInterest = governanceDapp.getIntegerData(firstCaller.address() + keyUserLastInterest);
        System.out.println("newInterest" + newInterest);
        System.out.println("user1lastInterest" + user1lastInterest);
        System.out.println("user1SWOPLocked" + user1SWOPLocked);
        long user1ClaimAmount = BigInteger.valueOf(user1SWOPLocked)
                .multiply(BigInteger.valueOf(newInterest - user1lastInterest))
                .divide(BigInteger.valueOf(100000000L)).longValue();
        long governanceSWOPBalance1 = governanceDapp.getAssetBalance(swopId);
        long totalSWOPAmount1 = governanceDapp.getIntegerData(keyTotalSWOPAmount);

        node().waitForTransaction(
                firstCaller.invoke(i -> i.dApp(governanceDapp)
                        .function("claimAndStakeSWOP")
                        .fee(500000L)).tx().id()
        );
        System.out.println("user1SWOPLocked" + user1SWOPLocked);
        System.out.println("user1ClaimAmount" + user1ClaimAmount);
        assertAll("state after first user claimAndStake check",
                () -> assertThat(governanceDapp.getIntegerData(firstCaller.address() + keyUserLastInterest)).isEqualTo(newInterest),
                () -> assertThat(governanceDapp.getIntegerData(firstCaller.address() + keyUserSWOPAmount)).isEqualTo(user1SWOPLocked + user1ClaimAmount),
                () -> assertThat(governanceDapp.getIntegerData(keyTotalSWOPAmount)).isEqualTo(totalSWOPAmount1 + user1ClaimAmount),
                () -> assertThat(firstCaller.getAssetBalance(swopId)).isEqualTo(user1SWOPBalance - user1LockAmount)
        );

        long user2SWOPLocked = governanceDapp.getIntegerData(secondCaller.address() + keyUserSWOPAmount);
        long user2LastInterest = governanceDapp.getIntegerData(secondCaller.address() + keyUserLastInterest);
        long user2ClaimAmount = BigInteger.valueOf(user2SWOPLocked)
                .multiply(BigInteger.valueOf(newInterest - user2LastInterest))
                .divide(BigInteger.valueOf(100000000L)).longValue();
        long governanceSWOPBalance2 = governanceDapp.getAssetBalance(swopId);
        long totalSWOPAmount2 = governanceDapp.getIntegerData(keyTotalSWOPAmount);

        node().waitForTransaction(
                secondCaller.invoke(i -> i.dApp(governanceDapp)
                        .function("claimAndStakeSWOP")
                        .fee(500000L)).tx().id()
        );

        assertAll("state after second user claimAndStake check",
                () -> assertThat(governanceDapp.getIntegerData(secondCaller.address() + keyUserLastInterest)).isEqualTo(newInterest),
                () -> assertThat(governanceDapp.getIntegerData(secondCaller.address() + keyUserSWOPAmount)).isEqualTo(user2SWOPLocked + user2ClaimAmount),
                () -> assertThat(governanceDapp.getIntegerData(keyTotalSWOPAmount)).isEqualTo(totalSWOPAmount2 + user2ClaimAmount),
                () -> assertThat(secondCaller.getAssetBalance(swopId)).isEqualTo(user2SWOPBalance - user2LockAmount)
        );
    }

    Stream<Arguments> withdrawSWOPProvider() {
        return Stream.of(
                Arguments.of(1, 1),
                Arguments.of(1000, 50000),
                Arguments.of(70000, 1000000),
                Arguments.of(100000000, 100000)
        );
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


        node().waitForTransaction(
                firstCaller.invoke(i -> i.dApp(governanceDapp)
                        .function("withdrawSWOP", IntegerArg.as(user1WithdrawAmount)).fee(500000L)).tx().id()
        );

        assertAll("state after first user withdraw check",
                () -> assertThat(governanceDapp.getIntegerData(firstCaller.address() + keyUserLastInterest)).isEqualTo(user1Interest),
                () -> assertThat(governanceDapp.getIntegerData(firstCaller.address() + keyUserSWOPAmount)).isEqualTo(user1SWOPAmount - user1WithdrawAmount),
                () -> assertThat(governanceDapp.getIntegerData(keyTotalSWOPAmount)).isEqualTo(totalSWOPAmount - user1WithdrawAmount),
                () -> assertThat(governanceDapp.getAssetBalance(swopId)).isEqualTo(governanceSWOPBalance - user1WithdrawAmount),
                () -> assertThat(firstCaller.getAssetBalance(swopId)).isEqualTo(user1SWOPBalance + user1WithdrawAmount)
        );

        node().waitForTransaction(
                secondCaller.invoke(i -> i.dApp(governanceDapp)
                        .function("withdrawSWOP", IntegerArg.as(user2WithdrawAmount)).fee(500000L)).tx().id()
        );

        assertAll("state after second user withdraw check",
                () -> assertThat(governanceDapp.getIntegerData(secondCaller.address() + keyUserLastInterest)).isEqualTo(user2Interest),
                () -> assertThat(governanceDapp.getIntegerData(secondCaller.address() + keyUserSWOPAmount)).isEqualTo(user2SWOPAmount - user2WithdrawAmount),
                () -> assertThat(governanceDapp.getIntegerData(keyTotalSWOPAmount)).isEqualTo(totalSWOPAmount - user1WithdrawAmount - user2WithdrawAmount),
                () -> assertThat(governanceDapp.getAssetBalance(swopId)).isEqualTo(governanceSWOPBalance - user1WithdrawAmount - user2WithdrawAmount),
                () -> assertThat(secondCaller.getAssetBalance(swopId)).isEqualTo(user2SWOPBalance + user2WithdrawAmount)
        );
    }


    private long calcNewInterest(long lastInterest, long airdropAmount, long totalSWOPAmount) {
        return lastInterest + BigInteger.valueOf(airdropAmount)
                .multiply(BigInteger.valueOf(scaleValue))
                .divide(BigInteger.valueOf(totalSWOPAmount)).longValue();
    }
}
