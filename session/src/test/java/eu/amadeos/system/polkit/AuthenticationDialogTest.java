package eu.amadeos.system.polkit;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.system.polkit.Authenticator.Answer;
import eu.amadeos.system.polkit.Authenticator.Request;
import eu.amadeos.system.polkit.Users.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthenticationDialogTest {
    private static final Messages EN = Messages.load(PolkitAgent.class, PolkitAgent.TEXTS, Locale.ENGLISH);
    private static final User ALICE = new User(1000, "alice", "Alice Example");
    private static final User ROOT = new User(0, "root", "root");
    private final AuthenticationDialog dialog = new AuthenticationDialog(EN);

    @AfterEach
    void close() throws Exception {
        dialog.close();
        SwingUtilities.invokeAndWait(() -> {
            if (dialog.frame() != null) dialog.frame().dispose();
        });
    }

    private static Request request(List<User> users) {
        return new Request("eu.amadeos.packages.manage", "Authentication is required to install software",
                "", Map.of("command_line", "/usr/libexec/amadeos-packages install gimp"), "c", users);
    }

    private static <T> T await(CompletableFuture<T> future) throws Exception {
        return future.get(5, TimeUnit.SECONDS);
    }

    private CompletableFuture<Answer> ask(String prompt) throws Exception {
        CompletableFuture<Answer> answer = CompletableFuture.supplyAsync(() -> dialog.ask(ALICE, prompt, false));
        long end = System.nanoTime() + 5_000_000_000L;
        while (!AuthenticationDialog.read(() -> dialog.authenticateButton().isEnabled())) {
            if (System.nanoTime() > end) throw new AssertionError("no prompt");
            Thread.sleep(10);
        }
        return answer;
    }

    @Test
    void theTypedPasswordAnswersThePrompt() throws Exception {
        dialog.open(request(List.of(ALICE)));
        assertTrue(AuthenticationDialog.read(() -> dialog.frame().isVisible()));
        assertEquals("Authentication Required", AuthenticationDialog.read(() -> dialog.frame().getTitle()));
        CompletableFuture<Answer> answer = ask("Password: ");
        assertEquals("Password:", AuthenticationDialog.read(dialog::promptText));
        SwingUtilities.invokeAndWait(() -> {
            dialog.passwordField().setText("secret");
            dialog.authenticateButton().doClick();
        });
        Answer.Text text = assertInstanceOf(Answer.Text.class, await(answer));
        assertArrayEquals("secret".toCharArray(), text.chars());
        assertEquals("Checking…", AuthenticationDialog.read(dialog::statusText));
        assertFalse(AuthenticationDialog.read(() -> dialog.authenticateButton().isEnabled()));
        assertEquals("", AuthenticationDialog.read(() -> new String(dialog.passwordField().getPassword())));

        dialog.failed();
        CompletableFuture<Answer> again = ask("Password: ");
        assertEquals("Authentication failed. Try again.", AuthenticationDialog.read(dialog::statusText));
        dialog.close();
        assertInstanceOf(Answer.Cancel.class, await(again));
        assertFalse(AuthenticationDialog.read(() -> dialog.frame().isVisible()));
    }

    @Test
    void choosingAnotherAdministratorRestartsWithThatUser() throws Exception {
        dialog.open(request(List.of(ALICE, ROOT)));
        assertTrue(AuthenticationDialog.read(() -> dialog.userChoice().isVisible()));
        CompletableFuture<Answer> answer = ask("Password: ");
        SwingUtilities.invokeAndWait(() -> dialog.userChoice().setSelectedItem(ROOT));
        assertEquals(new Answer.SwitchUser(ROOT), await(answer));
    }

    @Test
    void cancelWhileThePasswordIsCheckedAnswersTheNextPrompt() throws Exception {
        dialog.open(request(List.of(ALICE)));
        // The window's close box while no prompt is open.
        SwingUtilities.invokeAndWait(() -> dialog.frame().dispatchEvent(
                new java.awt.event.WindowEvent(dialog.frame(), java.awt.event.WindowEvent.WINDOW_CLOSING)));
        assertInstanceOf(Answer.Cancel.class, dialog.ask(ALICE, "Password: ", false));
    }

    @Test
    void theDetailsNameTheActionAndTheCommand() throws Exception {
        dialog.open(request(List.of(ALICE)));
        assertEquals("Action: eu.amadeos.packages.manage\ncommand_line: /usr/libexec/amadeos-packages install gimp",
                AuthenticationDialog.read(dialog::detailsText));
    }
}
