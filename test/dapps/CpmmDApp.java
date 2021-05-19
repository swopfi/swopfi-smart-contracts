package dapps;

import com.wavesplatform.crypto.base.Base58;
import com.wavesplatform.transactions.account.Address;
import com.wavesplatform.transactions.account.PublicKey;
import com.wavesplatform.transactions.common.AssetId;
import com.wavesplatform.transactions.invocation.BooleanArg;
import com.wavesplatform.transactions.invocation.Function;
import com.wavesplatform.transactions.invocation.IntegerArg;
import im.mak.paddle.dapp.DApp;
import im.mak.paddle.dapp.DAppCall;

import static im.mak.paddle.util.Script.fromFile;

public class CpmmDApp extends DApp {

    public CpmmDApp(long initialBalance, Address governance, Address staking, AssetId usdn, PublicKey admin) {
        super(initialBalance, fromFile("dApps/other_cpmm.ride")
                .replace("3P6J84oH51DzY6xk2mT5TheXRbrCwBMxonp", governance.toString())
                .replace("3PNikM6yp4NqcSU8guxQtmR5onr2D4e8yTJ", staking.toString())
                .replace("DG2xFkPdDwKUoBkzGAhQtLpSGzfXLiCYPEzeKH2Ad24p", usdn.toString())
                .replace("DXDY2itiEcYBtGkVLnkpHtDFyWQUkoLJz79uJ7ECbMrA", admin.toString())
                .replace("E6Wa1SGoktYcjHjsKrvjMiqJY3SWmGKcD8Q5L8kxSPS7", admin.toString())
                .replace("AZmWJtuy4GeVrMmJH4hfFBRApe1StvhJSk4jcbT6bArQ", admin.toString())
                .replace("EtVkT6ed8GtbUiVVEqdmEqsp2J4qbb3rre2HFgxeVYdg", admin.toString())
                .replace("Czn4yoAuUZCVCLJDRfskn8URfkwpknwBTZDbs1wFrY7h", admin.toString()));
    }

    public DAppCall init(boolean isFirstHarvest) {
        return new DAppCall(address(), Function.as("init", BooleanArg.as(isFirstHarvest)));
    }

    public DAppCall replenishWithTwoTokens(long slippageTolerance) {
        return new DAppCall(address(), Function.as("replenishWithTwoTokens", IntegerArg.as(slippageTolerance)));
    }

    public DAppCall withdraw() {
        return new DAppCall(address(), Function.as("withdraw"));
    }

    public DAppCall exchange(long minAmountToReceive) {
        return new DAppCall(address(), Function.as("exchange", IntegerArg.as(minAmountToReceive)));
    }

    public DAppCall shutdown() {
        return new DAppCall(address(), Function.as("shutdown"));
    }

    public DAppCall activate() {
        return new DAppCall(address(), Function.as("activate"));
    }

    public DAppCall takeIntoAccountExtraFunds(long amountLeave) {
        return new DAppCall(address(), Function.as("takeIntoAccountExtraFunds", IntegerArg.as(amountLeave)));
    }

}