# Food Log — AI 语音饮食记录后端

[![CI](https://github.com/kiky97/food-log/actions/workflows/ci.yml/badge.svg)](https://github.com/kiky97/food-log/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

用户用自然语言(语音或文字)说出吃了什么,系统自动转成结构化的饮食记录并计算营养。

```
语音 ─► STT ─► 文本 ─► LLM 实体抽取 ─► 食物匹配 ─► 份量换算 ─► 营养计算 ─► MongoDB
                         (Gemini,                (模糊匹配 +      (碗/片/个 → 克)
                          JSON schema 约束)       置信度分级)
```

## 技术栈

Java 17+ · Spring Boot 3.5 · Spring Web / Validation · Spring Data MongoDB · Gemini API(Google AI Studio)· springdoc(Swagger UI)· JUnit 5 · Testcontainers

## 快速开始

需要 JDK 17+ 和 Docker。

```bash
docker compose up -d
```

```bash
./mvnw spring-boot:run
```

Windows 用 `mvnw.cmd spring-boot:run`。启动后:

- Swagger UI: http://localhost:8080/swagger-ui.html
- 健康检查: http://localhost:8080/actuator/health

默认使用 **Mock 模式**(不需要任何 Key),首次启动会自动导入 45 条示例食物。

### 试一试

```bash
curl -X POST http://localhost:8080/api/v1/logs -H "Content-Type: application/json" -d "{\"userId\":\"u1\",\"text\":\"I had two boiled eggs, a bowl of rice and an apple for breakfast\"}"
```

```bash
curl "http://localhost:8080/api/v1/logs/summary?userId=u1"
```

### 接入真实的 Gemini

在 [Google AI Studio](https://aistudio.google.com/apikey) 申请 Key 后,设置环境变量再启动(PowerShell):

```powershell
$env:GEMINI_API_KEY = "你的key"; $env:LLM_PROVIDER = "gemini"; $env:STT_PROVIDER = "gemini"; ./mvnw.cmd spring-boot:run
```

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `GEMINI_API_KEY` | 空 | AI Studio 的 API Key,只通过请求头发送,不会写日志 |
| `LLM_PROVIDER` | `mock` | `gemini` 或 `mock`,控制实体抽取 |
| `STT_PROVIDER` | `mock` | `gemini` 或 `mock`,控制语音转文字 |
| `GEMINI_MODEL` | `gemini-2.5-flash` | 模型名 |
| `MONGODB_URI` | `mongodb://localhost:27017/foodlog` | |

> **Mock 模式的限制**:Mock LLM 只能解析简单英文句子;Mock STT 不能识别真实音频,上传 `.txt` 文件可模拟一段语音。中文、口语化、复杂句子需要用 Gemini。
> 语音文件建议用 wav / mp3 / ogg / flac,最大 15MB。

## 前端

位于 `frontend/`,React 19 + Vite + TypeScript,不依赖任何 UI 或图表库。

```bash
cd frontend
npm install
npm run dev
```

打开 http://localhost:5173 。开发服务器把 `/api` 代理到 `localhost:8080`,所以需要先启动后端(浏览器只看到一个源,不需要配置 CORS)。

不想启动后端和 MongoDB 时,用内存版的假后端看界面:

```bash
npm run dev:mock
```

> Mock 只认识八种食物、只做简单的关键词解析,仅用于开发界面和演示,不代表真实的匹配逻辑。

功能:

- 文字输入,或用麦克风录音、上传音频文件。浏览器录下来的音频先解码,再转成 16 kHz 单声道 WAV 上传,因为 Gemini 的音频输入不接受浏览器默认的 webm。
- 每条记录显示识别原文、匹配到的食物、克数(份量是估算值时带 `≈`)和热量。
- 状态为"需要确认"或"未找到"时,直接在该行选候选食物,或搜索食物库;已匹配的条目也可以点 "Wrong food?" 更正。
- 右侧是当天汇总:总热量、碳水/蛋白质/脂肪的供能占比、纤维/糖/盐、分餐热量;还有多少项未确认、没有计入。
- 支持手机宽度和深色模式。用户名保存在浏览器本地,没有登录。

```bash
npm test        # 单元与组件测试(vitest)
npm run build   # 类型检查并打包到 frontend/dist
```

## API

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/logs` | 文字记录 `{userId, text, loggedAt?}` |
| POST | `/api/v1/logs/voice` | 语音记录,multipart:`userId`、`audio`、`loggedAt?` |
| POST | `/api/v1/logs/{logId}/items/{index}/confirm` | 确认或更正某一项匹配的食物 `{foodId}` |
| GET | `/api/v1/logs?userId=&date=` | 某天的记录,`date` 默认今天 |
| GET | `/api/v1/logs/summary?userId=&date=` | 某天的营养汇总(总量、分餐) |
| GET | `/api/v1/foods/search?q=` | 食物模糊搜索,返回相似度 |
| GET | `/api/v1/foods/{id}` | 食物详情 |
| POST | `/api/v1/foods/import` | 上传 CSV 导入食物库(按 id 覆盖更新) |

### 匹配结果的三种状态

| 状态 | 条件(默认) | 是否计入营养 |
|---|---|---|
| `MATCHED` | 相似度 ≥ 0.85,且第二名没有紧跟 | 是 |
| `NEEDS_CONFIRMATION` | 0.6 ≤ 相似度 < 0.85,或前两名分数接近 | 否,等用户通过 `confirm` 接口选择 |
| `UNMATCHED` | 相似度 < 0.6 | 否 |

用户确认后状态变为 `CONFIRMED`。份量如果用了通用默认值(例如库里没有"一份"的克数),`portionEstimated` 为 `true`。

## 项目结构

```
frontend/     React 前端(见上文)
controller/   REST 接口(FoodLogController, FoodController)
dto/          请求与响应对象
service/      FoodLogService(主流程)、FoodCatalog、CSV 导入
client/       外部服务:stt/(语音转文字)、llm/(实体抽取)、gemini/(HTTP 调用、重试)
matching/     食物名归一化与模糊匹配
portion/      单位与份量换算
domain/       Mongo 文档与值对象
repository/   Spring Data 仓库
exception/    统一错误处理(ProblemDetail)
```

STT 与 LLM 都在接口后面,Gemini / Mock 通过配置切换;要换成别的模型,只需再实现一个接口。

## 关于食物数据(重要)

`src/main/resources/data/foods_sample.csv` 里的 45 条是**示例数据,营养值为近似值,不是瑞士官方数据库**,只用于演示和测试。

要使用 Swiss Food Composition Database:

1. 从官方网站(https://naehrwertdaten.ch)下载数据。
2. 整理成下面的 CSV 列(UTF-8,营养值均为每 100 g,`aliases` 用 `;` 分隔,份量列可留空):

```
id,name_en,name_de,name_zh,aliases,category,kcal,protein_g,fat_g,carb_g,fiber_g,sugar_g,salt_g,piece_g,slice_g,cup_g,bowl_g,serving_g
```

3. 调用 `POST /api/v1/foods/import` 上传。请先确认该数据库的使用许可。

## 测试

```bash
./mvnw test
```

- 单元测试:名称归一化、匹配、份量换算、CSV 解析、Gemini 客户端(用 `MockRestServiceServer` 模拟 HTTP,覆盖重试、非法 JSON、限流)。
- 集成测试:`FoodLogApiIntegrationTest` 通过 Testcontainers 启动真实 MongoDB,需要 Docker;没有 Docker 时**自动跳过而不是失败**(本地没装 Docker 也能跑单元测试)。CI(GitHub Actions 的 ubuntu 镜像自带 Docker)会真正执行这部分测试。
- `FoodMatcherTest` 里有一组带标注的查询(复数、拼写错误、语序、德文、中文),运行时会打印自动匹配准确率。这个数字只反映**示例库**上的表现,换成完整数据库后需要重新评测。

> Docker Engine 29+ 与 Testcontainers 1.x 默认的 API 版本不兼容,所以 `pom.xml` 里给 Surefire 设置了 `api.version=1.44`。如果你的 Docker 版本低于 25,请删掉这一项。

## 已知限制 / 后续可做

- Gemini 的两个实现已用模拟 HTTP 测试,但**没有用真实 Key 端到端验证过**;首次使用请先用一两句话试一遍。
- 前端的**麦克风录音没有在真实浏览器里验证过**(自动化环境没有麦克风),其余界面流程已用 Mock 后端走通;前端与真实后端的联调也还没做过。
- 食物匹配是词级模糊匹配,不理解语义(比如"cottage cheese"和"cheese"的区别依赖库里的条目)。可以在低置信度时增加一次让 LLM 从候选中选择的步骤,或改用向量检索。
- 份量换算是静态表,不会根据"一小碗/一大碗"调整。
- 没有用户认证,`userId` 由调用方传入。
