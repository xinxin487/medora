INSERT INTO department (code, name, category, description, tags, status, sort_order)
VALUES
    ('RESPIRATORY', '呼吸内科', 'OUTPATIENT', '主要处理呼吸系统相关问题。', ARRAY['咳嗽', '呼吸', '肺部'], 'ACTIVE', 10),
    ('GENERAL_INTERNAL', '普通内科', 'OUTPATIENT', '普通内科综合门诊。', ARRAY['发热', '感冒', '全科'], 'ACTIVE', 20),
    ('PEDIATRICS', '儿科', 'OUTPATIENT', '儿童常见疾病门诊。', ARRAY['儿童', '发烧', '咳嗽'], 'ACTIVE', 30);

INSERT INTO doctor (department_id, code, name, title, specialty, intro, tags, status, consultation_fee)
SELECT d.id,
       seed.code,
       seed.name,
       seed.title,
       seed.specialty,
       seed.intro,
       seed.tags,
       'ACTIVE',
       seed.consultation_fee
FROM (
         VALUES
             ('RESPIRATORY', 'DOC_RESP_01', '李医生', '主任医师', '咳嗽、哮喘、慢阻肺', '从业 20 年，擅长呼吸系统疾病门诊咨询。', ARRAY['慢阻肺', '哮喘'], 20.00),
             ('GENERAL_INTERNAL', 'DOC_GEN_01', '王医生', '副主任医师', '感冒、发热、慢性病基础管理', '擅长普通内科常见病诊疗建议与门诊服务。', ARRAY['感冒', '发热'], 15.00),
             ('PEDIATRICS', 'DOC_PED_01', '张医生', '主治医师', '儿童发热、呼吸道感染', '专注儿童常见呼吸道与发热问题。', ARRAY['儿童', '呼吸道'], 18.00)
     ) AS seed(department_code, code, name, title, specialty, intro, tags, consultation_fee)
         JOIN department d ON d.code = seed.department_code;

INSERT INTO schedule_slot (
    department_id,
    doctor_id,
    hospital_code,
    schedule_date,
    period_code,
    start_time,
    end_time,
    total_quota,
    available_quota,
    reserved_quota,
    fee,
    status,
    source_type
)
SELECT d.department_id,
       d.doctor_id,
       'MAIN',
       CURRENT_DATE + s.offset_day,
       s.period_code,
       s.start_time,
       s.end_time,
       s.total_quota,
       s.available_quota,
       0,
       d.consultation_fee,
       'OPEN',
       'SYSTEM'
FROM (
         SELECT doctor.id AS doctor_id,
                doctor.consultation_fee,
                doctor.department_id
         FROM doctor
     ) d
         CROSS JOIN (
    VALUES
        (1, 'AM', TIME '09:00:00', TIME '12:00:00', 20, 8),
        (1, 'PM', TIME '14:00:00', TIME '17:00:00', 20, 6),
        (2, 'AM', TIME '09:00:00', TIME '12:00:00', 20, 10),
        (2, 'PM', TIME '14:00:00', TIME '17:00:00', 20, 9),
        (3, 'AM', TIME '09:00:00', TIME '12:00:00', 20, 12),
        (3, 'PM', TIME '14:00:00', TIME '17:00:00', 20, 11)
) AS s(offset_day, period_code, start_time, end_time, total_quota, available_quota);

