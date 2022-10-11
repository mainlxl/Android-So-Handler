# p7z
**7z工具合集**
拷贝自[AndResGuard](https://github.com/shwenzhang/AndResGuard/tree/master/SevenZip)项目
并做如下修改
1. 重新编译linux-x86_64 
2. 并增加苹果M1架构与linux-aarch64,[使用16.02源码编译](source-16.02)编译



7z命令
```shell
7z a out.7z input -t7z -mx=9 -m0=LZMA2 -ms=10m -mf=on -mhc=on -mmt=on -mhcf
```
