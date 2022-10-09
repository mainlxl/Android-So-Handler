if [ "$1" == "-rM" ]; then
  echo ---------------------------------------------------------开始恢复maven状态---------------------------------------------------------
  rm -rf ./maven/com
#  git checkout HEAD maven
fi
echo ---------------------------------------------------------开始上传maven---------------------------------------------------------
./gradlew -q -P userPlugin=false clean
./gradlew -q -P userPlugin=false :android-un7z:publish
./gradlew -q -P userPlugin=false :file-plugin:publish
./gradlew -q -P userPlugin=false :load-hook:publish
./gradlew -q -P userPlugin=false :load-hook-plugin:publish
./gradlew -q -P userPlugin=false :load-assets-7z:publish
echo ---------------------------------------------------------上传maven完成---------------------------------------------------------