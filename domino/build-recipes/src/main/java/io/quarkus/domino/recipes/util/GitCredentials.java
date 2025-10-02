package io.quarkus.domino.recipes.util;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;

public class GitCredentials extends CredentialsProvider {

    final Map<String, String> hostToUsername = new HashMap<>();
    final Map<String, String> hostToPassword = new HashMap<>();

    public GitCredentials() {
        String creds = System.getenv("GIT_TOKEN");
        if (creds != null) {
            //this is a .gitcredentials file
            for (var i : creds.split("\n")) {
                i = i.trim();
                if (i.isEmpty()) {
                    continue;
                }
                if (!i.startsWith("https://")) {
                    Logger.getLogger("git-credentials").severe("git credentials contains non-https URI's");
                    continue;
                }
                i = i.substring("https://".length());
                var parts = i.split("@");
                if (parts.length != 2) {
                    Logger.getLogger("git-credentials").severe("Invalid git credentials format");
                    continue;
                }
                String host = parts[1];
                parts = parts[0].split(":");
                if (parts.length != 2) {
                    Logger.getLogger("git-credentials").severe("Invalid git credentials format");
                    continue;
                }
                String username = parts[0];
                String password = parts[1];
                hostToUsername.put(host, username);
                hostToPassword.put(host, password);
            }
        }
    }

    @Override
    public boolean isInteractive() {
        return false;
    }

    @Override
    public boolean supports(CredentialItem... items) {
        for (CredentialItem i : items) {
            if (i instanceof CredentialItem.InformationalMessage) {
                continue;
            }
            if (i instanceof CredentialItem.Username) {
                continue;
            }
            if (i instanceof CredentialItem.Password) {
                continue;
            }
            if (i instanceof CredentialItem.StringType) {
                if (i.getPromptText().equals("Password: ")) { //$NON-NLS-1$
                    continue;
                }
            }
            return false;
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean get(URIish uri, CredentialItem... items) throws UnsupportedCredentialItem {
        String username = hostToUsername.get(uri.getHost());
        String password = hostToPassword.get(uri.getHost());
        for (CredentialItem i : items) {
            if (i instanceof CredentialItem.InformationalMessage) {
                continue;
            }
            if (i instanceof CredentialItem.Username) {
                ((CredentialItem.Username) i).setValue(username);
                continue;
            }
            if (i instanceof CredentialItem.Password && password != null) {
                ((CredentialItem.Password) i).setValue(password.toCharArray());
                continue;
            }
            if (i instanceof CredentialItem.StringType && password != null) {
                if (i.getPromptText().equals("Password: ")) { //$NON-NLS-1$
                    ((CredentialItem.StringType) i).setValue(new String(
                            password));
                    continue;
                }
            }
            throw new UnsupportedCredentialItem(uri, i.getClass().getName()
                    + ":" + i.getPromptText()); //$NON-NLS-1$
        }
        return true;
    }

}
