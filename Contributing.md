# Contributing

We welcome contributions from the community! Whether you are fixing bugs, improving documentation, adding new features, or suggesting ideas, your help is appreciated.

---

## Getting Started

1. **Fork** the repository.
2. **Clone** your fork:
   ```sh
   git clone https://github.com/Al-Haq-Studio/AmniQuest.git
   cd AmniQuest
   ```

3. **Create a new branch** for your feature or fix:
   ```sh
   git checkout -b feature/your-feature-name
   ```

4. **Make your changes**, then commit:
   ```sh
   git add .
   git commit -m "Add: your message here"
   ```

5. **Push to your fork**:
   ```sh
   git push origin feature/your-feature-name
   ```

6. **Open a Pull Request** on the main repository.

Please follow the existing code style, ensure 16 KB page size alignment for any native code, adhere to the zero-emoji policy, and write clear, descriptive commit messages.

---

## Setting Up Environment Variables

This project uses environment variables to manage sensitive information like Supabase keys securely.

To configure Supabase backend access locally:

1. Create a file named `local.properties` in the project root directory (same level as root `build.gradle.kts`).
2. Add the following content:

   ```properties
   SUPABASE_URL=https://yourproject.supabase.co
   SUPABASE_API_KEY=your-secret-api-key
   ```

> [!IMPORTANT]
> Never commit your `local.properties` file. It is excluded from version control via `.gitignore`.

> [!TIP]
> You can fill in these keys with dummy values if you decide to debug the app locally without cloud sync. Offline on-device AI verification using local SigLIP 2 ONNX models and native SentencePiece JNI executes completely offline without network or cloud credentials across both `play` and `fdroid` flavors.

These values are automatically injected into `BuildConfig` and accessed in Kotlin:

```kotlin
val url = BuildConfig.SUPABASE_URL
val apiKey = BuildConfig.SUPABASE_API_KEY
```

---

## Tips for Contributors

- **Read the Architecture Guide**: Review [ARCHITECTURE.md](ARCHITECTURE.md) to understand the multi-module layout, app blocker foreground service, and on-device AI validation pipelines.
- **Run Automated Tests**: Verify all unit tests before submitting a PR:
  ```powershell
  .\gradlew.bat test
  ```
- **Verify Both Flavors**: Ensure both `:app:assemblePlayDebug` and `:app:assembleFdroidDebug` build cleanly.
- **Zero Emoji Policy**: Never include UTF-8 emojis or emoji-based unicode characters in code, UI, comments, strings, commit messages, or documentation.
- Open an issue first if you are planning a major architectural change.
- Keep pull requests focused and limited to a single cohesive change.
- Be respectful and constructive in discussions.

---

Thanks for helping improve the project!
