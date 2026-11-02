# IDEA 提示 "Reactor debug mode is enabled" 说明

## 一句话结论

这是 IntelliJ IDEA 的 Reactor 插件发的提示：它在项目 classpath 里发现了 Project Reactor（`reactor-core`），就问你要不要开启 Reactor 的“调试模式”，并选一种初始化方式。对 DataEase 来说，**基本用不到，忽略或者关掉都可以**，不影响项目运行，也和数据源驱动报错之类的问题无关。

## Reactor 是什么，为什么项目里有它

- Project Reactor 是一个响应式编程库，核心类型是 `Mono`（0~1 个元素）和 `Flux`（0~N 个元素），Spring WebFlux 就是基于它实现的。
- DataEase 后端是传统的 Spring MVC，并没有使用 WebFlux。项目里有 Reactor，是因为：
  - 一些依赖会间接引入 `reactor-core` / `reactor-netty`（本地 Maven 仓库中可以看到 `io/projectreactor`）；
  - 少量代码只是借用了它的工具类，例如 `TablePivotHandler.java` 里用到的 `reactor.util.function.Tuple2` / `Tuples`，这和响应式流无关。
- IDEA 只要在 classpath 里检测到 Reactor，就会弹出这个提示，不会去判断你是否真的在写响应式代码。

## Reactor debug mode 解决什么问题

响应式代码是“先组装、后执行”的：`flux.map(...).filter(...)` 这一行只是在**组装**流水线，真正执行是在订阅之后，而且通常在别的线程上执行。所以一旦出错，异常堆栈里几乎只有 Reactor 内部的调度帧，**看不出是你代码里哪一行组装的那个操作符出了问题**。

调试模式的作用就是：在组装阶段记录下“这个操作符是在哪一行代码创建的”（assembly trace），等出错时把这段信息附加到异常上。IDEA 拿到后，会在 Debug 窗口的 Frames 里显示回溯，这样就能定位到你自己的业务代码。

开启后，IDEA 调试器还会把 `Mono` / `Flux` 变量用特殊图标标出来，可以直接点击来计算流里的元素（默认取前 100 个）。

## 提示里要你选的“初始化方式”

点 `Configure...` 会跳到 `Settings | Languages & Frameworks | Reactive Streams`（不同版本的名称略有差异），主要有下面几种选择：

| 方式 | 原理 | 代价 | 适用场景 |
| --- | --- | --- | --- |
| 不开启 / None | 不做任何处理 | 无 | 项目没有真正使用 `Mono`/`Flux` 流（**DataEase 属于这种**） |
| `Hooks.onOperatorDebug()` | 运行时全局钩子，每组装一个操作符就抓一次调用栈 | **性能开销大**，只适合本地调试 | 临时排查响应式代码问题 |
| `ReactorDebugAgent.init()` | 通过 Java Agent 在字节码层面给操作符注入位置信息，需要 `io.projectreactor:reactor-tools` 依赖 | 运行时开销很小 | 正式使用 WebFlux 的项目 |

注意事项：

- 在 Spring Boot 2.2+ 中，如果 classpath 里有 `reactor-tools`，并且 `spring.reactor.debug-agent.enabled=true`（默认值），启动时会自动调用 `ReactorDebugAgent.init()`。这时如果再手动调用 `Hooks.onOperatorDebug()`，调试栈帧会重复出现，IDEA 也会通过 `SpringBootReactorHooksOnDebug` 这个检查给出警告。
- IDEA 里的这个设置只在**用 IDEA 启动调试**时生效，不会修改项目代码或配置文件，对打包、部署都没有影响。

## 对 DataEase 的建议

- 直接关掉这个通知即可；如果不想再看到它，可以在 `Settings | Languages & Frameworks | Reactive Streams` 里取消启用 Reactor debug mode。
- 不要为了这个提示在代码里加 `Hooks.onOperatorDebug()`，它对本项目没有帮助，反而会带来额外开销。
- 只有将来真的引入 WebFlux、写 `Mono`/`Flux` 业务代码时，才需要考虑开启，届时推荐 `ReactorDebugAgent`（引入 `reactor-tools`）。

## 参考

- [JetBrains Blog: Reactive Programming Made Easy](https://blog.jetbrains.com/idea/2023/06/reactive-programming-made-easy/)
- [Inspectopedia: Suspicious Hooks.onOperatorDebug() usage](https://www.jetbrains.com/help/inspectopedia/SpringBootReactorHooksOnDebug.html)
- [reactor-tools README（ReactorDebugAgent）](https://github.com/reactor/reactor-tools/blob/master/README.md)
