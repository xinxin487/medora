package com.medora.adk.agents;

import com.google.adk.agents.LlmAgent;
import com.google.adk.models.langchain4j.LangChain4j;
import com.google.adk.tools.BaseTool;
import com.medora.adk.config.AdkProperties;
import com.medora.adk.guard.HighRiskToolBeforeCallback;
import com.medora.adk.guard.LlmHallucinationAfterCallback;
import com.medora.adk.guard.LlmInputSafetyBeforeCallback;
import com.medora.adk.guard.LlmOutputSafetyAfterCallback;
import com.medora.adk.tool.MedoraFunctionTool;
import com.medora.adk.tool.ToolSchemaRegistry;
import com.medora.agent.tool.ToolNames;
import com.medora.agent.tool.ToolRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Phase 9.5 多 agent 拓扑定义。
 *
 * RootRouterAgent(instruction-only,无业务工具)
 *   ├── TriageAgent        工具: searchKnowledge
 *   ├── BookingAgent       工具: queryDepartments/queryDoctors/queryScheduleSlots/
 *   │                              getUserAppointments/bookAppointment/cancelAppointment
 *   │                      callback: HighRiskToolBeforeCallback
 *   └── KnowledgeAgent     工具: searchKnowledge
 *                          callback: LlmHallucinationAfterCallback
 *
 * 所有 agent 共享 InputSafety + OutputSafety callback(由 ADK 在 transfer 后子 agent 自己的
 * invocation 时触发)。
 *
 * 设计点:
 *  - Root 不持业务工具,避免 LLM 直接答复业务问题而绕过子 agent 的专科 prompt
 *  - sub_agents_transfer 由 ADK 原生支持,RootAgent 的 instruction 描述「何时转给谁」即可
 *  - KnowledgeAgent 用独立的 Hallucination callback,Triage/Booking 不挂(它们不答医疗知识)
 */
@Configuration
@ConditionalOnProperty(prefix = "medora.adk", name = "enabled", havingValue = "true")
public class AdkAgentTopology {

    private final LangChain4j deepseekLlmBridge;
    private final ToolRegistry toolRegistry;
    private final ToolSchemaRegistry schemaRegistry;
    private final AdkProperties adkProperties;
    private final LlmInputSafetyBeforeCallback inputSafety;
    private final LlmOutputSafetyAfterCallback outputSafety;
    private final HighRiskToolBeforeCallback highRiskToolGuard;
    private final LlmHallucinationAfterCallback hallucinationGuard;

    public AdkAgentTopology(
            LangChain4j deepseekLlmBridge,
            ToolRegistry toolRegistry,
            ToolSchemaRegistry schemaRegistry,
            AdkProperties adkProperties,
            LlmInputSafetyBeforeCallback inputSafety,
            LlmOutputSafetyAfterCallback outputSafety,
            HighRiskToolBeforeCallback highRiskToolGuard,
            LlmHallucinationAfterCallback hallucinationGuard) {
        this.deepseekLlmBridge = deepseekLlmBridge;
        this.toolRegistry = toolRegistry;
        this.schemaRegistry = schemaRegistry;
        this.adkProperties = adkProperties;
        this.inputSafety = inputSafety;
        this.outputSafety = outputSafety;
        this.highRiskToolGuard = highRiskToolGuard;
        this.hallucinationGuard = hallucinationGuard;
    }

    @Bean
    public BaseTool adkQueryDepartmentsTool() {
        return MedoraFunctionTool.create(ToolNames.QUERY_DEPARTMENTS, toolRegistry, schemaRegistry);
    }

    @Bean
    public BaseTool adkQueryDoctorsTool() {
        return MedoraFunctionTool.create(ToolNames.QUERY_DOCTORS, toolRegistry, schemaRegistry);
    }

    @Bean
    public BaseTool adkQueryScheduleSlotsTool() {
        return MedoraFunctionTool.create(ToolNames.QUERY_SCHEDULE_SLOTS, toolRegistry, schemaRegistry);
    }

    @Bean
    public BaseTool adkGetUserAppointmentsTool() {
        return MedoraFunctionTool.create(ToolNames.GET_USER_APPOINTMENTS, toolRegistry, schemaRegistry);
    }

    @Bean
    public BaseTool adkBookAppointmentTool() {
        return MedoraFunctionTool.create(ToolNames.BOOK_APPOINTMENT, toolRegistry, schemaRegistry);
    }

    @Bean
    public BaseTool adkCancelAppointmentTool() {
        return MedoraFunctionTool.create(ToolNames.CANCEL_APPOINTMENT, toolRegistry, schemaRegistry);
    }

    @Bean
    public BaseTool adkSearchKnowledgeTool() {
        return MedoraFunctionTool.create(ToolNames.SEARCH_KNOWLEDGE, toolRegistry, schemaRegistry);
    }

    @Bean
    public LlmAgent triageAgent(BaseTool adkSearchKnowledgeTool) {
        return LlmAgent.builder()
                .name("triage_agent")
                .description("导诊助手:根据症状描述推荐就诊科室")
                .model(deepseekLlmBridge)
                .instruction("""
                        你是 Medora 医院的导诊助手。职责是:
                          1) 根据用户描述的症状,推荐合适的就诊科室
                          2) 必要时使用 searchKnowledge 工具查询科室介绍

                        规则:
                          - 不要做疾病诊断,只做症状到科室的映射
                          - 用户问"我哪里不舒服应该看什么科"时,先用 searchKnowledge 查询,
                            如果信息不足以推荐,反问用户更具体的症状
                          - 用户问"这个科室怎么挂号"时,用 transfer_to_agent 转给 booking_agent
                          - 用户问医学知识时,用 transfer_to_agent 转给 knowledge_agent
                        """)
                .tools(List.of(adkSearchKnowledgeTool))
                .beforeModelCallbackSync(inputSafety)
                .afterModelCallbackSync(outputSafety)
                .maxSteps(4)
                .build();
    }

    @Bean
    public LlmAgent bookingAgent(BaseTool adkQueryDepartmentsTool,
                                 BaseTool adkQueryDoctorsTool,
                                 BaseTool adkQueryScheduleSlotsTool,
                                 BaseTool adkGetUserAppointmentsTool,
                                 BaseTool adkBookAppointmentTool,
                                 BaseTool adkCancelAppointmentTool) {
        return LlmAgent.builder()
                .name("booking_agent")
                .description("预约助手:挂号、查询、取消")
                .model(deepseekLlmBridge)
                .instruction("""
                        你是 Medora 医院的预约助手。可调用以下工具:
                          - queryDepartments / queryDoctors / queryScheduleSlots:查询信息
                          - getUserAppointments:查询用户已有预约
                          - bookAppointment(slotId):挂号(slotId 必须来自上一轮 queryScheduleSlots 结果)
                          - cancelAppointment(appointmentId):取消(appointmentId 必须来自上一轮 getUserAppointments 结果)

                        Slot 填充规则(关键):
                          - 不要用 LLM 推断/幻觉的 slotId 或 appointmentId 调高风险工具
                          - 缺信息时直接反问用户,例如:
                              * 缺科室:问"您想看哪个科室?"
                              * 缺医生:问"有指定的医生吗?没有的话我推荐一位?"
                              * 缺日期/时段:问"您希望约什么时间?"
                          - 调 bookAppointment 前,必须先用 queryScheduleSlots 把 slotId 真实拿到
                          - 调 cancelAppointment 前,必须先用 getUserAppointments 确认预约 ID

                        **工具调用后的回复规则(必须遵守)**:
                          - 调用任何工具后,必须用自然语言告诉用户结果,不能不输出文字就结束。
                          - bookAppointment 成功后,告诉用户:预约成功、科室、医生、日期、时段、挂号费。
                            (从工具返回的 data 字段读取,不要编造)
                          - bookAppointment 失败后,告诉用户失败原因,并询问是否换时间/医生。
                          - cancelAppointment 成功后,告诉用户已取消的预约号。
                          - queryScheduleSlots 返回后,用易读的格式列出可选号源。
                          - 即使工具返回的内容已经包含信息,也必须用助手口吻复述,不能让用户
                            自己去看工具响应。

                        用户问题超出预约范畴时(如医学知识、症状咨询),用 transfer_to_agent 转给对应 agent。
                        """)
                .tools(List.of(adkQueryDepartmentsTool, adkQueryDoctorsTool,
                        adkQueryScheduleSlotsTool, adkGetUserAppointmentsTool,
                        adkBookAppointmentTool, adkCancelAppointmentTool))
                .beforeModelCallbackSync(inputSafety)
                .afterModelCallbackSync(outputSafety)
                .beforeToolCallbackSync(highRiskToolGuard)
                .maxSteps(8)
                .build();
    }

    @Bean
    public LlmAgent knowledgeAgent(BaseTool adkSearchKnowledgeTool) {
        return LlmAgent.builder()
                .name("knowledge_agent")
                .description("知识助手:基于已发布知识库回答医学常识问题")
                .model(deepseekLlmBridge)
                .instruction("""
                        你是 Medora 医院的知识助手。必须基于 searchKnowledge 工具的检索结果回答,
                        不要凭记忆编造。

                        规则:
                          - 每次回答前先调 searchKnowledge,基于检索结果组织回答
                          - 引用要标注来源(把检索到的关键句作为引用)
                          - 不要做诊断或开药建议;若用户问这类问题,建议就医
                          - 用户问预约/挂号时,用 transfer_to_agent 转给 booking_agent
                        """)
                .tools(List.of(adkSearchKnowledgeTool))
                .beforeModelCallbackSync(inputSafety)
                .afterModelCallbackSync(outputSafety)
                .afterModelCallbackSync(hallucinationGuard)
                .maxSteps(5)
                .build();
    }

    /**
     * Root router agent —— 不持业务工具,instruction 只描述路由规则。
     * 由 ADK 在 LLM 决策后调用 transfer_to_agent 切换到对应子 agent。
     */
    @Bean
    public LlmAgent rootRouterAgent(LlmAgent triageAgent,
                                    LlmAgent bookingAgent,
                                    LlmAgent knowledgeAgent) {
        return LlmAgent.builder()
                .name("root_router_agent")
                .description("Medora 医院助手:统一入口,根据请求类型转给专科 agent")
                .model(deepseekLlmBridge)
                .instruction("""
                        你是 Medora 医院助手的总入口。你的唯一职责是判断用户请求应该交给哪个专科 agent,
                        然后调用 transfer_to_agent 转过去。不要直接回答业务问题。

                        路由规则:
                          - 症状/不适描述(如"我头疼看什么科","哪里不舒服该挂什么科")
                              → transfer_to_agent("triage_agent")
                          - 挂号/预约/查询预约/取消预约(如"我想挂号","帮我取消预约","查下我的预约")
                              → transfer_to_agent("booking_agent")
                          - 医学知识/健康常识/检查结果解读(如"高血压要注意什么","什么是 CT")
                              → transfer_to_agent("knowledge_agent")

                        如果请求类型不明确(如只是打招呼),可以简短答复后引导用户说明诉求。
                        """)
                .subAgents(triageAgent, bookingAgent, knowledgeAgent)
                .beforeModelCallbackSync(inputSafety)
                .afterModelCallbackSync(outputSafety)
                .maxSteps(2)
                .build();
    }
}
