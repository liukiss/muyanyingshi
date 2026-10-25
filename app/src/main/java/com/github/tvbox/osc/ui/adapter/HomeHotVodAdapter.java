package com.github.tvbox.osc.ui.adapter;

import android.graphics.Bitmap;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.ImgUtil;
import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;

import me.jessyan.autosize.utils.AutoSizeUtils;

public class HomeHotVodAdapter extends BaseQuickAdapter<Movie.Video, BaseViewHolder> {
    private int defaultWidth;
    private final ImgUtil.Style style;
    private String tvRateValue;

    public HomeHotVodAdapter(ImgUtil.Style style, String tvRate) {
        super(R.layout.item_user_hot_vod, new ArrayList<>());
        if (style != null) {
            this.defaultWidth = ImgUtil.getStyleDefaultWidth(style);
        }
        this.style = style;
        this.tvRateValue = tvRate;
    }

    @Override
    protected void convert(BaseViewHolder helper, Movie.Video item) {
        FrameLayout tvDel = helper.getView(R.id.delFrameLayout);
        tvDel.setVisibility(HawkConfig.hotVodDelete ? View.VISIBLE : View.GONE);

        TextView tvRate = helper.getView(R.id.tvRate);
        if (Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC) == 2) {
            SourceBean bean = ApiConfig.get().getSource(item.sourceKey);
            tvRateValue = bean != null ? bean.getName() : "";
        }
        tvRate.setText(tvRateValue);

        TextView tvNote = helper.getView(R.id.tvNote);
        if (item.note == null || item.note.isEmpty()) {
            tvNote.setVisibility(View.GONE);
        } else {
            tvNote.setText(item.note);
            tvNote.setVisibility(View.VISIBLE);
        }
        helper.setText(R.id.tvName, item.name);
        ImageView ivThumb = helper.getView(R.id.ivThumb);

        int newWidth = ImgUtil.defaultWidth;
        int newHeight = ImgUtil.defaultHeight;
        if (style != null) {
            newWidth = defaultWidth;
            newHeight = (int) (newWidth / style.ratio);
        }

        String pic = item.pic == null ? "" : item.pic.trim();
        // 先按站点声明比例排好版式，图片真实比例的回调随后覆盖，避免两者执行顺序互相覆盖
        applyStyleToImage(ivThumb);
        float imageRatio = 0f;
        if (!TextUtils.isEmpty(pic)) {
            if (ImgUtil.isBase64Image(pic)) {
                // 手机版修复：base64 图片按图片真实比例等比显示，不再沿用布局的 fitXY 拉伸
                Bitmap bitmap = ImgUtil.decodeBase64ToBitmap(pic);
                if (bitmap != null && bitmap.getHeight() > 0) {
                    imageRatio = bitmap.getWidth() / (float) bitmap.getHeight();
                }
                ivThumb.setScaleType(ImageView.ScaleType.FIT_CENTER);
                ivThumb.setImageBitmap(bitmap);
            } else {
                // 手机版修复：按原始比例加载，加载完成后用图片真实比例校正卡片高度
                final String picUrl = pic;
                ivThumb.setTag(R.id.ivThumb, picUrl);
                ImgUtil.loadWithAspect(pic, ivThumb, AutoSizeUtils.mm2px(mContext, 10), AutoSizeUtils.mm2px(mContext, newWidth), AutoSizeUtils.mm2px(mContext, newHeight), item.name, new ImgUtil.AspectListener() {
                    @Override
                    public void onAspect(float ratio) {
                        Object tag = ivThumb.getTag(R.id.ivThumb);
                        // ViewHolder 复用时忽略旧图片的回调
                        if (tag instanceof String && picUrl.equals(tag)) {
                            applyImageRatio(ivThumb, ratio);
                        }
                    }
                });
            }
        } else {
            ivThumb.setImageDrawable(ImgUtil.createTextDrawable(item.name));
        }
        // 手机版适配：横排热播（非网格）时固定 item 根宽，否则 match_parent 根宽会一屏只占一项
        if (!Hawk.get(HawkConfig.HOME_REC_STYLE, true)) {
            ViewGroup.LayoutParams rootLp = helper.itemView.getLayoutParams();
            if (rootLp != null) {
                rootLp.width = AutoSizeUtils.mm2px(mContext, 110);
                helper.itemView.setLayoutParams(rootLp);
            }
        }
        if (imageRatio > 0) {
            applyImageRatio(ivThumb, imageRatio);
        }
    }

    /**
     * 手机版：图片真实比例与站点声明比例差距较大时，让卡片高度跟随图片真实比例，
     * 避免方形图标等被强行裁剪成扁条/竖条；差距较小时保持站点原有版式（等比裁剪）。
     */
    private void applyImageRatio(final ImageView ivThumb, final float imageRatio) {
        if (imageRatio <= 0f || Float.isNaN(imageRatio) || Float.isInfinite(imageRatio)) return;
        final float ratio = Math.min(Math.max(imageRatio, 0.5f), 2.5f);
        if (style != null) {
            float base = Math.min(Math.max(style.ratio, 0.5f), 2.5f);
            if (Math.abs(ratio / base - 1f) < 0.15f) {
                ivThumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                return;
            }
        }
        ivThumb.setScaleType(ImageView.ScaleType.FIT_CENTER);
        final ViewGroup container = (ViewGroup) ivThumb.getParent();
        if (container == null) return;
        container.post(new Runnable() {
            @Override
            public void run() {
                int w = container.getWidth();
                if (w <= 0 && container.getLayoutParams() != null) {
                    w = container.getLayoutParams().width;
                }
                if (w > 0) {
                    ViewGroup.LayoutParams lp = container.getLayoutParams();
                    lp.height = (int) (w / ratio);
                    container.setLayoutParams(lp);
                }
            }
        });
    }

    private void applyStyleToImage(final ImageView ivThumb) {
        if (style != null) {
            final ViewGroup container = (ViewGroup) ivThumb.getParent();
            if (!Hawk.get(HawkConfig.HOME_REC_STYLE, true)) {
                // 横排热播：海报固定列宽，高度按 ratio 换算
                int w = AutoSizeUtils.mm2px(mContext, 110);
                ViewGroup.LayoutParams lp = container.getLayoutParams();
                lp.width = w;
                lp.height = (int) (w / style.ratio);
                container.setLayoutParams(lp);
                return;
            }
            // 网格热播：宽度交给网格列宽（MATCH_PARENT），高度按实测宽度和 ratio 换算，
            // 避免 TV 版按固定 mm 写死宽度在竖屏 360 基准下超出列宽。
            ViewGroup.LayoutParams containerParams = container.getLayoutParams();
            containerParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
            container.setLayoutParams(containerParams);
            container.post(new Runnable() {
                @Override
                public void run() {
                    int w = container.getWidth();
                    if (w > 0) {
                        ViewGroup.LayoutParams lp = container.getLayoutParams();
                        lp.height = (int) (w / style.ratio);
                        container.setLayoutParams(lp);
                    }
                }
            });
        }
    }
}