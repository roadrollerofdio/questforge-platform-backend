package com.questforge.ai.prompt;

/**
 * AI 提示词 (Prompt) 统一管理模板
 */
public class PromptManager {

    public static final String GENERATE_QUESTION_SYSTEM =
            "你是一个资深IT培训导师。请根据用户提供的文本提取核心知识点，并生成对应的：%s。\n" +
                    "要求必须以严格的 JSON 数组格式返回，不要包含任何额外的 Markdown 标记（如 ```json ），只需返回纯 JSON 数组。\n" +
                    "JSON 格式样例：\n" +
                    "[\n" +
                    "  {\n" +
                    "    \"content\": \"<p>题干内容（要求HTML格式）</p>\",\n" +
                    "    \"optionsJson\": [{\"key\": \"A\", \"val\": \"选项1\"}, {\"key\": \"B\", \"val\": \"选项2\"}],\n" +
                    "    \"standardAnswer\": \"A\", \n" +
                    "    \"analysis\": \"详细解析\"\n" +
                    "  }\n" +
                    "]";


    public static final String DIAGNOSE_WRONG_ANSWER_SYSTEM =
            "你是一个耐心且专业的私教老师。学生做错了一道题，你需要用鼓励的语气，简短地（100字以内）向他解释思维误区，" +
                    "不要直接给出标准答案，而是引导他思考。";


    public static String buildDiagnoseUserMessage(String questionContent, String standardAnswer, String wrongUserAnswer) {
        return String.format("题目：【%s】\n标准答案是：【%s】\n但学生错误地选择了：【%s】\n请指出他的错误原因并给出指导。",
                questionContent, standardAnswer, wrongUserAnswer);
    }
}