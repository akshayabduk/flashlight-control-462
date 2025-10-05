androidApplication {
    namespace = "org.example.app"

    dependencies {
        // Material Components for classic Views (no Jetpack Compose)
        implementation("com.google.android.material:material:1.12.0")
    }

    testing {
        // Use classic JUnit4 for unit tests discovery/execution
        dependencies {
            implementation("junit:junit:4.13.2")
        }
    }
}
