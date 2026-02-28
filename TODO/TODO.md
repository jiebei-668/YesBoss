2 状态机与调度引擎模块设计::TaskManager::createMasterTask & createWorkerTask的返回值对应的是哪个表中的字段？应该是task_session表中的id吧？ 答：是的。
3 功能需求第1大点的功能可以进行拆分，拆分为更分界清晰的几个功能：
    用户新建任务->主agent和用户讨论明确需求->主agent产生细拆分后的各个子任务（该子任务可以直接分发给子agent）
    主agent发布任务给子agent->子agent完成返回结果给主agent
    主agent发布任务给子agent->子agent有疑问询问主agent->主agent回答
    子agent react循环
4 时序图5.2关于命令黑名单沙箱，子agent的某个tool在黑名单中需要用户授权是怎么通过主agent让用户确认的，主agent怎么知道这一条消息是自己给子agent回答还是直接问用户回答？ 答：逻辑上通过master问用户，实现上显示补充master的上下文，然后直接去问用户
5 子agent向主agent主动提问（有两种，普通的提问，和子agent使用黑名单工具时需要向上请求许可）的接口没有设计 
  答： 场景一：常规业务提问（主动求助）。实现机制是工具调用 (Tool Call)。 逻辑流转： 为 Worker 注入专属的内部系统工具（如 AskMasterTool）。当 Worker 在底层的 ReAct 循环中遭遇代码逻辑盲区时，可自主决定调用该工具向 Master 发起文字提问，当前 Worker 线程挂起并等待 Master 回答后继续执行。
      场景二：高危工具授权（被动拦截）。实现机制是框架强制接管 (Bypass LLM)。 逻辑流转： 当 Worker 触发沙箱黑名单时，在业务概念上该求助动作归属于 Master。但在底层代码实现上，系统将完全绕过 Master 的大模型推理，由底层 Java 框架捕获挂起异常，直接溯源查询该任务绑定的 IM 群聊路由锚点，并越权调用 Webhook 向用户强制推送授权卡片。
6 子agent工具被拦截提问用户时候需要怎么处理子agent资源？阻塞等待？完全退出重新恢复？
7 需要支持运行时动态切换模型  
8 feishu订阅地址改成 后缀为webhook/feishu，第一次配置需要处理challenge，具体见相关飞书文档
9 MasterRunnerImpl的run()中先进行需求澄清，然后探索上下文，之后生成计划。生成计划时候会获取用户原始需求，目前实现是提取上下文的第一行，这么做不合适，前面进行的需求澄清没有派上用场。
