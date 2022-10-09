./gradlew -q -P userPlugin=false :file-plugin:publish
./gradlew clean :app:assembleDebug -q  --stacktrace