import com.wavesplatform.wavesj.*;
import im.mak.paddle.Account;
import im.mak.paddle.exceptions.NodeError;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static im.mak.paddle.Async.async;
import static im.mak.paddle.Node.node;
import static im.mak.paddle.actions.invoke.Arg.arg;
import static im.mak.paddle.util.Script.fromFile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.MethodOrderer.Alphanumeric;

@TestMethodOrder(Alphanumeric.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SwopfiTest {

    private Account firstExchanger, secondExchanger, thirdExchanger, firstCaller;
    private String tokenA;
    private String tokenB;
    private int aDecimal = 8;
    private int bDecimal = 6;
    private int commission = 3000;
    private int commisionGovernance = 1200;
    private int commissionScaleDelimiter = 1000000;
    private int slippageToleranceDelimiter = 1000;
    private int scaleValue8 = 100000000;
    private String version = "1.0.0";
    private HashMap<Account, String> shareTokenIds = new HashMap<>();
    private String governanceAddress = "3MP9d7iovdAZtsPeRcq97skdsQH5MPEsfgm";
    private Account secondCaller = new Account(1000_00000000L);
    private String dAppScript = fromFile("dApps/exchanger.ride")
            .replace("3P6J84oH51DzY6xk2mT5TheXRbrCwBMxonp", governanceAddress)
            //TODO staking
            //TODO USDN
            .replace("DXDY2itiEcYBtGkVLnkpHtDFyWQUkoLJz79uJ7ECbMrA", Base58.encode(secondCaller.publicKey()))
            .replace("E6Wa1SGoktYcjHjsKrvjMiqJY3SWmGKcD8Q5L8kxSPS7", Base58.encode(secondCaller.publicKey()))
            .replace("AZmWJtuy4GeVrMmJH4hfFBRApe1StvhJSk4jcbT6bArQ", Base58.encode(secondCaller.publicKey()))
            .replace("EtVkT6ed8GtbUiVVEqdmEqsp2J4qbb3rre2HFgxeVYdg", Base58.encode(secondCaller.publicKey()))
            .replace("Czn4yoAuUZCVCLJDRfskn8URfkwpknwBTZDbs1wFrY7h", Base58.encode(secondCaller.publicKey()));

    @BeforeAll
    void before() {
        async(
                () -> {
                    firstExchanger = new Account(1000_00000000L);
                    firstExchanger.setsScript(s -> s.script(dAppScript));
                },
                () -> {
                    secondExchanger = new Account(1000_00000000L);
                    secondExchanger.setsScript(s -> s.script(dAppScript));
                },
                () -> {
                    thirdExchanger = new Account(1000_00000000L);
                    thirdExchanger.setsScript(s -> s.script(dAppScript));
                },
                () -> {
                    firstCaller = new Account(1000_00000000L);
                    tokenA = firstCaller.issues(a -> a.quantity(Long.MAX_VALUE).name("tokenA").decimals(aDecimal)).getId().toString();
                    tokenB = firstCaller.issues(a -> a.quantity(Long.MAX_VALUE).name("tokenB").decimals(bDecimal)).getId().toString();
                }
        );
    }

    Stream<Arguments> fundProvider() {
        return Stream.of(
                Arguments.of(firstExchanger, 1000000, 100000),
                Arguments.of(secondExchanger, 100000, 100000),
                Arguments.of(thirdExchanger, 212345, 3456789));
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

        shareTokenIds.put(exchanger, exchanger.dataStr("share_asset_id"));

        long shareTokenSupply = (long) (((new BigDecimal(Math.pow(fundAmountA / Math.pow(10, aDecimal), 0.5)).setScale(aDecimal, RoundingMode.HALF_DOWN).movePointRight(aDecimal).doubleValue() *
                new BigDecimal(Math.pow(fundAmountB / Math.pow(10, bDecimal), 0.5)).setScale(bDecimal, RoundingMode.HALF_DOWN).movePointRight(bDecimal).doubleValue()) / Math.pow(10, digitsInShareToken)));

        assertAll("data and balances",
                () -> assertThat(exchanger.dataInt("A_asset_balance")).isEqualTo(fundAmountA),
                () -> assertThat(exchanger.dataInt("B_asset_balance")).isEqualTo(fundAmountB),
                () -> assertThat(exchanger.balance(tokenA)).isEqualTo(fundAmountA),
                () -> assertThat(exchanger.balance(tokenB)).isEqualTo(fundAmountB),
                () -> assertThat(exchanger.dataStr("A_asset_id")).isEqualTo(tokenA),
                () -> assertThat(exchanger.dataStr("B_asset_id")).isEqualTo(tokenB),
                () -> assertThat(exchanger.dataBool("active")).isTrue(),
                () -> assertThat(exchanger.dataInt("commission")).isEqualTo(commission),
                () -> assertThat(exchanger.dataInt("commission_scale_delimiter")).isEqualTo(commissionScaleDelimiter),
                () -> assertThat(exchanger.dataStr("version")).isEqualTo(version),
                () -> assertThat(exchanger.dataStr("share_asset_id")).isNotNull(),
                () -> assertThat(exchanger.dataInt("share_asset_supply")).isEqualTo(shareTokenSupply),
                () -> assertThat(firstCaller.balance(shareTokenIds.get(exchanger))).isEqualTo(shareTokenSupply)

        );
    }

    Stream<Arguments> aExchangerProvider() {
        return Stream.of(
                Arguments.of(firstExchanger, 100), Arguments.of(firstExchanger, 10000), Arguments.of(firstExchanger, 1899),
                Arguments.of(secondExchanger, 100), Arguments.of(secondExchanger, 10000), Arguments.of(secondExchanger, 2856),
                Arguments.of(thirdExchanger, 100), Arguments.of(thirdExchanger, 10000), Arguments.of(thirdExchanger, 1000)
        );
    }

    @ParameterizedTest(name = "firstCaller exchanges {1} tokenA")
    @MethodSource("aExchangerProvider")
    void b_canExchangeA(Account exchanger, int exchTokenAmount) {

        long tokenReceiveAmount = exchTokenAmount * (long) Math.pow(10, aDecimal);
        long amountTokenA = exchanger.dataInt("A_asset_balance");
        long amountTokenB = exchanger.dataInt("B_asset_balance");
        long callerBalanceA = firstCaller.balance(tokenA);
        long callerBalanceB = firstCaller.balance(tokenB);
        long shareTokenSuplyBefore = exchanger.dataInt("share_asset_supply");
        BigInteger tokenSendAmountWithoutFee =
                BigInteger.valueOf(tokenReceiveAmount)
                        .multiply(BigInteger.valueOf(amountTokenB))
                        .divide(BigInteger.valueOf(tokenReceiveAmount + amountTokenA));


        long tokenSendAmountWithFee = tokenSendAmountWithoutFee.multiply(BigInteger.valueOf(commissionScaleDelimiter - commission)).divide(BigInteger.valueOf(commissionScaleDelimiter)).longValue();
        long tokenSendGovernance = tokenSendAmountWithoutFee.longValue() * commisionGovernance / commissionScaleDelimiter;

        String invokeId = firstCaller.invokes(i -> i.dApp(exchanger).function("exchange", arg(tokenSendAmountWithFee)).payment(tokenReceiveAmount, tokenA).fee(1_00500000L)).getId().getBase58String();
        node().waitForTransaction(invokeId);

        assertAll("data and balances",
                () -> assertThat(exchanger.dataInt("A_asset_balance")).isEqualTo(amountTokenA + tokenReceiveAmount),
                () -> assertThat(exchanger.dataInt("B_asset_balance")).isEqualTo(amountTokenB - tokenSendAmountWithFee - tokenSendGovernance),
                () -> assertThat(exchanger.balance(tokenA)).isEqualTo(amountTokenA + tokenReceiveAmount),
                () -> assertThat(exchanger.balance(tokenB)).isEqualTo(amountTokenB - tokenSendAmountWithFee - tokenSendGovernance),
                () -> assertThat(firstCaller.balance(tokenA)).isEqualTo(callerBalanceA - tokenReceiveAmount),
                () -> assertThat(firstCaller.balance(tokenB)).isEqualTo(callerBalanceB + tokenSendAmountWithFee),
                () -> assertThat(exchanger.dataStr("A_asset_id")).isEqualTo(tokenA),
                () -> assertThat(exchanger.dataStr("B_asset_id")).isEqualTo(tokenB),
                () -> assertThat(exchanger.dataBool("active")).isTrue(),
                () -> assertThat(exchanger.dataInt("commission")).isEqualTo(commission),
                () -> assertThat(exchanger.dataInt("commission_scale_delimiter")).isEqualTo(commissionScaleDelimiter),
                () -> assertThat(exchanger.dataStr("version")).isEqualTo(version),
                () -> assertThat(exchanger.dataStr("share_asset_id")).isEqualTo(shareTokenIds.get(exchanger)),
                () -> assertThat(exchanger.dataInt("share_asset_supply")).isEqualTo(shareTokenSuplyBefore)

        );

    }

    Stream<Arguments> bExchangerProvider() {
        return Stream.of(
                Arguments.of(firstExchanger, 100), Arguments.of(firstExchanger, 10000), Arguments.of(firstExchanger, 1899),
                Arguments.of(secondExchanger, 100), Arguments.of(secondExchanger, 10000), Arguments.of(secondExchanger, 2856),
                Arguments.of(thirdExchanger, 100), Arguments.of(thirdExchanger, 10000), Arguments.of(thirdExchanger, 1000)
        );
    }

    @ParameterizedTest(name = "firstCaller exchanges {1} tokenB")
    @MethodSource("bExchangerProvider")
    void c_canExchangeB(Account exchanger, int exchTokenAmount) {

        long tokenReceiveAmount = exchTokenAmount * (long) Math.pow(10, bDecimal);
        long amountTokenA = exchanger.dataInt("A_asset_balance");
        long amountTokenB = exchanger.dataInt("B_asset_balance");
        long callerBalanceA = firstCaller.balance(tokenA);
        long callerBalanceB = firstCaller.balance(tokenB);
        long shareTokenSuplyBefore = exchanger.dataInt("share_asset_supply");
        BigInteger tokenSendAmountWithoutFee =
                BigInteger.valueOf(tokenReceiveAmount)
                        .multiply(BigInteger.valueOf(amountTokenA))
                        .divide(BigInteger.valueOf(tokenReceiveAmount + amountTokenB));

        long tokenSendAmountWithFee = tokenSendAmountWithoutFee.multiply(BigInteger.valueOf(commissionScaleDelimiter - commission)).divide(BigInteger.valueOf(commissionScaleDelimiter)).longValue();
        long tokenSendGovernance = tokenSendAmountWithoutFee.longValue() * commisionGovernance / commissionScaleDelimiter;

        String invokeId = firstCaller.invokes(i -> i.dApp(exchanger).function("exchange", arg(tokenSendAmountWithFee)).payment(tokenReceiveAmount, tokenB).fee(1_00500000L)).getId().getBase58String();
        node().waitForTransaction(invokeId);

        assertAll("data and balances",
                () -> assertThat(exchanger.dataInt("A_asset_balance")).isEqualTo(amountTokenA - tokenSendAmountWithFee - tokenSendGovernance),//91832344013287
                () -> assertThat(exchanger.dataInt("B_asset_balance")).isEqualTo(amountTokenB + tokenReceiveAmount),
                () -> assertThat(exchanger.balance(tokenA)).isEqualTo(amountTokenA - tokenSendAmountWithFee - tokenSendGovernance),
                () -> assertThat(exchanger.balance(tokenB)).isEqualTo(amountTokenB + tokenReceiveAmount),
                () -> assertThat(firstCaller.balance(tokenA)).isEqualTo(callerBalanceA + tokenSendAmountWithFee),
                () -> assertThat(firstCaller.balance(tokenB)).isEqualTo(callerBalanceB - tokenReceiveAmount),
                () -> assertThat(exchanger.dataStr("A_asset_id")).isEqualTo(tokenA),
                () -> assertThat(exchanger.dataStr("B_asset_id")).isEqualTo(tokenB),
                () -> assertThat(exchanger.dataBool("active")).isTrue(),
                () -> assertThat(exchanger.dataInt("commission")).isEqualTo(commission),
                () -> assertThat(exchanger.dataInt("commission_scale_delimiter")).isEqualTo(commissionScaleDelimiter),
                () -> assertThat(exchanger.dataStr("version")).isEqualTo(version),
                () -> assertThat(exchanger.dataStr("share_asset_id")).isEqualTo(shareTokenIds.get(exchanger)),
                () -> assertThat(exchanger.dataInt("share_asset_supply")).isEqualTo(shareTokenSuplyBefore)

        );
    }

    Stream<Arguments> replenishByTwiceProvider() {
        return Stream.of(
                Arguments.of(firstExchanger),
                Arguments.of(secondExchanger),
                Arguments.of(thirdExchanger));
    }

    @ParameterizedTest(name = "secondCaller replenish A/B by twice")
    @MethodSource("replenishByTwiceProvider")
    void d_secondCallerReplenishByTwice(Account exchanger) {
        long amountTokenABefore = exchanger.dataInt("A_asset_balance");
        long amountTokenBBefore = exchanger.dataInt("B_asset_balance");
        String transfer1 = firstCaller.transfers(t -> t
                .to(secondCaller)
                .amount(amountTokenABefore)
                .asset(tokenA)).getId().getBase58String();
        String transfer2 = firstCaller.transfers(t -> t
                .to(secondCaller)
                .amount(amountTokenBBefore)
                .asset(tokenB)).getId().getBase58String();
        node().waitForTransaction(transfer1);
        node().waitForTransaction(transfer2);
        long shareTokenSupplyBefore = exchanger.dataInt("share_asset_supply");
        String invokeId = secondCaller.invokes(i -> i.dApp(exchanger).function("replenishWithTwoTokens", arg(0)).payment(amountTokenABefore, tokenA).payment(amountTokenBBefore, tokenB).fee(1_00500000L)).getId().getBase58String();
        node().waitForTransaction(invokeId);

        long shareTokenToPay = (BigInteger.valueOf(amountTokenABefore).multiply(BigInteger.valueOf(shareTokenSupplyBefore)).divide(BigInteger.valueOf(amountTokenABefore))).longValue();

        assertAll("data and balances",
                () -> assertThat(exchanger.dataInt("A_asset_balance")).isEqualTo(amountTokenABefore + amountTokenABefore),
                () -> assertThat(exchanger.dataInt("B_asset_balance")).isEqualTo(amountTokenBBefore + amountTokenBBefore),
                () -> assertThat(exchanger.balance(tokenA)).isEqualTo(amountTokenABefore + amountTokenABefore),
                () -> assertThat(exchanger.balance(tokenB)).isEqualTo(amountTokenBBefore + amountTokenBBefore),
                () -> assertThat(exchanger.dataStr("A_asset_id")).isEqualTo(tokenA),
                () -> assertThat(exchanger.dataStr("B_asset_id")).isEqualTo(tokenB),
                () -> assertThat(exchanger.dataBool("active")).isTrue(),
                () -> assertThat(exchanger.dataInt("commission")).isEqualTo(commission),
                () -> assertThat(exchanger.dataInt("commission_scale_delimiter")).isEqualTo(commissionScaleDelimiter),
                () -> assertThat(exchanger.dataStr("version")).isEqualTo(version),
                () -> assertThat(exchanger.dataStr("share_asset_id")).isEqualTo(shareTokenIds.get(exchanger)),
                () -> assertThat(exchanger.dataInt("share_asset_supply")).isEqualTo(shareTokenSupplyBefore + shareTokenToPay),
                () -> assertThat(secondCaller.balance(shareTokenIds.get(exchanger))).isEqualTo(shareTokenToPay)

        );
    }

    Stream<Arguments> withdrawByTwiceProvider() {
        return Stream.of(
                Arguments.of(firstExchanger),
                Arguments.of(secondExchanger),
                Arguments.of(thirdExchanger));
    }

    @ParameterizedTest(name = "secondCaller withdraw A/B by twice")
    @MethodSource("withdrawByTwiceProvider")
    void e_secondCallerWithdrawAB(Account exchanger) {
        long dAppTokensAmountA = exchanger.balance(tokenA);
        long dAppTokensAmountB = exchanger.balance(tokenB);
        long secondCallerAmountA = secondCaller.balance(tokenA);
        long secondCallerAmountB = secondCaller.balance(tokenB);
        long shareTokenSupply = exchanger.dataInt("share_asset_supply");
        long secondCallerShareBalance = secondCaller.balance(shareTokenIds.get(exchanger));
        long tokensToPayA =
                BigDecimal.valueOf(secondCallerShareBalance)
                        .multiply(BigDecimal.valueOf(dAppTokensAmountA))
                        .divide(BigDecimal.valueOf(shareTokenSupply), 8, RoundingMode.HALF_DOWN).longValue();

        long tokensToPayB =
                BigDecimal.valueOf(secondCallerShareBalance)
                        .multiply(BigDecimal.valueOf(dAppTokensAmountB))
                        .divide(BigDecimal.valueOf(shareTokenSupply), 8, RoundingMode.HALF_DOWN).longValue();

        String invokeId = secondCaller.invokes(i -> i.dApp(exchanger).function("withdraw").payment(secondCallerShareBalance, shareTokenIds.get(exchanger)).fee(1_00500000L)).getId().getBase58String();
        node().waitForTransaction(invokeId);

        assertAll("data and balances",
                () -> assertThat(exchanger.dataInt("A_asset_balance")).isEqualTo(dAppTokensAmountA - tokensToPayA),
                () -> assertThat(exchanger.dataInt("B_asset_balance")).isEqualTo(dAppTokensAmountB - tokensToPayB),
                () -> assertThat(exchanger.balance(tokenA)).isEqualTo(dAppTokensAmountA - tokensToPayA),
                () -> assertThat(exchanger.balance(tokenB)).isEqualTo(dAppTokensAmountB - tokensToPayB),
                () -> assertThat(exchanger.dataStr("A_asset_id")).isEqualTo(tokenA),
                () -> assertThat(exchanger.dataStr("B_asset_id")).isEqualTo(tokenB),
                () -> assertThat(exchanger.dataBool("active")).isTrue(),
                () -> assertThat(exchanger.dataInt("commission")).isEqualTo(commission),
                () -> assertThat(exchanger.dataInt("commission_scale_delimiter")).isEqualTo(commissionScaleDelimiter),
                () -> assertThat(exchanger.dataStr("version")).isEqualTo(version),
                () -> assertThat(exchanger.dataStr("share_asset_id")).isEqualTo(shareTokenIds.get(exchanger)),
                () -> assertThat(exchanger.dataInt("share_asset_supply")).isEqualTo(shareTokenSupply - secondCallerShareBalance),
                () -> assertThat(secondCaller.balance(shareTokenIds.get(exchanger))).isEqualTo(0),
                () -> assertThat(secondCaller.balance(tokenA)).isEqualTo(secondCallerAmountA + tokensToPayA),
                () -> assertThat(secondCaller.balance(tokenB)).isEqualTo(secondCallerAmountB + tokensToPayB)

        );
    }

    Stream<Arguments> replenishProvider() {
        return Stream.of(
                Arguments.of(firstExchanger, 1L, 1), Arguments.of(firstExchanger, 100000L, 5), Arguments.of(firstExchanger, 26189L, 10),
                Arguments.of(secondExchanger, 50000L, 5), Arguments.of(secondExchanger, 100L, 10), Arguments.of(secondExchanger, 457382L, 20),
                Arguments.of(thirdExchanger, 35L, 3), Arguments.of(thirdExchanger, 1000L, 4), Arguments.of(thirdExchanger, 10004L, 7));
    }

    @ParameterizedTest(name = "secondCaller replenish A/B, slippage {2}")
    @MethodSource("replenishProvider")
    void f_canReplenishAB(Account exchanger, long replenishAmountB, int slippageTolerance) {
        long balanceA = exchanger.dataInt("A_asset_balance");
        long balanceB = exchanger.dataInt("B_asset_balance");
        long shareAssetSupply = exchanger.dataInt("share_asset_supply");
        String shareAssetId = exchanger.dataStr("share_asset_id");
        long callerShareBalance = firstCaller.balance(shareAssetId);
        int contractRatioMin = (1000 * (slippageToleranceDelimiter - slippageTolerance)) / slippageToleranceDelimiter;
        int contractRatioMax = (1000 * (slippageToleranceDelimiter + slippageTolerance)) / slippageToleranceDelimiter;
        long pmtAmountB = replenishAmountB * (long) Math.pow(10, bDecimal);

        Map<String, Long> insufficientTokenRatioAmounts = new HashMap<>();
        insufficientTokenRatioAmounts.put("pmtAmountA", aReplenishAmountByRatio(contractRatioMin - 1, pmtAmountB, balanceA, balanceB));
        insufficientTokenRatioAmounts.put("pmtAmountB", pmtAmountB);

        NodeError error = assertThrows(NodeError.class, () ->
                firstCaller.invokes(i -> i.dApp(exchanger).function("replenishWithTwoTokens", arg(slippageTolerance)).payment(insufficientTokenRatioAmounts.get("pmtAmountA"), tokenA).payment(insufficientTokenRatioAmounts.get("pmtAmountB"), tokenB).fee(1_00500000L))
        );
        assertTrue(error.getMessage().contains("Incorrect assets amount: amounts must have the contract ratio"));

        Map<String, Long> tooBigTokenRatioAmounts = new HashMap<>();
        tooBigTokenRatioAmounts.put("pmtAmountA", aReplenishAmountByRatio(contractRatioMax + 1, pmtAmountB, balanceA, balanceB));
        tooBigTokenRatioAmounts.put("pmtAmountB", pmtAmountB);

        NodeError error2 = assertThrows(NodeError.class, () ->
                firstCaller.invokes(i -> i.dApp(exchanger).function("replenishWithTwoTokens", arg(slippageTolerance)).payment(tooBigTokenRatioAmounts.get("pmtAmountA"), tokenA).payment(tooBigTokenRatioAmounts.get("pmtAmountB"), tokenB).fee(1_00500000L))
        );
        assertTrue(error2.getMessage().contains("Incorrect assets amount: amounts must have the contract ratio"));

        Map<String, Long> replenishAmounts = new HashMap<>();
        replenishAmounts.put("pmtAmountA", aReplenishAmountByRatio(contractRatioMin, pmtAmountB, balanceA, balanceB));
        replenishAmounts.put("pmtAmountB", pmtAmountB);

        String invokeId = firstCaller.invokes(i -> i.dApp(exchanger).function("replenishWithTwoTokens", arg(slippageTolerance)).payment(replenishAmounts.get("pmtAmountA"), tokenA).payment(replenishAmounts.get("pmtAmountB"), tokenB).fee(1_00500000L)).getId().getBase58String();
        node().waitForTransaction(invokeId);

        long ratioShareTokensInA = BigInteger.valueOf(replenishAmounts.get("pmtAmountA")).multiply(BigInteger.valueOf(scaleValue8)).divide(BigInteger.valueOf(balanceA)).longValue();
        long ratioShareTokensInB = BigInteger.valueOf(pmtAmountB).multiply(BigInteger.valueOf(scaleValue8)).divide(BigInteger.valueOf(balanceB)).longValue();

        long shareTokenToPayAmount = BigInteger.valueOf(Long.min(ratioShareTokensInA, ratioShareTokensInB)).multiply(BigInteger.valueOf(shareAssetSupply)).divide(BigInteger.valueOf(scaleValue8)).longValue();


        assertAll("data and balances",
                () -> assertThat(exchanger.dataInt("A_asset_balance")).isEqualTo(balanceA + replenishAmounts.get("pmtAmountA")),
                () -> assertThat(exchanger.dataInt("B_asset_balance")).isEqualTo(balanceB + pmtAmountB),
                () -> assertThat(exchanger.balance(tokenA)).isEqualTo(balanceA + replenishAmounts.get("pmtAmountA")),
                () -> assertThat(exchanger.balance(tokenB)).isEqualTo(balanceB + pmtAmountB),
                () -> assertThat(exchanger.dataStr("A_asset_id")).isEqualTo(tokenA),
                () -> assertThat(exchanger.dataStr("B_asset_id")).isEqualTo(tokenB),
                () -> assertThat(exchanger.dataBool("active")).isTrue(),
                () -> assertThat(exchanger.dataInt("commission")).isEqualTo(commission),
                () -> assertThat(exchanger.dataInt("commission_scale_delimiter")).isEqualTo(commissionScaleDelimiter),
                () -> assertThat(exchanger.dataStr("version")).isEqualTo(version),
                () -> assertThat(exchanger.dataStr("share_asset_id")).isNotNull(),
                () -> assertThat(exchanger.dataInt("share_asset_supply")).isEqualTo(shareAssetSupply + shareTokenToPayAmount),
                () -> assertThat(firstCaller.balance(shareAssetId)).isEqualTo(callerShareBalance + shareTokenToPayAmount)

        );
    }

    @Test
    void g_canShutdown() {
        secondCaller.invokes(i -> i.dApp(firstExchanger).function("shutdown").fee(900000L));
        assertThat(firstExchanger.dataBool("active")).isFalse();
        assertThat(firstExchanger.dataStr("shutdown_cause")).isEqualTo("Paused by admin");

        NodeError error = assertThrows(NodeError.class, () ->
                firstCaller.invokes(i -> i.dApp(firstExchanger).function("shutdown").fee(900000L)));
        assertTrue(error.getMessage().contains("Only admin can call this function"));
    }

    @Test
    void h_canActivate() {
        secondCaller.invokes(i -> i.dApp(firstExchanger).function("activate").fee(900000L));
        assertThat(firstExchanger.dataBool("active")).isTrue();
        NodeError error = assertThrows(NodeError.class, () -> firstExchanger.dataStr("shutdown_cause"));
        assertTrue(error.getMessage().contains("no data for this key"));

        NodeError error1 = assertThrows(NodeError.class, () ->
                firstCaller.invokes(i -> i.dApp(firstExchanger).function("activate").fee(900000L)));
        assertTrue(error1.getMessage().contains("Only admin can call this function"));
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
