import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.10"
}

android {
    namespace = "com.eta.attendance"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.eta.attendance"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "SUPABASE_URL", "\"\"")
        buildConfigField("String", "SUPABASE_KEY", "\"\"")
    }

    // 仅在提供了签名密钥（CI 环境变量或本地 local.properties）时才配置签名。
    // 未提供时 release 不做签名，产物为 app-release-unsigned.apk（需自行 zipalign + apksigner）。
    val localProps = Properties().apply {
        val lp = rootProject.file("local.properties")
        if (lp.exists()) lp.inputStream().use { load(it) }
    }
    // 先读环境变量（CI 路径），回退 local.properties（本地路径）。
    // 口令绝不写进 gradle.properties —— 那个文件是提交的。
    fun signingProp(name: String): String? = System.getenv(name) ?: localProps.getProperty(name)

    val keystoreFile = signingProp("KEYSTORE_PATH")?.let { File(it) }
    val hasKeystore = keystoreFile != null && keystoreFile.exists()

    if (hasKeystore) {
        signingConfigs {
            create("release") {
                storeFile = keystoreFile
                // 密钥库由 openssl pkcs12 -export 生成（无需 JDK），必须显式声明类型，
                // 否则 AGP 默认按 JKS 读取会失败。
                storeType = "PKCS12"
                storePassword = signingProp("KEYSTORE_PASS")
                keyAlias = signingProp("KEY_ALIAS")
                keyPassword = signingProp("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // 关闭 R8 以保证首编通过；需要体积优化时打开并补充 proguard 规则。
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("top.yukonga.miuix.kmp:miuix-ui:0.9.4-rc01")
    implementation("top.yukonga.miuix.kmp:miuix-icons:0.9.4-rc01")
    implementation("top.yukonga.miuix.kmp:miuix-nav:0.9.4-rc01")
    implementation("top.yukonga.miuix.kmp:miuix-blur:0.9.4-rc01")
    implementation("top.yukonga.miuix.kmp:miuix-preference:0.9.4-rc01")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("androidx.compose.ui:ui:1.12.0-rc01")
    // 液态玻璃组件直接用到 androidx.compose.ui.util.lerp / fastCoerceAtMost / fastFirstOrNull，
    // 必须显式声明，不能只靠 miuix/compose 的传递依赖。
    implementation("androidx.compose.ui:ui-util:1.12.0-rc01")
    implementation("androidx.compose.foundation:foundation:1.12.0-rc01")
    implementation("androidx.compose.runtime:runtime:1.12.0-rc01")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    testImplementation("junit:junit:4.13.2")
}
