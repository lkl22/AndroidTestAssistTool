
`instrument [options] component`	使用 `Instrumentation` 实例启动监控。通常情况下，目标 component 采用 `test_package/runner_class` 格式。

具体选项包括：

option|desc
---|---
-r：|输出原始结果（否则，对 report_key_streamresult 进行解码）。与 [-e perf true] 结合使用可生成性能测量的原始输出。
-e name value：|将参数 name 设为 value。 对于测试运行程序，通用格式为 -e testrunner_flag value[,value...]。
-p file：|将剖析数据写入 file。
-w：|等待插桩完成后再返回。测试运行程序需要使用此选项。
--no-window-animation：|运行时关闭窗口动画。
--user user_id | current：|指定以哪个用户身份运行插桩；如果未指定，则以当前用户身份运行。

eg:

> adb shell am instrument -e params testParams -w com.lkl.androidtestassisttool/.MainInstrumentation

## 参考文献

[adb shell am instrument 命令详解](https://www.cnblogs.com/insist8089/p/6897037.html)

[Android 调试桥 (adb)](https://developer.android.com/studio/command-line/adb.html?hl=zh-cn)
