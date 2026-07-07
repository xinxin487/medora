-- V16: 扩充 masterdata 种子数据
-- 背景:V3 只 seed 了 3 个科室 / 3 个医生 / 18 个排班(且排班日期在 V3 执行时算死,过后就过期)
-- 目标:demo 量级 — 覆盖常见症状的 15 个科室、30 个医生、未来 14 天 × 3 时段的排班,随机号源
--
-- 安全性:
--   - 只删除"过期且无 appointment 引用"的 schedule_slot,保留有外键关系的记录
--   - 不动 department / doctor 表的现有行,只追加
--   - 排班日期用 CURRENT_DATE + n,种子执行时计算

-- ============================================================
-- 1) 清理过期 schedule_slot(保留有 appointment 引用的)
-- ============================================================
DELETE FROM schedule_slot
WHERE schedule_date < CURRENT_DATE
  AND id NOT IN (SELECT slot_id FROM appointment WHERE slot_id IS NOT NULL);

-- ============================================================
-- 2) 追加 12 个科室(原 3 个 RESPIRATORY/GENERAL_INTERNAL/PEDIATRICS 保留)
-- ============================================================
INSERT INTO department (code, name, category, description, tags, status, sort_order)
VALUES
    ('CARDIOLOGY',        '心血管内科', 'OUTPATIENT', '心脏、血管相关疾病。',         ARRAY['胸闷','心悸','胸痛','高血压','心绞痛'],         'ACTIVE', 40),
    ('GASTROENTEROLOGY',  '消化内科',   'OUTPATIENT', '消化系统疾病门诊。',           ARRAY['胃痛','腹泻','便秘','反酸','胃炎'],             'ACTIVE', 50),
    ('NEUROLOGY',         '神经内科',   'OUTPATIENT', '神经系统疾病门诊。',           ARRAY['头痛','头晕','失眠','麻木','中风'],             'ACTIVE', 60),
    ('ENDOCRINOLOGY',     '内分泌科',   'OUTPATIENT', '内分泌与代谢疾病。',           ARRAY['糖尿病','甲状腺','肥胖','甲亢','甲减'],         'ACTIVE', 70),
    ('ORTHOPEDICS',       '骨科',       'OUTPATIENT', '骨与关节疾病门诊。',           ARRAY['骨折','关节痛','腰痛','扭伤','颈椎病'],         'ACTIVE', 80),
    ('DERMATOLOGY',       '皮肤科',     'OUTPATIENT', '皮肤、毛发、过敏相关问题。',   ARRAY['皮疹','瘙痒','痤疮','湿疹','荨麻疹'],           'ACTIVE', 90),
    ('OPHTHALMOLOGY',     '眼科',       'OUTPATIENT', '眼部疾病与视力问题。',         ARRAY['眼痛','视力下降','红眼','干眼','近视'],         'ACTIVE', 100),
    ('ENT',               '耳鼻喉科',   'OUTPATIENT', '耳、鼻、喉部疾病。',           ARRAY['鼻炎','咽炎','耳鸣','鼻塞','嗓子痛'],           'ACTIVE', 110),
    ('STOMATOLOGY',       '口腔科',     'OUTPATIENT', '口腔、牙齿疾病。',             ARRAY['牙痛','口腔溃疡','拔牙','牙龈出血','龋齿'],     'ACTIVE', 120),
    ('UROLOGY',           '泌尿外科',   'OUTPATIENT', '泌尿系统疾病。',               ARRAY['尿频','血尿','结石','肾绞痛','前列腺'],         'ACTIVE', 130),
    ('GYNECOLOGY',        '妇科',       'OUTPATIENT', '女性生殖系统疾病与孕检。',     ARRAY['月经','白带','痛经','妇科','孕检'],             'ACTIVE', 140),
    ('PSYCHOLOGY',        '心理科',     'OUTPATIENT', '心理与情绪问题咨询。',         ARRAY['失眠','焦虑','抑郁','压力','情绪'],             'ACTIVE', 150)
ON CONFLICT (code) DO NOTHING;

-- ============================================================
-- 3) 追加医生:原 3 科室各补 1 名 + 新 12 科室各 2 名 = 27 名新增,共 30 名
-- ============================================================
INSERT INTO doctor (department_id, code, name, title, specialty, intro, tags, status, consultation_fee)
SELECT d.id, seed.code, seed.name, seed.title, seed.specialty, seed.intro, seed.tags, 'ACTIVE', seed.fee
FROM (
    VALUES
        -- 原 3 科室补员
        ('RESPIRATORY',      'DOC_RESP_02', '陈主任', '主任医师',   '肺部感染、慢性咳嗽',         '呼吸内科资深专家。',                 ARRAY['感染','咳嗽'],      30.00),
        ('GENERAL_INTERNAL', 'DOC_GEN_02',  '刘医生', '主治医师',   '常见内科病、健康咨询',       '普通内科综合门诊。',                 ARRAY['感冒','体检'],      15.00),
        ('PEDIATRICS',       'DOC_PED_02',  '赵医生', '副主任医师', '儿童呼吸道感染、过敏',       '专注儿科常见病。',                   ARRAY['儿童','过敏'],      20.00),
        -- 心血管内科
        ('CARDIOLOGY',       'DOC_CARD_01', '孙主任', '主任医师',   '高血压、冠心病、心律失常',   '从医 25 年,心血管专家。',            ARRAY['高血压','冠心病'],  35.00),
        ('CARDIOLOGY',       'DOC_CARD_02', '周医生', '副主任医师', '心衰、心脏康复',             '擅长心脏康复指导。',                 ARRAY['心衰','康复'],      28.00),
        -- 消化内科
        ('GASTROENTEROLOGY', 'DOC_GI_01',   '吴主任', '主任医师',   '胃炎、消化性溃疡、肝病',     '消化系统疾病诊治。',                 ARRAY['胃痛','肝病'],      32.00),
        ('GASTROENTEROLOGY', 'DOC_GI_02',   '郑医生', '副主任医师', '肠易激、内镜检查',           '擅长消化内镜。',                     ARRAY['腹泻','内镜'],      25.00),
        -- 神经内科
        ('NEUROLOGY',        'DOC_NEU_01',  '钱主任', '主任医师',   '头痛、脑血管病、癫痫',       '神经内科资深。',                     ARRAY['头痛','中风'],      35.00),
        ('NEUROLOGY',        'DOC_NEU_02',  '冯医生', '主治医师',   '失眠、焦虑伴躯体症状',       '专长睡眠障碍。',                     ARRAY['失眠','睡眠'],      20.00),
        -- 内分泌科
        ('ENDOCRINOLOGY',    'DOC_ENDO_01', '蒋主任', '主任医师',   '糖尿病、甲状腺疾病',         '内分泌代谢病专家。',                 ARRAY['糖尿病','甲状腺'],  33.00),
        ('ENDOCRINOLOGY',    'DOC_ENDO_02', '韩医生', '副主任医师', '肥胖、骨质疏松',             '代谢综合征管理。',                   ARRAY['肥胖','骨质'],      26.00),
        -- 骨科
        ('ORTHOPEDICS',      'DOC_ORTHO_01','杨主任', '主任医师',   '骨折、关节置换',             '骨科手术专家。',                     ARRAY['骨折','关节'],      40.00),
        ('ORTHOPEDICS',      'DOC_ORTHO_02','朱医生', '副主任医师', '颈椎病、腰椎间盘突出',       '脊柱外科专长。',                     ARRAY['颈椎','腰椎'],      30.00),
        -- 皮肤科
        ('DERMATOLOGY',      'DOC_DERM_01', '秦主任', '主任医师',   '湿疹、银屑病、过敏',         '皮肤病专家。',                       ARRAY['湿疹','过敏'],      28.00),
        ('DERMATOLOGY',      'DOC_DERM_02', '许医生', '主治医师',   '痤疮、真菌感染',             '擅长青春痘治疗。',                   ARRAY['痤疮','痘'],        18.00),
        -- 眼科
        ('OPHTHALMOLOGY',    'DOC_OPH_01',  '何主任', '主任医师',   '白内障、青光眼',             '眼科资深。',                         ARRAY['白内障','青光眼'],  35.00),
        ('OPHTHALMOLOGY',    'DOC_OPH_02',  '罗医生', '副主任医师', '近视、干眼症',               '视光专科。',                         ARRAY['近视','干眼'],      25.00),
        -- 耳鼻喉科
        ('ENT',              'DOC_ENT_01',  '高主任', '主任医师',   '鼻炎、鼻窦炎',               '耳鼻喉专家。',                       ARRAY['鼻炎','鼻窦'],      30.00),
        ('ENT',              'DOC_ENT_02',  '林医生', '主治医师',   '中耳炎、咽喉炎',             '擅长中耳疾病。',                     ARRAY['中耳','咽炎'],      18.00),
        -- 口腔科
        ('STOMATOLOGY',      'DOC_STOMA_01','章主任', '主任医师',   '拔牙、种植牙',               '口腔外科。',                         ARRAY['拔牙','种植'],      35.00),
        ('STOMATOLOGY',      'DOC_STOMA_02','蔡医生', '主治医师',   '龋齿、根管治疗',             '牙体牙髓专长。',                     ARRAY['龋齿','根管'],      20.00),
        -- 泌尿外科
        ('UROLOGY',          'DOC_URO_01',  '梁主任', '主任医师',   '泌尿系结石、前列腺',         '泌尿外科专家。',                     ARRAY['结石','前列腺'],   32.00),
        ('UROLOGY',          'DOC_URO_02',  '宋医生', '副主任医师', '尿路感染、血尿',             '擅长尿路感染。',                     ARRAY['感染','血尿'],      24.00),
        -- 妇科
        ('GYNECOLOGY',       'DOC_GYN_01',  '唐主任', '主任医师',   '月经失调、妇科肿瘤',         '妇科专家。',                         ARRAY['月经','妇科'],      35.00),
        ('GYNECOLOGY',       'DOC_GYN_02',  '邓医生', '主治医师',   '孕检、产前咨询',             '围产医学。',                         ARRAY['孕检','产前'],      22.00),
        -- 心理科
        ('PSYCHOLOGY',       'DOC_PSY_01',  '叶主任', '主任医师',   '抑郁、焦虑、失眠',           '心理咨询与治疗。',                   ARRAY['抑郁','焦虑'],      40.00),
        ('PSYCHOLOGY',       'DOC_PSY_02',  '苏医生', '副主任医师', '压力管理、情绪问题',         '认知行为治疗。',                     ARRAY['压力','情绪'],      30.00)
) AS seed(department_code, code, name, title, specialty, intro, tags, fee)
JOIN department d ON d.code = seed.department_code
ON CONFLICT (code) DO NOTHING;

-- ============================================================
-- 4) 重新生成排班 — 14 天 × 3 时段 × 所有医生
--    - 跳过已存在的 (doctor_id, schedule_date, period_code) 组合(uk_slot_doctor_date_period 约束)
--    - 号源随机:total 20,available 5-19(用 random())
-- ============================================================
INSERT INTO schedule_slot (
    department_id, doctor_id, hospital_code,
    schedule_date, period_code, start_time, end_time,
    total_quota, available_quota, reserved_quota,
    fee, status, source_type
)
SELECT
    doc.department_id,
    doc.id,
    'MAIN',
    CURRENT_DATE + day_offset,
    period.code,
    period.start_time,
    period.end_time,
    20,
    floor(random() * 15 + 5)::int,    -- 5-19 之间,每行重新算
    0,
    doc.consultation_fee,
    'OPEN',
    'SYSTEM'
FROM doctor doc
CROSS JOIN generate_series(0, 13) AS day_offset
CROSS JOIN (
    VALUES
        ('AM',      TIME '09:00:00', TIME '12:00:00'),
        ('PM',      TIME '14:00:00', TIME '17:00:00'),
        ('EVENING', TIME '18:00:00', TIME '21:00:00')
) AS period(code, start_time, end_time)
WHERE doc.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1 FROM schedule_slot s
      WHERE s.doctor_id = doc.id
        AND s.schedule_date = CURRENT_DATE + day_offset
        AND s.period_code = period.code
  );
