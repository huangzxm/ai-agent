package com.yupi.yuaiagent.agent;


import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

/**
 * ReAct (Reasoning and Acting) 模式的代理抽象类
 * 实现了思考-行动的循环模式
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public abstract class ReActAgent extends BaseAgent  {

    /**
     * 处理当前状态并决定下一步行动
     *
     * @return 是否需要执行行动，true表示需要执行，false表示不需要执行
     */
    public abstract boolean think();

    /**
     * 执行行动
     *
     * @return 行动的结果
     */
    public abstract String act();

    /**
     * 执行单个步骤：思考和行动
     *
     * @return 步骤执行结果
     */
    @Override
    public String step() {
        try {
            boolean shouldAct = think(); // 思考
            if (!shouldAct) {
                return "思考完成，不需要行动";
            }
            return act(); // 行动
        }catch (Exception e) {
            log.error(e.getMessage());
            e.printStackTrace();
            return "执行步骤时发生错误" + e.getMessage();
        }
    }

}
