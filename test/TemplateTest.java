import com.wavesplatform.transactions.data.BooleanEntry;
import im.mak.paddle.Account;
import im.mak.paddle.exceptions.ApiError;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

import static im.mak.paddle.Node.node;
import static im.mak.paddle.util.Script.fromFile;
import static org.apache.commons.lang3.StringUtils.substringBefore;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@TestMethodOrder(OrderAnnotation.class)
public class TemplateTest {

    static final String TEMPLATE_SCRIPT = substringBefore(fromFile("dApps/_template.ride"), "@Verifier");

    static final Account USER = new Account(),
            DAPP = new Account();

    @BeforeAll
    static void before() {
        node().faucet().massTransfer(mt -> mt
                .to(USER.address(), 10_00000000)
                .to(DAPP.address(), 10_00000000));

        DAPP.setScript(s -> s.script(TEMPLATE_SCRIPT));
    }

    @Test @Order(0)
    void cantInteractBeforeInitialization() {
        assertThat(assertThrows(ApiError.class, () ->
                USER.invoke(i -> i.dApp(DAPP).function("doSome")))
        ).hasMessage("Error while executing account-script: DApp is inactive at this moment");
    }

    @Test @Order(10)
    void cantInitializeIfCallerIsNotTheDApp() {
        assertThat(assertThrows(ApiError.class, () ->
                USER.invoke(i -> i.dApp(DAPP).function("init")))
        ).hasMessage("Error while executing account-script: Only the DApp itself can invoke this function");
    }

    @Test @Order(20)
    void canInitialize() {
        DAPP.invoke(i -> i.function("init"));
        assertThat(DAPP.getData()).containsExactly(BooleanEntry.as("active", true));
    }

    //TODO doSome, shutdown, activate, verifier

}
