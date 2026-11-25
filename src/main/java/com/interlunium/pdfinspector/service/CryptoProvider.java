package com.interlunium.pdfinspector.service;

import java.security.Security;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import io.quarkus.runtime.StartupEvent;

/**
 * Registers the Bouncy Castle JCE provider once at application startup.
 */
@ApplicationScoped
public class CryptoProvider {

    void onStart(@Observes StartupEvent event) {
        register();
    }

    @PostConstruct
    void register() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }
}
