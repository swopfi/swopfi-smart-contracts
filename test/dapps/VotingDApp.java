package dapps;

import com.wavesplatform.transactions.account.Address;
import com.wavesplatform.transactions.account.PrivateKey;
import com.wavesplatform.transactions.account.PublicKey;
import com.wavesplatform.transactions.common.AssetId;
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

public class VotingDApp extends DApp {

    public VotingDApp(PrivateKey pk, long initialBalance, Address governance) {
        super(pk, initialBalance, StringUtils.substringBefore(
                fromFile("dApps/SWOP/voting.ride")
                        .replace("3PLHVWCqA9DJPDbadUofTohnCULLauiDWhS", governance.toString()),
                "@Verifier"));
    }

    public DAppCall votePoolWeight(List<String> poolAddresses, List<Long> poolsVoteSWOPNew) {
        return new DAppCall(address(), Function.as("votePoolWeight",
                ListArg.as(poolAddresses.stream().map(StringArg::as).collect(toList())),
                ListArg.as(poolsVoteSWOPNew.stream().map(IntegerArg::as).collect(toList()))));
    }

}
