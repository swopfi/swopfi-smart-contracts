package SWOP;

import im.mak.paddle.Account;
import com.wavesplatform.transactions.data.IntegerEntry;
import com.wavesplatform.transactions.data.StringEntry;
import com.wavesplatform.transactions.invocation.StringArg;
import im.mak.paddle.exceptions.ApiError;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static im.mak.paddle.Async.async;
import static im.mak.paddle.Node.node;
import static im.mak.paddle.util.Script.fromFile;
import static org.apache.commons.lang3.StringUtils.substringBefore;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@TestMethodOrder(OrderAnnotation.class)
public class OracleTest {

    static final String ORACLE_SCRIPT = substringBefore(fromFile("dApps/SWOP/oracle.ride"), "@Verifier");
    static final String CPMM_POOL_SCRIPT = fromFile("dApps/other_cpmm.ride");

    static Account user = new Account(),
            oracle = new Account(),
            pool1 = new Account(),
            pool2 = new Account(),
            pool3 = new Account();

    @BeforeAll
    static void before() {
        node().faucet().massTransfer(mt -> mt
                .to(user.address(), 10_00000000)
                .to(oracle.address(), 10_00000000)
                .to(pool1.address(), 10_00000000)
                .to(pool2.address(), 10_00000000)
                .to(pool3.address(), 10_00000000));

        async(
                () -> oracle.setScript(s -> s.script(ORACLE_SCRIPT)),
                () -> pool1.setScript(s -> s.script(CPMM_POOL_SCRIPT)),
                () -> pool2.setScript(s -> s.script(CPMM_POOL_SCRIPT)),
                () -> pool3.setScript(s -> s.script(CPMM_POOL_SCRIPT))
        );
    }

    @Test @Order(0)
    void canAddPool() {
        oracle.invoke(i -> i.function("addPool",
                StringArg.as(pool1.address().toString()), StringArg.as("A_B")));

        assertThat(oracle.getData()).containsExactlyInAnyOrder(
                IntegerEntry.as("index_" + pool1.address(), 0),
                StringEntry.as("pool_" + pool1.address(), "A_B"),
                StringEntry.as("pools", pool1.address().toString())
        );
    }

    @Test @Order(10)
    void canAddPoolWithTheSameName() {
        oracle.invoke(i -> i.function("addPool",
                StringArg.as(pool2.address().toString()), StringArg.as("A_B")));

        assertThat(oracle.getData()).containsExactlyInAnyOrder(
                IntegerEntry.as("index_" + pool1.address(), 0),
                StringEntry.as("pool_" + pool1.address(), "A_B"),
                IntegerEntry.as("index_" + pool2.address(), 1),
                StringEntry.as("pool_" + pool2.address(), "A_B"),
                StringEntry.as("pools", pool1.address() + "," + pool2.address())
        );
    }

    @Test @Order(20)
    void canRenamePool() {
        oracle.invoke(i -> i.function("renamePool",
                StringArg.as(pool2.address().toString()), StringArg.as("B_C")));

        assertThat(oracle.getData()).containsExactlyInAnyOrder(
                IntegerEntry.as("index_" + pool1.address(), 0),
                StringEntry.as("pool_" + pool1.address(), "A_B"),
                IntegerEntry.as("index_" + pool2.address(), 1),
                StringEntry.as("pool_" + pool2.address(), "B_C"),
                StringEntry.as("pools", pool1.address() + "," + pool2.address())
        );
    }

    @Test @Order(30)
    void cantAddAlreadyAddedPool() {
        assertThat(assertThrows(ApiError.class, () ->
                oracle.invoke(i -> i.function("addPool",
                        StringArg.as(pool1.address().toString()), StringArg.as("A_B"))))
        ).hasMessageContaining("Pool \"" + pool1.address() + "\" is already added with name \"A_B\"");

        assertThat(assertThrows(ApiError.class, () ->
                oracle.invoke(i -> i.function("addPool",
                        StringArg.as(pool2.address().toString()), StringArg.as("A_B"))))
        ).hasMessageContaining("Pool \"" + pool2.address() + "\" is already added with name \"B_C\"");
    }

    @Test @Order(40)
    void cantRenameNonexistentPool() {
        assertThat(assertThrows(ApiError.class, () ->
                oracle.invoke(i -> i.function("renamePool",
                        StringArg.as(pool3.address().toString()), StringArg.as("A_C"))))
        ).hasMessageContaining("Pool \"" + pool3.address() + "\" has not been added yet");
    }

    @Test @Order(50)
    void canAddAnotherPoolAfterRenaming() {
        oracle.invoke(i -> i.function("addPool",
                StringArg.as(pool3.address().toString()), StringArg.as("A_C")));

        assertThat(oracle.getData()).containsExactlyInAnyOrder(
                IntegerEntry.as("index_" + pool1.address(), 0),
                StringEntry.as("pool_" + pool1.address(), "A_B"),
                IntegerEntry.as("index_" + pool2.address(), 1),
                StringEntry.as("pool_" + pool2.address(), "B_C"),
                IntegerEntry.as("index_" + pool3.address(), 2),
                StringEntry.as("pool_" + pool3.address(), "A_C"),
                StringEntry.as("pools", pool1.address() + "," + pool2.address() + "," + pool3.address())
        );
    }

    @Test @Order(60)
    void onlyOracleItselfCanAddAndRenamePools() {
        assertThat(assertThrows(ApiError.class, () ->
                user.invoke(i -> i.dApp(oracle)
                        .function("addPool", StringArg.as(pool1.address().toString()), StringArg.as("A_B"))))
        ).hasMessageContaining("Only the Oracle itself can invoke this function");

        assertThat(assertThrows(ApiError.class, () ->
                user.invoke(i -> i.dApp(oracle)
                        .function("addPool", StringArg.as(pool1.address().toString()), StringArg.as("A_B"))))
        ).hasMessageContaining("Only the Oracle itself can invoke this function");
    }

    static Stream<Arguments> invalidPoolAddresses() {
        return Stream.of(
                Arguments.of("3Myqjf1D44wR8Vko4Tr5CwSzRNo2Vg9S7u7"), // different chain id
                Arguments.of("alias:" + (char) node().chainId() + ":alice"),
                Arguments.of("alice"),
                Arguments.of(""));
    }

    @ParameterizedTest() @Order(70)
    @MethodSource("invalidPoolAddresses")
    void cantAddPoolWithInvalidAddress(String invalidAddress) {
        assertThat(assertThrows(ApiError.class, () ->
                oracle.invoke(i -> i.function("addPool",
                        StringArg.as(invalidAddress), StringArg.as("A_B"))))
        ).hasMessageContaining("Can't parse \"" + invalidAddress + "\" as address");
    }

    static Stream<Arguments> invalidPoolNames() {
        return Stream.of(
                Arguments.of("A_"),
                Arguments.of("_B"),
                Arguments.of("AB"));
    }

    @ParameterizedTest() @Order(80)
    @MethodSource("invalidPoolNames")
    void cantAddOrRemovePoolWithInvalidNameFormat(String invalidName) {
        assertThat(assertThrows(ApiError.class, () ->
                oracle.invoke(i -> i.function("addPool",
                        StringArg.as(user.address().toString()), StringArg.as(invalidName))))
        ).hasMessageContaining(
                "Pool name \"" + invalidName + "\" must consist of two asset names separated by an underscore character");

        assertThat(assertThrows(ApiError.class, () ->
                oracle.invoke(i -> i.function("renamePool",
                        StringArg.as(pool1.address().toString()), StringArg.as(invalidName))))
        ).hasMessageContaining(
                "Pool name \"" + invalidName + "\" must consist of two asset names separated by an underscore character");
    }

}
