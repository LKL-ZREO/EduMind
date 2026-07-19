-- V8: 课堂出勤日志
CREATE TABLE IF NOT EXISTS live_attendance_log (
    id              BIGSERIAL PRIMARY KEY,
    session_id      BIGINT NOT NULL,
    class_id        BIGINT,
    student_id      VARCHAR(64) NOT NULL,
    student_name    VARCHAR(128) NOT NULL,
    event           VARCHAR(16) NOT NULL CHECK (event IN ('JOIN', 'LEAVE')),
    event_time      TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_attendance_session ON live_attendance_log(session_id);
CREATE INDEX IF NOT EXISTS idx_attendance_student ON live_attendance_log(student_id);
CREATE INDEX IF NOT EXISTS idx_attendance_class ON live_attendance_log(class_id);
COMMENT ON TABLE live_attendance_log IS '课堂出勤事件日志';
