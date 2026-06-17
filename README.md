# Week2 - MCP Agent 实验二

## 项目简介

这是一个基于 **MCP (Model Context Protocol)** 协议的 Java 智能助手，通过标准输入输出（stdin/stdout）与 AI 客户端（如 Cursor、Claude Desktop）通信，提供以下两个工具：

| 工具名称 | 功能描述 |
|---------|---------|
| `summarize_pdf` | 读取 PDF 文件的前 5 页文本内容，截取前 500 字返回摘要 |
| `get_weather` | 查询指定城市的当前天气和温度（使用 OpenWeatherMap API） |

## 技术栈

- **语言**：Java 17
- **构建工具**：Maven
- **核心依赖**：
  - Apache PDFBox 2.0.29（PDF 解析）
  - OkHttp 4.11.0（HTTP 请求）
  - Jackson 2.15.2（JSON 解析）

## 运行方式

### 1. 环境配置

在运行前，需要设置环境变量：

```bash
export OPENWEATHER_API_KEY=你的API密钥   # Mac/Linux
set OPENWEATHER_API_KEY=你的API密钥      # Windows CMD
$env:OPENWEATHER_API_KEY="你的API密钥"   # Windows PowerShell
