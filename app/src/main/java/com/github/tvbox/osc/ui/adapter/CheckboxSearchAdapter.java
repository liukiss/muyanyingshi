package com.github.tvbox.osc.ui.adapter;


import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;


import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.SearchHelper;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import me.jessyan.autosize.utils.AutoSizeUtils;

public class CheckboxSearchAdapter extends ListAdapter<SourceBean, CheckboxSearchAdapter.ViewHolder> {

    public CheckboxSearchAdapter(DiffUtil.ItemCallback<SourceBean> diffCallback) {
        super(diffCallback);
    }

    @Override
    public ViewHolder onCreateViewHolder(@NonNull @NotNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_dialog_checkbox_search, parent, false));
    }

    private void setCheckedSource(HashMap<String, String> checkedSources) {
        mCheckedSources = checkedSources;
    }

    private ArrayList<SourceBean> data = new ArrayList<>();
    public HashMap<String, String> mCheckedSources = new HashMap<>();

    @SuppressLint("NotifyDataSetChanged")
    public void setData(List<SourceBean> newData, HashMap<String, String> checkedSources) {
        data.clear();
        data.addAll(newData);
        setCheckedSource(checkedSources);
        notifyDataSetChanged();
    }

    public void setMCheckedSources() {
//        LOG.i(data.size()+"size----size"+mCheckedSources.size());
        SearchHelper.putCheckedSources(mCheckedSources,data.size()==mCheckedSources.size());
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        int pos = holder.getAdapterPosition();
        SourceBean sourceBean = data.get(pos);
        holder.oneSearchSource.setText(sourceBean.getName());
        holder.oneSearchSource.setOnCheckedChangeListener(null);
        if (mCheckedSources != null) {
            holder.oneSearchSource.setChecked(mCheckedSources.containsKey(sourceBean.getKey()));
        }
        // 手机版：复选框图标按当前密度重算尺寸（系统默认图标在 mm 设计尺寸下偏大），
        // 并按选中状态刷新，避免横竖屏切换后沿用旧密度膨胀出来的尺寸
        Context context = holder.itemView.getContext();
        Drawable icon = ContextCompat.getDrawable(context, R.drawable.selector_source_check);
        if (icon != null) {
            int size = AutoSizeUtils.mm2px(context, 15f);
            icon.setBounds(0, 0, size, size);
            holder.oneSearchSource.setCompoundDrawables(icon, null, null, null);
            holder.oneSearchSource.setCompoundDrawablePadding(AutoSizeUtils.mm2px(context, 5f));
            icon.setState(holder.oneSearchSource.getDrawableState());
        }
        holder.oneSearchSource.setTag(sourceBean);
        holder.oneSearchSource.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    mCheckedSources.put(sourceBean.getKey(), "1");
                } else {
                    mCheckedSources.remove(sourceBean.getKey());
                }
                notifyItemChanged(pos);
            }
        });

    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public CheckBox oneSearchSource;

        public ViewHolder(View view) {
            super(view);
            oneSearchSource = (CheckBox) view.findViewById(R.id.oneSearchSource);
        }
    }

}
