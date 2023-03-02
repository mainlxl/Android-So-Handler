echo -------------------- 开始上传 maven --------------------
echo 压缩 p7z 可执行文件
zip -j p7z/executable/p7z.aar p7z/executable/*
echo 压缩完成
#./gradlew -q -P userPlugin=false clean publish
./gradlew -q -P userPlugin=false clean publishToMavenLocal
echo -------------------- 上传 maven 完成 --------------------