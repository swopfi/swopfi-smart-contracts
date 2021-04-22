package dapps;

import com.wavesplatform.transactions.account.Address;
import com.wavesplatform.transactions.account.PrivateKey;
import com.wavesplatform.transactions.account.PublicKey;
import com.wavesplatform.transactions.invocation.Function;
import com.wavesplatform.transactions.invocation.IntegerArg;
import com.wavesplatform.transactions.invocation.ListArg;
import com.wavesplatform.transactions.invocation.StringArg;
import im.mak.paddle.dapp.DApp;
import im.mak.paddle.dapp.DAppCall;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

import static im.mak.paddle.util.Script.fromFile;
import static java.util.stream.Collectors.toList;

public class GovernanceDApp extends DApp {

    public GovernanceDApp(PrivateKey pk, long initialBalance, PublicKey farming, Address voting) {
        super(pk, initialBalance, StringUtils.substringBefore(
                fromFile("dApps/SWOP/governance.ride")
                        .replace("3PQZWxShKGRgBN1qoJw6B4s9YWS9FneZTPg", voting.toString())
                        .replace("3P73HDkPqG15nLXevjCbmXtazHYTZbpPoPw", farming.address().toString())
                        .replace("DXDY2itiEcYBtGkVLnkpHtDFyWQUkoLJz79uJ7ECbMrA", farming.toString())
                        .replace("E6Wa1SGoktYcjHjsKrvjMiqJY3SWmGKcD8Q5L8kxSPS7", farming.toString())
                        .replace("AZmWJtuy4GeVrMmJH4hfFBRApe1StvhJSk4jcbT6bArQ", farming.toString()),
                "@Verifier"));
    }

    public DAppCall airDrop() {
        return new DAppCall(address(), Function.as("airDrop"));
    }

    public DAppCall lockSWOP() {
        return new DAppCall(address(), Function.as("lockSWOP"));
    }

    public DAppCall withdrawSWOP(long withdrawAmount) {
        return new DAppCall(address(), Function.as("withdrawSWOP", IntegerArg.as(withdrawAmount)));
    }

    public DAppCall claimAndWithdrawSWOP() {
        return new DAppCall(address(), Function.as("claimAndWithdrawSWOP"));
    }

    public DAppCall claimAndStakeSWOP() {
        return new DAppCall(address(), Function.as("claimAndStakeSWOP"));
    }

    public DAppCall updateWeights(List<String> previousPools, List<Long> previousRewards, List<String> currentPools, List<Long> currentRewards, int rewardUpdateHeight) {
        return new DAppCall(address(), Function.as("updateWeights",
                ListArg.as(previousPools.stream().map(StringArg::as).collect(toList())),
                ListArg.as(previousRewards.stream().map(IntegerArg::as).collect(toList())),
                ListArg.as(currentPools.stream().map(StringArg::as).collect(toList())),
                ListArg.as(currentRewards.stream().map(IntegerArg::as).collect(toList())),
                IntegerArg.as(rewardUpdateHeight)));
    }

    public DAppCall shutdown() {
        return new DAppCall(address(), Function.as("shutdown"));
    }

    public DAppCall activate() {
        return new DAppCall(address(), Function.as("activate"));
    }

}
