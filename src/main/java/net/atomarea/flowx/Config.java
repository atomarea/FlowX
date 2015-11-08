package net.atomarea.flowx;

import android.graphics.Bitmap;

import net.atomarea.flowx.xmpp.chatstate.ChatState;

public final class Config {

    public static final String LOGTAG = "conversations";

    public static final String DOMAIN_LOCK = "flowx.im"; //only allow account creation for this domain
    public static final boolean DISALLOW_REGISTRATION_IN_UI = false; //hide the register checkbox
    public static final boolean HIDE_PGP_IN_UI = true; //some more consumer focused clients might want to disable OpenPGP
    public static final boolean LEGACY_NAMESPACE_HTTP_UPLOAD = false;
    public static final int PING_MAX_INTERVAL = 300;
    public static final int PING_MIN_INTERVAL = 30;
    public static final int PING_TIMEOUT = 10;
    public static final int SOCKET_TIMEOUT = 15;
    public static final int CONNECT_TIMEOUT = 90;
    public static final int CARBON_GRACE_PERIOD = 90;
    public static final int MINI_GRACE_PERIOD = 750;
    public static final boolean X509_VERIFICATION = false; //use x509 certificates to verify OMEMO keys

    public static final int AVATAR_SIZE = 1920;
    public static final Bitmap.CompressFormat AVATAR_FORMAT = Bitmap.CompressFormat.JPEG;

    public static final int IMAGE_SIZE = 1920;
    public static final Bitmap.CompressFormat IMAGE_FORMAT = Bitmap.CompressFormat.JPEG;
    public static final int IMAGE_QUALITY = 80;

    public static final int MESSAGE_MERGE_WINDOW = 20;

    public static final int PAGE_SIZE = 50;
    public static final int MAX_NUM_PAGES = 3;

    public static final int REFRESH_UI_INTERVAL = 500;

    public static final boolean NO_PROXY_LOOKUP = false; //useful to debug ibb
    public static final boolean DISABLE_STRING_PREP = false; // setting to true might increase startup performance
    public static final boolean EXTENDED_SM_LOGGING = true; // log stanza counts
    public static final boolean RESET_ATTEMPT_COUNT_ON_NETWORK_CHANGE = true; //setting to true might increase power consumption

    public static final boolean IGNORE_ID_REWRITE_IN_MUC = true;

    public static final boolean ENCRYPT_ON_HTTP_UPLOADED = false;

    public static final boolean REPORT_WRONG_FILESIZE_IN_OTR_JINGLE = true;

    public static final boolean SHOW_REGENERATE_AXOLOTL_KEYS_BUTTON = false;

    public static final long MILLISECONDS_IN_DAY = 24 * 60 * 60 * 1000;
    public static final long MAM_MAX_CATCHUP = MILLISECONDS_IN_DAY / 2;
    public static final int MAM_MAX_MESSAGES = 500;

    public static final ChatState DEFAULT_CHATSTATE = ChatState.ACTIVE;
    public static final int TYPING_TIMEOUT = 8;

    public static final String ENABLED_CIPHERS[] = {
            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA384",
            "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
            "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA",

            "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_DHE_RSA_WITH_AES_128_GCM_SHA384",
            "TLS_DHE_RSA_WITH_AES_256_GCM_SHA256",
            "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384",

            "TLS_DHE_RSA_WITH_CAMELLIA_256_SHA",

            // Fallback.
            "TLS_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_RSA_WITH_AES_128_GCM_SHA384",
            "TLS_RSA_WITH_AES_256_GCM_SHA256",
            "TLS_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_RSA_WITH_AES_128_CBC_SHA256",
            "TLS_RSA_WITH_AES_128_CBC_SHA384",
            "TLS_RSA_WITH_AES_256_CBC_SHA256",
            "TLS_RSA_WITH_AES_256_CBC_SHA384",
            "TLS_RSA_WITH_AES_128_CBC_SHA",
            "TLS_RSA_WITH_AES_256_CBC_SHA",
    };

    public static final String WEAK_CIPHER_PATTERNS[] = {
            "_NULL_",
            "_EXPORT_",
            "_anon_",
            "_RC4_",
            "_DES_",
            "_MD5",
    };

    private Config() {

    }
}
