package com.example.xiaomaotai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

class EmailService {
    companion object {
        private const val FROM_NAME = "小茅台纪念日"
    }

    suspend fun sendVerificationCode(toEmail: String, code: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                // 从Native层获取敏感配置
                val smtpHost = NativeSecrets.getSmtpHost()
                val smtpPort = NativeSecrets.getSmtpPort()
                val smtpUsername = NativeSecrets.getSmtpUsername()
                val smtpPassword = NativeSecrets.getSmtpPassword()

                val props = Properties().apply {
                    put("mail.smtp.host", smtpHost)
                    put("mail.smtp.port", smtpPort)
                    put("mail.smtp.auth", "true")
                    put("mail.smtp.ssl.enable", "true")
                    put("mail.smtp.ssl.protocols", "TLSv1.2")
                }

                val session = Session.getInstance(props, object : Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication {
                        return PasswordAuthentication(smtpUsername, smtpPassword)
                    }
                })

                val message = MimeMessage(session).apply {
                    setFrom(InternetAddress(smtpUsername, FROM_NAME, "UTF-8"))
                    setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail))
                    subject = "【小茅台纪念日】密码重置验证码"

                    val content = """
                        您好！

                        您正在重置小茅台纪念日应用的登录密码。

                        验证码：$code

                        验证码5分钟内有效，请勿泄露给他人。
                        如非本人操作，请忽略此邮件。

                        祝您使用愉快！
                    """.trimIndent()

                    setText(content, "UTF-8")
                }

                Transport.send(message)
                Result.success("验证码发送成功")

            } catch (e: Exception) {
                Result.failure(Exception("邮件发送失败: ${e.message}"))
            }
        }
    }
}
