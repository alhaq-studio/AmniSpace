package com.alhaq.amniquest.core.licensing

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

class LicenseValidatorTest {

    private lateinit var testKeyPair: KeyPair
    private lateinit var testKeyring: Map<Int, String>
    private val json = Json { encodeDefaults = true }

    @Before
    fun setUp() {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        testKeyPair = kpg.generateKeyPair()
        val pubKeyBase64 = Base64.getEncoder().encodeToString(testKeyPair.public.encoded)
        testKeyring = mapOf(1 to pubKeyBase64)
    }

    private fun signPayloadDer(data: ByteArray): ByteArray {
        val sig = Signature.getInstance("SHA256withECDSA")
        sig.initSign(testKeyPair.private)
        sig.update(data)
        return sig.sign()
    }

    private fun derToP1363(der: ByteArray): ByteArray {
        // ASN.1 DER sequence: 0x30 len 0x02 rLen [r] 0x02 sLen [s]
        var offset = 2
        if (der[1].toInt() and 0x80 != 0) {
            offset += (der[1].toInt() and 0x7F)
        }
        offset++ // skip 0x02
        val rLen = der[offset++].toInt() and 0xFF
        val rBytes = der.copyOfRange(offset, offset + rLen)
        offset += rLen
        offset++ // skip 0x02
        val sLen = der[offset++].toInt() and 0xFF
        val sBytes = der.copyOfRange(offset, offset + sLen)

        val p1363 = ByteArray(64)
        val rUnsigned = BigInteger(1, rBytes).toByteArray().let {
            if (it.size > 32) it.copyOfRange(it.size - 32, it.size) else it
        }
        val sUnsigned = BigInteger(1, sBytes).toByteArray().let {
            if (it.size > 32) it.copyOfRange(it.size - 32, it.size) else it
        }
        System.arraycopy(rUnsigned, 0, p1363, 32 - rUnsigned.size, rUnsigned.size)
        System.arraycopy(sUnsigned, 0, p1363, 64 - sUnsigned.size, sUnsigned.size)
        return p1363
    }

    @Test
    fun verifyLicense_validDerSignatureAndFutureExpiry_returnsPayload() {
        val futureExpiry = System.currentTimeMillis() + 86_400_000L
        val payload = LicensePayload(
            email = "supporter@alhaq.studio",
            type = "annual",
            expires = futureExpiry,
            user_id = "user-123",
            version = 1
        )
        val payloadBytes = json.encodeToString(payload).toByteArray(Charsets.UTF_8)
        val sigBytes = signPayloadDer(payloadBytes)

        val token = "${Base64.getEncoder().encodeToString(payloadBytes)}.${Base64.getEncoder().encodeToString(sigBytes)}"
        val verified = LicenseValidator.verifyLicense(token, testKeyring)

        assertNotNull(verified)
        assertEquals("supporter@alhaq.studio", verified?.email)
        assertEquals("annual", verified?.type)
        assertEquals(futureExpiry, verified?.expires)
        assertEquals("user-123", verified?.user_id)
    }

    @Test
    fun verifyLicense_validP1363RawSignature_convertsToDerAndVerifies() {
        val payload = LicensePayload(
            email = "lifetime@alhaq.studio",
            type = "lifetime",
            expires = 0L,
            version = 1
        )
        val payloadBytes = json.encodeToString(payload).toByteArray(Charsets.UTF_8)
        val derSig = signPayloadDer(payloadBytes)
        val p1363Sig = derToP1363(derSig)
        assertEquals(64, p1363Sig.size)

        val urlEncoder = Base64.getUrlEncoder().withoutPadding()
        val token = "${urlEncoder.encodeToString(payloadBytes)}.${urlEncoder.encodeToString(p1363Sig)}"

        val verified = LicenseValidator.verifyLicense(token, testKeyring)
        assertNotNull(verified)
        assertEquals("lifetime@alhaq.studio", verified?.email)
        assertEquals("lifetime", verified?.type)
        assertEquals(0L, verified?.expires)
    }

    @Test
    fun verifyLicense_fallbackSignedOverBase64AsciiString_verifiesSuccessfully() {
        val payload = LicensePayload(
            email = "monthly@alhaq.studio",
            type = "monthly",
            expires = System.currentTimeMillis() + 3_600_000L,
            version = 1
        )
        val payloadBase64 = Base64.getEncoder().encodeToString(
            json.encodeToString(payload).toByteArray(Charsets.UTF_8)
        )
        val sigBytes = signPayloadDer(payloadBase64.toByteArray(Charsets.UTF_8))
        val sigBase64 = Base64.getEncoder().encodeToString(sigBytes)

        val verified = LicenseValidator.verifyLicense("  \"$payloadBase64.$sigBase64\r\n\" ", testKeyring)
        assertNotNull(verified)
        assertEquals("monthly@alhaq.studio", verified?.email)
    }

    @Test
    fun verifyLicense_expiredLicenseWithValidSignature_returnsNull() {
        val pastExpiry = System.currentTimeMillis() - 60_000L
        val payload = LicensePayload(
            email = "expired@alhaq.studio",
            type = "monthly",
            expires = pastExpiry,
            version = 1
        )
        val payloadBytes = json.encodeToString(payload).toByteArray(Charsets.UTF_8)
        val sigBytes = signPayloadDer(payloadBytes)

        val token = "${Base64.getEncoder().encodeToString(payloadBytes)}.${Base64.getEncoder().encodeToString(sigBytes)}"
        val verified = LicenseValidator.verifyLicense(token, testKeyring)
        assertNull(verified)
    }

    @Test
    fun verifyLicense_tamperedPayloadAfterSigning_returnsNull() {
        val original = LicensePayload(
            email = "user@alhaq.studio",
            type = "monthly",
            expires = System.currentTimeMillis() + 86_400_000L,
            version = 1
        )
        val originalBytes = json.encodeToString(original).toByteArray(Charsets.UTF_8)
        val sigBytes = signPayloadDer(originalBytes)

        val tampered = original.copy(type = "lifetime", expires = 0L)
        val tamperedBytes = json.encodeToString(tampered).toByteArray(Charsets.UTF_8)

        val token = "${Base64.getEncoder().encodeToString(tamperedBytes)}.${Base64.getEncoder().encodeToString(sigBytes)}"
        assertNull(LicenseValidator.verifyLicense(token, testKeyring))
    }

    @Test
    fun verifyLicense_unknownKeyVersion_returnsNull() {
        val payload = LicensePayload(
            email = "user@alhaq.studio",
            type = "annual",
            expires = 0L,
            version = 99
        )
        val payloadBytes = json.encodeToString(payload).toByteArray(Charsets.UTF_8)
        val sigBytes = signPayloadDer(payloadBytes)

        val token = "${Base64.getEncoder().encodeToString(payloadBytes)}.${Base64.getEncoder().encodeToString(sigBytes)}"
        assertNull(LicenseValidator.verifyLicense(token, testKeyring))
    }

    @Test
    fun verifyLicense_malformedInputs_returnsNull() {
        assertNull(LicenseValidator.verifyLicense(""))
        assertNull(LicenseValidator.verifyLicense("   "))
        assertNull(LicenseValidator.verifyLicense("no-dot-separator"))
        assertNull(LicenseValidator.verifyLicense("too.many.dots"))
        assertNull(LicenseValidator.verifyLicense("!!!invalid_base64!!!.!!!invalid_sig!!!"))

        val nonJsonBase64 = Base64.getEncoder().encodeToString("not a json object".toByteArray())
        val dummySigBase64 = Base64.getEncoder().encodeToString(ByteArray(64))
        assertNull(LicenseValidator.verifyLicense("$nonJsonBase64.$dummySigBase64"))
    }

    @Test
    fun verifyLicense_defaultProductionKeyringRejectsForgedSignature() {
        val payload = LicensePayload(
            email = "forged@alhaq.studio",
            type = "lifetime",
            expires = 0L,
            version = 1
        )
        val payloadBytes = json.encodeToString(payload).toByteArray(Charsets.UTF_8)
        val forgedSigBytes = signPayloadDer(payloadBytes) // Signed with ephemeral key, not production key

        val token = "${Base64.getEncoder().encodeToString(payloadBytes)}.${Base64.getEncoder().encodeToString(forgedSigBytes)}"
        assertNull(LicenseValidator.verifyLicense(token))
    }
}

