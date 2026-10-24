package com.github.tvbox.osc.ui.activity;

import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.LiveChannelGroup;
import com.github.tvbox.osc.bean.LiveChannelItem;
import com.github.tvbox.osc.bean.LivePlayerManager;
import com.github.tvbox.osc.bean.LiveSettingGroup;
import com.github.tvbox.osc.bean.LiveSettingItem;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.ui.dialog.SelectDialog;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.HistoryHelper;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import xyz.doikki.videoplayer.exo.ExoMediaSourceHelper;
import xyz.doikki.videoplayer.player.VideoView;

/**
 * 手机版直播控制页：上方为按原始比例展示的直播播放器，下方为直播控制按钮。
 * 点击上方播放器进入 LivePlayActivity 全屏直播。
 */
public class LiveControlActivity extends BaseActivity {

    private interface OnOptionSelected {
        void onSelected(int position);
    }

    private VideoView<xyz.doikki.videoplayer.player.AbstractPlayer> mVideoView;
    private ProgressBar pbLiveLoading;
    private TextView tvChannelName;
    private TextView tvChannelMeta;
    private TextView tvSourceChip;
    private TextView tvPlayerChannel;

    private LiveChannelItem currentChannelItem;
    private final LivePlayerManager livePlayerManager = new LivePlayerManager();
    private boolean liveConfigLoading = false;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_live_control;
    }

    @Override
    protected void init() {
        setLoadSir(findViewById(R.id.live_control_root));
        mVideoView = findViewById(R.id.mVideoView);
        pbLiveLoading = findViewById(R.id.pbLiveLoading);
        tvChannelName = findViewById(R.id.tvChannelName);
        tvChannelMeta = findViewById(R.id.tvChannelMeta);
        tvSourceChip = findViewById(R.id.tvSourceChip);
        tvPlayerChannel = findViewById(R.id.tvPlayerChannel);

        livePlayerManager.init(mVideoView);
        mVideoView.setScreenScaleType(livePlayerManager.getLivePlayerScale());
        mVideoView.addOnStateChangeListener(new VideoView.SimpleOnStateChangeListener() {
            @Override
            public void onPlayStateChanged(int playState) {
                switch (playState) {
                    case VideoView.STATE_PREPARING:
                    case VideoView.STATE_BUFFERING:
                        showPlayerLoading(true);
                        break;
                    case VideoView.STATE_PREPARED:
                    case VideoView.STATE_BUFFERED:
                    case VideoView.STATE_PLAYING:
                    case VideoView.STATE_PAUSED:
                    case VideoView.STATE_PLAYBACK_COMPLETED:
                    case VideoView.STATE_ERROR:
                    case VideoView.STATE_IDLE:
                        showPlayerLoading(false);
                        break;
                }
            }
        });

        findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        // 点击上方播放器区域进入全屏直播
        findViewById(R.id.playerClickArea).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openFullscreenLive();
            }
        });

        tvSourceChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSourceDialog();
            }
        });
        findViewById(R.id.tvLineSelectChip).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSourceDialog();
            }
        });
        findViewById(R.id.tvScaleChip).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showScaleDialog();
            }
        });
        findViewById(R.id.tvDecoderChip).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDecoderDialog();
            }
        });
        findViewById(R.id.tvTimeoutChip).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTimeoutDialog();
            }
        });
        findViewById(R.id.tvPreferenceChip).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPreferenceDialog();
            }
        });
        findViewById(R.id.tvMultiSourceChip).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMultiSourceDialog();
            }
        });
        findViewById(R.id.tvConfigChip).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showConfigDialog();
            }
        });

        showPlayerLoading(true);
        loadLiveChannels();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mVideoView == null || currentChannelItem == null) return;
        syncChannelFromHawk();
        Hawk.put(HawkConfig.PLAYER_IS_LIVE, true);
        int playState = mVideoView.getCurrentPlayState();
        if (playState == VideoView.STATE_IDLE || playState == VideoView.STATE_ERROR) {
            playCurrentChannel();
        } else {
            mVideoView.start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mVideoView != null && mVideoView.isPlaying()) {
            mVideoView.pause();
        }
    }

    @Override
    protected void onDestroy() {
        Hawk.put(HawkConfig.PLAYER_IS_LIVE, false);
        if (mVideoView != null) {
            mVideoView.release();
        }
        super.onDestroy();
    }

    private void openFullscreenLive() {
        if (mVideoView != null) {
            mVideoView.pause();
        }
        jumpActivity(LivePlayActivity.class);
    }

    private void showPlayerLoading(boolean show) {
        if (pbLiveLoading != null) {
            pbLiveLoading.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void loadLiveChannels() {
        if (ApiConfig.get().shouldReloadLiveConfig()) {
            if (liveConfigLoading) return;
            liveConfigLoading = true;
            showLoading();
            ApiConfig.get().loadLiveConfig(true, new ApiConfig.LoadConfigCallback() {
                @Override
                public void success() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            liveConfigLoading = false;
                            if (isFinishing()) return;
                            showSuccess();
                            setupChannelAndPlay();
                        }
                    });
                }

                @Override
                public void error(String msg) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            liveConfigLoading = false;
                            if (isFinishing()) return;
                            showEmpty();
                            Toast.makeText(LiveControlActivity.this, msg, Toast.LENGTH_SHORT).show();
                        }
                    });
                }

                @Override
                public void notice(String msg) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!isFinishing()) {
                                Toast.makeText(LiveControlActivity.this, msg, Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                }
            });
        } else {
            setupChannelAndPlay();
        }
    }

    private void setupChannelAndPlay() {
        initSettingSelections();
        String lastChannelName = Hawk.get(HawkConfig.LIVE_CHANNEL, "");
        boolean found = !TextUtils.isEmpty(lastChannelName) && findChannelByName(lastChannelName);
        if (!found) {
            found = selectDefaultChannel();
        }
        if (!found) {
            showEmpty();
            return;
        }
        playCurrentChannel();
    }

    private boolean findChannelByName(String channelName) {
        List<LiveChannelGroup> groups = ApiConfig.get().getChannelGroupList();
        if (groups == null) return false;
        for (LiveChannelGroup group : groups) {
            if (group == null || !TextUtils.isEmpty(group.getGroupPassword())) continue;
            ArrayList<LiveChannelItem> channels = group.getLiveChannels();
            if (channels == null) continue;
            for (LiveChannelItem item : channels) {
                if (item != null && channelName.equals(item.getChannelName())) {
                    currentChannelItem = item;
                    return true;
                }
            }
        }
        return false;
    }

    private boolean selectDefaultChannel() {
        List<LiveChannelGroup> groups = ApiConfig.get().getChannelGroupList();
        if (groups == null) return false;
        LiveChannelGroup fallbackGroup = null;
        for (LiveChannelGroup group : groups) {
            if (group == null) continue;
            ArrayList<LiveChannelItem> channels = group.getLiveChannels();
            if (channels == null || channels.isEmpty()) continue;
            if (fallbackGroup == null) {
                fallbackGroup = group;
            }
            if (!TextUtils.isEmpty(group.getGroupPassword())) continue;
            for (LiveChannelItem item : channels) {
                if (item != null && !TextUtils.isEmpty(item.getChannelName())
                        && item.getChannelName().contains("CCTV1")) {
                    currentChannelItem = item;
                    return true;
                }
            }
            currentChannelItem = channels.get(0);
            return true;
        }
        if (fallbackGroup != null && fallbackGroup.getLiveChannels() != null
                && !fallbackGroup.getLiveChannels().isEmpty()) {
            currentChannelItem = fallbackGroup.getLiveChannels().get(0);
            return true;
        }
        return false;
    }

    private void playCurrentChannel() {
        if (currentChannelItem == null || mVideoView == null) return;
        ArrayList<String> urls = currentChannelItem.getChannelUrls();
        if (urls == null || urls.isEmpty()) {
            showPlayerLoading(false);
            Toast.makeText(this, "当前频道暂无可用线路", Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentChannelItem.getSourceIndex() < 0 || currentChannelItem.getSourceIndex() >= urls.size()) {
            currentChannelItem.setSourceIndex(0);
        }
        Hawk.put(HawkConfig.LIVE_CHANNEL, currentChannelItem.getChannelName());
        Hawk.put(HawkConfig.PLAYER_IS_LIVE, true);
        mVideoView.release();
        mVideoView.setUrl(currentChannelItem.getUrl(), liveChannelHeader());
        mVideoView.start();
        updateChannelViews();
    }

    private void updateChannelViews() {
        if (currentChannelItem == null) return;
        String channelName = currentChannelItem.getChannelName();
        String sourceName = currentSourceName();
        tvChannelName.setText(channelName);
        tvPlayerChannel.setText(channelName);
        tvChannelMeta.setText("直播 · " + sourceName);
        tvSourceChip.setText(sourceName);
        tvSourceChip.setSelected(true);
    }

    private String currentSourceName() {
        if (currentChannelItem == null) return "";
        ArrayList<String> names = currentChannelItem.getChannelSourceNames();
        int sourceIndex = currentChannelItem.getSourceIndex();
        if (names != null && sourceIndex >= 0 && sourceIndex < names.size()) {
            return names.get(sourceIndex);
        }
        return "源" + (sourceIndex + 1);
    }

    private void syncChannelFromHawk() {
        String channelName = Hawk.get(HawkConfig.LIVE_CHANNEL, "");
        if (TextUtils.isEmpty(channelName)) return;
        if (currentChannelItem != null && channelName.equals(currentChannelItem.getChannelName())) return;
        if (findChannelByName(channelName)) {
            playCurrentChannel();
        }
    }

    private HashMap<String, String> liveChannelHeader() {
        if (currentChannelItem == null) return liveWebHeader();
        HashMap<String, String> header = new HashMap<>();
        HashMap<String, String> liveHeader = liveWebHeader();
        if (liveHeader != null) header.putAll(liveHeader);
        if (currentChannelItem.getHeaders() != null) header.putAll(currentChannelItem.getHeaders());
        if (!currentChannelItem.getChannelFormat().isEmpty()) {
            header.put(ExoMediaSourceHelper.HEADER_FORMAT, currentChannelItem.getChannelFormat());
        }
        if (header.isEmpty()) return null;
        return header;
    }

    private HashMap<String, String> liveWebHeader() {
        return Hawk.get(HawkConfig.LIVE_WEB_HEADER);
    }

    private void initSettingSelections() {
        List<LiveSettingGroup> groups = ApiConfig.get().getLiveSettingGroupList();
        if (groups == null || groups.size() < 7) return;
        refreshGroupSelection(1, livePlayerManager.getLivePlayerScale());
        refreshGroupSelection(2, livePlayerManager.getLivePlayerType());
        refreshGroupSelection(3, Hawk.get(HawkConfig.LIVE_CONNECT_TIMEOUT, 1));
        updatePreferenceSelection();
        refreshGroupSelection(5, ApiConfig.getLiveGroupIndex());
        refreshGroupSelection(6, getCurrentLiveApiHistoryIndex());
    }

    private LiveSettingGroup findSettingGroup(int groupIndex) {
        List<LiveSettingGroup> groups = ApiConfig.get().getLiveSettingGroupList();
        if (groups == null) return null;
        for (LiveSettingGroup group : groups) {
            if (group != null && group.getGroupIndex() == groupIndex) return group;
        }
        return null;
    }

    private void refreshGroupSelection(int groupIndex, int itemIndex) {
        LiveSettingGroup group = findSettingGroup(groupIndex);
        if (group == null || group.getLiveSettingItems() == null) return;
        for (LiveSettingItem item : group.getLiveSettingItems()) {
            item.setItemSelected(item.getItemIndex() == itemIndex);
        }
    }

    private void updatePreferenceSelection() {
        LiveSettingGroup group = findSettingGroup(4);
        if (group == null || group.getLiveSettingItems() == null) return;
        setItemSelection(group.getLiveSettingItems(), 0, Hawk.get(HawkConfig.LIVE_SHOW_TIME, false));
        setItemSelection(group.getLiveSettingItems(), 1, Hawk.get(HawkConfig.LIVE_SHOW_NET_SPEED, false));
        setItemSelection(group.getLiveSettingItems(), 2, Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE, false));
        setItemSelection(group.getLiveSettingItems(), 3, Hawk.get(HawkConfig.LIVE_CROSS_GROUP, false));
    }

    private void setItemSelection(List<LiveSettingItem> items, int itemIndex, boolean selected) {
        for (LiveSettingItem item : items) {
            if (item.getItemIndex() == itemIndex) {
                item.setItemSelected(selected);
            }
        }
    }

    private int getCurrentLiveApiHistoryIndex() {
        ArrayList<String> history = Hawk.get(HawkConfig.LIVE_API_HISTORY, new ArrayList<String>());
        if (history == null || history.isEmpty()) return 0;
        String current = Hawk.get(HawkConfig.LIVE_API_URL, "");
        int index = history.indexOf(current);
        return index >= 0 ? index : 0;
    }

    private void showSelectDialog(String title, ArrayList<String> items, int select, final OnOptionSelected callback) {
        if (items == null || items.isEmpty()) {
            Toast.makeText(this, "暂无可选项", Toast.LENGTH_SHORT).show();
            return;
        }
        if (select < 0 || select >= items.size()) {
            select = 0;
        }
        final SelectDialog<String> dialog = new SelectDialog<>(this);
        dialog.setTip(title);
        dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<String>() {
            @Override
            public void click(String value, int pos) {
                dialog.cancel();
                callback.onSelected(pos);
            }

            @Override
            public String getDisplay(String val) {
                return val;
            }
        }, SelectDialogAdapter.stringDiff, items, select);
        dialog.show();
    }

    private ArrayList<String> settingItemNames(LiveSettingGroup group) {
        ArrayList<String> items = new ArrayList<>();
        if (group == null || group.getLiveSettingItems() == null) return items;
        for (LiveSettingItem item : group.getLiveSettingItems()) {
            items.add(item.getItemName());
        }
        return items;
    }

    private int indexOfItemIndex(LiveSettingGroup group, int itemIndex) {
        if (group == null || group.getLiveSettingItems() == null) return 0;
        List<LiveSettingItem> items = group.getLiveSettingItems();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getItemIndex() == itemIndex) return i;
        }
        return 0;
    }

    private int getItemIndexAt(LiveSettingGroup group, int position) {
        if (group == null || group.getLiveSettingItems() == null) return position;
        List<LiveSettingItem> items = group.getLiveSettingItems();
        if (position >= 0 && position < items.size()) {
            return items.get(position).getItemIndex();
        }
        return position;
    }

    private void showSourceDialog() {
        if (currentChannelItem == null) {
            Toast.makeText(this, "频道加载中，请稍候", Toast.LENGTH_SHORT).show();
            return;
        }
        ArrayList<String> names = currentChannelItem.getChannelSourceNames();
        ArrayList<String> urls = currentChannelItem.getChannelUrls();
        int count = urls != null ? urls.size() : (names == null ? 0 : names.size());
        if (count <= 0) {
            Toast.makeText(this, "当前频道暂无线路", Toast.LENGTH_SHORT).show();
            return;
        }
        ArrayList<String> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (names != null && i < names.size()) {
                items.add(names.get(i));
            } else {
                items.add("源" + (i + 1));
            }
        }
        showSelectDialog("线路选择", items, currentChannelItem.getSourceIndex(), new OnOptionSelected() {
            @Override
            public void onSelected(int position) {
                currentChannelItem.setSourceIndex(position);
                playCurrentChannel();
                refreshGroupSelection(0, position);
            }
        });
    }

    private void showScaleDialog() {
        final LiveSettingGroup group = findSettingGroup(1);
        if (group == null) return;
        showSelectDialog("画面比例", settingItemNames(group),
                indexOfItemIndex(group, livePlayerManager.getLivePlayerScale()), new OnOptionSelected() {
                    @Override
                    public void onSelected(int position) {
                        int itemIndex = getItemIndexAt(group, position);
                        livePlayerManager.changeLivePlayerScale(mVideoView, itemIndex);
                        refreshGroupSelection(1, itemIndex);
                    }
                });
    }

    private void showDecoderDialog() {
        final LiveSettingGroup group = findSettingGroup(2);
        if (group == null) return;
        showSelectDialog("播放解码", settingItemNames(group),
                indexOfItemIndex(group, livePlayerManager.getLivePlayerType()), new OnOptionSelected() {
                    @Override
                    public void onSelected(int position) {
                        int itemIndex = getItemIndexAt(group, position);
                        mVideoView.release();
                        livePlayerManager.changeLivePlayerType(mVideoView, itemIndex);
                        if (currentChannelItem != null) {
                            mVideoView.setUrl(currentChannelItem.getUrl(), liveChannelHeader());
                            mVideoView.start();
                        }
                        refreshGroupSelection(2, itemIndex);
                    }
                });
    }

    private void showTimeoutDialog() {
        final LiveSettingGroup group = findSettingGroup(3);
        if (group == null) return;
        showSelectDialog("超时换源", settingItemNames(group),
                indexOfItemIndex(group, Hawk.get(HawkConfig.LIVE_CONNECT_TIMEOUT, 1)), new OnOptionSelected() {
                    @Override
                    public void onSelected(int position) {
                        int itemIndex = getItemIndexAt(group, position);
                        Hawk.put(HawkConfig.LIVE_CONNECT_TIMEOUT, itemIndex);
                        refreshGroupSelection(3, itemIndex);
                    }
                });
    }

    private void showPreferenceDialog() {
        final String[] names = new String[]{"显示时间", "显示网速", "换台反转", "跨选分类"};
        final boolean[] checked = new boolean[]{
                Hawk.get(HawkConfig.LIVE_SHOW_TIME, false),
                Hawk.get(HawkConfig.LIVE_SHOW_NET_SPEED, false),
                Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE, false),
                Hawk.get(HawkConfig.LIVE_CROSS_GROUP, false)
        };
        new AlertDialog.Builder(this)
                .setTitle("偏好设置")
                .setMultiChoiceItems(names, checked, (dialog, which, isChecked) -> {
                    switch (which) {
                        case 0:
                            Hawk.put(HawkConfig.LIVE_SHOW_TIME, isChecked);
                            break;
                        case 1:
                            Hawk.put(HawkConfig.LIVE_SHOW_NET_SPEED, isChecked);
                            break;
                        case 2:
                            Hawk.put(HawkConfig.LIVE_CHANNEL_REVERSE, isChecked);
                            break;
                        case 3:
                            Hawk.put(HawkConfig.LIVE_CROSS_GROUP, isChecked);
                            break;
                    }
                    updatePreferenceSelection();
                })
                .setPositiveButton("完成", null)
                .show();
    }

    private void showMultiSourceDialog() {
        final LiveSettingGroup group = findSettingGroup(5);
        if (group == null) return;
        showSelectDialog("多源切换", settingItemNames(group),
                indexOfItemIndex(group, ApiConfig.getLiveGroupIndex()), new OnOptionSelected() {
                    @Override
                    public void onSelected(int position) {
                        switchLiveApiGroup(getItemIndexAt(group, position));
                    }
                });
    }

    private void showConfigDialog() {
        final LiveSettingGroup group = findSettingGroup(6);
        if (group == null) return;
        showSelectDialog("配置切换", settingItemNames(group),
                indexOfItemIndex(group, getCurrentLiveApiHistoryIndex()), new OnOptionSelected() {
                    @Override
                    public void onSelected(int position) {
                        switchLiveConfig(getItemIndexAt(group, position));
                    }
                });
    }

    private void switchLiveApiGroup(int itemIndex) {
        if (itemIndex == ApiConfig.getLiveGroupIndex()) return;
        JsonArray liveGroups = Hawk.get(HawkConfig.LIVE_GROUP_LIST, new JsonArray());
        if (liveGroups == null || itemIndex < 0 || itemIndex >= liveGroups.size()) return;
        String channelName = currentChannelItem == null ? "" : currentChannelItem.getChannelName();
        int sourceIndex = currentChannelItem == null ? -1 : currentChannelItem.getSourceIndex();
        JsonObject groupObject = liveGroups.get(itemIndex).getAsJsonObject();
        ApiConfig.setLiveGroupIndex(itemIndex);
        refreshGroupSelection(5, itemIndex);
        ApiConfig.get().loadLiveApi(groupObject);
        if (ApiConfig.get().getChannelGroupList().isEmpty()) {
            currentChannelItem = null;
            if (mVideoView != null) mVideoView.release();
            showEmpty();
            return;
        }
        restoreChannelAndPlay(channelName, sourceIndex);
    }

    private void switchLiveConfig(int position) {
        ArrayList<String> history = Hawk.get(HawkConfig.LIVE_API_HISTORY, new ArrayList<String>());
        if (history == null || position < 0 || position >= history.size()) return;
        final String value = history.get(position);
        String oldValue = Hawk.get(HawkConfig.LIVE_API_URL, "");
        if (value.equals(oldValue)) return;
        final String channelName = currentChannelItem == null ? "" : currentChannelItem.getChannelName();
        final int sourceIndex = currentChannelItem == null ? -1 : currentChannelItem.getSourceIndex();
        Hawk.put(HawkConfig.LIVE_API_URL, value);
        HistoryHelper.setLiveApiHistory(value);
        ApiConfig.get().refreshLiveApiHistoryItems();
        refreshGroupSelection(6, position);
        showLoading();
        ApiConfig.get().loadLiveConfig(false, new ApiConfig.LoadConfigCallback() {
            @Override
            public void success() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) return;
                        showSuccess();
                        restoreChannelAndPlay(channelName, sourceIndex);
                    }
                });
            }

            @Override
            public void error(String msg) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) return;
                        showSuccess();
                        Toast.makeText(LiveControlActivity.this, msg, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void notice(String msg) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!isFinishing()) {
                            Toast.makeText(LiveControlActivity.this, msg, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        });
    }

    private void restoreChannelAndPlay(String channelName, int sourceIndex) {
        initSettingSelections();
        boolean found = !TextUtils.isEmpty(channelName) && findChannelByName(channelName);
        if (!found) {
            found = selectDefaultChannel();
        }
        if (!found) {
            currentChannelItem = null;
            if (mVideoView != null) mVideoView.release();
            showEmpty();
            return;
        }
        if (sourceIndex >= 0 && currentChannelItem.getSourceNum() > 0) {
            currentChannelItem.setSourceIndex(Math.min(sourceIndex, currentChannelItem.getSourceNum() - 1));
        }
        playCurrentChannel();
    }
}
