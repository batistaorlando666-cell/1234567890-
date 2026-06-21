# How to restore the missing Gradle wrapper files

This partial package contains:
- gradlew
- gradlew.bat
- gradle/wrapper/gradle-wrapper.properties

It does NOT contain:
- gradle/wrapper/gradle-wrapper.jar

To regenerate the missing files, run one of these in a machine that has Gradle installed:

```bash
gradle wrapper
```

or in Android Studio:
- Open the project
- Open Terminal
- Run: `gradle wrapper`

After that, commit:
- gradlew
- gradlew.bat
- gradle/wrapper/gradle-wrapper.jar
