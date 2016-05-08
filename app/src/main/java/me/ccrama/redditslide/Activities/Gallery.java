package me.ccrama.redditslide.Activities;

import android.content.res.Configuration;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v7.widget.RecyclerView;

import net.dean.jraw.models.Submission;

import java.util.List;

import me.ccrama.redditslide.Adapters.GalleryView;
import me.ccrama.redditslide.Adapters.MultiredditPosts;
import me.ccrama.redditslide.Adapters.SubmissionDisplay;
import me.ccrama.redditslide.Adapters.SubredditPosts;
import me.ccrama.redditslide.Authentication;
import me.ccrama.redditslide.ContentType;
import me.ccrama.redditslide.Fragments.AlbumFull;
import me.ccrama.redditslide.Fragments.MediaFragment;
import me.ccrama.redditslide.Fragments.SelftextFull;
import me.ccrama.redditslide.Fragments.TitleFull;
import me.ccrama.redditslide.LastComments;
import me.ccrama.redditslide.OfflineSubreddit;
import me.ccrama.redditslide.PostLoader;
import me.ccrama.redditslide.R;
import me.ccrama.redditslide.Reddit;
import me.ccrama.redditslide.SettingValues;
import me.ccrama.redditslide.Views.CatchStaggeredGridLayoutManager;

/**
 * Created by ccrama on 9/17/2015.
 */
public class Gallery extends FullScreenActivity implements SubmissionDisplay {
    public static final String EXTRA_PROFILE = "profile";
    public static final String EXTRA_PAGE = "page";
    public static final String EXTRA_SUBREDDIT = "subreddit";
    public static final String EXTRA_MULTIREDDIT = "multireddit";
    public PostLoader subredditPosts;
    public String subreddit;

    @Override
    public void onCreate(Bundle savedInstance) {
        overrideSwipeFromAnywhere();
        subreddit = getIntent().getExtras().getString(EXTRA_SUBREDDIT);
        String multireddit = getIntent().getExtras().getString(EXTRA_MULTIREDDIT);
        String profile = getIntent().getExtras().getString(EXTRA_PROFILE, "");
        if (multireddit != null) {
            subredditPosts = new MultiredditPosts(multireddit, profile);
        } else {
            subredditPosts = new SubredditPosts(subreddit, Gallery.this);
        }
        subreddit = multireddit == null ? subreddit : ("multi" + multireddit);
        applyDarkColorTheme(subreddit);
        super.onCreate(savedInstance);
        setContentView(R.layout.gallery);

        long offline = getIntent().getLongExtra("offline",0L);

        OfflineSubreddit submissions = OfflineSubreddit.getSubreddit(subreddit, offline, !Authentication.didOnline, this);

        subredditPosts.getPosts().addAll(submissions.submissions);

        rv = (RecyclerView) findViewById(R.id.content_view);
        submissionsPager = new OverviewPagerAdapter(getSupportFragmentManager());

        GalleryView recyclerAdapter = new GalleryView(this, subredditPosts,subreddit);
        RecyclerView.LayoutManager layoutManager = createLayoutManager(getNumColumns(getResources().getConfiguration().orientation));
        rv.setLayoutManager(layoutManager);
        rv.setAdapter(recyclerAdapter);

    }
    RecyclerView rv;
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        final int currentOrientation = newConfig.orientation;

        final CatchStaggeredGridLayoutManager mLayoutManager =
                (CatchStaggeredGridLayoutManager) rv.getLayoutManager();

        mLayoutManager.setSpanCount(getNumColumns(currentOrientation));
    }

    OverviewPagerAdapter submissionsPager;

    @Override
    public void updateSuccess(final List<Submission> submissions, final int startIndex) {
        LastComments.setCommentsSince(submissions);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (startIndex != -1) {
                    // TODO determine correct behaviour
                    //comments.notifyItemRangeInserted(startIndex, posts.posts.size());
                    submissionsPager.notifyDataSetChanged();
                } else {
                    submissionsPager.notifyDataSetChanged();
                }

            }
        });
    }

    @Override
    public void updateOffline(List<Submission> submissions, final long cacheTime) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                submissionsPager.notifyDataSetChanged();
            }
        });
    }

    @Override
    public void updateOfflineError() {
    }

    @Override
    public void updateError() {
    }
    @NonNull
    private RecyclerView.LayoutManager createLayoutManager(final int numColumns) {
        return new CatchStaggeredGridLayoutManager(numColumns, CatchStaggeredGridLayoutManager.VERTICAL);
    }

    private int getNumColumns(final int orientation) {
        final int numColumns;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE && SettingValues.tabletUI) {
            numColumns = Reddit.dpWidth;
        } else if (orientation == Configuration.ORIENTATION_PORTRAIT && SettingValues.dualPortrait) {
            numColumns = 2;
        } else {
            numColumns = 1;
        }
        return numColumns;
    }
    public class OverviewPagerAdapter extends FragmentStatePagerAdapter {

        public OverviewPagerAdapter(FragmentManager fm) {
            super(fm);

        }

        @Override
        public Fragment getItem(int i) {

            Fragment f = null;
            ContentType.Type t = ContentType.getContentType(subredditPosts.getPosts().get(i));

            if (subredditPosts.getPosts().size() - 2 <= i && subredditPosts.hasMore()) {
                subredditPosts.loadMore(Gallery.this.getApplicationContext(), Gallery.this, false);
            }
            switch (t) {
                case GIF:
                case IMAGE:
                case IMGUR:
                case REDDIT:
                case EXTERNAL:
                case SPOILER:
                case DEVIANTART:
                case EMBEDDED:
                case LINK:
                case VID_ME:
                case STREAMABLE:
                case VIDEO:
                {
                    f = new MediaFragment();
                    Bundle args = new Bundle();
                    Submission submission = subredditPosts.getPosts().get(i);
                    String previewUrl = "";
                    if (submission.getDataNode().has("preview") && submission.getDataNode().get("preview").get("images").get(0).get("source").has("height")) { //Load the preview image which has probably already been cached in memory instead of the direct link
                        previewUrl = submission.getDataNode().get("preview").get("images").get(0).get("source").get("url").asText();
                    }
                    args.putString("contentUrl", submission.getUrl());
                    args.putString("firstUrl", previewUrl);
                    args.putInt("page", i);
                    args.putString("sub", subreddit);
                    f.setArguments(args);
                }
                break;
                case SELF: {
                    if (subredditPosts.getPosts().get(i).getSelftext().isEmpty()) {
                        f = new TitleFull();
                        Bundle args = new Bundle();
                        args.putInt("page", i);
                        args.putString("sub", subreddit);

                        f.setArguments(args);
                    } else {
                        f = new SelftextFull();
                        Bundle args = new Bundle();
                        args.putInt("page", i);
                        args.putString("sub", subreddit);

                        f.setArguments(args);
                    }
                }
                break;
                case ALBUM: {
                    f = new AlbumFull();
                    Bundle args = new Bundle();
                    args.putInt("page", i);
                    args.putString("sub", subreddit);

                    f.setArguments(args);
                }
                break;
                case NONE: {
                    if (subredditPosts.getPosts().get(i).getSelftext().isEmpty()) {
                        f = new TitleFull();
                        Bundle args = new Bundle();
                        args.putInt("page", i);
                        args.putString("sub", subreddit);

                        f.setArguments(args);
                    } else {
                        f = new SelftextFull();
                        Bundle args = new Bundle();
                        args.putInt("page", i);
                        args.putString("sub", subreddit);

                        f.setArguments(args);
                    }
                }
                break;
            }


            return f;


        }


        @Override
        public int getCount() {
            return subredditPosts.getPosts().size() ;
        }


    }

}
