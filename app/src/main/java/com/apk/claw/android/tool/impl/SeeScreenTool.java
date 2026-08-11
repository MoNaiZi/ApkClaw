package com.apk.claw.android.tool.impl;

import android.graphics.Bitmap;

import com.apk.claw.android.ClawApplication;
import com.apk.claw.android.R;
import com.apk.claw.android.service.ClawAccessibilityService;
import com.apk.claw.android.tool.BaseTool;
import com.apk.claw.android.tool.ToolParameter;
import com.apk.claw.android.tool.ToolResult;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 视觉观察工具：截取当前屏幕，压缩后以 base64 图片交给 LLM 进行视觉分析。
 * 用于 UI 层级树没有有效内容（游戏、视频、绘图类应用等）的场景。
 * 返回 data URI（如 data:image/jpeg;base64,...），由 Agent 循环注入多模态 UserMessage。
 */
public class SeeScreenTool extends BaseTool {

    /** 默认最大宽度：768px 足够视觉模型看清界面，同时控制 token 消耗 */
    private static final int DEFAULT_MAX_WIDTH = 768;
    /** 允许的宽度范围 */
    private static final int MIN_MAX_WIDTH = 320;
    private static final int MAX_MAX_WIDTH = 1280;
    /** JPEG 压缩质量 */
    private static final int JPEG_QUALITY = 85;

    /** 最近一次发送给 LLM 的截图实际尺寸（缩放后），供 Agent 循环注入坐标提示 */
    private static volatile int sLastImageWidth = 0;
    private static volatile int sLastImageHeight = 0;

    public static int getLastImageWidth() {
        return sLastImageWidth;
    }

    public static int getLastImageHeight() {
        return sLastImageHeight;
    }

    @Override
    public String getName() {
        return "see_screen";
    }

    @Override
    public String getDisplayName() {
        return ClawApplication.Companion.getInstance().getString(R.string.tool_name_see_screen);
    }

    @Override
    public String getDescriptionEN() {
        return "Capture the current screen as an image and send it to the AI for visual analysis. " +
                "Use this when the UI hierarchy tree is empty or unhelpful (e.g. games, videos, drawing apps, " +
                "or screens rendered without accessibility nodes). The image is downscaled automatically to save tokens. " +
                "After calling this, you will receive the screenshot as an image in the next message, so observe it directly.";
    }

    @Override
    public String getDescriptionCN() {
        return "截取当前屏幕并以图片形式交给 AI 进行视觉分析。当 UI 层级树为空或没有有用信息时使用（如游戏、视频、绘图类应用，或没有无障碍节点的界面）。图片会自动压缩以节省 Token。调用后你会在下一条消息中收到屏幕截图，请直接观察图片内容。";
    }

    @Override
    public List<ToolParameter> getParameters() {
        ToolParameter maxWidth = new ToolParameter(
                "max_width",
                "integer",
                "Optional: maximum width of the captured image in pixels. Default 768. Larger values give more detail but consume more tokens.",
                false
        );
        return Collections.singletonList(maxWidth);
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        ClawAccessibilityService service = ClawAccessibilityService.getInstance();
        if (service == null) {
            return ToolResult.error("Accessibility service is not running");
        }

        int maxWidth = DEFAULT_MAX_WIDTH;
        Object maxWidthObj = params.get("max_width");
        if (maxWidthObj instanceof Number) {
            maxWidth = ((Number) maxWidthObj).intValue();
        } else if (maxWidthObj != null) {
            try {
                maxWidth = Integer.parseInt(maxWidthObj.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        if (maxWidth < MIN_MAX_WIDTH) maxWidth = MIN_MAX_WIDTH;
        if (maxWidth > MAX_MAX_WIDTH) maxWidth = MAX_MAX_WIDTH;

        Bitmap bitmap = service.takeScreenshot(5000);
        if (bitmap == null) {
            return ToolResult.error("Failed to take screenshot. Requires Android 11+ (API 30).");
        }

        try {
            // 深拷贝，避免后续复用问题
            Bitmap copy = bitmap.copy(Bitmap.Config.ARGB_8888, false);
            if (copy != null) {
                bitmap.recycle();
                bitmap = copy;
            }

            // 按比例缩放到 max_width 以内，控制图片大小与 token 消耗。
            // 记录实际缩放比例，供坐标类工具（tap/swipe/long_press）把 LLM 返回的图片坐标换算为物理坐标。
            int originalWidth = bitmap.getWidth();
            float scale = 1.0f;
            if (originalWidth > maxWidth) {
                scale = (float) maxWidth / originalWidth;
                int newWidth = maxWidth;
                int newHeight = Math.max(1, Math.round(bitmap.getHeight() * scale));
                Bitmap scaled = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
                if (scaled != bitmap) {
                    bitmap.recycle();
                    bitmap = scaled;
                }
            }
            service.setVisualScale(scale);
            sLastImageWidth = bitmap.getWidth();
            sLastImageHeight = bitmap.getHeight();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, baos);
            bitmap.recycle();

            String base64 = android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP);
            return ToolResult.success("data:image/jpeg;base64," + base64);
        } catch (Exception e) {
            if (!bitmap.isRecycled()) {
                bitmap.recycle();
            }
            return ToolResult.error("Failed to capture screen image: " + e.getMessage());
        }
    }
}
