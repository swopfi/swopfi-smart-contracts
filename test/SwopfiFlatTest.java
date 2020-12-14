import com.wavesplatform.wavesj.Base58;
import com.wavesplatform.wavesj.transactions.InvokeScriptTransaction;
import im.mak.paddle.Account;
import im.mak.paddle.exceptions.NodeError;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import ch.obermuhlner.math.big.BigDecimalMath;

import static im.mak.paddle.Async.async;
import static im.mak.paddle.Node.node;
import static im.mak.paddle.actions.invoke.Arg.arg;
import static im.mak.paddle.util.Script.fromFile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.Alphanumeric.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SwopfiFlatTest {
    private Account exchanger1, exchanger2, exchanger3, exchanger4, exchanger5, exchanger6, exchanger7;
    private int aDecimal = 6;
    private int bDecimal = 6;
    private int commission = 500;
    private int commissionGovernance = 200;
    private int commissionScaleDelimiter = 1000000;
    private int scaleValue8 = 100000000;
    private double alpha = 0.5;
    private double betta = 0.46;
    private String version = "2.0.0";
    private String shareTokenId;
    private String governanceAddress = "3MP9d7iovdAZtsPeRcq97skdsQH5MPEsfgm";
    private int minSponsoredAssetFee = 30000;
    private long stakingFee = 9 * minSponsoredAssetFee;
    private Account firstCaller = new Account(1000_00000000L);
    private Account secondCaller = new Account(1000_00000000L);
    private Account stakingAcc = new Account(1000_00000000L);
    private String tokenA = firstCaller.issues(a -> a.quantity(Long.MAX_VALUE).name("tokenA").decimals(aDecimal)).getId().getBase58String();
    private String tokenB = firstCaller.issues(a -> a.quantity(Long.MAX_VALUE).name("tokenB").decimals(bDecimal)).getId().getBase58String();
    private String dAppScript = fromFile("dApps/exchangerFlat.ride")
            .replace("3P6J84oH51DzY6xk2mT5TheXRbrCwBMxonp", governanceAddress)
            .replace("3PNikM6yp4NqcSU8guxQtmR5onr2D4e8yTJ", stakingAcc.address())
            .replace("DG2xFkPdDwKUoBkzGAhQtLpSGzfXLiCYPEzeKH2Ad24p", tokenB)
            .replace("DXDY2itiEcYBtGkVLnkpHtDFyWQUkoLJz79uJ7ECbMrA", Base58.encode(secondCaller.publicKey()))
            .replace("E6Wa1SGoktYcjHjsKrvjMiqJY3SWmGKcD8Q5L8kxSPS7", Base58.encode(secondCaller.publicKey()))
            .replace("AZmWJtuy4GeVrMmJH4hfFBRApe1StvhJSk4jcbT6bArQ", Base58.encode(secondCaller.publicKey()))
            .replace("EtVkT6ed8GtbUiVVEqdmEqsp2J4qbb3rre2HFgxeVYdg", Base58.encode(secondCaller.publicKey()))
            .replace("Czn4yoAuUZCVCLJDRfskn8URfkwpknwBTZDbs1wFrY7h", Base58.encode(secondCaller.publicKey()));


    @BeforeAll
    void before() {
        async(
                () -> {
                    exchanger1 = new Account(100_00000000L);
                    exchanger1.setsScript(s -> s.script(dAppScript));
                },
                () -> {
                    exchanger2 = new Account(100_00000000L);
                    exchanger2.setsScript(s -> s.script(dAppScript));
                },
                () -> {
                    exchanger3 = new Account(100_00000000L);
                    exchanger3.setsScript(s -> s.script(dAppScript));
                },
                () -> {
                    exchanger4 = new Account(100_00000000L);
                    exchanger4.setsScript(s -> s.script(dAppScript));
                },
                () -> {
                    exchanger5 = new Account(100_00000000L);
                    exchanger5.setsScript(s -> s.script(dAppScript));
                },
                () -> {
                    exchanger6 = new Account(100_00000000L);
                    exchanger6.setsScript(s -> s.script(dAppScript));
                },
                () -> {
                    exchanger7 = new Account(100_00000000L);
                    exchanger7.setsScript(s -> s.script(dAppScript));
                },
                () -> {
                    String sponsorTxId = firstCaller.sponsors(s -> s.amountForMinFee(minSponsoredAssetFee).asset(tokenB)).getId().getBase58String();
                    node().waitForTransaction(sponsorTxId);
                },
                () -> {
                    String transfer1 = firstCaller.transfers(t -> t
                            .to(secondCaller)
                            .amount(10000000_00000000L)
                            .asset(tokenA)).getId().getBase58String();
                    String transfer2 = firstCaller.transfers(t -> t
                            .to(secondCaller)
                            .amount(10000000_00000000L)
                            .asset(tokenB)).getId().getBase58String();
                    node().waitForTransaction(transfer1);
                    node().waitForTransaction(transfer2);
                }
        );
    }

    Stream<Arguments> fundProvider() {
        return Stream.of(
                Arguments.of(exchanger1, 1, 1),
                Arguments.of(exchanger2, 80, 80),
                Arguments.of(exchanger3, 140, 140),
                Arguments.of(exchanger4, 2122, 2122),
                Arguments.of(exchanger5, 21234, 21234),
                Arguments.of(exchanger6, 212345, 212345),
                Arguments.of(exchanger7, 9999999, 9999999)
        );
    }

    @ParameterizedTest(name = "caller inits {index} exchanger with {1} tokenA and {2} tokenB")
    @MethodSource("fundProvider")
    void a_canFundAB(Account exchanger, int x, int y) {
        node().waitForTransaction(tokenA);
        node().waitForTransaction(tokenB);

        long fundAmountA = x * (long) Math.pow(10, aDecimal);
        long fundAmountB = y * (long) Math.pow(10, bDecimal);

        int digitsInShareToken = (aDecimal + bDecimal) / 2;
        String invokeId = firstCaller.invokes(i -> i.dApp(exchanger).function("init").payment(fundAmountA, tokenA).payment(fundAmountB, tokenB).fee(1_00500000L)).getId().getBase58String();
        node().waitForTransaction(invokeId);
        node().waitNBlocks(1);
        long shareTokenSupply = (long) (((new BigDecimal(Math.pow(fundAmountA / Math.pow(10, aDecimal), 0.5)).setScale(aDecimal, RoundingMode.HALF_DOWN).movePointRight(aDecimal).doubleValue() *
                new BigDecimal(Math.pow(fundAmountB / Math.pow(10, bDecimal), 0.5)).setScale(bDecimal, RoundingMode.HALF_DOWN).movePointRight(bDecimal).doubleValue()) / Math.pow(10, digitsInShareToken)));
        shareTokenId = exchanger.dataStr("share_asset_id");

        assertAll("data and balances",
                () -> assertThat(exchanger.dataInt("A_asset_balance")).isEqualTo(fundAmountA),
                () -> assertThat(exchanger.dataInt("B_asset_balance")).isEqualTo(fundAmountB),
                () -> assertThat(exchanger.balance(tokenA)).isEqualTo(fundAmountA),
                () -> assertThat(exchanger.balance(tokenB)).isEqualTo(fundAmountB),
                () -> assertThat(exchanger.dataStr("A_asset_id")).isEqualTo(tokenA),
                () -> assertThat(exchanger.dataStr("B_asset_id")).isEqualTo(tokenB),
                () -> assertThat(exchanger.dataInt("invariant")).isEqualTo(invariantCalc(fundAmountA, fundAmountB).longValue()),
                () -> assertThat(exchanger.dataBool("active")).isTrue(),
                () -> assertThat(exchanger.dataInt("commission")).isEqualTo(commission),
                () -> assertThat(exchanger.dataInt("commission_scale_delimiter")).isEqualTo(commissionScaleDelimiter),
                () -> assertThat(exchanger.dataStr("version")).isEqualTo("2.0.0"),
                () -> assertThat(exchanger.dataStr("share_asset_id")).isNotNull(),
                () -> assertThat(exchanger.dataInt("share_asset_supply")).isEqualTo(shareTokenSupply),
                () -> assertThat(firstCaller.balance(shareTokenId)).isEqualTo(shareTokenSupply)

        );
    }

    Stream<Arguments> aExchangeProvider() {
        return Stream.of(
                Arguments.of(exchanger2, ThreadLocalRandom.current().nextLong(100L, 1000L)),
                Arguments.of(exchanger2, ThreadLocalRandom.current().nextLong(1000L, 400000L)),
                Arguments.of(exchanger5, ThreadLocalRandom.current().nextLong(100L, 1_000000L)),
                Arguments.of(exchanger5, ThreadLocalRandom.current().nextLong(10_000000L, 100_000000L)),
                Arguments.of(exchanger5, ThreadLocalRandom.current().nextLong(100_000000L, 10000_000000L)),
                Arguments.of(exchanger6, ThreadLocalRandom.current().nextLong(100L, 1_000000L)),
                Arguments.of(exchanger6, ThreadLocalRandom.current().nextLong(10_000000L, 100_000000L)),
                Arguments.of(exchanger6, ThreadLocalRandom.current().nextLong(100_000000L, 10000_000000L)),
                Arguments.of(exchanger7, ThreadLocalRandom.current().nextLong(1_000000L, 10_000000L)),
                Arguments.of(exchanger7, ThreadLocalRandom.current().nextLong(10_000000L, 100_000000L)),
                Arguments.of(exchanger7, ThreadLocalRandom.current().nextLong(100_000000L, 10000_000000L))
        );
    }

    @ParameterizedTest(name = "firstCaller exchanges {1} tokenA")
    @MethodSource("aExchangeProvider")
    void b_canExchangeA(Account exchanger, long tokenReceiveAmount) {
        shareTokenId = exchanger.dataStr("share_asset_id");
        long amountTokenA = exchanger.dataInt("A_asset_balance");
        long amountTokenB = exchanger.dataInt("B_asset_balance");
        long invariant = exchanger.dataInt("invariant");
        long shareTokenSuplyBefore = exchanger.dataInt("share_asset_supply");
        long amountSendEstimated = amountToSendEstimated(amountTokenA, amountTokenB, amountTokenA + tokenReceiveAmount);
        long minTokenReceiveAmount = amountSendEstimated;
        long tokenSendAmountWithoutFee = calculateHowManySendTokenB(amountSendEstimated, minTokenReceiveAmount, amountTokenA, amountTokenB, tokenReceiveAmount, invariant);
        long tokenSendAmountWithFee = BigInteger.valueOf(tokenSendAmountWithoutFee).multiply(BigInteger.valueOf(commissionScaleDelimiter - commission)).divide(BigInteger.valueOf(commissionScaleDelimiter)).longValue();
        long tokenSendGovernance = tokenSendAmountWithoutFee * commissionGovernance / commissionScaleDelimiter;
        long invariantAfter;
        invariantAfter = invariantCalc(amountTokenA + tokenReceiveAmount, amountTokenB - tokenSendAmountWithFee - tokenSendGovernance).longValue();


        InvokeScriptTransaction invoke = firstCaller.invokes(i -> i.dApp(exchanger).function("exchange", arg(amountSendEstimated), arg(minTokenReceiveAmount)).payment(tokenReceiveAmount, tokenA).fee(1_00500000L));//.getId().getBase58String();
        node().waitForTransaction(invoke.getId().getBase58String());

        assertAll("data and balances",
                () -> assertThat(exchanger.dataInt("A_asset_balance")).isEqualTo(amountTokenA + tokenReceiveAmount),
                () -> assertThat(exchanger.dataInt("B_asset_balance")).isEqualTo(amountTokenB - tokenSendAmountWithFee - tokenSendGovernance),
                () -> assertThat(exchanger.balance(tokenA)).isEqualTo(amountTokenA + tokenReceiveAmount),
                () -> assertThat(exchanger.balance(tokenB)).isEqualTo(amountTokenB - tokenSendAmountWithFee - tokenSendGovernance),
                () -> assertThat(exchanger.dataStr("A_asset_id")).isEqualTo(tokenA),
                () -> assertThat(exchanger.dataStr("B_asset_id")).isEqualTo(tokenB),
                () -> assertThat(exchanger.dataBool("active")).isTrue(),
                () -> assertThat(exchanger.dataInt("commission")).isEqualTo(commission),
                () -> assertThat(exchanger.dataInt("commission_scale_delimiter")).isEqualTo(commissionScaleDelimiter),
                () -> assertThat(exchanger.dataInt("invariant")).isCloseTo(invariantAfter, within(2L)),
                () -> assertThat(exchanger.dataStr("version")).isEqualTo(version),
                () -> assertThat(exchanger.dataInt("share_asset_supply")).isEqualTo(shareTokenSuplyBefore)

        );

    }

    Stream<Arguments> bExchangeProvider() {
        return Stream.of(
                Arguments.of(exchanger2, ThreadLocalRandom.current().nextLong(1L, 100L)),
                Arguments.of(exchanger2, ThreadLocalRandom.current().nextLong(100L, 1000L)),
                Arguments.of(exchanger2, ThreadLocalRandom.current().nextLong(1000L, 400000L)),
                Arguments.of(exchanger5, ThreadLocalRandom.current().nextLong(100L, 1_000000L)),
                Arguments.of(exchanger5, ThreadLocalRandom.current().nextLong(10_000000L, 100_000000L)),
                Arguments.of(exchanger5, ThreadLocalRandom.current().nextLong(100_000000L, 10000_000000L)),
                Arguments.of(exchanger6, ThreadLocalRandom.current().nextLong(100L, 1_000000L)),
                Arguments.of(exchanger6, ThreadLocalRandom.current().nextLong(10_000000L, 100_000000L)),
                Arguments.of(exchanger6, ThreadLocalRandom.current().nextLong(100_000000L, 10000_000000L)),
                Arguments.of(exchanger7, ThreadLocalRandom.current().nextLong(1_000000L, 10_000000L)),
                Arguments.of(exchanger7, ThreadLocalRandom.current().nextLong(10_000000L, 100_000000L)),
                Arguments.of(exchanger7, ThreadLocalRandom.current().nextLong(100_000000L, 10000_000000L))
        );
    }
    @ParameterizedTest(name = "firstCaller exchanges {1} tokenB")
    @MethodSource("bExchangeProvider")
    void b_canExchangeB(Account exchanger, long tokenReceiveAmount) {
        shareTokenId = exchanger.dataStr("share_asset_id");
        long amountTokenA = exchanger.dataInt("A_asset_balance");
        long amountTokenB = exchanger.dataInt("B_asset_balance");
        long invariant = exchanger.dataInt("invariant");
        long shareTokenSuplyBefore = exchanger.dataInt("share_asset_supply");
        long amountSendEstimated = amountToSendEstimated(amountTokenB, amountTokenA, amountTokenB + tokenReceiveAmount);
        long minTokenReceiveAmount = amountSendEstimated;
        long tokenSendAmountWithoutFee = calculateHowManySendTokenA(amountSendEstimated, minTokenReceiveAmount, amountTokenA, amountTokenB, tokenReceiveAmount, invariant);
        long tokenSendAmountWithFee = BigInteger.valueOf(tokenSendAmountWithoutFee).multiply(BigInteger.valueOf(commissionScaleDelimiter - commission)).divide(BigInteger.valueOf(commissionScaleDelimiter)).longValue();
        long tokenSendGovernance = tokenSendAmountWithoutFee * commissionGovernance / commissionScaleDelimiter;


        InvokeScriptTransaction invoke = firstCaller.invokes(i -> i.dApp(exchanger).function("exchange", arg(amountSendEstimated), arg(minTokenReceiveAmount)).payment(tokenReceiveAmount, tokenB).fee(1_00500000L));//.getId().getBase58String();
        node().waitForTransaction(invoke.getId().getBase58String());
        long invariantAfter = invariantCalc(amountTokenA - tokenSendAmountWithFee - tokenSendGovernance, amountTokenB + tokenReceiveAmount).longValue();

        assertAll("data and balances",
                () -> assertThat(exchanger.dataInt("A_asset_balance")).isEqualTo(amountTokenA - tokenSendAmountWithFee - tokenSendGovernance),
                () -> assertThat(exchanger.dataInt("B_asset_balance")).isEqualTo(amountTokenB + tokenReceiveAmount),
                () -> assertThat(exchanger.balance(tokenA)).isEqualTo(amountTokenA - tokenSendAmountWithFee - tokenSendGovernance),
                () -> assertThat(exchanger.balance(tokenB)).isEqualTo(amountTokenB + tokenReceiveAmount),
                () -> assertThat(exchanger.dataStr("A_asset_id")).isEqualTo(tokenA),
                () -> assertThat(exchanger.dataStr("B_asset_id")).isEqualTo(tokenB),
                () -> assertThat(exchanger.dataBool("active")).isTrue(),
                () -> assertThat(exchanger.dataInt("commission")).isEqualTo(commission),
                () -> assertThat(exchanger.dataInt("commission_scale_delimiter")).isEqualTo(commissionScaleDelimiter),
                () -> assertThat(exchanger.dataInt("invariant")).isCloseTo(invariantAfter, within(2L)),
                () -> assertThat(exchanger.dataStr("version")).isEqualTo(version),
                () -> assertThat(exchanger.dataInt("share_asset_supply")).isEqualTo(shareTokenSuplyBefore)

        );

    }

    Stream<Arguments> replenishOneTokenAProvider() {
        return Stream.of(
                Arguments.of(exchanger5, ThreadLocalRandom.current().nextLong(5_000000L, 10_000000L)),
                Arguments.of(exchanger5, ThreadLocalRandom.current().nextLong(10_000000L, 100_000000L)),
                Arguments.of(exchanger5, ThreadLocalRandom.current().nextLong(100_000000L, 10000_000000L)),
                Arguments.of(exchanger6, ThreadLocalRandom.current().nextLong(5_000000L, 10_000000L)),
                Arguments.of(exchanger6, ThreadLocalRandom.current().nextLong(10_000000L, 100_000000L)),
                Arguments.of(exchanger6, ThreadLocalRandom.current().nextLong(100_000000L, 10000_000000L)),
                Arguments.of(exchanger7, ThreadLocalRandom.current().nextLong(5_000000L, 10_000000L)),
                Arguments.of(exchanger7, ThreadLocalRandom.current().nextLong(10_000000L, 100_000000L)),
                Arguments.of(exchanger7, ThreadLocalRandom.current().nextLong(100_000000L, 10000_000000L))
        );
    }
    @ParameterizedTest(name = "firstCaller replenish {1} tokenA")
    @MethodSource("replenishOneTokenAProvider")
    void d_firstCallerReplenishOneTokenA(Account exchanger, long pmtAmount) {
        long dAppTokensAmountA = exchanger.dataInt("A_asset_balance");
        long dAppTokensAmountB = exchanger.dataInt("B_asset_balance");

        long virtualSwapTokenPay = calculateVirtualPayGet(dAppTokensAmountA, dAppTokensAmountB, pmtAmount)[0];
        long virtualSwapTokenGet = calculateVirtualPayGet(dAppTokensAmountA, dAppTokensAmountB, pmtAmount)[1];
        long tokenShareSupply = exchanger.dataInt("share_asset_supply");
        shareTokenId = exchanger.dataStr("share_asset_id");
        long callerTokenShareBalance = firstCaller.balance(shareTokenId);
        long amountVirtualReplanishTokenA = pmtAmount - virtualSwapTokenPay;
        long amountVirtualReplanishTokenB = virtualSwapTokenGet;
        long contractBalanceAfterVirtualSwapTokenA = dAppTokensAmountA + virtualSwapTokenPay;
        long contractBalanceAfterVirtualSwapTokenB = dAppTokensAmountB - virtualSwapTokenGet;
        double ratioShareTokensInA = BigDecimal.valueOf(amountVirtualReplanishTokenA).multiply(BigDecimal.valueOf(scaleValue8)).divide(BigDecimal.valueOf(contractBalanceAfterVirtualSwapTokenA), 8, RoundingMode.HALF_DOWN).longValue();
        double ratioShareTokensInB = BigDecimal.valueOf(amountVirtualReplanishTokenB - stakingFee).multiply(BigDecimal.valueOf(scaleValue8)).divide(BigDecimal.valueOf(contractBalanceAfterVirtualSwapTokenB), 8, RoundingMode.HALF_DOWN).longValue();

        long shareTokenToPayAmount;
        if (ratioShareTokensInA <= ratioShareTokensInB) {
            shareTokenToPayAmount = BigDecimal.valueOf(ratioShareTokensInA).multiply(BigDecimal.valueOf(tokenShareSupply)).divide(BigDecimal.valueOf(scaleValue8), 8, RoundingMode.HALF_DOWN).longValue();
        } else {
            shareTokenToPayAmount = BigDecimal.valueOf(ratioShareTokensInB).multiply(BigDecimal.valueOf(tokenShareSupply)).divide(BigDecimal.valueOf(scaleValue8), 8, RoundingMode.HALF_DOWN).longValue();

        }
        long invariantCalculated = invariantCalc(dAppTokensAmountA + pmtAmount, dAppTokensAmountB).longValue();

        node().waitForTransaction(firstCaller.invokes(i -> i.dApp(exchanger).function("replenishWithOneToken", arg(virtualSwapTokenPay), arg(virtualSwapTokenGet)).payment(pmtAmount, tokenA).fee(1_00500000L)).getId().getBase58String());
        assertAll("data and balances",
                () -> assertThat(exchanger.dataInt("A_asset_balance")).isEqualTo(dAppTokensAmountA + pmtAmount),
                () -> assertThat(exchanger.dataInt("B_asset_balance")).isEqualTo(dAppTokensAmountB),
                () -> assertThat(exchanger.dataStr("A_asset_id")).isEqualTo(tokenA),
                () -> assertThat(exchanger.dataStr("B_asset_id")).isEqualTo(tokenB),
                () -> assertThat(exchanger.dataBool("active")).isTrue(),
                () -> assertThat(exchanger.dataInt("invariant")).isEqualTo(invariantCalculated),
                () -> assertThat(exchanger.dataInt("commission")).isEqualTo(commission),
                () -> assertThat(exchanger.dataInt("commission_scale_delimiter")).isEqualTo(commissionScaleDelimiter),
                () -> assertThat(exchanger.dataStr("version")).isEqualTo(version),
                () -> assertThat(exchanger.dataStr("share_asset_id")).isEqualTo(shareTokenId),
                () -> assertThat(exchanger.dataInt("share_asset_supply")).isEqualTo(tokenShareSupply + shareTokenToPayAmount),
                () -> assertThat(firstCaller.balance(shareTokenId)).isEqualTo(callerTokenShareBalance + shareTokenToPayAmount)

        );

    }

    Stream<Arguments> replenishOneTokenBProvider() {
        return Stream.of(
                Arguments.of(exchanger5, ThreadLocalRandom.current().nextLong(5_000000L, 10_000000L)),
                Arguments.of(exchanger5, ThreadLocalRandom.current().nextLong(10_000000L, 100_000000L)),
                Arguments.of(exchanger5, ThreadLocalRandom.current().nextLong(100_000000L, 10000_000000L)),
                Arguments.of(exchanger6, ThreadLocalRandom.current().nextLong(5_000000L, 10_000000L)),
                Arguments.of(exchanger6, ThreadLocalRandom.current().nextLong(10_000000L, 100_000000L)),
                Arguments.of(exchanger6, ThreadLocalRandom.current().nextLong(100_000000L, 10000_000000L)),
                Arguments.of(exchanger7, ThreadLocalRandom.current().nextLong(5_000000L, 10_000000L)),
                Arguments.of(exchanger7, ThreadLocalRandom.current().nextLong(10_000000L, 100_000000L)),
                Arguments.of(exchanger7, ThreadLocalRandom.current().nextLong(100_000000L, 10000_000000L))
        );
    }
    @ParameterizedTest(name = "firstCaller replenish {1} tokenB")
    @MethodSource("replenishOneTokenBProvider")
    void e_firstCallerReplenishOneTokenB(Account exchanger, long pmtAmount) {
        long dAppTokensAmountA = exchanger.dataInt("A_asset_balance");
        long dAppTokensAmountB = exchanger.dataInt("B_asset_balance");
        long virtualSwapTokenPay = calculateVirtualPayGet(dAppTokensAmountB, dAppTokensAmountA, pmtAmount)[0];
        long virtualSwapTokenGet = calculateVirtualPayGet(dAppTokensAmountB, dAppTokensAmountA, pmtAmount)[1];
        long tokenShareSupply = exchanger.dataInt("share_asset_supply");
        shareTokenId = exchanger.dataStr("share_asset_id");
        long callerTokenShareBalance = firstCaller.balance(shareTokenId);
        long amountVirtualReplanishTokenB = pmtAmount - virtualSwapTokenPay;
        long amountVirtualReplanishTokenA = virtualSwapTokenGet;
        long contractBalanceAfterVirtualSwapTokenA = dAppTokensAmountA - virtualSwapTokenGet;
        long contractBalanceAfterVirtualSwapTokenB = dAppTokensAmountB + virtualSwapTokenPay;

        double ratioShareTokensInA = BigDecimal.valueOf(amountVirtualReplanishTokenA).multiply(BigDecimal.valueOf(scaleValue8)).divide(BigDecimal.valueOf(contractBalanceAfterVirtualSwapTokenA), 8, RoundingMode.HALF_DOWN).longValue();
        System.out.println("ratioShareTokensInA" + ratioShareTokensInA);
        double ratioShareTokensInB = BigDecimal.valueOf(amountVirtualReplanishTokenB - stakingFee).multiply(BigDecimal.valueOf(scaleValue8)).divide(BigDecimal.valueOf(contractBalanceAfterVirtualSwapTokenB), 8, RoundingMode.HALF_DOWN).longValue();
        System.out.println("ratioShareTokensInB" + ratioShareTokensInB);

        long shareTokenToPayAmount;
        if (ratioShareTokensInA <= ratioShareTokensInB) {
            //fraction(ratioShareTokensInA,tokenShareSupply,scaleValue8)
            shareTokenToPayAmount = BigDecimal.valueOf(ratioShareTokensInA).multiply(BigDecimal.valueOf(tokenShareSupply)).divide(BigDecimal.valueOf(scaleValue8), 8, RoundingMode.HALF_DOWN).longValue();
        } else {
            //fraction(ratioShareTokensInB,tokenShareSupply,scaleValue8)
            shareTokenToPayAmount = BigDecimal.valueOf(ratioShareTokensInB).multiply(BigDecimal.valueOf(tokenShareSupply)).divide(BigDecimal.valueOf(scaleValue8), 8, RoundingMode.HALF_DOWN).longValue();

        }

        System.out.println("shareTokenToPayAmount: " + shareTokenToPayAmount);
        long invariantCalculated = invariantCalc(dAppTokensAmountA, dAppTokensAmountB + pmtAmount).longValue();

        node().waitForTransaction(firstCaller.invokes(i -> i.dApp(exchanger).function("replenishWithOneToken", arg(virtualSwapTokenPay), arg(virtualSwapTokenGet)).payment(pmtAmount, tokenB).fee(1_00500000L)).getId().getBase58String());
        assertAll("data and balances",
                () -> assertThat(exchanger.dataInt("A_asset_balance")).isEqualTo(dAppTokensAmountA),
                () -> assertThat(exchanger.dataInt("B_asset_balance")).isEqualTo(dAppTokensAmountB + pmtAmount),
                () -> assertThat(exchanger.dataStr("A_asset_id")).isEqualTo(tokenA),
                () -> assertThat(exchanger.dataStr("B_asset_id")).isEqualTo(tokenB),
                () -> assertThat(exchanger.dataBool("active")).isTrue(),
                () -> assertThat(exchanger.dataInt("invariant")).isEqualTo(invariantCalculated),
                () -> assertThat(exchanger.dataInt("commission")).isEqualTo(commission),
                () -> assertThat(exchanger.dataInt("commission_scale_delimiter")).isEqualTo(commissionScaleDelimiter),
                () -> assertThat(exchanger.dataStr("version")).isEqualTo(version),

                () -> assertThat(exchanger.dataStr("share_asset_id")).isEqualTo(shareTokenId),
                () -> assertThat(exchanger.dataInt("share_asset_supply")).isEqualTo(tokenShareSupply + shareTokenToPayAmount),
                () -> assertThat(firstCaller.balance(shareTokenId)).isEqualTo(callerTokenShareBalance + shareTokenToPayAmount)

        );
    }

    Stream<Arguments> replenishByTwiceProvider() {
        return Stream.of(
                Arguments.of(exchanger4),
                Arguments.of(exchanger5),
                Arguments.of(exchanger6),
                Arguments.of(exchanger7)
        );
    }

    @ParameterizedTest(name = "secondCaller replenish A/B by twice")
    @MethodSource("replenishByTwiceProvider")
    void e_secondCallerReplenishABByTwice(Account exchanger) {
        long amountTokenABefore = exchanger.dataInt("A_asset_balance");
        long amountTokenBBefore = exchanger.dataInt("B_asset_balance");
        long shareTokenSupplyBefore = exchanger.dataInt("share_asset_supply");
        long tokenReceiveAmountA = amountTokenABefore;
        long tokenReceiveAmountB = amountTokenBBefore;
        shareTokenId = exchanger.dataStr("share_asset_id");


        String invokeId = secondCaller.invokes(i -> i.dApp(exchanger).function("replenishWithTwoTokens", arg(1)).payment(tokenReceiveAmountA - stakingFee, tokenA).payment(tokenReceiveAmountB, tokenB).fee(1_00500000L)).getId().getBase58String();
        node().waitForTransaction(invokeId);
        double ratioShareTokensInA = BigDecimal.valueOf(amountTokenABefore - stakingFee).multiply(BigDecimal.valueOf(scaleValue8)).divide(BigDecimal.valueOf(amountTokenABefore), 8, RoundingMode.HALF_DOWN).longValue();
        double ratioShareTokensInB = BigDecimal.valueOf(amountTokenBBefore - stakingFee).multiply(BigDecimal.valueOf(scaleValue8)).divide(BigDecimal.valueOf(amountTokenBBefore), 8, RoundingMode.HALF_DOWN).longValue();

        long shareTokenToPay;
        if (ratioShareTokensInA <= ratioShareTokensInB) {
            shareTokenToPay = BigDecimal.valueOf(ratioShareTokensInA).multiply(BigDecimal.valueOf(shareTokenSupplyBefore)).divide(BigDecimal.valueOf(scaleValue8), 8, RoundingMode.HALF_DOWN).longValue();
        } else {
            shareTokenToPay = BigDecimal.valueOf(ratioShareTokensInB).multiply(BigDecimal.valueOf(shareTokenSupplyBefore)).divide(BigDecimal.valueOf(scaleValue8), 8, RoundingMode.HALF_DOWN).longValue();
        }

        assertAll("data and balances",
                () -> assertThat(exchanger.dataInt("A_asset_balance")).isEqualTo(amountTokenABefore + tokenReceiveAmountA - stakingFee),
                () -> assertThat(exchanger.dataInt("B_asset_balance")).isEqualTo(amountTokenBBefore + tokenReceiveAmountB - stakingFee),
                () -> assertThat(exchanger.balance(tokenA)).isEqualTo(amountTokenABefore + tokenReceiveAmountA - stakingFee),
                () -> assertThat(exchanger.balance(tokenB)).isEqualTo(amountTokenBBefore + tokenReceiveAmountB),
                () -> assertThat(exchanger.dataStr("A_asset_id")).isEqualTo(tokenA),
                () -> assertThat(exchanger.dataStr("B_asset_id")).isEqualTo(tokenB),
                () -> assertThat(exchanger.dataBool("active")).isTrue(),
                () -> assertThat(exchanger.dataInt("invariant")).isEqualTo(invariantCalc(amountTokenABefore + tokenReceiveAmountA - stakingFee, amountTokenBBefore + tokenReceiveAmountB - stakingFee).longValue()),
                () -> assertThat(exchanger.dataInt("commission")).isEqualTo(commission),
                () -> assertThat(exchanger.dataInt("commission_scale_delimiter")).isEqualTo(commissionScaleDelimiter),
                () -> assertThat(exchanger.dataStr("version")).isEqualTo(version),
                () -> assertThat(exchanger.dataStr("share_asset_id")).isEqualTo(shareTokenId),
                () -> assertThat(exchanger.dataInt("share_asset_supply")).isEqualTo(shareTokenSupplyBefore + shareTokenToPay),
                () -> assertThat(secondCaller.balance(shareTokenId)).isEqualTo(shareTokenToPay)

        );
    }

    @ParameterizedTest(name = "secondCaller withdraw A/B by twice")
    @MethodSource("replenishByTwiceProvider")
    void f_secondCallerWithdrawABByTwice(Account exchanger) {
        long amountTokenABefore = exchanger.balance(tokenA);
        long amountTokenBBefore = exchanger.balance(tokenB);
        long secondCallerAmountA = secondCaller.balance(tokenA);
        long secondCallerAmountB = secondCaller.balance(tokenB);
        long shareTokenSupply = exchanger.dataInt("share_asset_supply");
        shareTokenId = exchanger.dataStr("share_asset_id");
        long secondCallerShareBalance = secondCaller.balance(shareTokenId);
        long tokensToPayA =
                BigDecimal.valueOf(secondCallerShareBalance)
                        .multiply(BigDecimal.valueOf(amountTokenABefore))
                        .divide(BigDecimal.valueOf(shareTokenSupply), 8, RoundingMode.HALF_DOWN).longValue();

        long tokensToPayB =
                BigDecimal.valueOf(secondCallerShareBalance)
                        .multiply(BigDecimal.valueOf(amountTokenBBefore - stakingFee))
                        .divide(BigDecimal.valueOf(shareTokenSupply), 8, RoundingMode.HALF_DOWN).longValue();

        String invokeId = secondCaller.invokes(i -> i.dApp(exchanger).function("withdraw").payment(secondCallerShareBalance, shareTokenId).fee(1_00500000L)).getId().getBase58String();
        node().waitForTransaction(invokeId);
        node().waitNBlocks(1);

        assertAll("data and balances",
                () -> assertThat(exchanger.dataInt("A_asset_balance")).isEqualTo(amountTokenABefore - tokensToPayA),
                () -> assertThat(exchanger.dataInt("B_asset_balance")).isEqualTo(amountTokenBBefore - tokensToPayB),
                () -> assertThat(exchanger.balance(tokenA)).isEqualTo(amountTokenABefore - tokensToPayA),
                () -> assertThat(exchanger.balance(tokenB)).isEqualTo(amountTokenBBefore - tokensToPayB + stakingFee),
                () -> assertThat(exchanger.dataStr("A_asset_id")).isEqualTo(tokenA),
                () -> assertThat(exchanger.dataStr("B_asset_id")).isEqualTo(tokenB),
                () -> assertThat(exchanger.dataBool("active")).isTrue(),
                () -> assertThat(exchanger.dataInt("invariant")).isEqualTo(invariantCalc(amountTokenABefore - tokensToPayA, amountTokenBBefore - tokensToPayB).longValue()),
                () -> assertThat(exchanger.dataInt("commission")).isEqualTo(commission),
                () -> assertThat(exchanger.dataInt("commission_scale_delimiter")).isEqualTo(commissionScaleDelimiter),
                () -> assertThat(exchanger.dataStr("version")).isEqualTo(version),
                () -> assertThat(exchanger.dataStr("share_asset_id")).isEqualTo(shareTokenId),
                () -> assertThat(exchanger.dataInt("share_asset_supply")).isEqualTo(shareTokenSupply - secondCallerShareBalance),
                () -> assertThat(secondCaller.balance(tokenA)).isEqualTo(secondCallerAmountA + tokensToPayA),
                () -> assertThat(secondCaller.balance(tokenB)).isEqualTo(secondCallerAmountB + tokensToPayB - stakingFee)

        );
    }

    @Test
    void g_staking() {
        long balanceA = exchanger7.dataInt("A_asset_balance");
        long balanceB = exchanger7.dataInt("B_asset_balance");
        long tokenReceiveAmount = 1000000000L;
        long invariant = exchanger7.dataInt("invariant");
        node().waitForTransaction(stakingAcc.writes(d -> d.integer(String.format("rpd_balance_%s_%s", tokenB, exchanger7.address()), balanceB)).getId().getBase58String());
        long amountSendEstimated = amountToSendEstimated(balanceA, balanceB, balanceA + tokenReceiveAmount);
        long minTokenReceiveAmount = amountSendEstimated;
        long tokenSendAmountWithoutFee = calculateHowManySendTokenB(amountSendEstimated, minTokenReceiveAmount, balanceA, balanceB, tokenReceiveAmount, invariant);
        long tokenSendAmountWithFee = BigInteger.valueOf(tokenSendAmountWithoutFee).multiply(BigInteger.valueOf(commissionScaleDelimiter - commission)).divide(BigInteger.valueOf(commissionScaleDelimiter)).longValue();
        long tokenSendGovernance = tokenSendAmountWithoutFee * commissionGovernance / commissionScaleDelimiter;

        NodeError error = assertThrows(NodeError.class, () ->
                firstCaller.invokes(i -> i.dApp(exchanger7).function("exchange", arg(amountSendEstimated), arg(tokenSendAmountWithFee)).payment(tokenReceiveAmount, tokenA).fee(1_00500000L)));
        assertTrue(error.getMessage().contains(String.format("Error while executing account-script:" +
                " Insufficient DApp balance to pay %s tokenB due to staking. Available: 0 tokenB." +
                " Please contact support in Telegram: https://t.me/swopfisupport", tokenSendAmountWithFee)));

        node().waitForTransaction(stakingAcc.writes(d -> d.integer(String.format("rpd_balance_%s_%s", tokenB, exchanger7.address()), balanceB - tokenSendAmountWithFee - tokenSendGovernance - 1)).getId().getBase58String());
        node().waitForTransaction(firstCaller.invokes(i -> i.dApp(exchanger7).function("exchange", arg(amountSendEstimated), arg(tokenSendAmountWithFee)).payment(tokenReceiveAmount, tokenA).fee(1_00500000L)).getId().getBase58String());
    }

    @Test
    void h_canShutdown() {
        NodeError error = assertThrows(NodeError.class, () ->
                firstCaller.invokes(i -> i.dApp(exchanger1).function("shutdown").fee(900000L)));
        assertTrue(error.getMessage().contains("Only admin can call this function"));

        secondCaller.invokes(i -> i.dApp(exchanger1).function("shutdown").fee(900000L));
        assertThat(exchanger1.dataBool("active")).isFalse();
        assertThat(exchanger1.dataStr("shutdown_cause")).isEqualTo("Paused by admin");


        NodeError error1 = assertThrows(NodeError.class, () ->
                firstCaller.invokes(i -> i.dApp(exchanger1).function("shutdown").fee(900000L)));
        assertTrue(error1.getMessage().contains("DApp is already suspended. Cause: Paused by admin"));
    }

    @Test
    void i_canActivate() {
        NodeError error = assertThrows(NodeError.class, () ->
                firstCaller.invokes(i -> i.dApp(exchanger1).function("activate").fee(900000L)));
        assertTrue(error.getMessage().contains("Only admin can call this function"));

        secondCaller.invokes(i -> i.dApp(exchanger1).function("activate").fee(900000L));
        assertThat(exchanger1.dataBool("active")).isTrue();
        NodeError error1 = assertThrows(NodeError.class, () -> exchanger1.dataStr("shutdown_cause"));
        assertTrue(error1.getMessage().contains("no data for this key"));

        NodeError error2 = assertThrows(NodeError.class, () ->
                firstCaller.invokes(i -> i.dApp(exchanger1).function("activate").fee(900000L)));
        assertTrue(error2.getMessage().contains("DApp is already active"));
    }
//
//    @Test
//    void g_canShutdown() {
//        secondCaller.invokes(i -> i.dApp(firstExchanger).function("shutdown").fee(900000L));
//        assertThat(firstExchanger.dataBool("active")).isFalse();
//        assertThat(firstExchanger.dataStr("shutdown_cause")).isEqualTo("Paused by admin");
//
//        NodeError error = assertThrows(NodeError.class, () ->
//                firstCaller.invokes(i -> i.dApp(firstExchanger).function("shutdown").fee(900000L)));
//        assertTrue(error.getMessage().contains("Only admin can call this function"));
//    }
//
//    @Test
//    void h_canActivate() {
//        secondCaller.invokes(i -> i.dApp(firstExchanger).function("activate").fee(900000L));
//        assertThat(firstExchanger.dataBool("active")).isTrue();
//        NodeError error = assertThrows(NodeError.class, () -> firstExchanger.dataStr("shutdown_cause"));
//        assertTrue(error.getMessage().contains("no data for this key"));
//
//        NodeError error1 = assertThrows(NodeError.class, () ->
//                firstCaller.invokes(i -> i.dApp(firstExchanger).function("activate").fee(900000L)));
//        assertTrue(error1.getMessage().contains("Only admin can call this function"));
//    }

    private double skeweness(long x, long y) {
        return (BigDecimal.valueOf(x)
                .divide(BigDecimal.valueOf(y), 12, RoundingMode.DOWN))
                .add(BigDecimal.valueOf(y)
                        .divide(BigDecimal.valueOf(x), 12, RoundingMode.DOWN))
                .divide(BigDecimal.valueOf(2), 12, RoundingMode.DOWN).setScale(8, RoundingMode.DOWN).doubleValue();
    }

    private BigDecimal invariantCalc(long x, long y) {
        double sk = skeweness(x, y);
        long sk1 = (long) (skeweness(x, y) * scaleValue8);

        BigDecimal xySum = BigDecimal.valueOf(x).add(BigDecimal.valueOf(y));
        BigDecimal xySumMultScaleValue = xySum.multiply(BigDecimal.valueOf(scaleValue8));
        BigDecimal firstPow = BigDecimal.valueOf(Math.pow(sk / scaleValue8, alpha)).movePointRight(12).setScale(0, RoundingMode.UP);
        BigDecimal firstTerm = xySumMultScaleValue.divide(firstPow, 0, RoundingMode.DOWN);
        BigDecimal nestedFraction = (BigDecimal.valueOf(x).multiply(BigDecimal.valueOf(y))).divide(BigDecimal.valueOf(scaleValue8));
        BigDecimal secondPow = BigDecimalMath.sqrt(nestedFraction, new MathContext(20)).setScale(4, RoundingMode.DOWN).movePointRight(4);
        BigDecimal thirdPow = BigDecimal.valueOf(Math.pow(sk - betta, alpha)).setScale(8, RoundingMode.DOWN).movePointRight(8);
        BigDecimal fraction = secondPow.multiply(thirdPow).divide(BigDecimal.valueOf(scaleValue8)).setScale(0, RoundingMode.DOWN);
        return firstTerm.add(BigDecimal.valueOf(2).multiply(fraction)).setScale(0, RoundingMode.DOWN);
    }

    private long amountToSendEstimated(long x_balance, long y_balance, long x_balance_new) {
        long actual_invariant = invariantCalc(x_balance, y_balance).longValue();
        long y_left = 1;
        long y_right = 100 * y_balance;
        for (int i = 0; i < 50; i++) {
            long mean = (y_left + y_right) / 2;
            long invariant_delta_in_left = actual_invariant - invariantCalc(x_balance_new, y_left).longValue();
            long invariant_delta_in_right = actual_invariant - invariantCalc(x_balance_new, y_right).longValue();
            long invariant_delta_in_mean = actual_invariant - invariantCalc(x_balance_new, mean).longValue();

            if (BigInteger.valueOf(invariant_delta_in_mean).multiply(BigInteger.valueOf(invariant_delta_in_left)).signum() != 1) {
                y_left = y_left;
                y_right = mean;
            } else if (BigInteger.valueOf(invariant_delta_in_mean).multiply(BigInteger.valueOf(invariant_delta_in_right)).signum() != 1) {
                y_left = mean;
                y_right = y_right;
            } else {
                return y_balance - y_right - 2;
            }
        }
        return y_balance - y_right - 2;
    }

    private long calculateHowManySendTokenA(long amountToSendEstimated, long minTokenRecieveAmount, long amountTokenA, long amountTokenB, long tokenReceiveAmount, long invariant) {
        int slippageValue = scaleValue8 - scaleValue8 * 1 / 10000000;
        long deltaBetweenMaxAndMinSendValue = amountToSendEstimated - minTokenRecieveAmount;
        long amountToSendStep1 = amountToSendEstimated - 1 * deltaBetweenMaxAndMinSendValue / 5;
        long amountToSendStep2 = amountToSendEstimated - 2 * deltaBetweenMaxAndMinSendValue / 5;
        long amountToSendStep3 = amountToSendEstimated - 3 * deltaBetweenMaxAndMinSendValue / 5;
        long amountToSendStep4 = amountToSendEstimated - 4 * deltaBetweenMaxAndMinSendValue / 5;
        long amountToSendStep5 = amountToSendEstimated - 5 * deltaBetweenMaxAndMinSendValue / 5;

        long invariantEstimatedRatio = BigDecimal.valueOf(invariant).multiply(BigDecimal.valueOf(scaleValue8)).divide(invariantCalc(amountTokenA - amountToSendEstimated, amountTokenB + tokenReceiveAmount), 8, RoundingMode.HALF_DOWN).longValue();
        if (invariantEstimatedRatio > slippageValue && invariantEstimatedRatio < scaleValue8) {
            return amountToSendEstimated;
        } else {
            if (invariantCalc(amountTokenA - amountToSendStep1, amountTokenB + tokenReceiveAmount).longValue() - invariant > 0) {
                return amountToSendStep1 * (commissionScaleDelimiter - commission) / commissionScaleDelimiter;
            } else if (invariantCalc(amountTokenA - amountToSendStep2, amountTokenB + tokenReceiveAmount).longValue() - invariant > 0) {
                return amountToSendStep2 * (commissionScaleDelimiter - commission) / commissionScaleDelimiter;
            } else if (invariantCalc(amountTokenA - amountToSendStep3, amountTokenB + tokenReceiveAmount).longValue() - invariant > 0) {
                return amountToSendStep3 * (commissionScaleDelimiter - commission) / commissionScaleDelimiter;
            } else if (invariantCalc(amountTokenA - amountToSendStep4, amountTokenB + tokenReceiveAmount).longValue() - invariant > 0) {
                return amountToSendStep4 * (commissionScaleDelimiter - commission) / commissionScaleDelimiter;
            } else if (invariantCalc(amountTokenA - amountToSendStep5, amountTokenB + tokenReceiveAmount).longValue() - invariant > 0) {
                return amountToSendStep5 * (commissionScaleDelimiter - commission) / commissionScaleDelimiter;
            } else {
                return amountToSendStep5 * (commissionScaleDelimiter - commission) / commissionScaleDelimiter;
            }
        }
    }

    private long calculateHowManySendTokenB(long amountToSendEstimated, long minTokenRecieveAmount, long amountTokenA, long amountTokenB, long tokenReceiveAmount, long invariant) {
        int slippageValue = scaleValue8 - scaleValue8 * 1 / 10000000; // 0.000001% of slippage
        long deltaBetweenMaxAndMinSendValue = amountToSendEstimated - minTokenRecieveAmount;
        long amountToSendStep1 = amountToSendEstimated - 1 * deltaBetweenMaxAndMinSendValue / 5;
        long amountToSendStep2 = amountToSendEstimated - 2 * deltaBetweenMaxAndMinSendValue / 5;
        long amountToSendStep3 = amountToSendEstimated - 3 * deltaBetweenMaxAndMinSendValue / 5;
        long amountToSendStep4 = amountToSendEstimated - 4 * deltaBetweenMaxAndMinSendValue / 5;
        long amountToSendStep5 = amountToSendEstimated - 5 * deltaBetweenMaxAndMinSendValue / 5;

        long invariantEstimatedRatio = BigDecimal.valueOf(invariant).multiply(BigDecimal.valueOf(scaleValue8)).divide(invariantCalc(amountTokenA + tokenReceiveAmount, amountTokenB - amountToSendEstimated), 8, RoundingMode.HALF_DOWN).longValue();
        if (invariantEstimatedRatio > slippageValue && invariantEstimatedRatio < scaleValue8) {
            return amountToSendEstimated;
        } else {
            if (invariantCalc(amountTokenA - amountToSendStep1, amountTokenB + tokenReceiveAmount).longValue() - invariant > 0) {
                return amountToSendStep1 * (commissionScaleDelimiter - commission) / commissionScaleDelimiter;
            } else if (invariantCalc(amountTokenA - amountToSendStep2, amountTokenB + tokenReceiveAmount).longValue() - invariant > 0) {
                return amountToSendStep2 * (commissionScaleDelimiter - commission) / commissionScaleDelimiter;
            } else if (invariantCalc(amountTokenA - amountToSendStep3, amountTokenB + tokenReceiveAmount).longValue() - invariant > 0) {
                return amountToSendStep3 * (commissionScaleDelimiter - commission) / commissionScaleDelimiter;
            } else if (invariantCalc(amountTokenA - amountToSendStep4, amountTokenB + tokenReceiveAmount).longValue() - invariant > 0) {
                return amountToSendStep4 * (commissionScaleDelimiter - commission) / commissionScaleDelimiter;
            } else if (invariantCalc(amountTokenA - amountToSendStep5, amountTokenB + tokenReceiveAmount).longValue() - invariant > 0) {
                return amountToSendStep5 * (commissionScaleDelimiter - commission) / commissionScaleDelimiter;
            } else {
                return amountToSendStep5 * (commissionScaleDelimiter - commission) / commissionScaleDelimiter;
            }
        }
    }

    private long[] calculateVirtualPayGet(long x_balance, long y_balance, long deposit_in_x) {
        long x_left = 1;
        long x_right = deposit_in_x;
        long[] virtual_pay_get = new long[2];
        for (int i = 0; i < 50; i++) {
            long x_virtual_swap_pay = (x_left + x_right) / 2;
            long y_virtual_swap_get = amountToSendEstimated(x_balance, y_balance, x_balance + x_virtual_swap_pay);
            BigDecimal ratio_in_contract_after_virtual_swap = (BigDecimal.valueOf(x_balance).add(BigDecimal.valueOf(x_virtual_swap_pay))).divide(BigDecimal.valueOf(y_balance).subtract(BigDecimal.valueOf(y_virtual_swap_get)), 16, RoundingMode.HALF_DOWN);
            long x_to_deposit = deposit_in_x - x_virtual_swap_pay;
            long y_to_deposit = y_virtual_swap_get;
            BigDecimal ratio_virtual_replenish = BigDecimal.valueOf(x_to_deposit).divide(BigDecimal.valueOf(y_to_deposit), 20, RoundingMode.HALF_DOWN);

            if (ratio_in_contract_after_virtual_swap.subtract(ratio_virtual_replenish).signum() == 1) {
                x_left = x_left;
                x_right = x_virtual_swap_pay;
                virtual_pay_get[0] = x_virtual_swap_pay;
                virtual_pay_get[1] = y_virtual_swap_get;
            } else if (ratio_in_contract_after_virtual_swap.subtract(ratio_virtual_replenish).signum() == -1) {
                x_left = x_virtual_swap_pay;
                x_right = x_right;
                virtual_pay_get[0] = x_virtual_swap_pay;
                virtual_pay_get[1] = y_virtual_swap_get;
            } else if (ratio_in_contract_after_virtual_swap.subtract(ratio_virtual_replenish).signum() == 0) {
                virtual_pay_get[0] = x_virtual_swap_pay;
                virtual_pay_get[1] = y_virtual_swap_get;
            }
        }
        return virtual_pay_get;
    }
}