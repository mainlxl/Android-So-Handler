# SevenZip
**7z工具合集**
拷贝自[AndResGuard](https://github.com/shwenzhang/AndResGuard/tree/master/SevenZip)项目
并增加苹果M1架构[p7zip](https://sourceforge.net/projects/p7zip)编译
用户执行
7z命令
```shell
7z a out.7z input -t7z -mx=9 -m0=LZMA2 -ms=10m -mf=on -mhc=on -mmt=on -mhcf
```