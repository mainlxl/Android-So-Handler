#!/bin/bash

for i in {1..100}; do
  # 启动应用
  adb shell am start -n com.imf.test/.MainActivity

  # 等待5秒钟
  sleep 2

  # 关闭应用
  adb shell am force-stop com.imf.test
done
