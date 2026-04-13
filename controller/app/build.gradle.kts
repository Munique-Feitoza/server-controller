import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.pocketnoc"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pocketnoc"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        vectorDrawables {
            useSupportLibrary = true
        }

        // Carrega configurações do local.properties
        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localPropertiesFile.inputStream().use { localProperties.load(it) }
        }

        val server1 = localProperties.getProperty("POCKET_NOC_SERVER_1") ?: ""
        val server2 = localProperties.getProperty("POCKET_NOC_SERVER_2") ?: ""
        val server3 = localProperties.getProperty("POCKET_NOC_SERVER_3") ?: ""
        val server4 = localProperties.getProperty("POCKET_NOC_SERVER_4") ?: ""
        val serverName1 = localProperties.getProperty("POCKET_NOC_SERVER_NAME_1") ?: "Acme 1"
        val serverName2 = localProperties.getProperty("POCKET_NOC_SERVER_NAME_2") ?: "Acme 2"
        val serverName3 = localProperties.getProperty("POCKET_NOC_SERVER_NAME_3") ?: "Acme 3"
        val serverName4 = localProperties.getProperty("POCKET_NOC_SERVER_NAME_4") ?: "Acme 4"
        val secret = localProperties.getProperty("POCKET_NOC_SECRET") ?: ""
        val useHttps = localProperties.getProperty("USE_HTTPS")?.toBoolean() ?: true

        val sshKeyRaw = localProperties.getProperty("SSH_KEY_CONTENT_GLOBAL") ?: ""
        val escapedSshKey = sshKeyRaw.replace("\n", "\\n").replace("\r", "\\r")
        buildConfigField("String", "SSH_KEY_CONTENT_GLOBAL", "\"$escapedSshKey\"")

        // Configuracoes Globais
        buildConfigField("boolean", "USE_HTTPS", localProperties.getProperty("USE_HTTPS") ?: "false")
        buildConfigField("boolean", "EMERGENCY_MODE", localProperties.getProperty("EMERGENCY_MODE") ?: "false")
        buildConfigField("boolean", "SSH_STRICT_HOST_CHECKING", localProperties.getProperty("SSH_STRICT_HOST_CHECKING") ?: "true")
        buildConfigField("int", "MAX_AUTH_FAILURES", localProperties.getProperty("MAX_AUTH_FAILURES") ?: "3")
        buildConfigField("String", "DASHBOARD_NOC_TOKEN", "\"${localProperties.getProperty("DASHBOARD_NOC_TOKEN") ?: ""}\"")
        buildConfigField("String", "DASHBOARD_API_URL", "\"${localProperties.getProperty("DASHBOARD_API_URL") ?: "https://api.example.com/api/v1/pocketnoc/"}\"" )
        buildConfigField("int", "MAX_CPU_THRESHOLD", localProperties.getProperty("CPU_ALERT_THRESHOLD") ?: "80")

        // Servidor 1
        buildConfigField("String", "SSH_USER_1", "\"${localProperties.getProperty("SSH_USER_1") ?: ""}\"")
        buildConfigField("String", "SSH_HOST_1", "\"${localProperties.getProperty("SSH_HOST_1") ?: server1}\"")
        buildConfigField("String", "SSH_SERVICE_PORT_1", "\"${localProperties.getProperty("SSH_SERVICE_PORT_1") ?: "22"}\"")
        buildConfigField("String", "LOCAL_FORWARD_PORT_1", "\"${localProperties.getProperty("LOCAL_FORWARD_PORT_1") ?: "9443"}\"")
        buildConfigField("String", "REMOTE_AGENT_PORT_1", "\"${localProperties.getProperty("REMOTE_AGENT_PORT_1") ?: "9443"}\"")

        // Servidor 2
        buildConfigField("String", "SSH_USER_2", "\"${localProperties.getProperty("SSH_USER_2") ?: ""}\"")
        buildConfigField("String", "SSH_HOST_2", "\"${localProperties.getProperty("SSH_HOST_2") ?: server2}\"")
        buildConfigField("String", "SSH_SERVICE_PORT_2", "\"${localProperties.getProperty("SSH_SERVICE_PORT_2") ?: "22"}\"")
        buildConfigField("String", "LOCAL_FORWARD_PORT_2", "\"${localProperties.getProperty("LOCAL_FORWARD_PORT_2") ?: "9444"}\"")
        buildConfigField("String", "REMOTE_AGENT_PORT_2", "\"${localProperties.getProperty("REMOTE_AGENT_PORT_2") ?: "9443"}\"")

        // Servidor 3
        buildConfigField("String", "SSH_USER_3", "\"${localProperties.getProperty("SSH_USER_3") ?: ""}\"")
        buildConfigField("String", "SSH_HOST_3", "\"${localProperties.getProperty("SSH_HOST_3") ?: server3}\"")
        buildConfigField("String", "SSH_SERVICE_PORT_3", "\"${localProperties.getProperty("SSH_SERVICE_PORT_3") ?: "2222"}\"")
        buildConfigField("String", "LOCAL_FORWARD_PORT_3", "\"${localProperties.getProperty("LOCAL_FORWARD_PORT_3") ?: "9445"}\"")
        buildConfigField("String", "REMOTE_AGENT_PORT_3", "\"${localProperties.getProperty("REMOTE_AGENT_PORT_3") ?: "9443"}\"")

        // Servidor 4
        buildConfigField("String", "SSH_USER_4", "\"${localProperties.getProperty("SSH_USER_4") ?: ""}\"")
        buildConfigField("String", "SSH_HOST_4", "\"${localProperties.getProperty("SSH_HOST_4") ?: server4}\"")
        buildConfigField("String", "SSH_SERVICE_PORT_4", "\"${localProperties.getProperty("SSH_SERVICE_PORT_4") ?: "22"}\"")
        buildConfigField("String", "LOCAL_FORWARD_PORT_4", "\"${localProperties.getProperty("LOCAL_FORWARD_PORT_4") ?: "9446"}\"")
        buildConfigField("String", "REMOTE_AGENT_PORT_4", "\"${localProperties.getProperty("REMOTE_AGENT_PORT_4") ?: "9443"}\"")

        buildConfigField("String", "POCKET_NOC_SERVER_1", "\"$server1\"")
        buildConfigField("String", "POCKET_NOC_SERVER_2", "\"$server2\"")
        buildConfigField("String", "POCKET_NOC_SERVER_3", "\"$server3\"")
        buildConfigField("String", "POCKET_NOC_SERVER_4", "\"$server4\"")
        
        buildConfigField("String", "POCKET_NOC_SERVER_NAME_1", "\"$serverName1\"")
        buildConfigField("String", "POCKET_NOC_SERVER_NAME_2", "\"$serverName2\"")
        buildConfigField("String", "POCKET_NOC_SERVER_NAME_3", "\"$serverName3\"")
        buildConfigField("String", "POCKET_NOC_SERVER_NAME_4", "\"$serverName4\"")
        
        buildConfigField("String", "POCKET_NOC_SECRET", "\"$secret\"")
        buildConfigField("Boolean", "USE_HTTPS", useHttps.toString())
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes.add("/META-INF/**")
        }
    }
}

// Desabilita cache do KAPT para evitar factories desatualizadas do Hilt após mudanças de construtor
kapt {
    useBuildCache = false
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.04.01")
    val roomVersion = "2.6.1"
    val dataStoreVersion = "1.1.1"

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    kapt("androidx.room:room-compiler:$roomVersion")

    implementation("androidx.datastore:datastore-preferences:$dataStoreVersion")
    
    // Encrypted preferences for secure token storage
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.datastore:datastore-preferences-core:1.1.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    implementation("com.google.dagger:hilt-android:2.50")
    kapt("com.google.dagger:hilt-compiler:2.50")

    implementation("io.coil-kt:coil-compose:2.5.0")
    
    // JWT Generation
    implementation("com.auth0:java-jwt:4.4.0")

    // SSH Tunneling
    implementation("com.github.mwiede:jsch:0.2.20")

    // Biometric Authentication
    implementation("androidx.biometric:biometric:1.1.0")

    // Adaptive layout for tablets
    implementation("androidx.compose.material3:material3-adaptive:1.0.0-alpha06")

    // WorkManager for background tasks
    implementation("androidx.work:work-runtime-ktx:2.8.1")
    implementation("androidx.hilt:hilt-work:1.1.0")
    kapt("androidx.hilt:hilt-compiler:1.1.0")

    testImplementation("junit:junit:4.13.2")
}
