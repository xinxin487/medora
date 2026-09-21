package com.medora.a2ui.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConfirmationTokenServiceTest {

    @Test
    void generatesOpaqueBase64UrlTokensAndStableSha256Digests() {
        ConfirmationTokenService service = new ConfirmationTokenService();

        String token = service.generate();

        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        assertThat(service.sha256(token)).matches("[a-f0-9]{64}");
        assertThat(service.sha256(token)).isEqualTo(service.sha256(token));
        assertThat(service.generate()).isNotEqualTo(token);
    }
}
