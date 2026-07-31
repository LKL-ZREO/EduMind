package com.firedemo.edumind.platform.security.crypto;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * MyBatis-Plus TypeHandler — 字段自动 AES 加解密。
 * <p>
 * ⚠️ 不要加 {@code @Component}，否则会成为全局默认 TypeHandler，影响所有 String 字段。
 * 仅在实体的 {@code @TableField(typeHandler = AESEncryptHandler.class)} 上按需使用。
 * <p>
 * 使用前需注入 {@link AesUtil}：
 * <pre>
 * &#64;TableName(value = "sys_user", autoResultMap = true)
 * public class User {
 *     &#64;TableField(typeHandler = AESEncryptHandler.class)
 *     private String phone;
 * }
 * </pre>
 *
 * @see AesUtil
 */
@MappedJdbcTypes(JdbcType.VARCHAR)
@MappedTypes(String.class)
public class AESEncryptHandler extends BaseTypeHandler<String> {

    /**
     * Spring 容器注入的 AesUtil 实例。
     * 通过 {@code AESEncryptHandler.register(AesUtil)} 在启动时注册。
     */
    private static volatile AesUtil aesUtil;

    /** 启动时由 ApplicationRunner 调用 */
    public static void register(AesUtil util) {
        aesUtil = util;
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, aesUtil.encrypt(parameter));
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return tryDecrypt(rs.getString(columnName));
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return tryDecrypt(rs.getString(columnIndex));
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return tryDecrypt(cs.getString(columnIndex));
    }

    /**
     * 尝试解密。若解密失败（旧数据是明文），返回原值——兼容加密迁移过渡期。
     * <p>
     * 前置判断：如果值包含明显非 Base64 字符（如 @ . 中文等），
     * 说明是明文数据，直接返回避免触发 AesUtil 的 ERROR 日志。
     */
    private String tryDecrypt(String value) {
        if (value == null) return null;
        // 快速判断：Base64 只包含 A-Z a-z 0-9 + / =，含其他字符的必为明文
        if (value.contains("@") || value.contains(".") && value.length() < 50) {
            return value;
        }
        try {
            return aesUtil.decrypt(value);
        } catch (Exception e) {
            // 现有数据库里是明文，解密失败说明还没迁移，返回原值
            return value;
        }
    }
}
