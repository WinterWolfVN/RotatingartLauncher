package com.app.ralaunch.controls;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.lang.reflect.Type;

/**
 * ControlData 自定义反序列化器
 * 确保文本控件的 displayText 字段正确反序列化（即使是空字符串）
 * 处理 joystickComboKeys 从 int[][] 到 int[] 的兼容性
 */
public class ControlDataDeserializer implements JsonDeserializer<ControlData> {
    @Override
    public ControlData deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
        JsonObject jsonObject = json.getAsJsonObject();
        
        // 先使用默认的 Gson 反序列化
        ControlData data = new com.google.gson.Gson().fromJson(jsonObject, ControlData.class);
        
        // 对于文本控件，确保 displayText 正确设置
        if (data != null && data.type == ControlData.TYPE_TEXT) {
            // 如果 JSON 中有 displayText 字段，使用 JSON 中的值（即使是空字符串）
            if (jsonObject.has("displayText")) {
                JsonElement displayTextElement = jsonObject.get("displayText");
                if (displayTextElement.isJsonNull()) {
                    data.displayText = "";
                } else {
                    data.displayText = displayTextElement.getAsString();
                }
            } else {
                // 如果 JSON 中没有 displayText 字段，设置为空字符串（而不是构造函数中的默认值 "文本"）
                data.displayText = "";
            }
        }
        
        // 处理 joystickComboKeys 的兼容性（从 int[][] 转换为 int[]）
        if (data != null && data.type == ControlData.TYPE_JOYSTICK && jsonObject.has("joystickComboKeys")) {
            JsonElement comboKeysElement = jsonObject.get("joystickComboKeys");
            if (comboKeysElement.isJsonArray()) {
                com.google.gson.JsonArray comboKeysArray = comboKeysElement.getAsJsonArray();
                if (comboKeysArray.size() > 0) {
                    // 检查是否是二维数组（旧格式）
                    com.google.gson.JsonElement firstItem = comboKeysArray.get(0);
                    if (firstItem.isJsonArray()) {
                        // 旧格式：二维数组，转换为统一数组（使用第一个非空的组合键）
                        int[] firstNonEmpty = null;
                        for (int i = 0; i < comboKeysArray.size(); i++) {
                            com.google.gson.JsonArray directionArray = comboKeysArray.get(i).getAsJsonArray();
                            if (directionArray != null && directionArray.size() > 0) {
                                firstNonEmpty = new int[directionArray.size()];
                                for (int j = 0; j < directionArray.size(); j++) {
                                    firstNonEmpty[j] = directionArray.get(j).getAsInt();
                                }
                                break;
                            }
                        }
                        data.joystickComboKeys = (firstNonEmpty != null) ? firstNonEmpty : new int[0];
                    } else {
                        // 新格式：一维数组（Gson 应该已经正确反序列化）
                        // 如果 data.joystickComboKeys 仍然是 null，手动处理
                        if (data.joystickComboKeys == null) {
                            int[] comboKeys = new int[comboKeysArray.size()];
                            for (int i = 0; i < comboKeysArray.size(); i++) {
                                comboKeys[i] = comboKeysArray.get(i).getAsInt();
                            }
                            data.joystickComboKeys = comboKeys;
                        }
                    }
                } else {
                    data.joystickComboKeys = new int[0];
                }
            }
        }
        
        return data;
    }
}

