package com.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * MCP Server Implementation using JSON-RPC 2.0 over stdin/stdout.
 */
public class McpServer {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final OkHttpClient httpClient = new OkHttpClient();

    public static void main(String[] args) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;
                try {
                    processRequest(line);
                } catch (Exception e) {
                    // Send error response if possible, otherwise log to stderr
                    sendError(null, -32700, "Parse error: " + e.getMessage());
                    System.err.println("Error processing request: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Fatal IO error: " + e.getMessage());
        }
    }

    private static void processRequest(String jsonLine) throws IOException {
        JsonNode requestNode = objectMapper.readTree(jsonLine);

        // Basic validation
        if (!requestNode.has("jsonrpc") || !"2.0".equals(requestNode.get("jsonrpc").asText())) {
            sendError(null, -32600, "Invalid Request");
            return;
        }

        String method = requestNode.has("method") ? requestNode.get("method").asText() : null;
        JsonNode id = requestNode.get("id");
        JsonNode params = requestNode.get("params");

        if ("initialize".equals(method)) {
            handleInitialize(id);
        } else if ("tools/list".equals(method)) {
            handleToolsList(id);
        } else if ("tools/call".equals(method)) {
            handleToolsCall(id, params);
        } else {
            sendError(id, -32601, "Method not found: " + method);
        }
    }

    private static void handleInitialize(JsonNode id) throws IOException {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("protocolVersion", "2024-11-05");

        ObjectNode capabilities = objectMapper.createObjectNode();
        ObjectNode tools = objectMapper.createObjectNode();
        tools.put("listChanged", false);
        capabilities.set("tools", tools);
        result.set("capabilities", capabilities);

        ObjectNode serverInfo = objectMapper.createObjectNode();
        serverInfo.put("name", "JavaMcpServer");
        serverInfo.put("version", "1.0.0");
        result.set("serverInfo", serverInfo);

        sendResponse(id, result);
    }

    private static void handleToolsList(JsonNode id) throws IOException {
        ArrayNode tools = objectMapper.createArrayNode();

        // Tool 1: summarize_pdf
        ObjectNode pdfTool = objectMapper.createObjectNode();
        pdfTool.put("name", "summarize_pdf");
        pdfTool.put("description", "Extracts and summarizes text from the first 5 pages of a PDF file.");

        ObjectNode pdfInputSchema = objectMapper.createObjectNode();
        pdfInputSchema.put("type", "object");
        ObjectNode pdfProperties = objectMapper.createObjectNode();
        ObjectNode filePathProp = objectMapper.createObjectNode();
        filePathProp.put("type", "string");
        filePathProp.put("description", "Path to the PDF file");
        pdfProperties.set("file_path", filePathProp);
        pdfInputSchema.set("properties", pdfProperties);
        pdfInputSchema.set("required", objectMapper.createArrayNode().add("file_path"));
        pdfTool.set("inputSchema", pdfInputSchema);
        tools.add(pdfTool);

        // Tool 2: get_weather
        ObjectNode weatherTool = objectMapper.createObjectNode();
        weatherTool.put("name", "get_weather");
        weatherTool.put("description", "Gets current weather information for a specific city.");

        ObjectNode weatherInputSchema = objectMapper.createObjectNode();
        weatherInputSchema.put("type", "object");
        ObjectNode weatherProperties = objectMapper.createObjectNode();
        ObjectNode cityProp = objectMapper.createObjectNode();
        cityProp.put("type", "string");
        cityProp.put("description", "Name of the city");
        weatherProperties.set("city", cityProp);
        weatherInputSchema.set("properties", weatherProperties);
        weatherInputSchema.set("required", objectMapper.createArrayNode().add("city"));
        weatherTool.set("inputSchema", weatherInputSchema);
        tools.add(weatherTool);

        ObjectNode result = objectMapper.createObjectNode();
        result.set("tools", tools);
        sendResponse(id, result);
    }

    private static void handleToolsCall(JsonNode id, JsonNode params) throws IOException {
        if (params == null || !params.has("name")) {
            sendError(id, -32602, "Invalid params: missing tool name");
            return;
        }

        String toolName = params.get("name").asText();
        JsonNode arguments = params.has("arguments") ? params.get("arguments") : objectMapper.createObjectNode();

        try {
            if ("summarize_pdf".equals(toolName)) {
                handleSummarizePdf(id, arguments);
            } else if ("get_weather".equals(toolName)) {
                handleGetWeather(id, arguments);
            } else {
                sendError(id, -32602, "Unknown tool: " + toolName);
            }
        } catch (Exception e) {
            sendError(id, -32603, "Internal error: " + e.getMessage());
            System.err.println("Tool execution error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void handleSummarizePdf(JsonNode id, JsonNode arguments) throws IOException {
        if (!arguments.has("file_path")) {
            sendError(id, -32602, "Missing argument: file_path");
            return;
        }

        String filePath = arguments.get("file_path").asText();
        File file = new File(filePath);

        if (!file.exists()) {
            sendError(id, -32602, "File not found: " + filePath);
            return;
        }

        String textContent;
        try (PDDocument document = PDDocument.load(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            // Set start and end pages to limit to first 5 pages
            stripper.setStartPage(1);
            stripper.setEndPage(Math.min(5, document.getNumberOfPages()));
            textContent = stripper.getText(document);
        }

        // Truncate if exceeds 500 characters
        String summary = textContent;
        if (summary.length() > 500) {
            summary = summary.substring(0, 500) + "...";
        }

        // Clean up newlines for cleaner JSON output
        summary = summary.replaceAll("\\n+", " ").trim();

        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode content = objectMapper.createArrayNode();
        ObjectNode textBlock = objectMapper.createObjectNode();
        textBlock.put("type", "text");
        textBlock.put("text", summary);
        content.add(textBlock);
        result.set("content", content);
        result.put("isError", false);

        sendResponse(id, result);
    }

    private static void handleGetWeather(JsonNode id, JsonNode arguments) throws IOException {
        if (!arguments.has("city")) {
            sendError(id, -32602, "Missing argument: city");
            return;
        }

        String city = arguments.get("city").asText();
        String apiKey = System.getenv("OPENWEATHER_API_KEY");

        if (apiKey == null || apiKey.isEmpty()) {
            sendError(id, -32603, "Environment variable OPENWEATHER_API_KEY is not set");
            return;
        }

        String url = String.format(
                "https://api.openweathermap.org/data/2.5/weather?q=%s&units=metric&lang=zh_cn&appid=%s",
                city, apiKey
        );

        Request request = new Request.Builder().url(url).build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response.code());
            }

            String responseBody = response.body().string();
            JsonNode weatherData = objectMapper.readTree(responseBody);

            // Extract relevant info
            String description = weatherData.path("weather").get(0).path("description").asText("Unknown");
            double temp = weatherData.path("main").path("temp").asDouble(0.0);
            String cityName = weatherData.path("name").asText(city);

            String resultText = String.format("城市: %s, 天气: %s, 温度: %.1f°C", cityName, description, temp);

            ObjectNode result = objectMapper.createObjectNode();
            ArrayNode content = objectMapper.createArrayNode();
            ObjectNode textBlock = objectMapper.createObjectNode();
            textBlock.put("type", "text");
            textBlock.put("text", resultText);
            content.add(textBlock);
            result.set("content", content);
            result.put("isError", false);

            sendResponse(id, result);
        }
    }

    private static void sendResponse(JsonNode id, ObjectNode result) throws IOException {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        if (id != null) {
            response.set("id", id);
        }
        response.set("result", result);
        System.out.println(objectMapper.writeValueAsString(response));
        System.out.flush();
    }

    private static void sendError(JsonNode id, int code, String message) throws IOException {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        if (id != null) {
            response.set("id", id);
        }

        ObjectNode error = objectMapper.createObjectNode();
        error.put("code", code);
        error.put("message", message);
        response.set("error", error);

        System.out.println(objectMapper.writeValueAsString(response));
        System.out.flush();
    }
}