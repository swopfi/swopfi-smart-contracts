package SWOP;

import im.mak.paddle.Account;
import im.mak.waves.transactions.data.IntegerEntry;
import im.mak.waves.transactions.data.StringEntry;
import im.mak.waves.transactions.invocation.StringArg;
import org.junit.jupiter.api.Test;

import static im.mak.paddle.Async.async;
import static im.mak.paddle.util.Script.fromFile;
import static org.assertj.core.api.Assertions.assertThat;

public class OracleTest {

    @Test
    void canAddAndRenamePools() {
        Account oracle = new Account(10_00000000);
        Account pool1 = new Account(10_00000000);
        Account pool2 = new Account(10_00000000);
        Account pool3 = new Account(10_00000000);

        String oracleScript = fromFile("dApps/SWOP/oracle.ride")
                .replace("adminPubKey1Signed + adminPubKey2Signed + adminPubKey3Signed >= 2",
                        "sigVerify(tx.bodyBytes, tx.proofs[0], tx.senderPublicKey)");
        String cpmmPoolScript = fromFile("dApps/other_cpmm.ride");

        async(
                () -> oracle.setScript(s -> s.script(oracleScript)),
                () -> pool1.setScript(s -> s.script(cpmmPoolScript)),
                () -> pool2.setScript(s -> s.script(cpmmPoolScript)),
                () -> pool3.setScript(s -> s.script(cpmmPoolScript))
        );

        oracle.invoke(i -> i.function("addPool",
                StringArg.as(pool1.address().toString()), StringArg.as("B_A")));
        oracle.invoke(i -> i.function("addPool",
                StringArg.as(pool2.address().toString()), StringArg.as("B_C")));

        oracle.invoke(i -> i.function("renamePool",
                StringArg.as(pool1.address().toString()), StringArg.as("A_B")));

        oracle.invoke(i -> i.function("addPool",
                StringArg.as(pool3.address().toString()), StringArg.as("A_C")));

        assertThat(oracle.getData()).containsExactlyInAnyOrder(
                StringEntry.as("pools", pool1.address() + "," + pool2.address() + "," + pool3.address()),
                StringEntry.as("pool_" + pool1.address(), "A_B"),
                IntegerEntry.as("index_" + pool1.address(), 0),
                StringEntry.as("pool_" + pool2.address(), "B_C"),
                IntegerEntry.as("index_" + pool2.address(), 1),
                StringEntry.as("pool_" + pool3.address(), "A_C"),
                IntegerEntry.as("index_" + pool3.address(), 2)
        );
    }
}
