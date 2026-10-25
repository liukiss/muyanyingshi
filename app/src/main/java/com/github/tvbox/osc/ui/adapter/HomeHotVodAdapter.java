package com.github.tvbox.osc.ui.adapter;

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
        ivThumb.setTag(R.id.ivThumb, pic);
        if (!TextUtils.isEmpty(pic)) {
            if (ImgUtil.isBase64Image(pic)) {
                ivThumb.setImageBitmap(ImgUtil.decodeBase64ToBitmap(pic));
                ivThumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            } else {
                ImgUtil.load(pic, ivThumb, AutoSizeUtils.mm2px(mContext, 10), AutoSizeUtils.mm2px(mContext, newWidth), AutoSizeUtils.mm2px(mContext, newHeight), item.name);
            }
        } else {
            ivThumb.setImageDrawable(ImgUtil.createTextDrawable(item.name));
            ivThumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        }
        // 手机版适配：横排热播（非网格）时固定 item 根宽，否则 match_parent 根宽会一屏只占一项
        if (!Hawk.get(HawkConfig.HOME_REC_STYLE, true)) {
            ViewGroup.LayoutParams rootLp = helper.itemView.getLayoutParams();
            if (rootLp != null) {
                rootLp.width = AutoSizeUtils.mm2px(mContext, 110);
                helper.itemView.setLayoutParams(rootLp);
            }
        }
        applyStyleToImage(ivThumb);
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
            // 网格热播：宽度交给网格列宽（MATCH_PARENT），高度 = 列宽 / 站点声明比例。
            // 同一站点卡片高度统一（行高对齐），标签区已在图片下方独立显示。
            ViewGroup.LayoutParams containerParams = container.getLayoutParams();
            containerParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
            container.setLayoutParams(containerParams);
            ImgUtil.applyContainerRatio(ivThumb, style.ratio);
        }
    }
}