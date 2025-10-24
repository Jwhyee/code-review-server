package com.project.codereview.util

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object GithubSignature {

    fun sign256(secret: String, payload: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val digest = mac.doFinal(payload)
        val hex = digest.joinToString("") { "%02x".format(it) }
        return "sha256=$hex"
    }

    fun isValid(signatureHeader: String?, secret: String, payload: ByteArray): Boolean {
        if (signatureHeader.isNullOrBlank()) return false
        val expected = sign256(secret, payload)
        return constantTimeEquals(expected, signatureHeader)
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var r = 0
        for (i in a.indices) r = r or (a[i].code xor b[i].code)
        return r == 0
    }
}