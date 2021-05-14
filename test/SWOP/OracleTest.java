package SWOP;

import com.wavesplatform.crypto.Crypto;
import com.wavesplatform.transactions.account.PrivateKey;
import dapps.OracleDApp;
import im.mak.paddle.Account;
import com.wavesplatform.transactions.data.IntegerEntry;
import com.wavesplatform.transactions.data.StringEntry;
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

import static im.mak.paddle.token.Waves.WAVES;
import static im.mak.paddle.util.Async.async;
import static im.mak.paddle.Node.node;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@TestMethodOrder(OrderAnnotation.class)
public class OracleTest {

    static Account user;
    static OracleDApp oracle;
    static String pool1, pool2, pool3;

    @BeforeAll
    static void before() {
        async(
                () -> user = new Account(WAVES.amount(10)),
                () -> oracle = new OracleDApp(WAVES.amount(10))
        );
        pool1 = PrivateKey.fromSeed(Crypto.getRandomSeedBytes()).address().toString();
        pool2 = PrivateKey.fromSeed(Crypto.getRandomSeedBytes()).address().toString();
        pool3 = PrivateKey.fromSeed(Crypto.getRandomSeedBytes()).address().toString();
    }

    @Test @Order(0)
    void canAddPool() {
        oracle.invoke(oracle.addPool(pool1, "A_B"));

        assertThat(oracle.getData()).containsExactlyInAnyOrder(
                IntegerEntry.as("index_" + pool1, 0),
                StringEntry.as("pool_" + pool1, "A_B"),
                StringEntry.as("pools", pool1)
        );
    }

    @Test @Order(10)
    void canAddPoolWithTheSameName() {
        oracle.invoke(oracle.addPool(pool2, "A_B"));

        assertThat(oracle.getData()).containsExactlyInAnyOrder(
                IntegerEntry.as("index_" + pool1, 0),
                StringEntry.as("pool_" + pool1, "A_B"),
                IntegerEntry.as("index_" + pool2, 1),
                StringEntry.as("pool_" + pool2, "A_B"),
                StringEntry.as("pools", pool1 + "," + pool2)
        );
    }

    @Test @Order(20)
    void canRenamePool() {
        oracle.invoke(oracle.renamePool(pool2, "B_C"));

        assertThat(oracle.getData()).containsExactlyInAnyOrder(
                IntegerEntry.as("index_" + pool1, 0),
                StringEntry.as("pool_" + pool1, "A_B"),
                IntegerEntry.as("index_" + pool2, 1),
                StringEntry.as("pool_" + pool2, "B_C"),
                StringEntry.as("pools", pool1 + "," + pool2)
        );
    }

    @Test @Order(30)
    void cantAddAlreadyAddedPool() {
        assertThat(assertThrows(ApiError.class, () ->
                oracle.invoke(oracle.addPool(pool1, "A_B")))
        ).hasMessageContaining("Pool with address \"" + pool1 + "\" is already defined with name \"A_B\"");

        assertThat(assertThrows(ApiError.class, () ->
                oracle.invoke(oracle.addPool(pool2, "A_B")))
        ).hasMessageContaining("Pool with address \"" + pool2 + "\" is already defined with name \"B_C\"");
    }

    @Test @Order(40)
    void cantRenameNonexistentPool() {
        assertThat(assertThrows(ApiError.class, () ->
                oracle.invoke(oracle.renamePool(pool3, "A_C")))
        ).hasMessageContaining("Pool with address \"" + pool3 + "\" has not yet been added");
    }

    @Test @Order(50)
    void canAddAnotherPoolAfterRenaming() {
        oracle.invoke(oracle.addPool(pool3, "A_C"));

        assertThat(oracle.getData()).containsExactlyInAnyOrder(
                IntegerEntry.as("index_" + pool1, 0),
                StringEntry.as("pool_" + pool1, "A_B"),
                IntegerEntry.as("index_" + pool2, 1),
                StringEntry.as("pool_" + pool2, "B_C"),
                IntegerEntry.as("index_" + pool3, 2),
                StringEntry.as("pool_" + pool3, "A_C"),
                StringEntry.as("pools", pool1 + "," + pool2 + "," + pool3)
        );
    }

    @Test @Order(60)
    void onlyOracleItselfCanAddAndRenamePools() {
        assertThat(assertThrows(ApiError.class, () ->
                user.invoke(oracle.addPool(pool1, "A_B")))
        ).hasMessageContaining("Only the Oracle itself can invoke this function");

        assertThat(assertThrows(ApiError.class, () ->
                user.invoke(oracle.addPool(pool1, "A_B")))
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
                oracle.invoke(oracle.addPool(invalidAddress, "A_B")))
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
        String expectedErrorMessage = "Pool name must consist of two asset names separated by an underscore character";

        assertThat(assertThrows(ApiError.class, () ->
                oracle.invoke(oracle.addPool(user.address().toString(), invalidName)))
        ).hasMessageContaining(expectedErrorMessage);

        assertThat(assertThrows(ApiError.class, () ->
                oracle.invoke(oracle.renamePool(pool1, invalidName)))
        ).hasMessageContaining(expectedErrorMessage);
    }

}
