package SWOP;

import com.wavesplatform.crypto.Crypto;
import com.wavesplatform.transactions.account.PrivateKey;
import com.wavesplatform.transactions.common.Amount;
import dapps.GovernanceDApp;
import dapps.VotingDApp;
import im.mak.paddle.Account;
import com.wavesplatform.transactions.data.IntegerEntry;
import im.mak.paddle.token.Asset;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigInteger;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import static im.mak.paddle.token.Waves.WAVES;
import static im.mak.paddle.util.Async.async;
import static im.mak.paddle.Node.node;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@TestMethodOrder(MethodOrderer.Alphanumeric.class)
public class GovernanceTest {
    final long scaleValue = (long) Math.pow(10, 8);
    String keyLastInterest = "last_interest";
    String keyUserLastInterest = "_last_interest";
    String keyUserSWOPAmount = "_SWOP_amount";
    String keyTotalSWOPAmount = "total_SWOP_amount";

    static final String kBasePeriod = "base_period";
    static final String kStartHeight = "start_height";
    static final String kPeriodLength = "period_length";
    static Account pool, firstCaller, secondCaller, airdropCaller, farming;
    static VotingDApp voting;
    static GovernanceDApp governance;
    static Asset SWOP;

    @BeforeAll
    static void before() {
        PrivateKey votingPK = PrivateKey.fromSeed(Crypto.getRandomSeedBytes());
        PrivateKey governancePK = PrivateKey.fromSeed(Crypto.getRandomSeedBytes());

        async(
                () -> pool = new Account(WAVES.amount(1000)),
                () -> firstCaller = new Account(WAVES.amount(1000)),
                () -> secondCaller = new Account(WAVES.amount(1000)),
                () -> airdropCaller = new Account(WAVES.amount(1000)),
                () -> farming = new Account(WAVES.amount(1000))
        );
        SWOP = Asset.as(firstCaller.issue(a -> a.quantity(Long.MAX_VALUE).name("SWOP").decimals(6)).tx().assetId());
        async(
                () -> firstCaller.massTransfer(t -> t.assetId(SWOP)
                            .to(secondCaller, Long.MAX_VALUE / 3)
                            .to(airdropCaller, Long.MAX_VALUE / 3)),
                () -> farming.writeData(d -> d.string("SWOP_id", SWOP.toString())),
                () -> voting = new VotingDApp(votingPK, WAVES.amount(1000), governancePK.address()),
                () -> governance = new GovernanceDApp(governancePK, WAVES.amount(1000), farming.publicKey(), votingPK.address()),
                () -> governance.writeData(d -> d.data(
                        IntegerEntry.as(kBasePeriod, 0),
                        IntegerEntry.as(kPeriodLength, 10102_000000000L),
                        IntegerEntry.as(kStartHeight, 0)
                )),
                () -> voting.writeData(d -> d.data(
                        IntegerEntry.as(kBasePeriod, 0),
                        IntegerEntry.as(kPeriodLength, 10102_000000000L),
                        IntegerEntry.as(kStartHeight, 0)
                ))
        );
    }

    static Stream<Arguments> lockSWOPProvider() {
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
        long user1SWOPBalance = firstCaller.getAssetBalance(SWOP);
        long user2SWOPBalance = secondCaller.getAssetBalance(SWOP);
        long user1SWOPAmount;
        long user2SWOPAmount;
        long totalSWOPAmount;
        if (user1LockAmount == 1) {
            user1SWOPAmount = 0;
            user2SWOPAmount = 0;
            totalSWOPAmount = 0;
        } else {
            user1SWOPAmount = governance.getIntegerData(firstCaller.address() + keyUserSWOPAmount);
            user2SWOPAmount = governance.getIntegerData(secondCaller.address() + keyUserSWOPAmount);
            totalSWOPAmount = governance.getIntegerData(keyTotalSWOPAmount);
        }

        long lastInterest = 0;
        firstCaller.invoke(governance.lockSWOP(), Amount.of(user1LockAmount, SWOP));

        assertAll("state after first user lock check",
                () -> assertThat(governance.getData()).contains(
                        IntegerEntry.as(firstCaller.address() + keyUserLastInterest, lastInterest),
                        IntegerEntry.as(firstCaller.address() + keyUserSWOPAmount, user1SWOPAmount + user1LockAmount),
                        IntegerEntry.as(keyTotalSWOPAmount, totalSWOPAmount + user1LockAmount)),
                () -> assertThat(governance.getAssetBalance(SWOP)).isEqualTo(totalSWOPAmount + user1LockAmount),
                () -> assertThat(firstCaller.getAssetBalance(SWOP)).isEqualTo(user1SWOPBalance - user1LockAmount));

        secondCaller.invoke(governance.lockSWOP(), Amount.of(user2LockAmount, SWOP));

        assertAll("state after second user lock check",
                () -> assertThat(governance.getData()).contains(
                        IntegerEntry.as(secondCaller.address() + keyUserLastInterest, lastInterest),
                        IntegerEntry.as(secondCaller.address() + keyUserSWOPAmount, user2SWOPAmount + user2LockAmount),
                        IntegerEntry.as(keyTotalSWOPAmount, totalSWOPAmount + user1LockAmount + user2LockAmount)),
                () -> assertThat(governance.getAssetBalance(SWOP)).isEqualTo(totalSWOPAmount + user1LockAmount + user2LockAmount),
                () -> assertThat(secondCaller.getAssetBalance(SWOP)).isEqualTo(user2SWOPBalance - user2LockAmount));
    }

    static Stream<Arguments> lockAndAirdropProvider() {

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
        long user1SWOPBalance = firstCaller.getAssetBalance(SWOP);
        long user2SWOPBalance = secondCaller.getAssetBalance(SWOP);
        long user1SWOPAmount = governance.getIntegerData(firstCaller.address() + keyUserSWOPAmount);
        long user2SWOPAmount = governance.getIntegerData(secondCaller.address() + keyUserSWOPAmount);
        long totalSWOPAmount = governance.getIntegerData(keyTotalSWOPAmount);
        long governanceSWOPBalance = governance.getAssetBalance(SWOP);

        firstCaller.invoke(governance.lockSWOP(), Amount.of(user1LockAmount, SWOP));
        secondCaller.invoke(governance.lockSWOP(), Amount.of(user2LockAmount, SWOP));

        long lastInterest = user1LockAmount == 100L ? 0 : governance.getIntegerData(keyLastInterest);
        long newInterest = calcNewInterest(lastInterest, airdropAmount, totalSWOPAmount + user1LockAmount + user2LockAmount);
        airdropCaller.invoke(governance.airDrop(), Amount.of(airdropAmount, SWOP));

        assertAll("state after airdrop check",
                () -> assertThat(governance.getIntegerData(keyLastInterest)).isEqualTo(newInterest),
                () -> assertThat(governance.getIntegerData(keyTotalSWOPAmount)).isEqualTo(totalSWOPAmount + user1LockAmount + user2LockAmount),
                () -> assertThat(governance.getAssetBalance(SWOP)).isEqualTo(governanceSWOPBalance + user1LockAmount + user2LockAmount + airdropAmount));

        long user1SWOPLocked = governance.getIntegerData(firstCaller.address() + keyUserSWOPAmount);
        long user1lastInterest = governance.getIntegerData(firstCaller.address() + keyUserLastInterest);
        long user1ClaimAmount = BigInteger.valueOf(user1SWOPLocked)
                .multiply(BigInteger.valueOf(newInterest - user1lastInterest))
                .divide(BigInteger.valueOf(100000000L)).longValue();
        long governanceSWOPBalance1 = governance.getAssetBalance(SWOP);

        firstCaller.invoke(governance.claimAndWithdrawSWOP());

        assertAll("state after first user claimAndWithdraw check",
                () -> assertThat(governance.getIntegerData(firstCaller.address() + keyUserLastInterest)).isEqualTo(newInterest),
                () -> assertThat(governance.getAssetBalance(SWOP)).isEqualTo(governanceSWOPBalance1 - user1ClaimAmount),
                () -> assertThat(firstCaller.getAssetBalance(SWOP)).isEqualTo(user1SWOPBalance - user1LockAmount + user1ClaimAmount));

        long user2SWOPLocked = governance.getIntegerData(secondCaller.address() + keyUserSWOPAmount);
        long user2LastInterest = governance.getIntegerData(secondCaller.address() + keyUserLastInterest);
        long user2ClaimAmount = BigInteger.valueOf(user2SWOPLocked)
                .multiply(BigInteger.valueOf(newInterest - user2LastInterest))
                .divide(BigInteger.valueOf(100000000L)).longValue();
        long governanceSWOPBalance2 = governance.getAssetBalance(SWOP);

        secondCaller.invoke(governance.claimAndWithdrawSWOP());

        assertAll("state after second claimAndWithdraw lock check",
                () -> assertThat(governance.getIntegerData(secondCaller.address() + keyUserLastInterest)).isEqualTo(newInterest),
                () -> assertThat(governance.getAssetBalance(SWOP)).isEqualTo(governanceSWOPBalance2 - user2ClaimAmount),
                () -> assertThat(secondCaller.getAssetBalance(SWOP)).isEqualTo(user2SWOPBalance - user2LockAmount + user2ClaimAmount));
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
        long user1SWOPBalance = firstCaller.getAssetBalance(SWOP);
        long user2SWOPBalance = secondCaller.getAssetBalance(SWOP);
        long user1SWOPAmount = governance.getIntegerData(firstCaller.address() + keyUserSWOPAmount);
        long user2SWOPAmount = governance.getIntegerData(secondCaller.address() + keyUserSWOPAmount);
        long totalSWOPAmount = governance.getIntegerData(keyTotalSWOPAmount);
        long governanceSWOPBalance = governance.getAssetBalance(SWOP);

        firstCaller.invoke(governance.lockSWOP(), Amount.of(user1LockAmount, SWOP));
        secondCaller.invoke(governance.lockSWOP(), Amount.of(user2LockAmount, SWOP));

        long lastInterest = governance.getIntegerData(keyLastInterest);

        long newInterest = calcNewInterest(lastInterest, airdropAmount, totalSWOPAmount + user1LockAmount + user2LockAmount);
        airdropCaller.invoke(governance.airDrop(), Amount.of(airdropAmount, SWOP));

        long user1SWOPLocked = governance.getIntegerData(firstCaller.address() + keyUserSWOPAmount);
        long user1lastInterest = governance.getIntegerData(firstCaller.address() + keyUserLastInterest);
        long user1ClaimAmount = BigInteger.valueOf(user1SWOPLocked)
                .multiply(BigInteger.valueOf(newInterest - user1lastInterest))
                .divide(BigInteger.valueOf(100000000L)).longValue();
        long governanceSWOPBalance1 = governance.getAssetBalance(SWOP);
        long totalSWOPAmount1 = governance.getIntegerData(keyTotalSWOPAmount);

        firstCaller.invoke(governance.claimAndStakeSWOP());
        assertAll("state after first user claimAndStake check",
                () -> assertThat(governance.getData()).contains(
                        IntegerEntry.as(firstCaller.address() + keyUserLastInterest, newInterest),
                        IntegerEntry.as(firstCaller.address() + keyUserSWOPAmount, user1SWOPLocked + user1ClaimAmount),
                        IntegerEntry.as(keyTotalSWOPAmount, totalSWOPAmount1 + user1ClaimAmount)),
                () -> assertThat(firstCaller.getAssetBalance(SWOP)).isEqualTo(user1SWOPBalance - user1LockAmount));

        long user2SWOPLocked = governance.getIntegerData(secondCaller.address() + keyUserSWOPAmount);
        long user2LastInterest = governance.getIntegerData(secondCaller.address() + keyUserLastInterest);
        long user2ClaimAmount = BigInteger.valueOf(user2SWOPLocked)
                .multiply(BigInteger.valueOf(newInterest - user2LastInterest))
                .divide(BigInteger.valueOf(100000000L)).longValue();
        long governanceSWOPBalance2 = governance.getAssetBalance(SWOP);
        long totalSWOPAmount2 = governance.getIntegerData(keyTotalSWOPAmount);

        secondCaller.invoke(governance.claimAndStakeSWOP());

        assertAll("state after second user claimAndStake check",
                () -> assertThat(governance.getData()).contains(
                        IntegerEntry.as(secondCaller.address() + keyUserLastInterest, newInterest),
                        IntegerEntry.as(secondCaller.address() + keyUserSWOPAmount, user2SWOPLocked + user2ClaimAmount),
                        IntegerEntry.as(keyTotalSWOPAmount, totalSWOPAmount2 + user2ClaimAmount)),
                () -> assertThat(secondCaller.getAssetBalance(SWOP)).isEqualTo(user2SWOPBalance - user2LockAmount));
    }

    static Stream<Arguments> withdrawSWOPProvider() {
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
        long user1SWOPBalance = firstCaller.getAssetBalance(SWOP);
        long user2SWOPBalance = secondCaller.getAssetBalance(SWOP);
        long user1SWOPAmount = governance.getIntegerData(firstCaller.address() + keyUserSWOPAmount);
        long user1Interest = governance.getIntegerData(firstCaller.address() + keyUserLastInterest);
        long user2SWOPAmount = governance.getIntegerData(secondCaller.address() + keyUserSWOPAmount);
        long user2Interest = governance.getIntegerData(secondCaller.address() + keyUserLastInterest);
        long governanceSWOPBalance = governance.getAssetBalance(SWOP);
        long totalSWOPAmount = governance.getIntegerData(keyTotalSWOPAmount);
        long lastInterest = governance.getIntegerData(keyLastInterest);

        firstCaller.invoke(governance.withdrawSWOP(user1WithdrawAmount));

        assertAll("state after first user withdraw check",
                () -> assertThat(governance.getData()).contains(
                        IntegerEntry.as(firstCaller.address() + keyUserLastInterest, user1Interest),
                        IntegerEntry.as(firstCaller.address() + keyUserSWOPAmount, user1SWOPAmount - user1WithdrawAmount),
                        IntegerEntry.as(keyTotalSWOPAmount, totalSWOPAmount - user1WithdrawAmount)),
                () -> assertThat(governance.getAssetBalance(SWOP)).isEqualTo(governanceSWOPBalance - user1WithdrawAmount),
                () -> assertThat(firstCaller.getAssetBalance(SWOP)).isEqualTo(user1SWOPBalance + user1WithdrawAmount));

        secondCaller.invoke(governance.withdrawSWOP(user2WithdrawAmount));

        assertAll("state after second user withdraw check",
                () -> assertThat(governance.getData()).contains(
                        IntegerEntry.as(secondCaller.address() + keyUserLastInterest, user2Interest),
                        IntegerEntry.as(secondCaller.address() + keyUserSWOPAmount, user2SWOPAmount - user2WithdrawAmount),
                        IntegerEntry.as(keyTotalSWOPAmount, totalSWOPAmount - user1WithdrawAmount - user2WithdrawAmount)),
                () -> assertThat(governance.getAssetBalance(SWOP)).isEqualTo(governanceSWOPBalance - user1WithdrawAmount - user2WithdrawAmount),
                () -> assertThat(secondCaller.getAssetBalance(SWOP)).isEqualTo(user2SWOPBalance + user2WithdrawAmount));
    }

    private long calcNewInterest(long lastInterest, long airdropAmount, long totalSWOPAmount) {
        return lastInterest + BigInteger.valueOf(airdropAmount)
                .multiply(BigInteger.valueOf(scaleValue))
                .divide(BigInteger.valueOf(totalSWOPAmount)).longValue();
    }

}
