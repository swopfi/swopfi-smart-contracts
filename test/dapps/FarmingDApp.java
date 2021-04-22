package dapps;

import com.wavesplatform.transactions.account.Address;
import com.wavesplatform.transactions.invocation.Function;
import com.wavesplatform.transactions.invocation.IntegerArg;
import com.wavesplatform.transactions.invocation.StringArg;
import im.mak.paddle.Account;
import im.mak.paddle.dapp.DApp;
import im.mak.paddle.dapp.DAppCall;
import org.apache.commons.lang3.StringUtils;

import static im.mak.paddle.util.Script.fromFile;

public class FarmingDApp extends DApp {

    public FarmingDApp(long initialBalance, Address voting) {
        super(initialBalance);
        setScript(StringUtils.substringBefore(
                fromFile("dApps/SWOP/farming.ride")
                        .replace("3PLHVWCqA9DJPDbadUofTohnCULLauiDWhS", voting.toString())
                        .replace("oneWeekInBlock = 10106", "oneWeekInBlock = 10")
                        .replace("DXDY2itiEcYBtGkVLnkpHtDFyWQUkoLJz79uJ7ECbMrA", publicKey().toString())
                        .replace("E6Wa1SGoktYcjHjsKrvjMiqJY3SWmGKcD8Q5L8kxSPS7", publicKey().toString())
                        .replace("AZmWJtuy4GeVrMmJH4hfFBRApe1StvhJSk4jcbT6bArQ", publicKey().toString())
                        .replace("EtVkT6ed8GtbUiVVEqdmEqsp2J4qbb3rre2HFgxeVYdg", publicKey().toString())
                        .replace("Czn4yoAuUZCVCLJDRfskn8URfkwpknwBTZDbs1wFrY7h", publicKey().toString())
                        .replace("if i.caller != this then", "")
                        .replace("throw(\"Only the DApp itself can call this function\") else", ""),
                "@Verifier"));
    }

    public DAppCall init(String earlyLP) {
        return new DAppCall(address(), Function.as("init", StringArg.as(earlyLP)));
    }

    public DAppCall initPoolShareFarming(String pool) {
        return new DAppCall(address(), Function.as("initPoolShareFarming", StringArg.as(pool)));
    }

    public DAppCall initPoolShareFarming(Account pool) {
        return initPoolShareFarming(pool.address().toString());
    }

    public DAppCall lockShareTokens(String pool) {
        return new DAppCall(address(), Function.as("lockShareTokens", StringArg.as(pool)));
    }

    public DAppCall lockShareTokens(Account pool) {
        return lockShareTokens(pool.address().toString());
    }

    public DAppCall withdrawShareTokens(String pool, long shareTokensWithdrawAmount) {
        return new DAppCall(address(), Function.as("withdrawShareTokens",
                StringArg.as(pool), IntegerArg.as(shareTokensWithdrawAmount)));
    }

    public DAppCall withdrawShareTokens(Account pool, long shareTokensWithdrawAmount) {
        return withdrawShareTokens(pool.address().toString(), shareTokensWithdrawAmount);
    }

    public DAppCall claim(String pool) {
        return new DAppCall(address(), Function.as("claim", StringArg.as(pool)));
    }

    public DAppCall claim(Account pool) {
        return claim(pool.address().toString());
    }

}
