package com.firedemo.demo.mcp;

/**
 * ThreadLocal 持有者，让 MCP 工具在 execute 时获取用户上下文。
 */
public final class ToolContextHolder {

    private static final ThreadLocal<ToolContext> CTX = new ThreadLocal<>();

    public static void set(ToolContext ctx) { CTX.set(ctx); }
    public static ToolContext get() { return CTX.get(); }
    public static void clear() { CTX.remove(); }

    private ToolContextHolder() {}
}
