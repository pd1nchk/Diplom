package com.example.diplom.auth

import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.util.Base64

interface SmsSender {
    fun sendPasswordResetCode(phone: String, code: String): SmsSendResult
}

data class SmsSendResult(
    val sent: Boolean,
    val message: String,
)

object SmsSenderFactory {
    fun fromEnvironment(): SmsSender {
        val provider = System.getenv("SMS_PROVIDER")?.trim()?.lowercase().orEmpty()
        return when (provider) {
            "twilio" -> TwilioSmsSender(
                accountSid = System.getenv("TWILIO_ACCOUNT_SID").orEmpty(),
                authToken = System.getenv("TWILIO_AUTH_TOKEN").orEmpty(),
                fromNumber = System.getenv("TWILIO_FROM_NUMBER").orEmpty(),
            )
            else -> DemoSmsSender
        }
    }
}

private object DemoSmsSender : SmsSender {
    override fun sendPasswordResetCode(phone: String, code: String): SmsSendResult {
        return SmsSendResult(
            sent = true,
            message = "Демо-SMS: код $code",
        )
    }
}

private class TwilioSmsSender(
    private val accountSid: String,
    private val authToken: String,
    private val fromNumber: String,
) : SmsSender {
    private val client = HttpClient.newHttpClient()

    override fun sendPasswordResetCode(phone: String, code: String): SmsSendResult {
        if (accountSid.isBlank() || authToken.isBlank() || fromNumber.isBlank()) {
            return SmsSendResult(false, "Сервис отправки SMS настроен не полностью")
        }

        val body = formEncode(
            "To" to phone.normalizedPhone(),
            "From" to fromNumber,
            "Body" to "Код восстановления пароля: $code",
        )
        val auth = Base64.getEncoder()
            .encodeToString("$accountSid:$authToken".toByteArray(StandardCharsets.UTF_8))
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.twilio.com/2010-04-01/Accounts/$accountSid/Messages.json"))
            .header("Authorization", "Basic $auth")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        return runCatching {
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() in 200..299) {
                SmsSendResult(true, "SMS sent")
            } else {
                SmsSendResult(false, "Не удалось отправить SMS. Проверьте настройки SMS-сервиса")
            }
        }.getOrElse {
            SmsSendResult(false, "Не удалось отправить SMS")
        }
    }

    private fun formEncode(vararg pairs: Pair<String, String>): String {
        return pairs.joinToString("&") { (key, value) ->
            "${key.urlEncode()}=${value.urlEncode()}"
        }
    }

    private fun String.urlEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.name())

    private fun String.normalizedPhone(): String {
        val trimmed = trim()
        if (trimmed.startsWith("+")) return trimmed
        val digits = trimmed.filter { it.isDigit() }
        return if (digits.startsWith("375")) "+$digits" else trimmed
    }
}
