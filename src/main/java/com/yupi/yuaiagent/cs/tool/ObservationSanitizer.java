package com.yupi.yuaiagent.cs.tool;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Observation 瘦身：工具返回的原始 JSON 往往字段全、噪音多，直接塞给 LLM 会淹没关键事实。
 * 这里在「工具返回后、模型思考前」做一层基于规则的裁剪，属于数据流转管道中的滤网，零侵入。
 */
@Component
@Slf4j
public class ObservationSanitizer {

    /** 通用大字段：明细、附件、审批流等，对当前决策无帮助 */
    private static final Set<String> BULK_FIELDS = Set.of(
            "material_list", "materialList", "approval_form_history", "approvalHistory",
            "attachments", "attachment_list", "detail_list", "item_list", "operation_logs");

    /** 状态历史保留的最近条数 */
    private static final int RECENT_HISTORY_SIZE = 3;

    /** 业务异常关键词：即使不在最近几条，也必须保留，避免"缺料停产"被预排产等噪音覆盖 */
    private static final List<String> CRITICAL_KEYWORDS = List.of(
            "缺料", "停产", "异常", "失败", "拒绝", "驳回", "超时", "逾期", "取消", "冻结", "风险");

    /** 单条 Observation 最大字符数，防止超长文本挤占上下文 */
    private static final int MAX_CHARS = 4000;

    /**
     * @return 清洗后的观察文本，以及被裁剪掉的字符数（用于成本统计）
     */
    public SanitizedObservation sanitize(String toolName, String rawObservation) {
        if (rawObservation == null || rawObservation.isBlank()) {
            return new SanitizedObservation(rawObservation, 0);
        }
        String cleaned = rawObservation;
        try {
            String trimmed = rawObservation.trim();
            if (trimmed.startsWith("{")) {
                cleaned = sanitizeJsonObject(toolName, JSONUtil.parseObj(trimmed));
            } else if (trimmed.startsWith("[")) {
                JSONArray array = JSONUtil.parseArray(trimmed);
                cleaned = array.size() > 20 ? array.subList(0, 20).toString() + "\n...[数组已裁剪]" : trimmed;
            }
        } catch (Exception e) {
            // 解析失败原样返回，兜底不影响主流程
            log.debug("[CS] Observation 清洗失败，原样返回 tool={} error={}", toolName, e.getMessage());
            cleaned = rawObservation;
        }
        cleaned = truncate(cleaned);
        int saved = Math.max(0, rawObservation.length() - cleaned.length());
        if (saved > 0) {
            log.info("[CS] Observation 瘦身 tool={} 原始={}字符 清洗后={}字符 节省={}字符(约{}token)",
                    toolName, rawObservation.length(), cleaned.length(), saved, saved / 2);
        }
        return new SanitizedObservation(cleaned, saved);
    }

    private String sanitizeJsonObject(String toolName, JSONObject json) {
        // 1. 砍掉无关的巨量明细字段
        BULK_FIELDS.forEach(json::remove);

        // 2. 状态流转历史：只保留最近 N 条 + 所有异常条目
        JSONArray history = json.getJSONArray("status_history");
        if (history == null) {
            history = json.getJSONArray("statusHistory");
        }
        if (history != null && history.size() > RECENT_HISTORY_SIZE) {
            JSONArray slim = new JSONArray();
            int from = history.size() - RECENT_HISTORY_SIZE;
            for (int i = from; i < history.size(); i++) {
                slim.add(history.get(i));
            }
            // 强制保留异常状态，即使它较早
            for (int i = 0; i < from; i++) {
                String item = String.valueOf(history.get(i));
                if (CRITICAL_KEYWORDS.stream().anyMatch(item::contains) && !slim.toString().contains(item)) {
                    slim.add(history.get(i));
                }
            }
            json.set("status_history", slim);
        }

        // 3. 针对采购单：额外保留关键业务字段，明确告诉模型当前事实
        if ("query_erp_purchase_order".equals(toolName)) {
            json.set("_observation_note", "已裁剪物料明细与审批流，状态历史保留最近"
                    + RECENT_HISTORY_SIZE + "条及所有异常状态，请以状态历史中的异常反馈为准");
        }
        return json.toString();
    }

    private String truncate(String text) {
        if (text == null || text.length() <= MAX_CHARS) {
            return text;
        }
        return text.substring(0, MAX_CHARS) + "\n...[Observation 超过 " + MAX_CHARS + " 字符，已截断]";
    }

    public record SanitizedObservation(String content, int savedChars) {
    }
}
