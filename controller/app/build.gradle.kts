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
        val serverName1 = localProperties.getProperty("POCKET_NOC_SERVER_NAME_1") ?: "Winup 1"
        val serverName2 = localProperties.getProperty("POCKET_NOC_SERVER_NAME_2") ?: "Winup 2"
        val serverName3 = localProperties.getProperty("POCKET_NOC_SERVER_NAME_3") ?: "Winup 3"
        val serverName4 = localProperties.getProperty("POCKET_NOC_SERVER_NAME_4") ?: "Winup 4"
        val secret = localProperties.getProperty("POCKET_NOC_SECRET") ?: ""
        val useHttps = localProperties.getProperty("USE_HTTPS")?.toBoolean() ?: true

        val sshKeyRaw = localProperties.getProperty("SSH_KEY_CONTENT_GLOBAL") ?: ""
        val escapedSshKey = sshKeyRaw.replace("\n", "\\n").replace("\r", "\\r")
        buildConfigField("String", "SSH_KEY_CONTENT_GLOBAL", "\"$escapedSshKey\"")

        // Configurações Globais e Emergência
        buildConfigField("String", "POCKET_NOC_SECRET", "\"${localProperties.getProperty("POCKET_NOC_SECRET") ?: ""}\"")
        buildConfigField("boolean", "USE_HTTPS", localProperties.getProperty("USE_HTTPS") ?: "false")
        buildConfigField("boolean", "EMERGENCY_MODE", localProperties.getProperty("EMERGENCY_MODE") ?: "false")
        buildConfigField("boolean", "SSH_STRICT_HOST_CHECKING", localProperties.getProperty("SSH_STRICT_HOST_CHECKING") ?: "true")
        buildConfigField("int", "MAX_AUTH_FAILURES", localProperties.getProperty("MAX_AUTH_FAILURES") ?: "3")
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

dependencies {
    val composeVersion = "1.5.0"
    val roomVersion = "2.6.1"
    val dataStoreVersion = "1.1.1"

    implementation("androidx.compose.ui:ui:$composeVersion")
    implementation("androidx.compose.ui:ui-graphics:$composeVersion")
    implementation("androidx.compose.ui:ui-tooling-preview:$composeVersion")
    implementation("androidx.compose.material3:material3:1.1.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.compose.material:material-icons-extended:$composeVersion")
    debugImplementation("androidx.compose.ui:ui-tooling:$composeVersion")

    implementation("androidx.navigation:navigation-compose:2.7.7")
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

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    implementation("com.google.dagger:hilt-android:2.50")
    kapt("com.google.dagger:hilt-compiler:2.50")

    implementation("io.coil-kt:coil-compose:2.5.0")
    
    // JWT Generation
    implementation("com.auth0:java-jwt:4.4.0")

    // SSH Tunneling
    implementation("com.github.mwiede:jsch:0.2.20")
}
