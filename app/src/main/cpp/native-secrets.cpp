#include <jni.h>
#include <string>

/**
 * 邮箱密钥保护 - Native层实现
 * 通过分散存储+简单混淆，增加反编译难度
 */

// 密钥分片存储（混淆后的数据）
static const char* p1 = "RU52";      // 第1片
static const char* p2 = "Z1d3";      // 第2片
static const char* p3 = "VjV0";      // 第3片
static const char* p4 = "WFNV";      // 第4片
static const char* p5 = "Nmpg";      // 第5片 (最后部分是 "Z")

// 邮箱地址分片
static const char* e1 = "363";
static const char* e2 = "875";
static const char* e3 = "872";
static const char* e4 = "@163.com";

// 简单的Base64解码（运行时解码）
static const std::string base64_chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

std::string base64_decode(const std::string &encoded) {
    std::string decoded;
    int val = 0, valb = -8;
    for (unsigned char c : encoded) {
        if (c == '=') break;
        size_t pos = base64_chars.find(c);
        if (pos == std::string::npos) continue;
        val = (val << 6) + pos;
        valb += 6;
        if (valb >= 0) {
            decoded.push_back(char((val >> valb) & 0xFF));
            valb -= 8;
        }
    }
    return decoded;
}

// 拼接并解码密钥
std::string getDecodedPassword() {
    std::string encoded;
    encoded += p1;
    encoded += p2;
    encoded += p3;
    encoded += p4;
    encoded += p5;
    encoded += "Z";  // 补齐
    return base64_decode(encoded);
}

// 拼接邮箱地址
std::string getEmailAddress() {
    std::string email;
    email += e1;
    email += e2;
    email += e3;
    email += e4;
    return email;
}

extern "C" {

extern JNIEXPORT jstring JNICALL
Java_com_example_xiaomaotai_NativeSecrets_getSmtpPassword(JNIEnv *env, jobject /* this */) {
    std::string password = getDecodedPassword();
    return env->NewStringUTF(password.c_str());
}

extern JNIEXPORT jstring JNICALL
Java_com_example_xiaomaotai_NativeSecrets_getSmtpUsername(JNIEnv *env, jobject /* this */) {
    std::string email = getEmailAddress();
    return env->NewStringUTF(email.c_str());
}

extern JNIEXPORT jstring JNICALL
Java_com_example_xiaomaotai_NativeSecrets_getSmtpHost(JNIEnv *env, jobject /* this */) {
    return env->NewStringUTF("smtp.163.com");
}

extern JNIEXPORT jstring JNICALL
Java_com_example_xiaomaotai_NativeSecrets_getSmtpPort(JNIEnv *env, jobject /* this */) {
    return env->NewStringUTF("465");
}

}
