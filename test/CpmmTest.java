import com.wavesplatform.transactions.account.Address;
import com.wavesplatform.transactions.common.Amount;
import dapps.CpmmDApp;
import im.mak.paddle.Account;
import im.mak.paddle.exceptions.ApiError;
import com.wavesplatform.transactions.common.AssetId;
import com.wavesplatform.transactions.data.BooleanEntry;
import com.wavesplatform.transactions.data.IntegerEntry;
import com.wavesplatform.transactions.data.StringEntry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import static im.mak.paddle.token.Waves.WAVES;
import static im.mak.paddle.util.Async.async;
import static im.mak.paddle.Node.node;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.MethodOrderer.Alphanumeric;

@TestMethodOrder(Alphanumeric.class)
class CpmmTest {

    final int commission = 3000;
    final int commissionGovernance = 1200;
    final int commissionScaleDelimiter = 1000000;
    final int slippageToleranceDelimiter = 1000;
    final int scaleValue8 = 100000000;
    final String version = "1.0.0";
    final long stakingFee = 9 * minSponsoredAssetFee;
    final boolean isFirstHarvestFalse = false;

    static final int aDecimal = 8;
    static final int bDecimal = 6;
    static final int minSponsoredAssetFee = 30000;
    static final Address governance = Address.as("3MP9d7iovdAZtsPeRcq97skdsQH5MPEsfgm");
    static CpmmDApp exchanger1, exchanger2, exchanger3, exchanger4, exchanger5, exchanger6, exchanger7, exchanger8;
    static Account firstCaller, secondCaller, stakingAcc;
    static AssetId tokenA, tokenB;

    @BeforeAll
    static void before() {
        async(
                () -> firstCaller = new Account(WAVES.amount(1000)),
                () -> secondCaller = new Account(WAVES.amount(1000)),
                () -> stakingAcc = new Account(WAVES.amount(1000))
        );
        async(
                () -> tokenA = firstCaller.issue(a -> a.quantity(Long.MAX_VALUE).name("tokenA").decimals(aDecimal)).tx().assetId(),
                () -> tokenB = firstCaller.issue(a -> a.quantity(Long.MAX_VALUE).name("tokenB").decimals(bDecimal)).tx().assetId()
        );
        async(
                () -> firstCaller.sponsorFee(tokenB, minSponsoredAssetFee),
                () -> exchanger1 = new CpmmDApp(WAVES.amount(1000), governance, stakingAcc.address(), tokenB, firstCaller.publicKey()),
                () -> exchanger2 = new CpmmDApp(WAVES.amount(1000), governance, stakingAcc.address(), tokenB, firstCaller.publicKey()),
                () -> exchanger3 = new CpmmDApp(WAVES.amount(1000), governance, stakingAcc.address(), tokenB, firstCaller.publicKey()),
                () -> exchanger4 = new CpmmDApp(WAVES.amount(1000), governance, stakingAcc.address(), tokenB, firstCaller.publicKey()),
                () -> exchanger5 = new CpmmDApp(WAVES.amount(1000), governance, stakingAcc.address(), tokenB, firstCaller.publicKey()),
                () -> exchanger6 = new CpmmDApp(WAVES.amount(1000), governance, stakingAcc.address(), tokenB, firstCaller.publicKey()),
                () -> exchanger7 = new CpmmDApp(WAVES.amount(1000), governance, stakingAcc.address(), tokenB, firstCaller.publicKey()),
                () -> exchanger8 = new CpmmDApp(WAVES.amount(1000), governance, stakingAcc.address(), tokenB, firstCaller.publicKey())
        );
    }

    static Stream<Arguments> fundProvider() {
        return Stream.of(
                Arguments.of(exchanger1, 1, 1),
                Arguments.of(exchanger2, 10, 80),
                Arguments.of(exchanger3, 100, 140),
                Arguments.of(exchanger4, 2122, 3456),
                Arguments.of(exchanger5, 21234, 345678),
                Arguments.of(exchanger6, 212345, 34567),
                Arguments.of(exchanger7, 9999999, 8888888),
                Arguments.of(exchanger8, 99999999, 199999999));
    }

    @ParameterizedTest(name = "caller inits {index} exchanger with {1} tokenA and {2} tokenB")
    @MethodSource("fundProvider")
    void a_canFundAB(CpmmDApp exchanger, int x, int y) {
        long fundAmountA = x * (long) Math.pow(10, aDecimal);
        long fundAmountB = y * (long) Math.pow(10, bDecimal);

        int digitsInShareToken = (aDecimal + bDecimal) / 2;

        firstCaller.invoke(exchanger.init(isFirstHarvestFalse), i -> i
                .payment(fundAmountA, tokenA).payment(fundAmountB, tokenB).additionalFee(WAVES.amount(1)));

        AssetId shareTokenId = AssetId.as(exchanger.getStringData("share_asset_id"));

        long shareTokenSupply = (long) (((BigDecimal.valueOf(Math.pow(fundAmountA / Math.pow(10, aDecimal), 0.5)).setScale(aDecimal, RoundingMode.HALF_DOWN).movePointRight(aDecimal).doubleValue() *
                BigDecimal.valueOf(Math.pow(fundAmountB / Math.pow(10, bDecimal), 0.5)).setScale(bDecimal, RoundingMode.HALF_DOWN).movePointRight(bDecimal).doubleValue()) / Math.pow(10, digitsInShareToken)));

        assertAll("data and balances",
                () -> assertThat(exchanger.getData()).contains(
                        IntegerEntry.as("A_asset_balance", fundAmountA),
                        IntegerEntry.as("B_asset_balance", fundAmountB),
                        StringEntry.as("A_asset_id", tokenA.toString()),
                        StringEntry.as("B_asset_id", tokenB.toString()),
                        BooleanEntry.as("active", true),
                        IntegerEntry.as("commission", commission),
                        IntegerEntry.as("commission_scale_delimiter", commissionScaleDelimiter),
                        StringEntry.as("version", version),
                        IntegerEntry.as("share_asset_supply", shareTokenSupply)),
                () -> assertThat(shareTokenSupply).isEqualTo(firstCaller.getAssetBalance(
                        AssetId.as(exchanger.getStringData("share_asset_id")))),
                () -> assertThat(exchanger.getAssetBalance(tokenA)).isEqualTo(fundAmountA),
                () -> assertThat(exchanger.getAssetBalance(tokenB)).isEqualTo(fundAmountB),
                () -> assertThat(firstCaller.getAssetBalance(shareTokenId)).isEqualTo(shareTokenSupply));
        //for future staking fee check in replenish/withdraw tests
        stakingAcc.writeData(d -> d.integer(String.format("rpd_balance_%s_%s", tokenB, exchanger.address()), 100));
    }

    static Stream<Arguments> aExchangerProvider() {
        return Stream.of(
                Arguments.of(exchanger2, ThreadLocalRandom.current().nextLong(10000L, 1_00000000L)),
                Arguments.of(exchanger2, ThreadLocalRandom.current().nextLong(10000L, 1_00000000L)),
                Arguments.of(exchanger2, ThreadLocalRandom.current().nextLong(10000L, 1_00000000L)),
                Arguments.of(exchanger6, ThreadLocalRandom.current().nextLong(10000L, 1_00000000L)),
                Arguments.of(exchanger6, ThreadLocalRandom.current().nextLong(10_00000000L, 100_00000000L)),
                Arguments.of(exchanger6, ThreadLocalRandom.current().nextLong(100_00000000L, 10000_00000000L)),
                Arguments.of(exchanger7, ThreadLocalRandom.current().nextLong(10000L, 1_00000000L)),
                Arguments.of(exchanger7, ThreadLocalRandom.current().nextLong(10_00000000L, 100_00000000L)),
                Arguments.of(exchanger7, ThreadLocalRandom.current().nextLong(100_00000000L, 10000_00000000L)),
                Arguments.of(exchanger8, ThreadLocalRandom.current().nextLong(10000L, 1_00000000L)),
                Arguments.of(exchanger8, ThreadLocalRandom.current().nextLong(10_00000000L, 100_00000000L)),
                Arguments.of(exchanger8, ThreadLocalRandom.current().nextLong(100_00000000L, 10000_00000000L)));
    }

    @ParameterizedTest(name = "firstCaller exchanges {1} tokenA")
    @MethodSource("aExchangerProvider")
    void
    b_canExchangeA(CpmmDApp exchanger, long tokenReceiveAmount) {

        long amountTokenA = exchanger.getIntegerData("A_asset_balance");
        long amountTokenB = exchanger.getIntegerData("B_asset_balance");
        long callerBalanceA = firstCaller.getAssetBalance(tokenA);
        long callerBalanceB = firstCaller.getAssetBalance(tokenB);
        long shareTokenSuplyBefore = exchanger.getIntegerData("share_asset_supply");
        BigInteger tokenSendAmountWithoutFee =
                BigInteger.valueOf(tokenReceiveAmount)
                        .multiply(BigInteger.valueOf(amountTokenB))
                        .divide(BigInteger.valueOf(tokenReceiveAmount + amountTokenA));

        AssetId shareTokenId = AssetId.as(exchanger.getStringData("share_asset_id"));
        long tokenSendAmountWithFee = tokenSendAmountWithoutFee.multiply(BigInteger.valueOf(commissionScaleDelimiter - commission)).divide(BigInteger.valueOf(commissionScaleDelimiter)).longValue();
        long tokenSendGovernance = tokenSendAmountWithoutFee.longValue() * commissionGovernance / commissionScaleDelimiter;

        firstCaller.invoke(exchanger.exchange(tokenSendAmountWithFee), Amount.of(tokenReceiveAmount, tokenA));

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
                        StringEntry.as("share_asset_id", shareTokenId.toString()),
                        IntegerEntry.as("share_asset_supply", shareTokenSuplyBefore)),
                () -> assertThat(exchanger.getAssetBalance(tokenA)).isEqualTo(amountTokenA + tokenReceiveAmount),
                () -> assertThat(exchanger.getAssetBalance(tokenB)).isEqualTo(amountTokenB - tokenSendAmountWithFee - tokenSendGovernance),
                () -> assertThat(firstCaller.getAssetBalance(tokenA)).isEqualTo(callerBalanceA - tokenReceiveAmount),
                () -> assertThat(firstCaller.getAssetBalance(tokenB)).isEqualTo(callerBalanceB + tokenSendAmountWithFee));

    }

    static Stream<Arguments> bExchangerProvider() {
        return Stream.of(
                Arguments.of(exchanger2, ThreadLocalRandom.current().nextLong(10000L, 1_00000000L)),
                Arguments.of(exchanger2, ThreadLocalRandom.current().nextLong(10000L, 1_00000000L)),
                Arguments.of(exchanger2, ThreadLocalRandom.current().nextLong(10000L, 1_00000000L)),
                Arguments.of(exchanger6, ThreadLocalRandom.current().nextLong(10000L, 1_00000000L)),
                Arguments.of(exchanger6, ThreadLocalRandom.current().nextLong(10_00000000L, 100_00000000L)),
                Arguments.of(exchanger6, ThreadLocalRandom.current().nextLong(100_00000000L, 10000_00000000L)),
                Arguments.of(exchanger7, ThreadLocalRandom.current().nextLong(10000L, 1_00000000L)),
                Arguments.of(exchanger7, ThreadLocalRandom.current().nextLong(10_00000000L, 100_00000000L)),
                Arguments.of(exchanger7, ThreadLocalRandom.current().nextLong(100_00000000L, 10000_00000000L)),
                Arguments.of(exchanger8, ThreadLocalRandom.current().nextLong(10000L, 1_00000000L)),
                Arguments.of(exchanger8, ThreadLocalRandom.current().nextLong(10_00000000L, 100_00000000L)),
                Arguments.of(exchanger8, ThreadLocalRandom.current().nextLong(100_00000000L, 10000_00000000L)));
    }

    @ParameterizedTest(name = "firstCaller exchanges {1} tokenB")
    @MethodSource("bExchangerProvider")
    void c_canExchangeB(CpmmDApp exchanger, long tokenReceiveAmount) {
        long amountTokenA = exchanger.getIntegerData("A_asset_balance");
        long amountTokenB = exchanger.getIntegerData("B_asset_balance");
        long callerBalanceA = firstCaller.getAssetBalance(tokenA);
        long callerBalanceB = firstCaller.getAssetBalance(tokenB);
        long shareTokenSuplyBefore = exchanger.getIntegerData("share_asset_supply");
        BigInteger tokenSendAmountWithoutFee =
                BigInteger.valueOf(tokenReceiveAmount)
                        .multiply(BigInteger.valueOf(amountTokenA))
                        .divide(BigInteger.valueOf(tokenReceiveAmount + amountTokenB));

        AssetId shareTokenId = AssetId.as(exchanger.getStringData("share_asset_id"));
        long tokenSendAmountWithFee = tokenSendAmountWithoutFee.multiply(BigInteger.valueOf(commissionScaleDelimiter - commission)).divide(BigInteger.valueOf(commissionScaleDelimiter)).longValue();
        long tokenSendGovernance = tokenSendAmountWithoutFee.longValue() * commissionGovernance / commissionScaleDelimiter;

        firstCaller.invoke(exchanger.exchange(tokenSendAmountWithFee), Amount.of(tokenReceiveAmount, tokenB));

        assertAll("data and balances",
                () -> assertThat(exchanger.getData()).contains(
                        IntegerEntry.as("A_asset_balance", amountTokenA - tokenSendAmountWithFee - tokenSendGovernance),//91832344013287
                        IntegerEntry.as("B_asset_balance", amountTokenB + tokenReceiveAmount),
                        StringEntry.as("A_asset_id", tokenA.toString()),
                        StringEntry.as("B_asset_id", tokenB.toString()),
                        BooleanEntry.as("active", true),
                        IntegerEntry.as("commission", commission),
                        IntegerEntry.as("commission_scale_delimiter", commissionScaleDelimiter),
                        StringEntry.as("version", version),
                        StringEntry.as("share_asset_id", shareTokenId.toString()),
                        IntegerEntry.as("share_asset_supply", shareTokenSuplyBefore)),
                () -> assertThat(exchanger.getAssetBalance(tokenA)).isEqualTo(amountTokenA - tokenSendAmountWithFee - tokenSendGovernance),
                () -> assertThat(exchanger.getAssetBalance(tokenB)).isEqualTo(amountTokenB + tokenReceiveAmount),
                () -> assertThat(firstCaller.getAssetBalance(tokenA)).isEqualTo(callerBalanceA + tokenSendAmountWithFee),
                () -> assertThat(firstCaller.getAssetBalance(tokenB)).isEqualTo(callerBalanceB - tokenReceiveAmount));
    }

    static Stream<Arguments> replenishByTwiceProvider() {
        return Stream.of(
                Arguments.of(exchanger4),
                Arguments.of(exchanger5),
                Arguments.of(exchanger6),
                Arguments.of(exchanger7),
                Arguments.of(exchanger8));
    }

    @ParameterizedTest(name = "secondCaller replenish A/B by twice")
    @MethodSource("replenishByTwiceProvider")
    void d_secondCallerReplenishByTwice(CpmmDApp exchanger) {
        long amountTokenABefore = exchanger.getIntegerData("A_asset_balance");
        long amountTokenBBefore = exchanger.getIntegerData("B_asset_balance");
        long secondCallerBalanceA = secondCaller.getAssetBalance(tokenA);
        firstCaller.transfer(secondCaller, amountTokenABefore, tokenA).tx().id();
        firstCaller.transfer(secondCaller, amountTokenBBefore, tokenB).tx().id();
        long shareTokenSupplyBefore = exchanger.getIntegerData("share_asset_supply");
        secondCaller.invoke(exchanger.replenishWithTwoTokens(1), Amount.of(amountTokenABefore - stakingFee, tokenA), Amount.of(amountTokenBBefore, tokenB));

        double ratioShareTokensInA = BigDecimal.valueOf(amountTokenABefore - stakingFee).multiply(BigDecimal.valueOf(scaleValue8)).divide(BigDecimal.valueOf(amountTokenABefore), 8, RoundingMode.HALF_DOWN).longValue();
        double ratioShareTokensInB = BigDecimal.valueOf(amountTokenBBefore - stakingFee).multiply(BigDecimal.valueOf(scaleValue8)).divide(BigDecimal.valueOf(amountTokenBBefore), 8, RoundingMode.HALF_DOWN).longValue();

        double ratioShareTokens = Math.min(ratioShareTokensInA, ratioShareTokensInB);
        long shareTokenToPay = BigDecimal.valueOf(ratioShareTokens)
                .multiply(BigDecimal.valueOf(shareTokenSupplyBefore))
                .divide(BigDecimal.valueOf(scaleValue8), 8, RoundingMode.HALF_DOWN)
                .longValue();

        AssetId shareTokenId = AssetId.as(exchanger.getStringData("share_asset_id"));

        assertAll("data and balances",
                () -> assertThat(exchanger.getData()).contains(
                        IntegerEntry.as("A_asset_balance", amountTokenABefore + amountTokenABefore - stakingFee),
                        IntegerEntry.as("B_asset_balance", amountTokenBBefore + amountTokenBBefore - stakingFee),
                        StringEntry.as("A_asset_id", tokenA.toString()),
                        StringEntry.as("B_asset_id", tokenB.toString()),
                        BooleanEntry.as("active", true),
                        IntegerEntry.as("commission", commission),
                        IntegerEntry.as("commission_scale_delimiter", commissionScaleDelimiter),
                        StringEntry.as("version", version),
                        StringEntry.as("share_asset_id", shareTokenId.toString()),
                        IntegerEntry.as("share_asset_supply", shareTokenSupplyBefore + shareTokenToPay)),
                () -> assertThat(exchanger.getAssetBalance(tokenA)).isEqualTo(amountTokenABefore + amountTokenABefore - stakingFee),
                () -> assertThat(exchanger.getAssetBalance(tokenB)).isEqualTo(amountTokenBBefore + amountTokenBBefore),
                () -> assertThat(secondCaller.getAssetBalance(tokenA)).isEqualTo(secondCallerBalanceA + stakingFee),
                () -> assertThat(secondCaller.getAssetBalance(tokenB)).isEqualTo(0),
                () -> assertThat(secondCaller.getAssetBalance(shareTokenId)).isEqualTo(shareTokenToPay));
    }

    static Stream<Arguments> withdrawByTwiceProvider() {
        return Stream.of(
                Arguments.of(exchanger4),
                Arguments.of(exchanger5),
                Arguments.of(exchanger6),
                Arguments.of(exchanger7),
                Arguments.of(exchanger8));
    }

    @ParameterizedTest(name = "secondCaller withdraw A/B by twice")
    @MethodSource("withdrawByTwiceProvider")
    void e_secondCallerWithdrawByTwice(CpmmDApp exchanger) {
        long dAppTokensAmountA = exchanger.getAssetBalance(tokenA);
        long dAppTokensAmountB = exchanger.getAssetBalance(tokenB);
        long secondCallerAmountA = secondCaller.getAssetBalance(tokenA);
        long secondCallerAmountB = secondCaller.getAssetBalance(tokenB);
        long shareTokenSupply = exchanger.getIntegerData("share_asset_supply");
        AssetId shareTokenId = AssetId.as(exchanger.getStringData("share_asset_id"));
        long secondCallerShareBalance = secondCaller.getAssetBalance(shareTokenId);
        long tokensToPayA =
                BigDecimal.valueOf(secondCallerShareBalance)
                        .multiply(BigDecimal.valueOf(dAppTokensAmountA))
                        .divide(BigDecimal.valueOf(shareTokenSupply), 8, RoundingMode.HALF_DOWN).longValue();

        long tokensToPayB =
                BigDecimal.valueOf(secondCallerShareBalance)
                        .multiply(BigDecimal.valueOf(dAppTokensAmountB - stakingFee))
                        .divide(BigDecimal.valueOf(shareTokenSupply), 8, RoundingMode.HALF_DOWN).longValue();

        secondCaller.invoke(exchanger.withdraw(), Amount.of(secondCallerShareBalance, shareTokenId));

        assertAll("data and balances",
                () -> assertThat(exchanger.getData()).contains(
                        IntegerEntry.as("A_asset_balance", dAppTokensAmountA - tokensToPayA),
                        IntegerEntry.as("B_asset_balance", dAppTokensAmountB - tokensToPayB),
                        StringEntry.as("A_asset_id", tokenA.toString()),
                        StringEntry.as("B_asset_id", tokenB.toString()),
                        BooleanEntry.as("active", true),
                        IntegerEntry.as("commission", commission),
                        IntegerEntry.as("commission_scale_delimiter", commissionScaleDelimiter),
                        StringEntry.as("version", version),
                        StringEntry.as("share_asset_id", shareTokenId.toString()),
                        IntegerEntry.as("share_asset_supply", shareTokenSupply - secondCallerShareBalance)),
                () -> assertThat(exchanger.getAssetBalance(tokenA)).isEqualTo(dAppTokensAmountA - tokensToPayA),
                () -> assertThat(exchanger.getAssetBalance(tokenB)).isEqualTo(dAppTokensAmountB - tokensToPayB + stakingFee),
                () -> assertThat(secondCaller.getAssetBalance(shareTokenId)).isEqualTo(0),
                () -> assertThat(secondCaller.getAssetBalance(tokenA)).isEqualTo(secondCallerAmountA + tokensToPayA),
                () -> assertThat(secondCaller.getAssetBalance(tokenB)).isEqualTo(secondCallerAmountB + tokensToPayB - stakingFee));
    }

    static Stream<Arguments> replenishProvider() {
        return Stream.of(
                Arguments.of(exchanger4, 1L, 1), Arguments.of(exchanger4, 100000L, 5), Arguments.of(exchanger4, 26189L, 10),
                Arguments.of(exchanger5, 50000L, 5), Arguments.of(exchanger5, 100L, 10), Arguments.of(exchanger5, 457382L, 20),
                Arguments.of(exchanger6, 35L, 3), Arguments.of(exchanger6, 1000L, 4), Arguments.of(exchanger6, 10004L, 7));
    }

    @ParameterizedTest(name = "secondCaller replenish A/B, slippage {2}")
    @MethodSource("replenishProvider")
    void f_canReplenishAB(CpmmDApp exchanger, long replenishAmountB, int slippageTolerance) {
        long balanceA = exchanger.getIntegerData("A_asset_balance");
        long balanceB = exchanger.getIntegerData("B_asset_balance");
        long realBalanceB = exchanger.getAssetBalance(tokenB);
        long shareAssetSupply = exchanger.getIntegerData("share_asset_supply");
        AssetId shareAssetId = AssetId.as(exchanger.getStringData("share_asset_id"));

        long callerShareBalance = firstCaller.getAssetBalance(shareAssetId);
        int contractRatioMin = (1000 * (slippageToleranceDelimiter - slippageTolerance)) / slippageToleranceDelimiter;
        int contractRatioMax = (1000 * (slippageToleranceDelimiter + slippageTolerance)) / slippageToleranceDelimiter;
        long pmtAmountB = replenishAmountB * (long) Math.pow(10, bDecimal);

        Map<String, Long> insufficientTokenRatioAmounts = new HashMap<>();
        insufficientTokenRatioAmounts.put("pmtAmountA", aReplenishAmountByRatio(contractRatioMin - 2, pmtAmountB - stakingFee, balanceA, balanceB));
        insufficientTokenRatioAmounts.put("pmtAmountB", pmtAmountB);

        assertThat(assertThrows(ApiError.class, () ->
                firstCaller.invoke(exchanger.replenishWithTwoTokens(slippageTolerance),
                        Amount.of(insufficientTokenRatioAmounts.get("pmtAmountA"), tokenA),
                        Amount.of(insufficientTokenRatioAmounts.get("pmtAmountB"), tokenB)))
        ).hasMessageContaining("Incorrect assets amount: amounts must have the contract ratio");

        Map<String, Long> tooBigTokenRatioAmounts = new HashMap<>();
        tooBigTokenRatioAmounts.put("pmtAmountA", aReplenishAmountByRatio(contractRatioMax + 2, pmtAmountB - stakingFee, balanceA, balanceB));
        tooBigTokenRatioAmounts.put("pmtAmountB", pmtAmountB);

        assertThat(assertThrows(ApiError.class, () ->
                firstCaller.invoke(exchanger.replenishWithTwoTokens(slippageTolerance),
                        Amount.of(tooBigTokenRatioAmounts.get("pmtAmountA"), tokenA),
                        Amount.of(tooBigTokenRatioAmounts.get("pmtAmountB"), tokenB)))
        ).hasMessageContaining("Incorrect assets amount: amounts must have the contract ratio");

        Map<String, Long> replenishAmounts = new HashMap<>();
        replenishAmounts.put("pmtAmountA", aReplenishAmountByRatio(contractRatioMin + 1, pmtAmountB - stakingFee, balanceA, balanceB));
        replenishAmounts.put("pmtAmountB", pmtAmountB);

        firstCaller.invoke(exchanger.replenishWithTwoTokens(slippageTolerance),
                Amount.of(replenishAmounts.get("pmtAmountA"), tokenA),
                Amount.of(replenishAmounts.get("pmtAmountB"), tokenB));

        long ratioShareTokensInA = BigInteger.valueOf(replenishAmounts.get("pmtAmountA")).multiply(BigInteger.valueOf(scaleValue8)).divide(BigInteger.valueOf(balanceA)).longValue();
        long ratioShareTokensInB = BigInteger.valueOf(pmtAmountB - stakingFee).multiply(BigInteger.valueOf(scaleValue8)).divide(BigInteger.valueOf(balanceB)).longValue();

        long shareTokenToPayAmount = BigInteger.valueOf(Long.min(ratioShareTokensInA, ratioShareTokensInB)).multiply(BigInteger.valueOf(shareAssetSupply)).divide(BigInteger.valueOf(scaleValue8)).longValue();


        assertAll("data and balances",
                () -> assertThat(exchanger.getData()).contains(
                        IntegerEntry.as("A_asset_balance", balanceA + replenishAmounts.get("pmtAmountA")),
                        IntegerEntry.as("B_asset_balance", balanceB + pmtAmountB - stakingFee),
                        StringEntry.as("A_asset_id", tokenA.toString()),
                        StringEntry.as("B_asset_id", tokenB.toString()),
                        BooleanEntry.as("active", true),
                        IntegerEntry.as("commission", commission),
                        IntegerEntry.as("commission_scale_delimiter", commissionScaleDelimiter),
                        StringEntry.as("version", version),
                        StringEntry.as("share_asset_id", shareAssetId.toString()),
                        IntegerEntry.as("share_asset_supply", shareAssetSupply + shareTokenToPayAmount)),
                () -> assertThat(exchanger.getAssetBalance(tokenA)).isEqualTo(balanceA + replenishAmounts.get("pmtAmountA")),
                () -> assertThat(exchanger.getAssetBalance(tokenB)).isEqualTo(realBalanceB + pmtAmountB),
                () -> assertThat(firstCaller.getAssetBalance(shareAssetId)).isEqualTo(callerShareBalance + shareTokenToPayAmount));
    }

    @Test
    void g_staking() {
        long balanceA = exchanger8.getIntegerData("A_asset_balance");
        long balanceB = exchanger8.getIntegerData("B_asset_balance");
        long tokenReceiveAmount = 1000000000L;
        stakingAcc.writeData(d -> d.integer(String.format("rpd_balance_%s_%s", tokenB, exchanger8.address()), balanceB));
        BigInteger tokenSendAmountWithoutFee =
                BigInteger.valueOf(tokenReceiveAmount)
                        .multiply(BigInteger.valueOf(balanceB))
                        .divide(BigInteger.valueOf(tokenReceiveAmount + balanceA));
        long tokenSendAmountWithFee = tokenSendAmountWithoutFee.multiply(BigInteger.valueOf(commissionScaleDelimiter - commission)).divide(BigInteger.valueOf(commissionScaleDelimiter)).longValue();
        long tokenSendGovernance = tokenSendAmountWithoutFee.longValue() * commissionGovernance / commissionScaleDelimiter;

        assertThat(assertThrows(ApiError.class, () ->
                firstCaller.invoke(exchanger8.exchange(tokenSendAmountWithFee), Amount.of(tokenReceiveAmount, tokenA)))
        ).hasMessageContaining(
                "Error while executing account-script: Insufficient DApp balance to pay " + tokenSendAmountWithFee
                        + " tokenB due to staking. Available: 0 tokenB."
                        + " Please contact support in Telegram: https://t.me/swopfisupport");

        stakingAcc.writeData(d -> d.integer(String.format("rpd_balance_%s_%s", tokenB, exchanger8.address()), balanceB - tokenSendAmountWithFee - tokenSendGovernance - 1));
        firstCaller.invoke(exchanger8.exchange(tokenSendAmountWithFee), Amount.of(tokenReceiveAmount, tokenA));
    }

    @Test
    void h_canShutdown() {
        assertThat(assertThrows(ApiError.class, () ->
                secondCaller.invoke(exchanger1.shutdown()))
        ).hasMessageContaining("Only admin can call this function");

        firstCaller.invoke(exchanger1.shutdown());
        assertThat(exchanger1.getBooleanData("active")).isFalse();
        assertThat(exchanger1.getStringData("shutdown_cause")).isEqualTo("Paused by admin");


        assertThat(assertThrows(ApiError.class, () ->
                secondCaller.invoke(exchanger1.shutdown()))
        ).hasMessageContaining("DApp is already suspended. Cause: Paused by admin");
    }

    @Test
    void i_canActivate() {
        assertThat(assertThrows(ApiError.class, () ->
                secondCaller.invoke(exchanger1.activate()))
        ).hasMessageContaining("Only admin can call this function");

        firstCaller.invoke(exchanger1.activate());

        assertThat(exchanger1.getBooleanData("active")).isTrue();

        assertThat(assertThrows(ApiError.class, () ->
                exchanger1.getStringData("shutdown_cause"))
        ).hasMessageContaining("no data for this key");

        assertThat(assertThrows(ApiError.class, () ->
                secondCaller.invoke(exchanger1.activate()))
        ).hasMessageContaining("DApp is already active");
    }


    private long aReplenishAmountByRatio(int tokenRatio, long pmtAmountB, long balanceA, long balanceB) {
        return ((BigInteger.valueOf(balanceA)
                .multiply(BigInteger.valueOf(1000))
                .multiply(BigInteger.valueOf(pmtAmountB)))
                .divide(
                        BigInteger.valueOf(tokenRatio)
                                .multiply(BigInteger.valueOf(balanceB)))).longValue();

    }
}