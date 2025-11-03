package com.project.codereview.client.github

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.*

@Component
class GithubAppTokenProvider(
    @param:Value("\${app.github.app.app-id}") private val appId: String,
    @param:Value("\${app.github.app.private-key}") private val appPem: String
) {
    @Volatile private var cachedToken: String? = null
    @Volatile private var expiresAt: Instant? = null

    private val privateKey = run {
        val pem = appPem.replace("\\n", "\n")
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s+".toRegex(), "")

        val keySpec = PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem))
        KeyFactory.getInstance("RSA").generatePrivate(keySpec)
    }

    private fun generateJwt(): String {
        val now = Instant.now()
        return Jwts.builder()
            .setIssuer(appId)                            // iss = App ID
            .setIssuedAt(Date.from(now))
            .setExpiration(Date.from(now.plusSeconds(9 * 60))) // 최대 10분, 9분 권장
            .signWith(privateKey, SignatureAlgorithm.RS256)
            .compact()
    }

    fun getInstallationToken(installationId: String): String {
        val now = Instant.now()
        if (cachedToken != null && expiresAt?.isAfter(now.plusSeconds(30)) == true) {
            return cachedToken!!                         // 만료 30초 전까지 재사용
        }

        val jwt = generateJwt()
        val resp = WebClient.builder()
            .baseUrl("https://api.github.com")
            .defaultHeader("Authorization", "Bearer $jwt")
            .defaultHeader("Accept", "application/vnd.github+json")
            .build()
            .post()
            .uri("/app/installations/$installationId/access_tokens")
            .retrieve()
            .bodyToMono(Map::class.java)
            .block()!!

        cachedToken = resp["token"] as String
        // 예: "2025-10-24T09:12:34Z"
        expiresAt = Instant.parse(resp["expires_at"] as String)
        return cachedToken!!
    }
}