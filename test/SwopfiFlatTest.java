import im.mak.paddle.Account;
import im.mak.paddle.exceptions.ApiError;
import com.wavesplatform.transactions.common.AssetId;
import com.wavesplatform.transactions.data.BooleanEntry;
import com.wavesplatform.transactions.data.IntegerEntry;
import com.wavesplatform.transactions.data.StringEntry;
import com.wavesplatform.transactions.invocation.IntegerArg;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import ch.obermuhlner.math.big.BigDecimalMath;

import static im.mak.paddle.Async.async;
import static im.mak.paddle.Node.node;
import static im.mak.paddle.util.Script.fromFile;
import com.wavesplatform.crypto.base.Base58;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.Alphanumeric.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SwopfiFlatTest {
    private Account exchanger1, exchanger2, exchanger3, exchanger4, exchanger5, exchanger6, exchanger7;
    private final int aDecimal = 6;
    private final int bDecimal = 6;
    private final int commission = 500;
    private final int commissionGovernance = 200;
    private final int commissionScaleDelimiter = 1000000;
    private final int scaleValue8 = 100000000;
    private final double alpha = 0.5;
    private final double betta = 0.46;
    private final String version = "2.0.0";
    private AssetId shareTokenId;
    private final String governanceAddress = "3MP9d7iovdAZtsPeRcq97skdsQH5MPEsfgm";
    private final int minSponsoredAssetFee = 30000;
    private final long stakingFee = 9 * minSponsoredAssetFee;
    private final Account firstCaller = new Account(1000_00000000L);
    private final Account secondCaller = new Account(1000_00000000L);
    private final Account stakingAcc = new Account(1000_00000000L);
    private final AssetId tokenA = firstCaller.issue(a -> a.quantity(Long.MAX_VALUE).name("tokenA").decimals(aDecimal)).tx().assetId();
    private final AssetId tokenB = firstCaller.issue(a -> a.quantity(Long.MAX_VALUE).name("tokenB").decimals(bDecimal)).tx().assetId();
    private final String dAppScript = fromFile("dApps/flat.ride")
            .replace("3P6J84oH51DzY6xk2mT5TheXRbrCwBMxonp", governanceAddress)
            .replace("3PNikM6yp4NqcSU8guxQtmR5onr2D4e8yTJ", stakingAcc.address().toString())
            .replace("DG2xFkPdDwKUoBkzGAhQtLpSGzfXLiCYPEzeKH2Ad24p", tokenB.toString())
            .replace("DXDY2itiEcYBtGkVLnkpHtDFyWQUkoLJz79uJ7ECbMrA", Base58.encode(secondCaller.publicKey().bytes()))
            .replace("E6Wa1SGoktYcjHjsKrvjMiqJY3SWmGKcD8Q5L8kxSPS7", Base58.encode(secondCaller.publicKey().bytes()))
            .replace("AZmWJtuy4GeVrMmJH4hfFBRApe1StvhJSk4jcbT6bArQ", Base58.encode(secondCaller.publicKey().bytes()))
            .replace("EtVkT6ed8GtbUiVVEqdmEqsp2J4qbb3rre2HFgxeVYdg", Base58.encode(secondCaller.publicKey().bytes()))
            .replace("Czn4yoAuUZCVCLJDRfskn8URfkwpknwBTZDbs1wFrY7h", Base58.encode(secondCaller.publicKey().bytes()));


    @BeforeAll
    void before() {
        async(
                () -> {
                    exchanger1 = new Account(100_00000000L);
                    exchanger1.setScript(s -> s.script(dAppScript));
                },
                () -> {
                    exchanger2 = new Account(100_00000000L);
                    exchanger2.setScript(s -> s.script(dAppScript));
                },
                () -> {
                    exchanger3 = new Account(100_00000000L);
                    exchanger3.setScript(s -> s.script(dAppScript));
                },
                () -> {
                    exchanger4 = new Account(100_00000000L);
                    exchanger4.setScript(s -> s.script(dAppScript));
                },
                () -> {
                    exchanger5 = new Account(100_00000000L);
                    exchanger5.setScript(s -> s.script(dAppScript));
                },
                () -> {
                    exchanger6 = new Account(100_00000000L);
                    exchanger6.setScript(s -> s.script(dAppScript));
                },
                () -> {
                    exchanger7 = new Account(100_00000000L);
                    exchanger7.setScript(s -> s.script(dAppScript));
                },
                () -> firstCaller.sponsorFee(s -> s.amountForMinFee(minSponsoredAssetFee).assetId(tokenB)),
                () -> firstCaller.transfer(t -> t.to(secondCaller).amount(10000000_00000000L, tokenA)),
                () -> firstCaller.transfer(t -> t.to(secondCaller).amount(10000000_00000000L, tokenB)));
    }

    Stream<Arguments> fundProvider() {
        return Stream.of(
                Arguments.of(exchanger1, 1, 1),
                Arguments.of(exchanger2, 80, 80),
                Arguments.of(exchanger3, 140, 140),
                Arguments.of(exchanger4, 2122, 2122),
                Arguments.of(exchanger5, 21234, 21234),
                Arguments.of(exchanger6, 212345, 212345),
                Arguments.of(exchanger7, 9999999, 9999999));
    }

    @ParameterizedTest(name = "caller inits {index} exchanger with {1} tokenA and {2} tokenB")
    @MethodSource("fundProvider")
    void a_canFundAB(Account exchanger, int x, int y) {
        long fundAmountA = x * (long) Math.pow(10, aDecimal);
        long fundAmountB = y * (long) Math.pow(10, bDecimal);

        int digitsInShareToken = (aDecimal + bDecimal) / 2;
        firstCaller.invoke(i -> i.dApp(exchanger).function("init")
                .payment(fundAmountA, tokenA).payment(fundAmountB, tokenB)
                .fee(1_00500000L));
        node().waitNBlocks(1);
        long shareTokenSupply = (long) (((BigDecimal.valueOf(Math.pow(fundAmountA / Math.pow(10, aDecimal), 0.5)).setScale(aDecimal, RoundingMode.HALF_DOWN).movePointRight(aDecimal).doubleValue() *
                BigDecimal.valueOf(Math.pow(fundAmountB / Math.pow(10, bDecimal), 0.5)).setScale(bDecimal, RoundingMode.HALF_DOWN).movePointRight(bDecimal).doubleValue()) / Math.pow(10, digitsInShareToken)));
        shareTokenId = AssetId.as(exchanger.getStringData("share_asset_id"));

        assertAll("data and balances",
                () -> assertThat(exchanger.getData()).contains(
                        IntegerEntry.as("A_asset_balance", fundAmountA),
                        IntegerEntry.as("B_asset_balance", fundAmountB),
                        StringEntry.as("A_asset_id", tokenA.toString()),
                        StringEntry.as("B_asset_id", tokenB.toString()),
                        IntegerEntry.as("invariant", invariantCalc(fundAmountA, fundAmountB).longValue()),
                        BooleanEntry.as("active", true),
                        IntegerEntry.as("commission", commission),
                        IntegerEntry.as("commission_scale_delimiter", commissionScaleDelimiter),
                        StringEntry.as("version", "2.0.0"),
                        IntegerEntry.as("share_asset_supply", shareTokenSupply)),
                () -> assertThat(shareTokenSupply).isEqualTo(firstCaller.getAssetBalance(
                        AssetId.as(exchanger.getStringData("share_asset_id")))),
                () -> assertThat(exchanger.getAssetBalance(tokenA)).isEqualTo(fundAmountA),
                () -> assertThat(exchanger.getAssetBalance(tokenB)).isEqualTo(fundAmountB),
                () -> assertThat(firstCaller.getAssetBalance(shareTokenId)).isEqualTo(shareTokenSupply));
    }

    Stream<Arguments> aExchangeProvider() {
        return Stream.of(
                Arguments.of(exchanger5, ThreadLocalRandom.current().nextLong(10_000000L, 100_000000L)),
                Arguments.of(exchanger5, ThreadLocalRandom.current().nextLong(100_000000L, 10000_000000L)),
                Arguments.of(exchanger6, ThreadLocalRandom.current().nextLong(10_000000L, 100_000000L)),
                Arguments.of(exchanger6, ThreadLocalRandom.current().nextLong(100_000000L, 10000_000000L)),
                Arguments.of(exchanger7, ThreadLocalRandom.current().nextLong(10_000000L, 100_000000L)),
                Arguments.of(exchanger7, ThreadLocalRandom.current().nextLong(100_000000L, 10000_000000L)));
    }

    @ParameterizedTest(name = "firstCaller exchanges {1} tokenA")
    @MethodSource("aExchangeProvider")
    void b_canExchangeA(Account exchanger, long tokenReceiveAmount) {
        shareTokenId = AssetId.as(exchanger.getStringData("share_asset_id"));
        long amountTokenA = exchanger.getIntegerData("A_asset_balance");
        long amountTokenB = exchanger.getIntegerData("B_asset_balance");
        long invariant = exchanger.getIntegerData("invariant");
        long shareTokenSuplyBefore = exchanger.getIntegerData("share_asset_supply");
        long amountSendEstimated = amountToSendEstimated(amountTokenA, amountTokenB, amountTokenA + tokenReceiveAmount);
        long tokenSendAmountWithoutFee = calculateHowManySendTokenB(amountSendEstimated, amountSendEstimated, amountTokenA, amountTokenB, tokenReceiveAmount, invariant);
        long tokenSendAmountWithFee = BigInteger.valueOf(tokenSendAmountWithoutFee).multiply(BigInteger.valueOf(commissionScaleDelimiter - commission)).divide(BigInteger.valueOf(commissionScaleDelimiter)).longValue();
        long tokenSendGovernance = tokenSendAmountWithoutFee * commissionGovernance / commissionScaleDelimiter;
        long invariantAfter;
        invariantAfter = invariantCalc(amountTokenA + tokenReceiveAmount, amountTokenB - tokenSendAmountWithFee - tokenSendGovernance).longValue();

        firstCaller.invoke(i -> i.dApp(exchanger)
                .function("exchange", IntegerArg.as(amountSendEstimated), IntegerArg.as(amountSendEstimated))
                .payment(tokenReceiveAmount, tokenA));

        assertAll("data and balances",
                () -> assertThat(exchanger.getData()).contains(
                        IntegerEntry.as("A_asset_balance", amountTokenA + tokenReceiveAmount),
                        IntegerEntry.as("B_asset_balance", amountTokenB - tokenSendAmountWithFee - tokenSendGovernance),
                        StringEntry.as("A_asset_id", tokenA.toString()),
                        StringEntry.as("B_asset_id", tokenB.toString()),
                        BooleanEntry.as("active", true),
                        IntegerEntry.as("commission", commission),
                        IntegerEntry.as("commission_scale_delimiter", commissionScaleDelimiter),
                        StringEntry.as("version", version),
                        IntegerEntry.as("share_asset_supply", shareTokenSuplyBefore)),
                () -> assertThat(exchanger.getIntegerData("invariant")).isCloseTo(invariantAfter, within(2L)),
                () -> assertThat(exchanger.getAssetBalance(tokenA)).isEqualTo(amountTokenA + tokenReceiveAmount),
                () -> assertThat(exchanger.getAssetBalance(tokenB)).isEqualTo(amountTokenB - tokenSendAmountWithFee - tokenSendGovernance));

    }

    Stream<Arguments> bExchangeProvider() {
        return Stream.of(
                Arguments.of(exchanger5, ThreadLocalRandom.current().nextLong(10_000000L, 100_000000L)),
                Arguments.of(exchanger5, ThreadLocalRandom.current().nextLong(100_000000L, 10000_000000L)),
                Arguments.of(exchanger6, ThreadLocalRandom.current().nextLong(10_000000L, 100_000000L)),
                Arguments.of(exchanger6, ThreadLocalRandom.current().nextLong(100_000000L, 10000_000000L)),
                Arguments.of(exchanger7, ThreadLocalRandom.current().nextLong(10_000000L, 100_000000L)),
                Arguments.of(exchanger7, ThreadLocalRandom.current().nextLong(100_000000L, 10000_000000L)));
    }
    @ParameterizedTest(name = "firstCaller exchanges {1} tokenB")
    @MethodSource("bExchangeProvider")
    void c_canExchangeB(Account exchanger, long tokenReceiveAmount) {
        shareTokenId = AssetId.as(exchanger.getStringData("share_asset_id"));
        long amountTokenA = exchanger.getIntegerData("A_asset_balance");
        long amountTokenB = exchanger.getIntegerData("B_asset_balance");
        long invariant = exchanger.getIntegerData("invariant");
        long shareTokenSuplyBefore = exchanger.getIntegerData("share_asset_supply");
        long amountSendEstimated = amountToSendEstimated(amountTokenB, amountTokenA, amountTokenB + tokenReceiveAmount);
        long tokenSendAmountWithoutFee = calculateHowManySendTokenA(amountSendEstimated, amountSendEstimated, amountTokenA, amountTokenB, tokenReceiveAmount, invariant);
        long tokenSendAmountWithFee = BigInteger.valueOf(tokenSendAmountWithoutFee).multiply(BigInteger.valueOf(commissionScaleDelimiter - commission)).divide(BigInteger.valueOf(commissionScaleDelimiter)).longValue();
        long tokenSendGovernance = tokenSendAmountWithoutFee * commissionGovernance / commissionScaleDelimiter;


        firstCaller.invoke(i -> i.dApp(exchanger)
                .function("exchange", IntegerArg.as(amountSendEstimated), IntegerArg.as(amountSendEstimated))
                .payment(tokenReceiveAmount, tokenB));
        long invariantAfter = invariantCalc(amountTokenA - tokenSendAmountWithFee - tokenSendGovernance, amountTokenB + tokenReceiveAmount).longValue();

        assertAll("data and balances",
                () -> assertThat(exchanger.getData()).contains(
                        IntegerEntry.as("A_asset_balance", amountTokenA - tokenSendAmountWithFee - tokenSendGovernance),
                        IntegerEntry.as("B_asset_balance", amountTokenB + tokenReceiveAmount),
                        StringEntry.as("A_asset_id", tokenA.toString()),
                        StringEntry.as("B_asset_id", tokenB.toString()),
                        BooleanEntry.as("active", true),
                        IntegerEntry.as("commission", commission),
                        IntegerEntry.as("commission_scale_delimiter", commissionScaleDelimiter),
                        StringEntry.as("version", version),
                        IntegerEntry.as("share_asset_supply", shareTokenSuplyBefore)),
                () -> assertThat(exchanger.getIntegerData("invariant")).isCloseTo(invariantAfter, within(2L)),
                () -> assertThat(exchanger.getAssetBalance(tokenA)).isEqualTo(amountTokenA - tokenSendAmountWithFee - tokenSendGovernance),
                () -> assertThat(exchanger.getAssetBalance(tokenB)).isEqualTo(amountTokenB + tokenReceiveAmount));
    }

    @Test
    void d_cantExchangeBelowLimit() {
        shareTokenId = AssetId.as(exchanger7.getStringData("share_asset_id"));
        long amountBelowLimit = 9999999L;
        long amountTokenA = exchanger7.getIntegerData("A_asset_balance");
        long amountTokenB = exchanger7.getIntegerData("B_asset_balance");
        long amountSendEstimated = amountToSendEstimated(amountTokenB, amountTokenA, amountTokenB + amountBelowLimit);

        assertThat(assertThrows(ApiError.class, () ->
                firstCaller.invoke(i -> i.dApp(exchanger7)
                        .function("exchange", IntegerArg.as(amountSendEstimated), IntegerArg.as(amountSendEstimated))
                        .payment(amountBelowLimit, tokenB)))
        ).hasMessageContaining("Only swap of 10.000000 or more tokens is allowed");
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
                Arguments.of(exchanger7, ThreadLocalRandom.current().nextLong(100_000000L, 10000_000000L)));
    }
    @ParameterizedTest(name = "firstCaller replenish {1} tokenA")
    @MethodSource("replenishOneTokenAProvider")
    void e_firstCallerReplenishOneTokenA(Account exchanger, long pmtAmount) {
        long dAppTokensAmountA = exchanger.getIntegerData("A_asset_balance");
        long dAppTokensAmountB = exchanger.getIntegerData("B_asset_balance");

        long virtualSwapTokenPay = calculateVirtualPayGet(dAppTokensAmountA, dAppTokensAmountB, pmtAmount)[0];
        long virtualSwapTokenGet = calculateVirtualPayGet(dAppTokensAmountA, dAppTokensAmountB, pmtAmount)[1];
        long tokenShareSupply = exchanger.getIntegerData("share_asset_supply");
        shareTokenId = AssetId.as(exchanger.getStringData("share_asset_id"));
        long callerTokenShareBalance = firstCaller.getAssetBalance(shareTokenId);
        long amountVirtualReplenishTokenA = pmtAmount - virtualSwapTokenPay;
        long contractBalanceAfterVirtualSwapTokenA = dAppTokensAmountA + virtualSwapTokenPay;
        long contractBalanceAfterVirtualSwapTokenB = dAppTokensAmountB - virtualSwapTokenGet;
        double ratioShareTokensInA = BigDecimal.valueOf(amountVirtualReplenishTokenA).multiply(BigDecimal.valueOf(scaleValue8)).divide(BigDecimal.valueOf(contractBalanceAfterVirtualSwapTokenA), 8, RoundingMode.HALF_DOWN).longValue();
        double ratioShareTokensInB = BigDecimal.valueOf(virtualSwapTokenGet - stakingFee).multiply(BigDecimal.valueOf(scaleValue8)).divide(BigDecimal.valueOf(contractBalanceAfterVirtualSwapTokenB), 8, RoundingMode.HALF_DOWN).longValue();

        double ratioShareTokens = Math.min(ratioShareTokensInA, ratioShareTokensInB);
        long shareTokenToPayAmount = BigDecimal.valueOf(ratioShareTokens)
                .multiply(BigDecimal.valueOf(tokenShareSupply))
                .divide(BigDecimal.valueOf(scaleValue8), 8, RoundingMode.HALF_DOWN)
                .longValue();
        long invariantCalculated = invariantCalc(dAppTokensAmountA + pmtAmount, dAppTokensAmountB).longValue();

        firstCaller.invoke(i -> i.dApp(exchanger)
                .function("replenishWithOneToken", IntegerArg.as(virtualSwapTokenPay), IntegerArg.as(virtualSwapTokenGet))
                .payment(pmtAmount, tokenA));

        assertAll("data and balances", 
                () -> assertThat(exchanger.getData()).contains(
                        IntegerEntry.as("A_asset_balance", dAppTokensAmountA + pmtAmount),
                        IntegerEntry.as("B_asset_balance", dAppTokensAmountB),
                        StringEntry.as("A_asset_id", tokenA.toString()),
                        StringEntry.as("B_asset_id", tokenB.toString()),
                        BooleanEntry.as("active", true),
                        IntegerEntry.as("invariant", invariantCalculated),
                        IntegerEntry.as("commission", commission),
                        IntegerEntry.as("commission_scale_delimiter", commissionScaleDelimiter),
                        StringEntry.as("version", version),
                        StringEntry.as("share_asset_id", shareTokenId.toString()),
                        IntegerEntry.as("share_asset_supply", tokenShareSupply + shareTokenToPayAmount)),
                () -> assertThat(firstCaller.getAssetBalance(shareTokenId)).isEqualTo(callerTokenShareBalance + shareTokenToPayAmount));
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
                Arguments.of(exchanger7, ThreadLocalRandom.current().nextLong(100_000000L, 10000_000000L)));
    }
    @ParameterizedTest(name = "firstCaller replenish {1} tokenB")
    @MethodSource("replenishOneTokenBProvider")
    void f_firstCallerReplenishOneTokenB(Account exchanger, long pmtAmount) {
        long dAppTokensAmountA = exchanger.getIntegerData("A_asset_balance");
        long dAppTokensAmountB = exchanger.getIntegerData("B_asset_balance");
        long virtualSwapTokenPay = calculateVirtualPayGet(dAppTokensAmountB, dAppTokensAmountA, pmtAmount)[0];
        long virtualSwapTokenGet = calculateVirtualPayGet(dAppTokensAmountB, dAppTokensAmountA, pmtAmount)[1];
        long tokenShareSupply = exchanger.getIntegerData("share_asset_supply");
        shareTokenId = AssetId.as(exchanger.getStringData("share_asset_id"));
        long callerTokenShareBalance = firstCaller.getAssetBalance(shareTokenId);
        long amountVirtualReplenishTokenB = pmtAmount - virtualSwapTokenPay;
        long contractBalanceAfterVirtualSwapTokenA = dAppTokensAmountA - virtualSwapTokenGet;
        long contractBalanceAfterVirtualSwapTokenB = dAppTokensAmountB + virtualSwapTokenPay;

        double ratioShareTokensInA = BigDecimal.valueOf(virtualSwapTokenGet).multiply(BigDecimal.valueOf(scaleValue8)).divide(BigDecimal.valueOf(contractBalanceAfterVirtualSwapTokenA), 8, RoundingMode.HALF_DOWN).longValue();
        double ratioShareTokensInB = BigDecimal.valueOf(amountVirtualReplenishTokenB - stakingFee).multiply(BigDecimal.valueOf(scaleValue8)).divide(BigDecimal.valueOf(contractBalanceAfterVirtualSwapTokenB), 8, RoundingMode.HALF_DOWN).longValue();

        double ratioShareTokens = Math.min(ratioShareTokensInA, ratioShareTokensInB);
        long shareTokenToPayAmount = BigDecimal.valueOf(ratioShareTokens)
                .multiply(BigDecimal.valueOf(tokenShareSupply))
                .divide(BigDecimal.valueOf(scaleValue8), 8, RoundingMode.HALF_DOWN)
                .longValue();
        long invariantCalculated = invariantCalc(dAppTokensAmountA, dAppTokensAmountB + pmtAmount).longValue();

        firstCaller.invoke(i -> i.dApp(exchanger)
                .function("replenishWithOneToken", IntegerArg.as(virtualSwapTokenPay), IntegerArg.as(virtualSwapTokenGet))
                .payment(pmtAmount, tokenB));

        assertAll("data and balances",
                () -> assertThat(exchanger.getData()).contains(
                        IntegerEntry.as("A_asset_balance", dAppTokensAmountA),
                        IntegerEntry.as("B_asset_balance", dAppTokensAmountB + pmtAmount),
                        StringEntry.as("A_asset_id", tokenA.toString()),
                        StringEntry.as("B_asset_id", tokenB.toString()),
                        BooleanEntry.as("active", true),
                        IntegerEntry.as("invariant", invariantCalculated),
                        IntegerEntry.as("commission", commission),
                        IntegerEntry.as("commission_scale_delimiter", commissionScaleDelimiter),
                        StringEntry.as("version", version),
                        StringEntry.as("share_asset_id", shareTokenId.toString()),
                        IntegerEntry.as("share_asset_supply", tokenShareSupply + shareTokenToPayAmount)),
                () -> assertThat(firstCaller.getAssetBalance(shareTokenId)).isEqualTo(callerTokenShareBalance + shareTokenToPayAmount));
    }

    Stream<Arguments> replenishByTwiceProvider() {
        return Stream.of(
                Arguments.of(exchanger4),
                Arguments.of(exchanger5),
                Arguments.of(exchanger6),
                Arguments.of(exchanger7));
    }

    @ParameterizedTest(name = "secondCaller replenish A/B by twice")
    @MethodSource("replenishByTwiceProvider")
    void g_secondCallerReplenishABByTwice(Account exchanger) {
        long amountTokenABefore = exchanger.getIntegerData("A_asset_balance");
        long amountTokenBBefore = exchanger.getIntegerData("B_asset_balance");
        long shareTokenSupplyBefore = exchanger.getIntegerData("share_asset_supply");
        shareTokenId = AssetId.as(exchanger.getStringData("share_asset_id"));

        secondCaller.invoke(i -> i.dApp(exchanger)
                .function("replenishWithTwoTokens", IntegerArg.as(10))
                .payment(amountTokenABefore - stakingFee, tokenA).payment(amountTokenBBefore, tokenB));

        double ratioShareTokensInA = BigDecimal.valueOf(amountTokenABefore - stakingFee).multiply(BigDecimal.valueOf(scaleValue8)).divide(BigDecimal.valueOf(amountTokenABefore), 8, RoundingMode.HALF_DOWN).longValue();
        double ratioShareTokensInB = BigDecimal.valueOf(amountTokenBBefore - stakingFee).multiply(BigDecimal.valueOf(scaleValue8)).divide(BigDecimal.valueOf(amountTokenBBefore), 8, RoundingMode.HALF_DOWN).longValue();

        double ratioShareTokens = Math.min(ratioShareTokensInA, ratioShareTokensInB);
        long shareTokenToPay = BigDecimal.valueOf(ratioShareTokens)
                .multiply(BigDecimal.valueOf(shareTokenSupplyBefore))
                .divide(BigDecimal.valueOf(scaleValue8), 8, RoundingMode.HALF_DOWN)
                .longValue();

        assertAll("data and balances",
                () -> assertThat(exchanger.getData()).contains(
                        IntegerEntry.as("A_asset_balance", amountTokenABefore + amountTokenABefore - stakingFee),
                        IntegerEntry.as("B_asset_balance", amountTokenBBefore + amountTokenBBefore - stakingFee),
                        StringEntry.as("A_asset_id", tokenA.toString()),
                        StringEntry.as("B_asset_id", tokenB.toString()),
                        BooleanEntry.as("active", true),
                        IntegerEntry.as("invariant", invariantCalc(amountTokenABefore + amountTokenABefore - stakingFee, amountTokenBBefore + amountTokenBBefore - stakingFee).longValue()),
                        IntegerEntry.as("commission", commission),
                        IntegerEntry.as("commission_scale_delimiter", commissionScaleDelimiter),
                        StringEntry.as("version", version),
                        StringEntry.as("share_asset_id", shareTokenId.toString()),
                        IntegerEntry.as("share_asset_supply", shareTokenSupplyBefore + shareTokenToPay)),
                () -> assertThat(exchanger.getAssetBalance(tokenA)).isEqualTo(amountTokenABefore + amountTokenABefore - stakingFee),
                () -> assertThat(exchanger.getAssetBalance(tokenB)).isEqualTo(amountTokenBBefore + amountTokenBBefore),
                () -> assertThat(secondCaller.getAssetBalance(shareTokenId)).isEqualTo(shareTokenToPay));
    }

    @Test
    void h_slippToleranceAboveLimit() {
        int slippageToleranceAboveLimit = 11;
        assertThat(assertThrows(ApiError.class, () ->
                secondCaller.invoke(i -> i.dApp(exchanger7)
                        .function("replenishWithTwoTokens", IntegerArg.as(slippageToleranceAboveLimit))
                        .payment(100, tokenA).payment(100, tokenB)))
        ).hasMessageContaining("Slippage tolerance must be <= 1%");
    }

    @ParameterizedTest(name = "secondCaller withdraw A/B by twice")
    @MethodSource("replenishByTwiceProvider")
    void i_secondCallerWithdrawABByTwice(Account exchanger) {
        long amountTokenABefore = exchanger.getAssetBalance(tokenA);
        long amountTokenBBefore = exchanger.getAssetBalance(tokenB);
        long secondCallerAmountA = secondCaller.getAssetBalance(tokenA);
        long secondCallerAmountB = secondCaller.getAssetBalance(tokenB);
        long shareTokenSupply = exchanger.getIntegerData("share_asset_supply");
        shareTokenId = AssetId.as(exchanger.getStringData("share_asset_id"));
        long secondCallerShareBalance = secondCaller.getAssetBalance(shareTokenId);
        long tokensToPayA =
                BigDecimal.valueOf(secondCallerShareBalance)
                        .multiply(BigDecimal.valueOf(amountTokenABefore))
                        .divide(BigDecimal.valueOf(shareTokenSupply), 8, RoundingMode.HALF_DOWN).longValue();

        long tokensToPayB =
                BigDecimal.valueOf(secondCallerShareBalance)
                        .multiply(BigDecimal.valueOf(amountTokenBBefore - stakingFee))
                        .divide(BigDecimal.valueOf(shareTokenSupply), 8, RoundingMode.HALF_DOWN).longValue();

        secondCaller.invoke(i -> i.dApp(exchanger).function("withdraw").payment(secondCallerShareBalance, shareTokenId));
        node().waitNBlocks(1);

        assertAll("data and balances",
                () -> assertThat(exchanger.getData()).contains(
                        IntegerEntry.as("A_asset_balance", amountTokenABefore - tokensToPayA),
                        IntegerEntry.as("B_asset_balance", amountTokenBBefore - tokensToPayB),
                        StringEntry.as("A_asset_id", tokenA.toString()),
                        StringEntry.as("B_asset_id", tokenB.toString()),
                        BooleanEntry.as("active", true),
                        IntegerEntry.as("invariant", invariantCalc(amountTokenABefore - tokensToPayA, amountTokenBBefore - tokensToPayB).longValue()),
                        IntegerEntry.as("commission", commission),
                        IntegerEntry.as("commission_scale_delimiter", commissionScaleDelimiter),
                        StringEntry.as("version", version),
                        StringEntry.as("share_asset_id", shareTokenId.toString()),
                        IntegerEntry.as("share_asset_supply", shareTokenSupply - secondCallerShareBalance)),
                () -> assertThat(exchanger.getAssetBalance(tokenA)).isEqualTo(amountTokenABefore - tokensToPayA),
                () -> assertThat(exchanger.getAssetBalance(tokenB)).isEqualTo(amountTokenBBefore - tokensToPayB + stakingFee),
                () -> assertThat(secondCaller.getAssetBalance(tokenA)).isEqualTo(secondCallerAmountA + tokensToPayA),
                () -> assertThat(secondCaller.getAssetBalance(tokenB)).isEqualTo(secondCallerAmountB + tokensToPayB - stakingFee));
    }

    @Test
    void j_staking() {
        long balanceA = exchanger7.getIntegerData("A_asset_balance");
        long balanceB = exchanger7.getIntegerData("B_asset_balance");
        long tokenReceiveAmount = 1000000000L;
        long invariant = exchanger7.getIntegerData("invariant");

        stakingAcc.writeData(d -> d.integer(String.format("rpd_balance_%s_%s", tokenB, exchanger7.address()), balanceB));

        long amountSendEstimated = amountToSendEstimated(balanceA, balanceB, balanceA + tokenReceiveAmount);
        long tokenSendAmountWithoutFee = calculateHowManySendTokenB(amountSendEstimated, amountSendEstimated, balanceA, balanceB, tokenReceiveAmount, invariant);
        long tokenSendAmountWithFee = BigInteger.valueOf(tokenSendAmountWithoutFee).multiply(BigInteger.valueOf(commissionScaleDelimiter - commission)).divide(BigInteger.valueOf(commissionScaleDelimiter)).longValue();
        long tokenSendGovernance = tokenSendAmountWithoutFee * commissionGovernance / commissionScaleDelimiter;

        assertThat(assertThrows(ApiError.class, () -> firstCaller.invoke(i -> i.dApp(exchanger7)
                .function("exchange", IntegerArg.as(amountSendEstimated), IntegerArg.as(tokenSendAmountWithFee))
                .payment(tokenReceiveAmount, tokenA)))
        ).hasMessageContaining(
                "Error while executing account-script: Insufficient DApp balance to pay " + tokenSendAmountWithFee
                        + " tokenB due to staking. Available: 0 tokenB."
                        + " Please contact support in Telegram: https://t.me/swopfisupport");

        stakingAcc.writeData(d -> d.integer(String.format("rpd_balance_%s_%s", tokenB, exchanger7.address()), balanceB - tokenSendAmountWithFee - tokenSendGovernance - 1));
        firstCaller.invoke(i -> i.dApp(exchanger7)
                .function("exchange", IntegerArg.as(amountSendEstimated), IntegerArg.as(tokenSendAmountWithFee))
                .payment(tokenReceiveAmount, tokenA));
    }

    @Test
    void k_canShutdown() {
        assertThat(assertThrows(ApiError.class, () ->
                firstCaller.invoke(i -> i.dApp(exchanger1).function("shutdown")))
        ).hasMessageContaining("Only admin can call this function");

        secondCaller.invoke(i -> i.dApp(exchanger1).function("shutdown").fee(900000L));
        assertThat(exchanger1.getBooleanData("active")).isFalse();
        assertThat(exchanger1.getStringData("shutdown_cause")).isEqualTo("Paused by admin");


        assertThat(assertThrows(ApiError.class, () ->
                firstCaller.invoke(i -> i.dApp(exchanger1).function("shutdown")))
        ).hasMessageContaining("DApp is already suspended. Cause: Paused by admin");
    }

    @Test
    void l_canActivate() {
        assertThat(assertThrows(ApiError.class, () ->
                firstCaller.invoke(i -> i.dApp(exchanger1).function("activate")))
        ).hasMessageContaining("Only admin can call this function");

        secondCaller.invoke(i -> i.dApp(exchanger1).function("activate"));
        assertThat(exchanger1.getBooleanData("active")).isTrue();
        assertThat(assertThrows(ApiError.class, () ->
                exchanger1.getStringData("shutdown_cause"))
        ).hasMessageContaining("no data for this key");

        assertThat(assertThrows(ApiError.class, () ->
                firstCaller.invoke(i -> i.dApp(exchanger1).function("activate")))
        ).hasMessageContaining("DApp is already active");
    }

    private double skewness(long x, long y) {
        return (BigDecimal.valueOf(x)
                .divide(BigDecimal.valueOf(y), 12, RoundingMode.DOWN))
                .add(BigDecimal.valueOf(y)
                        .divide(BigDecimal.valueOf(x), 12, RoundingMode.DOWN))
                .divide(BigDecimal.valueOf(2), 12, RoundingMode.DOWN).setScale(8, RoundingMode.DOWN).doubleValue();
    }

    private BigDecimal invariantCalc(long x, long y) {
        double sk = skewness(x, y);
        long sk1 = (long) (skewness(x, y) * scaleValue8);

        BigDecimal xySum = BigDecimal.valueOf(x).add(BigDecimal.valueOf(y));
        BigDecimal xySumMultiScaleValue = xySum.multiply(BigDecimal.valueOf(scaleValue8));
        BigDecimal firstPow = BigDecimal.valueOf(Math.pow(sk / scaleValue8, alpha)).movePointRight(12).setScale(0, RoundingMode.UP);
        BigDecimal firstTerm = xySumMultiScaleValue.divide(firstPow, 0, RoundingMode.DOWN);
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
                y_right = mean;
            } else if (BigInteger.valueOf(invariant_delta_in_mean).multiply(BigInteger.valueOf(invariant_delta_in_right)).signum() != 1) {
                y_left = mean;
            } else {
                return y_balance - y_right - 2;
            }
        }
        return y_balance - y_right - 2;
    }

    private long calculateHowManySendTokenA(long amountToSendEstimated, long minTokenReceiveAmount, long amountTokenA, long amountTokenB, long tokenReceiveAmount, long invariant) {
        int slippageValue = scaleValue8 - scaleValue8 / 10000000;
        long deltaBetweenMaxAndMinSendValue = amountToSendEstimated - minTokenReceiveAmount;
        long amountToSendStep1 = amountToSendEstimated - deltaBetweenMaxAndMinSendValue / 5;
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
        int slippageValue = scaleValue8 - scaleValue8 / 10000000; // 0.000001% of slippage
        long deltaBetweenMaxAndMinSendValue = amountToSendEstimated - minTokenRecieveAmount;
        long amountToSendStep1 = amountToSendEstimated - deltaBetweenMaxAndMinSendValue / 5;
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
            BigDecimal ratio_virtual_replenish = BigDecimal.valueOf(x_to_deposit).divide(BigDecimal.valueOf(y_virtual_swap_get), 20, RoundingMode.HALF_DOWN);

            if (ratio_in_contract_after_virtual_swap.subtract(ratio_virtual_replenish).signum() == 1) {
                x_right = x_virtual_swap_pay;
                virtual_pay_get[0] = x_virtual_swap_pay;
                virtual_pay_get[1] = y_virtual_swap_get;
            } else if (ratio_in_contract_after_virtual_swap.subtract(ratio_virtual_replenish).signum() == -1) {
                x_left = x_virtual_swap_pay;
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