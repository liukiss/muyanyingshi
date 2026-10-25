package com.github.tvbox.osc.ui.adapter;

import android.graphics.Bitmap;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.util.ImgUtil;

import java.util.ArrayList;

import me.jessyan.autosize.utils.AutoSizeUtils;

public class GridAdapter extends BaseQuickAdapter<Movie.Video, BaseViewHolder> {
    private boolean mShowList;
    private int defaultWidth;
    public ImgUtil.Style style;

    public GridAdapter(boolean showList, ImgUtil.Style style) {
        super(showList ? R.layout.item_list : R.layout.item_grid, new ArrayList<>());
        this.mShowList = showList;
        if (style != null) {
            if (style.type.equals("list")) this.mShowList = true;
            this.defaultWidth = ImgUtil.getStyleDefaultWidth(style);
        }
        this.style = style;
    }

    @Override
    protected void convert(BaseViewHolder helper, Movie.Video item) {
        if (this.mShowList) {
            helper.setText(R.id.tvNote, item.note);
            helper.setText(R.id.tvName, item.name);
            ImageView ivThumb = helper.getView(R.id.ivThumb);
            String pic = item.pic == null ? "" : item.pic.trim();
            if (!TextUtils.isEmpty(pic)) {
                if (ImgUtil.isBase64Image(pic)) {
                    ivThumb.setImageBitmap(ImgUtil.decodeBase64ToBitmap(pic));
                } else {
                    ImgUtil.load(pic, ivThumb, AutoSizeUtils.mm2px(mContext, 10), AutoSizeUtils.mm2px(mContext, 240), AutoSizeUtils.mm2px(mContext, 336), item.name);
                }
            } else {
                ivThumb.setImageDrawable(ImgUtil.createTextDrawable(item.name));
            }
            return;
        }

        TextView tvYear = helper.getView(R.id.tvYear);
        if (item.year <= 0) {
            tvYear.setVisibility(View.GONE);
        } else {
            tvYear.setText(String.valueOf(item.year));
            tvYear.setVisibility(View.VISIBLE);
        }
        TextView tvLang = helper.getView(R.id.tvLang);
        tvLang.setVisibility(View.GONE);
        TextView tvArea = helper.getView(R.id.tvArea);
        tvArea.setVisibility(View.GONE);
        if (TextUtils.isEmpty(item.note)) {
            helper.setVisible(R.id.tvNote, false);
        } else {
            helper.setVisible(R.id.tvNote, true);
            helper.setText(R.id.tvNote, item.note);
        }
        helper.setText(R.id.tvName, item.name);
        helper.setText(R.id.tvActor, item.actor);
        ImageView ivThumb = helper.getView(R.id.ivThumb);
        int newWidth = ImgUtil.defaultWidth;
        int newHeight = ImgUtil.defaultHeight;
        if (style != null) {
            newWidth = defaultWidth;
            newHeight = (int) (newWidth / style.ratio);
        }
        String pic = item.pic == null ? "" : item.pic.trim();
        ivThumb.setTag(R.id.ivThumb, pic);
        Float imageRatio = style == null ? null : ImgUtil.getCachedRatio(pic);
        if (!TextUtils.isEmpty(pic)) {
            if (ImgUtil.isBase64Image(pic)) {
                Bitmap bitmap = ImgUtil.decodeBase64ToBitmap(pic);
                ivThumb.setImageBitmap(bitmap);
                if (style != null && bitmap != null && bitmap.getHeight() > 0) {
                    // 站点声明了比例：base64 图片按自身比例显示，避免被 fitXY 拉伸
                    ivThumb.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    imageRatio = bitmap.getWidth() / (float) bitmap.getHeight();
                }
            } else if (style != null) {
                // 站点声明了比例：按封面真实比例自适应卡片高度，图片完整填满、不裁剪不拉伸
                ImgUtil.loadAdaptive(pic, ivThumb, AutoSizeUtils.mm2px(mContext, 10), AutoSizeUtils.mm2px(mContext, newWidth), AutoSizeUtils.mm2px(mContext, newHeight), item.name, new ImgUtil.RatioListener() {
                    @Override
                    public void onRatio(float ratio) {
                        if (pic.equals(ivThumb.getTag(R.id.ivThumb))) {
                            ImgUtil.applyContainerRatio(ivThumb, ratio);
                        }
                    }
                });
            } else {
                ImgUtil.load(pic, ivThumb, AutoSizeUtils.mm2px(mContext, 10), AutoSizeUtils.mm2px(mContext, newWidth), AutoSizeUtils.mm2px(mContext, newHeight), item.name);
            }
        } else {
            ivThumb.setImageDrawable(ImgUtil.createTextDrawable(item.name));
            if (style != null) ivThumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        }
        applyStyleToImage(ivThumb, imageRatio);
    }

    private void applyStyleToImage(final ImageView ivThumb, Float imageRatio) {
        if (style != null) {
            // 手机版适配：宽度交给网格列宽（MATCH_PARENT），高度按图片真实比例（未知时用站点比例）换算，
            // 避免 TV 版按固定 mm 写死宽度在竖屏 360 基准下超出列宽。
            final ViewGroup container = (ViewGroup) ivThumb.getParent();
            ViewGroup.LayoutParams containerParams = container.getLayoutParams();
            containerParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
            container.setLayoutParams(containerParams);
            ImgUtil.applyContainerRatio(ivThumb, imageRatio != null ? imageRatio : style.ratio);
        }
    }
}
