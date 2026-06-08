#include <jni.h>
#include <string>
#include <thread>
#include <atomic>
#include <cstring>
#include <mutex>
#include <openssl/evp.h>
#include <openssl/sha.h>
#include <android/log.h>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "VanityMiner", __VA_ARGS__)

std::atomic<bool> running(false);
std::atomic<uint64_t> total_checked(0);
std::atomic<uint64_t> found_count(0);

char found_addr[128];
char found_priv[256];
std::mutex result_mutex;
bool has_result = false;

static const char b64[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

bool check_pattern(const char* addr, int min_repeat) {
    if (addr[0] != 'U' || addr[1] != 'Q') return false;
    char target = addr[2];
    if (target < 'A' || target > 'z') return false;
    int count = 0;
    for (int i = 2; i < 48; i++) {
        if (addr[i] == target) count++;
        else break;
    }
    return count >= min_repeat;
}

void to_base64(const uint8_t* data, size_t len, char* out) {
    size_t out_idx = 0;
    uint32_t buffer = 0;
    int bits = 0;
    for (size_t i = 0; i < len; i++) {
        buffer = (buffer << 8) | data[i];
        bits += 8;
        while (bits >= 6) {
            out[out_idx++] = b64[(buffer >> (bits - 6)) & 0x3F];
            bits -= 6;
        }
    }
    if (bits > 0) out[out_idx++] = b64[(buffer << (6 - bits)) & 0x3F];
    out[out_idx] = '\0';
}

void pubkey_to_address(const uint8_t* pub_key, char* addr) {
    uint8_t data[36];
    memset(data, 0, 4);
    data[4] = 0x29; data[5] = 0xA9; data[6] = 0x23; data[7] = 0x17;
    memcpy(data + 8, pub_key, 32);
    
    uint8_t hash[SHA256_DIGEST_LENGTH];
    SHA256(data, 36, hash);
    
    uint8_t full[36 + 2];
    full[0] = 0x51; full[1] = 0x00;
    memcpy(full + 2, hash, 32);
    
    uint16_t crc = 0;
    for (int i = 0; i < 34; i++) {
        crc ^= full[i] << 8;
        for (int j = 0; j < 8; j++) {
            crc = (crc & 0x8000) ? (crc << 1) ^ 0x1021 : crc << 1;
        }
    }
    full[34] = crc >> 8;
    full[35] = crc & 0xFF;
    
    to_base64(full, 36, addr);
}

void worker(int min_repeat) {
    while (running) {
        EVP_PKEY_CTX *pctx = EVP_PKEY_CTX_new_id(EVP_PKEY_ED25519, NULL);
        EVP_PKEY_keygen_init(pctx);
        EVP_PKEY *pkey = NULL;
        EVP_PKEY_keygen(pctx, &pkey);
        
        size_t priv_len = 32;
        uint8_t priv_key[32];
        EVP_PKEY_get_raw_private_key(pkey, priv_key, &priv_len);
        
        size_t pub_len = 32;
        uint8_t pub_key[32];
        EVP_PKEY_get_raw_public_key(pkey, pub_key, &pub_len);
        
        char addr[64];
        pubkey_to_address(pub_key, addr);
        total_checked++;
        
        if (check_pattern(addr, min_repeat)) {
            std::lock_guard<std::mutex> lock(result_mutex);
            strncpy(found_addr, addr, 127);
            for (int i = 0; i < 32; i++) sprintf(found_priv + i * 2, "%02x", priv_key[i]);
            found_priv[64] = '\0';
            has_result = true;
            found_count++;
            LOGI("FOUND! %s", addr);
        }
        
        EVP_PKEY_free(pkey);
        EVP_PKEY_CTX_free(pctx);
    }
}

extern "C" {

JNIEXPORT void JNICALL
Java_com_vanity_miner_MainActivity_nativeStartMining(
    JNIEnv* env, jobject, jint min_repeat, jint threads, jboolean use_gpu) {
    if (running) return;
    running = true;
    total_checked = 0;
    found_count = 0;
    has_result = false;
    int n = threads < 1 ? 1 : (threads > 16 ? 16 : threads);
    LOGI("Starting %d threads, min_repeat=%d", n, min_repeat);
    for (int i = 0; i < n; i++) std::thread(worker, min_repeat).detach();
}

JNIEXPORT void JNICALL
Java_com_vanity_miner_MainActivity_nativeStopMining(JNIEnv*, jobject) {
    running = false;
}

JNIEXPORT jlong JNICALL
Java_com_vanity_miner_MainActivity_nativeGetTotal(JNIEnv*, jobject) {
    return total_checked.load();
}

JNIEXPORT jlong JNICALL
Java_com_vanity_miner_MainActivity_nativeGetFound(JNIEnv*, jobject) {
    return found_count.load();
}

JNIEXPORT jboolean JNICALL
Java_com_vanity_miner_MainActivity_nativeHasResult(JNIEnv*, jobject) {
    return has_result;
}

JNIEXPORT jstring JNICALL
Java_com_vanity_miner_MainActivity_nativeGetResult(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lock(result_mutex);
    has_result = false;
    char result[512];
    snprintf(result, 512, "%s|%s", found_addr, found_priv);
    return env->NewStringUTF(result);
}

}
