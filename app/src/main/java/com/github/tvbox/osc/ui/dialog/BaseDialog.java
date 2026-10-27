package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;

import xyz.doikki.videoplayer.util.CutoutUtil;

public class BaseDialog extends Dialog {
    public BaseDialog(@NonNull Context context) {
        super(context, R.style.CustomDialogStyle);
    }

    public BaseDialog(Context context, int customDialogStyle) {
        super(context, customDialogStyle);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        CutoutUtil.adaptCutoutAboveAndroidP(this, true);//设置刘海
        super.onCreate(savedInstanceState);
    }

    @Override
    public void show() {
        if (isContextInvalid()) {
            return;
        }
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        super.show();
        hideSysBar();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        // 手机版：对话框窗口统一全宽，高度随内容（需在 show 之后设置才生效）
        Window window = getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private boolean isContextInvalid() {
        Context context = getContext();
        if (!(context instanceof Activity)) {
            return false;
        }
        Activity activity = (Activity) context;
        return activity.isFinishing()
                || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed());
    }

    /**
     * 手机版底部面板：贴屏幕下缘全宽显示，高度占屏幕 78%，从下向上滑入。
     * 必须在 {@code super.show()} 之前调用。
     */
    protected void prepareBottomSheet() {
        Window window = getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setGravity(Gravity.BOTTOM);
        window.setWindowAnimations(R.style.BottomSheetDialogAnimation);
        // 上一次拖动关闭可能残留位移，重新弹出前归零
        window.getDecorView().setTranslationY(0f);
    }

    /**
     * 手机版底部面板：在 {@code show()} 之后设置贴底全宽尺寸，并把标题行变成下滑关闭的拖拽区。
     *
     * @param dragView 手指按住可向下拖动关闭弹窗的标题行
     */
    protected void applyBottomSheet(View dragView) {
        Window window = getWindow();
        if (window == null) return;
        DisplayMetrics dm = getContext().getResources().getDisplayMetrics();
        int sheetHeight = (int) (dm.heightPixels * 0.78f);
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, sheetHeight);
        View decor = window.getDecorView();
        decor.setTranslationY(0f);
        installDragToDismiss(decor, dragView, sheetHeight);
    }

    private void installDragToDismiss(final View decor, View dragView, final int sheetHeight) {
        if (dragView == null) return;
        dragView.setOnTouchListener(new View.OnTouchListener() {
            private float downRawY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        float offset = event.getRawY() - downRawY;
                        // 只跟随向下拖动，向上拖动回弹到原位
                        decor.setTranslationY(Math.max(0f, offset));
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (decor.getTranslationY() > Math.max(160f, sheetHeight * 0.2f)) {
                            if (isShowing()) dismiss();
                        } else {
                            decor.animate().translationY(0f).setDuration(180).start();
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    /**
     * 手机版：对话框跟随宿主页面——只有全屏播放/直播页才隐藏系统栏，
     * 普通页面保持顶部状态栏可见。
     */
    private void hideSysBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            boolean immersion = false;
            Context context = getContext();
            while (context instanceof android.content.ContextWrapper) {
                if (context instanceof BaseActivity) {
                    immersion = ((BaseActivity) context).isImmersionPage();
                    break;
                }
                context = ((android.content.ContextWrapper) context).getBaseContext();
            }
            int uiOptions = getWindow().getDecorView().getSystemUiVisibility();
            if (immersion) {
                uiOptions |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
                uiOptions |= View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
                uiOptions |= View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
                uiOptions |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
                uiOptions |= View.SYSTEM_UI_FLAG_FULLSCREEN;
                uiOptions |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            } else {
                uiOptions &= ~(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            }
            getWindow().getDecorView().setSystemUiVisibility(uiOptions);
        }
    }
}
