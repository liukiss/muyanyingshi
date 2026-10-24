package com.github.tvbox.osc.ui.activity;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.github.catvod.crawler.Spider;
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
import com.github.tvbox.osc.util.live.TxtSubscribe;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
    private TextView tvPlayerChannel;

    private LiveChannelItem currentChannelItem;
    private final LivePlayerManager livePlayerManager = new LivePlayerManager();
    private boolean liveConfigLoading = false;
    private boolean liveProxyLoading = false;
    private String pendingChannelName;
    private int pendingSourceIndex = -1;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_live_control;
    }

    @Override
    protected void init() {
        setLoadSir(findViewById(R.id.live_control_root));
        mVideoView = findViewById(R.id.mVideoView);
        pbLiveLoading = findViewById(R.id.pbLiveLoading);
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

        findViewById(R.id.tvBackChip).setOnClickListener(new View.OnClickListener() {
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
        findViewById(R.id.tvFullscreenChip).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openFullscreenLive();
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
        List<LiveChannelGroup> cachedGroups = ApiConfig.get().getChannelGroupList();
        if (ApiConfig.get().shouldReloadLiveConfig() || cachedGroups == null || cachedGroups.isEmpty()) {
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
            // 使用缓存的直播数据时也必须切回成功状态，否则 LoadSir 不会把页面内容挂回视图树，
            // 会出现页面空白但直播声音正常播放的现象
            showSuccess();
            setupChannelAndPlay();
        }
    }

    private void setupChannelAndPlay() {
        initSettingSelections();
        loadChannelsOrPlay();
    }

    private void loadChannelsOrPlay() {
        List<LiveChannelGroup> groups = ApiConfig.get().getChannelGroupList();
        if (groups == null || groups.isEmpty()) {
            showEmpty();
            return;
        }
        // 配置中的直播地址为本地代理时，需要先拉取代理返回的真实频道列表
        if (groups.size() == 1 && groups.get(0) != null && groups.get(0).getGroupName() != null
                && groups.get(0).getGroupName().startsWith("http://127.0.0.1")) {
            loadProxyLives(groups.get(0).getGroupName());
            return;
        }
        playLastOrDefaultChannel();
    }

    private void playLastOrDefaultChannel() {
        String channelName = pendingChannelName != null ? pendingChannelName : Hawk.get(HawkConfig.LIVE_CHANNEL, "");
        int sourceIndex = pendingSourceIndex;
        pendingChannelName = null;
        pendingSourceIndex = -1;
        boolean found = !TextUtils.isEmpty(channelName) && findChannelByName(channelName);
        if (!found) {
            found = selectDefaultChannel();
        }
        if (!found) {
            showEmpty();
            return;
        }
        if (sourceIndex >= 0 && currentChannelItem.getSourceNum() > 0) {
            currentChannelItem.setSourceIndex(Math.min(sourceIndex, currentChannelItem.getSourceNum() - 1));
        }
        playCurrentChannel();
    }

    private void loadProxyLives(String proxyUrl) {
        if (liveProxyLoading) return;
        liveProxyLoading = true;
        showPlayerLoading(true);
        String url = proxyUrl;
        try {
            Uri parsedUrl = Uri.parse(proxyUrl);
            String ext = parsedUrl.getQueryParameter("ext");
            if (!TextUtils.isEmpty(ext)) {
                url = new String(Base64.decode(ext, Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP), "UTF-8");
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        if (!isValidLiveProxyUrl(url)) {
            liveProxyLoading = false;
            showEmpty();
            return;
        }
        final String liveUrl = url;
        if (liveUrl.contains(".py") || liveUrl.contains(".js")) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    ExecutorService executor = Executors.newSingleThreadExecutor();
                    Future<String> future = executor.submit(new Callable<String>() {
                        @Override
                        public String call() {
                            Spider spider = ApiConfig.get().getLiveCSP(liveUrl);
                            return spider.liveContent(liveUrl);
                        }
                    });
                    String content = null;
                    try {
                        content = future.get(ApiConfig.get().getLiveConnectTimeoutSeconds(), TimeUnit.SECONDS);
                    } catch (TimeoutException e) {
                        future.cancel(true);
                    } catch (InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                    } finally {
                        executor.shutdown();
                    }
                    applyProxyLives(content);
                }
            }).start();
        } else {
            OkGo.<String>get(liveUrl).execute(new AbsCallback<String>() {
                @Override
                public String convertResponse(okhttp3.Response response) throws Throwable {
                    if (response.body() == null) return "";
                    return response.body().string();
                }

                @Override
                public void onSuccess(Response<String> response) {
                    applyProxyLives(response.body());
                }

                @Override
                public void onError(Response<String> response) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            liveProxyLoading = false;
                            if (!isFinishing()) {
                                showEmpty();
                                Toast.makeText(LiveControlActivity.this, "直播列表加载失败", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                }
            });
        }
    }

    private void applyProxyLives(String content) {
        if (content == null || content.trim().isEmpty()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    liveProxyLoading = false;
                    if (!isFinishing()) showEmpty();
                }
            });
            return;
        }
        try {
            JsonArray livesArray = TxtSubscribe.parseToJsonArray(content);
            ApiConfig.get().loadLives(livesArray);
        } catch (Throwable th) {
            th.printStackTrace();
        }
        List<LiveChannelGroup> groups = ApiConfig.get().getChannelGroupList();
        if (groups == null || groups.isEmpty()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    liveProxyLoading = false;
                    if (!isFinishing()) showEmpty();
                }
            });
            return;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                liveProxyLoading = false;
                if (isFinishing()) return;
                showSuccess();
                initSettingSelections();
                playLastOrDefaultChannel();
            }
        });
    }

    private boolean isValidLiveProxyUrl(String url) {
        if (TextUtils.isEmpty(url)) return false;
        String lowerUrl = url.trim().toLowerCase(Locale.US);
        return lowerUrl.startsWith("http://")
                || lowerUrl.startsWith("https://")
                || lowerUrl.startsWith("rtsp://")
                || lowerUrl.startsWith("rtmp://")
                || lowerUrl.startsWith("rtp://");
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
        tvPlayerChannel.setText(currentChannelItem.getChannelName());
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
        pendingChannelName = channelName;
        pendingSourceIndex = sourceIndex;
        showPlayerLoading(true);
        loadChannelsOrPlay();
    }
}
