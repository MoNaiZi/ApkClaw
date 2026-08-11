package com.apk.claw.android.tool.impl;

import com.apk.claw.android.ClawApplication;
import com.apk.claw.android.R;
import com.apk.claw.android.service.ClawAccessibilityService;
import com.apk.claw.android.tool.BaseTool;
import com.apk.claw.android.tool.ToolParameter;
import com.apk.claw.android.tool.ToolResult;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class GetScreenInfoTool extends BaseTool {

    @Override
    public String getName() {
        return "get_screen_info";
    }

    @Override
    public String getDisplayName() {
        return ClawApplication.Companion.getInstance().getString(R.string.tool_name_get_screen_info);
    }

    @Override
    public String getDescriptionEN() {
        return "Get the current screen's UI hierarchy tree, including all visible elements with their properties (text, id, bounds, clickable, etc.). Use this to understand what is currently displayed on the screen.";
    }

    @Override
    public String getDescriptionCN() {
        return "获取当前屏幕的UI层级树，包括所有可见元素的属性（文本、ID、边界、可点击状态等）。用于了解当前屏幕显示的内容。";
    }

    @Override
    public List<ToolParameter> getParameters() {
        return Collections.emptyList();
    }

    public static final String SYSTEM_DIALOG_BLOCKED = "__SYSTEM_DIALOG_BLOCKED__";

    /**
     * 切换为完整节点树模式（包含所有节点和全部属性，用于调试）。
     * false = 精简模式（默认，省 token）；true = 完整模式。
     */
    public static boolean useFullTree = false;

    @Override
    public ToolResult execute(Map<String, Object> params) {
        ClawAccessibilityService service = ClawAccessibilityService.getInstance();
        if (service == null) {
            return ToolResult.error("Accessibility service is not running");
        }
        String tree = useFullTree ? service.getScreenTreeFull() : service.getScreenTree();
        if (tree == null) {
            return ToolResult.error(SYSTEM_DIALOG_BLOCKED);
        }
        // 节点树过于稀疏（如游戏/视频界面只有一个 SurfaceView 节点，几乎没有可读内容）时，
        // 引导 Agent 切换到视觉模式（see_screen），这是"节点树为主、视觉兜底"策略的关键信号
        if (tree.trim().isEmpty() || tree.length() < 100) {
            tree = tree + "\n\n[提示] 当前界面几乎没有任何可读的节点信息，这通常是游戏、视频或绘图类应用（它们没有无障碍节点）。请改用 see_screen 工具截取屏幕图片进行视觉分析。";
        }
        // 节点树返回的 bounds 是物理坐标，后续坐标操作不应再按视觉缩放换算
        service.resetVisualScale();
        return ToolResult.success(tree);
    }
}
