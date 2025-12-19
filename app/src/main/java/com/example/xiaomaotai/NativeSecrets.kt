package com.example.xiaomaotai

/**
 * Native层密钥访问桥接类
 * 通过JNI从C++层获取敏感配置信息
 */
object NativeSecrets {

    init {
        System.loadLibrary("native-secrets")
    }

    /**
     * 获取SMTP密码（授权码）
     */
    external fun getSmtpPassword(): String

    /**
     * 获取SMTP用户名（邮箱地址）
     */
    external fun getSmtpUsername(): String

    /**
     * 获取SMTP服务器地址
     */
    external fun getSmtpHost(): String

    /**
     * 获取SMTP端口
     */
    external fun getSmtpPort(): String
}
