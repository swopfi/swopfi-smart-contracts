package SWOP;

import com.wavesplatform.transactions.common.AssetId;
import com.wavesplatform.transactions.invocation.IntegerArg;
import im.mak.paddle.Account;
import com.wavesplatform.transactions.data.IntegerEntry;
import com.wavesplatform.transactions.invocation.StringArg;
import im.mak.paddle.exceptions.ApiError;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.*;
import static im.mak.paddle.util.Async.async;
import static im.mak.paddle.Node.node;
import static im.mak.paddle.util.Script.fromFile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
public class VotingNewPoolTest {
    private static final Account firstCaller = new Account(1000_00000000L);
    private static final Account secondCaller = new Account(1000_00000000L);
    private static final long firstCallerInitAmount = 1000_00000000L;
    private static final long secondCallerInitAmount = 1000_00000000L;
    private static final Account walletAddress = new Account(1_00000000L);
    private static final Account farming = new Account(1000_00000000L);
    private static final Account voting = new Account(100_00000000L);
    private static final Account governance = new Account(1000_00000000L);
    private static AssetId swopId;
    private static AssetId usdnId;
    private static AssetId firstAssetId;
    private static AssetId secondAssetId;
    private static AssetId thirdAssetId;
    private static AssetId fourthAssetId;
    private static final String firstPool = "3P5N94Qdb8SqJuy56p1btfzz1zACpPbqs6x";
    private static final String secondPool = "3PA26XNQfUzwNQHhSEbtKzRfYFvAcgj2Nfw";
    private static final String thirdPool = "3PLZSEaGDLht8GGK8rDfbY8zraHcXYHeiwP";
    private static final String fourthPool = "3P4D2zZJubRPbFTurHpCNS9HbFaNiw6mf7D";
    private static final String fifthPool = "3PPRh8DHaVTPqiv1Mes5amXq3Dujg7wSjZm";
    private static final String keyRewardPoolFractionCurrent = "_current_pool_fraction_reward";
    private static final String keyRewardPoolFractionPrevious = "_previous_pool_fraction_reward";
    private static final String keyRewardUpdateHeight = "reward_update_height";
    private static String votingScript;
    private static final String governanceScript = StringUtils.substringBefore(
            fromFile("dApps/SWOP/governance.ride")
                    .replace("3PQZWxShKGRgBN1qoJw6B4s9YWS9FneZTPg", voting.address().toString())
                    .replace("3P73HDkPqG15nLXevjCbmXtazHYTZbpPoPw", farming.address().toString())
                    .replace("DXDY2itiEcYBtGkVLnkpHtDFyWQUkoLJz79uJ7ECbMrA", firstCaller.publicKey().toString()),
            "@Verifier");
    @BeforeAll
    static void before() {
        async(
                () -> firstAssetId = firstCaller.issue(a -> a.quantity(Long.MAX_VALUE).name("FirstAsset").decimals(8)).tx().assetId(),
                () -> secondAssetId = firstCaller.issue(a -> a.quantity(Long.MAX_VALUE).name("SecondAsset").decimals(8)).tx().assetId(),
                () -> thirdAssetId = firstCaller.issue(a -> a.quantity(Long.MAX_VALUE).name("ThirdAsset").decimals(8)).tx().assetId(),
                () -> fourthAssetId = firstCaller.issue(a -> a.quantity(Long.MAX_VALUE).name("FourthAsset").decimals(8)).tx().assetId(),
               /* () -> governance.writeData(d -> d.data(
                        IntegerEntry.as(firstPool + keyRewardPoolFractionCurrent, 10_000000000L),
                        IntegerEntry.as(firstPool + keyRewardPoolFractionPrevious, 10_000000000L),
                        IntegerEntry.as(secondPool + keyRewardPoolFractionCurrent, 20_000000000L),
                        IntegerEntry.as(secondPool + keyRewardPoolFractionPrevious, 20_000000000L),
                        IntegerEntry.as(thirdPool + keyRewardPoolFractionCurrent, 30_000000000L),
                        IntegerEntry.as(thirdPool + keyRewardPoolFractionPrevious, 30_000000000L),
                        IntegerEntry.as(fourthPool + keyRewardPoolFractionCurrent, 15_000000000L),
                        IntegerEntry.as(fourthPool + keyRewardPoolFractionPrevious, 15_000000000L),
                        IntegerEntry.as(fifthPool + keyRewardPoolFractionCurrent, 25_000000000L),
                        IntegerEntry.as(fifthPool + keyRewardPoolFractionPrevious, 25_000000000L),
                        IntegerEntry.as(keyRewardUpdateHeight, node().getHeight()),
                        IntegerEntry.as(firstCaller.address().toString() + "_SWOP_amount", firstCallerInitAmount + 1),
                        IntegerEntry.as(secondCaller.address().toString() + "_SWOP_amount", secondCallerInitAmount + 1))),*/
                () -> {
                    async(
                            () -> {
                                swopId = firstCaller.issue(a -> a.quantity(Long.MAX_VALUE).name("SWOP").decimals(8)).tx().assetId();
                                farming.writeData(d -> d.string("SWOP_id", swopId.toString()));
                            },
                            () -> usdnId = firstCaller.issue(a -> a.quantity(Long.MAX_VALUE).name("USDN").decimals(6)).tx().assetId()
                    );
                    votingScript = StringUtils.substringBefore(
                            fromFile("dApps/SWOP/voting_for_new_pool.ride")
                                    .replace("3PLHVWCqA9DJPDbadUofTohnCULLauiDWhS", governance.address().toString())
                                    .replace("3P6J84oH51DzY6xk2mT5TheXRbrCwBMxonp", walletAddress.address().toString())
                                    .replace("DXDY2itiEcYBtGkVLnkpHtDFyWQUkoLJz79uJ7ECbMrA", firstCaller.publicKey().toString())
                                    .replace("Ehie5xYpeN8op1Cctc6aGUrqx8jq3jtf1DSjXDbfm7aT", swopId.toString())
                                    .replace("DG2xFkPdDwKUoBkzGAhQtLpSGzfXLiCYPEzeKH2Ad24p", usdnId.toString()),
                            "@Verifier");
                });
        //governance.setScript(s -> s.script(governanceScript));
    }

    @Test //initVotingForNewPool 1
    void addNewVoting() {
        Account votingDApp = new Account(10_00000000L);
        votingDApp.setScript(votingScript);
        votingDApp.invoke(i -> i.dApp(votingDApp).function("init"));
        long lastPoolId = 0;
        
        int height = firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                StringArg.as(firstAssetId.toString()),
                StringArg.as(usdnId.toString())
        ).payment(10_00000000L, swopId)).height();
        
        assertAll("state after init pool",
                () -> assertThat(votingDApp.getIntegerData(firstAssetId.toString() + "_" + usdnId.toString() + "_" + lastPoolId +"_voting")).isEqualTo(lastPoolId),
                () -> assertThat(votingDApp.getBooleanData(lastPoolId + "_status")).isEqualTo(true),
                () -> assertThat(votingDApp.getIntegerData(lastPoolId + "_finish_height")).isEqualTo(height + 1443 * 5),
                () -> assertThat(votingDApp.getIntegerData("voting_active_number")).isEqualTo(1),
                () -> assertThat(votingDApp.getIntegerData("voting_id_last")).isEqualTo(lastPoolId + 1)
        );
    }

    @Test //initVotingForNewPool 2,3,4,5,6
    void initVotingForNewPoolErrorPayment() {
        Account votingDApp = new Account(10_00000000L);
        votingDApp.setScript(votingScript);
        votingDApp.invoke(i -> i.dApp(votingDApp).function("init"));
        
        assertThat(assertThrows(ApiError.class, () ->
                firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(firstAssetId.toString()),
                        StringArg.as(usdnId.toString())
                ).payment(10_00000001L, swopId)))
        ).hasMessageContaining("You need to attach 10 SWOP tokens");
        
        assertThat(assertThrows(ApiError.class, () ->
                firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(firstAssetId.toString()),
                        StringArg.as(swopId.toString())
                ).payment(9_99999999L, swopId)))
        ).hasMessageContaining("You need to attach 10 SWOP tokens");
        
        assertThat(assertThrows(ApiError.class, () ->
                firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(secondAssetId.toString()),
                        StringArg.as("WAVES")
                ).payment(10_00000000L, AssetId.WAVES)))
        ).hasMessageContaining("You must use a SWOP token");
        
        assertThat(assertThrows(ApiError.class, () ->
                firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(secondAssetId.toString()),
                        StringArg.as(usdnId.toString())
                ).payment(10_00000000L, swopId).payment(1_00000000L, AssetId.WAVES)))
        ).hasMessageContaining("One attached asset expected");
        
        assertThat(assertThrows(ApiError.class, () ->
                firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(firstAssetId.toString()),
                        StringArg.as(usdnId.toString())
                )))
        ).hasMessageContaining("One attached asset expected");
    }

    @Test //initVotingForNewPool 7,8
    void createPoolWhenFiveActiveOrPoolExists() {
        Account votingDApp = new Account(10_00000000L);
        votingDApp.setScript(votingScript);
        votingDApp.invoke(i -> i.dApp(votingDApp).function("init"));

        async(
                () -> firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(firstAssetId.toString()),
                        StringArg.as("WAVES")
                ).payment(10_00000000L, swopId)),
                () -> firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(secondAssetId.toString()),
                        StringArg.as("WAVES")
                ).payment(10_00000000L, swopId)),
                () -> firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(firstAssetId.toString()),
                        StringArg.as(usdnId.toString())
                ).payment(10_00000000L, swopId)),
                () -> firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(secondAssetId.toString()),
                        StringArg.as(usdnId.toString())
                ).payment(10_00000000L, swopId)),
                () -> firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(firstAssetId.toString()),
                        StringArg.as(swopId.toString())
                ).payment(10_00000000L, swopId))
        );
        
        assertThat(assertThrows(ApiError.class, () ->
                firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(secondAssetId.toString()),
                        StringArg.as(swopId.toString())
                ).payment(10_00000000L, swopId)))
        ).hasMessageContaining("Too many votings. Maximum quantity: 5");
        
        votingDApp.writeData(d -> d.data(
                IntegerEntry.as("4_finish_height", node().getHeight() - 1)
        ));
        
        assertThat(assertThrows(ApiError.class, () ->
                firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(firstAssetId.toString()),
                        StringArg.as("WAVES")
                ).payment(10_00000000L, swopId)))
        ).hasMessageContaining("This pool already exists");
    }

    @Test //initVotingForNewPool 9
    void addNewVotingSameAsUnactive() {
        Account votingDApp = new Account(10_00000000L);
        votingDApp.setScript(votingScript);
        votingDApp.invoke(i -> i.dApp(votingDApp).function("init"));

        async(
                () -> firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(firstAssetId.toString()),
                        StringArg.as("WAVES")
                ).payment(10_00000000L, swopId)),
                () -> firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(secondAssetId.toString()),
                        StringArg.as("WAVES")
                ).payment(10_00000000L, swopId)),
                () -> firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(firstAssetId.toString()),
                        StringArg.as(usdnId.toString())
                ).payment(10_00000000L, swopId)),
                () -> firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(secondAssetId.toString()),
                        StringArg.as(usdnId.toString())
                ).payment(10_00000000L, swopId))
        );
        firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                StringArg.as(firstAssetId.toString()),
                StringArg.as(swopId.toString())
        ).payment(10_00000000L, swopId));
        votingDApp.writeData(d -> d.data(
                IntegerEntry.as("4_finish_height", node().getHeight() - 1)
        ));
        
        long lastPoolId = votingDApp.getIntegerData("voting_id_last");
        int height = firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                StringArg.as(firstAssetId.toString()),
                StringArg.as(swopId.toString())
        ).payment(10_00000000L, swopId)).height();
        
        assertAll("state after init pool",
                () -> assertThat(votingDApp.getIntegerData(firstAssetId.toString() + "_" + swopId.toString() + "_" + lastPoolId +"_voting")).isEqualTo(lastPoolId),
                () -> assertThat(votingDApp.getBooleanData(lastPoolId + "_status")).isEqualTo(true),
                () -> assertThat(votingDApp.getIntegerData(lastPoolId + "_finish_height")).isEqualTo(height + 1443 * 5),
                () -> assertThat(votingDApp.getIntegerData("voting_active_number")).isEqualTo(5),
                () -> assertThat(votingDApp.getIntegerData("voting_id_last")).isEqualTo(lastPoolId + 1)
        );
    }

    @Test //initVotingForNewPool 10
    void addNewVotingWithSameAAssetAndOtherBAsset() {
        Account votingDApp = new Account(10_00000000L);
        votingDApp.setScript(votingScript);
        votingDApp.invoke(i -> i.dApp(votingDApp).function("init"));

       firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                StringArg.as(firstAssetId.toString()),
                StringArg.as("WAVES")
        ).payment(10_00000000L, swopId));
       
        long lastPoolId = votingDApp.getIntegerData("voting_id_last");
        int height = firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                StringArg.as(firstAssetId.toString()),
                StringArg.as(usdnId.toString())
        ).payment(10_00000000L, swopId)).height();
        
        assertAll("state after init pool",
                () -> assertThat(votingDApp.getIntegerData(firstAssetId.toString() + "_" + usdnId.toString() + "_" + lastPoolId +"_voting")).isEqualTo(lastPoolId),
                () -> assertThat(votingDApp.getBooleanData(lastPoolId + "_status")).isEqualTo(true),
                () -> assertThat(votingDApp.getIntegerData(lastPoolId + "_finish_height")).isEqualTo(height + 1443 * 5),
                () -> assertThat(votingDApp.getIntegerData("voting_active_number")).isEqualTo(2),
                () -> assertThat(votingDApp.getIntegerData("voting_id_last")).isEqualTo(lastPoolId + 1)
        );
    }

    @Test //initVotingForNewPool 11
    void addNewVotingWhenFourUnactiveZeroActive() {
        Account votingDApp = new Account(10_00000000L);
        votingDApp.setScript(votingScript);
        votingDApp.invoke(i -> i.dApp(votingDApp).function("init"));

        async(
                () ->  firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(firstAssetId.toString()),
                        StringArg.as("WAVES")
                ).payment(10_00000000L, swopId)),
                () -> firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(secondAssetId.toString()),
                        StringArg.as("WAVES")
                ).payment(10_00000000L, swopId)),
                () -> firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(firstAssetId.toString()),
                        StringArg.as(usdnId.toString())
                ).payment(10_00000000L, swopId)),
                () -> firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(secondAssetId.toString()),
                        StringArg.as(usdnId.toString())
                ).payment(10_00000000L, swopId))
        );
        votingDApp.writeData(d -> d.data(
                IntegerEntry.as("0_finish_height", node().getHeight() - 1),
                IntegerEntry.as("1_finish_height", node().getHeight() - 1),
                IntegerEntry.as("2_finish_height", node().getHeight() - 1),
                IntegerEntry.as("3_finish_height", node().getHeight() - 1)
        ));
        
        long lastPoolId = votingDApp.getIntegerData("voting_id_last");
        int height = firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                StringArg.as(firstAssetId.toString()),
                StringArg.as(usdnId.toString())
        ).payment(10_00000000L, swopId)).height();
        
        assertAll("state after init pool",
                () -> assertThat(votingDApp.getIntegerData(firstAssetId.toString() + "_" + usdnId.toString() + "_" + lastPoolId +"_voting")).isEqualTo(lastPoolId),
                () -> assertThat(votingDApp.getBooleanData(lastPoolId + "_status")).isEqualTo(true),
                () -> assertThat(votingDApp.getIntegerData(lastPoolId + "_finish_height")).isEqualTo(height + 1443 * 5),
                () -> assertThat(votingDApp.getIntegerData("voting_active_number")).isEqualTo(1),
                () -> assertThat(votingDApp.getIntegerData("voting_id_last")).isEqualTo(lastPoolId + 1)
        );

    }

    @Test //initVotingForNewPool 12
    void addNewVotingWhenFourUnactiveFourActive() {
        Account votingDApp = new Account(10_00000000L);
        votingDApp.setScript(votingScript);
        votingDApp.invoke(i -> i.dApp(votingDApp).function("init"));
        
        async(
                () ->  firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(firstAssetId.toString()),
                        StringArg.as("WAVES")
                ).payment(10_00000000L, swopId)),
                () -> firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(secondAssetId.toString()),
                        StringArg.as("WAVES")
                ).payment(10_00000000L, swopId)),
                () -> firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(thirdAssetId.toString()),
                        StringArg.as("WAVES")
                ).payment(10_00000000L, swopId)),
                () -> firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(firstAssetId.toString()),
                        StringArg.as(usdnId.toString())
                ).payment(10_00000000L, swopId)),
                () -> firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(secondAssetId.toString()),
                        StringArg.as(usdnId.toString())
                ).payment(10_00000000L, swopId))
        );

        votingDApp.writeData(d -> d.data(
                IntegerEntry.as("0_finish_height", node().getHeight() - 1),
                IntegerEntry.as("1_finish_height", node().getHeight() - 1),
                IntegerEntry.as("2_finish_height", node().getHeight() - 1),
                IntegerEntry.as("3_finish_height", node().getHeight() - 1)
        ));
        
        async(
                () ->  firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(thirdAssetId.toString()),
                        StringArg.as(usdnId.toString())
                ).payment(10_00000000L, swopId)),
                () ->  firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(firstAssetId.toString()),
                        StringArg.as(swopId.toString())
                ).payment(10_00000000L, swopId)),
                () -> firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(secondAssetId.toString()),
                        StringArg.as(swopId.toString())
                ).payment(10_00000000L, swopId))
        );
        
        long lastPoolId = votingDApp.getIntegerData("voting_id_last");
        int height = firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                StringArg.as(fourthAssetId.toString()),
                StringArg.as(usdnId.toString())
        ).payment(10_00000000L, swopId)).height();
        
        assertAll("state after init pool",
                () -> assertThat(votingDApp.getIntegerData(fourthAssetId.toString() + "_" + usdnId.toString() + "_" + lastPoolId +"_voting")).isEqualTo(lastPoolId),
                () -> assertThat(votingDApp.getBooleanData(lastPoolId + "_status")).isEqualTo(true),
                () -> assertThat(votingDApp.getIntegerData(lastPoolId + "_finish_height")).isEqualTo(height + 1443 * 5),
                () -> assertThat(votingDApp.getIntegerData("voting_active_number")).isEqualTo(5),
                () -> assertThat(votingDApp.getIntegerData("voting_id_last")).isEqualTo(lastPoolId + 1)
        );
    }

    @Test //initVotingForNewPool 13
    void addNewVotingWhenFiveUnactiveFourActive() {
        Account votingDApp = new Account(10_00000000L);
        votingDApp.setScript(votingScript);
        votingDApp.invoke(i -> i.dApp(votingDApp).function("init"));
        
        async(
                () ->  firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(firstAssetId.toString()),
                        StringArg.as("WAVES")
                ).payment(10_00000000L, swopId)),
                () -> firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(secondAssetId.toString()),
                        StringArg.as("WAVES")
                ).payment(10_00000000L, swopId)),
                () -> firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(thirdAssetId.toString()),
                        StringArg.as("WAVES")
                ).payment(10_00000000L, swopId)),
                () -> firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(firstAssetId.toString()),
                        StringArg.as(usdnId.toString())
                ).payment(10_00000000L, swopId)),
                () -> firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(secondAssetId.toString()),
                        StringArg.as(usdnId.toString())
                ).payment(10_00000000L, swopId))
        );

        votingDApp.writeData(d -> d.data(
                IntegerEntry.as("0_finish_height", node().getHeight() - 1),
                IntegerEntry.as("1_finish_height", node().getHeight() - 1),
                IntegerEntry.as("2_finish_height", node().getHeight() - 1),
                IntegerEntry.as("3_finish_height", node().getHeight() - 1),
                IntegerEntry.as("4_finish_height", node().getHeight() - 1)
        ));
        async(
                () ->  firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(thirdAssetId.toString()),
                        StringArg.as(usdnId.toString())
                ).payment(10_00000000L, swopId)),
                () ->  firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(firstAssetId.toString()),
                        StringArg.as(swopId.toString())
                ).payment(10_00000000L, swopId)),
                () -> firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(secondAssetId.toString()),
                        StringArg.as(swopId.toString())
                ).payment(10_00000000L, swopId)),
                () -> firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(thirdAssetId.toString()),
                        StringArg.as(swopId.toString())
                ).payment(10_00000000L, swopId))
        );

        long lastPoolId = votingDApp.getIntegerData("voting_id_last");
        int height = firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                StringArg.as(fourthAssetId.toString()),
                StringArg.as(swopId.toString())
        ).payment(10_00000000L, swopId)).height();

        assertAll("state after init pool",
                () -> assertThat(votingDApp.getIntegerData(fourthAssetId.toString() + "_" + swopId.toString() + "_" + lastPoolId +"_voting")).isEqualTo(lastPoolId),
                () -> assertThat(votingDApp.getBooleanData(lastPoolId + "_status")).isEqualTo(true),
                () -> assertThat(votingDApp.getIntegerData(lastPoolId + "_finish_height")).isEqualTo(height + 1443 * 5),
                () -> assertThat(votingDApp.getIntegerData("voting_active_number")).isEqualTo(5),
                () -> assertThat(votingDApp.getIntegerData("voting_id_last")).isEqualTo(lastPoolId + 1)
        );
    }

    @Test //initVotingForNewPool 14
    void NoneNetworkAsset() {
        Account votingDApp = new Account(10_00000000L);
        votingDApp.setScript(votingScript);
        votingDApp.invoke(i -> i.dApp(votingDApp).function("init"));

        assertThat(assertThrows(ApiError.class, () ->
                firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as("2HAJrwa8q4SxBx9cHYaBTQdBjdk5wwqdof7ccpAx2uhZ"),
                        StringArg.as(usdnId.toString())
                ).payment(10_00000000L, swopId)))
        ).hasMessageContaining("Asset is not define");
    }

    @Test //initVotingForNewPool 15
    void NotRealAsset() {
        Account votingDApp = new Account(10_00000000L);
        votingDApp.setScript(votingScript);
        votingDApp.invoke(i -> i.dApp(votingDApp).function("init"));

        assertThat(assertThrows(ApiError.class, () ->
                firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as("2HAJrwa8q4SxBx9cHYaBTQdBjdk5wwqdof7ccpAx2uhZawefawfaw"),
                        StringArg.as(usdnId.toString())
                ).payment(10_00000000L, swopId)))
        ).hasMessageContaining("Asset is not define");
    }


    @Test //initVotingForNewPool 16
    void EmptyAsset() {
        Account votingDApp = new Account(10_00000000L);
        votingDApp.setScript(votingScript);
        votingDApp.invoke(i -> i.dApp(votingDApp).function("init"));

        assertThat(assertThrows(ApiError.class, () ->
                firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(""),
                        StringArg.as(usdnId.toString())
                ).payment(10_00000000L, swopId)))
        ).hasMessageContaining("Assets can't be empty");
    }

    @Test //initVotingForNewPool 17
    void NewPoolWithWaves() {
        Account votingDApp = new Account(10_00000000L);
        votingDApp.setScript(votingScript);
        votingDApp.invoke(i -> i.dApp(votingDApp).function("init"));

        long lastPoolId = 0;
        int height = firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                StringArg.as(firstAssetId.toString()),
                StringArg.as("WAVES")
        ).payment(10_00000000L, swopId)).height();

        assertAll("state after init pool",
                () -> assertThat(votingDApp.getIntegerData(firstAssetId.toString() + "_WAVES_" + lastPoolId +"_voting")).isEqualTo(lastPoolId),
                () -> assertThat(votingDApp.getBooleanData(lastPoolId + "_status")).isEqualTo(true),
                () -> assertThat(votingDApp.getIntegerData(lastPoolId + "_finish_height")).isEqualTo(height + 1443 * 5),
                () -> assertThat(votingDApp.getIntegerData("voting_active_number")).isEqualTo(1),
                () -> assertThat(votingDApp.getIntegerData("voting_id_last")).isEqualTo(lastPoolId + 1)
        );
    }

    @Test //initVotingForNewPool 18
    void NewPoolWithWavesUsdnPair() {
        Account votingDApp = new Account(10_00000000L);
        votingDApp.setScript(votingScript);
        votingDApp.invoke(i -> i.dApp(votingDApp).function("init"));

        long lastPoolId = 0;
        int height = firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                StringArg.as("WAVES"),
                StringArg.as(usdnId.toString())
        ).payment(10_00000000L, swopId)).height();

        assertAll("state after init pool",
                () -> assertThat(votingDApp.getIntegerData("WAVES_" + usdnId.toString() + "_" + lastPoolId +"_voting")).isEqualTo(lastPoolId),
                () -> assertThat(votingDApp.getBooleanData(lastPoolId + "_status")).isEqualTo(true),
                () -> assertThat(votingDApp.getIntegerData(lastPoolId + "_finish_height")).isEqualTo(height + 1443 * 5),
                () -> assertThat(votingDApp.getIntegerData("voting_active_number")).isEqualTo(1),
                () -> assertThat(votingDApp.getIntegerData("voting_id_last")).isEqualTo(lastPoolId + 1)
        );
    }

    @Test //initVotingForNewPool 19
    void NewPoolBAssetOther() {
        Account votingDApp = new Account(10_00000000L);
        votingDApp.setScript(votingScript);
        votingDApp.invoke(i -> i.dApp(votingDApp).function("init"));

        assertThat(assertThrows(ApiError.class, () ->
                firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(firstAssetId.toString()),
                        StringArg.as(secondAssetId.toString())
                ).payment(10_00000000L, swopId)))
        ).hasMessageContaining("assetB must be USDN, WAVES or SWOP");
    }

    @Test //initVotingForNewPool 20
    void NewPoolAAssetWavesBAssetOther() {
        Account votingDApp = new Account(10_00000000L);
        votingDApp.setScript(votingScript);
        votingDApp.invoke(i -> i.dApp(votingDApp).function("init"));

        assertThat(assertThrows(ApiError.class, () ->
                firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as("WAVES"),
                        StringArg.as(secondAssetId.toString())
                ).payment(10_00000000L, swopId)))
        ).hasMessageContaining("assetB must be USDN, WAVES or SWOP");
    }

    @Test //initVotingForNewPool 21
    void NewPoolAAssetSWOPBAssetOther() {
        Account votingDApp = new Account(10_00000000L);
        votingDApp.setScript(votingScript);
        votingDApp.invoke(i -> i.dApp(votingDApp).function("init"));

        assertThat(assertThrows(ApiError.class, () ->
                firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(swopId.toString()),
                        StringArg.as(secondAssetId.toString())
                ).payment(10_00000000L, swopId)))
        ).hasMessageContaining("assetB must be USDN, WAVES or SWOP");
    }

    @Test //initVotingForNewPool 22
    void NewPoolAAssetUSDNBAssetOther() {
        Account votingDApp = new Account(10_00000000L);
        votingDApp.setScript(votingScript);
        votingDApp.invoke(i -> i.dApp(votingDApp).function("init"));

        assertThat(assertThrows(ApiError.class, () ->
                firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(usdnId.toString()),
                        StringArg.as(secondAssetId.toString())
                ).payment(10_00000000L, swopId)))
        ).hasMessageContaining("assetB must be USDN, WAVES or SWOP");
    }

    @Test //initVotingForNewPool 23
    void NewDuplicatePoolWithAssetsVotedAfterFiveNotActive() {
        Account votingDApp = new Account(10_00000000L);
        votingDApp.setScript(votingScript);
        votingDApp.invoke(i -> i.dApp(votingDApp).function("init"));

        async(
                () -> firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(firstAssetId.toString()),
                        StringArg.as("WAVES")
                ).payment(10_00000000L, swopId)),
                () -> firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(secondAssetId.toString()),
                        StringArg.as("WAVES")
                ).payment(10_00000000L, swopId)),
                () -> firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(firstAssetId.toString()),
                        StringArg.as(usdnId.toString())
                ).payment(10_00000000L, swopId)),
                () -> firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(secondAssetId.toString()),
                        StringArg.as(usdnId.toString())
                ).payment(10_00000000L, swopId)),
                () -> firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(firstAssetId.toString()),
                        StringArg.as(swopId.toString())
                ).payment(10_00000000L, swopId))
        );
        votingDApp.writeData(d -> d.data(
                IntegerEntry.as("0_finish_height", node().getHeight() - 1),
                IntegerEntry.as("1_finish_height", node().getHeight() - 1),
                IntegerEntry.as("2_finish_height", node().getHeight() - 1),
                IntegerEntry.as("3_finish_height", node().getHeight() - 1),
                IntegerEntry.as("4_finish_height", node().getHeight() - 1)
        ));

        long lastPoolId = votingDApp.getIntegerData("voting_id_last");
        int height = firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                StringArg.as(firstAssetId.toString()),
                StringArg.as("WAVES")
        ).payment(10_00000000L, swopId)).height();

        assertAll("state after init pool",
                () -> assertThat(votingDApp.getIntegerData(firstAssetId.toString() + "_WAVES_" + lastPoolId +"_voting")).isEqualTo(lastPoolId),
                () -> assertThat(votingDApp.getBooleanData(lastPoolId + "_status")).isEqualTo(true),
                () -> assertThat(votingDApp.getIntegerData(lastPoolId + "_finish_height")).isEqualTo(height + 1443 * 5),
                () -> assertThat(votingDApp.getIntegerData("voting_active_number")).isEqualTo(1),
                () -> assertThat(votingDApp.getIntegerData("voting_id_last")).isEqualTo(lastPoolId + 1)
        );
    }

    @Test //initVotingForNewPool 24
    void NewPoolWhenFiveActive() {
        Account votingDApp = new Account(10_00000000L);
        votingDApp.setScript(votingScript);
        votingDApp.invoke(i -> i.dApp(votingDApp).function("init"));

        async(
                () -> firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(firstAssetId.toString()),
                        StringArg.as("WAVES")
                ).payment(10_00000000L, swopId)),
                () -> firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(secondAssetId.toString()),
                        StringArg.as("WAVES")
                ).payment(10_00000000L, swopId)),
                () -> firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(firstAssetId.toString()),
                        StringArg.as(usdnId.toString())
                ).payment(10_00000000L, swopId)),
                () -> firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(secondAssetId.toString()),
                        StringArg.as(usdnId.toString())
                ).payment(10_00000000L, swopId)),
                () -> firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(firstAssetId.toString()),
                        StringArg.as(swopId.toString())
                ).payment(10_00000000L, swopId))
        );
        votingDApp.writeData(d -> d.data(
                IntegerEntry.as("4_finish_height", node().getHeight() - 1)
        ));
        firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                StringArg.as("WAVES"),
                StringArg.as(swopId.toString())
        ).payment(10_00000000L, swopId));

        assertThat(assertThrows(ApiError.class, () ->
                firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(secondAssetId.toString()),
                        StringArg.as(swopId.toString())
                ).payment(10_00000000L, swopId)))
        ).hasMessageContaining("Too many votings. Maximum quantity: 5");

    }

    @Test //initVotingForNewPool 25
    void NewPoolWhenLastCanceled() {
        Account votingDApp = new Account(10_00000000L);
        votingDApp.setScript(votingScript);
        votingDApp.invoke(i -> i.dApp(votingDApp).function("init"));

        async(
                () -> firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(firstAssetId.toString()),
                        StringArg.as("WAVES")
                ).payment(10_00000000L, swopId)),
                () -> firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(secondAssetId.toString()),
                        StringArg.as("WAVES")
                ).payment(10_00000000L, swopId)),
                () -> firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(firstAssetId.toString()),
                        StringArg.as(usdnId.toString())
                ).payment(10_00000000L, swopId)),
                () -> firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(secondAssetId.toString()),
                        StringArg.as(usdnId.toString())
                ).payment(10_00000000L, swopId)),
                () -> firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(firstAssetId.toString()),
                        StringArg.as(swopId.toString())
                ).payment(10_00000000L, swopId))
        );
        firstCaller.invoke( i -> i.dApp(votingDApp).function("cancelVoting",
                IntegerArg.as(4),
                StringArg.as("because I can")
        ));

        long lastPoolId = votingDApp.getIntegerData("voting_id_last");
        int height = firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                StringArg.as(secondAssetId.toString()),
                StringArg.as(swopId.toString())
        ).payment(10_00000000L, swopId)).height();

        assertAll("state after init pool",
                () -> assertThat(votingDApp.getIntegerData(secondAssetId.toString() + "_" + swopId.toString() + "_" + lastPoolId +"_voting")).isEqualTo(lastPoolId),
                () -> assertThat(votingDApp.getBooleanData(lastPoolId + "_status")).isEqualTo(true),
                () -> assertThat(votingDApp.getIntegerData(lastPoolId + "_finish_height")).isEqualTo(height + 1443 * 5),
                () -> assertThat(votingDApp.getIntegerData("voting_active_number")).isEqualTo(5),
                () -> assertThat(votingDApp.getIntegerData("voting_id_last")).isEqualTo(lastPoolId + 1)
        );
    }

    @Test //initVotingForNewPool 26
    void addDuplicatePoolAfterCancel() {
        Account votingDApp = new Account(10_00000000L);
        votingDApp.setScript(votingScript);
        votingDApp.invoke(i -> i.dApp(votingDApp).function("init"));

        async(
                () -> firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(firstAssetId.toString()),
                        StringArg.as("WAVES")
                ).payment(10_00000000L, swopId)),
                () -> firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(secondAssetId.toString()),
                        StringArg.as("WAVES")
                ).payment(10_00000000L, swopId)),
                () -> firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(firstAssetId.toString()),
                        StringArg.as(usdnId.toString())
                ).payment(10_00000000L, swopId)),
                () -> firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as(secondAssetId.toString()),
                        StringArg.as(usdnId.toString())
                ).payment(10_00000000L, swopId))
        );
        firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                StringArg.as(firstAssetId.toString()),
                StringArg.as(swopId.toString())
        ).payment(10_00000000L, swopId));
        firstCaller.invoke( i -> i.dApp(votingDApp).function("cancelVoting",
                IntegerArg.as(4),
                StringArg.as("because I can")
        ));

        long lastPoolId = votingDApp.getIntegerData("voting_id_last");
        int height = firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                StringArg.as(firstAssetId.toString()),
                StringArg.as(swopId.toString())
        ).payment(10_00000000L, swopId)).height();

        assertAll("state after init pool",
                () -> assertThat(votingDApp.getIntegerData(firstAssetId.toString() + "_" + swopId.toString() + "_" + lastPoolId +"_voting")).isEqualTo(lastPoolId),
                () -> assertThat(votingDApp.getBooleanData(lastPoolId + "_status")).isEqualTo(true),
                () -> assertThat(votingDApp.getIntegerData(lastPoolId + "_finish_height")).isEqualTo(height + 1443 * 5),
                () -> assertThat(votingDApp.getIntegerData("voting_active_number")).isEqualTo(5),
                () -> assertThat(votingDApp.getIntegerData("voting_id_last")).isEqualTo(lastPoolId + 1)
        );
    }

    @Test //initVotingForNewPool 27
    void addVotingWithSameAssets() {
        Account votingDApp = new Account(10_00000000L);
        votingDApp.setScript(votingScript);
        votingDApp.invoke(i -> i.dApp(votingDApp).function("init"));

        assertThat(assertThrows(ApiError.class, () ->
                firstCaller.invoke( i -> i.dApp(votingDApp).function("initVotingForNewPool",
                        StringArg.as("WAVES"),
                        StringArg.as("WAVES")
                ).payment(10_00000000L, swopId)))
        ).hasMessageContaining("Assets must be different");
    }
}
