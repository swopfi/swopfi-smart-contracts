package dapps;

import com.wavesplatform.transactions.account.Address;
import com.wavesplatform.transactions.account.PrivateKey;
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
import static org.apache.commons.lang3.StringUtils.substringBefore;

public class OracleDApp extends DApp {

    public OracleDApp(long initialBalance) {
        super(initialBalance, substringBefore(fromFile("dApps/SWOP/oracle.ride"), "@Verifier"));
    }

    public DAppCall addPool(String poolAddress, String poolName) {
        return new DAppCall(address(), Function.as("addPool", StringArg.as(poolAddress), StringArg.as(poolName)));
    }

    public DAppCall renamePool(String poolAddress, String newPoolName) {
        return new DAppCall(address(), Function.as("renamePool", StringArg.as(poolAddress), StringArg.as(newPoolName)));
    }

}
