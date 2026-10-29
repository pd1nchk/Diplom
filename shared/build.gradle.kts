import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    iosArm64()
    iosSimulatorArm64()
    
    jvm()
    
    js {
        browser()
    }
    
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }
    
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.client.serialization)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.cio)
        }
        jsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js.wasm.js)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

// Если автоопределение IPv4 не сработало и в local.properties нет diplom.server.host.
val defaultDiplomServerHost: String = "192.168.100.10"

/** IPv4 этой машины для отладки по Wi‑Fi (игнорируем loopback, link-local и по возможности виртуальные NIC). */
fun detectBuildMachineLanIpv4(): String? {
    fun collect(useVirtualFilter: Boolean): List<String> = try {
        NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { ni ->
                ni.isUp && !ni.isLoopback && (!useVirtualFilter || !ni.isVirtual)
            }
            .flatMap { ni -> ni.inetAddresses.asSequence() }
            .filterIsInstance<Inet4Address>()
            .mapNotNull { it.hostAddress }
            .filter { !it.startsWith("127.") && !it.startsWith("169.254.") }
            .distinct()
            .toList()
    } catch (_: Exception) {
        emptyList()
    }

    fun score(ip: String): Int = when {
        ip.startsWith("192.168.") -> 300
        ip.startsWith("10.") -> 200
        ip.startsWith("172.") -> {
            val oct2 = ip.substringAfter('.').substringBefore('.').toIntOrNull() ?: 0
            if (oct2 in 16..31) 250 else 50
        }
        else -> 100
    }

    val primary = collect(useVirtualFilter = true)
        .maxByOrNull { score(it) }
    if (primary != null) return primary
    return collect(useVirtualFilter = false).maxByOrNull { score(it) }
}

android {
    namespace = "com.example.diplom.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        val props = Properties()
        val lp = rootProject.file("local.properties")
        if (lp.exists()) {
            lp.inputStream().use { props.load(it) }
        }
        val explicit = props.getProperty("diplom.server.host")?.trim()?.takeIf { it.isNotEmpty() }
        val host = explicit ?: detectBuildMachineLanIpv4() ?: defaultDiplomServerHost
        project.logger.lifecycle("[diplom] BuildConfig DIPLOM_SERVER_HOST=$host" + if (explicit != null) " (local.properties)" else " (auto-detected)")
        buildConfigField("String", "DIPLOM_SERVER_HOST", "\"${host.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
